package com.nickbether.pebbletasker.tasker.base

import com.nickbether.pebbletasker.bridge.CommandSender

/** Exact backend support required for published components, shared by saving and execution. */
object FeatureSupport {
    /** Runner -> accepted command types, preferred first. Any advertised one makes the runner usable. */
    private val commands: Map<String, List<String>> = mapOf(
        "AppMessageRunner" to listOf(CommandSender.Type.APPMESSAGE_SEND),
        "ConnectRunner" to listOf(CommandSender.Type.WATCH_CONNECT),
        "DeletePinRunner" to listOf(CommandSender.Type.TIMELINE_DELETE),
        "DevToggleRunner" to listOf(CommandSender.Type.DEV_TOGGLE_CONNECTION),
        "DisconnectRunner" to listOf(CommandSender.Type.WATCH_DISCONNECT),
        "FwCheckRunner" to listOf(CommandSender.Type.WATCH_CHECK_FIRMWARE, CommandSender.Type.FW_CHECK),
        "GetInfoRunner" to listOf(CommandSender.Type.WATCH_GET_INFO),
        "HealthRunner" to listOf(CommandSender.Type.HEALTH_SNAPSHOT),
        "InsertPinRunner" to listOf(CommandSender.Type.TIMELINE_INSERT),
        "LaunchAppRunner" to listOf(CommandSender.Type.WATCH_LAUNCH_APP),
        "MuteAppRunner" to listOf(CommandSender.Type.NOTIFICATION_MUTE_APP),
        "PingRunner" to listOf(CommandSender.Type.SYSTEM_PING),
        "QuickLaunchRunner" to listOf(CommandSender.Type.WATCH_SET_QUICK_LAUNCH),
        "ScreenshotRunner" to listOf(CommandSender.Type.WATCH_SCREENSHOT),
        "SendNotifRunner" to listOf(CommandSender.Type.NOTIFICATION_SEND),
        "SetPrefRunner" to listOf(CommandSender.Type.WATCH_SET_PREF),
        "SetWatchfaceRunner" to listOf(CommandSender.Type.WATCH_SET_WATCHFACE),
        "ListPrefsRunner" to listOf(CommandSender.Type.WATCH_LIST_PREFS),
        "GetPrefRunner" to listOf(CommandSender.Type.WATCH_GET_PREF),
        "StopAppRunner" to listOf(CommandSender.Type.WATCH_STOP_APP),
        "SyncTimeRunner" to listOf(CommandSender.Type.WATCH_SYNC_TIME),
        "InstallFirmwareRunner" to listOf(CommandSender.Type.WATCH_INSTALL_FIRMWARE),
        "RebootRunner" to listOf(CommandSender.Type.WATCH_REBOOT),
        "PressButtonRunner" to listOf(CommandSender.Type.WATCH_PRESS_BUTTON),
        "SwipeRunner" to listOf(CommandSender.Type.WATCH_SWIPE),
        "GatherLogsRunner" to listOf(CommandSender.Type.WATCH_GATHER_LOGS),
        "FactoryResetRunner" to listOf(CommandSender.Type.WATCH_FACTORY_RESET),
    )
    /** Event runners that need a host feature beyond events.core. */
    private val events = mapOf(
        "PrefChangedRunner" to "command.${CommandSender.Type.WATCH_GET_PREF}",
        "FirmwareAvailableRunner" to "command.${CommandSender.Type.WATCH_CHECK_FIRMWARE}",
        "JobDoneRunner" to "events.owner_targeted",
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
    /** Capabilities of which at least one must be advertised; empty when nothing is required. */
    fun requiredAny(runner: Class<*>): List<String> =
        if (runner.name.contains(".tasker.action.")) commands[runner.simpleName].orEmpty().map { "command.$it" }
        else if (runner.name.contains(".tasker.event.appmsg.")) listOf("appmessages.replace_subscriptions")
        else listOfNotNull(events[runner.simpleName] ?: states[runner.simpleName])
    fun required(runner: Class<*>): String? = requiredAny(runner).firstOrNull()
    /** The preferred command an action runner sends. */
    fun commandOf(runner: Class<*>): String? = commands[runner.simpleName]?.firstOrNull()
    /** The first command of [runner] that [capabilities] advertises. */
    fun supportedCommand(runner: Class<*>, capabilities: Set<String>?): String? =
        commands[runner.simpleName]?.firstOrNull { capabilities != null && "command.$it" in capabilities }
    fun reason(runner: Class<*>, capabilities: Set<String>?): String? {
        val required = requiredAny(runner)
        if (required.isEmpty()) return null
        if (capabilities == null) return "Connect to the Pebble app to check support before saving this configuration."
        return if (required.any { it in capabilities }) null else "The connected Pebble app does not support ${required.first()}. Update the Pebble app or choose a supported component."
    }
}
