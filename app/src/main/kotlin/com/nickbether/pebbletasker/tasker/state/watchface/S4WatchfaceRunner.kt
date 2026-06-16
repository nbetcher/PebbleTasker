package com.nickbether.pebbletasker.tasker.state.watchface

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.tasker.base.PebbleStateRunner
import com.nickbether.pebbletasker.tasker.state.StateJson
import com.nickbether.pebbletasker.tasker.state.StateSupport

/**
 * S4 runner. Reads the EXTENDED StateData.activeWatchface (uuid + optional name).
 *
 *  - bridge Err                              -> Unknown.
 *  - active-watchface field absent (today)   -> Unknown (capability-gated; never flaps false).
 *  - no [uuid] filter and a face is active   -> Satisfied (any watchface).
 *  - filter set and matches active uuid      -> Satisfied.
 *  - otherwise                               -> Unsatisfied.
 */
class S4WatchfaceRunner : PebbleStateRunner<S4WatchfaceInput, S4WatchfaceOutput>() {

    override fun evaluate(
        context: Context,
        input: S4WatchfaceInput,
    ): TaskerPluginResultCondition<S4WatchfaceOutput> {
        val state = when (val r = StateSupport.queryState(context)) {
            is BridgeResult.Ok -> r.value
            is BridgeResult.Err -> return TaskerPluginResultConditionUnknown()
        }
        val activeUuid = StateSupport.extendedFieldOrNull(state, "activeWatchface")
            ?: return TaskerPluginResultConditionUnknown()
        if (activeUuid.isBlank()) return TaskerPluginResultConditionUnsatisfied()

        val activeName = StateSupport.extendedFieldOrNull(state, "activeWatchfaceName")
        val want = input.uuid?.trim()
        val matches = want.isNullOrBlank() || want.equals(activeUuid, ignoreCase = true)
        return if (matches) {
            TaskerPluginResultConditionSatisfied(
                context,
                S4WatchfaceOutput(
                    pbJson = StateJson.obj(
                        "watchface_uuid" to activeUuid,
                        "watchface_name" to activeName,
                    ),
                    watchfaceUuid = activeUuid,
                    watchfaceName = activeName,
                ),
            )
        } else {
            TaskerPluginResultConditionUnsatisfied()
        }
    }
}
