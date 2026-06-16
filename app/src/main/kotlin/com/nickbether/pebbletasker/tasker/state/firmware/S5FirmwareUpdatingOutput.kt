package com.nickbether.pebbletasker.tasker.state.firmware

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * S5 outputs. %pb_json sub-paths: fw_progress, fw_status, serial.
 */
@TaskerInputRoot
@TaskerOutputObject()
class S5FirmwareUpdatingOutput @JvmOverloads constructor(
    @field:TaskerInputField("pb_json")
    @get:TaskerOutputVariable(PbVars.JSON, labelResIdName = "pb_lbl_json")
    val pbJson: String? = null,

    @field:TaskerInputField("fw_progress")
    @get:TaskerOutputVariable(PbVars.FW_PROGRESS, labelResIdName = "pb_lbl_fw_progress")
    val fwProgress: String? = null,

    @field:TaskerInputField("fw_status")
    @get:TaskerOutputVariable(PbVars.FW_STATUS, labelResIdName = "pb_lbl_fw_status")
    val fwStatus: String? = null,

    @field:TaskerInputField("serial")
    @get:TaskerOutputVariable(PbVars.SERIAL, labelResIdName = "pb_lbl_serial")
    val serial: String? = null,
)
