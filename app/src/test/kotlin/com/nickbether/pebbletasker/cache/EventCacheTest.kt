package com.nickbether.pebbletasker.cache

import androidx.test.core.app.ApplicationProvider
import com.nickbether.pebbletasker.bridge.dto.EventEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Delivery-logic tests for [EventCache]. Instances are constructed directly (never [EventCache.get])
 * so nothing is warmed from DataStore and each test starts from clean in-memory state.
 */
@RunWith(RobolectricTestRunner::class)
class EventCacheTest {

    private fun newCache() = EventCache(ApplicationProvider.getApplicationContext())

    private fun event(seq: Long, type: String = "watch.connected", boot: String = BOOT) = EventEnvelope(
        v = 1,
        bootId = boot,
        seq = seq,
        ts = seq,
        category = "connectivity",
        type = type,
        watch = null,
        data = emptyMap(),
    )

    /**
     * REGRESSION: re-handshaking within the same boot must NOT advance the high-water.
     *
     * The caller derives its replay cursor from the high-water immediately after seeding, so raising
     * it to latestSeq here made the bridge replay nothing and silently discarded every event that
     * arrived while this process was dead — precisely the window the reverse-bind tether revives us
     * to collect.
     */
    @Test
    fun `seed does not advance high-water within the same boot`() {
        val cache = newCache()
        cache.seedFromHandshake(BOOT, latestSeq = 100)
        assertEquals(100L, cache.currentHighWater)

        // Process died; 50 more events accumulated on the bridge; we re-handshake on the SAME boot.
        val gap = cache.seedFromHandshake(BOOT, latestSeq = 150)

        assertNull("same boot is not a gap", gap)
        assertEquals("high-water must stay put so 101..150 are replayed", 100L, cache.currentHighWater)
        assertEquals(100L, cache.highWaterSeqFor(BOOT))
    }

    @Test
    fun `seed resets high-water and synthesizes a gap on a new boot`() {
        val cache = newCache()
        cache.seedFromHandshake(BOOT, latestSeq = 100)

        val gap = cache.seedFromHandshake("boot-2", latestSeq = 5)

        assertNotNull("bridge restarted; prior seqs are void", gap)
        assertEquals(CachedEvent.TYPE_GAP, gap!!.type)
        assertEquals(5L, cache.currentHighWater)
        assertEquals(-1L, cache.highWaterSeqFor(BOOT))
    }

    @Test
    fun `first seed on a fresh install adopts latestSeq without replaying history`() {
        val cache = newCache()
        val gap = cache.seedFromHandshake(BOOT, latestSeq = 42)

        assertNull(gap)
        assertEquals(42L, cache.currentHighWater)
    }

    /** After the fix, the events the bridge replays are actually ingested and routed. */
    @Test
    fun `events after the cursor are routed once and deduped thereafter`() {
        val cache = newCache()
        cache.seedFromHandshake(BOOT, latestSeq = 100)

        val routed = cache.putBatch(listOf(event(101), event(102)))
        assertEquals(listOf(101L, 102L), routed.map { it.seq })
        assertEquals(102L, cache.currentHighWater)

        assertTrue("already-seen seqs must not route again", cache.putBatch(listOf(event(102))).isEmpty())
    }

    @Test
    fun `a seq discontinuity synthesizes a gap`() {
        val cache = newCache()
        cache.seedFromHandshake(BOOT, latestSeq = 100)

        val routed = cache.putBatch(listOf(event(160)))

        val gap = routed.firstOrNull { it.type == CachedEvent.TYPE_GAP }
        assertNotNull("events 101..159 were lost; the user must be told", gap)
        assertEquals("100", gap!!.data["gap_from"])
        assertEquals("160", gap.data["gap_to"])
    }

    @Test
    fun `latest returns the most recent event of a type`() {
        val cache = newCache()
        cache.seedFromHandshake(BOOT, latestSeq = 0)
        cache.putBatch(listOf(event(1, "watch.battery"), event(2, "watch.battery")))

        assertEquals(2L, cache.latest("watch.battery")?.seq)
        assertNull(cache.latest("watch.connected"))
    }

    private companion object {
        const val BOOT = "boot-1"
    }
}
