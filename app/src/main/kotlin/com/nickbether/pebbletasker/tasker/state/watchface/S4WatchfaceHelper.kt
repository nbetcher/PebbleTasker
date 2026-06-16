package com.nickbether.pebbletasker.tasker.state.watchface

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.tasker.base.PebbleStateHelper

class S4WatchfaceHelper(config: TaskerPluginConfig<S4WatchfaceInput>) :
    PebbleStateHelper<S4WatchfaceInput, S4WatchfaceOutput, S4WatchfaceRunner>(config) {

    override val inputClass = S4WatchfaceInput::class.java
    override val outputClass = S4WatchfaceOutput::class.java
    override val runnerClass = S4WatchfaceRunner::class.java

    override fun addToStringBlurb(
        input: TaskerInput<S4WatchfaceInput>,
        blurbBuilder: StringBuilder,
    ) {
        val uuid = input.regular.uuid
        blurbBuilder.append(
            if (uuid.isNullOrBlank()) "A Pebble watchface is active"
            else "Pebble watchface $uuid is active",
        )
    }
}
