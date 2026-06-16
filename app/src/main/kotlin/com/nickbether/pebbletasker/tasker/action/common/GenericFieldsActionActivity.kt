package com.nickbether.pebbletasker.tasker.action.common

import android.view.LayoutInflater
import android.view.View
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import com.nickbether.pebbletasker.databinding.ActivityActionGenericBinding

/**
 * Config-activity base for actions whose inputs are 1–4 plain %var-capable text fields
 * (Set Quick Launch, Set Watch Pref, Insert/Delete Timeline Pin, Take Screenshot, Mute App —
 * FINAL DESIGN §2.3), driven by [ActivityActionGenericBinding].
 *
 * Subclasses describe their fields via [fields] (hint res + which one is the watch `serial`) and map
 * the field VALUES <-> the plugin input via [makeInput] / [valuesOf]. Field 1 is conventionally the
 * watch serial and gets the browse-picker; set [FieldSpec.isSerial] to enable it on a different slot.
 * Unused slots are hidden. An optional [hintRes] shows a helper line under the fields.
 */
abstract class GenericFieldsActionActivity<
    TInput : Any,
    TOutput : Any,
    TRunner : TaskerPluginRunner<TInput, TOutput>,
    THelper : TaskerPluginConfigHelper<TInput, TOutput, TRunner>,
    > : ActionConfigActivity<TInput, TOutput, TRunner, THelper, ActivityActionGenericBinding>() {

    /** One configured field slot. */
    data class FieldSpec(
        val hintRes: Int,
        val isSerial: Boolean = false,
        val multiline: Boolean = false,
    )

    protected abstract val titleRes: Int
    protected abstract val descRes: Int
    protected open val hintRes: Int? = null

    /** 1..4 field specs, in display order. */
    protected abstract val fields: List<FieldSpec>

    /** Build the plugin input from the (up to 4) field values (index 0..3; null when absent/blank). */
    protected abstract fun makeInput(values: List<String?>): TInput

    /** Read the (up to 4) field values out of a plugin input, in the same order as [fields]. */
    protected abstract fun valuesOf(input: TInput): List<String?>

    override fun inflateBinding(inflater: LayoutInflater): ActivityActionGenericBinding =
        ActivityActionGenericBinding.inflate(inflater)

    private fun layouts(b: ActivityActionGenericBinding): List<TextInputLayout> =
        listOf(b.layoutField1, b.layoutField2, b.layoutField3, b.layoutField4)

    private fun edits(b: ActivityActionGenericBinding): List<TextInputEditText> =
        listOf(b.editField1, b.editField2, b.editField3, b.editField4)

    override fun onConfigCreated(binding: ActivityActionGenericBinding) {
        super.onConfigCreated(binding)
        binding.txtTitle.setText(titleRes)
        binding.txtDesc.setText(descRes)
        hintRes?.let {
            binding.txtHint.setText(it)
            binding.txtHint.visibility = View.VISIBLE
        }
        val ls = layouts(binding)
        val es = edits(binding)
        for (i in 0 until 4) {
            val spec = fields.getOrNull(i)
            if (spec == null) {
                ls[i].visibility = View.GONE
                continue
            }
            ls[i].visibility = View.VISIBLE
            ls[i].hint = getString(spec.hintRes)
            if (spec.multiline) {
                es[i].inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                es[i].setSingleLine(false)
                es[i].minLines = 2
                es[i].gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }
            if (spec.isSerial) wireWatchBrowse(ls[i])
        }
    }

    override fun assignFromInput(input: TaskerInput<TInput>) {
        val b = binding ?: return
        val values = valuesOf(input.regular)
        val es = edits(b)
        for (i in 0 until 4) es[i].setText(values.getOrNull(i))
    }

    override val inputForTasker: TaskerInput<TInput>
        get() {
            val es = binding?.let { edits(it) }
            val values = (0 until 4).map { i ->
                es?.getOrNull(i)?.text?.toString()?.ifBlank { null }
            }
            return TaskerInput(makeInput(values))
        }
}
