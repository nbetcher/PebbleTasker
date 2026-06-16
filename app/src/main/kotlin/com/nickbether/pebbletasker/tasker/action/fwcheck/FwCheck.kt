package com.nickbether.pebbletasker.tasker.action.fwcheck

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
 * A16 — Check Firmware Update (FINAL DESIGN §2.3, sensitive tier). Sends `fw.check`.
 * Bridge success `data`: fw_available, fw_version.
 */

@TaskerInputRoot
class FwCheckInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class FwCheckOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_fw_available")
    @get:TaskerOutputVariable(PbVars.FW_AVAILABLE, labelResIdName = "lbl_out_fw_available")
    val fwAvailable: String? = null,
    @field:TaskerInputField("pb_fw_version")
    @get:TaskerOutputVariable(PbVars.FW_VERSION, labelResIdName = "lbl_out_fw_version")
    val fwVersion: String? = null,
)

class FwCheckRunner : PebbleActionRunner<FwCheckInput, FwCheckOutput>() {
    override fun execute(context: Context, input: TaskerInput<FwCheckInput>): BridgeResult<Map<String, String>> =
        ActionSend.send(context, CommandSender.Type.FW_CHECK, watch = input.regular.serial)

    override fun buildOutput(input: TaskerInput<FwCheckInput>, result: CommandResult): FwCheckOutput =
        FwCheckOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            fwAvailable = ActionOutputs.data(result, "fw_available"),
            fwVersion = ActionOutputs.data(result, "fw_version"),
        )
}

class FwCheckHelper(config: TaskerPluginConfig<FwCheckInput>) :
    ActionHelper<FwCheckInput, FwCheckOutput, FwCheckRunner>(config) {
    override val inputClass = FwCheckInput::class.java
    override val outputClass = FwCheckOutput::class.java
    override val runnerClass = FwCheckRunner::class.java
    override val defaultBlurb: String = "Pebble: Check Firmware Update"
    override fun blurbFor(input: FwCheckInput): String {
        val s = input.serial?.takeIf { it.isNotBlank() }
        return if (s == null) "Firmware check: active watch" else "Firmware check: $s"
    }
}

class FwCheckActivity :
    SerialOnlyActionActivity<FwCheckInput, FwCheckOutput, FwCheckRunner, FwCheckHelper>() {
    override val isSensitive = false // FLAG_SECURE candidate; intentionally off (flip to true to enable)
    override val titleRes = R.string.act_fw_check_title
    override val descRes = R.string.act_fw_check_desc
    override fun getNewHelper(config: TaskerPluginConfig<FwCheckInput>) = FwCheckHelper(config)
    override fun makeInput(serial: String?) = FwCheckInput(serial = serial)
    override fun serialOf(input: FwCheckInput) = input.serial
}
