package com.nickbether.pebbletasker.tasker.event

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.dto.WatchRef

/**
 * Lightweight watch browse-picker for event config activities (FINAL DESIGN §2.1 config UI).
 *
 * Owned by the event package (NOT ui/pickers, which the UI role owns) so the event-plugin deliverable
 * compiles standalone. It queries the bridge's getState for the connected watch list and lets the user
 * tap one; the chosen serial is written INTO the (editable) serial field so a %var still survives.
 *
 * Browse is a CONVENIENCE: it never replaces typing. On an unbound bridge / empty list it shows a
 * one-line notice and the user can still type a serial or a %var.
 */
object WatchSerialPicker {

    /** Show the picker; [onPicked] receives the chosen watch serial (or address as fallback). */
    fun show(context: Context, onPicked: (String) -> Unit) {
        val watches = loadWatches(context)
        if (watches.isEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle("No watches available")
                .setMessage(
                    "No connected watch was reported by the Pebble app. " +
                        "Type a serial (or a %variable) into the field instead.",
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val labels = watches.map { w ->
            val name = w.nickname?.takeIf { it.isNotBlank() } ?: w.name
            val serial = w.serial.ifBlank { w.address.orEmpty() }
            if (serial.isBlank()) name else "$name  ($serial)"
        }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("Pick a watch")
            .setItems(labels) { _, which ->
                val w = watches[which]
                onPicked(w.serial.ifBlank { w.address.orEmpty() })
            }
            .show()
    }

    private fun loadWatches(context: Context): List<WatchRef> =
        when (val r = BridgeClient.get(context).getStateBlocking()) {
            is BridgeResult.Ok -> r.value.data.watches
            is BridgeResult.Err -> emptyList()
        }
}
