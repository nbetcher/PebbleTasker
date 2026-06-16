package com.nickbether.pebbletasker.tasker.state.devconn

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.tasker.base.PebbleStateHelper

class S3DevConnectionHelper(config: TaskerPluginConfig<S3DevConnectionInput>) :
    PebbleStateHelper<S3DevConnectionInput, S3DevConnectionOutput, S3DevConnectionRunner>(config) {

    override val inputClass = S3DevConnectionInput::class.java
    override val outputClass = S3DevConnectionOutput::class.java
    override val runnerClass = S3DevConnectionRunner::class.java

    override fun addToStringBlurb(
        input: TaskerInput<S3DevConnectionInput>,
        blurbBuilder: StringBuilder,
    ) {
        blurbBuilder.append("Pebble developer connection is on")
    }
}
