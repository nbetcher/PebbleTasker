package com.nickbether.pebbletasker.tasker.base

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.joaomgcd.taskerpluginlibrary.TaskerPluginConstants
import com.joaomgcd.taskerpluginlibrary.input.*
import com.joaomgcd.taskerpluginlibrary.runner.*
import com.nickbether.pebbletasker.bridge.*
import com.nickbether.pebbletasker.bridge.dto.*
import com.nickbether.pebbletasker.cache.*
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.event.connected.*
import com.nickbether.pebbletasker.tasker.event.disconnected.*
import com.nickbether.pebbletasker.tasker.event.appmsg.*
import com.nickbether.pebbletasker.tasker.event.bridgeerror.*
import com.nickbether.pebbletasker.tasker.state.StateRegistrations
import com.nickbether.pebbletasker.tasker.state.connected.*
import com.nickbether.pebbletasker.ui.BridgeWarning
import io.mockk.*
import net.dinglisch.android.tasker.TaskerPlugin
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DeliveryAndReadinessTest {
    private lateinit var context: Context
    private lateinit var client: BridgeClient
    private lateinit var cache: EventCache
    private val session = BridgeSession("token", "boot", 1, 0, "test", setOf("events.core"), Grants())
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        client = mockk(relaxed = true)
        cache = EventCache(context, writeSnapshot = {})
        cache.seedFromHandshake("boot", 0)
        mockkObject(BridgeClient.Companion, EventCache.Companion, BridgeWarning)
        every { BridgeClient.get(any()) } returns client
        every { EventCache.get(any()) } returns cache
        every { client.awaitReadyBlocking(any()) } returns BridgeResult.Ok(session)
        every { client.currentSession } returns session
        every { BridgeWarning.warnIfUsedWhileUnbridged(any()) } returns true
        ConditionAccess.clear(context)
        com.nickbether.pebbletasker.tasker.event.EventRouting.registerAll()
        StateRegistrations.registerAll()
    }
    @After fun cleanup() { unmockkAll() }
    private fun event(seq: Long, type: String = "watch.connected", watch: String = "A") = CachedEvent(type,"boot",seq,seq,"connectivity", WatchRef(watch,watch))
    private fun delivery(e: CachedEvent): EventDelivery {
        // Use the actual SDK serializer and decoder used by requestQuery/getUpdate, not a fake DTO.
        val bundle = TaskerInputInfos.fromInput(context, EventDelivery.from(e,cache.deliveryEpoch)).bundle
        return EventDelivery().also { TaskerInputInfos.fromBundle(context,it,bundle) }
    }
    @Test fun `same edge is independently delivered to two matching profiles`() {
        val update = delivery(event(1))
        val runner = ConnectedRunner()
        repeat(2) { assertTrue(runner.getSatisfiedCondition(context,TaskerInput(ConnectedFilter("A")),update) is TaskerPluginResultConditionSatisfied) }
        assertTrue(runner.getSatisfiedCondition(context,TaskerInput(ConnectedFilter("B")),update) is TaskerPluginResultConditionUnsatisfied)
    }
    @Test fun `old disconnect never becomes a later connect payload`() {
        val disconnected = delivery(event(1,"watch.disconnected"))
        val connected = delivery(event(2))
        cache.putBatch(listOf(EventEnvelope(bootId="boot",seq=2,ts=2,type="watch.connected",watch=WatchRef("B","B"))))
        assertTrue(DisconnectedRunner().getSatisfiedCondition(context,TaskerInput(DisconnectedFilter("A")),disconnected) is TaskerPluginResultConditionSatisfied)
        assertTrue(ConnectedRunner().getSatisfiedCondition(context,TaskerInput(ConnectedFilter("A")),connected) is TaskerPluginResultConditionSatisfied)
        assertTrue(DisconnectedRunner().getSatisfiedCondition(context,TaskerInput(DisconnectedFilter()),connected) is TaskerPluginResultConditionUnsatisfied)
    }
    @Test fun `handshake refresh queries only state activities`() {
        val before = shadowOf(context as Application).broadcastIntents.size
        EventRouter.requestQueryAll(context)
        val requests = shadowOf(context as Application).broadcastIntents.drop(before)
        assertTrue(requests.isNotEmpty())
        assertTrue(requests.all { it.getStringExtra(TaskerPluginConstants.EXTRA_ACTIVITY)!!.contains(".tasker.state.") })
    }
    @Test fun `SDK router broadcasts immutable per-event data and message IDs`() {
        val before = shadowOf(context as Application).broadcastIntents.size
        EventRouter.routeAll(context,listOf(event(1),event(2,watch="B")))
        val requests = shadowOf(context as Application).broadcastIntents.drop(before).filter { it.getStringExtra(TaskerPluginConstants.EXTRA_ACTIVITY) == ConnectedActivity::class.java.name }
        assertEquals(2,requests.size)
        assertTrue(requests.all { TaskerPlugin.Event.retrievePassThroughMessageID(it) != -1 })
        val events = requests.map { intent -> EventDelivery().also { TaskerInputInfos.fromBundle(context,it,TaskerPlugin.Event.retrievePassThroughData(intent)) }.event()!! }
        assertEquals(listOf("A","B"),events.map { it.watch!!.serial })
        assertEquals(listOf(1L,2L),events.map { it.seq })
    }
    @Test fun `ordered adapter uses actual SDK to evaluate immutable payload for independent profiles`() {
        val before = shadowOf(context as Application).broadcastIntents.size
        EventRouter.routeAll(context,listOf(event(1)))
        val request = shadowOf(context as Application).broadcastIntents.drop(before).single { it.getStringExtra(TaskerPluginConstants.EXTRA_ACTIVITY)==ConnectedActivity::class.java.name }
        repeat(2) {
            val inputs=TaskerInputInfos.fromInput(context,ConnectedFilter("A")).bundle
            inputs.putString(TaskerPluginConstants.EXTRA_ACTION_INPUT_CLASS,ConnectedFilter::class.java.name)
            inputs.putString(TaskerPluginConstants.EXTRA_ACTION_RUNNER_CLASS,ConnectedRunner::class.java.name)
            val query=Intent(request).setAction(TaskerPluginConstants.ACTION_QUERY_CONDITION)
                .setClass(context,com.joaomgcd.taskerpluginlibrary.condition.BroadcastReceiverCondition::class.java)
                .putExtra(TaskerPluginConstants.EXTRA_BUNDLE,inputs)
            val adapted = evaluateCondition(context, query)
            assertEquals(TaskerPluginConstants.RESULT_CONDITION_SATISFIED, adapted.first)
            assertNotNull(adapted.second.getBundle("net.dinglisch.android.tasker.extras.VARIABLES"))
        }
    }
    @Test fun `missing update cannot fire historical retained events`() {
        cache.put(EventEnvelope(bootId="boot",seq=1,ts=1,type="watch.connected"))
        assertTrue(ConnectedRunner().getSatisfiedCondition(context,TaskerInput(ConnectedFilter()),null) is TaskerPluginResultConditionUnsatisfied)
    }
    @Test fun `revocation expires already queued Tasker payload after reapproval`() {
        val update = delivery(event(1))
        cache.invalidateAuthority()
        assertTrue(ConnectedRunner().getSatisfiedCondition(context,TaskerInput(ConnectedFilter()),update) is TaskerPluginResultConditionUnsatisfied)
    }
    @Test fun `pending denied and unavailable conditions do not evaluate retained authorized content`() {
        val update = delivery(event(1))
        for (code in listOf(ErrCodes.CONSENT_PENDING,ErrCodes.ACCESS_DENIED,ErrCodes.BRIDGE_UNREACHABLE)) {
            every { client.awaitReadyBlocking(any()) } returns BridgeResult.Err(code,"decision-$code")
            assertTrue(ConnectedRunner().getSatisfiedCondition(context,TaskerInput(ConnectedFilter()),update) is TaskerPluginResultConditionUnknown)
            assertEquals("decision-$code",ConditionAccess.lastMessage(context))
        }
    }
    @Test fun `native diagnostic event reaches a condition profile without bridge access`() {
        every { client.awaitReadyBlocking(any()) } returns BridgeResult.Err(ErrCodes.ACCESS_DENIED,"Denied in the Pebble app","ACCESS_DENIED")
        val before = shadowOf(context as Application).broadcastIntents.size
        ConditionAccess.ready(context)
        val request = shadowOf(context as Application).broadcastIntents.drop(before).single { it.getStringExtra(TaskerPluginConstants.EXTRA_ACTIVITY) == BridgeErrorActivity::class.java.name }
        val update = EventDelivery().also { TaskerInputInfos.fromBundle(context,it,TaskerPlugin.Event.retrievePassThroughData(request)) }
        val result = BridgeErrorRunner().getSatisfiedCondition(context,TaskerInput(BridgeErrorFilter("ACCESS_DENIED")),update)
        assertEquals("Denied in the Pebble app",(result as TaskerPluginResultConditionSatisfied).regular!!.pbErrorMsg)
        ConditionAccess.ready(context)
        assertEquals(before+1,shadowOf(context as Application).broadcastIntents.size)
    }
    @Test fun `no-ID restored AppMessage query records desired subscription and reconciles denial`() {
        val uuid="12345678-1234-1234-1234-123456789abc"
        val bundle=TaskerInputInfos.fromInput(context,AppMessageFilter(uuid=uuid,serial="A")).bundle
        bundle.putString(TaskerPluginConstants.EXTRA_ACTION_INPUT_CLASS,AppMessageFilter::class.java.name)
        val intent=Intent(TaskerPluginConstants.ACTION_QUERY_CONDITION).putExtra(TaskerPluginConstants.EXTRA_BUNDLE,bundle)
        assertEquals(-1,TaskerPlugin.Event.retrievePassThroughMessageID(intent))
        every { client.awaitReadyBlocking(any()) } returns BridgeResult.Err(ErrCodes.ACCESS_DENIED,"Denied in the Pebble app")
        every { client.currentSession } returns null
        assertFalse(ConditionQueryDemand.reconcile(context,intent))
        assertTrue(context.getSharedPreferences("appmessage_subscriptions",0).getStringSet("desired",emptySet())!!.any { it.contains(uuid) })
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(2000) {
            while (ConditionAccess.lastMessage(context)==null) kotlinx.coroutines.delay(10)
        } }
        assertEquals("Denied in the Pebble app",ConditionAccess.lastMessage(context))
    }
    @Test fun `fresh and restored action both expose native denial through real sender`() {
        every { client.executeBlocking(any()) } returns BridgeResult.Err(ErrCodes.ACCESS_DENIED,"Denied in the Pebble app")
        val runner = com.nickbether.pebbletasker.tasker.action.ping.PingRunner()
        repeat(2) { assertTrue(runner.run(context,TaskerInput(com.nickbether.pebbletasker.tasker.action.ping.PingInput())) is TaskerPluginResultError) }
        verify(exactly = 2) { client.executeBlocking(any()) }
    }
    @Test fun `SDK failed-action completion carries authoritative pending and denied codes and messages`() {
        for ((code,message) in listOf(ErrCodes.ACCESS_DENIED to "Denied in the Pebble app",ErrCodes.CONSENT_PENDING to "Awaiting approval in the Pebble app")) {
            every { client.executeBlocking(any()) } returns BridgeResult.Err(code,message)
            val result=com.nickbether.pebbletasker.tasker.action.ping.PingRunner().run(context,TaskerInput(com.nickbether.pebbletasker.tasker.action.ping.PingInput()))
            val fire=Intent().putExtra("net.dinglisch.android.tasker.extras.COMPLETION_INTENT",Intent("test.COMPLETE").toUri(Intent.URI_INTENT_SCHEME))
            assertTrue(result.signalFinish(ArgsSignalFinish(context,fire)))
            val complete=shadowOf(context as Application).broadcastIntents.last { it.action=="test.COMPLETE" }
            val variables=complete.getBundleExtra("net.dinglisch.android.tasker.extras.VARIABLES")!!
            assertEquals(code.toString(),variables.getString("%err"))
            assertEquals(message,variables.getString("%errmsg"))
        }
    }
    @Test fun `typed AppMessage runner sends empty string and numeric string without flattening`() {
        val sent=slot<CommandEnvelope>()
        every { client.executeBlocking(capture(sent)) } returns BridgeResult.Ok(ResultEnvelope(ok=true,data=mapOf("acked" to "true")))
        val input=com.nickbether.pebbletasker.tasker.action.appmessage.AppMessageInput(uuid="12345678-1234-1234-1234-123456789abc",dictJson="""{"1":"","2":"0123"}""")
        val result=com.nickbether.pebbletasker.tasker.action.appmessage.AppMessageRunner().run(context,TaskerInput(input))
        assertTrue(result is TaskerPluginResultSucess)
        val dict=kotlinx.serialization.json.Json.parseToJsonElement(sent.captured.args.getValue("dict_json"))
        assertEquals(kotlinx.serialization.json.Json.parseToJsonElement(input.dictJson!!),dict)
        assertTrue(sent.captured.args.keys.none { it.startsWith("d.") })
    }
    @Test fun `measured ping and selected watchface outputs survive real action sender`() {
        every { client.executeBlocking(any()) } returns BridgeResult.Ok(ResultEnvelope(ok=true,data=mapOf("rtt_ms" to "42")))
        val ping=com.nickbether.pebbletasker.tasker.action.ping.PingRunner().run(context,TaskerInput(com.nickbether.pebbletasker.tasker.action.ping.PingInput())) as TaskerPluginResultSucess
        assertEquals("42",ping.regular!!.rttMs)
        every { client.executeBlocking(any()) } returns BridgeResult.Ok(ResultEnvelope(ok=true,data=mapOf("watchface" to "Face","serial" to "A")))
        val info=com.nickbether.pebbletasker.tasker.action.getinfo.GetInfoRunner().run(context,TaskerInput(com.nickbether.pebbletasker.tasker.action.getinfo.GetInfoInput("A"))) as TaskerPluginResultSucess
        assertEquals("Face",info.regular!!.watchface);assertEquals("A",info.regular!!.serial)
    }
    @Test fun `command and state gates require exact capability never broad command core`() {
        assertNotNull(FeatureSupport.reason(com.nickbether.pebbletasker.tasker.action.screenshot.ScreenshotRunner::class.java,setOf("commands.core")))
        assertNotNull(FeatureSupport.reason(com.nickbether.pebbletasker.tasker.state.dnd.S2DndRunner::class.java,session.capabilities))
        assertNull(FeatureSupport.reason(com.nickbether.pebbletasker.tasker.action.ping.PingRunner::class.java,setOf("command.system.ping")))
    }
}
