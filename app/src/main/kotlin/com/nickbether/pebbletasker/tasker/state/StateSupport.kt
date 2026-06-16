package com.nickbether.pebbletasker.tasker.state

import android.content.Context
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.dto.StateResult
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.EventCache

/**
 * Shared helpers for the 6 STATE plugins (FINAL DESIGN §2.2).
 *
 * State conditions answer "is X true right now?" on demand. Two backing sources, both read here:
 *   1. getState  — a live snapshot query (BridgeClient.getStateBlocking). Used by S1 (watch
 *      connected) which works on TODAY'S bridge, and by S2/S4 which read the append-only EXTENDED
 *      StateData fields the bridge does not emit yet (so they resolve to Unknown until it does).
 *   2. EventCache — the last cached event of a backing type (S3 dev.state, S5 fw.status, S6 bt.state).
 *
 * Reliability rule (FIX C8): when the backing source can't answer (bridge unbound, capability
 * missing, no cached event yet) a state runner returns **Unknown**, never Unsatisfied — so a Tasker
 * state context does not flap to "false" on a transient gap.
 *
 * Change-push: the state config activities register against their backing event type via
 * [com.nickbether.pebbletasker.cache.EventRouter] so an incoming event re-queries them immediately
 * (see [StateRegistrations]). getState-only states (S1/S2/S4) ALSO register against watch.connected /
 * watch.disconnected / fw.status so connect/disconnect/face-change pushes re-evaluate them.
 *
 * No exotic state is held here — every accessor is a pure read off the singletons.
 */
internal object StateSupport {

    /** Backing event types in [EventCache] the states subscribe to / read. */
    const val TYPE_WATCH_CONNECTED = "watch.connected"
    const val TYPE_WATCH_DISCONNECTED = "watch.disconnected"
    const val TYPE_WATCH_STATE = "watch.state"
    const val TYPE_DEV_STATE = "dev.state"
    const val TYPE_FW_STATUS = "fw.status"
    const val TYPE_BT_STATE = "bt.state"
    const val TYPE_APPS_RUN_STATE = "apps.run_state"

    /** Blocking getState for the runner's IntentService thread. Err -> caller maps to Unknown. */
    fun queryState(context: Context): BridgeResult<StateResult> =
        BridgeClient.get(context).getStateBlocking()

    /** Latest cached event of [type], or null if the bridge never reported it this boot. */
    fun cached(context: Context, type: String): CachedEvent? =
        runCatching { EventCache.get(context).latest(type) }.getOrNull()

    /**
     * Find the watch in a [StateResult] matching [serial] (matches WatchRef.serial OR address). When
     * [serial] is blank/null, returns the first connected watch (StateData only lists connected
     * devices today — StateProvider:19-30).
     */
    fun matchWatch(state: StateResult, serial: String?): WatchRef? {
        val watches = state.data.watches
        if (serial.isNullOrBlank()) return watches.firstOrNull()
        return watches.firstOrNull { it.serial == serial || it.address == serial }
    }

    /** True if any watch (or the [serial]-matched watch) is present in the snapshot. */
    fun isConnected(state: StateResult, serial: String?): Boolean = matchWatch(state, serial) != null

    /** Number of connected watches in the snapshot. */
    fun connectedCount(state: StateResult): Int = state.data.watches.size

    /**
     * Read an EXTENDED StateData field that the bridge does not emit yet. The plugin-side StateData
     * DTO is watches-only today, so these always return null until the bridge advertises
     * `state.extended` and the DTO grows the field. Returning null -> the runner yields Unknown.
     *
     * Centralised here so S2/S4 share one "not available yet" path and light up together when the
     * bridge catches up (the implementer extends StateData + this accessor, append-only).
     */
    fun extendedFieldOrNull(@Suppress("UNUSED_PARAMETER") state: StateResult, key: String): String? {
        // No extended fields on today's StateData. Append reads here when the DTO grows
        // (connected, dnd, runningAppUuid, activeWatchface, btEnabled, firmwareUpdating).
        return when (key) {
            else -> null
        }
    }

    /** "true"/"false" of a Boolean for %pb_* outputs (Tasker treats these as truthy strings). */
    fun boolStr(b: Boolean): String = if (b) "true" else "false"
}
