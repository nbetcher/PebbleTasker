package com.nickbether.pebbletasker.tasker.base

import android.os.SystemClock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

/** Shared by BOTH Android entry points. Enqueue on their main-thread callbacks, before any
 * background work. Completion is part of the work item, not another parallel SDK handoff. */
internal object ConditionQueryQueue {
    private val executor = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        ArrayBlockingQueue(128), { r -> Thread(r, "pb-conditions").apply { isDaemon = true } })
    fun submit(work: () -> Unit, unavailable: () -> Unit) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        try {
            executor.execute {
                ConditionQueryBudget.deadline.set(deadline)
                try {
                    if (ConditionQueryBudget.remaining(1) <= 0) unavailable() else work()
                } catch (_: Exception) { unavailable() }
                finally { ConditionQueryBudget.deadline.remove() }
            }
        } catch (_: RejectedExecutionException) { unavailable() }
    }
}

internal object ConditionQueryBudget {
    val deadline = ThreadLocal<Long>()
    fun remaining(max: Long): Long = deadline.get()?.let {
        (it - SystemClock.elapsedRealtime()).coerceIn(0, max)
    } ?: max
}
