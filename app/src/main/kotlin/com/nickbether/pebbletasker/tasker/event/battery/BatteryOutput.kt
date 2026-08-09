package com.nickbether.pebbletasker.tasker.event.battery

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * E4 output. Extends [BaseEventOutput] (identity + universal + %pbl_json + %pbl_battery) and adds the
 * event-specific %pbl_direction. Dual-annotated so it doubles as a pass-through payload (FIX C11).
 * @JvmOverloads is mandatory (FIX C14).
 */
@TaskerInputRoot
@TaskerOutputObject
class BatteryOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.DIRECTION)
    @field:TaskerInputField("pb_direction")
    var pbDirection: String? = null,
) : BaseEventOutput()
