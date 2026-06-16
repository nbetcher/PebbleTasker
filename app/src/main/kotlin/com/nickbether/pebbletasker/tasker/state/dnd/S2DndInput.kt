package com.nickbether.pebbletasker.tasker.state.dnd

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

/**
 * S2 — Pebble DND / Quiet Time (STATE). FINAL DESIGN §2.2. CAPABILITY-GATED (state.extended).
 *
 * Active when the watch's Do-Not-Disturb / Quiet Time is on. Backed by an EXTENDED StateData.dnd
 * field the bridge does not emit yet, so it resolves to Unknown until the bridge advertises
 * `state.extended`. Ships now (capability-gated) so saved configs aren't orphaned when it lights up.
 *
 * [serial] (String?, FIX C10): blank/null = active watch; a value matches WatchRef.serial OR address.
 */
@TaskerInputRoot
class S2DndInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "pb_lbl_serial")
    var serial: String? = null,
)
