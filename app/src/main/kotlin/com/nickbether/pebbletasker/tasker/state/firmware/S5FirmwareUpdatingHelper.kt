package com.nickbether.pebbletasker.tasker.state.firmware

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.tasker.base.PebbleStateHelper

class S5FirmwareUpdatingHelper(config: TaskerPluginConfig<S5FirmwareUpdatingInput>) :
    PebbleStateHelper<S5FirmwareUpdatingInput, S5FirmwareUpdatingOutput, S5FirmwareUpdatingRunner>(config) {

    override val inputClass = S5FirmwareUpdatingInput::class.java
    override val outputClass = S5FirmwareUpdatingOutput::class.java
    override val runnerClass = S5FirmwareUpdatingRunner::class.java

    override fun addToStringBlurb(
        input: TaskerInput<S5FirmwareUpdatingInput>,
        blurbBuilder: StringBuilder,
    ) {
        val serial = input.regular.serial
        blurbBuilder.append(
            if (serial.isNullOrBlank()) "A Pebble firmware update is in progress"
            else "Pebble $serial firmware update is in progress",
        )
    }
}
