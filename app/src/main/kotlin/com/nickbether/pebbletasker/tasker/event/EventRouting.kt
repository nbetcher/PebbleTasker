package com.nickbether.pebbletasker.tasker.event

import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.EventRouter
import com.nickbether.pebbletasker.tasker.event.appchanged.AppChangedActivity
import com.nickbether.pebbletasker.tasker.event.appmsg.AppMessageActivity
import com.nickbether.pebbletasker.tasker.event.battery.BatteryActivity
import com.nickbether.pebbletasker.tasker.event.bridgeerror.BridgeErrorActivity
import com.nickbether.pebbletasker.tasker.event.call.CallActivity
import com.nickbether.pebbletasker.tasker.event.connected.ConnectedActivity
import com.nickbether.pebbletasker.tasker.event.connfailed.ConnFailedActivity
import com.nickbether.pebbletasker.tasker.event.devconn.DevConnActivity
import com.nickbether.pebbletasker.tasker.event.disconnected.DisconnectedActivity
import com.nickbether.pebbletasker.tasker.event.firmware.FirmwareActivity
import com.nickbether.pebbletasker.tasker.event.health.HealthActivity
import com.nickbether.pebbletasker.tasker.event.music.MusicActivity
import com.nickbether.pebbletasker.tasker.event.notifaction.NotifActionActivity
import com.nickbether.pebbletasker.tasker.event.notifsent.NotifSentActivity
import com.nickbether.pebbletasker.tasker.event.timeline.TimelineActivity

/**
 * Central registration of every event plugin's config activity against the bridge event `type`(s)
 * that should re-evaluate it (FINAL DESIGN §2.1 push path).
 *
 * Call [registerAll] once at process start (from PebbleTaskerApp.onCreate, after BridgeClient.init).
 * After this, when BridgeListener.onEvents ingests an event into EventCache, EventRouter.routeAll
 * fans the new (bootId,seq) out to exactly the activities registered here, which `requestQuery`s
 * Tasker so matching profiles evaluate immediately — no polling.
 *
 * The frozen bridge `type` strings live here as constants so collectors/runners agree on one source.
 */
object EventRouting {

    // --- frozen bridge event-type strings (must match the bridge collectors & each Runner.eventType) ---
    const val TYPE_CONNECTED = "watch.connected"
    const val TYPE_DISCONNECTED = "watch.disconnected"
    const val TYPE_CONN_FAILED = "watch.state"
    const val TYPE_BATTERY = "watch.battery"
    const val TYPE_NOTIF_SENT = "notif.sent"
    const val TYPE_NOTIF_ACTION = "notif.action"
    const val TYPE_APP_CHANGED = "apps.run_state"
    const val TYPE_APPMSG = "appmsg.received"
    const val TYPE_TIMELINE = "timeline.action"
    const val TYPE_MUSIC = "media.command"
    const val TYPE_CALL = "calls.state"
    const val TYPE_FIRMWARE = "fw.status"
    const val TYPE_HEALTH = "health.updated"
    const val TYPE_DEV = "dev.state"
    const val TYPE_SYSTEM_ERROR = "system.error"

    @Volatile private var registered = false

    /** Idempotent. Wires all 15 event config activities into [EventRouter]. */
    @Synchronized
    fun registerAll() {
        if (registered) return
        registered = true

        EventRouter.register(ConnectedActivity::class.java, TYPE_CONNECTED)
        EventRouter.register(DisconnectedActivity::class.java, TYPE_DISCONNECTED)
        EventRouter.register(ConnFailedActivity::class.java, TYPE_CONN_FAILED)
        EventRouter.register(BatteryActivity::class.java, TYPE_BATTERY)
        EventRouter.register(NotifSentActivity::class.java, TYPE_NOTIF_SENT)
        EventRouter.register(NotifActionActivity::class.java, TYPE_NOTIF_ACTION)
        EventRouter.register(AppChangedActivity::class.java, TYPE_APP_CHANGED)
        EventRouter.register(AppMessageActivity::class.java, TYPE_APPMSG)
        EventRouter.register(TimelineActivity::class.java, TYPE_TIMELINE)
        EventRouter.register(MusicActivity::class.java, TYPE_MUSIC)
        EventRouter.register(CallActivity::class.java, TYPE_CALL)
        EventRouter.register(FirmwareActivity::class.java, TYPE_FIRMWARE)
        EventRouter.register(HealthActivity::class.java, TYPE_HEALTH)
        EventRouter.register(DevConnActivity::class.java, TYPE_DEV)
        // E15 fires on a real bridge error OR a client-synthesized gap (EventCache.TYPE_GAP).
        EventRouter.register(
            BridgeErrorActivity::class.java,
            TYPE_SYSTEM_ERROR,
            CachedEvent.TYPE_GAP,
        )
    }
}
