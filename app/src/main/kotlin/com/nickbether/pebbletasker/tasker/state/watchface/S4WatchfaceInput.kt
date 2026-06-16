package com.nickbether.pebbletasker.tasker.state.watchface

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

/**
 * S4 — Pebble Active Watchface Is (STATE). FINAL DESIGN §2.2. CAPABILITY-GATED (state.extended).
 *
 * Active when the watch's current watchface matches [uuid]. Backed by an EXTENDED StateData
 * .activeWatchface the bridge does not emit yet -> Unknown until `state.extended`.
 *
 * [uuid] (String?, FIX C10): the target watchface UUID. Blank/null means "any non-default watchface"
 * is active (i.e. a watchface is running at all). A %var resolves before the runner reads it.
 */
@TaskerInputRoot
class S4WatchfaceInput @JvmOverloads constructor(
    @field:TaskerInputField("uuid", labelResIdName = "pb_lbl_uuid")
    var uuid: String? = null,
)
