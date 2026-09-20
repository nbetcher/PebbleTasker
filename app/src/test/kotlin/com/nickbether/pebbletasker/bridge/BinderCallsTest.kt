package com.nickbether.pebbletasker.bridge

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

class BinderCallsTest {
    @Test fun `uninterruptible binder cannot trap caller or consume unbounded workers`() = runBlocking {
        val calls = BinderCalls(workers=1, queueSize=1)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        try {
            val first = async {
                runCatching { calls.call(150) {
                    entered.countDown()
                    while (release.count > 0) try { release.await() } catch (_: InterruptedException) { }
                    "late"
                } }
            }
            withContext(Dispatchers.IO) { assertTrue(entered.await(2,TimeUnit.SECONDS)) }
            assertTrue(first.await().exceptionOrNull() is TimeoutCancellationException)
            val queued = async { runCatching { calls.call(1_000) { "queued" } } }
            yield()
            assertTrue(runCatching { calls.call(100) { "excess" } }.exceptionOrNull() is RejectedExecutionException)
            queued.cancelAndJoin()
            release.countDown()
        } finally { release.countDown(); calls.close() }
    }
    @Test fun `cancellation discards late result`() = runBlocking {
        val calls = BinderCalls()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var published = false
        try {
            val job = launch { calls.call(1000) { entered.countDown(); release.await(); "result" }; published=true }
            withContext(Dispatchers.IO) { assertTrue(entered.await(2,TimeUnit.SECONDS)) }
            job.cancelAndJoin(); release.countDown()
            assertFalse(published)
        } finally { release.countDown(); calls.close() }
    }
}
