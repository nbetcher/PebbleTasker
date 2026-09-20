package com.nickbether.pebbletasker.cache

import com.nickbether.pebbletasker.bridge.dto.EventEnvelope
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import kotlinx.serialization.Serializable

/** Immutable event value used both as a per-watch snapshot and as an SDK delivery payload. */
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
    fun bool(key: String): Boolean? = when (data[key]) { "true", "1" -> true; "false", "0" -> false; else -> null }

    companion object {
        fun from(e: EventEnvelope): CachedEvent = CachedEvent(
            type = e.type,
            bootId = e.bootId,
            seq = e.seq,
            ts = e.ts,
            category = e.category,
            watch = e.watch,
            data = e.data.toMap(),
        )

        /** Synthetic client-side event types (not emitted by the bridge). */
        const val TYPE_GAP = "system.gap"
    }
}
