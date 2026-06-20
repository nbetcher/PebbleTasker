package com.nickbether.pebbletasker.tasker.state

import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown

/**
 * Watch-serial browse affordance for state config activities (FINAL DESIGN §4.3).
 *
 * Delegates to the shared [CriteriaDropdown]: an anchored "Any" + live-watches dropdown opened by the
 * field's start icon OR a field tap, writing the chosen serial into the editable field. ([edit] is
 * always layout.editText; it stays in the signature so the existing call sites are unchanged.)
 */
internal object WatchBrowse {
    @Suppress("UNUSED_PARAMETER")
    fun attach(layout: TextInputLayout, edit: EditText) = CriteriaDropdown.attachWatchSerial(layout)
}
