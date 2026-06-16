package com.nickbether.pebbletasker.tasker.state.dnd

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
 * S2 runner. Reads the EXTENDED StateData.dnd field via [StateSupport.extendedFieldOrNull].
 *
 *  - bridge Err                                  -> Unknown.
 *  - extended field absent (today's bridge)      -> Unknown (capability-gated; never flaps false).
 *  - dnd == true                                 -> Satisfied.
 *  - dnd == false                                -> Unsatisfied.
 */
class S2DndRunner : PebbleStateRunner<S2DndInput, S2DndOutput>() {

    override fun evaluate(
        context: Context,
        input: S2DndInput,
    ): TaskerPluginResultCondition<S2DndOutput> {
        val state = when (val r = StateSupport.queryState(context)) {
            is BridgeResult.Ok -> r.value
            is BridgeResult.Err -> return TaskerPluginResultConditionUnknown()
        }
        // Until the bridge emits StateData.dnd this is null -> Unknown (don't flap the context off).
        val raw = StateSupport.extendedFieldOrNull(state, "dnd")
            ?: return TaskerPluginResultConditionUnknown()
        val on = raw == "true" || raw == "1"
        val serial = StateSupport.matchWatch(state, input.serial)?.serial ?: input.serial
        return if (on) {
            TaskerPluginResultConditionSatisfied(
                context,
                S2DndOutput(
                    pbJson = StateJson.obj("dnd" to "true", "serial" to serial),
                    dnd = "true",
                    serial = serial,
                ),
            )
        } else {
            TaskerPluginResultConditionUnsatisfied()
        }
    }
}
