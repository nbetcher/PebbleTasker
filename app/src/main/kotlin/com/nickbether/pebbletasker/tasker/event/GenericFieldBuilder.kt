package com.nickbether.pebbletasker.tasker.event

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown

/**
 * Builds Neon-Grid-themed variable fields into a config activity's pbFieldsContainer at runtime, so
 * the multi-field event plugins (E3, E5-E15) can share one generic layout while still using real
 * Material TextInputEditText views (so Tasker %var substitution + the variable picker attach).
 *
 * Each field is a Widget.NeonGrid.TextInputLayout wrapping a monospace TextInputEditText. The hint is
 * the field label. The returned [TextInputEditText] is what the Activity reads/writes for that field.
 * After all fields are added, the base activity's attachVariablePickers() walks the tree and adds the
 * variable picker end-icon to every TextInputLayout uniformly. A field may also carry a criteria
 * dropdown (live watch serials, or a fixed value set) on its START icon + field tap via [CriteriaDropdown].
 */
object GenericFieldBuilder {

    /**
     * Add a labelled variable field to [container].
     * @param hint the field label (use a localized string).
     * @param numeric when true, sets a numeric-friendly inputType (still accepts %vars as text).
     * @param options when set, gives the field a fixed "Any" + values criteria dropdown.
     * @param watchSerial when true, gives the field a live "Any" + connected-watches dropdown.
     * @param lookup when set, gives the field a live source-of-truth dropdown + non-blocking validation
     *   (watch serial, installed package, or locker app/face UUID). Takes precedence over the above.
     */
    fun addField(
        context: Context,
        container: LinearLayout,
        hint: CharSequence,
        numeric: Boolean = false,
        singleLine: Boolean = true,
        options: List<Pair<String, String>>? = null,
        watchSerial: Boolean = false,
        lookup: CriteriaDropdown.Source? = null,
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
        // Criteria affordance (FIX #3a: START icon + field tap, never the end-icon variable slot):
        // a live watch-serial dropdown, or a fixed "Any" + values pick-list for an enumerable filter.
        when {
            lookup != null -> CriteriaDropdown.attach(layout, lookup)
            watchSerial -> CriteriaDropdown.attachWatchSerial(layout)
            options != null -> CriteriaDropdown.attachFixed(layout, options)
        }
        container.addView(layout)
        return edit
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
