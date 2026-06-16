package com.nickbether.pebbletasker.tasker.action.common

import android.content.Context
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import kotlin.concurrent.thread

/**
 * Browse-picker for the `serial` field on action config activities (FINAL DESIGN §2.3 / §4.3).
 *
 * Populated via [BridgeClient.getState] (the watches[] list). Per FIX #3a, this affordance lives on
 * the field's START icon (or an adjacent icon button) — NEVER the end icon, which is owned by the
 * variable picker. It writes the chosen watch's serial INTO the editable field, which stays
 * user-editable so a %var still works.
 *
 * getState is a blocking cross-process call, so it runs off the main thread; the result dialog is
 * posted back to the field's handler. If the bridge is unbound / not authorised, a short toast-style
 * dialog explains and the user can still type a serial or %var by hand.
 */
object WatchBrowsePicker {

    /** Attach a click handler to [trigger] that browses connected watches and fills [edit]. */
    fun attach(context: Context, edit: EditText, onPick: (() -> Unit)? = null) {
        browse(context, edit, onPick)
    }

    /** Run the browse flow: getState off-thread -> pick dialog on the UI thread. */
    fun browse(context: Context, edit: EditText, onPick: (() -> Unit)? = null) {
        val appCtx = context.applicationContext
        thread(name = "pb-watch-picker") {
            val result = BridgeClient.get(appCtx).getStateBlocking()
            edit.post {
                when (result) {
                    is BridgeResult.Ok -> showList(edit, result.value.data.watches, onPick)
                    is BridgeResult.Err -> showUnavailable(edit.context, result.message)
                }
            }
        }
    }

    private fun showList(edit: EditText, watches: List<WatchRef>, onPick: (() -> Unit)?) {
        if (watches.isEmpty()) {
            showUnavailable(edit.context, edit.context.getString(R.string.act_picker_no_watches))
            return
        }
        val labels = watches.map { w ->
            val nick = w.nickname?.takeIf { it.isNotBlank() }
            val title = nick ?: w.name.ifBlank { w.serial }
            "$title  (${w.serial})"
        }.toTypedArray()
        MaterialAlertDialogBuilder(edit.context)
            .setTitle(R.string.act_picker_choose_watch)
            .setItems(labels) { _, which ->
                edit.setText(watches[which].serial)
                edit.setSelection(edit.text?.length ?: 0)
                onPick?.invoke()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showUnavailable(context: Context, message: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.act_picker_unavailable_title)
            .setMessage(context.getString(R.string.act_picker_unavailable_body, message))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
