package com.nickbether.pebbletasker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeClient.ConnectionStatus
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.BridgeSession
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.EventCache
import com.nickbether.pebbletasker.cache.EventRouter
import com.nickbether.pebbletasker.databinding.ActivityDiagnosticsBinding
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.util.applyContentInsets
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics (FINAL DESIGN §1 ui/DiagnosticsActivity, "KEY BLOCKERS").
 *
 * A read-only window into the bridge connection for troubleshooting:
 *  - live [BridgeClient.status] (reactive);
 *  - handshake snapshot from [BridgeClient.currentSession] (bootId, protocol, app version — which
 *    can legitimately be "unknown", token prefix, latestSeq, contentRedacted);
 *  - the ACTUAL returned capability list (today only `events.core`) + grants, as chips/text — never
 *    a hard-coded list, so it reflects exactly what this bridge advertises;
 *  - recent cached events (last-event-per-type) from [EventCache] with seq/time;
 *  - a getState self-test button that round-trips the bridge and reports watch count or the error.
 *
 * Everything refreshes on resume, on the Refresh button, and the status portion reactively.
 */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding

    private val bridge get() = BridgeClient.get(this)
    private val cache get() = EventCache.get(this)

    private val timeFmt by lazy { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyContentInsets()

        binding.btnSelftest.setOnClickListener { runSelfTest() }
        binding.btnClearSubscriptions.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Clear AppMessage subscriptions?")
                .setMessage("Delete unused profiles in Tasker first. Active profiles may request their subscriptions again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ -> lifecycleScope.launch {
                    val subscriptions = com.nickbether.pebbletasker.tasker.event.appmsg.AppMessageSubscriptions
                    subscriptions.clear(this@DiagnosticsActivity)
                    val applied = subscriptions.restore(this@DiagnosticsActivity)
                    Toast.makeText(this@DiagnosticsActivity, if (applied) "Subscriptions cleared" else "Saved subscriptions cleared; Pebble will be updated after reconnecting", Toast.LENGTH_LONG).show()
                } }.show()
        }
        binding.btnRefresh.setOnClickListener { refreshAll() }
        binding.btnCopy.setOnClickListener { copyReport() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bridge.status.collect { renderStatus(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun refreshAll() {
        renderHandshake(bridge.currentSession)
        renderCapabilities(bridge.currentSession)
        renderEvents()
    }

    // --- connection ---

    private fun renderStatus(status: ConnectionStatus) {
        binding.statusChip.text = getString(UiSupport.statusLabel(status))
        UiSupport.styleStatusChip(binding.statusChip, status)
        binding.statusDetail.text = listOfNotNull(UiSupport.statusDetail(this, status),
            com.nickbether.pebbletasker.tasker.base.ConditionAccess.lastMessage(this)?.let { "Last condition decision: $it" },
        ).joinToString("\n")
        // Handshake fields can appear/disappear with status changes.
        renderHandshake(bridge.currentSession)
        renderCapabilities(bridge.currentSession)
    }

    // --- handshake ---

    private fun renderHandshake(session: BridgeSession?) {
        if (session == null) {
            binding.handshakeBody.text = getString(R.string.diag_no_session)
            return
        }
        binding.handshakeBody.text = buildString {
            line(R.string.diag_label_boot_id, session.bootId)
            line(R.string.diag_label_protocol, session.protocolVersion.toString())
            line(R.string.diag_label_app_version, session.appVersion) // may be "unknown" (StateProvider:36)
            line(R.string.diag_label_client_token, tokenPreview(session.clientToken))
            line(R.string.diag_label_latest_seq, session.latestSeq.toString())
            line(R.string.diag_label_content_redacted, yesNo(session.contentRedacted))
        }.trimEnd()
    }

    // --- capabilities + grants ---

    private fun renderCapabilities(session: BridgeSession?) {
        binding.capsGroup.removeAllViews()
        val caps = session?.capabilities.orEmpty().sorted()
        if (caps.isEmpty()) {
            binding.capsEmpty.visibility = View.VISIBLE
        } else {
            binding.capsEmpty.visibility = View.GONE
            for (cap in caps) binding.capsGroup.addView(capabilityChip(cap))
        }

        val grants = session?.grants
        binding.grantsBody.text = if (grants == null) {
            ""
        } else {
            buildString {
                val cats = grants.categories.ifEmpty { listOf(getString(R.string.value_none)) }
                line(R.string.diag_label_categories, cats.joinToString(", "))
                line(R.string.diag_label_tier, grants.tier ?: getString(R.string.value_unknown))
                line(R.string.diag_label_content_redacted, yesNo(grants.contentRedacted))
            }.trimEnd()
        }
    }

    private fun capabilityChip(cap: String): Chip = Chip(this).apply {
        text = cap
        isClickable = false
        isCheckable = false
        isCloseIconVisible = false
        // Info/cyan pill for advertised capabilities.
        chipBackgroundColor = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this@DiagnosticsActivity, R.color.ng_cyan_10),
        )
        chipStrokeColor = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this@DiagnosticsActivity, R.color.ng_stroke_cyan),
        )
        chipStrokeWidth = resources.displayMetrics.density // ~1dp
        setTextColor(ContextCompat.getColor(this@DiagnosticsActivity, R.color.ng_cyan))
        setEnsureMinTouchTargetSize(false)
    }

    // --- recent events ---

    private fun renderEvents() {
        binding.eventsContainer.removeAllViews()

        binding.eventsHighwater.text = getString(
            R.string.diag_high_water,
            cache.currentHighWater.takeIf { it >= 0 }?.toString() ?: "-",
            cache.currentBootId ?: getString(R.string.value_none),
        )

        // Enumerate every known event type (the frozen routing catalog + the synthetic gap type),
        // plus anything else currently registered, and show the last cached event per type.
        val types = (knownEventTypes() + EventRouter.registeredTypes() + CachedEvent.TYPE_GAP).distinct()
        val events = types.mapNotNull { cache.latest(it) }.sortedByDescending { it.seq }

        if (events.isEmpty()) {
            binding.eventsEmpty.visibility = View.VISIBLE
            return
        }
        binding.eventsEmpty.visibility = View.GONE
        for (e in events) binding.eventsContainer.addView(eventRow(e))
    }

    private fun eventRow(e: CachedEvent): TextView = TextView(this).apply {
        val serial = e.watch?.serial?.let { " · $it" } ?: ""
        val data = if (e.data.isEmpty()) "" else
            " { " + e.data.entries.joinToString(", ") { "${it.key}=${it.value}" } + " }"
        text = "#${e.seq}  ${timeFmt.format(Date(e.ts))}\n${e.type}$serial$data"
        setTextColor(ContextCompat.getColor(this@DiagnosticsActivity, R.color.ng_text_secondary))
        typeface = android.graphics.Typeface.MONOSPACE
        textSize = 12f
        setPadding(0, dp(6), 0, dp(6))
        gravity = Gravity.START
    }

    // --- self-test ---

    private fun runSelfTest() {
        binding.selftestResult.text = getString(R.string.diag_selftest_running)
        binding.btnSelftest.isEnabled = false
        lifecycleScope.launch {
            val result = bridge.getState()
            binding.btnSelftest.isEnabled = true
            binding.selftestResult.text = when (result) {
                is BridgeResult.Ok ->
                    getString(R.string.diag_selftest_ok, result.value.data.watches.size)
                is BridgeResult.Err ->
                    getString(R.string.diag_selftest_err, result.message, result.code)
            }
            // A successful getState may have refreshed the session; re-render.
            renderHandshake(bridge.currentSession)
            renderCapabilities(bridge.currentSession)
        }
    }

    // --- copy report ---

    private fun copyReport() {
        val report = buildString {
            appendLine("Pebble Tasker diagnostics")
            appendLine("status: ${bridge.status.value::class.simpleName}")
            appendLine()
            appendLine("[handshake]")
            appendLine(binding.handshakeBody.text)
            appendLine()
            appendLine("[capabilities]")
            appendLine(bridge.currentSession?.capabilities?.sorted()?.joinToString(", ") ?: "-")
            appendLine()
            appendLine("[grants]")
            appendLine(binding.grantsBody.text.ifBlank { "-" })
            appendLine()
            appendLine("[events]")
            appendLine(binding.eventsHighwater.text)
            val types = (knownEventTypes() + EventRouter.registeredTypes() + CachedEvent.TYPE_GAP).distinct()
            types.mapNotNull { cache.latest(it) }.sortedByDescending { it.seq }.forEach {
                appendLine("#${it.seq} ${it.type} ${it.data}")
            }
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("pebble-tasker-diagnostics", report))
        Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show()
    }

    // --- helpers ---

    /** The frozen event-type catalog from EventRouting, resilient if that object isn't on classpath. */
    private fun knownEventTypes(): List<String> = runCatching {
        listOf(
            EventRouting.TYPE_CONNECTED, EventRouting.TYPE_DISCONNECTED, EventRouting.TYPE_CONN_FAILED,
            EventRouting.TYPE_BATTERY, EventRouting.TYPE_NOTIF_SENT, EventRouting.TYPE_NOTIF_ACTION,
            EventRouting.TYPE_APP_CHANGED, EventRouting.TYPE_APPMSG, EventRouting.TYPE_TIMELINE,
            EventRouting.TYPE_MUSIC, EventRouting.TYPE_CALL, EventRouting.TYPE_FIRMWARE,
            EventRouting.TYPE_HEALTH, EventRouting.TYPE_DEV, EventRouting.TYPE_SYSTEM_ERROR,
        )
    }.getOrDefault(emptyList())

    private fun StringBuilder.line(labelRes: Int, value: String) {
        append(getString(labelRes)).append(": ").append(value).append('\n')
    }

    private fun yesNo(b: Boolean): String =
        getString(if (b) R.string.value_yes else R.string.value_no)

    private fun tokenPreview(token: String): String =
        if (token.length <= 8) token else token.take(8) + "…"

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
