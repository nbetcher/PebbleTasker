package com.nickbether.pebbletasker.cache

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.nickbether.pebbletasker.bridge.BridgePluginJson

/** Immutable serialized payload carried by SDK 0.4.10 in each query's pass-through bundle.
 * Every matching profile receives its own copy; there is no global consume operation.
 * Hosts that discard pass-through data cannot safely deliver events and are refused at setup.
 */
@TaskerInputRoot
class EventDelivery @JvmOverloads constructor(
    @field:TaskerInputField("pebble_event_payload") var payload: String = "",
    @field:TaskerInputField("pebble_event_epoch") var epoch: String = "",
) {
    fun event(): CachedEvent? = runCatching {
        BridgePluginJson.json.decodeFromString(CachedEvent.serializer(), payload)
    }.getOrNull()

    companion object {
        fun from(event: CachedEvent, epoch: String = "") = EventDelivery(
            BridgePluginJson.json.encodeToString(CachedEvent.serializer(), event), epoch,
        )
    }
}
