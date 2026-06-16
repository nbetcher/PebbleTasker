package com.nickbether.pebbletasker.tasker.state.dnd

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.tasker.base.PebbleStateHelper

class S2DndHelper(config: TaskerPluginConfig<S2DndInput>) :
    PebbleStateHelper<S2DndInput, S2DndOutput, S2DndRunner>(config) {

    override val inputClass = S2DndInput::class.java
    override val outputClass = S2DndOutput::class.java
    override val runnerClass = S2DndRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<S2DndInput>, blurbBuilder: StringBuilder) {
        val serial = input.regular.serial
        blurbBuilder.append(
            if (serial.isNullOrBlank()) "Pebble Quiet Time / DND is on"
            else "Pebble $serial Quiet Time / DND is on",
        )
    }
}
