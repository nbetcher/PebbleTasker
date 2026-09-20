package com.nickbether.pebbletasker.bridge

import com.nickbether.pebbletasker.bridge.dto.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class BridgeListenerTest {
    private fun raw(seq:Long, token:String?="token", boot:String="boot") = BridgePluginJson.json.encodeToString(EventBatch.serializer(),
        EventBatch(bootId=boot,events=listOf(EventEnvelope(bootId=boot,seq=seq,ts=seq,type="watch.connected")),cursor=seq,subscriptionToken=token))
    @Test fun `no session old generation wrong token and wrong boot cannot enqueue`() {
        var active=false
        val listener=BridgeListener("boot","token",{active},{})
        listener.onEvents(raw(1)); assertTrue(listener.batches.tryReceive().isFailure)
        active=true
        listener.onEvents(raw(2,"old")); listener.onEvents(raw(3,null)); listener.onEvents(raw(4,boot="old"))
        assertTrue(listener.batches.tryReceive().isFailure)
        listener.onEvents(raw(5)); assertEquals(5L,listener.batches.tryReceive().getOrNull()!!.events.single().seq)
        active=false; listener.onEvents(raw(6)); assertTrue(listener.batches.tryReceive().isFailure)
    }
    @Test fun `callback order retained while registration waits for acknowledgment`() = runBlocking {
        val listener=BridgeListener("boot","token",{true},{})
        listener.onEvents(raw(1)); listener.onEvents(raw(2)); listener.onEvents(raw(3))
        assertEquals(listOf(1L,2L,3L),List(3) { listener.batches.receive().events.single().seq })
        listener.close()
    }
    @Test fun `goodbye closes only current registration and overflow fails closed`() {
        var active=false; var goodbyes=0
        val listener=BridgeListener("boot","token",{active},{goodbyes++})
        listener.onBridgeGoodbye(null); assertEquals(0,goodbyes)
        active=true
        repeat(129) { listener.onEvents(raw(it.toLong())) }
        assertEquals(1,goodbyes)
        listener.onEvents(raw(200)); assertTrue(listener.batches.tryReceive().isFailure)
    }
}
