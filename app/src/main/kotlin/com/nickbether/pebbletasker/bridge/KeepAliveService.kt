package com.nickbether.pebbletasker.bridge

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.nickbether.pebbletasker.log.PLog

/**
 * Liveness anchor for the reverse-bind tether.
 *
 * The Pebble app (which is already alive holding the watch's BLE link) binds to this exported service
 * with BIND_AUTO_CREATE while a watch is connected. That does two things:
 *   1. If Android had killed this plugin process, the bind **revives** it (BIND_AUTO_CREATE starts the
 *      process → PebbleTaskerApp.onCreate → BridgeClient warms → we re-register for events).
 *   2. The inbound binding from the higher-priority app keeps this process out of the cached/killable
 *      state for the duration of the connection, so watch events keep reaching Tasker.
 *
 * This is what actually fixes "watch state fires rarely" — no foreground service, no persistent
 * notification, no CompanionDeviceManager.
 *
 * SECURITY — intentional asymmetry (per the agreed design): this service **auto-accepts any binder**
 * and exposes NOTHING sensitive. It carries no data and offers no commands; binding merely keeps us
 * warm. All verification lives on the app side (it only tethers consented clients whose signing cert it
 * re-checks). The worst a rogue app could do by binding here is keep us running (waste a little
 * battery), so there is deliberately no caller check.
 */
class KeepAliveService : Service() {

    private val binder = Binder()

    override fun onCreate() {
        super.onCreate()
        PLog.i { "keepalive: onCreate — process warmed by tether; ensuring bridge is up" }
        // The process starting already ran PebbleTaskerApp.onCreate -> BridgeClient.init; call start()
        // defensively (it is idempotent) so a bind after a transient stop re-establishes the listener.
        runCatching { BridgeClient.get(applicationContext).start() }
    }

    override fun onBind(intent: Intent?): IBinder {
        PLog.i { "keepalive: onBind from ${intent?.getPackage() ?: "?"}" }
        runCatching { BridgeClient.get(applicationContext).start() }
        return binder
    }

    override fun onDestroy() {
        PLog.i { "keepalive: onDestroy — tether released" }
        super.onDestroy()
    }

    companion object {
        /** Action the Pebble app resolves + binds to keep this client alive. MUST match the app side. */
        const val ACTION_KEEP_ALIVE = "coredevices.coreapp.automation.KEEP_ALIVE"
    }
}
