package com.nickbether.pebbletasker.tasker.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.joaomgcd.taskerpluginlibrary.TaskerPluginConstants
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputInfos
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import com.nickbether.pebbletasker.tasker.event.appmsg.AppMessageFilter
import com.nickbether.pebbletasker.tasker.event.appmsg.AppMessageSubscriptions
import kotlinx.coroutines.*
import net.dinglisch.android.tasker.TaskerPlugin

/** Runs before the SDK's intentional early return for event queries with no message ID.
 * Tasker's normal QUERY_CONDITION is the restore/initialization hook. It is not an event.
 * Delegate the actual condition execution to the pinned SDK to preserve its result/output ABI.
 */
object ConditionQueryDemand {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun reconcile(context: Context, intent: Intent): Boolean {
        val passThrough = TaskerPlugin.Event.retrievePassThroughData(intent)
        val delivery = com.nickbether.pebbletasker.cache.EventDelivery()
        if (passThrough != null) TaskerInputInfos.fromBundle(context, delivery, passThrough)
        if (delivery.event()?.type == "plugin.access") return true
        // Restore desired subscription from the real host query input, including no-ID initialization.
        val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
        if (bundle?.getString(TaskerPluginConstants.EXTRA_ACTION_INPUT_CLASS) == AppMessageFilter::class.java.name) {
            val filter = AppMessageFilter()
            TaskerInputInfos.fromBundle(context, filter, bundle)
            AppMessageSubscriptions.remember(context, filter.uuid, filter.serial, filter.ownership, filter.subscriptionId)
        }
        // Initialization queries have no edge for the SDK to evaluate. Warm asynchronously;
        // real queries wait once in their runner rather than consuming two readiness budgets.
        if (bundle?.getString(TaskerPluginConstants.EXTRA_ACTION_INPUT_CLASS)?.contains(".tasker.event.") == true &&
            TaskerPlugin.Event.retrievePassThroughMessageID(intent) == -1) scope.launch {
            if (ConditionAccess.ready(context.applicationContext)) AppMessageSubscriptions.restore(context.applicationContext)
        }
        return com.nickbether.pebbletasker.bridge.BridgeClient.get(context).currentSession != null
    }
}

/** Evaluate and return the pinned SDK's result synchronously on our ordered worker. */
internal fun evaluateCondition(context: Context, intent: Intent): Pair<Int, Bundle> {
    ConditionQueryDemand.reconcile(context, intent)
    val result = ConditionSdk.evaluate(context, intent)
    val extras = Bundle()
    result?.bundle?.let { TaskerPlugin.addVariableBundle(extras, it) }
    return (result?.code ?: TaskerPluginConstants.RESULT_CONDITION_UNKNOWN) to extras
}

class ConditionQueryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val pending = goAsync()
        val completed = java.util.concurrent.atomic.AtomicBoolean()
        fun finish(code: Int, extras: Bundle) {
            if (completed.compareAndSet(false, true)) {
                pending.setResult(code, null, extras)
                pending.finish()
            }
        }
        ConditionQueryQueue.submit(work = {
            val (code, extras) = evaluateCondition(context.applicationContext, intent)
            finish(code, extras)
        }, unavailable = { finish(TaskerPluginConstants.RESULT_CONDITION_UNKNOWN, Bundle()) })
    }
}

class ConditionQueryService : android.app.Service() {
    private val binder = android.os.Binder()
    override fun onBind(intent: Intent?): android.os.IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TaskerPluginRunner.startForegroundIfNeeded(this)
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY }
        val receiver = TaskerPlugin.Condition.getResultReceiver(intent)
        val completed = java.util.concurrent.atomic.AtomicBoolean()
        fun finish(code: Int, extras: Bundle) {
            if (!completed.compareAndSet(false, true)) return
            try { receiver?.send(code, extras) }
            finally { stopSelfResult(startId) }
        }
        ConditionQueryQueue.submit(work = {
            val (code, extras) = evaluateCondition(this, intent)
            finish(code, extras)
        }, unavailable = { finish(TaskerPluginConstants.RESULT_CONDITION_UNKNOWN, Bundle()) })
        return START_NOT_STICKY
    }
}
