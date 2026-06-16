package com.nickbether.pebbletasker.tasker.event.connfailed

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
import com.nickbether.pebbletasker.tasker.event.EventSupport
import com.nickbether.pebbletasker.tasker.event.FilterMatch
import com.nickbether.pebbletasker.tasker.event.GenericEventConfigActivity
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * E3 — Pebble Connection Failed (COLLECTOR). Backing bridge type: watch.state (a failed/errored
 * connection transition carrying a `reason`). Capability-gated until the bridge emits it.
 */
@TaskerInputRoot
class ConnFailedFilter @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "pb_evt_lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("reason", labelResIdName = "pb_lbl_reason")
    var reason: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class ConnFailedOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.REASON)
    @field:TaskerInputField("pb_reason")
    var pbReason: String? = null,
) : BaseEventOutput()

class ConnFailedRunner : PebbleEventRunner<ConnFailedFilter, ConnFailedOutput>() {
    override val eventType: String = EventRouting.TYPE_CONN_FAILED

    override fun evaluate(
        context: Context,
        filter: ConnFailedFilter,
        cached: CachedEvent?,
        update: ConnFailedOutput?,
    ): TaskerPluginResultCondition<ConnFailedOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        // watch.state is broad; only treat it as a "failure" when it carries an error reason.
        val reason = e.str("reason")
        if (reason.isNullOrBlank()) return TaskerPluginResultConditionUnsatisfied()
        if (!EventSupport.matchesSerial(filter.serial, e.watch)) {
            return TaskerPluginResultConditionUnsatisfied()
        }
        if (!FilterMatch.contains(filter.reason, reason)) {
            return TaskerPluginResultConditionUnsatisfied()
        }
        val out = ConnFailedOutput(pbReason = reason).fillBase<ConnFailedOutput>(e, mapOf("reason" to reason))
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class ConnFailedHelper(config: TaskerPluginConfig<ConnFailedFilter>) :
    PebbleEventHelper<ConnFailedFilter, ConnFailedOutput, ConnFailedRunner>(config) {
    override val inputClass = ConnFailedFilter::class.java
    override val outputClass = ConnFailedOutput::class.java
    override val runnerClass = ConnFailedRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<ConnFailedFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires when a watch connection fails.")
            .append("\nOutputs: %pb_reason + identity + %pb_json.")
    }
}

class ConnFailedActivity :
    GenericEventConfigActivity<ConnFailedFilter, ConnFailedOutput, ConnFailedRunner, ConnFailedHelper>() {

    override val titleRes = R.string.pb_evt_connfailed_title
    override val descRes = R.string.pb_evt_connfailed_desc

    override fun buildFields() = listOf(
        FieldSpec("serial", getString(R.string.pb_evt_lbl_serial), isWatchSerial = true),
        FieldSpec("reason", getString(R.string.pb_lbl_reason)),
    )

    override fun getNewHelper(config: TaskerPluginConfig<ConnFailedFilter>) = ConnFailedHelper(config)

    override fun buildInput(values: Map<String, String>) =
        ConnFailedFilter(serial = values["serial"], reason = values["reason"])

    override fun extractValues(input: ConnFailedFilter) =
        mapOf("serial" to input.serial.orEmpty(), "reason" to input.reason.orEmpty())
}
