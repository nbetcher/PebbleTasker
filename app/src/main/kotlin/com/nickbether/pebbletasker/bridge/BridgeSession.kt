package com.nickbether.pebbletasker.bridge

import com.nickbether.pebbletasker.bridge.dto.BridgeHello
import com.nickbether.pebbletasker.bridge.dto.Grants

/**
 * Immutable snapshot of one successful handshake (FINAL DESIGN §3.2).
 *
 * A fresh [BridgeSession] is minted on EVERY (re)bind — tokens are NEVER reused. The bridge's token
 * map is static/process-scoped, and registerEventListener fails silently on a stale token, so a
 * session is valid only until the next disconnect/goodbye/rebind.
 *
 * Nothing here is persisted across bridge restarts. Only [EventCache] persists (high-water seq +
 * bootId), and it is keyed by bootId precisely so a new boot resets cleanly.
 */
data class BridgeSession(
    val clientToken: String,
    val bootId: String,
    val protocolVersion: Int,
    val latestSeq: Long,
    val appVersion: String,
    val capabilities: Set<String>,
    val grants: Grants,
) {
    /** True if the bridge advertised [capability] in this handshake. Unknown caps are treated absent. */
    fun has(capability: String): Boolean = capability in capabilities

    /** Notification/text content is redacted unless the bridge grant explicitly clears it. */
    val contentRedacted: Boolean get() = grants.contentRedacted

    companion object {
        /** Capability vocabulary (FINAL DESIGN "CONTRACTS TO FREEZE" #4). */
        const val CAP_EVENTS_CORE = "events.core"
        const val CAP_EVENTS_NOTIFICATIONS = "events.notifications"
        const val CAP_EVENTS_HEALTH = "events.health"
        const val CAP_COMMANDS_CORE = "commands.core"
        const val CAP_COMMANDS_SENSITIVE = "commands.sensitive"
        const val CAP_COMMANDS_DANGEROUS = "commands.dangerous"
        const val CAP_APPMESSAGES = "appmessages"
        const val CAP_SCREENSHOT = "screenshot"
        const val CAP_STATE_EXTENDED = "state.extended"

        fun from(hello: BridgeHello): BridgeSession = BridgeSession(
            clientToken = hello.clientToken,
            bootId = hello.bootId,
            protocolVersion = hello.protocolVersion,
            latestSeq = hello.latestSeq,
            appVersion = hello.appVersion,
            capabilities = hello.capabilities.toSet(),
            grants = hello.grants,
        )
    }
}
