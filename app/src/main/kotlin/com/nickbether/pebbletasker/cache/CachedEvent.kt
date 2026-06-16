package com.nickbether.pebbletasker.cache

import com.nickbether.pebbletasker.bridge.dto.EventEnvelope
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of the LATEST event of a given `type`, persisted by [EventCache].
 *
 * This is deliberately NOT a history record — only the most recent event per type is retained
 * (privacy + bounded storage). Event runners read this as their source of truth (the Tasker
 * pass-through `update` is treated as an unreliable hint).
 *
 * `data` carries the bridge's flat string map verbatim (e.g. battery uses data["level"]). Runners
 * pull typed values out of it.
 */
@Serializable
data class CachedEvent(
    val type: String,
    val bootId: String,
    val seq: Long,
    val ts: Long,
    val category: String = "",
    val watch: WatchRef? = null,
    val data: Map<String, String> = emptyMap(),
) {
    /** Convenience typed getters used widely by runners. */
    fun str(key: String): String? = data[key]
    fun int(key: String): Int? = data[key]?.toIntOrNull()
    fun long(key: String): Long? = data[key]?.toLongOrNull()
    fun bool(key: String): Boolean? = data[key]?.let { it == "true" || it == "1" }

    companion object {
        fun from(e: EventEnvelope): CachedEvent = CachedEvent(
            type = e.type,
            bootId = e.bootId,
            seq = e.seq,
            ts = e.ts,
            category = e.category,
            watch = e.watch,
            data = e.data,
        )

        /** Synthetic client-side event types (not emitted by the bridge). */
        const val TYPE_GAP = "system.gap"
    }
}
