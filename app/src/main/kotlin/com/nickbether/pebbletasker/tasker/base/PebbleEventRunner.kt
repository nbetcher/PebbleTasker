package com.nickbether.pebbletasker.tasker.base

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.*
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.EventDelivery
import com.nickbether.pebbletasker.log.PLog

/** SDK pass-through payload is authoritative. A state refresh never replays a cached edge. */
abstract class PebbleEventRunner<TInput : Any, TOutput : Any> :
    TaskerPluginRunnerConditionEvent<TInput, TOutput, EventDelivery>() {
    abstract val eventType: String
    protected open fun accepts(type: String) = type == eventType
    protected open val isLocalDiagnostic = false

    abstract fun evaluate(context: Context, filter: TInput, cached: CachedEvent?, update: TOutput?): TaskerPluginResultCondition<TOutput>

    final override fun getSatisfiedCondition(context: Context, input: TaskerInput<TInput>, update: EventDelivery?): TaskerPluginResultCondition<TOutput> {
        return try {
            val event = update?.event()
            val local = isLocalDiagnostic && event?.type == "plugin.access"
            if (!local && !ConditionAccess.ready(context)) return TaskerPluginResultConditionUnknown()
            if (event == null) return TaskerPluginResultConditionUnsatisfied()
            if (local) {
                if (!ConditionAccess.acceptsDiagnostic(context, event)) return TaskerPluginResultConditionUnsatisfied()
            } else if (update!!.epoch != com.nickbether.pebbletasker.cache.EventCache.get(context).deliveryEpoch) return TaskerPluginResultConditionUnsatisfied()
            if (!accepts(event.type)) return TaskerPluginResultConditionUnsatisfied()
            evaluate(context, input.regular, event, null)
        } catch (t: Exception) {
            PLog.e(t) { "event[$eventType]: query failed" }
            TaskerPluginResultConditionUnknown()
        }
    }
}
