package com.nickbether.pebbletasker.diagnostics

import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender.Type
import com.nickbether.pebbletasker.bridge.dto.Grants
import com.nickbether.pebbletasker.tasker.ErrCodes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsEngineTest {
    private fun TestScope.watch() = FakeWatch(backgroundScope) { testScheduler.currentTime }

    private fun TestScope.engine(w: FakeWatch, reboot: Boolean = false, onUpdate: (List<CheckResult>) -> Unit = {}) =
        DiagnosticsEngine(w, FakeWatch::decode, DiagnosticsEngine.Options(includeReboot = reboot, pluginVersion = "test"),
            clock = { testScheduler.currentTime }, onUpdate = onUpdate)

    private fun DiagReport.outcome(id: CheckId) = checks.first { it.id == id }.outcome
    private fun DiagReport.detail(id: CheckId) = checks.first { it.id == id }.detail
    private fun err(code: String, msg: String = code) = BridgeResult.Err(ErrCodes.toInt(code), msg, code)

    @Test fun `healthy watch passes every check and never installs firmware when an update is offered`() = runTest {
        val w = watch().apply { firmwareStatus = "available" }
        val report = engine(w, reboot = true).run()
        for (id in CheckId.entries) assertEquals("$id: ${report.detail(id)}", Outcome.PASS, report.outcome(id))
        assertEquals(0, w.count(Type.WATCH_INSTALL_FIRMWARE))
        assertEquals(0, w.count("watch.factoryReset"))
        assertTrue(report.detail(CheckId.REBOOT).contains("RATE_LIMITED"))
        assertFalse(report.isFailure)
        assertEquals("false", w.dnd)
    }

    @Test fun `each check runs strictly after the previous one finished`() = runTest {
        val seen = mutableListOf<List<CheckResult>>()
        engine(watch()) { seen += it }.run()
        for (snapshot in seen) assertTrue(snapshot.count { it.outcome == Outcome.RUNNING } <= 1)
    }

    @Test fun `no update means install must be refused as unavailable, not stale`() = runTest {
        val w = watch()
        assertEquals(Outcome.PASS, engine(w).run().outcome(CheckId.FIRMWARE))
        w.overrides[Type.WATCH_INSTALL_FIRMWARE] = { err("FIRMWARE_CHECK_STALE") }
        val stale = engine(w).run()
        assertEquals(Outcome.FAIL, stale.outcome(CheckId.FIRMWARE))
        assertTrue(stale.detail(CheckId.FIRMWARE).contains("FIRMWARE_CHECK_STALE"))
    }

    @Test fun `classification per error code`() = runTest {
        val w = watch()
        w.overrides[Type.WATCH_SYNC_TIME] = { err("RATE_LIMITED", "Time sync cooldown: 1742 s remaining") }
        w.overrides[Type.WATCH_GATHER_LOGS] = { err("COMMAND_NOT_AUTHORIZED", "tier") }
        w.firmwareStatus = "pending"
        val r = engine(w).run()
        assertEquals(Outcome.DEFERRED, r.outcome(CheckId.TIME_SYNC))
        assertTrue(r.detail(CheckId.TIME_SYNC).contains("retry in 1742 s"))
        assertEquals(Outcome.DEFERRED, r.outcome(CheckId.LOG_DUMP))
        assertEquals(Outcome.DEFERRED, r.outcome(CheckId.FIRMWARE))
        assertEquals(Outcome.SKIPPED, r.outcome(CheckId.REBOOT))
        assertFalse("deferred and skipped are not failures", r.isFailure)

        for (code in listOf("INTERNAL", "TIMEOUT")) {
            val w2 = watch()
            w2.overrides[Type.WATCH_SYNC_TIME] = { err(code) }
            assertEquals(code, Outcome.FAIL, engine(w2).run().outcome(CheckId.TIME_SYNC))
        }
    }

    @Test fun `old firmware defers remote input checks without sending input`() = runTest {
        val w = watch().apply { fw = "v4.34.2" }
        val r = engine(w).run()
        for (id in listOf(CheckId.BUTTON_PRESS, CheckId.BUSY_REFUSAL, CheckId.LONG_PRESS, CheckId.SWIPE))
            assertEquals(id.name, Outcome.DEFERRED, r.outcome(id))
        assertEquals(0, w.count(Type.WATCH_PRESS_BUTTON))
        assertEquals(0, w.count(Type.WATCH_SWIPE))
    }

    @Test fun `unsupported remote input and missing touch screen defer`() = runTest {
        val w = watch().apply { fw = "unknown"; touch = false }
        w.overrides[Type.WATCH_PRESS_BUTTON] = { err("UNSUPPORTED_COMMAND") }
        val r = engine(w).run()
        assertEquals(Outcome.DEFERRED, r.outcome(CheckId.BUTTON_PRESS))
        val w2 = watch().apply { touch = false }
        val r2 = engine(w2).run()
        assertEquals(Outcome.DEFERRED, r2.outcome(CheckId.SWIPE))
        assertTrue(r2.detail(CheckId.SWIPE).contains("touch"))
    }

    @Test fun `busy refusal fails when the second sequence is accepted`() = runTest {
        val w = watch()
        var presses = 0
        w.overrides[Type.WATCH_PRESS_BUTTON] = { presses++; BridgeResult.Ok(mapOf("accepted" to "true")) }
        val r = engine(w).run()
        assertEquals(Outcome.FAIL, r.outcome(CheckId.BUSY_REFUSAL))
        assertTrue(presses > 0)
    }

    @Test fun `a screen that never changes fails the button check with the measured fraction`() = runTest {
        val w = watch()
        w.overrides[Type.WATCH_PRESS_BUTTON] = { BridgeResult.Ok(mapOf("accepted" to "true")) }
        val r = engine(w).run()
        assertEquals(Outcome.FAIL, r.outcome(CheckId.BUTTON_PRESS))
        assertTrue(r.detail(CheckId.BUTTON_PRESS).contains("0.000"))
    }

    @Test fun `Quiet Time is restored when the flip is never reported`() = runTest {
        val w = watch().apply { dndEchoes = false }
        val r = engine(w).run()
        assertEquals(Outcome.FAIL, r.outcome(CheckId.LONG_PRESS))
        assertEquals("false", w.dnd)
        assertTrue(w.calls.any { it.first == Type.WATCH_SET_PREF && it.second["pref_key"] == "dndManuallyEnabled" && it.second["pref_value"] == "false" })
    }

    @Test fun `long press defers unless holding Back toggles Quiet Time`() = runTest {
        val w = watch().apply { qlBack = """{"enabled":true,"uuid":"00000000-0000-0000-0000-000000000000"}""" }
        val r = engine(w).run()
        assertEquals(Outcome.DEFERRED, r.outcome(CheckId.LONG_PRESS))
        assertEquals(0, w.calls.count { it.first == Type.WATCH_PRESS_BUTTON && it.second["hold_ms"] == "1500" })
    }

    @Test fun `stop during the long press restores Quiet Time and returns to the watchface`() = runTest {
        val w = watch()
        val e = engine(w)
        var report: DiagReport? = null
        val job = launch { report = runCatching { e.run() }.getOrNull() }
        // Advance until the watch has flipped Quiet Time, then stop.
        while (w.dnd == "false") { advanceTimeBy(250); runCurrent() }
        val pressesBefore = w.count(Type.WATCH_PRESS_BUTTON)
        job.cancel()
        job.join()
        assertEquals(null, report)
        assertEquals("false", w.dnd)
        assertTrue("cleanup pressed Back", w.count(Type.WATCH_PRESS_BUTTON) > pressesBefore)
        val stopped = e.report(stopped = true)
        assertEquals(Outcome.STOPPED, stopped.outcome(CheckId.SWIPE))
        assertTrue(stopped.stopped)
        assertEquals(0, w.count(Type.WATCH_GATHER_LOGS))
    }

    @Test fun `preflight stops with update advice or missing grant`() = runTest {
        val old = watch().apply { capabilities = capabilities - "command.watch.swipe" }
        val r = engine(old).run()
        assertNotNull(r.aborted)
        assertTrue(r.aborted!!.startsWith("Update the Pebble app"))
        assertTrue(r.aborted!!.contains("watch.swipe"))
        assertTrue(r.isFailure)
        assertEquals(0, old.count(Type.WATCH_PRESS_BUTTON))

        val low = watch().apply { grants = Grants(categories = listOf("system"), tier = "sensitive") }
        assertTrue(engine(low).run().aborted!!.contains("dangerous"))

        val noSystem = watch().apply { grants = Grants(categories = listOf("connectivity"), tier = "extremely_dangerous") }
        assertTrue(engine(noSystem).run().aborted!!.contains("system event category"))
    }

    @Test fun `log dump requires busy refusal of a second dump and readable text`() = runTest {
        val w = watch()
        val r = engine(w).run()
        assertEquals(r.detail(CheckId.LOG_DUMP), Outcome.PASS, r.outcome(CheckId.LOG_DUMP))
        assertEquals(2, w.count(Type.WATCH_GATHER_LOGS))
    }

    @Test fun `reboot cooldown sub-check is skipped when reconnect is slow`() = runTest {
        val w = watch()
        w.overrides[Type.WATCH_REBOOT] = {
            backgroundScope.launch {
                kotlinx.coroutines.delay(1_000); w.publish("watch.disconnected", "connectivity", emptyMap())
                kotlinx.coroutines.delay(90_000); w.publish("watch.connected", "connectivity", emptyMap())
            }
            BridgeResult.Ok(mapOf("rebooting" to "true"))
        }
        val r = engine(w, reboot = true).run()
        assertEquals(Outcome.PASS, r.outcome(CheckId.REBOOT))
        assertTrue(r.detail(CheckId.REBOOT).contains("skipped"))
        assertEquals(1, w.count(Type.WATCH_REBOOT))
    }

    @Test fun `reboot without a disconnect fails`() = runTest {
        val w = watch()
        w.overrides[Type.WATCH_REBOOT] = { BridgeResult.Ok(mapOf("rebooting" to "true")) }
        assertEquals(Outcome.FAIL, engine(w, reboot = true).run().outcome(CheckId.REBOOT))
    }
}
