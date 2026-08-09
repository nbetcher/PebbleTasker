package com.nickbether.pebbletasker.tasker.base

import android.content.Context
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender
import com.nickbether.pebbletasker.bridge.dto.CommandEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Anchored criteria dropdown for a field with a known source of truth (FINAL DESIGN §4.3).
 *
 * Shows a [ListPopupWindow] anchored under the field — opened by tapping the field OR its start icon —
 * listing "Any" (clears the field -> match everything) followed by the known values. Picking a row
 * writes the matched value into the field; the field stays an editable [EditText], so the end-icon
 * variable picker and %var typing are both untouched (the dropdown lives on the START icon + tap, never
 * the end icon — FIX #3a).
 *
 * Values are loaded live off the main thread each time the dropdown opens (watch serials via the
 * bridge's getState, installed packages via PackageManager, locker apps/faces via the bridge's
 * system.getLocker), so a slow/unbound bridge never blocks the UI.
 *
 * VALIDATION (serial / package / UUID fields): when attached with a warning, the value the user typed
 * is checked against the live set of known values. A non-matching, non-%variable value surfaces a
 * small neon-yellow "nothing matches" bubble BELOW the field. It is purely advisory — it never blocks
 * saving, and it stays silent for empty values, %variables, and whenever the known set couldn't be
 * loaded (e.g. the bridge is down) so we never cry wolf.
 */
object CriteriaDropdown {

    /** A field's source of truth — selects which live dropdown + validation to attach. */
    enum class Source { WATCH_SERIAL, APP_PACKAGE, LOCKER_APP, LOCKER_FACE, LOCKER_ANY }

    /** Attach the live dropdown + validation for [source]. */
    fun attach(layout: TextInputLayout, source: Source) = when (source) {
        Source.WATCH_SERIAL -> attachWatchSerial(layout)
        Source.APP_PACKAGE -> attachPackages(layout)
        Source.LOCKER_APP -> attachLockerApp(layout)
        Source.LOCKER_FACE -> attachLockerFace(layout)
        Source.LOCKER_ANY -> attachLockerAny(layout)
    }

    private val lockerJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class LockerEntryDto(val uuid: String = "", val title: String = "", val type: String = "")

    // Short-TTL process cache so several same-source fields on one screen (and the seed + each dropdown
    // open) don't each repeat a blocking IPC / full package enumeration. Keyed by source.
    private const val CACHE_TTL_MS = 15_000L
    private val cache = HashMap<String, Pair<Long, List<Pair<String, String>>>>()

    private fun cachedLoad(key: String, loader: () -> List<Pair<String, String>>): List<Pair<String, String>> {
        val now = SystemClock.uptimeMillis()
        synchronized(cache) { cache[key]?.let { (at, rows) -> if (now - at < CACHE_TTL_MS) return rows } }
        val rows = loader()
        // Don't pin an empty result (bridge momentarily down / locker not ready) — let the next open
        // re-fetch so real data appears as soon as it's available, rather than a stale empty list.
        if (rows.isNotEmpty()) synchronized(cache) { cache[key] = now to rows }
        return rows
    }

    // --- public attach points ---

    /** Live watch-serial dropdown + validation: "Any" + the watches the Pebble app reports. */
    fun attachWatchSerial(layout: TextInputLayout) {
        val appCtx = layout.context.applicationContext
        attachCore(layout, R.string.pb_warn_no_serial) { cachedLoad("serial") { loadWatchOptions(appCtx) } }
    }

    /** Installed-app dropdown + validation: "Any" + every installed package ("Name (package)"). */
    fun attachPackages(layout: TextInputLayout) {
        val appCtx = layout.context.applicationContext
        attachCore(layout, R.string.pb_warn_no_package) { cachedLoad("pkg") { loadPackageOptions(appCtx) } }
    }

    /** Locker watch-apps dropdown + UUID validation. */
    fun attachLockerApp(layout: TextInputLayout) = attachLocker(layout, "watchapp")

    /** Locker watchfaces dropdown + UUID validation. */
    fun attachLockerFace(layout: TextInputLayout) = attachLocker(layout, "watchface")

    /** Locker apps + faces (any) dropdown + UUID validation. */
    fun attachLockerAny(layout: TextInputLayout) = attachLocker(layout, "")

    /**
     * Locker dropdown for [type] ("watchapp"/"watchface"/"" = any). The DROPDOWN lists the type-filtered
     * subset, but VALIDATION always runs against the FULL locker — so a real UUID never false-warns just
     * because it's the "other" type (e.g. a watchface that the user launches via Launch App, or an app
     * UUID typed into Set Watchface).
     */
    private fun attachLocker(layout: TextInputLayout, type: String) {
        val appCtx = layout.context.applicationContext
        attachCore(
            layout,
            R.string.pb_warn_no_uuid,
            validateLoad = { cachedLoad("locker:") { loadLockerOptions(appCtx, "") } },
        ) { cachedLoad("locker:$type") { loadLockerOptions(appCtx, type) } }
    }

    /** Fixed-value dropdown (enumerable criterion): "Any" + [options]. No validation. */
    fun attachFixed(layout: TextInputLayout, options: List<Pair<String, String>>) {
        attachCore(layout, warnRes = 0) { options }
    }

    /** Dropdown with a custom loader and no validation. */
    fun attach(layout: TextInputLayout, load: suspend () -> List<Pair<String, String>>) {
        attachCore(layout, warnRes = 0, load = load)
    }

    // --- core wiring ---

    /**
     * Wire the start-icon + field-tap dropdown to [layout]. When [warnRes] != 0, also validate the
     * field against [load]'s values and show the neon-yellow bubble on a non-match.
     */
    private fun attachCore(
        layout: TextInputLayout,
        warnRes: Int,
        validateLoad: (suspend () -> List<Pair<String, String>>)? = null,
        load: suspend () -> List<Pair<String, String>>,
    ) {
        val edit = layout.editText ?: return
        layout.setStartIconDrawable(R.drawable.ic_lookup_dropdown)
        layout.setStartIconContentDescription(R.string.pb_lookup_pick)
        layout.isStartIconVisible = true
        layout.setStartIconOnClickListener { open(layout, edit, load) }
        edit.setOnClickListener { open(layout, edit, load) }
        if (warnRes == 0) return

        // Validation set (may be broader than the dropdown's [load] — e.g. faces validate vs the full
        // locker). Seeded once off the main thread; the predicate never blocks saving.
        val ctx = layout.context
        val valid = HashSet<String>()
        var loaded = false
        fun revalidate() {
            if (!loaded || !layout.isAttachedToWindow) return // never touch an off-screen / dead view
            val text = edit.text?.toString()?.trim().orEmpty()
            val matches = text.isEmpty() ||
                text.contains('%') || // %variable -> resolved at run time, don't validate
                valid.isEmpty() || // no known set (e.g. bridge down) -> never cry wolf
                valid.any { it.equals(text, ignoreCase = true) }
            setWarning(layout, if (matches) null else ctx.getString(warnRes))
        }
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = revalidate()
        })
        // Seed the known set in the background so the first validation pass has something to check.
        MainScope().launch {
            val rows = withContext(Dispatchers.IO) {
                runCatching { (validateLoad ?: load)() }.getOrElse { emptyList() }
            }
            valid.clear()
            rows.forEach { valid.add(it.second) }
            loaded = true
            revalidate()
        }
    }

    private fun open(
        layout: TextInputLayout,
        edit: EditText,
        load: suspend () -> List<Pair<String, String>>,
    ) {
        val ctx = layout.context
        MainScope().launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { load() }.getOrElse { emptyList() } }
            // The field may have detached while we waited; don't anchor a popup to a dead view.
            if (!layout.isAttachedToWindow) return@launch
            val rows = listOf(ctx.getString(R.string.pb_lookup_any) to "") + loaded
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

    // --- the neon-yellow "nothing matches" bubble ---

    /** Find (or lazily build, GONE) the bubble TextView associated with [layout] via its tag. */
    private fun bubbleFor(layout: TextInputLayout): TextView {
        (layout.getTag(R.id.pb_warn_bubble) as? TextView)?.let { return it }
        val ctx = layout.context
        val d = ctx.resources.displayMetrics.density
        val tv = TextView(ctx).apply {
            setBackgroundResource(R.drawable.bg_warn_bubble)
            setTextColor(ContextCompat.getColor(ctx, R.color.pb_warn_text))
            textSize = 12f
            val ph = (12 * d).toInt()
            val pv = (7 * d).toInt()
            setPadding(ph, pv, ph, pv)
            visibility = View.GONE
        }
        layout.setTag(R.id.pb_warn_bubble, tv)
        return tv
    }

    /**
     * Show [message] in the field's bubble (inserted just below the field), or hide it if null. Assumes
     * the field sits in a vertical container — every validated field does — so the bubble stacks
     * beneath it as the next sibling.
     */
    private fun setWarning(layout: TextInputLayout, message: String?) {
        val tv = bubbleFor(layout)
        if (message == null) {
            tv.visibility = View.GONE
            return
        }
        tv.text = message
        tv.visibility = View.VISIBLE
        if (tv.parent != null) return
        val parent = layout.parent as? ViewGroup
        if (parent == null) {
            // Field not attached to its container yet — retry after the next layout pass.
            layout.post { setWarning(layout, message) }
            return
        }
        parent.addView(tv, parent.indexOfChild(layout) + 1)
        (tv.layoutParams as? ViewGroup.MarginLayoutParams)?.let { mlp ->
            val d = layout.context.resources.displayMetrics.density
            mlp.topMargin = (4 * d).toInt()
            mlp.bottomMargin = (8 * d).toInt()
            tv.layoutParams = mlp
        }
    }

    // --- loaders (run off the main thread) ---

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

    /** Installed apps as ("Label (package)" -> package) pairs. Needs QUERY_ALL_PACKAGES to see all. */
    private fun loadPackageOptions(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val apps = runCatching { pm.getInstalledApplications(0) }.getOrElse { return emptyList() }
        return apps.mapNotNull { ai ->
            val pkg = ai.packageName ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(ai).toString() }
                .getOrNull()?.takeIf { it.isNotBlank() }
            (if (label == null) pkg else "$label  ($pkg)") to pkg
        }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
    }

    /**
     * Locker apps/faces as ("Title (uuid)" -> uuid) pairs via the bridge's system.getLocker. [type] is
     * "watchapp", "watchface", or "" (both). Returns empty on any bridge/parse failure (validation then
     * stays silent rather than warning falsely).
     */
    private fun loadLockerOptions(context: Context, type: String): List<Pair<String, String>> {
        val args = if (type.isBlank()) emptyMap() else mapOf("type" to type)
        val res = when (val r = BridgeClient.get(context).executeBlocking(CommandEnvelope(type = CommandSender.Type.SYSTEM_GET_LOCKER, args = args))) {
            is BridgeResult.Ok -> r.value
            is BridgeResult.Err -> return emptyList()
        }
        if (!res.ok) return emptyList()
        val entriesJson = res.data?.get("entries") ?: return emptyList()
        val entries = runCatching { lockerJson.decodeFromString<List<LockerEntryDto>>(entriesJson) }
            .getOrElse { return emptyList() }
        return entries.filter { it.uuid.isNotBlank() }
            .map { e ->
                val title = e.title.takeIf { it.isNotBlank() } ?: e.uuid
                "$title  (${e.uuid})" to e.uuid
            }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }
}
