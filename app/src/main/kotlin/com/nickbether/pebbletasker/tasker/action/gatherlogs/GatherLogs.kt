package com.nickbether.pebbletasker.tasker.action.gatherlogs

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.nickbether.pebbletasker.bridge.CommandSender
import com.nickbether.pebbletasker.bridge.JobFiles
import com.nickbether.pebbletasker.tasker.action.common.FormActionActivity
import com.nickbether.pebbletasker.tasker.action.common.JobActionHelper
import com.nickbether.pebbletasker.tasker.action.common.JobActionOutput
import com.nickbether.pebbletasker.tasker.action.common.JobActionRunner
import com.nickbether.pebbletasker.tasker.action.common.JobInput
import com.nickbether.pebbletasker.tasker.action.watchctl.RESULT_VARS
import com.nickbether.pebbletasker.tasker.action.watchctl.SERIAL_FIELD

/** Gather Logs (extremely dangerous tier): dumps the watch logs to Download/<folder>. */
@TaskerInputRoot
class GatherLogsInput @JvmOverloads constructor(
    @field:TaskerInputField("serial") override var serial: String? = null,
    @field:TaskerInputField("folder") override var folder: String? = null,
    @field:TaskerInputField("timeout_s") override var timeoutS: String? = null,
    @field:TaskerInputField("save") override var save: String? = null,
) : JobInput

@TaskerInputRoot
@TaskerOutputObject
class GatherLogsOutput @JvmOverloads constructor() : JobActionOutput()

class GatherLogsRunner : JobActionRunner<GatherLogsInput, GatherLogsOutput>() {
    override val command = CommandSender.Type.WATCH_GATHER_LOGS
    override val kind = JobFiles.Kind.LOGS
    override val defaultTimeoutS = 600
    override fun newOutput() = GatherLogsOutput()
}

class GatherLogsHelper(config: TaskerPluginConfig<GatherLogsInput>) :
    JobActionHelper<GatherLogsInput, GatherLogsOutput, GatherLogsRunner>(config, 600) {
    override val inputClass = GatherLogsInput::class.java
    override val outputClass = GatherLogsOutput::class.java
    override val runnerClass = GatherLogsRunner::class.java
    override val defaultBlurb = "Pebble: Gather Watch Logs"
    override fun blurbFor(input: GatherLogsInput) = withWatch("Gather logs", input.serial)
}

class GatherLogsActivity : FormActionActivity<GatherLogsInput, GatherLogsOutput, GatherLogsRunner, GatherLogsHelper>() {
    override val formTitle = "Gather Watch Logs"
    override val formDescription = "Dumps the watch's logs to a text file in Download/<folder>. This can take several minutes. " +
        "Needs the extremely dangerous tier, which you approve in the Pebble app (a one-time warning per plugin; its Accept button " +
        "unlocks after 10 seconds), and the system event category (CATEGORY_DISABLED otherwise). WATCH_BUSY means a dump is already running."
    override val formWarning = "Watch logs can contain personal information such as notification text, app names and locations. Share them with care."
    override val formOutputs = "Outputs: %pbl_file (saved path), %pbl_uri, %pbl_mime, %pbl_job_id, $RESULT_VARS"
    override fun buildFields() = listOf(
        SERIAL_FIELD,
        FormActionActivity.Field("folder", "Folder under Download/ (default PebbleTasker)"),
        FormActionActivity.Field("timeout_s", "Wait for the dump, seconds (default 600)", numeric = true),
        FormActionActivity.Field("save", "Save a copy (default true)", options = listOf("Save to Download" to "true", "Don't save; output the Pebble app's URI" to "false"),
            helper = "The Pebble app's URI is readable only by this plugin and expires after an hour."),
    )
    override fun getNewHelper(config: TaskerPluginConfig<GatherLogsInput>) = GatherLogsHelper(config)
    override fun buildInput(values: Map<String, String>) =
        GatherLogsInput(values.opt("serial"), values.opt("folder"), values.opt("timeout_s"), values.opt("save"))
    override fun extractValues(input: GatherLogsInput) = mapOf(
        "serial" to input.serial.orEmpty(), "folder" to input.folder.orEmpty(),
        "timeout_s" to input.timeoutS.orEmpty(), "save" to input.save.orEmpty(),
    )
}
