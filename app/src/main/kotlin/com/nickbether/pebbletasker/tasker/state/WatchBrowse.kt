package com.nickbether.pebbletasker.tasker.state

import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Self-contained "browse watches" affordance for the serial field on state config activities
 * (FINAL DESIGN §4.3 — browse goes on the startIcon, the variable picker owns the endIcon).
 *
 * Deliberately does NOT depend on ui/pickers/WatchPicker (not yet written): it queries getState
 * directly and shows a themed chooser, writing the chosen serial INTO the EditText, which stays
 * user-editable so a %var still works. The endIcon variable picker is attached separately by the
 * base PebbleConfigActivity, so the two affordances never share the single end-icon slot.
 */
internal object WatchBrowse {

    /**
     * Wire a startIcon on [layout] that, when tapped, queries the bridge for connected watches and
     * lets the user pick one — writing its serial into [edit]. Falls back to focusing the field with a
     * helper message when the bridge is unreachable or no watch is connected.
     */
    fun attach(layout: TextInputLayout, edit: EditText) {
        layout.startIconDrawable = layout.context.getDrawable(R.drawable.ic_watch_browse)
        layout.setStartIconContentDescription(R.string.pb_browse_watches)
        layout.setStartIconOnClickListener { browse(layout, edit) }
    }

    private fun browse(layout: TextInputLayout, edit: EditText) {
        val ctx = layout.context
        MainScope().launch {
            val watches = withContext(Dispatchers.IO) {
                when (val r = StateSupport.queryState(ctx)) {
                    is BridgeResult.Ok -> r.value.data.watches
                    is BridgeResult.Err -> emptyList()
                }
            }
            // Always offer "Any" first (clears the field -> match any watch), then the reported watches.
            val labels = (
                listOf(ctx.getString(R.string.pb_lookup_any)) + watches.map { it.displayLabel() }
            ).toTypedArray()
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.pb_pick_watch)
                .setItems(labels) { _, which ->
                    edit.setText(if (which == 0) "" else watches[which - 1].serial)
                    edit.setSelection(edit.text?.length ?: 0)
                }
                .show()
        }
    }

    private fun WatchRef.displayLabel(): String {
        val title = nickname?.takeIf { it.isNotBlank() } ?: name.takeIf { it.isNotBlank() } ?: serial
        return if (title == serial) serial else "$title  ($serial)"
    }
}
