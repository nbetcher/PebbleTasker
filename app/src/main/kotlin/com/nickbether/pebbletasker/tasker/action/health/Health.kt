package com.nickbether.pebbletasker.tasker.action.health

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender
import com.nickbether.pebbletasker.tasker.action.common.ActionHelper
import com.nickbether.pebbletasker.tasker.action.common.ActionOutputs
import com.nickbether.pebbletasker.tasker.action.common.ActionSend
import com.nickbether.pebbletasker.tasker.action.common.SerialOnlyActionActivity
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A12 — Get Health Snapshot (FINAL DESIGN §2.3, normal tier, SENSITIVE config UI).
 * Sends `health.snapshot`; surfaces steps / heart-rate / sleep metrics.
 * Bridge success `data`: steps_today, latest_hr, sleep_min, resting_hr, hr_zones_json.
 */

@TaskerInputRoot
class HealthInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class HealthOutput @JvmOverloads constructor(
    @field:TaskerInputField("pb_json")
    @get:TaskerOutputVariable(PbVars.JSON, labelResIdName = "lbl_out_json")
    val pbJson: String? = null,
    @field:TaskerInputField("pb_ok")
    @get:TaskerOutputVariable(PbVars.OK, labelResIdName = "lbl_out_ok")
    val pbOk: String? = null,
    @field:TaskerInputField("pb_err")
    @get:TaskerOutputVariable(PbVars.ERR, labelResIdName = "lbl_out_err")
    val pbErr: String? = null,
    @field:TaskerInputField("pb_errmsg")
    @get:TaskerOutputVariable(PbVars.ERRMSG, labelResIdName = "lbl_out_errmsg")
    val pbErrmsg: String? = null,
    @field:TaskerInputField("pb_steps_today")
    @get:TaskerOutputVariable(PbVars.STEPS_TODAY, labelResIdName = "lbl_out_steps")
    val stepsToday: String? = null,
    @field:TaskerInputField("pb_latest_hr")
    @get:TaskerOutputVariable(PbVars.LATEST_HR, labelResIdName = "lbl_out_hr")
    val latestHr: String? = null,
    @field:TaskerInputField("pb_sleep_min")
    @get:TaskerOutputVariable(PbVars.SLEEP_MIN, labelResIdName = "lbl_out_sleep")
    val sleepMin: String? = null,
    @field:TaskerInputField("pb_resting_hr")
    @get:TaskerOutputVariable(PbVars.RESTING_HR, labelResIdName = "lbl_out_resting_hr")
    val restingHr: String? = null,
    @field:TaskerInputField("pb_hr_zones_json")
    @get:TaskerOutputVariable(PbVars.HR_ZONES_JSON, labelResIdName = "lbl_out_hr_zones")
    val hrZonesJson: String? = null,
)

class HealthRunner : PebbleActionRunner<HealthInput, HealthOutput>() {
    override fun execute(context: Context, input: TaskerInput<HealthInput>): BridgeResult<Map<String, String>> =
        ActionSend.send(context, CommandSender.Type.HEALTH_SNAPSHOT, watch = input.regular.serial)

    override fun buildOutput(input: TaskerInput<HealthInput>, result: CommandResult): HealthOutput =
        HealthOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            stepsToday = ActionOutputs.data(result, "steps_today"),
            latestHr = ActionOutputs.data(result, "latest_hr"),
            sleepMin = ActionOutputs.data(result, "sleep_min"),
            restingHr = ActionOutputs.data(result, "resting_hr"),
            hrZonesJson = ActionOutputs.data(result, "hr_zones_json"),
        )
}

class HealthHelper(config: TaskerPluginConfig<HealthInput>) :
    ActionHelper<HealthInput, HealthOutput, HealthRunner>(config) {
    override val inputClass = HealthInput::class.java
    override val outputClass = HealthOutput::class.java
    override val runnerClass = HealthRunner::class.java
    override val defaultBlurb: String = "Pebble: Get Health Snapshot"
    override fun blurbFor(input: HealthInput): String {
        val s = input.serial?.takeIf { it.isNotBlank() }
        return if (s == null) "Health snapshot: active watch" else "Health snapshot: $s"
    }
}

class HealthActivity :
    SerialOnlyActionActivity<HealthInput, HealthOutput, HealthRunner, HealthHelper>() {
    override val isSensitive = false // FLAG_SECURE candidate; intentionally off (flip to true to enable)
    override val titleRes = R.string.act_health_title
    override val descRes = R.string.act_health_desc
    override fun getNewHelper(config: TaskerPluginConfig<HealthInput>) = HealthHelper(config)
    override fun makeInput(serial: String?) = HealthInput(serial = serial)
    override fun serialOf(input: HealthInput) = input.serial
}
