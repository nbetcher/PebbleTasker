package com.nickbether.pebbletasker.tasker.event.appchanged

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
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.event.FilterMatch
import com.nickbether.pebbletasker.tasker.event.GenericEventConfigActivity
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * E7 — Pebble Watch App Changed (COLLECTOR). Backing type apps.run_state — the foreground app/
 * watchface on the watch changed. Capability-gated until the bridge emits it.
 */
@TaskerInputRoot
class AppChangedFilter @JvmOverloads constructor(
    @field:TaskerInputField("uuid", labelResIdName = "pb_evt_lbl_uuid")
    var uuid: String? = null,
    @field:TaskerInputField("app_type", labelResIdName = "pb_lbl_app_type")
    var appType: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class AppChangedOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.UUID)
    @field:TaskerInputField("pb_uuid")
    var pbUuid: String? = null,
    @get:TaskerOutputVariable(PbVars.APP_NAME)
    @field:TaskerInputField("pb_app_name")
    var pbAppName: String? = null,
    @get:TaskerOutputVariable(PbVars.APP_TYPE)
    @field:TaskerInputField("pb_app_type")
    var pbAppType: String? = null,
    @get:TaskerOutputVariable(PbVars.PREV_UUID)
    @field:TaskerInputField("pb_prev_uuid")
    var pbPrevUuid: String? = null,
) : BaseEventOutput()

class AppChangedRunner : PebbleEventRunner<AppChangedFilter, AppChangedOutput>() {
    override val eventType: String = EventRouting.TYPE_APP_CHANGED

    override fun evaluate(
        context: Context,
        filter: AppChangedFilter,
        cached: CachedEvent?,
        update: AppChangedOutput?,
    ): TaskerPluginResultCondition<AppChangedOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val uuid = e.str("uuid")
        val appType = e.str("app_type")
        if (!FilterMatch.eq(filter.uuid, uuid)) return TaskerPluginResultConditionUnsatisfied()
        if (!FilterMatch.eq(filter.appType, appType)) return TaskerPluginResultConditionUnsatisfied()
        val out = AppChangedOutput(
            pbUuid = uuid,
            pbAppName = e.str("app_name"),
            pbAppType = appType,
            pbPrevUuid = e.str("prev_uuid"),
        ).fillBase<AppChangedOutput>(
            e,
            buildMap {
                uuid?.let { put("uuid", it) }
                e.str("app_name")?.let { put("app_name", it) }
                appType?.let { put("app_type", it) }
                e.str("prev_uuid")?.let { put("prev_uuid", it) }
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class AppChangedHelper(config: TaskerPluginConfig<AppChangedFilter>) :
    PebbleEventHelper<AppChangedFilter, AppChangedOutput, AppChangedRunner>(config) {
    override val inputClass = AppChangedFilter::class.java
    override val outputClass = AppChangedOutput::class.java
    override val runnerClass = AppChangedRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<AppChangedFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires when the foreground watch app/watchface changes.")
            .append("\nOutputs: %pbl_uuid %pbl_app_name %pbl_app_type %pbl_prev_uuid + %pbl_json.")
    }
}

class AppChangedActivity :
    GenericEventConfigActivity<AppChangedFilter, AppChangedOutput, AppChangedRunner, AppChangedHelper>() {

    override val titleRes = R.string.pb_evt_appchanged_title
    override val descRes = R.string.pb_evt_appchanged_desc

    override fun buildFields() = listOf(
        FieldSpec("uuid", getString(R.string.pb_evt_lbl_uuid), lookup = CriteriaDropdown.Source.LOCKER_ANY),
        FieldSpec("app_type", getString(R.string.pb_lbl_app_type)),
    )

    override fun getNewHelper(config: TaskerPluginConfig<AppChangedFilter>) = AppChangedHelper(config)

    override fun buildInput(values: Map<String, String>) =
        AppChangedFilter(uuid = values["uuid"], appType = values["app_type"])

    override fun extractValues(input: AppChangedFilter) =
        mapOf("uuid" to input.uuid.orEmpty(), "app_type" to input.appType.orEmpty())
}
