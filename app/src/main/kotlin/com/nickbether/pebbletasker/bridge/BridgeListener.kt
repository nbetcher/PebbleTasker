package com.nickbether.pebbletasker.bridge

import com.nickbether.pebbletasker.bridge.dto.EventBatch
import coredevices.coreapp.automation.IBridgeEventListener
import kotlinx.coroutines.channels.Channel

/** One listener per registration. Binder callbacks only validate and enqueue; they never call IPC
 * or mutate the cache. The owner starts one ordered consumer after registration is acknowledged. */
class BridgeListener(
    private val bootId: String,
    private val token: String,
    private val isCurrent: () -> Boolean,
    private val onGoodbye: (String?) -> Unit,
) : IBridgeEventListener.Stub() {
    internal val batches = Channel<EventBatch>(128)
    @Volatile private var closed = false
    override fun onEvents(eventBatchJson: String?) {
        if (closed || !isCurrent()) return
        val batch = BridgeCodec.decodeBatch(eventBatchJson).valueOrNull() ?: return
        if (batch.bootId != bootId || batch.subscriptionToken != token) return
        if (closed || !isCurrent()) return
        if (batches.trySend(batch).isFailure) {
            close()
            onGoodbye("{\"ok\":false,\"error\":{\"code\":\"BRIDGE_UNREACHABLE\",\"message\":\"Event delivery overflow; reconnect to recover\"}}")
        }
    }
    override fun onBridgeGoodbye(reasonJson: String?) {
        if (closed || !isCurrent()) return
        close()
        onGoodbye(reasonJson)
    }
    fun close() { closed = true; batches.cancel() }
}
