package com.nickbether.pebbletasker.tasker.action.screenshot

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.bridge.CommandSender
import com.nickbether.pebbletasker.bridge.JobFiles
import com.nickbether.pebbletasker.tasker.action.common.FormActionActivity
import com.nickbether.pebbletasker.tasker.action.common.JobActionHelper
import com.nickbether.pebbletasker.tasker.action.common.JobActionOutput
import com.nickbether.pebbletasker.tasker.action.common.JobActionRunner
import com.nickbether.pebbletasker.tasker.action.common.JobInput
import com.nickbether.pebbletasker.tasker.action.watchctl.RESULT_VARS
import com.nickbether.pebbletasker.tasker.action.watchctl.SERIAL_FIELD
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A10 — Take Screenshot (sensitive tier). `watch.screenshot` returns a job_id; the image arrives as a
 * `system`/`job.done` event and is copied to Pictures/<folder>. `out_path` (kept from earlier configs)
 * is now the folder name.
 */
@TaskerInputRoot
class ScreenshotInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    override var serial: String? = null,
    @field:TaskerInputField("out_path", labelResIdName = "lbl_out_path")
    var outPath: String? = null,
    @field:TaskerInputField("timeout_s")
    override var timeoutS: String? = null,
    @field:TaskerInputField("save")
    override var save: String? = null,
) : JobInput {
    override val folder: String? get() = outPath
}

@TaskerInputRoot
@TaskerOutputObject()
class ScreenshotOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.WIDTH) @field:TaskerInputField("pb_width") var width: String? = null,
    @get:TaskerOutputVariable(PbVars.HEIGHT) @field:TaskerInputField("pb_height") var height: String? = null,
) : JobActionOutput()

class ScreenshotRunner : JobActionRunner<ScreenshotInput, ScreenshotOutput>() {
    override val command = CommandSender.Type.WATCH_SCREENSHOT
    override val kind = JobFiles.Kind.SCREENSHOT
    override val defaultTimeoutS = 60
    override fun newOutput() = ScreenshotOutput()
    override fun fill(output: ScreenshotOutput, data: Map<String, String>) { output.width = data["width"]; output.height = data["height"] }
}

class ScreenshotHelper(config: TaskerPluginConfig<ScreenshotInput>) :
    JobActionHelper<ScreenshotInput, ScreenshotOutput, ScreenshotRunner>(config, 60) {
    override val inputClass = ScreenshotInput::class.java
    override val outputClass = ScreenshotOutput::class.java
    override val runnerClass = ScreenshotRunner::class.java
    override val defaultBlurb: String = "Pebble: Take Screenshot"
    override fun blurbFor(input: ScreenshotInput) = withWatch("Screenshot", input.serial)
}

class ScreenshotActivity : FormActionActivity<ScreenshotInput, ScreenshotOutput, ScreenshotRunner, ScreenshotHelper>() {
    override val formTitle = "Take Screenshot"
    override val formDescription = "Captures the watch screen and saves it as an image in Pictures/<folder>. " +
        "This plugin needs the system event category (CATEGORY_DISABLED otherwise): the image arrives as a system event. " +
        "The Pebble app keeps its copy for one hour; this action copies it straight away."
    override val formOutputs = "Outputs: %pbl_file (saved path), %pbl_uri, %pbl_width, %pbl_height, %pbl_mime, %pbl_job_id, $RESULT_VARS"
    override fun buildFields() = listOf(
        SERIAL_FIELD,
        FormActionActivity.Field("out_path", "Folder under Pictures/ (default PebbleTasker)"),
        FormActionActivity.Field("timeout_s", "Wait for the image, seconds (default 60)", numeric = true),
        FormActionActivity.Field("save", "Save a copy (default true)", options = listOf("Save to Pictures" to "true", "Don't save; output the Pebble app's URI" to "false"),
            helper = "The Pebble app's URI is readable only by this plugin and expires after an hour."),
    )
    override fun getNewHelper(config: TaskerPluginConfig<ScreenshotInput>) = ScreenshotHelper(config)
    override fun buildInput(values: Map<String, String>) =
        ScreenshotInput(values.opt("serial"), values.opt("out_path"), values.opt("timeout_s"), values.opt("save"))
    override fun extractValues(input: ScreenshotInput) = mapOf(
        "serial" to input.serial.orEmpty(), "out_path" to input.outPath.orEmpty(),
        "timeout_s" to input.timeoutS.orEmpty(), "save" to input.save.orEmpty(),
    )
}
