package com.nickbether.pebbletasker.tasker.event.system

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.bridge.Jobs
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.tasker.action.prefs.layoutOf
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput
import com.nickbether.pebbletasker.tasker.event.FilterMatch
import com.nickbether.pebbletasker.tasker.event.GenericEventConfigActivity
import com.nickbether.pebbletasker.tasker.prefs.PrefPicker
import com.nickbether.pebbletasker.tasker.vars.PbVars

object SystemEventTypes {
    const val PREF = "watch.pref"
    const val FW_AVAILABLE = "fw.available"
    const val JOB_DONE = Jobs.EVENT_TYPE
}

// ─────────────────────────────── Preference Changed ───────────────────────────────

@TaskerInputRoot
class PrefChangedFilter @JvmOverloads constructor(
    @field:TaskerInputField("pref_key") var prefKey: String? = null,
    @field:TaskerInputField("value") var value: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class PrefChangedOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.PREF_KEY) @field:TaskerInputField("pb_pref_key") var key: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_LABEL) @field:TaskerInputField("pb_pref_label") var label: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_VALUE) @field:TaskerInputField("pb_pref_value") var value: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_PREVIOUS) @field:TaskerInputField("pb_pref_previous") var previous: String? = null,
) : BaseEventOutput()

class PrefChangedRunner : PebbleEventRunner<PrefChangedFilter, PrefChangedOutput>() {
    override val eventType = SystemEventTypes.PREF
    override fun evaluate(context: Context, filter: PrefChangedFilter, cached: CachedEvent?, update: PrefChangedOutput?): TaskerPluginResultCondition<PrefChangedOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val key = e.str("pref_key")
        // Keys and option values are exact wire strings.
        if (!filter.prefKey.isNullOrBlank() && filter.prefKey!!.trim() != key) return TaskerPluginResultConditionUnsatisfied()
        if (!filter.value.isNullOrBlank() && filter.value!!.trim() != e.str("value")?.trim()) return TaskerPluginResultConditionUnsatisfied()
        val out = PrefChangedOutput(key, e.str("label"), e.str("value"), e.str("previous"))
            .fillBase<PrefChangedOutput>(e, buildMap {
                key?.let { put("pref_key", it) }
                e.str("label")?.let { put("label", it) }
                e.str("value")?.let { put("value", it) }
                e.str("previous")?.let { put("previous", it) }
            })
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class PrefChangedHelper(config: TaskerPluginConfig<PrefChangedFilter>) : PebbleEventHelper<PrefChangedFilter, PrefChangedOutput, PrefChangedRunner>(config) {
    override val inputClass = PrefChangedFilter::class.java
    override val outputClass = PrefChangedOutput::class.java
    override val runnerClass = PrefChangedRunner::class.java
    override fun addToStringBlurb(input: TaskerInput<PrefChangedFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Preference ").append(input.regular.prefKey?.ifBlank { null } ?: "(any)")
        input.regular.value?.ifBlank { null }?.let { blurbBuilder.append(" becomes ").append(it) }
        blurbBuilder.append("\nOutputs: %pbl_pref_key %pbl_pref_label %pbl_pref_value %pbl_pref_previous + %pbl_json.")
    }
}

class PrefChangedActivity : GenericEventConfigActivity<PrefChangedFilter, PrefChangedOutput, PrefChangedRunner, PrefChangedHelper>() {
    override val titleRes = com.nickbether.pebbletasker.R.string.pb_evt_pref_title
    override val descRes = com.nickbether.pebbletasker.R.string.pb_evt_pref_desc
    override fun buildFields() = listOf(FieldSpec("pref_key", "Preference (blank = any)"), FieldSpec("value", "New value (blank = any)"))
    override fun onConfigCreated(binding: com.nickbether.pebbletasker.databinding.ActivityConfigEventGenericBinding) {
        super.onConfigCreated(binding)
        PrefPicker(this, edits.getValue("pref_key").layoutOf(), edits.getValue("value").layoutOf(), { null }, "blank matches any value").attach()
    }
    override fun getNewHelper(config: TaskerPluginConfig<PrefChangedFilter>) = PrefChangedHelper(config)
    override fun buildInput(values: Map<String, String>) = PrefChangedFilter(values["pref_key"]?.ifBlank { null }, values["value"]?.ifBlank { null })
    override fun extractValues(input: PrefChangedFilter) = mapOf("pref_key" to input.prefKey.orEmpty(), "value" to input.value.orEmpty())
}

// ─────────────────────────────── Firmware Update Available ───────────────────────────────

@TaskerInputRoot
class FirmwareAvailableFilter @JvmOverloads constructor(
    @field:TaskerInputField("serial") var serial: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class FirmwareAvailableOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.FW_VERSION) @field:TaskerInputField("pb_fw_version") var version: String? = null,
    @get:TaskerOutputVariable(PbVars.FW_CURRENT) @field:TaskerInputField("pb_fw_current") var current: String? = null,
    @get:TaskerOutputVariable(PbVars.CAN_DOWNGRADE) @field:TaskerInputField("pb_can_downgrade") var canDowngrade: String? = null,
    @get:TaskerOutputVariable(PbVars.NOTES) @field:TaskerInputField("pb_notes") var notes: String? = null,
) : BaseEventOutput()

class FirmwareAvailableRunner : PebbleEventRunner<FirmwareAvailableFilter, FirmwareAvailableOutput>() {
    override val eventType = SystemEventTypes.FW_AVAILABLE
    override fun evaluate(context: Context, filter: FirmwareAvailableFilter, cached: CachedEvent?, update: FirmwareAvailableOutput?): TaskerPluginResultCondition<FirmwareAvailableOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        if (!com.nickbether.pebbletasker.tasker.event.EventSupport.matchesSerial(filter.serial, e.watch)) return TaskerPluginResultConditionUnsatisfied()
        val out = FirmwareAvailableOutput(e.str("version"), e.str("current"), e.str("can_downgrade"), e.str("notes"))
            .fillBase<FirmwareAvailableOutput>(e)
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class FirmwareAvailableHelper(config: TaskerPluginConfig<FirmwareAvailableFilter>) :
    PebbleEventHelper<FirmwareAvailableFilter, FirmwareAvailableOutput, FirmwareAvailableRunner>(config) {
    override val inputClass = FirmwareAvailableFilter::class.java
    override val outputClass = FirmwareAvailableOutput::class.java
    override val runnerClass = FirmwareAvailableRunner::class.java
    override fun addToStringBlurb(input: TaskerInput<FirmwareAvailableFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires once per watch and offered firmware version.")
            .append("\nOutputs: %pbl_fw_version %pbl_fw_current %pbl_can_downgrade %pbl_notes + %pbl_json.")
    }
}

class FirmwareAvailableActivity : GenericEventConfigActivity<FirmwareAvailableFilter, FirmwareAvailableOutput, FirmwareAvailableRunner, FirmwareAvailableHelper>() {
    override val titleRes = com.nickbether.pebbletasker.R.string.pb_evt_fw_available_title
    override val descRes = com.nickbether.pebbletasker.R.string.pb_evt_fw_available_desc
    override fun buildFields() = listOf(FieldSpec("serial", getString(com.nickbether.pebbletasker.R.string.pb_evt_lbl_serial), lookup = CriteriaDropdown.Source.WATCH_SERIAL))
    override fun getNewHelper(config: TaskerPluginConfig<FirmwareAvailableFilter>) = FirmwareAvailableHelper(config)
    override fun buildInput(values: Map<String, String>) = FirmwareAvailableFilter(values["serial"]?.ifBlank { null })
    override fun extractValues(input: FirmwareAvailableFilter) = mapOf("serial" to input.serial.orEmpty())
}

// ─────────────────────────────── Job Finished ───────────────────────────────

@TaskerInputRoot
class JobDoneFilter @JvmOverloads constructor(
    @field:TaskerInputField("command") var command: String? = null,
    @field:TaskerInputField("status") var status: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class JobDoneOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.JOB_ID) @field:TaskerInputField("pb_job_id") var jobId: String? = null,
    @get:TaskerOutputVariable(PbVars.JOB_COMMAND) @field:TaskerInputField("pb_job_command") var command: String? = null,
    @get:TaskerOutputVariable(PbVars.JOB_STATUS) @field:TaskerInputField("pb_job_status") var status: String? = null,
    @get:TaskerOutputVariable(PbVars.JOB_ERROR) @field:TaskerInputField("pb_job_error") var error: String? = null,
    @get:TaskerOutputVariable(PbVars.URI) @field:TaskerInputField("pb_uri") var uri: String? = null,
    @get:TaskerOutputVariable(PbVars.MIME) @field:TaskerInputField("pb_mime") var mime: String? = null,
    @get:TaskerOutputVariable(PbVars.WIDTH) @field:TaskerInputField("pb_width") var width: String? = null,
    @get:TaskerOutputVariable(PbVars.HEIGHT) @field:TaskerInputField("pb_height") var height: String? = null,
) : BaseEventOutput()

class JobDoneRunner : PebbleEventRunner<JobDoneFilter, JobDoneOutput>() {
    override val eventType = SystemEventTypes.JOB_DONE
    override fun evaluate(context: Context, filter: JobDoneFilter, cached: CachedEvent?, update: JobDoneOutput?): TaskerPluginResultCondition<JobDoneOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        if (!FilterMatch.eq(filter.command, e.str("command")) || !FilterMatch.eq(filter.status, e.str("status")))
            return TaskerPluginResultConditionUnsatisfied()
        val out = JobDoneOutput(e.str("job_id"), e.str("command"), e.str("status"), e.str("error"), e.str("uri"), e.str("mime"), e.str("width"), e.str("height"))
            .fillBase<JobDoneOutput>(e)
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class JobDoneHelper(config: TaskerPluginConfig<JobDoneFilter>) : PebbleEventHelper<JobDoneFilter, JobDoneOutput, JobDoneRunner>(config) {
    override val inputClass = JobDoneFilter::class.java
    override val outputClass = JobDoneOutput::class.java
    override val runnerClass = JobDoneRunner::class.java
    override fun addToStringBlurb(input: TaskerInput<JobDoneFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Screenshot or log job finished").append(input.regular.command?.ifBlank { null }?.let { " ($it)" } ?: "")
            .append("\nOutputs: %pbl_job_id %pbl_job_command %pbl_job_status %pbl_job_error %pbl_uri %pbl_mime %pbl_width %pbl_height.")
    }
}

class JobDoneActivity : GenericEventConfigActivity<JobDoneFilter, JobDoneOutput, JobDoneRunner, JobDoneHelper>() {
    override val titleRes = com.nickbether.pebbletasker.R.string.pb_evt_job_done_title
    override val descRes = com.nickbether.pebbletasker.R.string.pb_evt_job_done_desc
    override fun buildFields() = listOf(
        FieldSpec("command", "Command (blank = any)", options = listOf("Screenshot" to "watch.screenshot", "Log dump" to "watch.gatherLogs")),
        FieldSpec("status", "Status (blank = any)", options = listOf("Succeeded" to "ok", "Failed" to "failed")),
    )
    override fun getNewHelper(config: TaskerPluginConfig<JobDoneFilter>) = JobDoneHelper(config)
    override fun buildInput(values: Map<String, String>) = JobDoneFilter(values["command"]?.ifBlank { null }, values["status"]?.ifBlank { null })
    override fun extractValues(input: JobDoneFilter) = mapOf("command" to input.command.orEmpty(), "status" to input.status.orEmpty())
}
