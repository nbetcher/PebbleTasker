package com.nickbether.pebbletasker.tasker.base

import android.content.Context
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.EventRouter
import com.nickbether.pebbletasker.log.PLog
import com.nickbether.pebbletasker.ui.BridgeWarning

/** Condition results have no native action-error channel. Send a normal SDK diagnostic event to
 * configured Pebble Bridge/Watch Error profiles, and retain the decision for foreground inspection.
 * Tasker decides whether a diagnostic task runs/notifies. No backup-restored broadcast is assumed.
 */
object ConditionAccess {
    fun ready(context: Context): Boolean = when (val result = BridgeClient.get(context).awaitReadyBlocking(timeoutMs = ConditionQueryBudget.remaining(4000))) {
        is BridgeResult.Ok -> {
            val prefs = context.getSharedPreferences("condition_access", Context.MODE_PRIVATE)
            if (prefs.getInt("code", 0) != com.nickbether.pebbletasker.tasker.ErrCodes.UNSUPPORTED_COMMAND) clear(context)
            true
        }
        is BridgeResult.Err -> { report(context, result); false }
    }

    @Synchronized fun report(context: Context, error: BridgeResult.Err) {
        val prefs = context.getSharedPreferences("condition_access", Context.MODE_PRIVATE)
        val key = "${error.code}:${error.message}"
        if (prefs.getString("decision", null) == key) return
        val diagnosticId = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("diagnostic_id", diagnosticId).putString("decision", key).putString("message", error.message).putInt("code", error.code).apply()
        PLog.w { "condition access: ${error.code} ${error.message}" }
        BridgeWarning.warnIfUsedWhileUnbridged(context)
        EventRouter.routeAll(context, listOf(CachedEvent(
            type = "plugin.access", bootId = "plugin", seq = System.nanoTime(), ts = System.currentTimeMillis(), category = "system",
            data = mapOf("diagnostic_id" to diagnosticId, "error_type" to (error.bridgeCode ?: error.code.toString()), "message" to error.message),
        )))
    }

    @Synchronized fun clear(context: Context) {
        context.getSharedPreferences("condition_access", Context.MODE_PRIVATE).edit().remove("diagnostic_id").remove("decision").remove("message").remove("code").apply()
    }
    fun acceptsDiagnostic(context: Context, event: CachedEvent): Boolean {
        val id = event.data["diagnostic_id"] ?: return false
        return id == context.getSharedPreferences("condition_access", Context.MODE_PRIVATE).getString("diagnostic_id", null)
    }
    fun lastMessage(context: Context): String? = context.getSharedPreferences("condition_access", Context.MODE_PRIVATE).getString("message", null)
}
