package com.nickbether.pebbletasker.tasker.prefs

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.AndroidBridgePort
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender
import kotlinx.coroutines.launch

/**
 * Preference key picker plus a matching value editor, fed by watch.listPrefs for the selected watch.
 * The last list is cached per watch so configuration works offline; it is refreshed when the editor
 * opens. Both fields stay free text so %variables still work.
 */
class PrefPicker(
    private val activity: AppCompatActivity,
    private val keyLayout: TextInputLayout,
    private val valueLayout: TextInputLayout?,
    /** The watch selector currently entered, or null for the active watch. */
    private val watch: () -> String?,
    /** Value hint for an empty field, e.g. "blank = any value". */
    private val emptyValueHint: String? = null,
) {
    private var prefs: List<PrefInfo> = emptyList()
    private var status: String = "Loading preferences…"

    fun attach() {
        val keyEdit = keyLayout.editText ?: return
        prefs = WatchPrefs.cached(activity, literalWatch())
        if (prefs.isNotEmpty()) status = "Cached list"
        keyLayout.setStartIconDrawable(R.drawable.ic_lookup_dropdown)
        keyLayout.setStartIconContentDescription(R.string.pb_lookup_pick)
        keyLayout.isStartIconVisible = true
        keyLayout.setStartIconOnClickListener { showKeyDialog() }
        keyEdit.setOnClickListener { showKeyDialog() }
        keyEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = renderSelection()
        })
        valueLayout?.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = renderSelection()
        })
        valueLayout?.let { layout ->
            layout.setStartIconDrawable(R.drawable.ic_lookup_dropdown)
            layout.setStartIconContentDescription(R.string.pb_lookup_pick)
            layout.setStartIconOnClickListener { showValuePopup() }
        }
        renderSelection()
        refresh()
    }

    private fun literalWatch(): String? = watch()?.trim()?.takeIf { it.isNotEmpty() && !it.contains('%') }

    /** Re-fetch the list for the selected watch and cache it. */
    fun refresh() {
        val target = literalWatch()
        activity.lifecycleScope.launch {
            when (val r = AndroidBridgePort(activity).execute(CommandSender.Type.WATCH_LIST_PREFS, target)) {
                is BridgeResult.Ok -> {
                    val raw = r.value["prefs"].orEmpty()
                    val parsed = WatchPrefs.parseList(raw)
                    if (parsed.isNotEmpty()) {
                        WatchPrefs.store(activity, target, raw)
                        prefs = parsed
                        status = "Live list (${parsed.size})"
                    } else if (prefs.isEmpty()) status = "The watch reported no preferences"
                }
                is BridgeResult.Err -> status = if (prefs.isEmpty()) "Could not list preferences: ${r.bridgeCode ?: r.message}" else "Offline: showing the cached list"
            }
            renderSelection()
        }
    }

    private fun selected(): PrefInfo? {
        val key = keyLayout.editText?.text?.toString()?.trim().orEmpty()
        return prefs.firstOrNull { it.key == key }
    }

    private fun renderSelection() {
        if (!keyLayout.isAttachedToWindow && keyLayout.parent == null) return
        val key = keyLayout.editText?.text?.toString()?.trim().orEmpty()
        val info = selected()
        keyLayout.helperText = when {
            key.isEmpty() -> "Tap to choose. $status"
            key.contains('%') -> "Resolved at run time"
            info == null -> if (prefs.isEmpty()) status else "Not in this watch's list ($status)"
            else -> buildString {
                append(info.label).append(" · ").append(badge(info.support))
                info.description?.let { append("\n").append(it) }
            }
        }
        val layout = valueLayout ?: return
        layout.isStartIconVisible = info?.valueChoices?.isNotEmpty() == true
        val value = layout.editText?.text?.toString().orEmpty()
        val problem = info?.problemWith(value)
        layout.helperText = when {
            problem != null -> "⚠ $problem"
            info == null -> emptyValueHint
            else -> listOfNotNull(
                when (info.editor) {
                    PrefInfo.Editor.BOOLEAN -> "true or false"
                    PrefInfo.Editor.CHOICE -> "Tap the icon to pick an option"
                    PrefInfo.Editor.NUMBER -> "Number" + (info.rangeText?.let { " ($it)" } ?: "")
                    PrefInfo.Editor.TEXT -> info.type.ifBlank { "Text" }
                },
                info.value?.let { "current: $it" },
                if (value.isBlank()) emptyValueHint else null,
            ).joinToString(" · ")
        }
    }

    private fun badge(support: String) = when (support) {
        PrefInfo.SUPPORT_SUPPORTED -> "supported"
        PrefInfo.SUPPORT_UNSUPPORTED -> "not supported by this watch"
        else -> "support unknown"
    }

    private fun showValuePopup() {
        val layout = valueLayout ?: return
        val choices = selected()?.valueChoices.orEmpty()
        if (choices.isEmpty()) return
        val popup = ListPopupWindow(activity).apply {
            anchorView = layout
            isModal = true
            setAdapter(ArrayAdapter(activity, R.layout.item_criteria_dropdown, choices.map { it.first }))
        }
        popup.setOnItemClickListener { _, _, position, _ ->
            layout.editText?.setText(choices[position].second)
            popup.dismiss()
        }
        popup.show()
    }

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Item(val info: PrefInfo) : Row()
    }

    private fun showKeyDialog() {
        if (prefs.isEmpty()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Watch preferences")
                .setMessage("$status.\n\nConnect the watch and reopen this editor, or type a key or %variable.")
                .setPositiveButton(R.string.action_close, null)
                .show()
            refresh()
            return
        }
        val rows = WatchPrefs.grouped(prefs).flatMap { (group, items) ->
            val title = when (group) {
                PrefInfo.SUPPORT_SUPPORTED -> "Supported by this watch"
                PrefInfo.SUPPORT_UNSUPPORTED -> "Not supported by this watch"
                else -> "Support not known yet"
            }
            listOf<Row>(Row.Header("$title (${items.size})")) + items.map { Row.Item(it) }
        }
        val adapter = object : ArrayAdapter<Row>(activity, 0, rows) {
            override fun isEnabled(position: Int) = rows[position] is Row.Item
            override fun areAllItemsEnabled() = false
            override fun getViewTypeCount() = 2
            override fun getItemViewType(position: Int) = if (rows[position] is Row.Item) 1 else 0
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(context).apply {
                    val d = resources.displayMetrics.density
                    setPadding((20 * d).toInt(), (10 * d).toInt(), (20 * d).toInt(), (10 * d).toInt())
                }
                when (val row = rows[position]) {
                    is Row.Header -> {
                        tv.text = row.title
                        tv.setTypeface(null, Typeface.BOLD)
                        tv.setTextColor(ContextCompat.getColor(context, R.color.ng_cyan))
                    }
                    is Row.Item -> {
                        tv.text = itemText(context, row.info)
                        tv.setTypeface(null, Typeface.NORMAL)
                        tv.setTextColor(ContextCompat.getColor(context,
                            if (row.info.support == PrefInfo.SUPPORT_UNSUPPORTED) R.color.ng_text_dim else R.color.ng_text))
                    }
                }
                return tv
            }
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Choose a preference")
            .setAdapter(adapter) { _, which ->
                (rows[which] as? Row.Item)?.let { keyLayout.editText?.setText(it.info.key) }
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun itemText(context: Context, info: PrefInfo): CharSequence {
        val sb = SpannableStringBuilder()
        sb.append(info.label, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        info.description?.let {
            sb.append("\n")
            val start = sb.length
            sb.append(it)
            sb.setSpan(RelativeSizeSpan(0.85f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        sb.append("\n")
        val start = sb.length
        sb.append(info.key)
        info.value?.let { sb.append(" = ").append(it) }
        sb.setSpan(RelativeSizeSpan(0.8f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.ng_text_secondary)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }
}
