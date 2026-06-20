package com.nickbether.pebbletasker.tasker.event

import android.view.LayoutInflater
import com.google.android.material.textfield.TextInputEditText
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import com.nickbether.pebbletasker.databinding.ActivityConfigEventGenericBinding
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity

/**
 * Reusable config-activity base for multi-field event plugins (E3, E5-E15).
 *
 * Subclasses declare a list of [FieldSpec]s (key, label, optional default, optional watch-browse);
 * this base inflates the shared generic layout, builds a themed variable field per spec into
 * pbFieldsContainer, sets the title/description, wires the optional watch-browse button to the first
 * watch-serial field, and round-trips values to/from the input object via [readFields]/[writeFields].
 *
 * Because every field is a real Material TextInputEditText and the base's attachVariablePickers()
 * runs after onConfigCreated, all fields get the variable picker + accept %vars uniformly.
 */
abstract class GenericEventConfigActivity<
    TInput : Any,
    TOutput : Any,
    TRunner : TaskerPluginRunner<TInput, TOutput>,
    THelper : TaskerPluginConfigHelper<TInput, TOutput, TRunner>,
    > : PebbleConfigActivity<TInput, TOutput, TRunner, THelper, ActivityConfigEventGenericBinding>() {

    /** A single configurable field. */
    data class FieldSpec(
        val key: String,
        val label: CharSequence,
        val default: String = "",
        val numeric: Boolean = false,
        /** When true, the watch-browse button writes the picked serial into this field. */
        val isWatchSerial: Boolean = false,
        /**
         * Fixed criteria values (display label -> matched value) for an enumerable filter. When set,
         * the field gets a start-icon pick-list of "Any" + these values (e.g. media commands, call
         * states). The field stays editable so a %variable still works.
         */
        val options: List<Pair<String, String>>? = null,
    )

    /** Title/description string resource ids. */
    protected abstract val titleRes: Int
    protected abstract val descRes: Int

    /**
     * Build the ordered fields this plugin exposes. Called from onConfigCreated (NOT a constructor
     * val) so getString()/resources are available — building FieldSpec labels at construction time
     * would crash before attachBaseContext.
     */
    protected abstract fun buildFields(): List<FieldSpec>

    /** Build the input object from the current field values (keyed by FieldSpec.key). */
    protected abstract fun buildInput(values: Map<String, String>): TInput

    /** Extract the saved field values (keyed by FieldSpec.key) from a restored input object. */
    protected abstract fun extractValues(input: TInput): Map<String, String>

    private val edits = LinkedHashMap<String, TextInputEditText>()

    override fun inflateBinding(inflater: LayoutInflater): ActivityConfigEventGenericBinding =
        ActivityConfigEventGenericBinding.inflate(inflater)

    override fun onConfigCreated(binding: ActivityConfigEventGenericBinding) {
        binding.pbTitle.setText(titleRes)
        binding.pbDesc.setText(descRes)

        for (spec in buildFields()) {
            val edit = GenericFieldBuilder.addField(
                context = this,
                container = binding.pbFieldsContainer,
                hint = spec.label,
                numeric = spec.numeric,
                options = spec.options,
                watchSerial = spec.isWatchSerial,
            )
            if (spec.default.isNotEmpty()) edit.setText(spec.default)
            edits[spec.key] = edit
        }
        // Each serial/enum field now carries its own anchored dropdown; the shared button is unused.
        binding.pbBtnPickWatch.visibility = android.view.View.GONE

        // Re-attach variable pickers now that fields were added dynamically (base attached the static
        // tree earlier, before these existed).
        attachVariablePickers(binding.root)

        // assignFromInput ran before this (base.onCreate order); flush any saved values now.
        flushPending()
    }

    override fun assignFromInput(input: TaskerInput<TInput>) {
        // Fields may not be built yet on the very first assign; defer until onConfigCreated populated
        // defaults, then overwrite with saved values.
        val saved = extractValues(input.regular)
        if (edits.isEmpty()) {
            // onConfigCreated runs after onCreate's assignFromInput in the base; store for later.
            pendingValues = saved
        } else {
            applyValues(saved)
        }
    }

    private var pendingValues: Map<String, String>? = null

    override val inputForTasker: TaskerInput<TInput>
        get() {
            val values = edits.mapValues { (_, edit) -> edit.text?.toString()?.trim().orEmpty() }
            return TaskerInput(buildInput(values))
        }

    private fun applyValues(values: Map<String, String>) {
        for ((key, edit) in edits) {
            values[key]?.let { edit.setText(it) }
        }
    }

    /** Called by the base after the binding is created; flush any pending saved values. */
    protected fun flushPending() {
        pendingValues?.let { applyValues(it); pendingValues = null }
    }
}
