package com.nickbether.pebbletasker.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagModelTest {
    private fun frame(w: Int, h: Int, fill: (Int) -> Int) = Frame(w, h, IntArray(w * h, fill))

    @Test fun `pixel diff counts differing pixels and treats size changes as full change`() {
        val a = frame(10, 10) { 0 }
        assertEquals(0.0, PixelDiff.fraction(a, frame(10, 10) { 0 }), 0.0)
        assertEquals(0.5, PixelDiff.fraction(a, frame(10, 10) { if (it < 50) 1 else 0 }), 1e-9)
        assertEquals(1.0, PixelDiff.fraction(a, frame(10, 11) { 0 }), 0.0)
        assertEquals(1.0, PixelDiff.fraction(frame(0, 0) { 0 }, frame(0, 0) { 0 }), 0.0)
    }

    @Test fun `changed threshold is strictly above 0_2`() {
        val a = frame(10, 10) { 0 }
        assertEquals(0.2, PixelDiff.CHANGED_THRESHOLD, 0.0)
        assertFalse(PixelDiff.changed(a, frame(10, 10) { if (it < 20) 1 else 0 }))
        assertTrue(PixelDiff.changed(a, frame(10, 10) { if (it < 21) 1 else 0 }))
        // A clock digit ticking over is a few percent of the screen.
        assertFalse(PixelDiff.changed(a, frame(10, 10) { if (it < 4) 1 else 0 }))
        assertEquals("0.210", PixelDiff.format(0.21))
    }

    @Test fun `firmware versions gate remote input at 4_35_0`() {
        assertEquals(listOf(4, 35, 0), FirmwareVersion.parse("v4.35.0-rc2"))
        assertEquals(listOf(4, 4, 0), FirmwareVersion.parse("4.4"))
        assertNull(FirmwareVersion.parse("unknown"))
        assertEquals(true, FirmwareVersion.atLeast("v4.35.0", FirmwareVersion.REMOTE_INPUT_MIN))
        assertEquals(true, FirmwareVersion.atLeast("v4.36.1", FirmwareVersion.REMOTE_INPUT_MIN))
        assertEquals(true, FirmwareVersion.atLeast("v5.0.0", FirmwareVersion.REMOTE_INPUT_MIN))
        assertEquals(false, FirmwareVersion.atLeast("v4.34.9", FirmwareVersion.REMOTE_INPUT_MIN))
        assertNull(FirmwareVersion.atLeast(null, FirmwareVersion.REMOTE_INPUT_MIN))
    }

    private fun report(vararg outcomes: Pair<CheckId, Outcome>, aborted: String? = null) = DiagReport(
        startedAt = 0, finishedAt = 65_000,
        context = DiagContext("0.9.1 (10)", "host-1.2", "Pebble Time 2", "SER1", "emery", "v4.35.0", "dangerous", listOf("system")),
        checks = CheckId.entries.map { id -> CheckResult(id, outcomes.toMap()[id] ?: Outcome.PASS, if (outcomes.toMap()[id] == Outcome.FAIL) "broke" else "ok") },
        aborted = aborted,
    )

    @Test fun `report text carries versions, watch and every check`() {
        val text = report(CheckId.SWIPE to Outcome.DEFERRED, CheckId.LOG_DUMP to Outcome.FAIL).toText()
        assertTrue(text.contains("Plugin: 0.9.1 (10)"))
        assertTrue(text.contains("Pebble app: host-1.2"))
        assertTrue(text.contains("Model: emery; firmware: v4.35.0"))
        assertTrue(text.contains("Result: 9 passed, 1 failed, 1 deferred"))
        assertTrue(text.lines().any { it.contains("10. Log dump") && it.contains("FAIL") && it.endsWith("broke") })
        assertEquals(11, text.lines().count { Regex("""^\s?\d+\. """).containsMatchIn(it) })
    }

    @Test fun `error message names failed checks only and deferred is not a failure`() {
        val deferredOnly = report(CheckId.SWIPE to Outcome.DEFERRED)
        assertFalse(deferredOnly.isFailure)
        val failed = report(CheckId.BUTTON_PRESS to Outcome.FAIL, CheckId.LOG_DUMP to Outcome.FAIL, CheckId.SWIPE to Outcome.DEFERRED)
        assertTrue(failed.isFailure)
        val msg = failed.errorMessage()
        assertTrue(msg.contains("3. Button press: broke"))
        assertTrue(msg.contains("10. Log dump: broke"))
        assertFalse(msg.contains("Touchscreen swipe:"))
        val aborted = report(aborted = "Update the Pebble app")
        assertTrue(aborted.isFailure)
        assertEquals("Watch diagnostics did not run: Update the Pebble app", aborted.errorMessage())
    }
}
