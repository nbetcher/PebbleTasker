package com.nickbether.pebbletasker.tasker.state.bluetooth

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.tasker.base.PebbleStateRunner
import com.nickbether.pebbletasker.tasker.state.StateJson
import com.nickbether.pebbletasker.tasker.state.StateSupport

/**
 * S6 runner. Reads the last cached `bt.state` event (data["enabled"]).
 *
 *  - no cached bt.state yet  -> Unknown.
 *  - enabled == true         -> Satisfied + %pbl_bt_enabled.
 *  - enabled == false        -> Unsatisfied.
 */
class S6BluetoothRunner : PebbleStateRunner<S6BluetoothInput, S6BluetoothOutput>() {

    override fun evaluate(
        context: Context,
        input: S6BluetoothInput,
    ): TaskerPluginResultCondition<S6BluetoothOutput> {
        val snapshot = StateSupport.queryState(context).valueOrNull() ?: return TaskerPluginResultConditionUnknown()
        com.nickbether.pebbletasker.cache.EventCache.get(context).seedState(snapshot)
        val enabled = snapshot.data.bluetoothEnabled ?: return TaskerPluginResultConditionUnknown()
        return if (enabled) {
            TaskerPluginResultConditionSatisfied(
                context,
                S6BluetoothOutput(
                    pbJson = StateJson.obj("bt_enabled" to "true"),
                    btEnabled = "true",
                ),
            )
        } else {
            TaskerPluginResultConditionUnsatisfied()
        }
    }
}
