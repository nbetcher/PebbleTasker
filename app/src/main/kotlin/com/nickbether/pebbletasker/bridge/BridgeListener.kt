package com.nickbether.pebbletasker.bridge

import android.content.Context
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.EventCache
import com.nickbether.pebbletasker.log.PLog
import coredevices.coreapp.automation.IBridgeEventListener
import kotlinx.coroutines.channels.Channel

/**
 * The IBridgeEventListener.Stub the bridge pushes event batches to (FINAL DESIGN §3.3).
 *
 * CRITICAL Binder-thread contract:
 *   - onEvents() may run on a Binder POOL thread AND re-entrantly on our own
 *     registerEventListener() calling thread (the bridge replays missed events INLINE inside
 *     register, ListenerHub). It must therefore NEVER block, await, or round-trip back to the
 *     bridge dispatcher — doing so deadlocks the register() call.
 *   - So onEvents does the minimum, synchronously: envelope-decode, validate bootId, write the
 *     EventCache (in-memory write is non-blocking; disk persist is launched async inside the cache),
 *     and trySend the new-event set to [routedChannel]. Routing (requestQuery) and any heavier work
 *     happen on the app scope draining that channel, NOT here.
 *
 * onBridgeGoodbye() signals revocation/shutdown: we surface it via [onGoodbye] so the owning
 * BridgeConnection can clear the session and proactively schedule a rebind (events have stopped, so
 * waiting passively for "next need" may never fire — FINAL DESIGN §3.3 FIX C2-crit3).
 */
class BridgeListener(
    private val appContext: Context,
    /** Returns the bootId of the CURRENT live session, or null if none. Used to drop stale batches. */
    private val currentBootId: () -> String?,
    private val onGoodbye: (reasonJson: String?) -> Unit,
) : IBridgeEventListener.Stub() {

    /**
     * Routed-event batches drained by [BridgeClient] on the app scope, which calls EventRouter.
     * Unlimited buffer so a burst on the Binder thread never blocks the bridge; each element is the
     * exact set of NEW (deduped) events from one onEvents call.
     */
    val routedChannel: Channel<List<CachedEvent>> = Channel(capacity = Channel.UNLIMITED)

    private val cache get() = EventCache.get(appContext)

    override fun onEvents(eventBatchJson: String?) {
        // Decode + ingest, no blocking. Any parse failure is swallowed (untrusted-ish input).
        val batch = BridgeCodec.decodeBatch(eventBatchJson).valueOrNull()
        if (batch == null) {
            PLog.w { "listener: onEvents undecodable batch (len=${eventBatchJson?.length ?: 0})" }
            return
        }

        // Drop batches from a stale boot (we re-handshake and re-seed on boot change separately).
        val live = currentBootId()
        if (live != null && batch.bootId != live) {
            PLog.w { "listener: dropping stale-boot batch (batch=${batch.bootId} live=$live)" }
            return
        }

        val routed = cache.putBatch(batch.events)
        PLog.i {
            "listener: onEvents bootId=${batch.bootId} in=${batch.events.size} new=${routed.size}" +
                (if (routed.isNotEmpty()) " types=${routed.map { it.type }.distinct()}" else "")
        }
        if (routed.isNotEmpty()) {
            // Hand the exact new-event set to the app-scope drainer (honors no-work-on-Binder rule).
            routedChannel.trySend(routed)
        }
    }

    override fun onBridgeGoodbye(reasonJson: String?) {
        PLog.w { "listener: onBridgeGoodbye reason=$reasonJson" }
        onGoodbye(reasonJson)
    }
}
