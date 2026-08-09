package com.nickbether.pebbletasker.tasker.event.timeline

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
 * E9 — Pebble Timeline Action (COLLECTOR). Fires when the user invokes an action on a timeline pin.
 */
@TaskerInputRoot
class TimelineFilter @JvmOverloads constructor(
    @field:TaskerInputField("pin_uuid", labelResIdName = "pb_lbl_pin_uuid")
    var pinUuid: String? = null,
    @field:TaskerInputField("action_id", labelResIdName = "pb_lbl_action_id")
    var actionId: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class TimelineOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.PIN_UUID)
    @field:TaskerInputField("pb_pin_uuid")
    var pbPinUuid: String? = null,
    @get:TaskerOutputVariable(PbVars.ACTION_ID)
    @field:TaskerInputField("pb_action_id")
    var pbActionId: String? = null,
) : BaseEventOutput()

class TimelineRunner : PebbleEventRunner<TimelineFilter, TimelineOutput>() {
    override val eventType: String = EventRouting.TYPE_TIMELINE

    override fun evaluate(
        context: Context,
        filter: TimelineFilter,
        cached: CachedEvent?,
        update: TimelineOutput?,
    ): TaskerPluginResultCondition<TimelineOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val pinUuid = e.str("pin_uuid")
        val actionId = e.str("action_id")
        if (!FilterMatch.eq(filter.pinUuid, pinUuid)) return TaskerPluginResultConditionUnsatisfied()
        if (!FilterMatch.eq(filter.actionId, actionId)) return TaskerPluginResultConditionUnsatisfied()
        val out = TimelineOutput(pbPinUuid = pinUuid, pbActionId = actionId).fillBase<TimelineOutput>(
            e,
            buildMap {
                pinUuid?.let { put("pin_uuid", it) }
                actionId?.let { put("action_id", it) }
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class TimelineHelper(config: TaskerPluginConfig<TimelineFilter>) :
    PebbleEventHelper<TimelineFilter, TimelineOutput, TimelineRunner>(config) {
    override val inputClass = TimelineFilter::class.java
    override val outputClass = TimelineOutput::class.java
    override val runnerClass = TimelineRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<TimelineFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires when a timeline pin action is invoked.")
            .append("\nOutputs: %pbl_pin_uuid %pbl_action_id + %pbl_json.")
    }
}

class TimelineActivity :
    GenericEventConfigActivity<TimelineFilter, TimelineOutput, TimelineRunner, TimelineHelper>() {

    override val titleRes = R.string.pb_evt_timeline_title
    override val descRes = R.string.pb_evt_timeline_desc

    override fun buildFields() = listOf(
        FieldSpec("pin_uuid", getString(R.string.pb_lbl_pin_uuid), lookup = CriteriaDropdown.Source.LOCKER_ANY),
        FieldSpec("action_id", getString(R.string.pb_lbl_action_id)),
    )

    override fun getNewHelper(config: TaskerPluginConfig<TimelineFilter>) = TimelineHelper(config)

    override fun buildInput(values: Map<String, String>) =
        TimelineFilter(pinUuid = values["pin_uuid"], actionId = values["action_id"])

    override fun extractValues(input: TimelineFilter) =
        mapOf("pin_uuid" to input.pinUuid.orEmpty(), "action_id" to input.actionId.orEmpty())
}
