package com.nickbether.pebbletasker.bridge

import android.content.Context
import com.nickbether.pebbletasker.bridge.dto.CommandEnvelope
import com.nickbether.pebbletasker.bridge.dto.ResultEnvelope

/**
 * Thin builder/dispatcher for watch/app commands (FINAL DESIGN §3.4, action plugins).
 *
 * Action runners construct a [CommandEnvelope] (closed `type` allowlist, flat string `args`) and call
 * [send], which delegates to [BridgeClient.execute] — that applies the `commands.core` capability
 * gate, the 9s timeout, and the RemoteException backstop for an old bridge lacking execute().
 *
 * The command `type` strings are the FROZEN allowlist (FINAL DESIGN "CONTRACTS TO FREEZE" #4 /
 * bridge §6). They are defined here as constants so every action plugin references the same string.
 */
class CommandSender(private val appContext: Context) {

    private val client get() = BridgeClient.get(appContext)

    /** Suspend send. Returns Ok(ResultEnvelope) on bridge success, Err otherwise. */
    suspend fun send(cmd: CommandEnvelope): BridgeResult<ResultEnvelope> = client.execute(cmd)

    /** Blocking send for a Tasker action runner's IntentService thread. */
    fun sendBlocking(cmd: CommandEnvelope): BridgeResult<ResultEnvelope> = client.executeBlocking(cmd)

    /** Convenience builder. */
    fun command(type: String, watch: String? = null, args: Map<String, String> = emptyMap()): CommandEnvelope =
        CommandEnvelope(type = type, watch = watch?.ifBlank { null }, args = args)

    /** FROZEN command type allowlist (bridge §6 mapping). */
    object Type {
        const val WATCH_GET_INFO = "watch.getInfo"
        const val WATCH_LAUNCH_APP = "watch.launchApp"
        const val WATCH_SET_WATCHFACE = "watch.setWatchface"
        const val WATCH_SET_QUICK_LAUNCH = "watch.setQuickLaunch"
        const val WATCH_SET_PREF = "watch.setPref"
        const val WATCH_SCREENSHOT = "watch.screenshot"
        const val WATCH_CONNECT = "watch.connect"
        const val WATCH_DISCONNECT = "watch.disconnect"
        const val NOTIFICATION_SEND = "notification.send"
        const val NOTIFICATION_MUTE_APP = "notification.muteApp"
        const val APPMESSAGE_SEND = "appmessage.send"
        const val APPMESSAGE_SUBSCRIBE = "appmessage.subscribe"
        const val TIMELINE_INSERT = "timeline.insert"
        const val TIMELINE_DELETE = "timeline.delete"
        const val SYSTEM_PING = "system.ping"
        const val SYSTEM_GET_LOCKER = "system.getLocker"
        const val HEALTH_SNAPSHOT = "health.snapshot"
        const val FW_CHECK = "fw.check"
        const val DEV_TOGGLE_CONNECTION = "dev.toggleConnection"
    }
}
