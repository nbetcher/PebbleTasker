package com.nickbether.pebbletasker.tasker.state.connected

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import com.nickbether.pebbletasker.tasker.base.PebbleStateRunner
import com.nickbether.pebbletasker.tasker.state.StateJson
import com.nickbether.pebbletasker.tasker.state.StateSupport

/**
 * S1 runner. READY on today's bridge: queries getState and checks for the (optionally serial-matched)
 * watch in the connected-devices snapshot.
 *
 *  - bridge Err (unbound / not authorized / timeout)  -> Unknown (context won't flap — FIX C8).
 *  - watch present                                     -> Satisfied + identity outputs.
 *  - watch absent                                      -> Unsatisfied.
 */
class S1WatchConnectedRunner : PebbleStateRunner<S1WatchConnectedInput, S1WatchConnectedOutput>() {

    override fun evaluate(
        context: Context,
        input: S1WatchConnectedInput,
    ): TaskerPluginResultCondition<S1WatchConnectedOutput> {
        val state = when (val r = StateSupport.queryState(context)) {
            is BridgeResult.Ok -> r.value
            is BridgeResult.Err -> return TaskerPluginResultConditionUnknown()
        }
        val watch = StateSupport.matchWatch(state, input.serial)
            ?: return TaskerPluginResultConditionUnsatisfied()
        val count = StateSupport.connectedCount(state)
        return TaskerPluginResultConditionSatisfied(context, buildOutput(watch, count))
    }

    private fun buildOutput(w: WatchRef, count: Int): S1WatchConnectedOutput {
        val battery = w.battery?.toString()
        return S1WatchConnectedOutput(
            pbJson = StateJson.obj(
                "serial" to w.serial,
                "name" to w.name,
                "nickname" to w.nickname,
                "model" to w.model,
                "fw" to w.fw,
                "battery" to battery,
                "address" to w.address,
                "connected" to "true",
                "connected_count" to count.toString(),
            ),
            connected = "true",
            connectedCount = count.toString(),
            serial = w.serial,
            name = w.name,
            nickname = w.nickname,
            model = w.model,
            fw = w.fw,
            battery = battery,
            address = w.address,
        )
    }
}
