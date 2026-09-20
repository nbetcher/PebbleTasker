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
class ReadinessRegressionTest {
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

    @Test fun commandPolicyAndServerTimeoutKeepEventSession() = runBlocking {
        val client=client();assertTrue(client.awaitReady().isOk)
        val original=client.currentSession
        for(code in listOf("COMMAND_NOT_AUTHORIZED","TIMEOUT")) {
            every {svc.execute(any(),any())} returns """{"ok":false,"error":{"code":"$code","message":"command rejected"}}"""
            assertFalse(client.execute(CommandEnvelope(type="system.ping")).isOk)
            assertSame(original,client.currentSession)
        }
        verify(exactly=0) {svc.unregisterEventListener(any())}
        verify(exactly=1) {svc.handshake(any())}
    }
    @Test fun transportFailureRecoversWithoutAnotherTaskerDemand() = runBlocking {
        val client=client();assertTrue(client.awaitReady().isOk)
        every {svc.execute(any(),any())} throws android.os.RemoteException("gone")
        assertFalse(client.execute(CommandEnvelope(type="system.ping")).isOk)
        withTimeout(4000) { while(client.currentSession==null) delay(10) }
        verify(exactly=2) {svc.handshake(any())}
    }
    @Test fun localDiagnosticSurvivesRemoteEpochChangeAndDoesNotHandshake() = runBlocking {
        val client=client()
        every {svc.handshake(any())} returns """{"ok":false,"error":{"code":"CONSENT_PENDING","message":"Review in Pebble"}}"""
        assertFalse(client.awaitReady().isOk)
        val access=com.nickbether.pebbletasker.tasker.base.ConditionAccess
        access.clear(base)
        mockkObject(BridgeClient.Companion,com.nickbether.pebbletasker.ui.BridgeWarning)
        every {BridgeClient.get(any())} returns client
        every {com.nickbether.pebbletasker.ui.BridgeWarning.warnIfUsedWhileUnbridged(any())} returns true
        try {
            access.report(base,BridgeResult.Err(ErrCodes.CONSENT_PENDING,"Review in Pebble","CONSENT_PENDING"))
            val id=base.getSharedPreferences("condition_access",0).getString("diagnostic_id",null)!!
            val cache=com.nickbether.pebbletasker.cache.EventCache.get(base)
            val e=com.nickbether.pebbletasker.cache.CachedEvent(type="plugin.access",bootId="plugin",seq=1,ts=1,data=mapOf("error_type" to "CONSENT_PENDING","message" to "Review in Pebble","diagnostic_id" to id))
            val delivery=com.nickbether.pebbletasker.cache.EventDelivery.from(e,cache.deliveryEpoch)
            cache.invalidateAuthority()
            com.nickbether.pebbletasker.tasker.event.EventRouting.registerAll()
            com.nickbether.pebbletasker.cache.EventRouter.routeAll(base,listOf(e))
            val intent=org.robolectric.Shadows.shadowOf(base as android.app.Application).broadcastIntents.last()
            assertTrue(com.nickbether.pebbletasker.tasker.base.ConditionQueryDemand.reconcile(base,intent))
            val runner=com.nickbether.pebbletasker.tasker.event.bridgeerror.BridgeErrorRunner()
            val input=com.joaomgcd.taskerpluginlibrary.input.TaskerInput(com.nickbether.pebbletasker.tasker.event.bridgeerror.BridgeErrorFilter())
            assertTrue(runner.getSatisfiedCondition(base,input,delivery) is com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied)
            access.clear(base)
            assertTrue(runner.getSatisfiedCondition(base,input,delivery) is com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied)
            verify(exactly=1) {svc.handshake(any())}
        } finally {unmockkObject(BridgeClient.Companion,com.nickbether.pebbletasker.ui.BridgeWarning)}
    }
}
