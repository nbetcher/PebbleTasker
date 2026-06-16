package com.nickbether.pebbletasker.tasker.event.battery

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper

/**
 * E4 helper. Plain TaskerPluginConfigHelper base (FIX C7); supplies the three *Class members and a
 * dynamic blurb documenting the %pb_json sub-paths (Tasker's picker only suggests %pb_json itself).
 */
class BatteryHelper(config: TaskerPluginConfig<BatteryFilter>) :
    PebbleEventHelper<BatteryFilter, BatteryOutput, BatteryRunner>(config) {

    override val inputClass = BatteryFilter::class.java
    override val outputClass = BatteryOutput::class.java
    override val runnerClass = BatteryRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<BatteryFilter>, blurbBuilder: StringBuilder) {
        val f = input.regular
        val serial = f.serial?.takeIf { it.isNotBlank() } ?: "any watch"
        blurbBuilder.append("Fires when battery is ")
            .append(f.direction ?: "below")
            .append(' ')
            .append(f.threshold ?: "20")
            .append("% on ")
            .append(serial)
            .append(".\nOutputs: %pb_battery %pb_direction + identity, and %pb_json")
            .append(" (.battery .direction .serial .name).")
    }
}
