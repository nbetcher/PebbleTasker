package com.nickbether.pebbletasker.tasker.vars

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.nickbether.pebbletasker.R

/**
 * THE variable picker (FINAL DESIGN §4.1 manner 2 / §4.3, FIX #1).
 *
 * Tasker does NOT inject a variable picker into a plugin's own config activity — the activity is a
 * separate activity Tasker merely launches via ACTION_EDIT_*. So this endIcon dialog is the SOLE,
 * MANDATORY, first-class picker on every variable-bearing field, not a fallback.
 *
 * Design rules enforced here:
 *  - The editable view is ALWAYS a plain [EditText] subclass (e.g. TextInputEditText). Tasker
 *    substitutes %vars by intent key (VARIABLE_REPLACE_KEYS), so the view type is irrelevant to
 *    substitution as long as it is a real EditText the config writes into the input object. Never an
 *    exotic custom view.
 *  - The TextInputLayout's single end-icon slot is owned by the variable picker. NEVER also put an
 *    ExposedDropdownMenu / browse chevron on the same field's end icon (FIX #3a). Browse-pickers
 *    (watch/app/pref) go on the startIcon or an adjacent icon button and write their result INTO the
 *    EditText, which stays user-editable so a %var survives.
 *  - End-icon tint is forced to colorPrimary (the Neon-Grid style default ng_text_dim is near-
 *    invisible on dark, and this is the only picker affordance).
 *
 * The dialog inherits Theme.NeonGrid's MaterialAlertDialog overlay automatically (constructed from a
 * view's themed context).
 */
object VariableFieldBinder {

    /**
     * Attach the variable picker to a single [TextInputLayout]/[EditText] pair.
     *
     * @param suggestions the merged suggestion list (Tasker inbound vars + plugin %pbl_* outputs).
     *        Pass [RelevantVars.suggestionsFor]. May be empty — then the icon just focuses the field.
     */
    fun attach(layout: TextInputLayout, edit: EditText, suggestions: Array<String>) {
        layout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        layout.setEndIconDrawable(R.drawable.ic_insert_variable)
        layout.setEndIconContentDescription(R.string.insert_variable)
        layout.isEndIconVisible = true
        layout.setEndIconTintList(
            ColorStateList.valueOf(
                MaterialColors.getColor(
                    layout,
                    com.google.android.material.R.attr.colorPrimary,
                ),
            ),
        )
        layout.setEndIconOnClickListener { showPicker(edit, suggestions) }
    }

    /**
     * Attach to a [TextInputLayout] whose editText is already set in the layout XML. No-op if the
     * layout has no EditText yet.
     */
    fun attach(layout: TextInputLayout, suggestions: Array<String>) {
        val edit = layout.editText ?: return
        attach(layout, edit, suggestions)
    }

    /**
     * Walk [root] and attach the picker to EVERY [TextInputLayout] that has an EditText. This is the
     * one-call path config activities use in onCreate after inflating the binding:
     *
     *   VariableFieldBinder.attachAll(binding.root, RelevantVars.suggestionsFor(hostVars))
     *
     * Fields that should NOT get the picker (rare) can opt out via android:tag="pb_no_var" in XML.
     */
    fun attachAll(root: View, suggestions: Array<String>) {
        forEachTextInputLayout(root) { layout ->
            if (layout.tag == TAG_NO_VAR) return@forEachTextInputLayout
            layout.editText?.let { attach(layout, it, suggestions) }
        }
    }

    private fun showPicker(edit: EditText, suggestions: Array<String>) {
        if (suggestions.isEmpty()) {
            edit.requestFocus()
            return
        }
        MaterialAlertDialogBuilder(edit.context)
            .setTitle(R.string.pick_variable)
            .setItems(suggestions) { _, which ->
                insertAtCursor(edit, suggestions[which])
            }
            .show()
    }

    /** Insert [variable] at the current cursor (or append), preserving any surrounding text/%vars. */
    private fun insertAtCursor(edit: EditText, variable: String) {
        val start = edit.selectionStart.coerceAtLeast(0)
        val end = edit.selectionEnd.coerceAtLeast(start)
        edit.text?.replace(start, end, variable)
        edit.setSelection((start + variable.length).coerceAtMost(edit.text?.length ?: 0))
        edit.requestFocus()
    }

    private fun forEachTextInputLayout(view: View, action: (TextInputLayout) -> Unit) {
        if (view is TextInputLayout) {
            // Do not descend INTO a TextInputLayout (its inner FrameLayout holds the EditText).
            action(view)
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                forEachTextInputLayout(view.getChildAt(i), action)
            }
        }
    }

    /** Set this as a TextInputLayout's android:tag to skip the variable picker on that field. */
    const val TAG_NO_VAR = "pb_no_var"
}
