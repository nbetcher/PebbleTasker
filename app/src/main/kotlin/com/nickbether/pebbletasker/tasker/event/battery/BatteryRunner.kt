package com.nickbether.pebbletasker.tasker.event.battery

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.event.EventSupport

/**
 * E4 runner (FINAL DESIGN §2.1 template, FIX C3/C4/C9).
 *
 * Source of truth = EventCache (the [cached] arg). The battery level lives at data["level"] as a
 * String (verified ConnectivityCollector:51). We return:
 *   - Unknown   when the bridge never reported a battery event (so contexts don't flap to false),
 *   - Unsatisfied when serial doesn't match the filter or the threshold isn't crossed,
 *   - Satisfied(context, output) when the level crosses the threshold in the chosen direction.
 */
class BatteryRunner : PebbleEventRunner<BatteryFilter, BatteryOutput>() {

    override val eventType: String = EventRouting.TYPE_BATTERY

    override fun evaluate(
        context: Context,
        filter: BatteryFilter,
        cached: CachedEvent?,
        update: BatteryOutput?,
    ): TaskerPluginResultCondition<BatteryOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        if (!EventSupport.matchesSerial(filter.serial, e.watch)) {
            return TaskerPluginResultConditionUnsatisfied()
        }
        val level = e.int("level") ?: return TaskerPluginResultConditionUnknown()
        val threshold = filter.threshold?.trim()?.toIntOrNull()
            ?: return TaskerPluginResultConditionUnsatisfied()
        val above = filter.direction?.trim()?.equals("above", ignoreCase = true) == true
        val crossed = if (above) level >= threshold else level <= threshold
        if (!crossed) return TaskerPluginResultConditionUnsatisfied()

        return TaskerPluginResultConditionSatisfied(context, buildOutput(e, level, above))
    }

    private fun buildOutput(e: CachedEvent, level: Int, above: Boolean): BatteryOutput {
        val direction = if (above) "above" else "below"
        return BatteryOutput(pbDirection = direction).fillBase<BatteryOutput>(
            e,
            mapOf("battery" to level.toString(), "direction" to direction),
        ).also {
            // The battery EVENT level lives in data["level"], not WatchRef.battery — override it.
            it.pbBattery = level.toString()
        }
    }
}
