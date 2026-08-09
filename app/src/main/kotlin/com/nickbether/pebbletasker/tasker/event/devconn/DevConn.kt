package com.nickbether.pebbletasker.tasker.event.devconn

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
 * E14 — Pebble Dev Connection (COLLECTOR). Fires when the developer connection state changes
 * (enabled/disabled, transport WS/serial/QEMU). Backing type dev.state.
 *
 * dev_enabled filter: "true"/"false" (blank = any). transport filter: blank = any.
 */
@TaskerInputRoot
class DevConnFilter @JvmOverloads constructor(
    @field:TaskerInputField("dev_enabled", labelResIdName = "pb_evt_lbl_dev_enabled")
    var devEnabled: String? = null,
    @field:TaskerInputField("transport", labelResIdName = "pb_evt_lbl_transport")
    var transport: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class DevConnOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.DEV_ENABLED)
    @field:TaskerInputField("pb_dev_enabled")
    var pbDevEnabled: String? = null,
    @get:TaskerOutputVariable(PbVars.TRANSPORT)
    @field:TaskerInputField("pb_transport")
    var pbTransport: String? = null,
) : BaseEventOutput()

class DevConnRunner : PebbleEventRunner<DevConnFilter, DevConnOutput>() {
    override val eventType: String = EventRouting.TYPE_DEV

    override fun evaluate(
        context: Context,
        filter: DevConnFilter,
        cached: CachedEvent?,
        update: DevConnOutput?,
    ): TaskerPluginResultCondition<DevConnOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val enabled = e.str("dev_enabled") ?: e.bool("enabled")?.toString()
        val transport = e.str("transport")
        if (!FilterMatch.eq(filter.devEnabled, enabled)) return TaskerPluginResultConditionUnsatisfied()
        if (!FilterMatch.eq(filter.transport, transport)) return TaskerPluginResultConditionUnsatisfied()
        val out = DevConnOutput(
            pbDevEnabled = enabled,
            pbTransport = transport,
        ).fillBase<DevConnOutput>(
            e,
            buildMap {
                enabled?.let { put("dev_enabled", it) }
                transport?.let { put("transport", it) }
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class DevConnHelper(config: TaskerPluginConfig<DevConnFilter>) :
    PebbleEventHelper<DevConnFilter, DevConnOutput, DevConnRunner>(config) {
    override val inputClass = DevConnFilter::class.java
    override val outputClass = DevConnOutput::class.java
    override val runnerClass = DevConnRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<DevConnFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires when the developer connection state changes.")
            .append("\nOutputs: %pbl_dev_enabled %pbl_transport + %pbl_json.")
    }
}

class DevConnActivity :
    GenericEventConfigActivity<DevConnFilter, DevConnOutput, DevConnRunner, DevConnHelper>() {

    override val titleRes = R.string.pb_evt_devconn_title
    override val descRes = R.string.pb_evt_devconn_desc

    override fun buildFields() = listOf(
        // Blank already matched anything; the pick-list makes that visible ("Any") instead of leaving
        // the true/false convention to the hint text.
        FieldSpec(
            "dev_enabled",
            getString(R.string.pb_evt_lbl_dev_enabled),
            options = listOf("Enabled" to "true", "Disabled" to "false"),
        ),
        FieldSpec("transport", getString(R.string.pb_evt_lbl_transport)),
    )

    override fun getNewHelper(config: TaskerPluginConfig<DevConnFilter>) = DevConnHelper(config)

    override fun buildInput(values: Map<String, String>) =
        DevConnFilter(devEnabled = values["dev_enabled"], transport = values["transport"])

    override fun extractValues(input: DevConnFilter) =
        mapOf("dev_enabled" to input.devEnabled.orEmpty(), "transport" to input.transport.orEmpty())
}
