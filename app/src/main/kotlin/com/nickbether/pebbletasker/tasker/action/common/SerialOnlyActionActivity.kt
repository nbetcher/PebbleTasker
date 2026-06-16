package com.nickbether.pebbletasker.tasker.action.common

import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import com.nickbether.pebbletasker.databinding.ActivityActionSerialOnlyBinding

/**
 * Reusable config-activity base for actions whose only input is the watch `serial`
 * (Connect, Disconnect, Send Ping, Get Health Snapshot, Check Firmware Update — FINAL DESIGN §2.3).
 *
 * Subclasses provide:
 *   - [titleRes] / [descRes]               header strings shown in the card;
 *   - [warnRes]                            optional red warning line (null = hidden);
 *   - [getNewHelper] / [makeInput] / [serialOf]   the plugin glue.
 *
 * The single `serial` field receives the variable picker (end icon, via PebbleConfigActivity) and the
 * watch browse-picker (start icon, via ActionConfigActivity.wireWatchBrowse). FLAG_SECURE is applied
 * when [isSensitive] is true (Disconnect/health are sensitive).
 */
abstract class SerialOnlyActionActivity<
    TInput : Any,
    TOutput : Any,
    TRunner : TaskerPluginRunner<TInput, TOutput>,
    THelper : TaskerPluginConfigHelper<TInput, TOutput, TRunner>,
    > : ActionConfigActivity<TInput, TOutput, TRunner, THelper, ActivityActionSerialOnlyBinding>() {

    protected abstract val titleRes: Int
    protected abstract val descRes: Int
    protected open val warnRes: Int? = null

    /** Build the plugin input from a serial (blank -> null = active watch). */
    protected abstract fun makeInput(serial: String?): TInput

    /** Read the serial out of a plugin input (for assignFromInput). */
    protected abstract fun serialOf(input: TInput): String?

    override fun inflateBinding(inflater: LayoutInflater): ActivityActionSerialOnlyBinding =
        ActivityActionSerialOnlyBinding.inflate(inflater)

    override fun onConfigCreated(binding: ActivityActionSerialOnlyBinding) {
        super.onConfigCreated(binding)
        binding.txtTitle.setText(titleRes)
        binding.txtDesc.setText(descRes)
        warnRes?.let {
            binding.txtWarn.setText(it)
            binding.txtWarn.visibility = android.view.View.VISIBLE
        }
        wireWatchBrowse(binding.layoutSerial)
    }

    override fun assignFromInput(input: TaskerInput<TInput>) {
        binding?.editSerial?.setText(serialOf(input.regular))
    }

    override val inputForTasker: TaskerInput<TInput>
        get() = TaskerInput(makeInput(binding?.editSerial?.text?.toString()?.ifBlank { null }))
}
