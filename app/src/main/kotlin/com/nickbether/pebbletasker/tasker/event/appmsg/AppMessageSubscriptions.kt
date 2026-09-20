package com.nickbether.pebbletasker.tasker.event.appmsg

import android.content.Context
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.dto.CommandEnvelope
import com.nickbether.pebbletasker.log.PLog
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.*

/** Desired configuration only, never authorization. Each Ready session must ask the server again. */
object AppMessageSubscriptions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun requestRestore(context: Context) { scope.launch {
        if (BridgeClient.get(context).awaitReady().isOk) restore(context)
    } }
    private val restoreLock = Mutex()
    private var appliedSession: String? = null
    private var applied: String? = null
    @Synchronized fun remember(context: Context, uuid: String?, watch: String? = null, ownership: String = "observe", subscriptionId: String? = null) {
        if (uuid.isNullOrBlank() || uuid.contains('%') || watch?.contains('%') == true) return
        if (runCatching { java.util.UUID.fromString(uuid) }.isFailure) return
        if (ownership !in setOf("observe", "tasker")) return
        val prefs = context.getSharedPreferences("appmessage_subscriptions", Context.MODE_PRIVATE)
        val id = subscriptionId ?: "$uuid/${watch.orEmpty()}"
        val entry = JSONObject().put("id", id).put("uuid", java.util.UUID.fromString(uuid).toString()).put("watch", watch.orEmpty()).put("ownership", ownership).toString()
        // A copied Tasker bundle also copies its opaque ID. Only identical configurations
        // coalesce; observing another configuration must never evict an independent profile.
        val incoming = JSONObject(entry)
        val previous = prefs.getStringSet("desired", emptySet()).orEmpty().filterNot {
            runCatching { sameConfiguration(JSONObject(it), incoming) }.getOrDefault(false)
        }.toSet()
        prefs.edit().putStringSet("desired", previous + entry).apply()
    }
    private fun sameConfiguration(a: JSONObject, b: JSONObject): Boolean =
        listOf("id", "uuid", "watch", "ownership").all { key -> a.optString(key, if (key == "ownership") "observe" else "") == b.optString(key, if (key == "ownership") "observe" else "") }

    /** Only an explicit edit decision may retire a previous configuration; host queries cannot
     * distinguish edits from copies. Other configurations sharing its ID must survive. */
    @Synchronized fun forget(context: Context, previous: AppMessageFilter) {
        val uuid = runCatching { java.util.UUID.fromString(previous.uuid).toString() }.getOrNull() ?: return
        val old = JSONObject().put("id", previous.subscriptionId ?: "${previous.uuid}/${previous.serial.orEmpty()}")
            .put("uuid", uuid).put("watch", previous.serial.orEmpty()).put("ownership", previous.ownership)
        val prefs = context.getSharedPreferences("appmessage_subscriptions", Context.MODE_PRIVATE)
        val kept = prefs.getStringSet("desired", emptySet()).orEmpty().filterNot {
            runCatching { sameConfiguration(JSONObject(it), old) }.getOrDefault(false)
        }.toSet()
        prefs.edit().putStringSet("desired", kept).apply()
    }
    @Synchronized fun clear(context: Context) {
        context.getSharedPreferences("appmessage_subscriptions", Context.MODE_PRIVATE).edit().remove("desired").apply()
    }
    suspend fun restore(context: Context): Boolean = restoreLock.withLock {
        val client = BridgeClient.get(context)
        val session = client.currentSession ?: return@withLock false
        if (!session.has("appmessages.replace_subscriptions")) {
            PLog.w { "Update Pebble: atomic AppMessage subscription replacement is unavailable" }
            return@withLock false
        }
        if (appliedSession != session.clientToken) { applied = null; appliedSession = session.clientToken }
        val entries = context.getSharedPreferences("appmessage_subscriptions", Context.MODE_PRIVATE).getStringSet("desired", emptySet()).orEmpty().toSet()
        val groups = entries.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .groupBy { it.optString("uuid") to it.optString("watch") }
        val desired = JSONArray()
        for ((key, profiles) in groups.toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })) {
            desired.put(JSONObject().put("uuid", key.first).put("watch", key.second)
                .put("ownership", if (profiles.any { it.optString("ownership", "observe") == "tasker" }) "tasker" else "observe"))
        }
        val payload = desired.toString()
        if (applied == payload) return@withLock true
        val result = client.execute(CommandEnvelope(type = "appmessage.subscribe",
            args = mapOf("mode" to "replace", "subscriptions_json" to payload)))
        if (result.isOk && client.currentSession === session) {
            val deferred = result.valueOrNull()?.data?.get("deferred_watches")
            if (deferred == null || deferred == "[]") applied = payload
            else { applied = null; PLog.w { "AppMessage watches not yet available: $deferred" } }
            true
        }
        else { PLog.w { "AppMessage subscriptions were not replaced: $result" }; false }
    }
}
