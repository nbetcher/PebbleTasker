package com.nickbether.pebbletasker.cache

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.nickbether.pebbletasker.bridge.dto.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class EventCacheTest {
    private fun cache() = EventCache(ApplicationProvider.getApplicationContext(), writeSnapshot = {})
    private fun event(seq: Long, watch: String = "A", type: String = "watch.battery", boot: String = "boot") = EventEnvelope(
        bootId = boot, seq = seq, ts = seq, type = type, watch = WatchRef(watch, watch), data = mapOf("level" to seq.toString()),
    )
    @Test fun `first install skips history but a configured recovery replays new boot`() {
        val c = cache()
        c.seedFromHandshake("boot", 30)
        assertEquals(30L, c.currentHighWater)
        c.seedFromHandshake("new", 7, recover = true)
        assertEquals(-1L, c.currentHighWater)
        assertEquals(listOf(1L, 7L), c.putBatch(listOf(event(7, boot = "new"), event(1, boot = "new"))).map { it.seq })
    }
    @Test fun `same boot handshake never advances replay cursor`() {
        val c = cache(); c.seedFromHandshake("boot", 10); c.seedFromHandshake("boot", 90)
        assertEquals(10L, c.currentHighWater)
        assertEquals(1, c.put(event(11)).size)
    }
    @Test fun `repeated same-type events preserve payloads and per-watch snapshots`() {
        val c = cache(); c.seedFromHandshake("boot", 0)
        val delivery = c.putBatch(listOf(event(1), event(2, "B"), event(3)))
        assertEquals(listOf("1", "2", "3"), delivery.map { it.str("level") })
        assertEquals("3", c.latestForSerial("watch.battery", "A")?.str("level"))
        assertEquals("2", c.latestForSerial("watch.battery", "B")?.str("level"))
    }
    @Test fun `duplicates within and across batches never route again`() {
        val c = cache(); c.seedFromHandshake("boot", 0)
        assertEquals(1, c.putBatch(listOf(event(1), event(1))).size)
        assertTrue(c.put(event(1)).isEmpty())
    }
    @Test fun `filtered cursor holes are not inferred history loss`() {
        val c = cache(); c.seedFromHandshake("boot", 5)
        val r = c.ingestBatch(EventBatch(bootId = "boot", events = listOf(event(99)), cursor = 100))
        assertEquals(listOf("watch.battery"), r.map { it.type })
        assertEquals(100L, c.currentHighWater)
        assertTrue(c.put(event(99)).isEmpty())
    }
    @Test fun `empty batch cursor and explicit historyLost are respected once`() {
        val c = cache(); c.seedFromHandshake("boot", 5)
        val batch = EventBatch(bootId = "boot", cursor = 50, historyLost = true)
        assertEquals(CachedEvent.TYPE_GAP, c.ingestBatch(batch).single().type)
        assertEquals(50L, c.currentHighWater)
        assertTrue(c.ingestBatch(batch).isEmpty())
    }
    @Test fun `stale boot and mixed boot callbacks cannot regress cache`() {
        val c = cache(); c.seedFromHandshake("boot", 5)
        assertTrue(c.put(event(6, boot = "old")).isEmpty())
        assertTrue(c.ingestBatch(EventBatch(bootId = "boot", events = listOf(event(6), event(7, boot = "old")))).isEmpty())
        assertEquals("boot", c.currentBootId); assertEquals(5L, c.currentHighWater)
    }
    @Test fun `disconnect resets only its watch developer and firmware snapshot`() {
        val c = cache(); c.seedFromHandshake("boot", 0)
        c.seedState(StateResult(data = StateData(watches = listOf(WatchRef("A","A",devEnabled=true,fwStatus="in_progress"), WatchRef("B","B",devEnabled=true)))))
        c.put(event(1, "A", "watch.disconnected"))
        assertEquals(false, c.latestForSerial("dev.state", "A")?.bool("enabled"))
        assertEquals(true, c.latestForSerial("dev.state", "B")?.bool("enabled"))
        assertEquals("unavailable", c.latestForSerial("fw.status", "A")?.str("status"))
    }
    @Test fun `snapshot hydration produces no edges and preserves cursor`() {
        val c = cache(); c.seedFromHandshake("boot", 4)
        c.seedState(StateResult(data = StateData(bluetoothEnabled = false)))
        assertEquals(4L, c.currentHighWater); assertEquals(false, c.latest("bt.state")?.bool("enabled"))
        c.seedState(StateResult(data = StateData()))
        assertNull(c.latest("bt.state"))
    }
    @Test fun `authority invalidation clears content and rotates payload epoch without losing cursor`() {
        val c = cache(); c.seedFromHandshake("boot", 0); c.put(event(1))
        val epoch = c.deliveryEpoch
        c.invalidateAuthority()
        assertNull(c.latest("watch.battery")); assertEquals(1L, c.currentHighWater)
        assertNotEquals(epoch, c.deliveryEpoch)
    }
    @Test fun `one persistence writer cannot overtake blocked older snapshot`() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val stored = mutableListOf<Long>()
        val c = EventCache(ApplicationProvider.getApplicationContext(), writeSnapshot = {
            if (it.highWater == 0L) { entered.complete(Unit); release.await() }
            stored += it.highWater
        })
        c.seedFromHandshake("boot", 0); entered.await(); c.put(event(1)); c.put(event(2))
        assertTrue(stored.isEmpty())
        release.complete(Unit); c.awaitPersistence()
        assertEquals(listOf(0L, 2L), stored)
    }
    @Test fun `invalidation persistence follows earlier sensitive writes`() = runBlocking {
        val stored = mutableListOf<EventCache.Snapshot>()
        val c = EventCache(ApplicationProvider.getApplicationContext(), writeSnapshot = { stored += it })
        c.seedFromHandshake("boot", 0); c.put(event(1)); c.invalidateAuthority(); c.awaitPersistence()
        assertTrue(stored.last().events.isEmpty()); assertEquals(1L, stored.last().highWater)
    }
    @Test fun `failed persistence is reported and the next snapshot can recover`() = runBlocking {
        var failWrite = true
        val c = EventCache(ApplicationProvider.getApplicationContext(), writeSnapshot = {
            if (failWrite) throw java.io.IOException("disk unavailable")
        })
        c.seedFromHandshake("boot", 0)
        try {
            c.awaitPersistence()
            fail("A failed write must not be reported as persisted")
        } catch (expected: java.io.IOException) {
            assertEquals("disk unavailable", expected.message)
        }
        failWrite = false
        c.put(event(1))
        c.awaitPersistence()
        assertEquals(1L, c.currentHighWater)
    }
    @Test fun `slow disk coalesces a burst without dropping the final cursor or revocation`() = runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val stored=mutableListOf<EventCache.Snapshot>()
        val c=EventCache(ApplicationProvider.getApplicationContext(),writeSnapshot={
            if(it.highWater==0L){entered.complete(Unit);release.await()}
            stored+=it
        })
        c.seedFromHandshake("boot",0);entered.await()
        repeat(1000){c.put(event(it+1L))}
        c.invalidateAuthority()
        release.complete(Unit);c.awaitPersistence()
        assertEquals(2,stored.size)
        assertEquals(1000L,stored.last().highWater)
        assertTrue(stored.last().events.isEmpty())
    }
}
