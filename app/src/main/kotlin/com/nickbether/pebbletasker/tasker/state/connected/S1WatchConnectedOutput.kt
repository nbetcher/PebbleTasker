package com.nickbether.pebbletasker.tasker.state.connected

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * S1 outputs. Dual-annotated (@TaskerInputRoot + @TaskerOutputObject) so the same class doubles as
 * the requestQuery pass-through payload (FIX C11). The watch-identity block is 1:1 with WatchRef.
 *
 * %pbl_json sub-paths: serial, name, nickname, model, fw, battery, address, connected, connected_count.
 */
@TaskerInputRoot
@TaskerOutputObject()
class S1WatchConnectedOutput @JvmOverloads constructor(
    @field:TaskerInputField("pb_json")
    @get:TaskerOutputVariable(PbVars.JSON, labelResIdName = "pb_lbl_json")
    val pbJson: String? = null,

    @field:TaskerInputField("connected")
    @get:TaskerOutputVariable(PbVars.CONNECTED, labelResIdName = "pb_lbl_connected")
    val connected: String? = null,

    @field:TaskerInputField("connected_count")
    @get:TaskerOutputVariable(PbVars.CONNECTED_COUNT, labelResIdName = "pb_lbl_connected_count")
    val connectedCount: String? = null,

    // --- watch identity block (1:1 with WatchRef) ---
    @field:TaskerInputField("serial")
    @get:TaskerOutputVariable(PbVars.SERIAL, labelResIdName = "pb_lbl_serial")
    val serial: String? = null,

    @field:TaskerInputField("name")
    @get:TaskerOutputVariable(PbVars.NAME, labelResIdName = "pb_lbl_name")
    val name: String? = null,

    @field:TaskerInputField("nickname")
    @get:TaskerOutputVariable(PbVars.NICKNAME, labelResIdName = "pb_lbl_nickname")
    val nickname: String? = null,

    @field:TaskerInputField("model")
    @get:TaskerOutputVariable(PbVars.MODEL, labelResIdName = "pb_lbl_model")
    val model: String? = null,

    @field:TaskerInputField("fw")
    @get:TaskerOutputVariable(PbVars.FW, labelResIdName = "pb_lbl_fw")
    val fw: String? = null,

    @field:TaskerInputField("battery")
    @get:TaskerOutputVariable(PbVars.BATTERY, labelResIdName = "pb_lbl_battery")
    val battery: String? = null,

    @field:TaskerInputField("address")
    @get:TaskerOutputVariable(PbVars.ADDRESS, labelResIdName = "pb_lbl_address")
    val address: String? = null,
)
