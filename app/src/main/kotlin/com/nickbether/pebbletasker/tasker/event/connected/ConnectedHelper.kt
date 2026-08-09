package com.nickbether.pebbletasker.tasker.event.connected

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper

class ConnectedHelper(config: TaskerPluginConfig<ConnectedFilter>) :
    PebbleEventHelper<ConnectedFilter, ConnectedOutput, ConnectedRunner>(config) {

    override val inputClass = ConnectedFilter::class.java
    override val outputClass = ConnectedOutput::class.java
    override val runnerClass = ConnectedRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<ConnectedFilter>, blurbBuilder: StringBuilder) {
        val serial = input.regular.serial?.takeIf { it.isNotBlank() } ?: "any watch"
        blurbBuilder.append("Fires when ").append(serial).append(" connects.")
            .append("\nOutputs: identity block + %pbl_json.")
    }
}
