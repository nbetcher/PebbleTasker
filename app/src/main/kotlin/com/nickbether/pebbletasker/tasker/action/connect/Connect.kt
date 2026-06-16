package com.nickbether.pebbletasker.tasker.action.connect

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
 * A14 — Connect Watch (FINAL DESIGN §2.3, sensitive tier). Sends `watch.connect`.
 */

@TaskerInputRoot
class ConnectInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class ConnectOutput @JvmOverloads constructor(
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

class ConnectRunner : PebbleActionRunner<ConnectInput, ConnectOutput>() {
    override fun execute(context: Context, input: TaskerInput<ConnectInput>): BridgeResult<Map<String, String>> =
        ActionSend.send(context, CommandSender.Type.WATCH_CONNECT, watch = input.regular.serial)

    override fun buildOutput(input: TaskerInput<ConnectInput>, result: CommandResult): ConnectOutput =
        ConnectOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            serial = ActionOutputs.data(result, "serial") ?: input.regular.serial,
        )
}

class ConnectHelper(config: TaskerPluginConfig<ConnectInput>) :
    ActionHelper<ConnectInput, ConnectOutput, ConnectRunner>(config) {
    override val inputClass = ConnectInput::class.java
    override val outputClass = ConnectOutput::class.java
    override val runnerClass = ConnectRunner::class.java
    override val defaultBlurb: String = "Pebble: Connect Watch"
    override fun blurbFor(input: ConnectInput): String {
        val s = input.serial?.takeIf { it.isNotBlank() }
        return if (s == null) "Connect: active watch" else "Connect: $s"
    }
}

class ConnectActivity :
    SerialOnlyActionActivity<ConnectInput, ConnectOutput, ConnectRunner, ConnectHelper>() {
    override val isSensitive = false // FLAG_SECURE candidate; intentionally off (flip to true to enable)
    override val titleRes = R.string.act_connect_title
    override val descRes = R.string.act_connect_desc
    override fun getNewHelper(config: TaskerPluginConfig<ConnectInput>) = ConnectHelper(config)
    override fun makeInput(serial: String?) = ConnectInput(serial = serial)
    override fun serialOf(input: ConnectInput) = input.serial
}
