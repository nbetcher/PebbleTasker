package com.nickbether.pebbletasker.tasker.event

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nickbether.pebbletasker.R

/**
 * Builds Neon-Grid-themed variable fields into a config activity's pbFieldsContainer at runtime, so
 * the multi-field event plugins (E3, E5-E15) can share one generic layout while still using real
 * Material TextInputEditText views (so Tasker %var substitution + the variable picker attach).
 *
 * Each field is a Widget.NeonGrid.TextInputLayout wrapping a monospace TextInputEditText. The hint is
 * the field label. The returned [TextInputEditText] is what the Activity reads/writes for that field.
 * After all fields are added, the base activity's attachVariablePickers() walks the tree and adds the
 * variable picker end-icon to every TextInputLayout uniformly.
 */
object GenericFieldBuilder {

    /**
     * Add a labelled variable field to [container].
     * @param hint the field label (use a localized string).
     * @param numeric when true, sets a numeric-friendly inputType (still accepts %vars as text).
     */
    fun addField(
        context: Context,
        container: LinearLayout,
        hint: CharSequence,
        numeric: Boolean = false,
        singleLine: Boolean = true,
        options: List<Pair<String, String>>? = null,
    ): TextInputEditText {
        // Construct with the theme's default textInputStyle (mapped to Widget.NeonGrid.TextInputLayout
        // in Theme.NeonGrid), so the field is themed consistently with the XML-authored fields.
        val layout = TextInputLayout(context).apply {
            this.hint = hint
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 8) }
        }
        val edit = TextInputEditText(layout.context).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            isSingleLine = singleLine
            // Always keep inputType TEXT so a %variable is always enterable; a numeric keyboard would
            // block the % key. The `numeric` flag is advisory; runners parse numbers defensively.
            inputType = InputType.TYPE_CLASS_TEXT
        }
        layout.addView(edit)
        if (options != null) {
            // Start icon opens a pick-list of "Any" + the known values; the end icon stays the variable
            // picker and the field remains editable, so a %variable still works (FIX #3a slot rule).
            layout.setStartIconDrawable(R.drawable.ic_lookup_dropdown)
            layout.setStartIconContentDescription(R.string.pb_lookup_pick)
            layout.isStartIconVisible = true
            layout.setStartIconOnClickListener {
                showOptions(layout.context, options) { value ->
                    edit.setText(value)
                    edit.setSelection(edit.text?.length ?: 0)
                }
            }
        }
        container.addView(layout)
        return edit
    }

    /**
     * Criteria pick-list dialog: an "Any" row (clears the field -> match everything) followed by every
     * known [options] value. [onPicked] receives the matched value to write (empty string for "Any").
     */
    private fun showOptions(
        context: Context,
        options: List<Pair<String, String>>,
        onPicked: (String) -> Unit,
    ) {
        val labels = (listOf(context.getString(R.string.pb_lookup_any)) + options.map { it.first })
            .toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pb_lookup_pick)
            .setItems(labels) { _, which -> onPicked(if (which == 0) "" else options[which - 1].second) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
