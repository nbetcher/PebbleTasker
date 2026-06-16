package com.nickbether.pebbletasker.tasker.state

import com.nickbether.pebbletasker.cache.EventRouter
import com.nickbether.pebbletasker.tasker.state.bluetooth.S6BluetoothActivity
import com.nickbether.pebbletasker.tasker.state.connected.S1WatchConnectedActivity
import com.nickbether.pebbletasker.tasker.state.devconn.S3DevConnectionActivity
import com.nickbether.pebbletasker.tasker.state.dnd.S2DndActivity
import com.nickbether.pebbletasker.tasker.state.firmware.S5FirmwareUpdatingActivity
import com.nickbether.pebbletasker.tasker.state.watchface.S4WatchfaceActivity

/**
 * Wires the change-push fan-out for all 6 STATE plugins (FINAL DESIGN §2.2 / §3.7).
 *
 * State conditions are re-evaluated by Tasker only when something calls `requestQuery` on their
 * config-activity class. Registering each state's Activity against the bridge event type(s) that can
 * change its answer makes a single incoming event re-query the matching states immediately — no
 * polling. [EventRouter] dedupes by (bootId,seq) upstream, so a state never double-evaluates.
 *
 * Call [registerAll] once at process start. The integrator invokes this from PebbleTaskerApp.onCreate
 * (alongside the event-plugin registrations) — it is idempotent (EventRouter uses a set).
 *
 * Mapping rationale:
 *  - S1 Watch Connected   <- watch.connected / watch.disconnected / watch.state
 *  - S2 DND / Quiet Time  <- watch.connected / watch.disconnected (re-query on attach; getState ext)
 *  - S3 Dev Connection On <- dev.state
 *  - S4 Active Watchface  <- apps.run_state (running-app/face change) + watch.connected
 *  - S5 Firmware Updating <- fw.status
 *  - S6 Bluetooth On      <- bt.state
 */
object StateRegistrations {

    fun registerAll() {
        EventRouter.register(
            S1WatchConnectedActivity::class.java,
            StateSupport.TYPE_WATCH_CONNECTED,
            StateSupport.TYPE_WATCH_DISCONNECTED,
            StateSupport.TYPE_WATCH_STATE,
        )
        EventRouter.register(
            S2DndActivity::class.java,
            StateSupport.TYPE_WATCH_CONNECTED,
            StateSupport.TYPE_WATCH_DISCONNECTED,
        )
        EventRouter.register(
            S3DevConnectionActivity::class.java,
            StateSupport.TYPE_DEV_STATE,
        )
        EventRouter.register(
            S4WatchfaceActivity::class.java,
            StateSupport.TYPE_APPS_RUN_STATE,
            StateSupport.TYPE_WATCH_CONNECTED,
        )
        EventRouter.register(
            S5FirmwareUpdatingActivity::class.java,
            StateSupport.TYPE_FW_STATUS,
        )
        EventRouter.register(
            S6BluetoothActivity::class.java,
            StateSupport.TYPE_BT_STATE,
        )
    }
}
