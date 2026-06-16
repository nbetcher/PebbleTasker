package com.nickbether.pebbletasker.tasker.action.disconnect

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
 * A15 — Disconnect Watch (FINAL DESIGN §2.3, sensitive tier). Sends `watch.disconnect`.
 */

@TaskerInputRoot
class DisconnectInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class DisconnectOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_serial")
    @get:TaskerOutputVariable(PbVars.SERIAL, labelResIdName = "lbl_serial")
    val serial: String? = null,
)

class DisconnectRunner : PebbleActionRunner<DisconnectInput, DisconnectOutput>() {
    override fun execute(context: Context, input: TaskerInput<DisconnectInput>): BridgeResult<Map<String, String>> =
        ActionSend.send(context, CommandSender.Type.WATCH_DISCONNECT, watch = input.regular.serial)

    override fun buildOutput(input: TaskerInput<DisconnectInput>, result: CommandResult): DisconnectOutput =
        DisconnectOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            serial = ActionOutputs.data(result, "serial") ?: input.regular.serial,
        )
}

class DisconnectHelper(config: TaskerPluginConfig<DisconnectInput>) :
    ActionHelper<DisconnectInput, DisconnectOutput, DisconnectRunner>(config) {
    override val inputClass = DisconnectInput::class.java
    override val outputClass = DisconnectOutput::class.java
    override val runnerClass = DisconnectRunner::class.java
    override val defaultBlurb: String = "Pebble: Disconnect Watch"
    override fun blurbFor(input: DisconnectInput): String {
        val s = input.serial?.takeIf { it.isNotBlank() }
        return if (s == null) "Disconnect: active watch" else "Disconnect: $s"
    }
}

class DisconnectActivity :
    SerialOnlyActionActivity<DisconnectInput, DisconnectOutput, DisconnectRunner, DisconnectHelper>() {
    override val isSensitive = true
    override val titleRes = R.string.act_disconnect_title
    override val descRes = R.string.act_disconnect_desc
    override fun getNewHelper(config: TaskerPluginConfig<DisconnectInput>) = DisconnectHelper(config)
    override fun makeInput(serial: String?) = DisconnectInput(serial = serial)
    override fun serialOf(input: DisconnectInput) = input.serial
}
