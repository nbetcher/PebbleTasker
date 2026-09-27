package com.nickbether.pebbletasker.tasker.action.common

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.databinding.ActivityConfigEventGenericBinding
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.event.GenericFieldBuilder

/**
 * Config base for the watch-control actions: a title, description, optional warning, and any number
 * of %var-capable fields, each optionally carrying a pick-list or live lookup on its start icon.
 * Values round-trip through a key -> string map.
 */
abstract class FormActionActivity<
    TInput : Any,
    TOutput : Any,
    TRunner : TaskerPluginRunner<TInput, TOutput>,
    THelper : TaskerPluginConfigHelper<TInput, TOutput, TRunner>,
    > : ActionConfigActivity<TInput, TOutput, TRunner, THelper, ActivityConfigEventGenericBinding>() {

    data class Field(
        val key: String,
        val label: CharSequence,
        /** Fixed choices (display label -> value). */
        val options: List<Pair<String, String>>? = null,
        val lookup: CriteriaDropdown.Source? = null,
        /** Shown under the field. */
        val helper: CharSequence? = null,
        val numeric: Boolean = false,
        val multiline: Boolean = false,
    )

    protected abstract val formTitle: CharSequence
    protected abstract val formDescription: CharSequence
    /** A prominent warning above the fields, or null. */
    protected open val formWarning: CharSequence? = null
    /** Output variables summary shown under the fields. */
    protected open val formOutputs: CharSequence? = null

    protected abstract fun buildFields(): List<Field>
    protected abstract fun buildInput(values: Map<String, String>): TInput
    protected abstract fun extractValues(input: TInput): Map<String, String>

    /** Called after fields exist, to wire extra behaviour (e.g. preference pickers). */
    protected open fun onFieldsBuilt(fields: Map<String, TextInputEditText>) {}

    protected val edits = LinkedHashMap<String, TextInputEditText>()
    private var pending: Map<String, String>? = null

    override fun inflateBinding(inflater: LayoutInflater) = ActivityConfigEventGenericBinding.inflate(inflater)

    override fun onConfigCreated(binding: ActivityConfigEventGenericBinding) {
        super.onConfigCreated(binding)
        binding.pbTitle.text = formTitle
        binding.pbDesc.text = formDescription
        binding.pbBtnPickWatch.visibility = View.GONE
        formWarning?.let { warning ->
            val tv = TextView(this).apply {
                text = warning
                setTextColor(ContextCompat.getColor(this@FormActionActivity, R.color.pb_warn_text))
                setBackgroundResource(R.drawable.bg_warn_bubble)
                val p = (12 * resources.displayMetrics.density).toInt()
                setPadding(p, p, p, p)
            }
            binding.pbFieldsContainer.addView(tv)
        }
        for (spec in buildFields()) {
            val edit = GenericFieldBuilder.addField(
                context = this,
                container = binding.pbFieldsContainer,
                hint = spec.label,
                numeric = spec.numeric,
                singleLine = !spec.multiline,
                options = spec.options,
                lookup = spec.lookup,
            )
            spec.helper?.let { (edit.parent?.parent as? TextInputLayout)?.helperText = it }
            edits[spec.key] = edit
        }
        binding.pbOutputs.text = formOutputs ?: ""
        binding.pbOutputs.visibility = if (formOutputs == null) View.GONE else View.VISIBLE
        attachVariablePickers(binding.root)
        pending?.let { apply(it); pending = null }
        onFieldsBuilt(edits)
    }

    override fun assignFromInput(input: TaskerInput<TInput>) {
        val saved = extractValues(input.regular)
        if (edits.isEmpty()) pending = saved else apply(saved)
    }

    private fun apply(values: Map<String, String>) {
        for ((key, edit) in edits) values[key]?.let { edit.setText(it) }
    }

    override val inputForTasker: TaskerInput<TInput>
        get() = TaskerInput(buildInput(edits.mapValues { (_, e) -> e.text?.toString()?.trim().orEmpty() }))

    protected fun Map<String, String>.opt(key: String): String? = this[key]?.trim()?.ifEmpty { null }
}
