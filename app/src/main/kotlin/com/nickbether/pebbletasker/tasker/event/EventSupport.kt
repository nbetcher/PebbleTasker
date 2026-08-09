package com.nickbether.pebbletasker.tasker.event

import com.nickbether.pebbletasker.bridge.dto.WatchRef
import com.nickbether.pebbletasker.cache.CachedEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Shared helpers for the 15 event plugins (FINAL DESIGN §2.1).
 *
 * Every event output carries:
 *  - the watch IDENTITY block (1:1 with WatchRef), blank-safe (on watch.disconnected only
 *    serial/name/address are populated; the rest are null per ConnectivityCollector:30-34),
 *  - the universal trio %pbl_event_type / %pbl_seq / %pbl_boot_id,
 *  - one structured %pbl_json blob (Tasker 5.12+ reads %pbl_json.field).
 *
 * These helpers keep that surface identical across all events without a shared output superclass
 * (the library reflects over each concrete @TaskerOutputObject, so inheritance of annotated getters
 * is avoided — every output declares its own fields, and uses these builders to fill them).
 */
object EventSupport {

    /** Serializer for the %pbl_json blob. Stable key order, omit nulls handled by builder. */
    val json: Json = Json { encodeDefaults = true }

    /** Identity block extracted from an event's WatchRef (all fields blank/empty when absent). */
    data class Identity(
        val serial: String,
        val name: String,
        val nickname: String,
        val model: String,
        val fw: String,
        val battery: String,
        val address: String,
    ) {
        companion object {
            val EMPTY = Identity("", "", "", "", "", "", "")

            fun from(w: WatchRef?): Identity {
                if (w == null) return EMPTY
                return Identity(
                    serial = w.serial,
                    name = w.name,
                    nickname = w.nickname.orEmpty(),
                    model = w.model.orEmpty(),
                    fw = w.fw.orEmpty(),
                    battery = w.battery?.toString().orEmpty(),
                    address = w.address.orEmpty(),
                )
            }
        }
    }

    /** True when the configured serial filter is blank (match-any) or matches serial OR address. */
    fun matchesSerial(filter: String?, w: WatchRef?): Boolean {
        if (filter.isNullOrBlank()) return true
        if (w == null) return false
        return filter == w.serial || filter == w.address
    }

    /**
     * Build the %pbl_json blob from the identity block plus the event's flat data map and metadata.
     * Used by every event output so %pbl_json.<field> is consistent and never throws.
     */
    fun buildJson(cached: CachedEvent?, extra: Map<String, String> = emptyMap()): String {
        val id = Identity.from(cached?.watch)
        val obj: JsonObject = buildJsonObject {
            put("event_type", cached?.type ?: "")
            put("seq", cached?.seq ?: -1L)
            put("boot_id", cached?.bootId ?: "")
            put("ts", cached?.ts ?: 0L)
            put("serial", id.serial)
            put("name", id.name)
            put("nickname", id.nickname)
            put("model", id.model)
            put("fw", id.fw)
            put("battery", id.battery)
            put("address", id.address)
            // Event-specific scalars (already string-typed by the bridge).
            for ((k, v) in extra) put(k, v)
            // Raw bridge data map, namespaced so it never collides with the curated fields above.
            cached?.data?.let { d ->
                if (d.isNotEmpty()) {
                    put(
                        "data",
                        buildJsonObject { for ((k, v) in d) put(k, v) },
                    )
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }
}
