package com.nickbether.pebbletasker.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Outcome { PENDING, RUNNING, PASS, FAIL, DEFERRED, SKIPPED, STOPPED;
    val final: Boolean get() = this == PASS || this == FAIL || this == DEFERRED || this == SKIPPED || this == STOPPED
}

enum class CheckId(val number: Int, val title: String) {
    TIME_SYNC(1, "Time sync"),
    SCREENSHOT(2, "Screenshot"),
    BUTTON_PRESS(3, "Button press"),
    BUSY_REFUSAL(4, "Busy refusal"),
    LONG_PRESS(5, "Long press (Quiet Time)"),
    SWIPE(6, "Touchscreen swipe"),
    APP_LAUNCH(7, "App launch and close"),
    PREF_SUPPORT(8, "Preference support"),
    FIRMWARE(9, "Firmware check"),
    LOG_DUMP(10, "Log dump"),
    REBOOT(11, "Reboot"),
}

data class CheckResult(val id: CheckId, val outcome: Outcome = Outcome.PENDING, val detail: String = "")

/** A single frame decoded from a screenshot: ARGB pixels, row-major. */
class Frame(val width: Int, val height: Int, val pixels: IntArray)

object PixelDiff {
    /** A screen counts as changed above this fraction of differing pixels (a clock digit is far less). */
    const val CHANGED_THRESHOLD = 0.2

    /** Fraction of pixels that differ; 1.0 when the sizes differ or a frame is empty. */
    fun fraction(a: Frame, b: Frame): Double {
        if (a.width != b.width || a.height != b.height) return 1.0
        val n = a.width * a.height
        if (n <= 0 || a.pixels.size < n || b.pixels.size < n) return 1.0
        var diff = 0
        for (i in 0 until n) if (a.pixels[i] != b.pixels[i]) diff++
        return diff.toDouble() / n
    }

    fun changed(a: Frame, b: Frame): Boolean = fraction(a, b) > CHANGED_THRESHOLD

    fun format(f: Double): String = String.format(Locale.US, "%.3f", f)
}

/** PebbleOS version parsing for the remote-input gate. */
object FirmwareVersion {
    val REMOTE_INPUT_MIN = listOf(4, 35, 0)

    /** "v4.35.0-rc2" -> [4, 35, 0]; null when no version number is present. */
    fun parse(fw: String?): List<Int>? {
        val m = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""").find(fw ?: return null) ?: return null
        return listOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { "0" }.toInt())
    }

    /** True/false when known; null when the version cannot be read. */
    fun atLeast(fw: String?, min: List<Int>): Boolean? {
        val v = parse(fw) ?: return null
        for (i in 0 until 3) if (v[i] != min[i]) return v[i] > min[i]
        return true
    }
}

/** Everything a report shows besides the checks. */
data class DiagContext(
    val pluginVersion: String = "",
    val hostVersion: String? = null,
    val watchName: String? = null,
    val serial: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val tier: String? = null,
    val categories: List<String> = emptyList(),
)

data class DiagReport(
    val startedAt: Long,
    val finishedAt: Long,
    val context: DiagContext,
    val checks: List<CheckResult>,
    /** Preflight refusal or other reason the run stopped early. */
    val aborted: String? = null,
    val stopped: Boolean = false,
) {
    fun count(o: Outcome) = checks.count { it.outcome == o }
    val failed: List<CheckResult> get() = checks.filter { it.outcome == Outcome.FAIL }
    /** True when the run should be treated as a failure by automation. */
    val isFailure: Boolean get() = aborted != null || failed.isNotEmpty()

    fun summaryLine(): String = when {
        aborted != null -> "Not run: $aborted"
        else -> buildString {
            append("${count(Outcome.PASS)} passed, ${count(Outcome.FAIL)} failed, ${count(Outcome.DEFERRED)} deferred")
            count(Outcome.SKIPPED).takeIf { it > 0 }?.let { append(", $it skipped") }
            if (stopped) append(" (stopped)")
        }
    }

    /** Message for a Tasker error: which checks failed and why. */
    fun errorMessage(): String = when {
        aborted != null -> "Watch diagnostics did not run: $aborted"
        else -> "Watch diagnostics failed: " + failed.joinToString("; ") { "${it.id.number}. ${it.id.title}: ${it.detail}" } +
            " (${summaryLine()})"
    }

    fun toText(): String = buildString {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        appendLine("PebbleTasker watch diagnostics")
        appendLine("Run: ${fmt.format(Date(startedAt))} (${(finishedAt - startedAt).coerceAtLeast(0) / 1000} s)")
        appendLine("Plugin: ${context.pluginVersion}")
        appendLine("Pebble app: ${context.hostVersion ?: "unknown"}")
        appendLine("Watch: " + listOfNotNull(context.watchName, context.serial?.let { "serial $it" }).joinToString(", ").ifEmpty { "unknown" })
        appendLine("Model: ${context.model ?: "unknown"}; firmware: ${context.firmware ?: "unknown"}")
        appendLine("Grant: ${context.tier ?: "unknown"}; categories: ${context.categories.joinToString(", ").ifEmpty { "none" }}")
        appendLine("Result: ${summaryLine()}")
        appendLine()
        for (c in checks) {
            append(String.format(Locale.US, "%2d. %-26s %-8s", c.id.number, c.id.title, c.outcome.name))
            if (c.detail.isNotBlank()) append(" ").append(c.detail)
            appendLine()
        }
    }.trimEnd()
}
