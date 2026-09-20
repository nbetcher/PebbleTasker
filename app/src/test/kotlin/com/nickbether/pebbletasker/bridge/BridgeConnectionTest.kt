package com.nickbether.pebbletasker.bridge

import android.content.*
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import coredevices.coreapp.automation.IBridgeService
import io.mockk.mockk
import io.mockk.every
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class BridgeConnectionTest {
    class Host : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val connections = mutableListOf<ServiceConnection>()
        var unbinds=0
        override fun bindService(intent:Intent, conn:ServiceConnection, flags:Int):Boolean { connections += conn; return true }
        override fun unbindService(conn:ServiceConnection) { unbinds++ }
    }
    @Test fun `accepted bind has one watchdog and demand does not multiply binding`() {
        val host=Host(); var dropped=0; var retries=0
        val connection=BridgeConnection(host, {}, { dropped++ }, { retries++ }, bindTimeoutMs=100)
        repeat(50) { connection.ensureBound() }
        assertEquals(1,host.connections.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(101))
        assertEquals(BridgeConnection.State.UNBOUND,connection.state)
        assertEquals(1,dropped); assertEquals(1,retries); assertEquals(1,host.unbinds)
        connection.ensureBound(); assertEquals(2,host.connections.size)
        connection.unbind()
    }
    @Test fun `binding death releases registration and obsolete callback cannot resurrect it`() {
        val host=Host(); var connected=0
        val connection=BridgeConnection(host,{ connected++ },{}, {})
        connection.ensureBound()
        val old=host.connections.single()
        old.onBindingDied(null)
        assertEquals(1,host.unbinds)
        connection.ensureBound()
        val binder=mockk<android.os.IBinder>()
        every { binder.queryLocalInterface(any()) } returns mockk<IBridgeService>()
        old.onServiceConnected(null,binder)
        assertEquals(0,connected); assertEquals(BridgeConnection.State.BINDING,connection.state)
        connection.unbind()
    }
    @Test fun `null binding and stop cancel watchdog without duplicate drops`() {
        val host=Host(); var dropped=0
        val connection=BridgeConnection(host,{}, {dropped++}, {},bindTimeoutMs=50)
        connection.ensureBound(); host.connections.single().onNullBinding(null)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(1,dropped); assertEquals(1,host.unbinds)
        connection.ensureBound(); connection.unbind()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(1,dropped)
    }
}
