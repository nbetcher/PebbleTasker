package com.nickbether.pebbletasker.tasker.state.devconn

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * S3 outputs. %pbl_json sub-paths: dev_enabled, transport.
 */
@TaskerInputRoot
@TaskerOutputObject()
class S3DevConnectionOutput @JvmOverloads constructor(
    @field:TaskerInputField("pb_json")
    @get:TaskerOutputVariable(PbVars.JSON, labelResIdName = "pb_lbl_json")
    val pbJson: String? = null,

    @field:TaskerInputField("dev_enabled")
    @get:TaskerOutputVariable(PbVars.DEV_ENABLED, labelResIdName = "pb_lbl_dev_enabled")
    val devEnabled: String? = null,

    @field:TaskerInputField("transport")
    val transport: String? = null,
)
