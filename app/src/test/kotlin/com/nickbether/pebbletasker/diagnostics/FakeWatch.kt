package com.nickbether.pebbletasker.diagnostics

import com.nickbether.pebbletasker.bridge.BridgePort
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.BridgeSession
import com.nickbether.pebbletasker.bridge.CommandSender.Type
import com.nickbether.pebbletasker.bridge.dto.Grants
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.EventTap
import com.nickbether.pebbletasker.tasker.ErrCodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A scripted watch behind the bridge, driven in virtual time. Screens are 10x10 frames filled with
 * one value: 0 = watchface, 1 = launcher, 2 = swipe target, 3 = an app.
 */
class FakeWatch(private val scope: CoroutineScope, private val now: () -> Long) : BridgePort {
    val serialNo = "SER1"
    var capabilities: Set<String> = (DiagnosticsEngine.REQUIRED_COMMANDS.map { "command.$it" } + DiagnosticsEngine.CAP_OWNER_TARGETED).toSet()
    var grants = Grants(categories = listOf("system", "connectivity"), tier = "dangerous")
    var fw = "v4.35.0"
    var touch = true
    var dnd = "false"
    var qlBack = """{"enabled":true,"uuid":"${DiagnosticsEngine.QUIET_TIME_UUID}"}"""
    /** Whether the watch syncs Quiet Time changes back (watch.pref events). */
    var dndEchoes = true
    /** Whether holding Back actually toggles Quiet Time on the watch. */
    var holdToggles = true
    var firmwareStatus = "none"
    var screen = 0
    var running: String? = null
    var readyError: BridgeResult.Err? = null
    val overrides = mutableMapOf<String, (Map<String, String>) -> BridgeResult<Map<String, String>>>()
    val calls = mutableListOf<Pair<String, Map<String, String>>>()
    private val tap = EventTap()
    private var seq = 0L
    private var busyUntil = -1L
    private var jobs = 0
    private var logsBusy = false
    private var lastReboot = Long.MIN_VALUE / 2
    val files = mutableMapOf<String, ByteArray>()

    override val session: BridgeSession get() = BridgeSession("t", "boot", 1, 0, "host-1.2", capabilities, grants)
    override suspend fun ready(): BridgeResult<BridgeSession> = readyError ?: BridgeResult.Ok(session)
    override suspend fun watches() = BridgeResult.Ok(listOf(WatchRef(serialNo, "Pebble Time 2", fw = fw)))
    override fun eventMark() = tap.mark()
    override suspend fun awaitEvent(after: Long, timeoutMs: Long, predicate: (CachedEvent) -> Boolean) = tap.await(after, timeoutMs, predicate)
    override suspend fun read(uri: String): ByteArray? = files[uri]

    fun publish(type: String, category: String, data: Map<String, String>, watch: Boolean = true) =
        tap.publish(listOf(CachedEvent(type, "boot", ++seq, now(), category, if (watch) WatchRef(serialNo, "Pebble") else null, data)))

    fun count(type: String) = calls.count { it.first == type }

    private fun ok(vararg kv: Pair<String, String>): BridgeResult<Map<String, String>> = BridgeResult.Ok(mapOf(*kv))
    private fun err(code: String, msg: String = code) = BridgeResult.Err(ErrCodes.toInt(code), msg, code)

    private fun changeDnd(value: String) {
        val previous = dnd
        dnd = value
        if (dndEchoes) publish("watch.pref", "system", mapOf("pref_key" to "dndManuallyEnabled", "label" to "Quiet Time", "value" to value, "previous" to previous), watch = false)
    }

    private fun job(command: String, bytes: ByteArray, mime: String, afterMs: Long = 0, extra: Map<String, String> = emptyMap()): String {
        val id = "job${++jobs}"
        val uri = "content://host/$id"
        files[uri] = bytes
        val publish = { publish("job.done", "system", mapOf("job_id" to id, "command" to command, "status" to "ok", "uri" to uri, "mime" to mime) + extra) }
        // A zero delay publishes before execute() returns, exercising the mark-before-send path.
        if (afterMs == 0L) publish() else scope.launch { delay(afterMs); publish() }
        return id
    }

    override suspend fun execute(type: String, watch: String?, args: Map<String, String>): BridgeResult<Map<String, String>> {
        calls += type to args
        overrides[type]?.let { return it(args) }
        return when (type) {
            Type.WATCH_GET_INFO -> BridgeResult.Ok(buildMap {
                put("serial", serialNo); put("name", "Pebble Time 2"); put("model", "emery"); put("fw", fw)
                running?.let { put("running_app", it) }
            })
            Type.WATCH_SYNC_TIME -> ok("verified" to "true", "skew_s" to "0")
            Type.WATCH_SCREENSHOT -> ok("job_id" to job(type, byteArrayOf(screen.toByte()), "image/png", extra = mapOf("width" to "10", "height" to "10")))
            Type.WATCH_PRESS_BUTTON -> press(args)
            Type.WATCH_SWIPE -> {
                if (!touch) return err("INVALID_ARGS", "no touch screen")
                if (now() < busyUntil) return err("WATCH_BUSY")
                busyUntil = now() + 150
                if (args["direction"] == "left" && screen == 0) screen = 2
                ok("accepted" to "true")
            }
            Type.WATCH_GET_PREF -> when (args["pref_key"]) {
                "qlBack" -> ok("pref_key" to "qlBack", "label" to "Quick launch: hold Back", "type" to "json", "value" to qlBack, "support" to "supported")
                "dndManuallyEnabled" -> ok("pref_key" to "dndManuallyEnabled", "label" to "Quiet Time", "type" to "boolean", "value" to dnd, "support" to "supported")
                "backlight" -> ok("pref_key" to "backlight", "label" to "Backlight", "type" to "enum", "value" to "auto", "support" to "supported")
                else -> err("PREF_UNSUPPORTED")
            }
            Type.WATCH_SET_PREF -> {
                if (args["pref_key"] == "dndManuallyEnabled") changeDnd(args["pref_value"].orEmpty())
                ok("watch_status" to "accepted")
            }
            Type.WATCH_LIST_PREFS -> ok("count" to "3", "prefs" to """[
                {"key":"dndManuallyEnabled","label":"Quiet Time","type":"boolean","value":"$dnd","default":"false","support":"supported"},
                {"key":"backlight","label":"Backlight","type":"enum","value":"auto","default":"auto","support":"supported",
                 "options":[{"value":"on","label":"On"},{"value":"auto","label":"Automatic"},{"value":"off","label":"Off"}]},
                {"key":"motionShake","label":"Shake to light","type":"boolean","value":"true","default":"true","support":"unknown"}]""")
            Type.SYSTEM_GET_LOCKER -> ok("entries" to """[{"uuid":"app-1","title":"Weather","type":"watchapp"},{"uuid":"app-2","title":"Timer","type":"watchapp"}]""")
            Type.WATCH_LAUNCH_APP -> { running = args["uuid"]; screen = 3; ok() }
            Type.WATCH_STOP_APP -> { val r = running.orEmpty(); running = null; screen = 0; ok("uuid" to r) }
            Type.WATCH_CHECK_FIRMWARE -> ok("status" to firmwareStatus)
            Type.WATCH_INSTALL_FIRMWARE -> err("FIRMWARE_UPDATE_UNAVAILABLE")
            Type.WATCH_GATHER_LOGS -> {
                if (logsBusy) return err("WATCH_BUSY")
                logsBusy = true
                val id = job(type, "log line 1\nlog line 2\n".toByteArray(), "text/plain", afterMs = 90_000)
                scope.launch { delay(90_001); logsBusy = false }
                ok("job_id" to id)
            }
            Type.WATCH_REBOOT -> {
                if (now() - lastReboot < 60_000) return err("RATE_LIMITED", "Reboot cooldown: 42 s remaining")
                lastReboot = now()
                scope.launch {
                    delay(3_000); publish("watch.disconnected", "connectivity", emptyMap())
                    delay(30_000); publish("watch.connected", "connectivity", emptyMap())
                }
                ok("rebooting" to "true")
            }
            else -> err("UNSUPPORTED_COMMAND")
        }
    }

    private fun press(args: Map<String, String>): BridgeResult<Map<String, String>> {
        if (fw.startsWith("v4.34")) return err("UNSUPPORTED_COMMAND")
        if (now() < busyUntil) return err("WATCH_BUSY", "input sequence running")
        val presses = args["presses"]!!.toInt()
        val hold = args["hold_ms"]!!.toInt()
        val gap = args["gap_ms"]!!.toInt()
        busyUntil = now() + presses * (hold + gap)
        repeat(presses) {
            when (args["button"]) {
                "select" -> if (screen == 0) screen = 1
                "back" -> when {
                    hold >= 1_000 && screen == 0 -> if (holdToggles) scope.launch { delay(800); changeDnd(if (dnd == "true") "false" else "true") }
                    screen == 3 -> { screen = 0; running = null }
                    screen != 0 -> screen = 0
                }
            }
        }
        return ok("accepted" to "true")
    }

    companion object {
        fun decode(bytes: ByteArray): Frame = Frame(10, 10, IntArray(100) { bytes[0].toInt() })
    }
}
