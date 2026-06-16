package com.nickbether.pebbletasker.tasker.event.connected

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

/**
 * E1 — Pebble Watch Connected filter (READY today). Blank serial = any watch.
 */
@TaskerInputRoot
class ConnectedFilter @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "pb_evt_lbl_serial")
    var serial: String? = null,
)
