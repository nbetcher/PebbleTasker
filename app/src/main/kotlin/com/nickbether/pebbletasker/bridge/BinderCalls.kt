package com.nickbether.pebbletasker.bridge

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Binder cannot be interrupted reliably. Bound both abandoned workers and queued work, and never
 * run callers on these workers: a deadline can return even if the remote transaction never does. */
internal class BinderCalls(workers: Int = 2, queueSize: Int = 8) : AutoCloseable {
    private val executor = ThreadPoolExecutor(workers, workers, 30, TimeUnit.SECONDS,
        ArrayBlockingQueue(queueSize), { r -> Thread(r, "pb-binder").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())

    suspend fun <T> call(timeoutMs: Long, block: () -> T): T = withTimeout(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val future = try {
                executor.submit {
                    try {
                        val result = block()
                        if (continuation.isActive) continuation.resume(result)
                    } catch (t: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(t)
                    }
                }
            } catch (t: Throwable) {
                continuation.resumeWithException(t)
                null
            }
            continuation.invokeOnCancellation {
                future?.cancel(true)
                executor.purge()
            }
        }
    }
    override fun close() { executor.shutdownNow() }
}
