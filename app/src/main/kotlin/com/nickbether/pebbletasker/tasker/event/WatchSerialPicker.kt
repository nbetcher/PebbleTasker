package com.nickbether.pebbletasker.tasker.event

import android.app.Activity
import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight watch browse-picker for event config activities (FINAL DESIGN §2.1 config UI).
 *
 * Owned by the event package (NOT ui/pickers, which the UI role owns) so the event-plugin deliverable
 * compiles standalone. It queries the bridge's getState for the connected watch list and lets the user
 * pick one; the chosen serial is written INTO the (editable) serial field so a %var still survives.
 *
 * getState is a BLOCKING cross-process call, so it runs off the main thread (mirroring the action/state
 * pickers); the chooser is shown back on the main thread once it returns. The list ALWAYS begins with
 * "Any" (clears the field -> match any watch); the rest are the watches the Pebble app reports. Browse
 * is a CONVENIENCE: it never replaces typing a serial or a %variable.
 */
object WatchSerialPicker {

    /** Show the picker; [onPicked] receives the chosen serial, or "" for "Any". */
    fun show(context: Context, onPicked: (String) -> Unit) {
        val appCtx = context.applicationContext
        MainScope().launch {
            val watches = withContext(Dispatchers.IO) { loadWatches(appCtx) }
            // The activity may have gone away while getState was in flight; don't show a dialog on a
            // dead window.
            if (context is Activity && (context.isFinishing || context.isDestroyed)) return@launch
            showChooser(context, watches, onPicked)
        }
    }

    private fun showChooser(context: Context, watches: List<WatchRef>, onPicked: (String) -> Unit) {
        val labels = (
            listOf(context.getString(R.string.pb_lookup_any)) + watches.map { w ->
                val name = w.nickname?.takeIf { it.isNotBlank() } ?: w.name
                val serial = w.serial.ifBlank { w.address.orEmpty() }
                if (serial.isBlank()) name else "$name  ($serial)"
            }
        ).toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pb_pick_watch)
            .setItems(labels) { _, which ->
                if (which == 0) {
                    onPicked("")
                } else {
                    val w = watches[which - 1]
                    onPicked(w.serial.ifBlank { w.address.orEmpty() })
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadWatches(context: Context): List<WatchRef> =
        when (val r = BridgeClient.get(context).getStateBlocking()) {
            is BridgeResult.Ok -> r.value.data.watches
            is BridgeResult.Err -> emptyList()
        }
}
