package com.nickbether.pebbletasker

import android.app.Application
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.state.StateRegistrations

/**
 * Application entry point for the Pebble x Tasker plugin.
 *
 * Per FINAL DESIGN §1, this class:
 *  - warms the [BridgeClient] process singleton (sticky bind to coredevices.coreapp's BridgeService,
 *    handshake, register, and the routed-event drain loop);
 *  - (TODO, UI layer) creates notification channels used by onboarding/consent guidance.
 *
 * BridgeClient.init() is idempotent and non-blocking (all IPC runs on its own single-thread
 * dispatcher), so calling it here is safe and starts the connection eagerly so events flow as soon
 * as the bridge authorizes us.
 */
class PebbleTaskerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Single ServiceConnection on a 1-thread bridge dispatcher; sticky bind + 1s..60s backoff,
        // TOFU cert pin, handshake, listener registration, routed-event fan-out (FINAL DESIGN §3).
        BridgeClient.init(this)
        // Register every event plugin's config activity against its bridge event type(s) so ingested
        // events fan out to Tasker via requestQuery (FINAL DESIGN §2.1 push path). Idempotent.
        EventRouting.registerAll()
        // Register the 6 STATE plugins for change-push: an incoming event re-queries the matching
        // state condition immediately instead of waiting for Tasker's own poll (FINAL DESIGN §2.2).
        // Idempotent (EventRouter dedupes registrations via a set).
        StateRegistrations.registerAll()
        // TODO(ui): create notification channels for ConsentGuidance/onboarding (UI-layer concern).
    }
}
