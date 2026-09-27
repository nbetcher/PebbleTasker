package com.nickbether.pebbletasker.diagnostics

import com.nickbether.pebbletasker.bridge.BridgePort
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender.Type
import com.nickbether.pebbletasker.bridge.Jobs
import com.nickbether.pebbletasker.tasker.base.CommandTiers
import com.nickbether.pebbletasker.tasker.base.Tier
import com.nickbether.pebbletasker.tasker.prefs.PrefInfo
import com.nickbether.pebbletasker.tasker.prefs.WatchPrefs
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Runs the on-watch checks against the installed Pebble app, strictly one after another, using only
 * the bridge (commands and events), exactly like the Tasker actions.
 *
 * Stop = cancel the calling coroutine. Cleanup (Back presses to the watchface, restoring Quiet Time)
 * runs even then. Factory reset is never sent; firmware install is sent only when the check reported
 * no update, and only to confirm the refusal.
 */
class DiagnosticsEngine(
    private val port: BridgePort,
    private val decode: (ByteArray) -> Frame?,
    private val options: Options,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onUpdate: (List<CheckResult>) -> Unit = {},
) {
    data class Options(
        val serial: String? = null,
        val includeReboot: Boolean = false,
        /** App tried first by the launch check. */
        val appUuid: String? = null,
        val pluginVersion: String = "",
    )

    companion object {
        const val SETTLE_MS = 1_500L
        const val HOME_WAIT_MS = 2_000L
        const val SCREEN_TIMEOUT_MS = 60_000L
        const val PREF_EVENT_TIMEOUT_MS = 20_000L
        const val APP_POLL_MS = 10_000L
        const val PREF_POLL_MS = 60_000L
        const val LOG_TIMEOUT_MS = 10 * 60_000L
        const val DISCONNECT_TIMEOUT_MS = 60_000L
        const val RECONNECT_TIMEOUT_MS = 3 * 60_000L
        const val REBOOT_COOLDOWN_MS = 60_000L
        const val QUIET_TIME_UUID = "2220d805-cf9a-4e12-92b9-5ca778aff6bb"
        const val PREF_QL_BACK = "qlBack"
        const val PREF_DND = "dndManuallyEnabled"

        val REQUIRED_COMMANDS = listOf(
            Type.WATCH_GET_INFO, Type.WATCH_SYNC_TIME, Type.WATCH_SCREENSHOT, Type.WATCH_PRESS_BUTTON,
            Type.WATCH_SWIPE, Type.WATCH_GET_PREF, Type.WATCH_SET_PREF, Type.WATCH_LIST_PREFS,
            Type.WATCH_LAUNCH_APP, Type.WATCH_STOP_APP, Type.SYSTEM_GET_LOCKER, Type.WATCH_CHECK_FIRMWARE,
            Type.WATCH_INSTALL_FIRMWARE, Type.WATCH_GATHER_LOGS, Type.WATCH_REBOOT,
        )
        const val CAP_OWNER_TARGETED = "events.owner_targeted"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    private class CheckEnd(val outcome: Outcome, val detail: String) : Exception(detail)

    private val results = CheckId.entries.map { CheckResult(it) }.toMutableList()
    private var serial: String? = null
    private var firmware: String? = null
    private var remoteInputUsed = false
    private var remoteInputOk: Boolean? = null
    /** Quiet Time value to restore, while the long-press check may have changed it. */
    private var quietTimeOriginal: String? = null
    private var context = DiagContext(pluginVersion = options.pluginVersion)
    private val startedAt = clock()

    fun snapshot(): List<CheckResult> = synchronized(results) { results.toList() }

    private fun set(id: CheckId, outcome: Outcome, detail: String = "") {
        synchronized(results) { results[id.ordinal] = CheckResult(id, outcome, detail) }
        onUpdate(snapshot())
    }

    /** Builds a report of the current state; used after a stop too. */
    fun report(aborted: String? = null, stopped: Boolean = false): DiagReport {
        val checks = snapshot().map {
            if (stopped && !it.outcome.final) it.copy(outcome = Outcome.STOPPED, detail = it.detail.ifBlank { "Stopped before it finished" }) else it
        }
        return DiagReport(startedAt, clock(), context, checks, aborted, stopped)
    }

    suspend fun run(): DiagReport {
        preflight()?.let { return report(aborted = it) }
        try {
            check(CheckId.TIME_SYNC) { timeSync() }
            check(CheckId.SCREENSHOT) { screenshot() }
            check(CheckId.BUTTON_PRESS) { buttonPress() }
            check(CheckId.BUSY_REFUSAL) { busyRefusal() }
            check(CheckId.LONG_PRESS) { longPress() }
            check(CheckId.SWIPE) { swipe() }
            check(CheckId.APP_LAUNCH) { appLaunch() }
            check(CheckId.PREF_SUPPORT) { prefSupport() }
            check(CheckId.FIRMWARE) { firmwareCheck() }
            check(CheckId.LOG_DUMP) { logDump() }
            if (options.includeReboot) check(CheckId.REBOOT) { reboot() }
            else set(CheckId.REBOOT, Outcome.SKIPPED, "Opt-in switch off")
        } finally {
            withContext(NonCancellable) { cleanup() }
        }
        return report()
    }

    private suspend fun check(id: CheckId, body: suspend () -> Pair<Outcome, String>) {
        set(id, Outcome.RUNNING)
        val (outcome, detail) = try { body() } catch (e: CheckEnd) { e.outcome to e.detail }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Outcome.FAIL to "Internal error: ${e.message ?: e::class.java.simpleName}" }
        set(id, outcome, detail)
    }

    private fun progress(id: CheckId, detail: String) = set(id, Outcome.RUNNING, detail)

    // ─────────────────────────────── preflight ───────────────────────────────

    /** Null when ready; otherwise why the run cannot start. */
    private suspend fun preflight(): String? {
        val session = when (val r = port.ready()) {
            is BridgeResult.Err -> return "The Pebble bridge is not ready (${r.bridgeCode ?: r.code}): ${r.message}"
            is BridgeResult.Ok -> r.value
        }
        context = context.copy(hostVersion = session.appVersion, tier = session.grants.tier, categories = session.grants.categories)
        val missing = REQUIRED_COMMANDS.filter { "command.$it" !in session.capabilities } +
            listOf(CAP_OWNER_TARGETED).filter { it !in session.capabilities }
        if (missing.isNotEmpty()) return "Update the Pebble app. This version does not offer: ${missing.joinToString(", ")}"
        val tier = Tier.parse(session.grants.tier)
        if (tier != null && tier < Tier.DANGEROUS)
            return "This plugin has the ${tier.label.lowercase()} command tier; diagnostics need dangerous. " +
                "Raise it in the Pebble app and turn on \"Allow dangerous commands\"."
        if (session.grants.categories.none { it.equals("system", ignoreCase = true) })
            return "Allow the system event category for this plugin in the Pebble app (screenshot and log results, preference changes)."
        val watches = when (val r = port.watches()) {
            is BridgeResult.Err -> return "Could not list watches: ${r.message}"
            is BridgeResult.Ok -> r.value
        }
        val wanted = options.serial?.trim()?.ifEmpty { null }
        val watch = when {
            wanted != null -> watches.firstOrNull { it.serial == wanted || it.address == wanted }
                ?: return "Watch $wanted is not connected"
            watches.isEmpty() -> return "No watch is connected"
            watches.size > 1 -> return "Several watches are connected; choose one (${watches.joinToString(", ") { it.serial }})"
            else -> watches.single()
        }
        serial = watch.serial.ifBlank { watch.address }
        val info = when (val r = port.execute(Type.WATCH_GET_INFO, serial)) {
            is BridgeResult.Err -> return "watch.getInfo failed: ${r.bridgeCode ?: r.code} ${r.message}"
            is BridgeResult.Ok -> r.value
        }
        firmware = info["fw"] ?: watch.fw
        context = context.copy(
            watchName = info["nickname"]?.ifBlank { null } ?: info["name"] ?: watch.name,
            serial = serial, model = info["model"] ?: watch.model, firmware = firmware,
        )
        remoteInputOk = FirmwareVersion.atLeast(firmware, FirmwareVersion.REMOTE_INPUT_MIN)
        return null
    }

    // ─────────────────────────────── helpers ───────────────────────────────

    private suspend fun cmd(type: String, args: Map<String, String> = emptyMap()) = port.execute(type, serial, args)

    /** Why a refused command ends a check. */
    private fun failure(err: BridgeResult.Err, type: String, what: String = type): CheckEnd = when (err.bridgeCode) {
        "RATE_LIMITED" -> CheckEnd(Outcome.DEFERRED, "Cooldown: " + retryText(err.message))
        "COMMAND_NOT_AUTHORIZED" -> CheckEnd(Outcome.FAIL, "$what refused: " + CommandTiers.explain(err, type, port.session))
        "CATEGORY_DISABLED" -> CheckEnd(Outcome.FAIL, "$what refused: " + CommandTiers.explain(err, type, port.session))
        else -> CheckEnd(Outcome.FAIL, "$what: ${err.bridgeCode ?: "error ${err.code}"} ${err.message}".trim())
    }

    private fun retryText(message: String): String {
        val secs = Regex("""(\d+)\s*(s\b|sec|second)""").find(message)?.groupValues?.get(1)
            ?: Regex("""\d+""").find(message)?.value
        return if (secs != null) "retry in $secs s ($message)" else message
    }

    private fun gate() {
        if (remoteInputOk == false)
            throw CheckEnd(Outcome.DEFERRED, "Remote input needs PebbleOS 4.35.0 or later; this watch runs ${firmware ?: "unknown"}")
    }

    private suspend fun press(button: String, presses: Int = 1, holdMs: Int = 50, gapMs: Int = 100): BridgeResult<Map<String, String>> {
        remoteInputUsed = true
        return cmd(Type.WATCH_PRESS_BUTTON, mapOf(
            "button" to button, "presses" to presses.toString(), "hold_ms" to holdMs.toString(), "gap_ms" to gapMs.toString()))
    }

    /** Press, ending the check on refusal (UNSUPPORTED_COMMAND = older firmware = deferred). */
    private suspend fun pressOrEnd(button: String, presses: Int = 1, holdMs: Int = 50, gapMs: Int = 100) {
        val r = press(button, presses, holdMs, gapMs)
        if (r is BridgeResult.Err) {
            if (r.bridgeCode == "UNSUPPORTED_COMMAND") throw CheckEnd(Outcome.DEFERRED, "Remote input not supported by this watch (${r.message})")
            throw failure(r, Type.WATCH_PRESS_BUTTON, "Press $button")
        }
    }

    /** Back x4 to the watchface. A still-running earlier sequence (WATCH_BUSY) is waited out briefly. */
    private suspend fun goHome() {
        repeat(4) { attempt ->
            val r = press("back", presses = 4, gapMs = 250)
            if (r is BridgeResult.Err && r.bridgeCode == "WATCH_BUSY" && attempt < 3) { delay(1_000); return@repeat }
            if (r is BridgeResult.Err) {
                if (r.bridgeCode == "UNSUPPORTED_COMMAND") throw CheckEnd(Outcome.DEFERRED, "Remote input not supported by this watch (${r.message})")
                throw failure(r, Type.WATCH_PRESS_BUTTON, "Return to watchface")
            }
            delay(HOME_WAIT_MS)
            return
        }
    }

    private suspend fun screen(): Frame {
        val started = when (val s = Jobs.start(port, Type.WATCH_SCREENSHOT, serial)) {
            is Jobs.Start.Refused -> throw failure(s.err, Type.WATCH_SCREENSHOT, "Screenshot")
            is Jobs.Start.Started -> s
        }
        val done = Jobs.await(port, started, SCREEN_TIMEOUT_MS)
            ?: throw CheckEnd(Outcome.FAIL, "No job.done for screenshot ${started.jobId} within ${SCREEN_TIMEOUT_MS / 1000} s")
        if (!done.ok) throw CheckEnd(Outcome.FAIL, "Screenshot job failed: ${done.error ?: "no reason given"}")
        val uri = done.uri ?: throw CheckEnd(Outcome.FAIL, "Screenshot job finished without a URI")
        val bytes = port.read(uri) ?: throw CheckEnd(Outcome.FAIL, "Could not read screenshot $uri")
        return decode(bytes) ?: throw CheckEnd(Outcome.FAIL, "Screenshot is not a decodable image (${bytes.size} bytes)")
    }

    private fun diff(a: Frame, b: Frame) = PixelDiff.fraction(a, b)
    private fun fmt(f: Double) = PixelDiff.format(f)

    // ─────────────────────────────── checks ───────────────────────────────

    private suspend fun timeSync(): Pair<Outcome, String> = when (val r = cmd(Type.WATCH_SYNC_TIME)) {
        is BridgeResult.Ok ->
            if (r.value["verified"] == "true") Outcome.PASS to "Verified" + (r.value["skew_s"]?.let { ", skew $it s" } ?: "")
            else Outcome.FAIL to "Sync not verified (${r.value})"
        is BridgeResult.Err -> when (r.bridgeCode) {
            "INTERNAL" -> Outcome.FAIL to "Watch clock still off after sync: ${r.message}"
            "TIMEOUT" -> Outcome.FAIL to "Watch did not answer the clock read-back: ${r.message}"
            else -> throw failure(r, Type.WATCH_SYNC_TIME, "Time sync")
        }
    }

    private suspend fun screenshot(): Pair<Outcome, String> {
        val f = screen()
        return if (f.width > 0 && f.height > 0) Outcome.PASS to "${f.width}x${f.height}" else Outcome.FAIL to "Empty image ${f.width}x${f.height}"
    }

    private suspend fun buttonPress(): Pair<Outcome, String> {
        gate()
        goHome()
        val a = screen()
        pressOrEnd("select")
        delay(SETTLE_MS)
        val b = screen()
        val ab = diff(a, b)
        if (ab <= PixelDiff.CHANGED_THRESHOLD) return Outcome.FAIL to "Select did not open the launcher (changed ${fmt(ab)})"
        pressOrEnd("back")
        delay(SETTLE_MS)
        val c = screen()
        val bc = diff(b, c)
        if (bc <= PixelDiff.CHANGED_THRESHOLD) return Outcome.FAIL to "Back did not close the launcher (open ${fmt(ab)}, close ${fmt(bc)})"
        return Outcome.PASS to "Launcher opened (${fmt(ab)}) and closed (${fmt(bc)})"
    }

    private suspend fun busyRefusal(): Pair<Outcome, String> {
        gate()
        goHome()
        pressOrEnd("back", presses = 3, gapMs = 400)
        val second = press("back")
        delay(HOME_WAIT_MS)
        return when {
            second is BridgeResult.Err && second.bridgeCode == "WATCH_BUSY" -> Outcome.PASS to "Second sequence refused with WATCH_BUSY"
            second is BridgeResult.Err -> Outcome.FAIL to "Expected WATCH_BUSY, got ${second.bridgeCode ?: second.code}: ${second.message}"
            else -> Outcome.FAIL to "Second sequence was accepted while the first was running"
        }
    }

    private suspend fun getPref(key: String): PrefInfo = when (val r = cmd(Type.WATCH_GET_PREF, mapOf("pref_key" to key))) {
        is BridgeResult.Ok -> WatchPrefs.fromFlat(r.value) ?: throw CheckEnd(Outcome.FAIL, "watch.getPref $key returned no pref_key")
        is BridgeResult.Err ->
            if (r.bridgeCode == "PREF_UNSUPPORTED") throw CheckEnd(Outcome.DEFERRED, "$key is not supported by this watch")
            else throw failure(r, Type.WATCH_GET_PREF, "Read $key")
    }

    private fun quietTimeOnBack(value: String?): Boolean {
        val o = runCatching { json.parseToJsonElement(value ?: return false).jsonObject }.getOrNull() ?: return false
        val enabled = o["enabled"]?.jsonPrimitive?.content == "true"
        val uuid = o["uuid"]?.jsonPrimitive?.content
        return enabled && uuid.equals(QUIET_TIME_UUID, ignoreCase = true)
    }

    private suspend fun holdBackAndAwait(expected: String): Boolean {
        val mark = port.eventMark()
        goHome()
        pressOrEnd("back", holdMs = 1_500)
        return port.awaitEvent(mark, PREF_EVENT_TIMEOUT_MS) {
            it.type == "watch.pref" && it.str("pref_key") == PREF_DND && it.str("value")?.trim()?.lowercase() == expected
        } != null
    }

    private fun normBool(v: String?): String = if (v?.trim()?.lowercase() in setOf("true", "1", "on")) "true" else "false"

    private suspend fun longPress(): Pair<Outcome, String> {
        gate()
        val ql = getPref(PREF_QL_BACK)
        if (!quietTimeOnBack(ql.value)) return Outcome.DEFERRED to "Holding Back is not set to toggle Quiet Time (qlBack=${ql.value})"
        val original = normBool(getPref(PREF_DND).value)
        val flipped = if (original == "true") "false" else "true"
        quietTimeOriginal = original
        var restoreAttempted = false
        try {
            val flippedSeen = holdBackAndAwait(flipped)
            val restored = restoreQuietTime(original)
            restoreAttempted = true
            return when {
                !flippedSeen -> Outcome.FAIL to "No watch.pref $PREF_DND=$flipped within ${PREF_EVENT_TIMEOUT_MS / 1000} s of holding Back" +
                    if (restored.second) "" else "; ${restored.first}"
                !restored.second -> Outcome.FAIL to "Quiet Time turned $flipped but ${restored.first}"
                else -> Outcome.PASS to "Quiet Time turned $flipped and back to $original (${restored.first})"
            }
        } finally {
            // Always restore, also when the check ended early or the run was stopped.
            withContext(NonCancellable) {
                if (!restoreAttempted && quietTimeOriginal != null) runCatching { restoreQuietTime(original) }
                quietTimeOriginal = null
            }
        }
    }

    /**
     * Returns Quiet Time to [original]: reads the current value, holds Back again if it differs, and
     * falls back to watch.setPref. Clears [quietTimeOriginal] once the value matches.
     */
    private suspend fun restoreQuietTime(original: String): Pair<String, Boolean> {
        suspend fun current(): String? = (cmd(Type.WATCH_GET_PREF, mapOf("pref_key" to PREF_DND)) as? BridgeResult.Ok)?.value?.get("value")?.let(::normBool)
        if (current() == original) { quietTimeOriginal = null; return "already $original" to true }
        val viaHold = runCatching { holdBackAndAwait(original) }.getOrDefault(false)
        if (viaHold) { quietTimeOriginal = null; return "restored by holding Back" to true }
        val set = cmd(Type.WATCH_SET_PREF, mapOf("pref_key" to PREF_DND, "pref_value" to original))
        if (set is BridgeResult.Ok && current() == original) { quietTimeOriginal = null; return "restored with watch.setPref" to true }
        return "could not restore Quiet Time to $original" to false
    }

    private suspend fun swipe(): Pair<Outcome, String> {
        gate()
        val measured = mutableListOf<String>()
        try {
            for (dir in listOf("left", "right", "up", "down")) {
                goHome()
                val a = screen()
                remoteInputUsed = true
                val r = cmd(Type.WATCH_SWIPE, mapOf("direction" to dir, "duration_ms" to "150"))
                if (r is BridgeResult.Err) when (r.bridgeCode) {
                    "INVALID_ARGS" -> return Outcome.DEFERRED to "No touch screen, or touch is off (${r.message})"
                    "UNSUPPORTED_COMMAND" -> return Outcome.DEFERRED to "Swipe not supported by this watch (${r.message})"
                    else -> throw failure(r, Type.WATCH_SWIPE, "Swipe $dir")
                }
                delay(SETTLE_MS)
                val b = screen()
                val d = diff(a, b)
                measured += "$dir ${fmt(d)}"
                if (d > PixelDiff.CHANGED_THRESHOLD) return Outcome.PASS to "Swipe $dir changed the screen (${measured.joinToString(", ")})"
            }
            return Outcome.DEFERRED to "Swipes accepted but no visible change; check touch by hand (${measured.joinToString(", ")})"
        } finally {
            withContext(NonCancellable) { runCatching { goHome() } }
        }
    }

    private suspend fun runningApp(): String? = (cmd(Type.WATCH_GET_INFO) as? BridgeResult.Ok)?.value?.get("running_app")

    private suspend fun pollRunning(timeoutMs: Long, predicate: (String?) -> Boolean): Boolean {
        val deadline = clock() + timeoutMs
        while (true) {
            if (predicate(runningApp())) return true
            if (clock() >= deadline) return false
            delay(1_000)
        }
    }

    private fun lockerApps(raw: String?): List<String> = runCatching {
        json.parseToJsonElement(raw ?: return emptyList()).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val type = o["type"]?.jsonPrimitive?.content
            if (type != null && type != "watchapp") return@mapNotNull null
            o["uuid"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    private suspend fun appLaunch(): Pair<Outcome, String> {
        val locker = when (val r = cmd(Type.SYSTEM_GET_LOCKER, mapOf("type" to "watchapp"))) {
            is BridgeResult.Ok -> lockerApps(r.value["entries"])
            is BridgeResult.Err -> if (options.appUuid.isNullOrBlank()) throw failure(r, Type.SYSTEM_GET_LOCKER, "Locker") else emptyList()
        }
        val candidates = (listOfNotNull(options.appUuid?.trim()?.ifEmpty { null }) + locker).distinctBy { it.lowercase() }.take(3)
        if (candidates.isEmpty()) return Outcome.DEFERRED to "No watchapps in the locker to launch"
        var launched: String? = null
        val tried = mutableListOf<String>()
        for (uuid in candidates) {
            val r = cmd(Type.WATCH_LAUNCH_APP, mapOf("uuid" to uuid))
            if (r is BridgeResult.Err) { tried += "$uuid: ${r.bridgeCode ?: r.code}"; continue }
            if (pollRunning(APP_POLL_MS) { it.equals(uuid, ignoreCase = true) }) { launched = uuid; break }
            tried += "$uuid: not running after ${APP_POLL_MS / 1000} s"
        }
        val uuid = launched ?: return Outcome.DEFERRED to "No candidate app started (${tried.joinToString("; ")})"
        val stop = cmd(Type.WATCH_STOP_APP)
        if (stop is BridgeResult.Err) throw failure(stop, Type.WATCH_STOP_APP, "Stop app")
        val stopped = (stop as BridgeResult.Ok).value["uuid"]
        if (!stopped.equals(uuid, ignoreCase = true)) return Outcome.FAIL to "watch.stopApp returned $stopped, expected $uuid"
        if (!pollRunning(APP_POLL_MS) { !it.equals(uuid, ignoreCase = true) })
            return Outcome.FAIL to "$uuid still running ${APP_POLL_MS / 1000} s after watch.stopApp"
        return Outcome.PASS to "Launched and stopped $uuid"
    }

    private suspend fun prefSupport(): Pair<Outcome, String> {
        val deadline = clock() + PREF_POLL_MS
        var prefs: List<PrefInfo>
        while (true) {
            prefs = when (val r = cmd(Type.WATCH_LIST_PREFS)) {
                is BridgeResult.Ok -> WatchPrefs.parseList(r.value["prefs"])
                is BridgeResult.Err -> throw failure(r, Type.WATCH_LIST_PREFS, "List preferences")
            }
            if (prefs.any { it.support == PrefInfo.SUPPORT_SUPPORTED }) break
            if (clock() >= deadline) return Outcome.FAIL to "No preference reported as supported within ${PREF_POLL_MS / 1000} s (${prefs.size} listed)"
            progress(CheckId.PREF_SUPPORT, "Waiting for the watch's settings sync (${prefs.size} listed, none supported yet)")
            delay(5_000)
        }
        val supported = prefs.filter { it.support == PrefInfo.SUPPORT_SUPPORTED }
        val probe = supported.first()
        val read = getPref(probe.key)
        if (read.support != PrefInfo.SUPPORT_SUPPORTED)
            return Outcome.FAIL to "watch.listPrefs says ${probe.key} is supported but watch.getPref says ${read.support}"
        val excluded = setOf(PREF_DND, PREF_QL_BACK)
        val roundTrip = supported.firstOrNull { p -> p.key !in excluded && p.options.isNotEmpty() && p.options.any { it.value == p.value } }
            ?: supported.firstOrNull { it.key !in excluded && it.editor == PrefInfo.Editor.BOOLEAN && it.value != null }
        val summary = "${supported.size} of ${prefs.size} supported; ${probe.key} confirmed"
        if (roundTrip == null) return Outcome.PASS to "$summary; no option-typed key to round-trip"
        return when (val set = cmd(Type.WATCH_SET_PREF, mapOf("pref_key" to roundTrip.key, "pref_value" to roundTrip.value!!))) {
            is BridgeResult.Ok -> Outcome.PASS to "$summary; ${roundTrip.key}=${roundTrip.value} round-tripped" +
                (set.value["watch_status"]?.let { " (watch: $it)" } ?: "")
            is BridgeResult.Err -> Outcome.FAIL to "Setting ${roundTrip.key} to its listed value ${roundTrip.value} failed: ${set.bridgeCode ?: set.code} ${set.message}"
        }
    }

    private suspend fun firmwareCheck(): Pair<Outcome, String> {
        val r = cmd(Type.WATCH_CHECK_FIRMWARE, mapOf("force" to "true"))
        if (r is BridgeResult.Err) throw failure(r, Type.WATCH_CHECK_FIRMWARE, "Firmware check")
        val data = (r as BridgeResult.Ok).value
        return when (val status = data["status"]) {
            "failed", "pending" -> Outcome.DEFERRED to "Check $status (network or update server)"
            // Never call install when an update is offered: it would start the update.
            "available" -> Outcome.PASS to "Update ${data["version"] ?: ""} available; install not attempted".replace("  ", " ")
            "none" -> when (val install = cmd(Type.WATCH_INSTALL_FIRMWARE)) {
                is BridgeResult.Err -> when (install.bridgeCode) {
                    "FIRMWARE_UPDATE_UNAVAILABLE" -> Outcome.PASS to "No update; install refused with FIRMWARE_UPDATE_UNAVAILABLE"
                    "FIRMWARE_CHECK_STALE" -> Outcome.FAIL to "Install refused with FIRMWARE_CHECK_STALE right after a successful check: the check time was not recorded"
                    else -> Outcome.FAIL to "Install refused with ${install.bridgeCode ?: install.code}, expected FIRMWARE_UPDATE_UNAVAILABLE: ${install.message}"
                }
                is BridgeResult.Ok -> Outcome.FAIL to "Install was accepted although the check found no update (${install.value})"
            }
            else -> Outcome.FAIL to "Unexpected status \"$status\""
        }
    }

    private fun looksLikeText(bytes: ByteArray, mime: String?): Boolean {
        if (mime?.lowercase()?.startsWith("text/") == true) return true
        val sample = bytes.take(4096)
        val printable = sample.count { b -> val c = b.toInt() and 0xFF; c == 9 || c == 10 || c == 13 || c in 32..126 || c >= 128 }
        return printable >= sample.size * 0.9
    }

    private suspend fun logDump(): Pair<Outcome, String> {
        val started = when (val s = Jobs.start(port, Type.WATCH_GATHER_LOGS, serial)) {
            is Jobs.Start.Refused -> {
                if (s.err.bridgeCode == "COMMAND_NOT_AUTHORIZED")
                    return Outcome.DEFERRED to "Needs the extremely dangerous tier: " + CommandTiers.explain(s.err, Type.WATCH_GATHER_LOGS, port.session)
                throw failure(s.err, Type.WATCH_GATHER_LOGS, "Log dump")
            }
            is Jobs.Start.Started -> s
        }
        val second = cmd(Type.WATCH_GATHER_LOGS)
        val busyNote = when {
            second is BridgeResult.Err && second.bridgeCode == "WATCH_BUSY" -> null
            second is BridgeResult.Err -> "second dump refused with ${second.bridgeCode ?: second.code}, expected WATCH_BUSY"
            else -> "second dump was accepted (job ${(second as BridgeResult.Ok).value["job_id"]}), expected WATCH_BUSY"
        }
        val begin = clock()
        var done: Jobs.Done? = null
        while (done == null && clock() - begin < LOG_TIMEOUT_MS) {
            progress(CheckId.LOG_DUMP, "Waiting for the log dump… ${(clock() - begin) / 1000} s of ${LOG_TIMEOUT_MS / 1000} s")
            done = Jobs.await(port, started, minOf(5_000L, LOG_TIMEOUT_MS - (clock() - begin)).coerceAtLeast(1))
        }
        if (done == null) return Outcome.FAIL to "No job.done for log dump ${started.jobId} within ${LOG_TIMEOUT_MS / 60_000} min" + (busyNote?.let { "; $it" } ?: "")
        if (!done.ok) return Outcome.FAIL to "Log dump failed: ${done.error ?: "no reason given"}" + (busyNote?.let { "; $it" } ?: "")
        val bytes = done.uri?.let { port.read(it) }
        return when {
            bytes == null -> Outcome.FAIL to "Could not read the log file ${done.uri}"
            bytes.isEmpty() -> Outcome.FAIL to "Log file is empty"
            !looksLikeText(bytes, done.mime) -> Outcome.FAIL to "Log file is not text (${done.mime}, ${bytes.size} bytes)"
            busyNote != null -> Outcome.FAIL to "Log dump ok (${bytes.size} bytes) but $busyNote"
            else -> Outcome.PASS to "${bytes.size} bytes of logs in ${(clock() - begin) / 1000} s; second dump refused with WATCH_BUSY"
        }
    }

    private fun forThisWatch(e: com.nickbether.pebbletasker.cache.CachedEvent): Boolean =
        e.watch?.let { it.serial == serial || it.address == serial } ?: false

    private suspend fun reboot(): Pair<Outcome, String> {
        if (port.session?.grants?.categories?.none { it.equals("connectivity", ignoreCase = true) } == true)
            return Outcome.DEFERRED to "Needs the connectivity event category to observe the reboot"
        val mark = port.eventMark()
        val sentAt = clock()
        val r = cmd(Type.WATCH_REBOOT)
        if (r is BridgeResult.Err) throw failure(r, Type.WATCH_REBOOT, "Reboot")
        val down = port.awaitEvent(mark, DISCONNECT_TIMEOUT_MS) { it.type == "watch.disconnected" && forThisWatch(it) }
            ?: return Outcome.FAIL to "No watch.disconnected within ${DISCONNECT_TIMEOUT_MS / 1000} s of the reboot"
        progress(CheckId.REBOOT, "Watch disconnected; waiting for it to reconnect")
        port.awaitEvent(mark, RECONNECT_TIMEOUT_MS) { it.type == "watch.connected" && forThisWatch(it) && it.seq > down.seq }
            ?: return Outcome.FAIL to "Watch did not reconnect within ${RECONNECT_TIMEOUT_MS / 60_000} min"
        val reconnectedAfter = (clock() - sentAt) / 1000
        if (clock() - sentAt >= REBOOT_COOLDOWN_MS)
            return Outcome.PASS to "Rebooted and reconnected after $reconnectedAfter s; cooldown sub-check skipped (outside 60 s)"
        return when (val again = cmd(Type.WATCH_REBOOT)) {
            is BridgeResult.Err ->
                if (again.bridgeCode == "RATE_LIMITED") Outcome.PASS to "Rebooted and reconnected after $reconnectedAfter s; second reboot refused with RATE_LIMITED"
                else Outcome.FAIL to "Second reboot refused with ${again.bridgeCode ?: again.code}, expected RATE_LIMITED"
            is BridgeResult.Ok -> Outcome.FAIL to "Second reboot within the 60 s cooldown was accepted"
        }
    }

    // ─────────────────────────────── cleanup ───────────────────────────────

    private suspend fun cleanup() {
        quietTimeOriginal?.let { runCatching { restoreQuietTime(it) } }
        if (remoteInputUsed && remoteInputOk != false) runCatching {
            repeat(4) {
                val r = press("back", presses = 4, gapMs = 250)
                if (r is BridgeResult.Err && r.bridgeCode == "WATCH_BUSY") delay(1_000) else return@runCatching
            }
        }
    }
}
