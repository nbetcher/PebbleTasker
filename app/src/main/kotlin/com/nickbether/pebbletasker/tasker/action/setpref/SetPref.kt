package com.nickbether.pebbletasker.tasker.action.setpref

import com.google.android.material.textfield.TextInputEditText
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.bridge.CommandSender
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.action.common.ActionResultOutput
import com.nickbether.pebbletasker.tasker.action.common.Args
import com.nickbether.pebbletasker.tasker.action.common.FormActionActivity
import com.nickbether.pebbletasker.tasker.action.common.WatchCommandHelper
import com.nickbether.pebbletasker.tasker.action.common.WatchCommandRunner
import com.nickbether.pebbletasker.tasker.action.prefs.layoutOf
import com.nickbether.pebbletasker.tasker.action.watchctl.RESULT_VARS
import com.nickbether.pebbletasker.tasker.prefs.PrefPicker
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A6 — Set Watch Preference (sensitive tier). Sends `watch.setPref` with pref_key + pref_value.
 * Preferences are phone-global, so no watch selector is sent. With exactly one connected watch, the
 * Pebble app refuses keys that watch does not support and waits up to 3 s for its answer
 * (`watch_status`). Option values from the picker are passed back unchanged.
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
    @get:TaskerOutputVariable(PbVars.WATCH_STATUS) @field:TaskerInputField("pb_watch_status") var watchStatus: String? = null,
) : ActionResultOutput()

class SetPrefRunner : WatchCommandRunner<SetPrefInput, SetPrefOutput>() {
    override val command = CommandSender.Type.WATCH_SET_PREF
    override fun watchOf(input: SetPrefInput) = input.serial
    override fun args(input: SetPrefInput) =
        mapOf("pref_key" to Args.required("pref_key", input.prefKey), "pref_value" to input.prefValue.orEmpty())
    override fun newOutput() = SetPrefOutput()
    override fun fill(output: SetPrefOutput, data: Map<String, String>) { output.watchStatus = data["watch_status"] }
    override fun isHardFailure(code: Int): Boolean =
        code != ErrCodes.INVALID_ARGS && super.isHardFailure(code)
}

class SetPrefHelper(config: TaskerPluginConfig<SetPrefInput>) :
    WatchCommandHelper<SetPrefInput, SetPrefOutput, SetPrefRunner>(config) {
    override val inputClass = SetPrefInput::class.java
    override val outputClass = SetPrefOutput::class.java
    override val runnerClass = SetPrefRunner::class.java
    override val defaultBlurb: String = "Pebble: Set Watch Preference"
    override fun blurbFor(input: SetPrefInput): String =
        "Set pref: ${input.prefKey?.takeIf { it.isNotBlank() } ?: "(unset)"}" +
            (input.prefValue?.takeIf { it.isNotBlank() }?.let { " = $it" } ?: "")
}

class SetPrefActivity : FormActionActivity<SetPrefInput, SetPrefOutput, SetPrefRunner, SetPrefHelper>() {
    override val formTitle = "Set Watch Preference"
    override val formDescription = "Writes a watch setting. Settings are shared by all watches paired with the phone. " +
        "With exactly one watch connected, a key it does not support is refused, and %pbl_watch_status reports the watch's answer."
    override val formOutputs = "Outputs: %pbl_watch_status, $RESULT_VARS"
    override fun buildFields() = listOf(FormActionActivity.Field("pref_key", "Preference"), FormActionActivity.Field("pref_value", "Value"))
    override fun onFieldsBuilt(fields: Map<String, TextInputEditText>) {
        PrefPicker(this, fields.getValue("pref_key").layoutOf(), fields.getValue("pref_value").layoutOf(), { null }).attach()
    }
    override fun getNewHelper(config: TaskerPluginConfig<SetPrefInput>) = SetPrefHelper(config)
    // A selector saved by an older version is dropped: the command is global.
    override fun buildInput(values: Map<String, String>) = SetPrefInput(null, values.opt("pref_key"), values["pref_value"].orEmpty())
    override fun extractValues(input: SetPrefInput) = mapOf("pref_key" to input.prefKey.orEmpty(), "pref_value" to input.prefValue.orEmpty())
}
