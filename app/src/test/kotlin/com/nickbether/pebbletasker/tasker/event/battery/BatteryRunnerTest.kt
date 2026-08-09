package com.nickbether.pebbletasker.tasker.event.battery

import androidx.test.core.app.ApplicationProvider
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.cache.CachedEvent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Threshold semantics for E4, including the optional ("Any") direction. */
@RunWith(RobolectricTestRunner::class)
class BatteryRunnerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun batteryEvent(level: Int) = CachedEvent(
        type = "watch.battery",
        bootId = "boot-1",
        seq = 1,
        ts = 1,
        category = "connectivity",
        watch = null,
        data = mapOf("level" to level.toString()),
    )

    private fun evaluate(level: Int, direction: String?, threshold: String?) =
        BatteryRunner().evaluate(
            context,
            BatteryFilter(serial = "", threshold = threshold, direction = direction),
            batteryEvent(level),
            null,
        )

    @Test
    fun `any direction fires regardless of level`() {
        assertTrue(evaluate(97, "any", "20") is TaskerPluginResultConditionSatisfied)
        assertTrue(evaluate(3, "any", "20") is TaskerPluginResultConditionSatisfied)
    }

    /** Previously a blank threshold produced an event that could never fire, with no warning. */
    @Test
    fun `blank threshold fires instead of silently never matching`() {
        assertTrue(evaluate(50, "below", "") is TaskerPluginResultConditionSatisfied)
        assertTrue(evaluate(50, "", null) is TaskerPluginResultConditionSatisfied)
        assertTrue(evaluate(50, "below", "not-a-number") is TaskerPluginResultConditionSatisfied)
    }

    @Test
    fun `below fires at or under the threshold only`() {
        assertTrue(evaluate(20, "below", "20") is TaskerPluginResultConditionSatisfied)
        assertTrue(evaluate(19, "below", "20") is TaskerPluginResultConditionSatisfied)
        assertTrue(evaluate(21, "below", "20") is TaskerPluginResultConditionUnsatisfied)
    }

    @Test
    fun `above fires at or over the threshold only`() {
        assertTrue(evaluate(80, "above", "80") is TaskerPluginResultConditionSatisfied)
        assertTrue(evaluate(81, "above", "80") is TaskerPluginResultConditionSatisfied)
        assertTrue(evaluate(79, "above", "80") is TaskerPluginResultConditionUnsatisfied)
    }

    @Test
    fun `a non-matching serial still blocks the event`() {
        val result = BatteryRunner().evaluate(
            context,
            BatteryFilter(serial = "OTHER-WATCH", threshold = null, direction = "any"),
            batteryEvent(50),
            null,
        )
        assertTrue(result is TaskerPluginResultConditionUnsatisfied)
    }
}
