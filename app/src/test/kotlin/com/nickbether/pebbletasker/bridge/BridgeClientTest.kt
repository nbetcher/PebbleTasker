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
class BridgeClientTest {
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
        bootId="boot",capabilities=listOf("events.core","command.system.ping"),grants=Grants(),latestSeq=0,appVersion="1",clientToken="token"))
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
    @Test fun `cold action awaits readiness and queries only verified session`() = runBlocking {
        val client=client()
        assertTrue(client.execute(CommandEnvelope(type="system.ping")).isOk)
        assertTrue(client.getState().isOk)
        assertEquals(1,host.binds)
        verify(exactly=1) {svc.handshake(any())}
    }
    @Test fun `pending repeat demand retries once each with no background pollers`() = runBlocking {
        every {svc.handshake(any())} returns """{"ok":false,"error":{"code":"CONSENT_PENDING","message":"Review in Pebble"}}"""
        val client=client()
        repeat(3) {assertEquals(ErrCodes.CONSENT_PENDING,(client.awaitReady() as BridgeResult.Err).code)}
        verify(exactly=3) {svc.handshake(any())}
        every {svc.handshake(any())} returns jsonHello()
        assertTrue(client.awaitReady().isOk)
        assertEquals(1,host.binds)
    }
    @Test fun `concurrent readiness requests share one handshake`() = runBlocking {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        every {svc.handshake(any())} answers { entered.countDown();release.await(3,TimeUnit.SECONDS);jsonHello() }
        val client=client()
        val pending=List(20) {async(Dispatchers.Default) {client.awaitReady()} }
        withContext(Dispatchers.IO) {assertTrue(entered.await(2,TimeUnit.SECONDS))}
        release.countDown()
        assertTrue(pending.awaitAll().all {it.isOk})
        verify(exactly=1) {svc.handshake(any())}
    }
    @Test fun `denied demand never handshakes again even after stop or recreate`() = runBlocking {
        every {svc.handshake(any())} returns """{"ok":false,"error":{"code":"ACCESS_DENIED","message":"Denied in the Pebble app"}}"""
        val client=client()
        repeat(5) {assertEquals(ErrCodes.ACCESS_DENIED,(client.awaitReady() as BridgeResult.Err).code)}
        client.stop()
        assertEquals(ErrCodes.ACCESS_DENIED,(client().awaitReady() as BridgeResult.Err).code)
        verify(exactly=1) {svc.handshake(any())}
    }
    @Test fun `rejected host pin blocks state and command IPC`() = runBlocking {
        val client=client(CertPinner.PinResult.MISMATCH)
        assertEquals(ErrCodes.CERT_MISMATCH,(client.getState() as BridgeResult.Err).code)
        assertEquals(ErrCodes.CERT_MISMATCH,(client.execute(CommandEnvelope(type="system.ping")) as BridgeResult.Err).code)
        verify(exactly=0) {svc.getState(any())}; verify(exactly=0) {svc.handshake(any())}
        assertEquals(0,host.binds)
    }
    @Test fun `stale handshake after stop cannot mark ready or setup complete`() = runBlocking {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        every {svc.handshake(any())} answers {
            entered.countDown()
            while(release.count>0) try {release.await()} catch(_:InterruptedException) {}
            jsonHello()
        }
        val client=client()
        val pending=async(Dispatchers.Default) {client.awaitReady()}
        withContext(Dispatchers.IO) {assertTrue(entered.await(2,TimeUnit.SECONDS))}
        client.stop(); release.countDown();pending.await()
        assertNull(client.currentSession)
        assertFalse(client.status.value is BridgeClient.ConnectionStatus.Ready)
        assertFalse(SetupState.isSetupComplete(base))
        verify(exactly=0) {svc.registerEventListener(any(),any(),any())}
    }
    @Test fun `probe without active registration acknowledgement is not ready`() = runBlocking {
        every {svc.getEventsSince(any(),any())} returns """{"bootId":"boot","subscriptionToken":"obsolete"}"""
        val client=client()
        assertFalse(client.awaitReady().isOk);assertNull(client.currentSession)
        assertFalse(SetupState.isSetupComplete(base))
    }
    @Test fun `binder remote exception remains transport error`() = runBlocking {
        every {svc.handshake(any())} throws android.os.RemoteException("gone")
        assertEquals(ErrCodes.BRIDGE_UNREACHABLE,(client().awaitReady() as BridgeResult.Err).code)
    }
    @Test fun `probe replay never overtakes ordered disconnect then connect callbacks`() = runBlocking {
        val router=com.nickbether.pebbletasker.cache.EventRouter
        mockkObject(router)
        val delivered=java.util.concurrent.CopyOnWriteArrayList<Long>()
        every {router.routeAll(any(),any())} answers { delivered.addAll(secondArg<List<com.nickbether.pebbletasker.cache.CachedEvent>>().map {it.seq}); Unit }
        every {router.requestQueryAll(any())} just Runs
        fun batch(seq:Long,type:String)=BridgePluginJson.json.encodeToString(EventBatch.serializer(),EventBatch(
            bootId="boot",events=listOf(EventEnvelope(bootId="boot",seq=seq,ts=seq,type=type)),cursor=seq,subscriptionToken="token"))
        every {svc.registerEventListener(any(),any(),any())} answers {
            val callback=secondArg<IBridgeEventListener>()
            callback.onEvents(batch(1,"watch.disconnected"))
            callback.onEvents(batch(2,"watch.connected"))
        }
        every {svc.getEventsSince(any(),any())} returns batch(3,"watch.disconnected")
        try {
            assertTrue(client().awaitReady().isOk)
            withTimeout(2000) {while(delivered.size<2) delay(10)}
            assertEquals(listOf(1L,2L),delivered.toList())
            assertEquals(2L,com.nickbether.pebbletasker.cache.EventCache.get(base).currentHighWater)
        } finally {unmockkObject(router)}
    }
    @Test fun `delayed probe after disconnect cannot publish Ready`() = runBlocking {
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        every {svc.getEventsSince(any(),any())} answers {
            entered.countDown()
            while(release.count>0) try {release.await()} catch(_:InterruptedException) {}
            """{"bootId":"boot","subscriptionToken":"token"}"""
        }
        val client=client()
        val waiting=async(Dispatchers.Default) {client.awaitReady()}
        withContext(Dispatchers.IO) {assertTrue(entered.await(2,TimeUnit.SECONDS))}
        host.callback!!.onServiceDisconnected(null)
        client.stop() // suppress the bounded recovery attempt so the obsolete completion is isolated
        release.countDown();waiting.await()
        assertNull(client.currentSession)
        assertFalse(SetupState.isSetupComplete(base))
    }
    @Test fun `blocked handshake reaches deadline and late success is discarded`() = runBlocking {
        val release=CountDownLatch(1)
        every {svc.handshake(any())} answers {
            while(release.count>0) try {release.await()} catch(_:InterruptedException) {}
            jsonHello()
        }
        try {
            val client=client()
            assertEquals(ErrCodes.TIMEOUT,(client.awaitReady(5000) as BridgeResult.Err).code)
            release.countDown()
            assertNull(client.currentSession)
            assertFalse(SetupState.isSetupComplete(base))
            verify(exactly=0) {svc.registerEventListener(any(),any(),any())}
        } finally {release.countDown()}
    }
    @Test fun `nonterminal goodbye recovers once but denied goodbye never retries`() = runBlocking {
        val callback=slot<IBridgeEventListener>()
        every {svc.registerEventListener(any(),capture(callback),any())} just Runs
        val client=client()
        assertTrue(client.awaitReady().isOk)
        callback.captured.onBridgeGoodbye("""{"ok":false,"error":{"code":"NOT_AUTHORIZED","message":"Access changed"}}""")
        assertNull(client.currentSession)
        withTimeout(4000) {while(client.currentSession==null) delay(20)}
        verify(exactly=2) {svc.handshake(any())}
        callback.captured.onBridgeGoodbye("""{"ok":false,"error":{"code":"ACCESS_DENIED","message":"Denied in the Pebble app"}}""")
        assertEquals(ErrCodes.ACCESS_DENIED,(client.awaitReady() as BridgeResult.Err).code)
        delay(1100)
        verify(exactly=2) {svc.handshake(any())}
        assertNull(client.currentSession)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `stop cancels queued demand before owner starts`() {
        val scheduler=kotlinx.coroutines.test.TestCoroutineScheduler()
        val scope=CoroutineScope(SupervisorJob()+kotlinx.coroutines.test.StandardTestDispatcher(scheduler)).also {scopes+=it}
        val calls=BinderCalls().also {workers+=it}
        val pinner=mockk<CertPinner>()
        every {pinner.verify()} returns CertPinner.PinResult.OK
        val client=BridgeClient(host,pinner,scope,calls).also {clients+=it}
        client.start();client.stop()
        scheduler.runCurrent()
        assertEquals(0,host.binds)
        assertNull(client.currentSession)
        assertTrue(client.status.value is BridgeClient.ConnectionStatus.Idle)
        verify(exactly=0) {svc.handshake(any())}
    }
}
