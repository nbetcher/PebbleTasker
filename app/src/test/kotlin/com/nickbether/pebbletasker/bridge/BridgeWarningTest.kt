package com.nickbether.pebbletasker.bridge

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.ui.BridgeWarning
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class BridgeWarningTest {
    @Test fun `reason direction and exact denial text are retained`() {
        assertEquals(BridgeClient.DENIED_MESSAGE,BridgeWarning.messageFor(BridgeClient.ConnectionStatus.Error(ErrCodes.ACCESS_DENIED,"remote")))
        assertTrue(BridgeWarning.messageFor(BridgeClient.ConnectionStatus.Error(ErrCodes.CERT_MISMATCH,"remote"))!!.contains("plugin's signature"))
        assertTrue(BridgeWarning.messageFor(BridgeClient.ConnectionStatus.CertMismatch("host-sha"))!!.contains("Pebble app fingerprint"))
        assertEquals("Master switch off",BridgeWarning.messageFor(BridgeClient.ConnectionStatus.NotAuthorized("Master switch off")))
    }
    @Test fun `blocked notification retains diagnostic and denial bypasses previous pending reason`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val manager=context.getSystemService(NotificationManager::class.java)
        val state=MutableStateFlow<BridgeClient.ConnectionStatus>(BridgeClient.ConnectionStatus.ConsentPending("pending"))
        val client=mockk<BridgeClient>()
        every {client.status} returns state
        every {client.retryHandshake()} just Runs
        mockkObject(BridgeClient.Companion)
        every {BridgeClient.get(any())} returns client
        try {
            BridgeWarning.cancel(context)
            shadowOf(manager).setNotificationsEnabled(false)
            assertTrue(BridgeWarning.warnIfUsedWhileUnbridged(context))
            assertNotNull(BridgeWarning.lastDiagnostic(context))
            assertTrue(shadowOf(manager).allNotifications.isEmpty())
            state.value=BridgeClient.ConnectionStatus.Error(ErrCodes.ACCESS_DENIED,BridgeClient.DENIED_MESSAGE)
            shadowOf(manager).setNotificationsEnabled(true)
            BridgeWarning.warnIfUsedWhileUnbridged(context)
            assertEquals(BridgeClient.DENIED_MESSAGE,BridgeWarning.lastDiagnostic(context))
            assertEquals(1,shadowOf(manager).allNotifications.size)
        } finally {unmockkObject(BridgeClient.Companion)}
    }
}
