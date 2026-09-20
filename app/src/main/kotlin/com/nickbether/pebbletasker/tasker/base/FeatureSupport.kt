package com.nickbether.pebbletasker.tasker.base

import com.nickbether.pebbletasker.bridge.CommandSender

/** Exact backend support required for published components, shared by saving and execution. */
object FeatureSupport {
    private val commands = mapOf(
        "AppMessageRunner" to CommandSender.Type.APPMESSAGE_SEND,
        "ConnectRunner" to CommandSender.Type.WATCH_CONNECT,
        "DeletePinRunner" to CommandSender.Type.TIMELINE_DELETE,
        "DevToggleRunner" to CommandSender.Type.DEV_TOGGLE_CONNECTION,
        "DisconnectRunner" to CommandSender.Type.WATCH_DISCONNECT,
        "FwCheckRunner" to CommandSender.Type.FW_CHECK,
        "GetInfoRunner" to CommandSender.Type.WATCH_GET_INFO,
        "HealthRunner" to CommandSender.Type.HEALTH_SNAPSHOT,
        "InsertPinRunner" to CommandSender.Type.TIMELINE_INSERT,
        "LaunchAppRunner" to CommandSender.Type.WATCH_LAUNCH_APP,
        "MuteAppRunner" to CommandSender.Type.NOTIFICATION_MUTE_APP,
        "PingRunner" to CommandSender.Type.SYSTEM_PING,
        "QuickLaunchRunner" to CommandSender.Type.WATCH_SET_QUICK_LAUNCH,
        "ScreenshotRunner" to CommandSender.Type.WATCH_SCREENSHOT,
        "SendNotifRunner" to CommandSender.Type.NOTIFICATION_SEND,
        "SetPrefRunner" to CommandSender.Type.WATCH_SET_PREF,
        "SetWatchfaceRunner" to CommandSender.Type.WATCH_SET_WATCHFACE,
    )
    private val states = mapOf(
        "S2DndRunner" to "state.dnd", "S3DevConnectionRunner" to "state.dev",
        "S4WatchfaceRunner" to "state.watchface", "S5FirmwareUpdatingRunner" to "state.firmware",
        "S6BluetoothRunner" to "state.bluetooth",
    )
    fun inputReason(input: Any): String? {
        val serial = when (input) {
            is com.nickbether.pebbletasker.tasker.action.sendnotif.SendNotifInput -> input.serial
            is com.nickbether.pebbletasker.tasker.action.setpref.SetPrefInput -> input.serial
            is com.nickbether.pebbletasker.tasker.action.quicklaunch.QuickLaunchInput -> input.serial
            else -> null
        }
        return if (serial.isNullOrBlank()) null else "This command applies globally. Clear the watch selector before saving."
    }
    fun required(runner: Class<*>): String? =
        if (runner.name.contains(".tasker.action.")) commands[runner.simpleName]?.let { "command.$it" }
        else if (runner.name.contains(".tasker.event.appmsg.")) "appmessages.replace_subscriptions"
        else states[runner.simpleName]
    fun reason(runner: Class<*>, capabilities: Set<String>?): String? {
        val required = required(runner) ?: return null
        if (capabilities == null) return "Connect to the Pebble app to check support before saving this configuration."
        return if (required in capabilities) null else "The connected Pebble app does not support $required. Update the Pebble app or choose a supported component."
    }
}
