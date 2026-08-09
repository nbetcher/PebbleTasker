package com.nickbether.pebbletasker.tasker.action.launchapp

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
import com.nickbether.pebbletasker.tasker.action.common.SerialUuidActionActivity
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A3 — Launch Watch App (FINAL DESIGN §2.3, normal tier). Sends `watch.launchApp` (uuid required).
 */

@TaskerInputRoot
class LaunchAppInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("uuid", labelResIdName = "lbl_uuid")
    var uuid: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class LaunchAppOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_uuid")
    @get:TaskerOutputVariable(PbVars.UUID, labelResIdName = "lbl_uuid")
    val uuid: String? = null,
)

class LaunchAppRunner : PebbleActionRunner<LaunchAppInput, LaunchAppOutput>() {
    override fun execute(context: Context, input: TaskerInput<LaunchAppInput>): BridgeResult<Map<String, String>> {
        val uuid = input.regular.uuid?.trim().orEmpty()
        if (uuid.isEmpty()) {
            return BridgeResult.err(com.nickbether.pebbletasker.tasker.ErrCodes.INVALID_ARGS, "uuid is required")
        }
        return ActionSend.send(
            context,
            CommandSender.Type.WATCH_LAUNCH_APP,
            watch = input.regular.serial,
            args = mapOf("uuid" to uuid),
        )
    }

    override fun buildOutput(input: TaskerInput<LaunchAppInput>, result: CommandResult): LaunchAppOutput =
        LaunchAppOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            uuid = ActionOutputs.data(result, "uuid") ?: input.regular.uuid,
        )

    // INVALID_ARGS for a missing required uuid is a SOFT error (delivered as %pbl_ok=false), not infra.
    override fun isHardFailure(code: Int): Boolean =
        code != com.nickbether.pebbletasker.tasker.ErrCodes.INVALID_ARGS && super.isHardFailure(code)
}

class LaunchAppHelper(config: TaskerPluginConfig<LaunchAppInput>) :
    ActionHelper<LaunchAppInput, LaunchAppOutput, LaunchAppRunner>(config) {
    override val inputClass = LaunchAppInput::class.java
    override val outputClass = LaunchAppOutput::class.java
    override val runnerClass = LaunchAppRunner::class.java
    override val defaultBlurb: String = "Pebble: Launch Watch App"
    override fun blurbFor(input: LaunchAppInput): String =
        "Launch app: ${input.uuid?.takeIf { it.isNotBlank() } ?: "(unset)"}"
}

class LaunchAppActivity :
    SerialUuidActionActivity<LaunchAppInput, LaunchAppOutput, LaunchAppRunner, LaunchAppHelper>() {
    override val titleRes = R.string.act_launch_app_title
    override val descRes = R.string.act_launch_app_desc
    override fun getNewHelper(config: TaskerPluginConfig<LaunchAppInput>) = LaunchAppHelper(config)
    override fun makeInput(serial: String?, uuid: String?) = LaunchAppInput(serial = serial, uuid = uuid)
    override fun serialOf(input: LaunchAppInput) = input.serial
    override fun uuidOf(input: LaunchAppInput) = input.uuid
}
