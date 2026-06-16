package com.nickbether.pebbletasker.tasker.action.quicklaunch

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
 * A5 — Set Quick Launch Button (FINAL DESIGN §2.3, normal tier).
 * Sends `watch.setQuickLaunch` with button (up/down) + press (short/long) + uuid.
 * The button/press fields stay plain %var-capable text inputs (accept up/down, short/long, or a %var).
 */

@TaskerInputRoot
class QuickLaunchInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("button", labelResIdName = "lbl_button")
    var button: String? = null,
    @field:TaskerInputField("press", labelResIdName = "lbl_press")
    var press: String? = null,
    @field:TaskerInputField("uuid", labelResIdName = "lbl_uuid")
    var uuid: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class QuickLaunchOutput @JvmOverloads constructor(
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

class QuickLaunchRunner : PebbleActionRunner<QuickLaunchInput, QuickLaunchOutput>() {
    override fun execute(context: Context, input: TaskerInput<QuickLaunchInput>): BridgeResult<Map<String, String>> {
        val r = input.regular
        val button = r.button?.trim().orEmpty()
        if (button.isEmpty()) return BridgeResult.err(ErrCodes.INVALID_ARGS, "button is required")
        val args = HashMap<String, String>()
        args["button"] = button
        r.press?.trim()?.takeIf { it.isNotEmpty() }?.let { args["press"] = it }
        r.uuid?.trim()?.takeIf { it.isNotEmpty() }?.let { args["uuid"] = it }
        return ActionSend.send(context, CommandSender.Type.WATCH_SET_QUICK_LAUNCH, watch = r.serial, args = args)
    }

    override fun buildOutput(input: TaskerInput<QuickLaunchInput>, result: CommandResult): QuickLaunchOutput =
        QuickLaunchOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
        )

    override fun isHardFailure(code: Int): Boolean =
        code != ErrCodes.INVALID_ARGS && super.isHardFailure(code)
}

class QuickLaunchHelper(config: TaskerPluginConfig<QuickLaunchInput>) :
    ActionHelper<QuickLaunchInput, QuickLaunchOutput, QuickLaunchRunner>(config) {
    override val inputClass = QuickLaunchInput::class.java
    override val outputClass = QuickLaunchOutput::class.java
    override val runnerClass = QuickLaunchRunner::class.java
    override val defaultBlurb: String = "Pebble: Set Quick Launch Button"
    override fun blurbFor(input: QuickLaunchInput): String {
        val btn = input.button?.takeIf { it.isNotBlank() } ?: "?"
        val press = input.press?.takeIf { it.isNotBlank() } ?: "short"
        return "Quick launch: $btn/$press -> ${input.uuid?.takeIf { it.isNotBlank() } ?: "(unset)"}"
    }
}

class QuickLaunchActivity :
    GenericFieldsActionActivity<QuickLaunchInput, QuickLaunchOutput, QuickLaunchRunner, QuickLaunchHelper>() {
    override val titleRes = R.string.act_quick_launch_title
    override val descRes = R.string.act_quick_launch_desc
    override val hintRes = R.string.hint_uuid_required
    override val fields = listOf(
        FieldSpec(R.string.lbl_serial, isSerial = true),
        FieldSpec(R.string.lbl_button),
        FieldSpec(R.string.lbl_press),
        FieldSpec(R.string.lbl_uuid),
    )
    override fun getNewHelper(config: TaskerPluginConfig<QuickLaunchInput>) = QuickLaunchHelper(config)
    override fun makeInput(values: List<String?>) = QuickLaunchInput(
        serial = values.getOrNull(0),
        button = values.getOrNull(1),
        press = values.getOrNull(2),
        uuid = values.getOrNull(3),
    )
    override fun valuesOf(input: QuickLaunchInput) =
        listOf(input.serial, input.button, input.press, input.uuid)
}
