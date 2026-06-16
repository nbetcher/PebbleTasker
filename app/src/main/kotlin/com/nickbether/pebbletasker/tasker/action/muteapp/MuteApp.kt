package com.nickbether.pebbletasker.tasker.action.muteapp

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
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.action.common.ActionHelper
import com.nickbether.pebbletasker.tasker.action.common.ActionOutputs
import com.nickbether.pebbletasker.tasker.action.common.ActionSend
import com.nickbether.pebbletasker.tasker.action.common.GenericFieldsActionActivity
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A13 — Mute App Notifications (FINAL DESIGN §2.3, sensitive tier).
 * Sends `notification.muteApp` with pkg + duration (minutes).
 */

@TaskerInputRoot
class MuteAppInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("pkg", labelResIdName = "lbl_pkg")
    var pkg: String? = null,
    @field:TaskerInputField("duration", labelResIdName = "lbl_duration")
    var duration: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class MuteAppOutput @JvmOverloads constructor(
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
)

class MuteAppRunner : PebbleActionRunner<MuteAppInput, MuteAppOutput>() {
    override fun execute(context: Context, input: TaskerInput<MuteAppInput>): BridgeResult<Map<String, String>> {
        val pkg = input.regular.pkg?.trim().orEmpty()
        if (pkg.isEmpty()) return BridgeResult.err(ErrCodes.INVALID_ARGS, "pkg is required")
        val args = HashMap<String, String>()
        args["pkg"] = pkg
        input.regular.duration?.trim()?.takeIf { it.isNotEmpty() }?.let { args["duration"] = it }
        return ActionSend.send(context, CommandSender.Type.NOTIFICATION_MUTE_APP, watch = input.regular.serial, args = args)
    }

    override fun buildOutput(input: TaskerInput<MuteAppInput>, result: CommandResult): MuteAppOutput =
        MuteAppOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
        )

    override fun isHardFailure(code: Int): Boolean =
        code != ErrCodes.INVALID_ARGS && super.isHardFailure(code)
}

class MuteAppHelper(config: TaskerPluginConfig<MuteAppInput>) :
    ActionHelper<MuteAppInput, MuteAppOutput, MuteAppRunner>(config) {
    override val inputClass = MuteAppInput::class.java
    override val outputClass = MuteAppOutput::class.java
    override val runnerClass = MuteAppRunner::class.java
    override val defaultBlurb: String = "Pebble: Mute App Notifications"
    override fun blurbFor(input: MuteAppInput): String =
        "Mute: ${input.pkg?.takeIf { it.isNotBlank() } ?: "(unset)"}"
}

class MuteAppActivity :
    GenericFieldsActionActivity<MuteAppInput, MuteAppOutput, MuteAppRunner, MuteAppHelper>() {
    override val isSensitive = false // FLAG_SECURE candidate; intentionally off (flip to true to enable)
    override val titleRes = R.string.act_mute_title
    override val descRes = R.string.act_mute_desc
    override val fields = listOf(
        FieldSpec(R.string.lbl_serial, isSerial = true),
        FieldSpec(R.string.lbl_pkg),
        FieldSpec(R.string.lbl_duration),
    )
    override fun getNewHelper(config: TaskerPluginConfig<MuteAppInput>) = MuteAppHelper(config)
    override fun makeInput(values: List<String?>) =
        MuteAppInput(serial = values.getOrNull(0), pkg = values.getOrNull(1), duration = values.getOrNull(2))
    override fun valuesOf(input: MuteAppInput) = listOf(input.serial, input.pkg, input.duration)
}
