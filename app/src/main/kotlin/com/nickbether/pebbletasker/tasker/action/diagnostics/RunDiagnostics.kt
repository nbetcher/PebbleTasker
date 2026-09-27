package com.nickbether.pebbletasker.tasker.action.diagnostics

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.nickbether.pebbletasker.diagnostics.DiagReport
import com.nickbether.pebbletasker.diagnostics.DiagnosticsEngine
import com.nickbether.pebbletasker.diagnostics.DiagnosticsRunner
import com.nickbether.pebbletasker.diagnostics.Outcome
import com.nickbether.pebbletasker.log.PLog
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.action.common.Args
import com.nickbether.pebbletasker.tasker.action.common.FormActionActivity
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.nickbether.pebbletasker.tasker.action.common.orNullIfBlank
import com.nickbether.pebbletasker.tasker.action.watchctl.SERIAL_FIELD
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.vars.PbVars
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Run Watch Diagnostics from a Tasker task. This is separate from the launcher screen: it keeps no
 * report there and shows no UI. Any failed check (or a run that cannot start) ends the action with a
 * Tasker error whose %errmsg names the failed checks, so the step needs "Continue Task After Error" to
 * go on. On success the counts, summary and full report are returned as variables.
 */
@TaskerInputRoot
class RunDiagnosticsInput @JvmOverloads constructor(
    @field:TaskerInputField("serial") var serial: String? = null,
    @field:TaskerInputField("include_reboot") var includeReboot: String? = null,
    @field:TaskerInputField("app_uuid") var appUuid: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class RunDiagnosticsOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.DIAG_PASS) @field:TaskerInputField("pb_diag_pass") var pass: String? = null,
    @get:TaskerOutputVariable(PbVars.DIAG_FAIL) @field:TaskerInputField("pb_diag_fail") var fail: String? = null,
    @get:TaskerOutputVariable(PbVars.DIAG_DEFERRED) @field:TaskerInputField("pb_diag_deferred") var deferred: String? = null,
    @get:TaskerOutputVariable(PbVars.DIAG_SUMMARY) @field:TaskerInputField("pb_diag_summary") var summary: String? = null,
    @get:TaskerOutputVariable(PbVars.DIAG_REPORT) @field:TaskerInputField("pb_diag_report") var report: String? = null,
    @get:TaskerOutputVariable(PbVars.DIAG_CHECKS) @field:TaskerInputField("pb_diag_checks") var checks: Array<String>? = null,
    @get:TaskerOutputVariable(PbVars.JSON) @field:TaskerInputField("pb_json") var json: String? = null,
)

object DiagnosticsOutputs {
    fun from(report: DiagReport) = RunDiagnosticsOutput(
        pass = report.count(Outcome.PASS).toString(),
        fail = report.count(Outcome.FAIL).toString(),
        deferred = report.count(Outcome.DEFERRED).toString(),
        summary = report.summaryLine(),
        report = report.toText(),
        checks = report.checks.map { "${it.id.number}|${it.outcome.name}|${it.detail}" }.toTypedArray(),
        json = JSONObject().apply {
            put("summary", report.summaryLine())
            put("model", report.context.model); put("fw", report.context.firmware); put("serial", report.context.serial)
            put("checks", JSONArray().apply {
                report.checks.forEach { c -> put(JSONObject().put("id", c.id.number).put("name", c.id.title).put("outcome", c.outcome.name).put("detail", c.detail)) }
            })
        }.toString(),
    )
}

class RunDiagnosticsRunner : TaskerPluginRunnerAction<RunDiagnosticsInput, RunDiagnosticsOutput>() {
    override fun run(context: Context, input: TaskerInput<RunDiagnosticsInput>): TaskerPluginResult<RunDiagnosticsOutput> {
        val reboot = try { Args.bool(input.regular.includeReboot, false) } catch (e: Args.Invalid) {
            @Suppress("UNCHECKED_CAST")
            return TaskerPluginResultError(ErrCodes.INVALID_ARGS, "include_reboot: ${e.message}") as TaskerPluginResult<RunDiagnosticsOutput>
        }
        val options = DiagnosticsEngine.Options(
            serial = input.regular.serial.orNullIfBlank(),
            includeReboot = reboot,
            appUuid = input.regular.appUuid.orNullIfBlank(),
            pluginVersion = DiagnosticsRunner.pluginVersion,
        )
        val report = runBlocking { DiagnosticsRunner.runHeadless(context, options) }
        PLog.i { "action[RunDiagnostics]: ${report.summaryLine()}" }
        @Suppress("UNCHECKED_CAST")
        return if (report.isFailure) TaskerPluginResultError(ErrCodes.DIAGNOSTICS_FAILED, report.errorMessage()) as TaskerPluginResult<RunDiagnosticsOutput>
        else TaskerPluginResultSucess(DiagnosticsOutputs.from(report))
    }
}

class RunDiagnosticsHelper(config: TaskerPluginConfig<RunDiagnosticsInput>) :
    TaskerPluginConfigHelper<RunDiagnosticsInput, RunDiagnosticsOutput, RunDiagnosticsRunner>(config) {
    override val inputClass = RunDiagnosticsInput::class.java
    override val outputClass = RunDiagnosticsOutput::class.java
    override val runnerClass = RunDiagnosticsRunner::class.java
    // Log dump alone may take 10 minutes, reboot up to 4.
    override val timeoutSeconds: Int = 1_800
    override fun addToStringBlurb(input: TaskerInput<RunDiagnosticsInput>, blurbBuilder: StringBuilder) {
        val i = input.regular
        blurbBuilder.append("Watch diagnostics")
        if (i.includeReboot.orNullIfBlank()?.lowercase() == "true") blurbBuilder.append(" + reboot")
        blurbBuilder.append(": ").append(i.serial.orNullIfBlank() ?: "connected watch")
    }
}

class RunDiagnosticsActivity : FormActionActivity<RunDiagnosticsInput, RunDiagnosticsOutput, RunDiagnosticsRunner, RunDiagnosticsHelper>() {
    override val formTitle = "Run Watch Diagnostics"
    override val formDescription = "Runs the eleven on-watch checks through the Pebble app and returns the results. " +
        "If any check FAILS, or the run cannot start, the action ends with a Tasker error: %err is ${ErrCodes.DIAGNOSTICS_FAILED} " +
        "and %errmsg names each failed check. Turn on \"Continue Task After Error\" on this step to handle it. DEFERRED checks " +
        "(the watch cannot run them, or they need a manual check) do not cause an error. Can take up to 20 minutes."
    override val formWarning = "The checks press buttons on the watch, briefly toggle Quiet Time, and launch and close an app. " +
        "The reboot check runs only if switched on. Factory reset is never run. Needs the dangerous tier and \"Allow dangerous commands\"."
    override val formOutputs = "Outputs on success: %pbl_diag_pass, %pbl_diag_fail, %pbl_diag_deferred, %pbl_diag_summary, " +
        "%pbl_diag_report (full text), %pbl_diag_checks() (\"number|OUTCOME|detail\"), %pbl_json."
    override fun buildFields() = listOf(
        SERIAL_FIELD.copy(label = "Watch serial (blank = the only connected watch)"),
        FormActionActivity.Field("include_reboot", "Include the reboot check (default false)",
            options = listOf("Reboot the watch as part of the checks" to "true", "Skip the reboot check" to "false")),
        FormActionActivity.Field("app_uuid", "App to launch (optional; blank = from the locker)", lookup = CriteriaDropdown.Source.LOCKER_APP),
    )
    override fun getNewHelper(config: TaskerPluginConfig<RunDiagnosticsInput>) = RunDiagnosticsHelper(config)
    override fun buildInput(values: Map<String, String>) =
        RunDiagnosticsInput(values.opt("serial"), values.opt("include_reboot"), values.opt("app_uuid"))
    override fun extractValues(input: RunDiagnosticsInput) = mapOf(
        "serial" to input.serial.orEmpty(), "include_reboot" to input.includeReboot.orEmpty(), "app_uuid" to input.appUuid.orEmpty(),
    )
}
