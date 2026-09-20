package com.nickbether.pebbletasker.tasker.state.firmware

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.tasker.base.PebbleStateRunner
import com.nickbether.pebbletasker.tasker.state.StateJson
import com.nickbether.pebbletasker.tasker.state.StateSupport

/**
 * S5 runner. Reads the last cached `fw.status` event (data["status"], data["progress"]).
 *
 *  - no cached fw.status yet                 -> Unknown.
 *  - serial filter set and doesn't match     -> Unsatisfied.
 *  - status is an in-progress phase          -> Satisfied + %pbl_fw_progress / %pbl_fw_status.
 *  - status is idle/complete/failed          -> Unsatisfied.
 */
class S5FirmwareUpdatingRunner :
    PebbleStateRunner<S5FirmwareUpdatingInput, S5FirmwareUpdatingOutput>() {

    override fun evaluate(
        context: Context,
        input: S5FirmwareUpdatingInput,
    ): TaskerPluginResultCondition<S5FirmwareUpdatingOutput> {
        val snapshot = StateSupport.queryState(context).valueOrNull() ?: return TaskerPluginResultConditionUnknown()
        com.nickbether.pebbletasker.cache.EventCache.get(context).seedState(snapshot)
        val watches = snapshot.data.watches.filter { input.serial.isNullOrBlank() || it.serial == input.serial || it.address == input.serial }
        if (watches.isEmpty()) return TaskerPluginResultConditionUnsatisfied()
        val watch = watches.firstOrNull { it.fwStatus in IN_PROGRESS }
            ?: return if (watches.any { it.fwStatus == null }) TaskerPluginResultConditionUnknown() else TaskerPluginResultConditionUnsatisfied()
        val status = watch.fwStatus
        val progress = watch.fwProgress?.toString()
        return TaskerPluginResultConditionSatisfied(
            context,
            S5FirmwareUpdatingOutput(
                pbJson = StateJson.obj(
                    "fw_progress" to progress,
                    "fw_status" to status,
                    "serial" to watch.serial,
                ),
                fwProgress = progress,
                fwStatus = status,
                serial = watch.serial,
            ),
        )
    }

    private companion object {
        /** fw.status phases that mean an update is actively running. */
        val IN_PROGRESS = setOf("waiting", "in_progress", "rebooting")
    }
}
