package com.nickbether.pebbletasker.tasker.event.disconnected

import android.content.Context
import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.databinding.ActivityConfigSerialOnlyBinding
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.event.EventSupport
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown

/**
 * E2 — Pebble Watch Disconnected (READY today).
 *
 * IMPORTANT (FINAL DESIGN E2 note, verified ConnectivityCollector:30-34): on watch.disconnected the
 * WatchRef has serial == name == address == identifier; nickname/model/fw/battery are null. The
 * identity block therefore renders those fields blank — documented in the blurb so users don't expect
 * a model/battery here.
 */
@TaskerInputRoot
class DisconnectedFilter @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "pb_evt_lbl_serial")
    var serial: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class DisconnectedOutput @JvmOverloads constructor() : BaseEventOutput()

class DisconnectedRunner : PebbleEventRunner<DisconnectedFilter, DisconnectedOutput>() {
    override val eventType: String = EventRouting.TYPE_DISCONNECTED

    override fun evaluate(
        context: Context,
        filter: DisconnectedFilter,
        cached: CachedEvent?,
        update: DisconnectedOutput?,
    ): TaskerPluginResultCondition<DisconnectedOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        if (!EventSupport.matchesSerial(filter.serial, e.watch)) {
            return TaskerPluginResultConditionUnsatisfied()
        }
        return TaskerPluginResultConditionSatisfied(context, DisconnectedOutput().fillBase<DisconnectedOutput>(e))
    }
}

class DisconnectedHelper(config: TaskerPluginConfig<DisconnectedFilter>) :
    PebbleEventHelper<DisconnectedFilter, DisconnectedOutput, DisconnectedRunner>(config) {
    override val inputClass = DisconnectedFilter::class.java
    override val outputClass = DisconnectedOutput::class.java
    override val runnerClass = DisconnectedRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<DisconnectedFilter>, blurbBuilder: StringBuilder) {
        val serial = input.regular.serial?.takeIf { it.isNotBlank() } ?: "any watch"
        blurbBuilder.append("Fires when ").append(serial).append(" disconnects.")
            .append("\nOutputs: %pb_serial %pb_name %pb_address + %pb_json")
            .append(" (model/fw/battery are blank on disconnect).")
    }
}

class DisconnectedActivity :
    PebbleConfigActivity<DisconnectedFilter, DisconnectedOutput, DisconnectedRunner, DisconnectedHelper, ActivityConfigSerialOnlyBinding>() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityConfigSerialOnlyBinding =
        ActivityConfigSerialOnlyBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<DisconnectedFilter>) = DisconnectedHelper(config)

    override fun onConfigCreated(binding: ActivityConfigSerialOnlyBinding) {
        binding.pbTitle.setText(R.string.pb_evt_disconnected_title)
        binding.pbDesc.setText(R.string.pb_evt_disconnected_desc)
        // The serial field now carries its own "Any" + watches dropdown; the old button is redundant.
        binding.pbBtnPickWatch.visibility = android.view.View.GONE
        CriteriaDropdown.attachWatchSerial(binding.pbLayoutSerial)
    }

    override fun assignFromInput(input: TaskerInput<DisconnectedFilter>) {
        binding?.pbEditSerial?.setText(input.regular.serial.orEmpty())
    }

    override val inputForTasker: TaskerInput<DisconnectedFilter>
        get() = TaskerInput(
            DisconnectedFilter(serial = binding?.pbEditSerial?.text?.toString()?.trim().orEmpty()),
        )
}
