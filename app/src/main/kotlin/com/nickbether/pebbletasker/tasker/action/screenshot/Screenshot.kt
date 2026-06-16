package com.nickbether.pebbletasker.tasker.action.screenshot

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
import com.nickbether.pebbletasker.tasker.action.common.GenericFieldsActionActivity
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A10 — Take Screenshot (FINAL DESIGN §2.3, normal tier). Sends `watch.screenshot`; returns the
 * saved file path. `out_path` is an optional preferred output location.
 * Bridge success `data`: file.
 */

@TaskerInputRoot
class ScreenshotInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("out_path", labelResIdName = "lbl_out_path")
    var outPath: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class ScreenshotOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_file")
    @get:TaskerOutputVariable(PbVars.FILE, labelResIdName = "lbl_out_file")
    val file: String? = null,
)

class ScreenshotRunner : PebbleActionRunner<ScreenshotInput, ScreenshotOutput>() {
    override fun execute(context: Context, input: TaskerInput<ScreenshotInput>): BridgeResult<Map<String, String>> {
        val args = HashMap<String, String>()
        input.regular.outPath?.trim()?.takeIf { it.isNotEmpty() }?.let { args["out_path"] = it }
        return ActionSend.send(context, CommandSender.Type.WATCH_SCREENSHOT, watch = input.regular.serial, args = args)
    }

    override fun buildOutput(input: TaskerInput<ScreenshotInput>, result: CommandResult): ScreenshotOutput =
        ScreenshotOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            file = ActionOutputs.data(result, "file"),
        )
}

class ScreenshotHelper(config: TaskerPluginConfig<ScreenshotInput>) :
    ActionHelper<ScreenshotInput, ScreenshotOutput, ScreenshotRunner>(config) {
    override val inputClass = ScreenshotInput::class.java
    override val outputClass = ScreenshotOutput::class.java
    override val runnerClass = ScreenshotRunner::class.java
    override val defaultBlurb: String = "Pebble: Take Screenshot"
    override fun blurbFor(input: ScreenshotInput): String {
        val s = input.serial?.takeIf { it.isNotBlank() }
        return if (s == null) "Screenshot: active watch" else "Screenshot: $s"
    }
}

class ScreenshotActivity :
    GenericFieldsActionActivity<ScreenshotInput, ScreenshotOutput, ScreenshotRunner, ScreenshotHelper>() {
    override val titleRes = R.string.act_screenshot_title
    override val descRes = R.string.act_screenshot_desc
    override val fields = listOf(
        FieldSpec(R.string.lbl_serial, isSerial = true),
        FieldSpec(R.string.lbl_out_path),
    )
    override fun getNewHelper(config: TaskerPluginConfig<ScreenshotInput>) = ScreenshotHelper(config)
    override fun makeInput(values: List<String?>) =
        ScreenshotInput(serial = values.getOrNull(0), outPath = values.getOrNull(1))
    override fun valuesOf(input: ScreenshotInput) = listOf(input.serial, input.outPath)
}
