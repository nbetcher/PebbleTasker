package com.nickbether.pebbletasker.cache

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.nickbether.pebbletasker.bridge.BridgePluginJson
import com.nickbether.pebbletasker.bridge.dto.*
import com.nickbether.pebbletasker.log.PLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val Context.eventDataStore by preferencesDataStore(name = "pb_event_cache")

/** Per-watch current snapshots and durable replay cursor. Edges travel in EventDelivery, not here.
 * One writer consumes snapshots in mutation order. A barrier lets tests wait for actual persistence.
 */
class EventCache internal constructor(
    private val appContext: Context,
    private val writeSnapshot: (suspend (Snapshot) -> Unit)? = null,
) {
    internal data class Snapshot(val boot: String?, val highWater: Long, val events: Map<String, CachedEvent>, val epoch: String, val authority: String? = null)
    private data class Write(val snapshot: Snapshot, val version: Long)
    private data class Written(val version: Long = 0, val error: Exception? = null)
    private val mem = linkedMapOf<String, CachedEvent>()
    @Volatile private var memBootId: String? = null
    @Volatile private var memHighWater = -1L
    private var authority: String? = null
    @Volatile var deliveryEpoch: String = java.util.UUID.randomUUID().toString()
        private set
    private val serializer = MapSerializer(String.serializer(), CachedEvent.serializer())
    // Snapshots supersede older snapshots; edges are routed independently. Slow storage must
    // not retain an unbounded queue of full copies during replay or rapid watch updates.
    private val writes = Channel<Write>(Channel.CONFLATED)
    private val submitted = AtomicLong()
    private val written = MutableStateFlow(Written())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        ioScope.launch {
            for (write in writes) {
                var failure: Exception? = null
                write.snapshot.let { snapshot ->
                    try {
                        if (writeSnapshot != null) writeSnapshot.invoke(snapshot)
                        else appContext.eventDataStore.edit { prefs ->
                            snapshot.boot?.let { prefs[KEY_BOOT_ID] = it }
                            prefs[KEY_EPOCH] = snapshot.epoch
                            if (snapshot.authority == null) prefs.remove(KEY_AUTHORITY) else prefs[KEY_AUTHORITY] = snapshot.authority
                            prefs[KEY_HIGH_WATER] = snapshot.highWater
                            prefs[KEY_EVENTS_JSON] = BridgePluginJson.json.encodeToString(serializer, snapshot.events)
                        }
                    } catch (e: Exception) { failure = e; PLog.e(e) { "cache: persistence failed; replay may resume from older cursor" } }
                }
                written.value = Written(write.version, failure)
            }
        }
    }

    internal fun warm() {
        try {
            runBlocking {
                val prefs = appContext.eventDataStore.data.first()
                synchronized(this@EventCache) {
                    deliveryEpoch = prefs[KEY_EPOCH] ?: deliveryEpoch
                    authority = prefs[KEY_AUTHORITY]
                    memBootId = prefs[KEY_BOOT_ID]
                    memHighWater = prefs[KEY_HIGH_WATER] ?: -1L
                    prefs[KEY_EVENTS_JSON]?.let { raw ->
                        BridgePluginJson.json.decodeFromString(serializer, raw).values.forEach { remember(it) }
                    }
                }
            }
        } catch (e: Exception) { PLog.e(e) { "cache: cannot restore cursor" } }
    }

    @Synchronized fun latest(type: String): CachedEvent? = mem.values.filter { it.type == type }.maxByOrNull { it.seq }
    @Synchronized fun latestForSerial(type: String, serial: String?): CachedEvent? =
        mem.values.filter { it.type == type && (serial.isNullOrBlank() || it.watch?.serial == serial || it.watch?.address == serial) }.maxByOrNull { it.seq }
    @Synchronized fun latestAll(type: String): List<CachedEvent> = mem.values.filter { it.type == type }
    fun highWaterSeqFor(bootId: String): Long = if (memBootId == bootId) memHighWater else -1
    val currentBootId: String? get() = memBootId
    val currentHighWater: Long get() = memHighWater

    @Synchronized fun seedFromHandshake(bootId: String, latestSeq: Long, recover: Boolean = false): CachedEvent? {
        val changed = memBootId != null && memBootId != bootId
        val gap = if (changed) synthGap(bootId, memHighWater, latestSeq, "boot_changed") else null
        if (memBootId != bootId) {
            mem.clear()
            memHighWater = if (recover) -1L else latestSeq
        }
        memBootId = bootId
        gap?.let { remember(it) }
        persist()
        return gap
    }

    /** Authoritative server cursor accounts for events filtered by grants. Sequence holes alone
     * are not loss. Empty batches can advance the cursor and explicitly report history loss.
     */
    @Synchronized fun ingestBatch(batch: EventBatch): List<CachedEvent> {
        if (memBootId != null && memBootId != batch.bootId) return emptyList()
        if (batch.events.any { it.bootId != batch.bootId }) return emptyList()
        memBootId = batch.bootId
        val old = memHighWater
        val routed = ingest(batch.events)
        val end = maxOf(memHighWater, batch.cursor ?: memHighWater)
        if (batch.historyLost && (end > old || old < 0)) {
            val gap = synthGap(batch.bootId, old, end, "history_lost")
            remember(gap)
            routed.add(0, gap)
        }
        memHighWater = end
        persist()
        return routed
    }

    /** Legacy single-event callers retained; sequence holes do not establish filtered-history loss. */
    @Synchronized fun put(e: EventEnvelope): List<CachedEvent> = putBatch(listOf(e))
    @Synchronized fun putBatch(events: List<EventEnvelope>): List<CachedEvent> {
        if (events.isEmpty()) return emptyList()
        return ingestBatch(EventBatch(bootId = events.first().bootId, events = events))
    }

    private fun ingest(events: List<EventEnvelope>): ArrayList<CachedEvent> {
        val result = arrayListOf<CachedEvent>()
        for (event in events.sortedBy { it.seq }) {
            if (event.seq <= memHighWater) continue
            val cached = CachedEvent.from(event)
            remember(cached)
            result.add(cached)
            memHighWater = event.seq
            if (event.type == "watch.disconnected") {
                remember(cached.copy(type = "dev.state", data = mapOf("enabled" to "false")))
                remember(cached.copy(type = "fw.status", data = mapOf("status" to "unavailable")))
            }
        }
        return result
    }

    /** Snapshot hydration never emits an edge or advances the replay cursor. */
    @Synchronized fun seedState(state: StateResult) {
        val boot = memBootId ?: return
        mem.entries.removeAll { it.value.type in setOf("dev.state", "fw.status", "bt.state") }
        fun rememberState(type: String, watch: WatchRef?, data: Map<String, String>) = remember(
            CachedEvent(type, boot, memHighWater, System.currentTimeMillis(), watch = watch, data = data),
        )
        for (watch in state.data.watches) {
            watch.devEnabled?.let { rememberState("dev.state", watch, mapOf("enabled" to it.toString())) }
            watch.fwStatus?.let { status -> rememberState("fw.status", watch, buildMap {
                put("status", status)
                watch.fwProgress?.let { put("progress", it.toString()) }
            }) }
        }
        state.data.bluetoothEnabled?.let { rememberState("bt.state", null, mapOf("enabled" to it.toString())) }
        persist()
    }

    /** Content revocation is independent of replay progress. Queued old Tasker payloads expire too. */
    @Synchronized fun invalidateAuthority() {
        mem.clear()
        authority = null
        deliveryEpoch = java.util.UUID.randomUUID().toString()
        persist()
    }

    /** Transport loss hides snapshots but cannot revoke already routed edges. Runners must await
     * fresh readiness; only a matching server authority revision can retain their epoch. */
    @Synchronized fun suspendAuthority() { mem.clear(); persist() }
    @Synchronized fun acceptAuthority(boot: String, revision: String?) {
        val next = revision?.takeIf { it.isNotBlank() }?.let { "$boot:$it" }
        if (next == null || next != authority) invalidateAuthority()
        authority = next
        persist()
    }

    private fun remember(event: CachedEvent) { mem[event.type + "\u0000" + (event.watch?.serial ?: event.watch?.address ?: "")] = event }
    private fun synthGap(boot: String, from: Long, to: Long, reason: String) = CachedEvent(
        CachedEvent.TYPE_GAP, boot, maxOf(from, to), System.currentTimeMillis(), "system", data = mapOf(
            "error_type" to "gap", "reason" to reason, "gap_from" to from.coerceAtLeast(0).toString(), "gap_to" to to.coerceAtLeast(0).toString(),
        ),
    )
    private fun persist() { writes.trySend(Write(Snapshot(memBootId, memHighWater, mem.toMap(), deliveryEpoch, authority), submitted.incrementAndGet())) }
    internal suspend fun awaitPersistence() {
        val target = submitted.get()
        written.first { it.version >= target }.error?.let { throw it }
    }

    companion object {
        private val KEY_EPOCH = stringPreferencesKey("delivery_epoch")
        private val KEY_AUTHORITY = stringPreferencesKey("authority_revision")
        private val KEY_BOOT_ID = stringPreferencesKey("boot_id")
        private val KEY_HIGH_WATER = longPreferencesKey("high_water_seq")
        private val KEY_EVENTS_JSON = stringPreferencesKey("events_json")
        @Volatile private var instance: EventCache? = null
        fun get(context: Context): EventCache = instance ?: synchronized(this) {
            instance ?: EventCache(context.applicationContext).also { it.warm(); instance = it }
        }
        fun isDisconnectPartial(w: WatchRef?): Boolean = w != null && w.serial == w.address && w.nickname == null && w.battery == null
    }
}
