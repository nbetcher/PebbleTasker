package com.nickbether.pebbletasker.tasker.event.connected

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.event.EventSupport

/**
 * E1 runner — Satisfied when the latest watch.connected event matches the serial filter (READY).
 */
class ConnectedRunner : PebbleEventRunner<ConnectedFilter, ConnectedOutput>() {

    override val eventType: String = EventRouting.TYPE_CONNECTED

    override fun evaluate(
        context: Context,
        filter: ConnectedFilter,
        cached: CachedEvent?,
        update: ConnectedOutput?,
    ): TaskerPluginResultCondition<ConnectedOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        if (!EventSupport.matchesSerial(filter.serial, e.watch)) {
            return TaskerPluginResultConditionUnsatisfied()
        }
        return TaskerPluginResultConditionSatisfied(context, ConnectedOutput().fillBase<ConnectedOutput>(e))
    }
}
