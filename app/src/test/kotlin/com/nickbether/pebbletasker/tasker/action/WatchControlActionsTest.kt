package com.nickbether.pebbletasker.tasker.action

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.BridgeSession
import com.nickbether.pebbletasker.bridge.dto.CommandEnvelope
import com.nickbether.pebbletasker.bridge.dto.Grants
import com.nickbether.pebbletasker.bridge.dto.ResultEnvelope
import com.nickbether.pebbletasker.diagnostics.CheckId
import com.nickbether.pebbletasker.diagnostics.CheckResult
import com.nickbether.pebbletasker.diagnostics.DiagContext
import com.nickbether.pebbletasker.diagnostics.DiagReport
import com.nickbether.pebbletasker.diagnostics.DiagnosticsRunner
import com.nickbether.pebbletasker.diagnostics.Outcome
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.action.diagnostics.RunDiagnosticsInput
import com.nickbether.pebbletasker.tasker.action.diagnostics.RunDiagnosticsRunner
import com.nickbether.pebbletasker.tasker.action.fwcheck.FwCheckInput
import com.nickbether.pebbletasker.tasker.action.fwcheck.FwCheckRunner
import com.nickbether.pebbletasker.tasker.action.fwcheck.InstallFirmwareInput
import com.nickbether.pebbletasker.tasker.action.fwcheck.InstallFirmwareRunner
import com.nickbether.pebbletasker.tasker.action.gatherlogs.GatherLogsRunner
import com.nickbether.pebbletasker.tasker.action.prefs.GetPrefInput
import com.nickbether.pebbletasker.tasker.action.prefs.GetPrefRunner
import com.nickbether.pebbletasker.tasker.action.prefs.ListPrefsInput
import com.nickbether.pebbletasker.tasker.action.prefs.ListPrefsRunner
import com.nickbether.pebbletasker.tasker.action.setpref.SetPrefInput
import com.nickbether.pebbletasker.tasker.action.setpref.SetPrefRunner
import com.nickbether.pebbletasker.tasker.action.watchctl.FactoryResetInput
import com.nickbether.pebbletasker.tasker.action.watchctl.FactoryResetRunner
import com.nickbether.pebbletasker.tasker.action.watchctl.PressButtonInput
import com.nickbether.pebbletasker.tasker.action.watchctl.PressButtonRunner
import com.nickbether.pebbletasker.tasker.action.watchctl.RebootRunner
import com.nickbether.pebbletasker.tasker.action.watchctl.StopAppInput
import com.nickbether.pebbletasker.tasker.action.watchctl.StopAppRunner
import com.nickbether.pebbletasker.tasker.action.watchctl.SwipeInput
import com.nickbether.pebbletasker.tasker.action.watchctl.SwipeRunner
import com.nickbether.pebbletasker.tasker.action.watchctl.SyncTimeInput
import com.nickbether.pebbletasker.tasker.action.watchctl.SyncTimeRunner
import com.nickbether.pebbletasker.tasker.base.CommandTiers
import com.nickbether.pebbletasker.tasker.base.FeatureSupport
import com.nickbether.pebbletasker.tasker.base.Tier
import com.nickbether.pebbletasker.tasker.prefs.PrefInfo
import com.nickbether.pebbletasker.tasker.prefs.WatchPrefs
import com.nickbether.pebbletasker.ui.BridgeWarning
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class WatchControlActionsTest {
    private lateinit var context: Context
    private lateinit var client: BridgeClient
    private var session = BridgeSession("token", "boot", 1, 0, "test",
        setOf("command.watch.checkFirmware", "command.fw.check"), Grants(tier = "sensitive"))
    private val sent = mutableListOf<CommandEnvelope>()
    private var reply: (CommandEnvelope) -> BridgeResult<ResultEnvelope> = { BridgeResult.Ok(ResultEnvelope(ok = true, data = emptyMap())) }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        client = mockk(relaxed = true)
        mockkObject(BridgeClient.Companion, BridgeWarning)
        every { BridgeClient.get(any()) } returns client
        every { client.currentSession } answers { session }
        every { client.awaitReadyBlocking(any()) } answers { BridgeResult.Ok(session) }
        val cmd = slot<CommandEnvelope>()
        every { client.executeBlocking(capture(cmd)) } answers { sent += cmd.captured; reply(cmd.captured) }
    }
    @After fun cleanup() = unmockkAll()

    private val TaskerPluginResultError.code: Int
        get() = com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput::class.java
            .getDeclaredField("code").apply { isAccessible = true }.getInt(this)

    private fun ok(vararg kv: Pair<String, String>) = BridgeResult.Ok(ResultEnvelope(ok = true, data = mapOf(*kv)))
    private fun err(code: String, msg: String = code) = BridgeResult.Err(ErrCodes.toInt(code), msg, code)

    @Test fun `button press sends documented defaults and the watch selector`() {
        val out = (PressButtonRunner().run(context, TaskerInput(PressButtonInput("SER1", "Select"))) as TaskerPluginResultSucess).regular!!
        assertEquals("watch.pressButton", sent.single().type)
        assertEquals("SER1", sent.single().watch)
        assertEquals(mapOf("button" to "select", "presses" to "1", "hold_ms" to "50", "gap_ms" to "100"), sent.single().args)
        assertEquals("true", out.pbOk)
        PressButtonRunner().run(context, TaskerInput(PressButtonInput(null, "back", "4", "1000", "250")))
        assertEquals(mapOf("button" to "back", "presses" to "4", "hold_ms" to "1000", "gap_ms" to "250"), sent.last().args)
    }

    @Test fun `out of range input is refused locally with INVALID_ARGS`() {
        for (input in listOf(PressButtonInput(null, "back", "0"), PressButtonInput(null, "back", "256"), PressButtonInput(null, "left"),
            PressButtonInput(null, "back", holdMs = "70000"), PressButtonInput(null, null))) {
            val out = (PressButtonRunner().run(context, TaskerInput(input)) as TaskerPluginResultSucess).regular!!
            assertEquals("false", out.pbOk)
            assertEquals(ErrCodes.INVALID_ARGS.toString(), out.pbErr)
            assertEquals("INVALID_ARGS", out.pbErrCode)
        }
        val swipe = (SwipeRunner().run(context, TaskerInput(SwipeInput(null, "up", "301"))) as TaskerPluginResultSucess).regular!!
        assertEquals("false", swipe.pbOk)
        assertTrue(sent.isEmpty())
        SwipeRunner().run(context, TaskerInput(SwipeInput(null, "LEFT")))
        assertEquals(mapOf("direction" to "left", "duration_ms" to "150"), sent.single().args)
    }

    @Test fun `busy watch is a soft error that keeps the wire code visible`() {
        reply = { err("WATCH_BUSY", "input sequence running") }
        val out = (PressButtonRunner().run(context, TaskerInput(PressButtonInput(null, "back"))) as TaskerPluginResultSucess).regular!!
        assertEquals("false", out.pbOk)
        assertEquals(ErrCodes.WATCH_BUSY.toString(), out.pbErr)
        assertEquals("WATCH_BUSY", out.pbErrCode)
        assertTrue(out.pbErrmsg!!.startsWith("WATCH_BUSY:"))
        assertTrue(out.pbErrmsg!!.contains("Retry later"))
        assertTrue(out.pbJson!!.contains("\"err_code\":\"WATCH_BUSY\""))
    }

    @Test fun `command tier refusal uses the authorization code and explains the missing grant`() {
        reply = { err("COMMAND_NOT_AUTHORIZED", "Command tier exceeds grant") }
        val result = RebootRunner().run(context, TaskerInput(com.nickbether.pebbletasker.tasker.action.watchctl.RebootInput()))
        assertTrue(result is TaskerPluginResultError)
        val e = result as TaskerPluginResultError
        assertEquals(ErrCodes.NOT_AUTHORIZED, e.code)
        assertTrue(e.message.startsWith("COMMAND_NOT_AUTHORIZED:"))
        assertTrue(e.message.contains("dangerous tier; this plugin has sensitive"))
    }

    @Test fun `firmware errors carry recovery hints`() {
        reply = { err("FIRMWARE_CHECK_STALE", "No recent check") }
        val out = (InstallFirmwareRunner().run(context, TaskerInput(InstallFirmwareInput(null, "v4.36.0"))) as TaskerPluginResultSucess).regular!!
        assertEquals(mapOf("version" to "v4.36.0"), sent.single().args)
        assertEquals(ErrCodes.FIRMWARE_CHECK_STALE.toString(), out.pbErr)
        assertTrue(out.pbErrmsg!!.contains("Run Check Firmware first"))
        reply = { err("FIRMWARE_UPDATE_UNAVAILABLE") }
        val none = (InstallFirmwareRunner().run(context, TaskerInput(InstallFirmwareInput())) as TaskerPluginResultSucess).regular!!
        assertEquals(ErrCodes.FIRMWARE_UPDATE_UNAVAILABLE.toString(), none.pbErr)
        assertTrue(sent.last().args.isEmpty())
    }

    @Test fun `check firmware uses the new command with force and falls back on older hosts`() {
        reply = { ok("status" to "available", "version" to "v4.36.0") }
        val out = (FwCheckRunner().run(context, TaskerInput(FwCheckInput())) as TaskerPluginResultSucess).regular!!
        assertEquals("watch.checkFirmware", sent.last().type)
        assertEquals(mapOf("force" to "true"), sent.last().args)
        assertEquals("available", out.fwStatus); assertEquals("v4.36.0", out.fwVersion); assertEquals("true", out.fwAvailable)
        FwCheckRunner().run(context, TaskerInput(FwCheckInput(force = "false")))
        assertTrue(sent.last().args.isEmpty())
        session = session.copy(capabilities = setOf("command.fw.check"))
        reply = { ok("fw_available" to "false") }
        FwCheckRunner().run(context, TaskerInput(FwCheckInput()))
        assertEquals("fw.check", sent.last().type)
    }

    @Test fun `factory reset requires the confirmation serial`() {
        val out = (FactoryResetRunner().run(context, TaskerInput(FactoryResetInput("SER1", " "))) as TaskerPluginResultSucess).regular!!
        assertEquals("false", out.pbOk)
        assertTrue(sent.isEmpty())
        reply = { ok("factory_reset" to "started") }
        val ok = (FactoryResetRunner().run(context, TaskerInput(FactoryResetInput("SER1", "SER1"))) as TaskerPluginResultSucess).regular!!
        assertEquals(mapOf("confirm_serial" to "SER1"), sent.single().args)
        assertEquals("started", ok.factoryReset)
    }

    @Test fun `preference actions build args and expose flat and raw results`() {
        reply = { ok("pref_key" to "backlight", "label" to "Backlight", "type" to "enum", "value" to "auto", "support" to "supported",
            "options" to """[{"value":"on","label":"On"},{"value":"auto","label":"Automatic"}]""") }
        val get = (GetPrefRunner().run(context, TaskerInput(GetPrefInput("SER1", "backlight"))) as TaskerPluginResultSucess).regular!!
        assertEquals(mapOf("pref_key" to "backlight"), sent.last().args)
        assertEquals("auto", get.value); assertEquals("supported", get.support)
        assertTrue(get.options!!.contains("Automatic"))

        reply = { err("PREF_UNSUPPORTED", "Not supported") }
        val unsupported = (GetPrefRunner().run(context, TaskerInput(GetPrefInput(null, "motionShake"))) as TaskerPluginResultSucess).regular!!
        assertEquals(ErrCodes.PREF_UNSUPPORTED.toString(), unsupported.pbErr)

        reply = { ok("count" to "2", "prefs" to """[{"key":"a","label":"A","support":"supported"},{"key":"b","label":"B"}]""") }
        val list = (ListPrefsRunner().run(context, TaskerInput(ListPrefsInput("SER1"))) as TaskerPluginResultSucess).regular!!
        assertArrayEquals(arrayOf("a", "b"), list.keys)
        assertEquals("2", list.count)
        assertEquals(listOf("a", "b"), WatchPrefs.cached(context, "SER1").map { it.key })
        assertEquals(listOf("a", "b"), WatchPrefs.cached(context, "OTHER").map { it.key })

        reply = { ok("watch_status" to "accepted") }
        val set = (SetPrefRunner().run(context, TaskerInput(SetPrefInput(null, "backlight", "auto"))) as TaskerPluginResultSucess).regular!!
        assertEquals(mapOf("pref_key" to "backlight", "pref_value" to "auto"), sent.last().args)
        assertNull(sent.last().watch)
        assertEquals("accepted", set.watchStatus)
    }

    @Test fun `stop app and sync time map results`() {
        reply = { ok("uuid" to "app-1") }
        val stop = (StopAppRunner().run(context, TaskerInput(StopAppInput())) as TaskerPluginResultSucess).regular!!
        assertTrue(sent.last().args.isEmpty())
        assertEquals("app-1", stop.uuid)
        StopAppRunner().run(context, TaskerInput(StopAppInput(uuid = "app-2")))
        assertEquals(mapOf("uuid" to "app-2"), sent.last().args)
        reply = { ok("verified" to "true", "skew_s" to "1") }
        val sync = (SyncTimeRunner().run(context, TaskerInput(SyncTimeInput())) as TaskerPluginResultSucess).regular!!
        assertEquals("true", sync.verified); assertEquals("1", sync.skewS)
        reply = { err("RATE_LIMITED", "Time sync cooldown: 1700 s remaining") }
        val limited = (SyncTimeRunner().run(context, TaskerInput(SyncTimeInput())) as TaskerPluginResultSucess).regular!!
        assertEquals(ErrCodes.RATE_LIMITED.toString(), limited.pbErr)
        assertTrue(limited.pbErrmsg!!.contains("1700 s"))
    }

    @Test fun `new error codes are stable and appended`() {
        assertEquals(24, ErrCodes.toInt("PREF_UNSUPPORTED"))
        assertEquals(25, ErrCodes.toInt("FIRMWARE_UPDATE_UNAVAILABLE"))
        assertEquals(26, ErrCodes.toInt("FIRMWARE_CHECK_STALE"))
        assertEquals(27, ErrCodes.toInt("WATCH_BUSY"))
        assertEquals(ErrCodes.NOT_AUTHORIZED, ErrCodes.toInt("COMMAND_NOT_AUTHORIZED"))
        assertEquals(ErrCodes.CATEGORY_DISABLED, ErrCodes.toInt("CATEGORY_DISABLED"))
    }

    @Test fun `tiers rank unknown grants as dangerous`() {
        assertEquals(Tier.EXTREMELY_DANGEROUS, Tier.parse("extremely_dangerous"))
        assertEquals(Tier.DANGEROUS, Tier.parse("super_dangerous_v2"))
        assertNull(Tier.parse(""))
        assertEquals(Tier.EXTREMELY_DANGEROUS, CommandTiers.of("watch.gatherLogs"))
        assertEquals(Tier.EXTREMELY_DANGEROUS, CommandTiers.of("watch.factoryReset"))
        assertEquals(Tier.SENSITIVE, CommandTiers.of("watch.screenshot"))
        assertEquals(Tier.NORMAL, CommandTiers.of("watch.checkFirmware"))
        assertTrue(CommandTiers.insufficient(Tier.DANGEROUS, session))
        assertFalse(CommandTiers.insufficient(Tier.DANGEROUS, session.copy(grants = Grants(tier = "future_tier"))))
        assertTrue(CommandTiers.describe(Tier.EXTREMELY_DANGEROUS, session).contains("10 seconds"))
    }

    @Test fun `actions are unavailable when the host does not advertise their command`() {
        val caps = setOf("command.watch.pressButton")
        assertNull(FeatureSupport.reason(PressButtonRunner::class.java, caps))
        assertNotNull(FeatureSupport.reason(SwipeRunner::class.java, caps))
        assertNotNull(FeatureSupport.reason(GatherLogsRunner::class.java, caps))
        assertNull(FeatureSupport.reason(FwCheckRunner::class.java, setOf("command.fw.check")))
        assertEquals("watch.checkFirmware", FeatureSupport.commandOf(FwCheckRunner::class.java))
        assertNotNull(FeatureSupport.reason(com.nickbether.pebbletasker.tasker.event.system.JobDoneRunner::class.java, setOf("events.core")))
    }

    @Test fun `run diagnostics ends with a Tasker error naming failed checks`() {
        mockkObject(DiagnosticsRunner)
        val checks = CheckId.entries.map { CheckResult(it, Outcome.PASS, "ok") }.toMutableList()
        checks[CheckId.SWIPE.ordinal] = CheckResult(CheckId.SWIPE, Outcome.DEFERRED, "no touch")
        val passing = DiagReport(0, 1, DiagContext("t"), checks)
        coEvery { DiagnosticsRunner.runHeadless(any(), any()) } returns passing
        val ok = RunDiagnosticsRunner().run(context, TaskerInput(RunDiagnosticsInput()))
        assertTrue(ok is TaskerPluginResultSucess)
        val out = (ok as TaskerPluginResultSucess).regular!!
        assertEquals("10", out.pass); assertEquals("1", out.deferred)

        checks[CheckId.BUTTON_PRESS.ordinal] = CheckResult(CheckId.BUTTON_PRESS, Outcome.FAIL, "launcher did not open (changed 0.010)")
        coEvery { DiagnosticsRunner.runHeadless(any(), any()) } returns passing.copy(checks = checks.toList())
        val failed = RunDiagnosticsRunner().run(context, TaskerInput(RunDiagnosticsInput(includeReboot = "true"))) as TaskerPluginResultError
        assertEquals(ErrCodes.DIAGNOSTICS_FAILED, failed.code)
        assertTrue(failed.message.contains("3. Button press: launcher did not open"))
        assertFalse(failed.message.contains("6. Touchscreen swipe:"))
    }

    @Test fun `preference model picks editors and validates values`() {
        val prefs = WatchPrefs.parseList("""[
            {"key":"backlight","label":"Backlight","type":"enum","value":"auto","support":"supported","options":[{"value":"on","label":"On"},{"value":"auto","label":"Automatic"}]},
            {"key":"timeout","label":"Timeout","type":"int","value":3,"min":1,"max":10,"unit":"s","support":"unknown"},
            {"key":"qlBack","label":"Hold Back","type":"json","value":{"enabled":true,"uuid":"x"},"support":"unsupported"},
            {"key":"dnd","label":"Quiet Time","type":"boolean","value":false},
            {"label":"no key"}]""")
        assertEquals(4, prefs.size)
        assertEquals(PrefInfo.Editor.CHOICE, prefs[0].editor)
        assertEquals(listOf("on", "auto"), prefs[0].valueChoices.map { it.second })
        assertEquals(PrefInfo.Editor.NUMBER, prefs[1].editor)
        assertEquals("3", prefs[1].value)
        assertNotNull(prefs[1].problemWith("11")); assertNull(prefs[1].problemWith("5")); assertNull(prefs[1].problemWith("%v"))
        assertEquals("""{"enabled":true,"uuid":"x"}""", prefs[2].value)
        assertEquals(PrefInfo.Editor.BOOLEAN, prefs[3].editor)
        assertEquals(listOf("supported", "unknown", "unsupported"), WatchPrefs.grouped(prefs).map { it.first })
        assertTrue(WatchPrefs.parseList("not json").isEmpty())
        val flat = WatchPrefs.fromFlat(mapOf("pref_key" to "backlight", "options" to """["on","off"]""", "support" to "supported"))!!
        assertEquals(listOf("on", "off"), flat.options.map { it.value })
    }
}
