package com.nickbether.pebbletasker.cache

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AuthorityRecoveryTest {
    @Test fun transportHidesSnapshotsButMatchingAuthorityPreservesDeliveries() {
        val cache = EventCache(ApplicationProvider.getApplicationContext(), writeSnapshot = {})
        cache.acceptAuthority("boot", "grant-1")
        val epoch = cache.deliveryEpoch
        cache.suspendAuthority()
        cache.acceptAuthority("boot", "grant-1")
        assertEquals(epoch, cache.deliveryEpoch)
        cache.acceptAuthority("boot", "grant-2")
        assertNotEquals(epoch, cache.deliveryEpoch)
        val revised = cache.deliveryEpoch
        cache.acceptAuthority("new-boot", "grant-2")
        assertNotEquals(revised, cache.deliveryEpoch)
    }
    @Test fun missingRevisionAndExplicitRevocationCannotRestoreOldAuthority() {
        val cache = EventCache(ApplicationProvider.getApplicationContext(), writeSnapshot = {})
        cache.acceptAuthority("boot", "grant")
        val original = cache.deliveryEpoch
        cache.invalidateAuthority()
        cache.acceptAuthority("boot", "grant")
        assertNotEquals(original, cache.deliveryEpoch)
        val current = cache.deliveryEpoch
        cache.acceptAuthority("boot", null)
        assertNotEquals(current, cache.deliveryEpoch)
    }
    @Test fun processRestoreRetainsEpochOnlyAfterSameAuthorityRevalidation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val old = EventCache(context)
        old.acceptAuthority("boot", "grant")
        old.seedFromHandshake("boot", 7)
        val epoch = old.deliveryEpoch
        old.awaitPersistence()
        val restored = EventCache(context)
        restored.warm()
        restored.suspendAuthority()
        restored.acceptAuthority("boot", "grant")
        assertEquals(epoch, restored.deliveryEpoch)
        assertEquals(7L, restored.highWaterSeqFor("boot"))
        restored.awaitPersistence()
    }
}
