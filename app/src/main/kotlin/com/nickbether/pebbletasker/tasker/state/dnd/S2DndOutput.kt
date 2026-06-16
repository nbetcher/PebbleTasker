package com.nickbether.pebbletasker.tasker.state.dnd

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * S2 outputs. %pb_json sub-paths: dnd, serial.
 */
@TaskerInputRoot
@TaskerOutputObject()
class S2DndOutput @JvmOverloads constructor(
    @field:TaskerInputField("pb_json")
    @get:TaskerOutputVariable(PbVars.JSON, labelResIdName = "pb_lbl_json")
    val pbJson: String? = null,

    @field:TaskerInputField("dnd")
    @get:TaskerOutputVariable(PbVars.DND, labelResIdName = "pb_lbl_dnd")
    val dnd: String? = null,

    @field:TaskerInputField("serial")
    @get:TaskerOutputVariable(PbVars.SERIAL, labelResIdName = "pb_lbl_serial")
    val serial: String? = null,
)
