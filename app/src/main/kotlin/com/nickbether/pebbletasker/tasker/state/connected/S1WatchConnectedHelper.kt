package com.nickbether.pebbletasker.tasker.state.connected

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.tasker.base.PebbleStateHelper

/**
 * S1 helper. Plain TaskerPluginConfigHelper base (FIX C7) — supplies the three *Class members and a
 * dynamic blurb. The event-vs-state distinction lives in the runner base + manifest intent-filter,
 * not here.
 */
class S1WatchConnectedHelper(config: TaskerPluginConfig<S1WatchConnectedInput>) :
    PebbleStateHelper<S1WatchConnectedInput, S1WatchConnectedOutput, S1WatchConnectedRunner>(config) {

    override val inputClass = S1WatchConnectedInput::class.java
    override val outputClass = S1WatchConnectedOutput::class.java
    override val runnerClass = S1WatchConnectedRunner::class.java

    override fun addToStringBlurb(
        input: TaskerInput<S1WatchConnectedInput>,
        blurbBuilder: StringBuilder,
    ) {
        val serial = input.regular.serial
        blurbBuilder.append(
            if (serial.isNullOrBlank()) "Any Pebble watch is connected"
            else "Pebble watch $serial is connected",
        )
    }
}
