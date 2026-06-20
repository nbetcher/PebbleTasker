package com.nickbether.pebbletasker.tasker.base

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow
import com.google.android.material.textfield.TextInputLayout
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Anchored criteria dropdown for a filter field (FINAL DESIGN §4.3 affordance slots).
 *
 * Shows a [ListPopupWindow] anchored under the field — opened by tapping the field OR its start icon —
 * listing "Any" (clears the field -> match everything) followed by the known values. Picking a row
 * writes the matched value into the field; the field stays an editable [EditText], so the end-icon
 * variable picker and %var typing are both untouched (the dropdown lives on the START icon + tap, never
 * the end icon — FIX #3a).
 *
 * Values are either fixed (an enumerable criterion) or loaded live off the main thread each time the
 * dropdown opens (e.g. watch serials via the bridge's getState), so a slow/unbound bridge never blocks
 * the UI.
 */
object CriteriaDropdown {

    /** Live watch-serial dropdown: "Any" + the watches the Pebble app reports (value = serial). */
    fun attachWatchSerial(layout: TextInputLayout) {
        val appCtx = layout.context.applicationContext
        attach(layout) { loadWatchOptions(appCtx) }
    }

    /** Fixed-value dropdown: "Any" + [options] (display label -> matched value). */
    fun attachFixed(layout: TextInputLayout, options: List<Pair<String, String>>) {
        attach(layout) { options }
    }

    /**
     * Attach a dropdown to [layout]'s edit field, opened by a field tap or the start icon. [load] is
     * invoked off the main thread each time the dropdown opens.
     */
    fun attach(layout: TextInputLayout, load: suspend () -> List<Pair<String, String>>) {
        val edit = layout.editText ?: return
        layout.setStartIconDrawable(R.drawable.ic_lookup_dropdown)
        layout.setStartIconContentDescription(R.string.pb_lookup_pick)
        layout.isStartIconVisible = true
        layout.setStartIconOnClickListener { open(layout, edit, load) }
        edit.setOnClickListener { open(layout, edit, load) }
    }

    private fun open(
        layout: TextInputLayout,
        edit: EditText,
        load: suspend () -> List<Pair<String, String>>,
    ) {
        val ctx = layout.context
        MainScope().launch {
            val rows = listOf(ctx.getString(R.string.pb_lookup_any) to "") +
                withContext(Dispatchers.IO) { load() }
            // The field may have detached while we waited; don't anchor a popup to a dead view.
            if (!layout.isAttachedToWindow) return@launch
            val popup = ListPopupWindow(ctx).apply {
                anchorView = layout
                isModal = true
                setAdapter(ArrayAdapter(ctx, R.layout.item_criteria_dropdown, rows.map { it.first }))
            }
            popup.setOnItemClickListener { _, _, position, _ ->
                edit.setText(rows[position].second)
                edit.setSelection(edit.text?.length ?: 0)
                popup.dismiss()
            }
            popup.show()
        }
    }

    /** Live watches as (display label -> serial) pairs. Blocking getState — call off the main thread. */
    private fun loadWatchOptions(context: Context): List<Pair<String, String>> =
        when (val r = BridgeClient.get(context).getStateBlocking()) {
            is BridgeResult.Ok -> r.value.data.watches.map { w ->
                val name = w.nickname?.takeIf { it.isNotBlank() } ?: w.name
                val serial = w.serial.ifBlank { w.address.orEmpty() }
                (if (serial.isBlank()) name else "$name  ($serial)") to serial
            }
            is BridgeResult.Err -> emptyList()
        }
}
