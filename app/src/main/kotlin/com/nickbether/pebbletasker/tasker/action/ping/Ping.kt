package com.nickbether.pebbletasker.tasker.action.ping

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
 * A11 — Send Ping (FINAL DESIGN §2.3, normal tier). Sends `system.ping`; surfaces round-trip ms.
 * Bridge success `data`: sent="true" (and, where the bridge measures it, rtt_ms).
 */

@TaskerInputRoot
class PingInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class PingOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_rtt_ms")
    @get:TaskerOutputVariable(PbVars.RTT_MS, labelResIdName = "lbl_out_rtt")
    val rttMs: String? = null,
)

class PingRunner : PebbleActionRunner<PingInput, PingOutput>() {
    override fun execute(context: Context, input: TaskerInput<PingInput>): BridgeResult<Map<String, String>> =
        ActionSend.send(context, CommandSender.Type.SYSTEM_PING, watch = input.regular.serial)

    override fun buildOutput(input: TaskerInput<PingInput>, result: CommandResult): PingOutput =
        PingOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            rttMs = ActionOutputs.data(result, "rtt_ms"),
        )
}

class PingHelper(config: TaskerPluginConfig<PingInput>) :
    ActionHelper<PingInput, PingOutput, PingRunner>(config) {
    override val inputClass = PingInput::class.java
    override val outputClass = PingOutput::class.java
    override val runnerClass = PingRunner::class.java
    override val defaultBlurb: String = "Pebble: Send Ping"
    override fun blurbFor(input: PingInput): String {
        val s = input.serial?.takeIf { it.isNotBlank() }
        return if (s == null) "Ping: active watch" else "Ping: $s"
    }
}

class PingActivity :
    SerialOnlyActionActivity<PingInput, PingOutput, PingRunner, PingHelper>() {
    override val titleRes = R.string.act_ping_title
    override val descRes = R.string.act_ping_desc
    override fun getNewHelper(config: TaskerPluginConfig<PingInput>) = PingHelper(config)
    override fun makeInput(serial: String?) = PingInput(serial = serial)
    override fun serialOf(input: PingInput) = input.serial
}
