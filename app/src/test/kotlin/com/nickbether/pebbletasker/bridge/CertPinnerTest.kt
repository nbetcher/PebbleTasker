package com.nickbether.pebbletasker.bridge

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import com.nickbether.pebbletasker.setup.SetupState
import com.nickbether.pebbletasker.bridge.dto.CommandEnvelope
import coredevices.coreapp.automation.IBridgeService
import io.mockk.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[27])
class CertPinnerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { CertPinner.PinStore.durable(context).clear(); SetupState.setAccessDenied(context,false) }
    @Suppress("DEPRECATION") private fun install(signature:String) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName=CertPinner.BRIDGE_PACKAGE
            signatures=arrayOf(Signature(signature.toByteArray()))
        })
    }
    @Test fun `first use pins durably and replacement needs new approval`() {
        install("first")
        val first=CertPinner(context)
        assertEquals(CertPinner.PinResult.PINNED,first.verify())
        assertNotNull(CertPinner.PinStore.durable(context).read())
        assertEquals(CertPinner.PinResult.OK,CertPinner(context).verify())
        install("replacement")
        assertEquals(CertPinner.PinResult.MISMATCH,CertPinner(context).verify())
    }
    @Test fun `denial survives new client and restored setup does not override it`() {
        SetupState.markSetupComplete(context)
        SetupState.setAccessDenied(context,true)
        val client=BridgeClient(context)
        assertEquals(BridgeClient.DENIED_MESSAGE,(client.awaitReadyBlocking() as BridgeResult.Err).message)
        repeat(10) { assertEquals(9,(client.awaitReadyBlocking() as BridgeResult.Err).code) }
        client.stop()
        assertTrue(SetupState.isAccessDenied(context))
    }
    @Test fun `command builder preserves explicit empty text`() {
        assertEquals("",CommandSender(context).command("notification.send",args=mapOf("body" to "")).args["body"])
    }
    @Test fun `fresh cold action and state reach pending approval with production first-use pin`() = runBlocking {
        install("installed-host")
        val service=mockk<IBridgeService>(relaxed=true)
        val binder=mockk<android.os.IBinder>()
        every {binder.queryLocalInterface(any())} returns service
        every {service.handshake(any())} returns """{"ok":false,"error":{"code":"CONSENT_PENDING","message":"Review in Pebble"}}"""
        val host=object:ContextWrapper(context) {
            override fun bindService(intent:Intent,conn:ServiceConnection,flags:Int):Boolean {conn.onServiceConnected(null,binder);return true}
            override fun unbindService(conn:ServiceConnection) {}
        }
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val workers=BinderCalls()
        val client=BridgeClient(host,CertPinner(host),scope,workers)
        try {
            assertEquals(2,(client.execute(CommandEnvelope(type="system.ping")) as BridgeResult.Err).code)
            assertEquals(2,(client.getState() as BridgeResult.Err).code)
            assertEquals(CertPinner.PinResult.OK,CertPinner(context).verify())
            verify(exactly=2) {service.handshake(any())}
        } finally {client.stop();scope.cancel();workers.close()}
    }
}
