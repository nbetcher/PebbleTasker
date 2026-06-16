package com.nickbether.pebbletasker.tasker.event.health

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.event.GenericEventConfigActivity
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * E13 — Pebble Health Updated (COLLECTOR). No filter — fires whenever health metrics refresh.
 * Backing type health.updated. Capability-gated until the bridge emits it.
 */
@TaskerInputRoot
class HealthFilter @JvmOverloads constructor(
    // No user-configurable filter; an empty root is valid and the library still instantiates it.
    @field:TaskerInputField("unused", labelResIdName = "pb_lbl_unused")
    var unused: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class HealthOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.STEPS_TODAY)
    @field:TaskerInputField("pb_steps_today")
    var pbStepsToday: String? = null,
    @get:TaskerOutputVariable(PbVars.LATEST_HR)
    @field:TaskerInputField("pb_latest_hr")
    var pbLatestHr: String? = null,
) : BaseEventOutput()

class HealthRunner : PebbleEventRunner<HealthFilter, HealthOutput>() {
    override val eventType: String = EventRouting.TYPE_HEALTH

    override fun evaluate(
        context: Context,
        filter: HealthFilter,
        cached: CachedEvent?,
        update: HealthOutput?,
    ): TaskerPluginResultCondition<HealthOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val out = HealthOutput(
            pbStepsToday = e.str("steps_today"),
            pbLatestHr = e.str("latest_hr"),
        ).fillBase<HealthOutput>(
            e,
            buildMap {
                e.str("steps_today")?.let { put("steps_today", it) }
                e.str("latest_hr")?.let { put("latest_hr", it) }
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class HealthHelper(config: TaskerPluginConfig<HealthFilter>) :
    PebbleEventHelper<HealthFilter, HealthOutput, HealthRunner>(config) {
    override val inputClass = HealthFilter::class.java
    override val outputClass = HealthOutput::class.java
    override val runnerClass = HealthRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<HealthFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires when watch health metrics update.")
            .append("\nOutputs: %pb_steps_today %pb_latest_hr + %pb_json.")
    }
}

class HealthActivity :
    GenericEventConfigActivity<HealthFilter, HealthOutput, HealthRunner, HealthHelper>() {

    override val titleRes = R.string.pb_evt_health_title
    override val descRes = R.string.pb_evt_health_desc

    // No user-configurable fields.
    override fun buildFields() = emptyList<FieldSpec>()

    override fun getNewHelper(config: TaskerPluginConfig<HealthFilter>) = HealthHelper(config)

    override fun buildInput(values: Map<String, String>) = HealthFilter()

    override fun extractValues(input: HealthFilter) = emptyMap<String, String>()
}
