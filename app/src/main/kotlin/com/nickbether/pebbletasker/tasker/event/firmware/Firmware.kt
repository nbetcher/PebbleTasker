package com.nickbether.pebbletasker.tasker.event.firmware

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.event.FilterMatch
import com.nickbether.pebbletasker.tasker.event.GenericEventConfigActivity
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * E12 — Pebble Firmware Update (COLLECTOR). Fires on firmware update lifecycle transitions
 * (available/downloading/installing/complete/failed). Backing type fw.status.
 */
@TaskerInputRoot
class FirmwareFilter @JvmOverloads constructor(
    @field:TaskerInputField("fw_status", labelResIdName = "pb_evt_lbl_fw_status")
    var fwStatus: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class FirmwareOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.FW_STATUS)
    @field:TaskerInputField("pb_fw_status")
    var pbFwStatus: String? = null,
    @get:TaskerOutputVariable(PbVars.FW_VERSION)
    @field:TaskerInputField("pb_fw_version")
    var pbFwVersion: String? = null,
    @get:TaskerOutputVariable(PbVars.PROGRESS)
    @field:TaskerInputField("pb_progress")
    var pbProgress: String? = null,
) : BaseEventOutput()

class FirmwareRunner : PebbleEventRunner<FirmwareFilter, FirmwareOutput>() {
    override val eventType: String = EventRouting.TYPE_FIRMWARE

    override fun evaluate(
        context: Context,
        filter: FirmwareFilter,
        cached: CachedEvent?,
        update: FirmwareOutput?,
    ): TaskerPluginResultCondition<FirmwareOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val status = e.str("fw_status") ?: e.str("status")
        if (!FilterMatch.eq(filter.fwStatus, status)) return TaskerPluginResultConditionUnsatisfied()
        val out = FirmwareOutput(
            pbFwStatus = status,
            pbFwVersion = e.str("fw_version"),
            pbProgress = e.str("progress"),
        ).fillBase<FirmwareOutput>(
            e,
            buildMap {
                status?.let { put("fw_status", it) }
                e.str("fw_version")?.let { put("fw_version", it) }
                e.str("progress")?.let { put("progress", it) }
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class FirmwareHelper(config: TaskerPluginConfig<FirmwareFilter>) :
    PebbleEventHelper<FirmwareFilter, FirmwareOutput, FirmwareRunner>(config) {
    override val inputClass = FirmwareFilter::class.java
    override val outputClass = FirmwareOutput::class.java
    override val runnerClass = FirmwareRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<FirmwareFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires on a firmware update status change.")
            .append("\nOutputs: %pbl_fw_status %pbl_fw_version %pbl_progress + %pbl_json.")
    }
}

class FirmwareActivity :
    GenericEventConfigActivity<FirmwareFilter, FirmwareOutput, FirmwareRunner, FirmwareHelper>() {

    override val titleRes = R.string.pb_evt_firmware_title
    override val descRes = R.string.pb_evt_firmware_desc

    override fun buildFields() = listOf(
        // Blank already matched anything; the pick-list makes that visible ("Any") and spells out the
        // status vocabulary that was previously only described in the plugin's help text.
        FieldSpec(
            "fw_status",
            getString(R.string.pb_evt_lbl_fw_status),
            options = listOf(
                "Available" to "available",
                "Downloading" to "downloading",
                "Installing" to "installing",
                "Complete" to "complete",
                "Failed" to "failed",
            ),
        ),
    )

    override fun getNewHelper(config: TaskerPluginConfig<FirmwareFilter>) = FirmwareHelper(config)

    override fun buildInput(values: Map<String, String>) =
        FirmwareFilter(fwStatus = values["fw_status"])

    override fun extractValues(input: FirmwareFilter) =
        mapOf("fw_status" to input.fwStatus.orEmpty())
}
