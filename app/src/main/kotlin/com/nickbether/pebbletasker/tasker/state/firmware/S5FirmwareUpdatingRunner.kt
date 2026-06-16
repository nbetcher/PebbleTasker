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
 *  - status is an in-progress phase          -> Satisfied + %pb_fw_progress / %pb_fw_status.
 *  - status is idle/complete/failed          -> Unsatisfied.
 */
class S5FirmwareUpdatingRunner :
    PebbleStateRunner<S5FirmwareUpdatingInput, S5FirmwareUpdatingOutput>() {

    override fun evaluate(
        context: Context,
        input: S5FirmwareUpdatingInput,
    ): TaskerPluginResultCondition<S5FirmwareUpdatingOutput> {
        val e = StateSupport.cached(context, StateSupport.TYPE_FW_STATUS)
            ?: return TaskerPluginResultConditionUnknown()

        val serial = input.serial
        if (!serial.isNullOrBlank() &&
            e.watch?.serial != serial && e.watch?.address != serial
        ) {
            return TaskerPluginResultConditionUnsatisfied()
        }

        val status = (e.str("status") ?: e.str("fw_status"))?.lowercase()
        val updating = status in IN_PROGRESS
        if (!updating) return TaskerPluginResultConditionUnsatisfied()

        val progress = e.str("progress") ?: e.str("fw_progress")
        return TaskerPluginResultConditionSatisfied(
            context,
            S5FirmwareUpdatingOutput(
                pbJson = StateJson.obj(
                    "fw_progress" to progress,
                    "fw_status" to status,
                    "serial" to e.watch?.serial,
                ),
                fwProgress = progress,
                fwStatus = status,
                serial = e.watch?.serial,
            ),
        )
    }

    private companion object {
        /** fw.status phases that mean an update is actively running. */
        val IN_PROGRESS = setOf("updating", "in_progress", "installing", "downloading", "transferring")
    }
}
