package com.nickbether.pebbletasker.tasker.state.connected

import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.databinding.ActivityStateConnectedBinding
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity
import com.nickbether.pebbletasker.tasker.state.WatchBrowse

/**
 * S1 config activity — Pebble Watch Connected (STATE). Neon-Grid-themed via the base
 * PebbleConfigActivity (AppCompatActivity + Theme.NeonGrid). The base attaches the variable picker to
 * the serial field's END icon and reads Tasker's inbound relevant variables; here we add the
 * "browse watches" START-icon affordance and bridge the UI <-> input object.
 *
 * Manifest intent-filter: com.twofortyfouram.locale.intent.action.EDIT_CONDITION.
 */
class S1WatchConnectedActivity : PebbleConfigActivity<
    S1WatchConnectedInput,
    S1WatchConnectedOutput,
    S1WatchConnectedRunner,
    S1WatchConnectedHelper,
    ActivityStateConnectedBinding,
    >() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityStateConnectedBinding =
        ActivityStateConnectedBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<S1WatchConnectedInput>) =
        S1WatchConnectedHelper(config)

    override fun onConfigCreated(binding: ActivityStateConnectedBinding) {
        WatchBrowse.attach(binding.pbFieldSerial, binding.pbInputSerial)
    }

    override fun assignFromInput(input: TaskerInput<S1WatchConnectedInput>) {
        binding?.pbInputSerial?.setText(input.regular.serial.orEmpty())
    }

    override val inputForTasker: TaskerInput<S1WatchConnectedInput>
        get() = TaskerInput(
            S1WatchConnectedInput(
                serial = binding?.pbInputSerial?.text?.toString()?.trim()?.ifBlank { null },
            ),
        )
}
