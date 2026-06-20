package com.nickbether.pebbletasker.tasker.event.connected

import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.databinding.ActivityConfigSerialOnlyBinding
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity

/**
 * E1 config activity — reuses the shared serial-only layout; sets its own title/description in code.
 */
class ConnectedActivity :
    PebbleConfigActivity<ConnectedFilter, ConnectedOutput, ConnectedRunner, ConnectedHelper, ActivityConfigSerialOnlyBinding>() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityConfigSerialOnlyBinding =
        ActivityConfigSerialOnlyBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<ConnectedFilter>) = ConnectedHelper(config)

    override fun onConfigCreated(binding: ActivityConfigSerialOnlyBinding) {
        binding.pbTitle.setText(R.string.pb_evt_connected_title)
        binding.pbDesc.setText(R.string.pb_evt_connected_desc)
        // The serial field now carries its own "Any" + watches dropdown; the old button is redundant.
        binding.pbBtnPickWatch.visibility = android.view.View.GONE
        CriteriaDropdown.attachWatchSerial(binding.pbLayoutSerial)
    }

    override fun assignFromInput(input: TaskerInput<ConnectedFilter>) {
        binding?.pbEditSerial?.setText(input.regular.serial.orEmpty())
    }

    override val inputForTasker: TaskerInput<ConnectedFilter>
        get() = TaskerInput(
            ConnectedFilter(serial = binding?.pbEditSerial?.text?.toString()?.trim().orEmpty()),
        )
}
