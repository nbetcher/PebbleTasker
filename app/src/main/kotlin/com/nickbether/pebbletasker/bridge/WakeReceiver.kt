package com.nickbether.pebbletasker.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Untrusted wake hint -> bind-and-sync (FINAL DESIGN §3.8).
 *
 * A wake is a CONTENTLESS, untrusted hint: it NEVER carries event data. On receipt we simply ensure
 * the bridge is bound and let the normal connect sequence (handshake -> register -> getEventsSince
 * liveness probe) recover any missed events. The recovery path's gap detection (bootId change OR
 * seq-discontinuity) lives in [EventCache]; we do not trust anything in the broadcast itself.
 *
 * Declared exported=false in the manifest. It is a hint optimizer, not a trust boundary.
 *
 * Because BridgeClient.start()/retryHandshake() do the real work asynchronously on the bridge
 * dispatcher, onReceive returns immediately without goAsync — the bound connection and its drain
 * loop keep the app alive long enough for the sync.
 */
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val client = BridgeClient.get(context)
        // ensureBound (idempotent) + force a fresh handshake so getEventsSince replays any gap.
        client.start()
        client.retryHandshake()
    }
}
