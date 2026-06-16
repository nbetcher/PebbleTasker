package com.nickbether.pebbletasker.tasker.state.bluetooth

import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.databinding.ActivityStateBluetoothBinding
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity

/**
 * S6 config activity — Pebble Bluetooth On (STATE). No input fields.
 * Manifest intent-filter: com.twofortyfouram.locale.intent.action.EDIT_CONDITION.
 */
class S6BluetoothActivity : PebbleConfigActivity<
    S6BluetoothInput,
    S6BluetoothOutput,
    S6BluetoothRunner,
    S6BluetoothHelper,
    ActivityStateBluetoothBinding,
    >() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityStateBluetoothBinding =
        ActivityStateBluetoothBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<S6BluetoothInput>) = S6BluetoothHelper(config)

    override fun assignFromInput(input: TaskerInput<S6BluetoothInput>) {
        // No fields to populate.
    }

    override val inputForTasker: TaskerInput<S6BluetoothInput>
        get() = TaskerInput(S6BluetoothInput())
}
