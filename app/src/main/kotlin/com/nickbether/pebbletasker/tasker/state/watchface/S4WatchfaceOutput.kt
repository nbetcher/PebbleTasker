package com.nickbether.pebbletasker.tasker.state.watchface

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * S4 outputs. %pb_json sub-paths: watchface_uuid, watchface_name.
 */
@TaskerInputRoot
@TaskerOutputObject()
class S4WatchfaceOutput @JvmOverloads constructor(
    @field:TaskerInputField("pb_json")
    @get:TaskerOutputVariable(PbVars.JSON, labelResIdName = "pb_lbl_json")
    val pbJson: String? = null,

    @field:TaskerInputField("watchface_uuid")
    @get:TaskerOutputVariable(PbVars.WATCHFACE_UUID, labelResIdName = "pb_lbl_watchface_uuid")
    val watchfaceUuid: String? = null,

    @field:TaskerInputField("watchface_name")
    @get:TaskerOutputVariable(PbVars.WATCHFACE_NAME, labelResIdName = "pb_lbl_watchface_name")
    val watchfaceName: String? = null,
)
