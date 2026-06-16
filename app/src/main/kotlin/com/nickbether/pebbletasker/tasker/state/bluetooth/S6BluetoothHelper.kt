package com.nickbether.pebbletasker.tasker.state.bluetooth

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.tasker.base.PebbleStateHelper

class S6BluetoothHelper(config: TaskerPluginConfig<S6BluetoothInput>) :
    PebbleStateHelper<S6BluetoothInput, S6BluetoothOutput, S6BluetoothRunner>(config) {

    override val inputClass = S6BluetoothInput::class.java
    override val outputClass = S6BluetoothOutput::class.java
    override val runnerClass = S6BluetoothRunner::class.java

    override fun addToStringBlurb(
        input: TaskerInput<S6BluetoothInput>,
        blurbBuilder: StringBuilder,
    ) {
        blurbBuilder.append("Bluetooth is on (as seen by the Pebble app)")
    }
}
