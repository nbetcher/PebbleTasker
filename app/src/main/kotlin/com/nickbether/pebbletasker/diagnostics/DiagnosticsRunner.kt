package com.nickbether.pebbletasker.diagnostics

import android.content.Context
import android.graphics.BitmapFactory
import com.nickbether.pebbletasker.BuildConfig
import com.nickbether.pebbletasker.bridge.AndroidBridgePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Hosts diagnostics runs. The launcher screen and the Tasker action are separate entry points: the
 * screen's run, live results and saved report live here in [interactive]; a Tasker run reports only
 * through its own result. They share one lock so two runs never drive the same watch at once.
 */
object DiagnosticsRunner {
    data class State(
        val running: Boolean = false,
        val checks: List<CheckResult> = CheckId.entries.map { CheckResult(it) },
        val report: DiagReport? = null,
    )

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _interactive = MutableStateFlow(State())
    val interactive: StateFlow<State> = _interactive.asStateFlow()
    private var job: Job? = null

    private const val PREFS = "pb_watch_diagnostics"
    private const val KEY_LAST_REPORT = "last_report"

    val pluginVersion: String get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    fun decodePng(bytes: ByteArray): Frame? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            Frame(bmp.width, bmp.height, px)
        } finally { bmp.recycle() }
    }

    private fun busyReport(options: DiagnosticsEngine.Options): DiagReport {
        val now = System.currentTimeMillis()
        return DiagReport(now, now, DiagContext(options.pluginVersion, serial = options.serial),
            CheckId.entries.map { CheckResult(it) }, aborted = "Another watch diagnostics run is in progress")
    }

    /** Launcher run: results stream into [interactive]; the report is kept for Share/Export. */
    fun startInteractive(context: Context, options: DiagnosticsEngine.Options): Boolean {
        val app = context.applicationContext
        if (_interactive.value.running || !lock.tryLock()) return false
        _interactive.value = State(running = true)
        job = scope.launch {
            val engine = DiagnosticsEngine(AndroidBridgePort(app), ::decodePng, options,
                onUpdate = { checks -> _interactive.value = _interactive.value.copy(checks = checks) })
            val report = try {
                engine.run()
            } catch (e: CancellationException) {
                engine.report(stopped = true)
            } catch (e: Exception) {
                engine.report(aborted = "Internal error: ${e.message}")
            } finally {
                lock.unlock()
            }
            withContext(NonCancellable) {
                save(app, report)
                _interactive.value = State(running = false, checks = report.checks, report = report)
            }
        }
        return true
    }

    /** Stop: cancels the run; the engine returns the watch to its watchface and restores Quiet Time. */
    fun stop() { job?.cancel() }

    /** Tasker run: blocks the caller's coroutine and returns its own report. */
    suspend fun runHeadless(context: Context, options: DiagnosticsEngine.Options): DiagReport {
        if (!lock.tryLock()) return busyReport(options)
        return try {
            DiagnosticsEngine(AndroidBridgePort(context.applicationContext), ::decodePng, options).run()
        } finally { lock.unlock() }
    }

    private fun save(context: Context, report: DiagReport) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_REPORT, report.toText()).apply()
    }

    /** The launcher's last report as text, kept across restarts. */
    fun lastReportText(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_REPORT, null)
}
