package com.nickbether.pebbletasker.tasker.state.firmware

import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.databinding.ActivityStateFirmwareBinding
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity
import com.nickbether.pebbletasker.tasker.state.WatchBrowse

/**
 * S5 config activity — Pebble Firmware Updating (STATE).
 * Manifest intent-filter: com.twofortyfouram.locale.intent.action.EDIT_CONDITION.
 */
class S5FirmwareUpdatingActivity : PebbleConfigActivity<
    S5FirmwareUpdatingInput,
    S5FirmwareUpdatingOutput,
    S5FirmwareUpdatingRunner,
    S5FirmwareUpdatingHelper,
    ActivityStateFirmwareBinding,
    >() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityStateFirmwareBinding =
        ActivityStateFirmwareBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<S5FirmwareUpdatingInput>) =
        S5FirmwareUpdatingHelper(config)

    override fun onConfigCreated(binding: ActivityStateFirmwareBinding) {
        WatchBrowse.attach(binding.pbFieldSerial, binding.pbInputSerial)
    }

    override fun assignFromInput(input: TaskerInput<S5FirmwareUpdatingInput>) {
        binding?.pbInputSerial?.setText(input.regular.serial.orEmpty())
    }

    override val inputForTasker: TaskerInput<S5FirmwareUpdatingInput>
        get() = TaskerInput(
            S5FirmwareUpdatingInput(
                serial = binding?.pbInputSerial?.text?.toString()?.trim()?.ifBlank { null },
            ),
        )
}
