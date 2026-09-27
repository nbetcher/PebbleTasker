package com.nickbether.pebbletasker.tasker.base

import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.BridgeSession
import com.nickbether.pebbletasker.bridge.CommandSender.Type

/** Command tiers granted per client in the Pebble app, lowest first. */
enum class Tier(val wire: String, val label: String) {
    NORMAL("normal", "Normal"),
    SENSITIVE("sensitive", "Sensitive"),
    DANGEROUS("dangerous", "Dangerous"),
    EXTREMELY_DANGEROUS("extremely_dangerous", "Extremely dangerous");

    companion object {
        /** A grant string from BridgeHello. Blank means no grant; an unknown value ranks as dangerous. */
        fun parse(value: String?): Tier? {
            val v = value?.trim()?.lowercase().orEmpty()
            if (v.isEmpty()) return null
            return entries.firstOrNull { it.wire == v } ?: DANGEROUS
        }
    }
}

/** The tier each command needs, as documented by the host. */
object CommandTiers {
    private val tiers = mapOf(
        Type.WATCH_GET_INFO to Tier.NORMAL,
        Type.WATCH_LAUNCH_APP to Tier.NORMAL,
        Type.WATCH_SET_WATCHFACE to Tier.NORMAL,
        Type.WATCH_SET_QUICK_LAUNCH to Tier.NORMAL,
        Type.WATCH_SET_PREF to Tier.SENSITIVE,
        Type.WATCH_CONNECT to Tier.SENSITIVE,
        Type.WATCH_DISCONNECT to Tier.SENSITIVE,
        Type.NOTIFICATION_SEND to Tier.NORMAL,
        Type.NOTIFICATION_MUTE_APP to Tier.SENSITIVE,
        Type.TIMELINE_INSERT to Tier.NORMAL,
        Type.TIMELINE_DELETE to Tier.NORMAL,
        Type.SYSTEM_PING to Tier.NORMAL,
        Type.HEALTH_SNAPSHOT to Tier.NORMAL,
        Type.FW_CHECK to Tier.SENSITIVE,
        Type.DEV_TOGGLE_CONNECTION to Tier.DANGEROUS,
        Type.WATCH_LIST_PREFS to Tier.NORMAL,
        Type.WATCH_GET_PREF to Tier.NORMAL,
        Type.WATCH_STOP_APP to Tier.NORMAL,
        Type.WATCH_SYNC_TIME to Tier.NORMAL,
        Type.WATCH_CHECK_FIRMWARE to Tier.NORMAL,
        Type.WATCH_SCREENSHOT to Tier.SENSITIVE,
        Type.WATCH_REBOOT to Tier.DANGEROUS,
        Type.WATCH_PRESS_BUTTON to Tier.DANGEROUS,
        Type.WATCH_SWIPE to Tier.DANGEROUS,
        Type.WATCH_INSTALL_FIRMWARE to Tier.DANGEROUS,
        Type.WATCH_GATHER_LOGS to Tier.EXTREMELY_DANGEROUS,
        Type.WATCH_FACTORY_RESET to Tier.EXTREMELY_DANGEROUS,
    )

    fun of(command: String?): Tier? = command?.let { tiers[it] }

    /** The client's effective grant. A grant the plugin does not know ranks as dangerous. */
    fun granted(session: BridgeSession?): Tier? = Tier.parse(session?.grants?.tier)

    /** True when the grant is known to be below [required]. Unknown grants are not judged. */
    fun insufficient(required: Tier, session: BridgeSession?): Boolean {
        val granted = granted(session) ?: return false
        return granted < required
    }

    /** One line for the action editor: what the command needs and what this plugin holds. */
    fun describe(required: Tier, session: BridgeSession?): String = buildString {
        append("Requires the ").append(required.label.lowercase()).append(" command tier")
        val granted = granted(session)
        if (granted != null) append(" (this plugin has ").append(granted.label.lowercase()).append(")")
        append('.')
        if (required >= Tier.DANGEROUS) append(" The Pebble app's \"Allow dangerous commands\" switch must also be on.")
        if (required == Tier.EXTREMELY_DANGEROUS)
            append(" Granting it in the Pebble app shows a one-time warning; its Accept button unlocks after 10 seconds.")
    }

    /**
     * Explains a refused command so the user knows which grant, switch or preparation is missing.
     * Returns the original message when there is nothing to add.
     */
    fun explain(err: BridgeResult.Err, command: String?, session: BridgeSession?): String {
        val base = err.message.ifBlank { err.bridgeCode ?: "error" }
        val hint = when (err.bridgeCode) {
            "COMMAND_NOT_AUTHORIZED" -> {
                val required = of(command)
                val granted = granted(session)
                when {
                    required == null -> "Raise this plugin's command tier in the Pebble app."
                    granted != null && granted < required ->
                        "Needs the ${required.label.lowercase()} tier; this plugin has ${granted.label.lowercase()}. Raise it in the Pebble app."
                    required >= Tier.DANGEROUS ->
                        "Needs the ${required.label.lowercase()} tier and the Pebble app's \"Allow dangerous commands\" switch." +
                            if (required == Tier.EXTREMELY_DANGEROUS) " Accept the one-time warning shown when granting it." else ""
                    else -> "Needs the ${required.label.lowercase()} tier. Check this plugin's grant in the Pebble app."
                }
            }
            "CATEGORY_DISABLED" -> "Allow the system event category for this plugin in the Pebble app; job results are delivered as system events."
            "FIRMWARE_CHECK_STALE" -> "Run Check Firmware first; no successful check is known from the last 24 hours."
            "FIRMWARE_UPDATE_UNAVAILABLE" -> "The last firmware check found no update."
            "WATCH_BUSY" -> "The watch is busy with another input sequence or update. Retry later."
            "PREF_UNSUPPORTED" -> "This watch does not support that preference."
            else -> null
        }
        return if (hint == null || base.contains(hint)) base else "$base. $hint"
    }
}
