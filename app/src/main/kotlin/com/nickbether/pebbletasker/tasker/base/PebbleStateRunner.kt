package com.nickbether.pebbletasker.tasker.base

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionState
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.nickbether.pebbletasker.log.PLog
import com.nickbether.pebbletasker.ui.BridgeWarning

/**
 * Base for ALL state plugins (FINAL DESIGN §2.2 / §4.2, FIX C8).
 *
 * State conditions query the bridge's getState (or read a cached backing event) on demand. The hook
 * is getSatisfiedCondition(context, input, update) with update fixed to Unit (states have no
 * pass-through). We make it final and delegate to [evaluate].
 *
 * Implementations call BridgeClient.getStateBlocking() (runner threads are not coroutines) and return:
 *   - Satisfied(context, output) when the state holds,
 *   - Unsatisfied() when it does not,
 *   - Unknown() when the bridge is UNBOUND / not yet handshaken (so the context does not flap off).
 *
 * @param TInput  the @TaskerInputRoot input class
 * @param TOutput the @TaskerInputRoot @TaskerOutputObject output class
 */
abstract class PebbleStateRunner<TInput : Any, TOutput : Any> :
    TaskerPluginRunnerConditionState<TInput, TOutput>() {

    abstract fun evaluate(
        context: Context,
        input: TInput,
    ): TaskerPluginResultCondition<TOutput>

    final override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<TInput>,
        update: Unit?,
    ): TaskerPluginResultCondition<TOutput> {
        val name = this::class.simpleName
        PLog.d { "state[$name]: query" }
        return try {
            // Warn (throttled notification) if Tasker is polling this state while we're not bridged.
            if (!ConditionAccess.ready(context)) return TaskerPluginResultConditionUnknown()
            val supported = FeatureSupport.reason(this::class.java, com.nickbether.pebbletasker.bridge.BridgeClient.get(context).currentSession?.capabilities)
            if (supported != null) {
                ConditionAccess.report(context, com.nickbether.pebbletasker.bridge.BridgeResult.err(com.nickbether.pebbletasker.tasker.ErrCodes.UNSUPPORTED_COMMAND, supported))
                return TaskerPluginResultConditionUnknown()
            }
            evaluate(context, input.regular).also { r ->
                // Log the resolved condition (Satisfied / Unsatisfied / Unknown) — the single most
                // useful line for the "state rarely fires" diagnosis.
                PLog.i { "state[$name]: -> ${r::class.simpleName?.removePrefix("TaskerPluginResultCondition")}" }
            }
        } catch (t: Throwable) {
            PLog.e(t) { "state[$name]: threw -> Unknown" }
            TaskerPluginResultConditionUnknown()
        }
    }
}
