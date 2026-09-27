package com.nickbether.pebbletasker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.databinding.ActivityWatchDiagnosticsBinding
import com.nickbether.pebbletasker.diagnostics.CheckResult
import com.nickbether.pebbletasker.diagnostics.DiagnosticsEngine
import com.nickbether.pebbletasker.diagnostics.DiagnosticsRunner
import com.nickbether.pebbletasker.diagnostics.Outcome
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.util.applyContentInsets
import kotlinx.coroutines.launch

/**
 * Launcher entry point for the on-watch checks. Results stream live from [DiagnosticsRunner]; the run
 * survives rotation and leaving the screen. Stop sends Back presses and restores Quiet Time.
 */
class WatchDiagnosticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWatchDiagnosticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatchDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyContentInsets()

        CriteriaDropdown.attachWatchSerial(binding.wdiagLayoutSerial)
        CriteriaDropdown.attach(binding.wdiagLayoutApp, CriteriaDropdown.Source.LOCKER_APP)
        binding.wdiagStart.setOnClickListener { confirmAndStart() }
        binding.wdiagStop.setOnClickListener { DiagnosticsRunner.stop() }
        binding.wdiagShare.setOnClickListener { reportText()?.let(::share) }
        binding.wdiagCopy.setOnClickListener { reportText()?.let(::copy) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DiagnosticsRunner.interactive.collect { render(it) }
            }
        }
    }

    private fun reportText(): String? =
        DiagnosticsRunner.interactive.value.report?.toText() ?: DiagnosticsRunner.lastReportText(this)

    private fun confirmAndStart() {
        val reboot = binding.wdiagReboot.isChecked
        val message = getString(R.string.wdiag_warning) +
            if (reboot) "\n\nThe reboot check is on: the watch will restart once." else ""
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.wdiag_confirm_title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.wdiag_confirm_ok) { _, _ ->
                val options = DiagnosticsEngine.Options(
                    serial = binding.wdiagSerial.text?.toString()?.trim()?.ifEmpty { null },
                    includeReboot = reboot,
                    appUuid = binding.wdiagApp.text?.toString()?.trim()?.ifEmpty { null },
                    pluginVersion = DiagnosticsRunner.pluginVersion,
                )
                if (!DiagnosticsRunner.startInteractive(this, options))
                    Toast.makeText(this, R.string.wdiag_busy, Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun render(state: DiagnosticsRunner.State) {
        binding.wdiagStart.isEnabled = !state.running
        binding.wdiagStop.isEnabled = state.running
        listOf(binding.wdiagSerial, binding.wdiagApp, binding.wdiagReboot).forEach { it.isEnabled = !state.running }
        val hasReport = state.report != null || DiagnosticsRunner.lastReportText(this) != null
        binding.wdiagShare.isEnabled = !state.running && hasReport
        binding.wdiagCopy.isEnabled = !state.running && hasReport

        val report = state.report
        binding.wdiagSummary.text = when {
            state.running -> "Running… " + (state.checks.firstOrNull { it.outcome == Outcome.RUNNING }?.let { "${it.id.number}. ${it.id.title}" } ?: "preflight")
            report != null -> report.summaryLine() + (report.context.firmware?.let { " · ${report.context.model ?: "watch"} ${it}" } ?: "")
            else -> DiagnosticsRunner.lastReportText(this)?.lineSequence()?.firstOrNull { it.startsWith("Result:") }
                ?.let { "Last run — $it" } ?: getString(R.string.wdiag_never_run)
        }
        binding.wdiagChecks.removeAllViews()
        if (report?.aborted != null) {
            binding.wdiagChecks.addView(row("STOP", R.color.ng_red, "Not run", report.aborted))
            return
        }
        if (!state.running && report == null) {
            DiagnosticsRunner.lastReportText(this)?.let { binding.wdiagChecks.addView(mono(it)) }
            return
        }
        for (c in state.checks) binding.wdiagChecks.addView(checkRow(c))
    }

    private fun checkRow(c: CheckResult): View {
        val color = when (c.outcome) {
            Outcome.PASS -> R.color.ng_green
            Outcome.FAIL -> R.color.ng_red
            Outcome.DEFERRED -> R.color.ng_orange
            Outcome.RUNNING -> R.color.ng_cyan
            else -> R.color.ng_text_dim
        }
        return row(c.outcome.name, color, "${c.id.number}. ${c.id.title}", c.detail)
    }

    private fun row(tag: String, colorRes: Int, title: String, detail: String): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
            addView(TextView(context).apply {
                text = "$tag  ·  $title"
                setTextColor(ContextCompat.getColor(context, colorRes))
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 13f
            })
            if (detail.isNotBlank()) addView(TextView(context).apply {
                text = detail
                setTextColor(ContextCompat.getColor(context, R.color.ng_text_secondary))
                textSize = 12f
                setTextIsSelectable(true)
            })
        }
    }

    private fun mono(text: String) = TextView(this).apply {
        this.text = text
        typeface = Typeface.MONOSPACE
        textSize = 11f
        setTextColor(ContextCompat.getColor(context, R.color.ng_text_secondary))
        setTextIsSelectable(true)
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PebbleTasker watch diagnostics")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.wdiag_share)))
    }

    private fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("pebble-watch-diagnostics", text))
        Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show()
    }
}
