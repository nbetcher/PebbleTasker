package com.nickbether.pebbletasker.bridge

import com.nickbether.pebbletasker.bridge.dto.*
import com.nickbether.pebbletasker.tasker.ErrCodes
import org.junit.Assert.*
import org.junit.Test

class BridgeCodecTest {
    private val hello = BridgeHello(bootId="boot", capabilities=listOf("events.core"), grants=Grants(), latestSeq=0, appVersion="1", clientToken="token")
    private fun raw(value: BridgeHello) = BridgePluginJson.json.encodeToString(BridgeHello.serializer(), value)
    @Test fun `valid protocol one accepted`() { assertTrue(BridgeCodec.decodeHello(raw(hello)).isOk) }
    @Test fun `rejects each incompatible handshake discriminator`() {
        listOf(hello.copy(v=2), hello.copy(kind="state"), hello.copy(protocolVersion=999)).forEach {
            assertEquals(ErrCodes.UNSUPPORTED_VERSION, (BridgeCodec.decodeHello(raw(it)) as BridgeResult.Err).code)
            assertThrows(IllegalArgumentException::class.java) { BridgeSession.from(it) }
        }
    }
    @Test fun `rejects blank session identity and invalid cursor`() {
        listOf(hello.copy(bootId=" "), hello.copy(clientToken=""), hello.copy(appVersion=""), hello.copy(latestSeq=-1)).forEach {
            assertFalse(BridgeCodec.decodeHello(raw(it)).isOk)
        }
    }
    @Test fun `denial code and reason survive wire decoding`() {
        val result = BridgeCodec.decodeHello("""{"ok":false,"error":{"code":"ACCESS_DENIED","message":"Denied in the Pebble app"}}""") as BridgeResult.Err
        assertEquals(ErrCodes.ACCESS_DENIED, result.code)
        assertEquals("ACCESS_DENIED", result.bridgeCode)
        assertEquals(BridgeClient.DENIED_MESSAGE, result.message)
    }
    @Test fun `remote signer rejection never becomes local retrust status`() {
        assertTrue(BridgeClient.ConnectionStatus.fromError(BridgeResult.err(ErrCodes.CERT_MISMATCH,"plugin signer rejected")) is BridgeClient.ConnectionStatus.Error)
    }
    @Test fun `batch optional fields preserve wire compatibility`() {
        val old = BridgeCodec.decodeBatch("""{"bootId":"boot","events":[]}""").valueOrNull()!!
        assertNull(old.cursor); assertNull(old.subscriptionToken); assertFalse(old.historyLost)
        val new = BridgeCodec.decodeBatch("""{"bootId":"boot","events":[],"cursor":12,"historyLost":true,"subscriptionToken":"registered"}""").valueOrNull()!!
        assertEquals(12L,new.cursor); assertTrue(new.historyLost); assertEquals("registered",new.subscriptionToken)
    }
    @Test fun `wrong state and event envelopes fail closed`() {
        assertFalse(BridgeCodec.decodeState("""{"v":9,"data":{"watches":[]}}""").isOk)
        assertFalse(BridgeCodec.decodeBatch("""{"kind":"result","bootId":"boot"}""").isOk)
        assertFalse(BridgeCodec.decodeBatch("""{"bootId":"boot","cursor":-1}""").isOk)
    }
}
