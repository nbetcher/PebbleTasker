package com.nickbether.pebbletasker.tasker.base

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.EventCache
import com.nickbether.pebbletasker.log.PLog
import com.nickbether.pebbletasker.ui.BridgeWarning

/**
 * Base for ALL event plugins (FINAL DESIGN §2.1 / §4.2, FIX C3/C4/C9).
 *
 * The library's condition hook is getSatisfiedCondition(context, input, update) — there is no
 * matches()/toOutput(). We make it final and delegate to [evaluate], which subclasses implement.
 *
 * SOURCE OF TRUTH = [EventCache]: the Tasker pass-through `update` is capability-gated and NOT
 * guaranteed (some hosts don't pass it through), so [evaluate] receives the latest cached event of
 * [eventType] read here, plus the raw `update` as a hint only. Subclasses return one of:
 *   - TaskerPluginResultConditionSatisfied(context, output)   (output REQUIRED, needs context)
 *   - TaskerPluginResultConditionUnsatisfied()                (no output)
 *   - TaskerPluginResultConditionUnknown()                    (no output) — use when the bridge has
 *     never reported this event type, so contexts don't flap to "false".
 *
 * @param TInput  the @TaskerInputRoot filter class
 * @param TOutput the @TaskerInputRoot @TaskerOutputObject output class
 */
abstract class PebbleEventRunner<TInput : Any, TOutput : Any> :
    TaskerPluginRunnerConditionEvent<TInput, TOutput, TOutput>() {

    /** The bridge event `type` this plugin matches (e.g. "watch.battery"). */
    abstract val eventType: String

    /**
     * Decide satisfaction. [cached] is the latest cached event of [eventType] (null if never seen).
     * [filter] is the user's configured filter (input.regular). [update] is Tasker's pass-through hint
     * (usually null; do not rely on it).
     */
    abstract fun evaluate(
        context: Context,
        filter: TInput,
        cached: CachedEvent?,
        update: TOutput?,
    ): TaskerPluginResultCondition<TOutput>

    final override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<TInput>,
        update: TOutput?,
    ): TaskerPluginResultCondition<TOutput> {
        val name = this::class.simpleName
        // Warn (throttled notification) if this event condition is evaluated while we're not bridged.
        BridgeWarning.warnIfUsedWhileUnbridged(context)
        val cached = runCatching { EventCache.get(context).latest(eventType) }.getOrNull()
        PLog.d { "event[$name]: query type=$eventType cached=${cached != null}" }
        return try {
            evaluate(context, input.regular, cached, update).also { r ->
                PLog.i { "event[$name]: -> ${r::class.simpleName?.removePrefix("TaskerPluginResultCondition")}" }
            }
        } catch (t: Throwable) {
            // Never throw out of a condition runner; Unknown keeps the context from flapping.
            PLog.e(t) { "event[$name]: threw -> Unknown" }
            TaskerPluginResultConditionUnknown()
        }
    }
}
