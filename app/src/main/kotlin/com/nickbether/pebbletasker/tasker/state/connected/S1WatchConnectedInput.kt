package com.nickbether.pebbletasker.tasker.state.connected

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

/**
 * S1 — Pebble Watch Connected (STATE). FINAL DESIGN §2.2. READY on today's bridge.
 *
 * Active when the bridge's getState snapshot lists a connected watch (optionally a specific [serial]).
 * StateData lists only connected devices today (StateProvider:19-30), so "serial present" == connected.
 *
 * [serial] is a String? safety-convention field (FIX C10) — blank/null means "any watch", a value
 * matches WatchRef.serial OR address, and a %var resolves before the runner reads it.
 */
@TaskerInputRoot
class S1WatchConnectedInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "pb_lbl_serial")
    var serial: String? = null,
)
