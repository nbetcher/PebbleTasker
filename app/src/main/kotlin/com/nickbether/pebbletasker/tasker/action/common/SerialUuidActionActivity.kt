package com.nickbether.pebbletasker.tasker.action.common

import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import com.nickbether.pebbletasker.databinding.ActivityActionSerialUuidBinding

/**
 * Reusable config-activity base for actions taking watch `serial` + an app/watchface `uuid`
 * (Launch App, Set Watchface — FINAL DESIGN §2.3).
 *
 * Both fields are %var-capable (variable end-icon from PebbleConfigActivity); serial also gets the
 * watch browse-picker start-icon.
 */
abstract class SerialUuidActionActivity<
    TInput : Any,
    TOutput : Any,
    TRunner : TaskerPluginRunner<TInput, TOutput>,
    THelper : TaskerPluginConfigHelper<TInput, TOutput, TRunner>,
    > : ActionConfigActivity<TInput, TOutput, TRunner, THelper, ActivityActionSerialUuidBinding>() {

    protected abstract val titleRes: Int
    protected abstract val descRes: Int

    protected abstract fun makeInput(serial: String?, uuid: String?): TInput
    protected abstract fun serialOf(input: TInput): String?
    protected abstract fun uuidOf(input: TInput): String?

    override fun inflateBinding(inflater: LayoutInflater): ActivityActionSerialUuidBinding =
        ActivityActionSerialUuidBinding.inflate(inflater)

    override fun onConfigCreated(binding: ActivityActionSerialUuidBinding) {
        super.onConfigCreated(binding)
        binding.txtTitle.setText(titleRes)
        binding.txtDesc.setText(descRes)
        wireWatchBrowse(binding.layoutSerial)
    }

    override fun assignFromInput(input: TaskerInput<TInput>) {
        binding?.editSerial?.setText(serialOf(input.regular))
        binding?.editUuid?.setText(uuidOf(input.regular))
    }

    override val inputForTasker: TaskerInput<TInput>
        get() = TaskerInput(
            makeInput(
                binding?.editSerial?.text?.toString()?.ifBlank { null },
                binding?.editUuid?.text?.toString()?.ifBlank { null },
            ),
        )
}
