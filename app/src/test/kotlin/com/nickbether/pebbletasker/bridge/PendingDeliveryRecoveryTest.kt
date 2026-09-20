package com.nickbether.pebbletasker.bridge

import android.content.*
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import com.nickbether.pebbletasker.bridge.dto.*
import com.nickbether.pebbletasker.setup.SetupState
import com.nickbether.pebbletasker.tasker.ErrCodes
import coredevices.coreapp.automation.*
import io.mockk.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PendingDeliveryRecoveryTest {
    private val base get()=ApplicationProvider.getApplicationContext<Context>()
    private val clients=mutableListOf<BridgeClient>()
    private val scopes=mutableListOf<CoroutineScope>()
    private val workers=mutableListOf<BinderCalls>()
    private val svc=mockk<IBridgeService>(relaxed=true)
    private val binder=mockk<IBinder>()
    private lateinit var host:Host
    private class Host(base:Context,val binder:IBinder):ContextWrapper(base) {
        var binds=0
        var callback:ServiceConnection?=null
        override fun bindService(intent:Intent,connection:ServiceConnection,flags:Int):Boolean {
            binds++; callback=connection; connection.onServiceConnected(null,binder); return true
        }
        override fun unbindService(connection:ServiceConnection) { }
    }
    private fun jsonHello()=BridgePluginJson.json.encodeToString(BridgeHello.serializer(),BridgeHello(
        bootId="boot",capabilities=listOf("events.core","command.system.ping"),grants=Grants(),latestSeq=0,appVersion="1",clientToken="token",authorityId="stable-grant"))
    private fun client(pin:CertPinner.PinResult=CertPinner.PinResult.OK):BridgeClient {
        val pinner=mockk<CertPinner>()
        every { pinner.verify() } returns pin
        every { pinner.currentSha() } returns "fingerprint"
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default).also { scopes+=it }
        val calls=BinderCalls().also { workers+=it }
        return BridgeClient(host,pinner,scope,calls).also {clients+=it}
    }
    @Before fun setup() {
        SetupState.setAccessDenied(base,false)
        base.getSharedPreferences("pb_setup",Context.MODE_PRIVATE).edit().clear().commit()
        every { binder.queryLocalInterface(any()) } returns svc
        host=Host(base,binder)
        every { svc.handshake(any()) } returns jsonHello()
        every { svc.getEventsSince(any(),any()) } returns """{"bootId":"boot","subscriptionToken":"token"}"""
        every { svc.getState(any()) } returns """{"data":{"watches":[]}}"""
        every { svc.execute(any(),any()) } returns """{"ok":true}"""
    }
    @After fun cleanup() { clients.forEach {it.stop()}; scopes.forEach {it.cancel()}; workers.forEach {it.close()} }

    @Test fun queuedCommandDoesNotReachPebbleAfterClientStop() = runBlocking {
        val client = client()
        assertTrue(client.awaitReady().isOk)
        val calls = workers.last()
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val blocked = (1..2).map {
            async(start = CoroutineStart.UNDISPATCHED) { calls.call(5000) { entered.countDown(); release.await(); Unit } }
        }
        try {
            withContext(Dispatchers.IO) { assertTrue(entered.await(2, TimeUnit.SECONDS)) }
            val action = async(start = CoroutineStart.UNDISPATCHED) { client.execute(CommandEnvelope(type = "system.ping")) }
            client.stop()
            release.countDown()
            assertFalse(action.await().isOk)
            verify(exactly = 0) { svc.execute(any(), any()) }
        } finally { release.countDown(); blocked.awaitAll() }
    }

    /** A transport generation must not revoke pending deliveries. */
    @Test fun transientRecoveryRetainsAlreadyRoutedEdge() = runBlocking {
        val client = client()
        assertTrue(client.awaitReady().isOk)
        val cache = com.nickbether.pebbletasker.cache.EventCache.get(base)
        val seq = cache.currentHighWater + 1
        val edge = com.nickbether.pebbletasker.bridge.dto.EventEnvelope(type="calls.state", bootId="boot", seq=seq, ts=1, category="calls", data=mapOf("state" to "ringing"))
        val cached = cache.put(edge).single()
        val delivery = com.nickbether.pebbletasker.cache.EventDelivery.from(cached, cache.deliveryEpoch)
        mockkObject(BridgeClient.Companion)
        every { BridgeClient.get(any()) } returns client
        val runner = com.nickbether.pebbletasker.tasker.event.call.CallRunner()
        val input = com.joaomgcd.taskerpluginlibrary.input.TaskerInput(com.nickbether.pebbletasker.tasker.event.call.CallFilter())
        try {
            assertTrue(runner.getSatisfiedCondition(base, input, delivery) is com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied)
            every { svc.execute(any(), any()) } throws android.os.RemoteException("temporary transport failure")
            assertFalse(client.execute(CommandEnvelope(type="system.ping")).isOk)
            withTimeout(4000) { while (client.currentSession == null) delay(10) }
            assertEquals(delivery.epoch, cache.deliveryEpoch)
            assertTrue(runner.getSatisfiedCondition(base, input, delivery) is com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied)
            assertEquals(seq, cache.highWaterSeqFor("boot"))
            verify(atLeast=1) { svc.getEventsSince(seq, "boot") }
            println("Same boot and grants; queued edge seq=$seq remains eligible after recovery")
        } finally { unmockkObject(BridgeClient.Companion) }
    }
}
