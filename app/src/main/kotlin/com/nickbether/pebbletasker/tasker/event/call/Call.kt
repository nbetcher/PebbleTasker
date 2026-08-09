package com.nickbether.pebbletasker.tasker.event.call

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
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.event.FilterMatch
import com.nickbether.pebbletasker.tasker.event.GenericEventConfigActivity
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * E11 — Pebble Call Event (COLLECTOR). Fires on a phone-call state change relayed to/from the watch
 * (ringing/answered/ended) and any watch-side handling (answer/decline). Backing type calls.state.
 */
@TaskerInputRoot
class CallFilter @JvmOverloads constructor(
    @field:TaskerInputField("call_state", labelResIdName = "pb_lbl_call_state")
    var callState: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class CallOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.CALL_STATE)
    @field:TaskerInputField("pb_call_state")
    var pbCallState: String? = null,
    @get:TaskerOutputVariable(PbVars.CALL_ACTION)
    @field:TaskerInputField("pb_call_action")
    var pbCallAction: String? = null,
    @get:TaskerOutputVariable(PbVars.NUMBER)
    @field:TaskerInputField("pb_number")
    var pbNumber: String? = null,
    @get:TaskerOutputVariable(PbVars.CALLER_NAME)
    @field:TaskerInputField("pb_caller_name")
    var pbCallerName: String? = null,
    @get:TaskerOutputVariable(PbVars.HANDLED_ON_WATCH)
    @field:TaskerInputField("pb_handled_on_watch")
    var pbHandledOnWatch: String? = null,
) : BaseEventOutput()

class CallRunner : PebbleEventRunner<CallFilter, CallOutput>() {
    override val eventType: String = EventRouting.TYPE_CALL

    override fun evaluate(
        context: Context,
        filter: CallFilter,
        cached: CachedEvent?,
        update: CallOutput?,
    ): TaskerPluginResultCondition<CallOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val state = e.str("call_state") ?: e.str("state")
        if (!FilterMatch.eq(filter.callState, state)) return TaskerPluginResultConditionUnsatisfied()
        val out = CallOutput(
            pbCallState = state,
            pbCallAction = e.str("call_action"),
            pbNumber = e.str("number"),
            pbCallerName = e.str("caller_name"),
            pbHandledOnWatch = e.str("handled_on_watch"),
        ).fillBase<CallOutput>(
            e,
            buildMap {
                state?.let { put("call_state", it) }
                e.str("call_action")?.let { put("call_action", it) }
                e.str("number")?.let { put("number", it) }
                e.str("caller_name")?.let { put("caller_name", it) }
                e.str("handled_on_watch")?.let { put("handled_on_watch", it) }
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class CallHelper(config: TaskerPluginConfig<CallFilter>) :
    PebbleEventHelper<CallFilter, CallOutput, CallRunner>(config) {
    override val inputClass = CallFilter::class.java
    override val outputClass = CallOutput::class.java
    override val runnerClass = CallRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<CallFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires on a call state change involving the watch.")
            .append("\nOutputs: %pbl_call_state %pbl_call_action %pbl_number %pbl_caller_name")
            .append(" %pbl_handled_on_watch + %pbl_json.")
    }
}

class CallActivity :
    GenericEventConfigActivity<CallFilter, CallOutput, CallRunner, CallHelper>() {

    override val titleRes = R.string.pb_evt_call_title
    override val descRes = R.string.pb_evt_call_desc

    override fun buildFields() = listOf(
        // Values are the bridge's Call subclass simpleNames (calls.state -> "state") plus "Ended";
        // labels are friendly. "Any" (cleared field) matches every state.
        FieldSpec(
            "call_state",
            getString(R.string.pb_lbl_call_state),
            options = listOf(
                "Ringing" to "RingingCall",
                "Dialing" to "DialingCall",
                "Active" to "ActiveCall",
                "Holding" to "HoldingCall",
                "Ended" to "Ended",
            ),
        ),
    )

    override fun getNewHelper(config: TaskerPluginConfig<CallFilter>) = CallHelper(config)

    override fun buildInput(values: Map<String, String>) =
        CallFilter(callState = values["call_state"])

    override fun extractValues(input: CallFilter) =
        mapOf("call_state" to input.callState.orEmpty())
}
