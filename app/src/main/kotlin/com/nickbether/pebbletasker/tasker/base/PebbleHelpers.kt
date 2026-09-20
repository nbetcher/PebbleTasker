package com.nickbether.pebbletasker.tasker.base

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner

/**
 * Helper bases for the three plugin kinds (FINAL DESIGN §4.2, FIX C7).
 *
 * The library has NO event/state-specific helper with I/O — the event-vs-state distinction lives in
 * the RUNNER base ([PebbleEventRunner] / [PebbleStateRunner]) and the manifest intent-filter, NOT the
 * helper. So all three kinds extend plain TaskerPluginConfigHelper<I, O, Runner>; subclasses supply
 * inputClass / outputClass / runnerClass.
 *
 * Shared defaults baked in here:
 *  - timeoutSeconds = 9  (staggered BELOW Tasker's 10s and ABOVE the bridge's ~7s — FIX B4).
 *
 * OUTPUT SUGGESTION CHAINING (FINAL DESIGN §4.1 manner 4): the library already forwards this plugin's
 * @TaskerOutputVariable names into downstream pickers via TaskerPlugin.addRelevantVariableList in
 * getTaskerIntentFromInput (verified TaskerPluginConfigHelper:47). So declaring outputs is normally
 * enough. If a host fails to chain CONDITION outputs, override addOutputs/relevant handling per plugin
 * — but the action path is reliable. (relevantVariables itself is read-only; never assign it.)
 */
abstract class PebbleActionHelper<TInput : Any, TOutput : Any, TRunner : TaskerPluginRunner<TInput, TOutput>>(
    config: TaskerPluginConfig<TInput>,
) : TaskerPluginConfigHelper<TInput, TOutput, TRunner>(config) {
    override fun isInputValid(input: com.joaomgcd.taskerpluginlibrary.input.TaskerInput<TInput>): com.joaomgcd.taskerpluginlibrary.SimpleResult {
        val reason = FeatureSupport.inputReason(input.regular) ?: FeatureSupport.reason(runnerClass, com.nickbether.pebbletasker.bridge.BridgeClient.get(config.context).currentSession?.capabilities)
        return if (reason == null) super.isInputValid(input) else com.joaomgcd.taskerpluginlibrary.SimpleResultError(reason)
    }
    override val timeoutSeconds: Int = 30
}

abstract class PebbleEventHelper<TInput : Any, TOutput : Any, TRunner : TaskerPluginRunner<TInput, TOutput>>(
    config: TaskerPluginConfig<TInput>,
) : TaskerPluginConfigHelper<TInput, TOutput, TRunner>(config) {
    override fun isInputValid(input: com.joaomgcd.taskerpluginlibrary.input.TaskerInput<TInput>): com.joaomgcd.taskerpluginlibrary.SimpleResult {
        if (!hostCapabilities.event.supportsPassThroughData) return com.joaomgcd.taskerpluginlibrary.SimpleResultError("This host does not support immutable event pass-through data. Use a current Tasker version.")
        val reason = FeatureSupport.reason(runnerClass, com.nickbether.pebbletasker.bridge.BridgeClient.get(config.context).currentSession?.capabilities)
        return if (reason == null) super.isInputValid(input) else com.joaomgcd.taskerpluginlibrary.SimpleResultError(reason)
    }
    override val timeoutSeconds: Int = PLUGIN_TIMEOUT_SECONDS
}

abstract class PebbleStateHelper<TInput : Any, TOutput : Any, TRunner : TaskerPluginRunner<TInput, TOutput>>(
    config: TaskerPluginConfig<TInput>,
) : TaskerPluginConfigHelper<TInput, TOutput, TRunner>(config) {
    override fun isInputValid(input: com.joaomgcd.taskerpluginlibrary.input.TaskerInput<TInput>): com.joaomgcd.taskerpluginlibrary.SimpleResult {
        val reason = FeatureSupport.reason(runnerClass, com.nickbether.pebbletasker.bridge.BridgeClient.get(config.context).currentSession?.capabilities)
        return if (reason == null) super.isInputValid(input) else com.joaomgcd.taskerpluginlibrary.SimpleResultError(reason)
    }
    override val timeoutSeconds: Int = PLUGIN_TIMEOUT_SECONDS
}

/** Condition timeout requested from the host; action helpers request 30 seconds. */
const val PLUGIN_TIMEOUT_SECONDS = 9
