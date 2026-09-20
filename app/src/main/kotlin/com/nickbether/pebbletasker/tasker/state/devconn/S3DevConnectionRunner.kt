package com.nickbether.pebbletasker.tasker.state.devconn

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.tasker.base.PebbleStateRunner
import com.nickbether.pebbletasker.tasker.state.StateJson
import com.nickbether.pebbletasker.tasker.state.StateSupport

/**
 * S3 runner. Reads the last cached `dev.state` event (data["enabled"], data["transport"]).
 *
 *  - no cached dev.state yet (bridge hasn't reported / collector absent) -> Unknown (won't flap).
 *  - enabled == true  -> Satisfied + %pbl_dev_enabled / %pbl_transport.
 *  - enabled == false -> Unsatisfied.
 */
class S3DevConnectionRunner : PebbleStateRunner<S3DevConnectionInput, S3DevConnectionOutput>() {

    override fun evaluate(
        context: Context,
        input: S3DevConnectionInput,
    ): TaskerPluginResultCondition<S3DevConnectionOutput> {
        val snapshot = StateSupport.queryState(context).valueOrNull() ?: return TaskerPluginResultConditionUnknown()
        com.nickbether.pebbletasker.cache.EventCache.get(context).seedState(snapshot)
        if (snapshot.data.watches.isEmpty()) return TaskerPluginResultConditionUnsatisfied()
        val enabled = snapshot.data.watches.map { it.devEnabled }
        if (enabled.none { it == true }) return if (enabled.any { it == null }) TaskerPluginResultConditionUnknown() else TaskerPluginResultConditionUnsatisfied()
        val transport: String? = null // Framework cannot distinguish LAN from relay.
        return TaskerPluginResultConditionSatisfied(
            context,
            S3DevConnectionOutput(
                pbJson = StateJson.obj("dev_enabled" to "true", "transport" to transport),
                devEnabled = "true",
                transport = transport,
            ),
        )
    }
}
