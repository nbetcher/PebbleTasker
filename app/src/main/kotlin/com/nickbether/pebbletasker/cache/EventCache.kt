package com.nickbether.pebbletasker.cache

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nickbether.pebbletasker.bridge.BridgePluginJson
import com.nickbether.pebbletasker.bridge.dto.EventEnvelope
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.ConcurrentHashMap

private val Context.eventDataStore by preferencesDataStore(name = "pb_event_cache")

/**
 * Last-event-per-type cache + seq/bootId high-water tracking + gap synthesis (FINAL DESIGN §3.7).
 *
 * Push model: BridgeListener.onEvents -> [put] each event -> EventRouter fans out requestQuery.
 * Event runners later call [latest] to read the most recent event of their type.
 *
 * Storage: a DataStore-backed durable copy (survives process death so a profile evaluated after a
 * cold start still sees the last-known state) PLUS an in-memory mirror for synchronous reads on the
 * runner's IntentService thread (runners are not coroutines).
 *
 * GAP DETECTION (FINAL DESIGN §0 FIX A2): the bridge's recovery ring is only 50 events with NO
 * `more` flag, and getEventsSince returns the bridge's CURRENT bootId even on mismatch. So a gap is
 * detected two ways, both here in [put]/[putBatch]:
 *   1. bootId change          -> the bridge restarted; all prior seqs are void.
 *   2. seq discontinuity      -> min(incoming seq) > lastSeq + 1 within the same boot (>50 lost).
 * Either condition synthesizes a [CachedEvent.TYPE_GAP] event so the E15 (Bridge/Watch Error) plugin
 * can fire, carrying gap_from/gap_to.
 *
 * Thread-safety: [put]/[putBatch] are synchronized; the in-memory map is concurrent. DataStore
 * writes are launched on the caller's coroutine when available, else best-effort blocking.
 */
class EventCache internal constructor(private val appContext: Context) {

    private val mem = ConcurrentHashMap<String, CachedEvent>()

    @Volatile private var memBootId: String? = null
    @Volatile private var memHighWater: Long = -1L

    private val mapSerializer = MapSerializer(String.serializer(), CachedEvent.serializer())

    /** Persistence runs off the caller's thread: [put] is invoked on the bridge's Binder thread and
     *  MUST NOT block on disk (FINAL DESIGN §3.3 — onEvents enqueues and returns immediately). */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Warm the in-memory mirror from disk so synchronous runner reads work after a cold start.
     * Called once by [get]; kept explicit rather than an init block so construction has no side
     * effects and tests can exercise the delivery logic without touching DataStore.
     */
    private fun warm() {
        runCatching {
            runBlocking {
                val prefs = appContext.eventDataStore.data.first()
                memBootId = prefs[KEY_BOOT_ID]
                memHighWater = prefs[KEY_HIGH_WATER] ?: -1L
                prefs[KEY_EVENTS_JSON]?.let { raw ->
                    BridgePluginJson.json.decodeFromString(mapSerializer, raw).forEach { (k, v) -> mem[k] = v }
                }
            }
        }
    }

    // --- reads (synchronous; safe on runner thread) ---

    /** Most recent cached event of [type], or null if none seen this boot. */
    fun latest(type: String): CachedEvent? = mem[type]

    /** Most recent event whose [WatchRef.serial] matches, scanning all types (used by some states). */
    fun latestForSerial(type: String, serial: String?): CachedEvent? {
        val e = mem[type] ?: return null
        if (serial.isNullOrBlank()) return e
        return if (e.watch?.serial == serial || e.watch?.address == serial) e else null
    }

    /** Current high-water seq for [bootId]; -1 if the cache is for a different/unknown boot. */
    fun highWaterSeqFor(bootId: String): Long =
        if (memBootId == bootId) memHighWater else -1L

    val currentBootId: String? get() = memBootId
    val currentHighWater: Long get() = memHighWater

    // --- writes ---

    /**
     * Seed the high-water from a handshake's BridgeHello.latestSeq for [bootId]. On a NEW bootId this
     * resets the cache (clears per-type events for the prior boot) and synthesizes a gap.
     * Returns a synthesized gap event if the boot changed, else null.
     */
    @Synchronized
    fun seedFromHandshake(bootId: String, latestSeq: Long): CachedEvent? {
        val prevBoot = memBootId
        var gap: CachedEvent? = null
        if (prevBoot != null && prevBoot != bootId) {
            gap = synthGap(bootId, fromSeq = memHighWater, toSeq = latestSeq, reason = "boot_changed")
            mem.clear()
        }
        memBootId = bootId
        // Only reset the high-water on a NEW boot. Raising it to latestSeq within the same boot would
        // skip every event that arrived while this process was dead — the caller derives its replay
        // cursor from this value immediately after seeding — and the seq-gap check in putAll() can no
        // longer see the hole either, so the loss is silent.
        if (prevBoot != bootId) memHighWater = latestSeq
        persist()
        gap?.let { mem[it.type] = it }
        return gap
    }

    /**
     * Ingest one event. Returns the list of events the caller should route (the event itself if it
     * is new, plus a synthesized gap event when a discontinuity is detected). Duplicates (already-seen
     * (bootId,seq)) return an empty list.
     */
    @Synchronized
    fun put(e: EventEnvelope): List<CachedEvent> = putAll(listOf(e))

    /** Ingest a batch (recovery / push). Same semantics as [put], deduped and gap-checked once. */
    @Synchronized
    fun putBatch(events: List<EventEnvelope>): List<CachedEvent> = putAll(events)

    private fun putAll(events: List<EventEnvelope>): List<CachedEvent> {
        if (events.isEmpty()) return emptyList()
        val routed = ArrayList<CachedEvent>(events.size + 1)

        // All events in a batch share a bootId in practice; handle a boot change up front.
        val incomingBoot = events.first().bootId
        if (memBootId != null && memBootId != incomingBoot) {
            val minSeq = events.minOf { it.seq }
            val gap = synthGap(incomingBoot, fromSeq = memHighWater, toSeq = minSeq, reason = "boot_changed")
            mem[gap.type] = gap
            routed += gap
            mem.entries.removeIf { it.value.bootId != incomingBoot }
            memHighWater = -1L
        }
        memBootId = incomingBoot

        // Same-boot seq-contiguity gap: if the smallest NEW seq jumps past lastSeq+1, >50 were lost.
        val newOnes = events.filter { it.seq > memHighWater }.sortedBy { it.seq }
        if (newOnes.isNotEmpty() && memHighWater >= 0) {
            val firstNew = newOnes.first().seq
            if (firstNew > memHighWater + 1) {
                val gap = synthGap(incomingBoot, fromSeq = memHighWater, toSeq = firstNew, reason = "seq_gap")
                mem[gap.type] = gap
                routed += gap
            }
        }

        for (e in newOnes) {
            val cached = CachedEvent.from(e)
            mem[e.type] = cached
            routed += cached
            if (e.seq > memHighWater) memHighWater = e.seq
        }

        if (routed.isNotEmpty()) persist()
        return routed
    }

    // --- gap synthesis ---

    private fun synthGap(bootId: String, fromSeq: Long, toSeq: Long, reason: String): CachedEvent =
        CachedEvent(
            type = CachedEvent.TYPE_GAP,
            bootId = bootId,
            // Synthetic seq sits just above the current high-water so it routes once and dedupes.
            seq = maxOf(memHighWater, toSeq),
            ts = System.currentTimeMillis(),
            category = "system",
            watch = null,
            data = mapOf(
                "error_type" to "gap",
                "reason" to reason,
                "gap_from" to fromSeq.coerceAtLeast(0).toString(),
                "gap_to" to toSeq.coerceAtLeast(0).toString(),
            ),
        )

    // --- persistence ---

    private fun persist() {
        val snapshot = HashMap(mem)
        val boot = memBootId
        val hw = memHighWater
        ioScope.launch {
            runCatching {
                appContext.eventDataStore.edit { prefs ->
                    boot?.let { prefs[KEY_BOOT_ID] = it }
                    prefs[KEY_HIGH_WATER] = hw
                    prefs[KEY_EVENTS_JSON] =
                        BridgePluginJson.json.encodeToString(mapSerializer, snapshot)
                }
            }
        }
    }

    companion object {
        private val KEY_BOOT_ID = stringPreferencesKey("boot_id")
        private val KEY_HIGH_WATER = longPreferencesKey("high_water_seq")
        private val KEY_EVENTS_JSON = stringPreferencesKey("events_json")

        @Volatile private var instance: EventCache? = null

        fun get(context: Context): EventCache =
            instance ?: synchronized(this) {
                instance ?: EventCache(context.applicationContext).also { it.warm(); instance = it }
            }

        /** Discard the disconnected/never-populated WatchRef fields are documented per E2; helper. */
        fun isDisconnectPartial(w: WatchRef?): Boolean =
            w != null && w.serial == w.address && w.nickname == null && w.battery == null
    }
}
