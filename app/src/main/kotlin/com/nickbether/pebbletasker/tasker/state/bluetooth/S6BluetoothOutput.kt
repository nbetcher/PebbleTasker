package com.nickbether.pebbletasker.tasker.state.bluetooth

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * S6 outputs. %pbl_json sub-paths: bt_enabled.
 */
@TaskerInputRoot
@TaskerOutputObject()
class S6BluetoothOutput @JvmOverloads constructor(
    @field:TaskerInputField("pb_json")
    @get:TaskerOutputVariable(PbVars.JSON, labelResIdName = "pb_lbl_json")
    val pbJson: String? = null,

    @field:TaskerInputField("bt_enabled")
    @get:TaskerOutputVariable(PbVars.BT_ENABLED, labelResIdName = "pb_lbl_bt_enabled")
    val btEnabled: String? = null,
)
