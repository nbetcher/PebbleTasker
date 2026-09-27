package com.nickbether.pebbletasker.cache

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeoutOrNull

/**
 * In-process tap on routed bridge events, for code that waits for a specific event after sending a
 * command (job results, preference echoes, reconnects). Tasker delivery is unaffected.
 *
 * Callers take a [mark] BEFORE sending the command, then [await] an event published after that mark.
 * A short replay buffer closes the race where the event arrives before the waiter subscribes.
 */
class EventTap(private val replay: Int = 128) {
    private data class Entry(val n: Long, val event: CachedEvent)

    private val lock = Any()
    private var counter = 0L
    private val recent = ArrayDeque<Entry>()
    private val flow = MutableSharedFlow<Entry>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun publish(events: List<CachedEvent>) {
        if (events.isEmpty()) return
        synchronized(lock) {
            for (event in events) {
                val entry = Entry(++counter, event)
                recent.addLast(entry)
                while (recent.size > replay) recent.removeFirst()
                flow.tryEmit(entry)
            }
        }
    }

    /** Position after the newest event published so far. */
    fun mark(): Long = synchronized(lock) { counter }

    /** First event published after [after] that satisfies [predicate], or null on timeout. */
    suspend fun await(after: Long, timeoutMs: Long, predicate: (CachedEvent) -> Boolean): CachedEvent? =
        withTimeoutOrNull(timeoutMs.coerceAtLeast(0)) {
            flow.onSubscription { synchronized(lock) { recent.filter { it.n > after } }.forEach { emit(it) } }
                .first { it.n > after && predicate(it.event) }
                .event
        }
}

/** The process-wide tap fed by [EventRouter.routeAll]. */
object LiveEvents {
    val tap = EventTap()
    fun publish(events: List<CachedEvent>) = tap.publish(events)
    fun mark(): Long = tap.mark()
    suspend fun await(after: Long, timeoutMs: Long, predicate: (CachedEvent) -> Boolean): CachedEvent? =
        tap.await(after, timeoutMs, predicate)
}
