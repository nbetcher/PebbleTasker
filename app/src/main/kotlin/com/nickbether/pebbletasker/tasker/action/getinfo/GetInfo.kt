package com.nickbether.pebbletasker.tasker.action.getinfo

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender
import com.nickbether.pebbletasker.tasker.action.common.ActionHelper
import com.nickbether.pebbletasker.tasker.action.common.ActionOutputs
import com.nickbether.pebbletasker.tasker.action.common.ActionSend
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A1 — Get Watch Info (FINAL DESIGN §2.3, normal tier).
 *
 * Sends `watch.getInfo`; surfaces the identity block + running app / watchface / connected.
 * Bridge success `data` keys: serial,name,nickname?,model,fw,battery?,address,connected,running_app?.
 *
 * Result model: success-with-ok=false (PebbleActionRunner). The serial field is %var-capable.
 */

@TaskerInputRoot
class GetInfoInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class GetInfoOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_name")
    @get:TaskerOutputVariable(PbVars.NAME, labelResIdName = "lbl_name")
    val name: String? = null,
    @field:TaskerInputField("pb_nickname")
    @get:TaskerOutputVariable(PbVars.NICKNAME, labelResIdName = "lbl_nickname")
    val nickname: String? = null,
    @field:TaskerInputField("pb_model")
    @get:TaskerOutputVariable(PbVars.MODEL, labelResIdName = "lbl_model")
    val model: String? = null,
    @field:TaskerInputField("pb_fw")
    @get:TaskerOutputVariable(PbVars.FW, labelResIdName = "lbl_fw")
    val fw: String? = null,
    @field:TaskerInputField("pb_battery")
    @get:TaskerOutputVariable(PbVars.BATTERY, labelResIdName = "lbl_battery")
    val battery: String? = null,
    @field:TaskerInputField("pb_address")
    @get:TaskerOutputVariable(PbVars.ADDRESS, labelResIdName = "lbl_address")
    val address: String? = null,
    @field:TaskerInputField("pb_running_app")
    @get:TaskerOutputVariable(PbVars.RUNNING_APP, labelResIdName = "lbl_running_app")
    val runningApp: String? = null,
    @field:TaskerInputField("pb_watchface")
    @get:TaskerOutputVariable(PbVars.WATCHFACE, labelResIdName = "lbl_watchface")
    val watchface: String? = null,
    @field:TaskerInputField("pb_connected")
    @get:TaskerOutputVariable(PbVars.CONNECTED, labelResIdName = "lbl_connected")
    val connected: String? = null,
)

class GetInfoRunner : PebbleActionRunner<GetInfoInput, GetInfoOutput>() {

    override fun execute(
        context: Context,
        input: TaskerInput<GetInfoInput>,
    ): BridgeResult<Map<String, String>> =
        ActionSend.send(context, CommandSender.Type.WATCH_GET_INFO, watch = input.regular.serial)

    override fun buildOutput(
        input: TaskerInput<GetInfoInput>,
        result: CommandResult,
    ): GetInfoOutput = GetInfoOutput(
        pbJson = ActionOutputs.jsonBlob(result),
        pbOk = ActionOutputs.okStr(result),
        pbErr = ActionOutputs.errStr(result),
        pbErrmsg = ActionOutputs.errMsgStr(result),
        serial = ActionOutputs.data(result, "serial"),
        name = ActionOutputs.data(result, "name"),
        nickname = ActionOutputs.data(result, "nickname"),
        model = ActionOutputs.data(result, "model"),
        fw = ActionOutputs.data(result, "fw"),
        battery = ActionOutputs.data(result, "battery"),
        address = ActionOutputs.data(result, "address"),
        runningApp = ActionOutputs.data(result, "running_app"),
        watchface = ActionOutputs.data(result, "watchface"),
        connected = ActionOutputs.data(result, "connected"),
    )
}

class GetInfoHelper(config: TaskerPluginConfig<GetInfoInput>) :
    ActionHelper<GetInfoInput, GetInfoOutput, GetInfoRunner>(config) {
    override val inputClass = GetInfoInput::class.java
    override val outputClass = GetInfoOutput::class.java
    override val runnerClass = GetInfoRunner::class.java
    override val defaultBlurb: String = "Pebble: Get Watch Info"
    override fun blurbFor(input: GetInfoInput): String {
        val s = input.serial?.takeIf { it.isNotBlank() }
        return if (s == null) "Get info: active watch" else "Get info: $s"
    }
}
