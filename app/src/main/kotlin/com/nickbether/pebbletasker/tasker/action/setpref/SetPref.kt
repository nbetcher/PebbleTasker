package com.nickbether.pebbletasker.tasker.action.setpref

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
 * A6 — Set Watch Preference (FINAL DESIGN §2.3, normal/sensitive tier).
 * Sends `watch.setPref` with pref_key + pref_value. (Bridge handler currently TODO; returns
 * UNSUPPORTED_COMMAND until wired — surfaced as %pb_ok=false.)
 */

@TaskerInputRoot
class SetPrefInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("pref_key", labelResIdName = "lbl_pref_key")
    var prefKey: String? = null,
    @field:TaskerInputField("pref_value", labelResIdName = "lbl_pref_value")
    var prefValue: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class SetPrefOutput @JvmOverloads constructor(
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

class SetPrefRunner : PebbleActionRunner<SetPrefInput, SetPrefOutput>() {
    override fun execute(context: Context, input: TaskerInput<SetPrefInput>): BridgeResult<Map<String, String>> {
        val key = input.regular.prefKey?.trim().orEmpty()
        if (key.isEmpty()) return BridgeResult.err(ErrCodes.INVALID_ARGS, "pref_key is required")
        return ActionSend.send(
            context,
            CommandSender.Type.WATCH_SET_PREF,
            watch = input.regular.serial,
            args = mapOf("pref_key" to key, "pref_value" to input.regular.prefValue.orEmpty()),
        )
    }

    override fun buildOutput(input: TaskerInput<SetPrefInput>, result: CommandResult): SetPrefOutput =
        SetPrefOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
        )

    override fun isHardFailure(code: Int): Boolean =
        code != ErrCodes.INVALID_ARGS && super.isHardFailure(code)
}

class SetPrefHelper(config: TaskerPluginConfig<SetPrefInput>) :
    ActionHelper<SetPrefInput, SetPrefOutput, SetPrefRunner>(config) {
    override val inputClass = SetPrefInput::class.java
    override val outputClass = SetPrefOutput::class.java
    override val runnerClass = SetPrefRunner::class.java
    override val defaultBlurb: String = "Pebble: Set Watch Preference"
    override fun blurbFor(input: SetPrefInput): String =
        "Set pref: ${input.prefKey?.takeIf { it.isNotBlank() } ?: "(unset)"}"
}

class SetPrefActivity :
    GenericFieldsActionActivity<SetPrefInput, SetPrefOutput, SetPrefRunner, SetPrefHelper>() {
    override val isSensitive = true
    override val titleRes = R.string.act_set_pref_title
    override val descRes = R.string.act_set_pref_desc
    override val fields = listOf(
        FieldSpec(R.string.lbl_serial, isSerial = true),
        FieldSpec(R.string.lbl_pref_key),
        FieldSpec(R.string.lbl_pref_value),
    )
    override fun getNewHelper(config: TaskerPluginConfig<SetPrefInput>) = SetPrefHelper(config)
    override fun makeInput(values: List<String?>) =
        SetPrefInput(serial = values.getOrNull(0), prefKey = values.getOrNull(1), prefValue = values.getOrNull(2))
    override fun valuesOf(input: SetPrefInput) = listOf(input.serial, input.prefKey, input.prefValue)
}
