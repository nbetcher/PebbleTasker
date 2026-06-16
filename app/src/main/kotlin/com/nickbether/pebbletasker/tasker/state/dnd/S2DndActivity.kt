package com.nickbether.pebbletasker.tasker.state.dnd

import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.databinding.ActivityStateDndBinding
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity
import com.nickbether.pebbletasker.tasker.state.WatchBrowse

/**
 * S2 config activity — Pebble DND / Quiet Time (STATE).
 * Manifest intent-filter: com.twofortyfouram.locale.intent.action.EDIT_CONDITION.
 */
class S2DndActivity : PebbleConfigActivity<
    S2DndInput,
    S2DndOutput,
    S2DndRunner,
    S2DndHelper,
    ActivityStateDndBinding,
    >() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityStateDndBinding =
        ActivityStateDndBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<S2DndInput>) = S2DndHelper(config)

    override fun onConfigCreated(binding: ActivityStateDndBinding) {
        WatchBrowse.attach(binding.pbFieldSerial, binding.pbInputSerial)
    }

    override fun assignFromInput(input: TaskerInput<S2DndInput>) {
        binding?.pbInputSerial?.setText(input.regular.serial.orEmpty())
    }

    override val inputForTasker: TaskerInput<S2DndInput>
        get() = TaskerInput(
            S2DndInput(
                serial = binding?.pbInputSerial?.text?.toString()?.trim()?.ifBlank { null },
            ),
        )
}
