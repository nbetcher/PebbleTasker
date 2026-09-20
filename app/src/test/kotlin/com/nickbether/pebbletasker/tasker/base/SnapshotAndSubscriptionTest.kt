package com.nickbether.pebbletasker.tasker.base

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.joaomgcd.taskerpluginlibrary.TaskerPluginConstants
import com.joaomgcd.taskerpluginlibrary.input.*
import com.joaomgcd.taskerpluginlibrary.runner.*
import com.nickbether.pebbletasker.bridge.*
import com.nickbether.pebbletasker.bridge.dto.*
import com.nickbether.pebbletasker.cache.EventCache
import com.nickbether.pebbletasker.tasker.event.appmsg.*
import com.nickbether.pebbletasker.tasker.state.devconn.*
import com.nickbether.pebbletasker.tasker.state.firmware.*
import com.nickbether.pebbletasker.tasker.state.bluetooth.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[34])
class SnapshotAndSubscriptionTest {
    private lateinit var context:Context
    private lateinit var client:BridgeClient
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        client=mockk(relaxed=true)
        mockkObject(BridgeClient.Companion,EventCache.Companion)
        every { BridgeClient.get(any()) } returns client
        val cache=EventCache(context,writeSnapshot={});cache.seedFromHandshake("boot",0)
        every { EventCache.get(any()) } returns cache
        every { client.currentSession } returns BridgeSession(java.util.UUID.randomUUID().toString(),"boot",1,0,"test",setOf("appmessages.replace_subscriptions","command.appmessage.subscribe","state.dev","state.firmware","state.bluetooth"),Grants())
        context.getSharedPreferences("appmessage_subscriptions",0).edit().clear().commit()
    }
    @After fun cleanup(){unmockkAll()}
    @Test fun `developer snapshot means any active watch and does not require an event`() {
        every { client.getStateBlocking() } returns BridgeResult.Ok(StateResult(data=StateData(watches=listOf(WatchRef("A","A",devEnabled=true),WatchRef("B","B",devEnabled=false)))))
        assertTrue(S3DevConnectionRunner().evaluate(context,S3DevConnectionInput()) is TaskerPluginResultConditionSatisfied)
        every { client.getStateBlocking() } returns BridgeResult.Ok(StateResult(data=StateData(watches=listOf(WatchRef("B","B",devEnabled=false)))))
        assertTrue(S3DevConnectionRunner().evaluate(context,S3DevConnectionInput()) is TaskerPluginResultConditionUnsatisfied)
    }
    @Test fun `firmware snapshots isolate watches and every active phase`() {
        for(phase in listOf("waiting","in_progress","rebooting")) {
            every { client.getStateBlocking() } returns BridgeResult.Ok(StateResult(data=StateData(watches=listOf(WatchRef("A","A",fwStatus=phase,fwProgress=25),WatchRef("B","B",fwStatus="idle")))))
            val runner=S5FirmwareUpdatingRunner()
            assertTrue(runner.evaluate(context,S5FirmwareUpdatingInput("A")) is TaskerPluginResultConditionSatisfied)
            assertTrue(runner.evaluate(context,S5FirmwareUpdatingInput("B")) is TaskerPluginResultConditionUnsatisfied)
            assertTrue(runner.evaluate(context,S5FirmwareUpdatingInput()) is TaskerPluginResultConditionSatisfied)
        }
    }
    @Test fun `bluetooth reads current snapshot including false and permission-masked null`() {
        every { client.getStateBlocking() } returns BridgeResult.Ok(StateResult(data=StateData(bluetoothEnabled=true)))
        assertTrue(S6BluetoothRunner().evaluate(context,S6BluetoothInput()) is TaskerPluginResultConditionSatisfied)
        every { client.getStateBlocking() } returns BridgeResult.Ok(StateResult(data=StateData(bluetoothEnabled=false)))
        assertTrue(S6BluetoothRunner().evaluate(context,S6BluetoothInput()) is TaskerPluginResultConditionUnsatisfied)
        every { client.getStateBlocking() } returns BridgeResult.Ok(StateResult(data=StateData()))
        assertTrue(S6BluetoothRunner().evaluate(context,S6BluetoothInput()) is TaskerPluginResultConditionUnknown)
    }
    @Test fun `snapshot errors never expose an earlier state`() {
        every { client.getStateBlocking() } returns BridgeResult.Err(9,"Denied in the Pebble app")
        assertTrue(S3DevConnectionRunner().evaluate(context,S3DevConnectionInput()) is TaskerPluginResultConditionUnknown)
        assertTrue(S5FirmwareUpdatingRunner().evaluate(context,S5FirmwareUpdatingInput()) is TaskerPluginResultConditionUnknown)
        assertTrue(S6BluetoothRunner().evaluate(context,S6BluetoothInput()) is TaskerPluginResultConditionUnknown)
    }
    @Test fun `observe is default and Tasker-only ACK is explicit through persisted replay`() = runBlocking {
        val commands=mutableListOf<CommandEnvelope>()
        coEvery { client.execute(capture(commands)) } returns BridgeResult.Ok(ResultEnvelope(ok=true))
        val uuid="12345678-1234-1234-1234-123456789abc"
        AppMessageSubscriptions.remember(context,uuid,"A",subscriptionId="profile-1")
        AppMessageSubscriptions.restore(context)
        assertEquals("observe",org.json.JSONArray(commands.last().args["subscriptions_json"]).getJSONObject(0).getString("ownership"))
        AppMessageSubscriptions.forget(context, AppMessageFilter(uuid=uuid,serial="A",ownership="observe",subscriptionId="profile-1"))
        AppMessageSubscriptions.remember(context,uuid,"A","tasker","profile-1")
        AppMessageSubscriptions.restore(context)
        assertEquals("tasker",org.json.JSONArray(commands.last().args["subscriptions_json"]).getJSONObject(0).getString("ownership"))
        AppMessageSubscriptions.forget(context, AppMessageFilter(uuid=uuid,serial="A",ownership="tasker",subscriptionId="profile-1"))
        AppMessageSubscriptions.remember(context,uuid,"A","observe","profile-1")
        AppMessageSubscriptions.restore(context)
        assertEquals("observe",org.json.JSONArray(commands.last().args["subscriptions_json"]).getJSONObject(0).getString("ownership"))
    }
    @Test fun `two profiles and watches restore independently without order-dependent ACK ownership`() = runBlocking {
        val commands=mutableListOf<CommandEnvelope>()
        coEvery { client.execute(capture(commands)) } returns BridgeResult.Ok(ResultEnvelope(ok=true))
        val uuid="12345678-1234-1234-1234-123456789abc"
        AppMessageSubscriptions.remember(context,uuid,"A","tasker","1")
        AppMessageSubscriptions.remember(context,uuid,"A","observe","2")
        AppMessageSubscriptions.remember(context,uuid,"B","observe","3")
        AppMessageSubscriptions.restore(context)
        assertEquals(1,commands.size)
        val entries=org.json.JSONArray(commands.single().args["subscriptions_json"])
        assertEquals(2,entries.length())
        assertEquals("tasker",entries.getJSONObject(0).getString("ownership"))
        assertEquals("observe",entries.getJSONObject(1).getString("ownership"))
    }
    @Test fun `Tasker input backup roundtrip retains explicit ownership and profile identity`() {
        val input=AppMessageFilter(ownership="tasker",subscriptionId="profile",serial="A",uuid="uuid",key="1")
        val bundle=TaskerInputInfos.fromInput(context,input).bundle
        val restored=AppMessageFilter().also{TaskerInputInfos.fromBundle(context,it,bundle)}
        assertEquals("tasker",restored.ownership);assertEquals("profile",restored.subscriptionId);assertEquals("A",restored.serial)
        val legacy=AppMessageFilter().also{TaskerInputInfos.fromBundle(context,it,TaskerInputInfos.fromInput(context,AppMessageFilter(uuid="uuid")).bundle)}
        assertEquals("observe",legacy.ownership)
    }
    @Test fun `denied client does not replay remembered subscriptions`() = runBlocking {
        val uuid="12345678-1234-1234-1234-123456789abc"
        AppMessageSubscriptions.remember(context,uuid,"A","tasker")
        every { client.currentSession } returns null
        AppMessageSubscriptions.restore(context)
        coVerify(exactly=0){client.execute(any())}
    }
    @Test fun `manifest discovery routes no-ID queries through readiness adapters`() {
        val intent=Intent(TaskerPluginConstants.ACTION_QUERY_CONDITION).setPackage(context.packageName)
        val receivers=context.packageManager.queryBroadcastReceivers(intent,0).map{it.activityInfo.name}
        val services=context.packageManager.queryIntentServices(intent,0).map{it.serviceInfo.name}
        assertEquals(listOf(ConditionQueryReceiver::class.java.name),receivers)
        assertEquals(listOf(ConditionQueryService::class.java.name),services)
    }
    @Test fun `editing profile replaces complete owner snapshot and failed replacement retries`() = runBlocking {
        val commands=mutableListOf<CommandEnvelope>()
        coEvery {client.execute(capture(commands))} returns BridgeResult.Ok(ResultEnvelope(ok=true))
        val first="00000000-0000-0000-0000-000000000001"
        val second="00000000-0000-0000-0000-000000000002"
        AppMessageSubscriptions.remember(context,first,"A","tasker","profile")
        assertTrue(AppMessageSubscriptions.restore(context))
        AppMessageSubscriptions.forget(context, AppMessageFilter(uuid=first,serial="A",ownership="tasker",subscriptionId="profile"))
        AppMessageSubscriptions.remember(context,second,"B","observe","profile")
        coEvery {client.execute(capture(commands))} returns BridgeResult.Err(22,"retry")
        assertFalse(AppMessageSubscriptions.restore(context))
        coEvery {client.execute(capture(commands))} returns BridgeResult.Ok(ResultEnvelope(ok=true))
        assertTrue(AppMessageSubscriptions.restore(context))
        assertEquals(3,commands.size)
        val replacement=org.json.JSONArray(commands.last().args["subscriptions_json"])
        assertEquals(1,replacement.length());assertEquals(second,replacement.getJSONObject(0).getString("uuid"))
        AppMessageSubscriptions.clear(context)
        assertTrue(AppMessageSubscriptions.restore(context))
        assertEquals("[]",commands.last().args["subscriptions_json"])
    }

    @Test fun `deferred watches retry until the complete desired snapshot is applied`() = runBlocking {
        val commands = mutableListOf<CommandEnvelope>()
        AppMessageSubscriptions.remember(context, "00000000-0000-0000-0000-000000000001", "missing")
        coEvery { client.execute(capture(commands)) } returns BridgeResult.Ok(
            ResultEnvelope(ok = true, data = mapOf("deferred_watches" to "[\"missing\"]")))
        assertTrue(AppMessageSubscriptions.restore(context))
        assertTrue(AppMessageSubscriptions.restore(context))
        assertEquals(2, commands.size)
        coEvery { client.execute(capture(commands)) } returns BridgeResult.Ok(
            ResultEnvelope(ok = true, data = mapOf("deferred_watches" to "[]")))
        assertTrue(AppMessageSubscriptions.restore(context))
        assertTrue(AppMessageSubscriptions.restore(context))
        assertEquals(3, commands.size)
    }
    @Test fun `copied ids preserve independent apps watches and ownership on every query`() = runBlocking {
        val commands=mutableListOf<CommandEnvelope>()
        coEvery {client.execute(capture(commands))} returns BridgeResult.Ok(ResultEnvelope(ok=true))
        val a="00000000-0000-0000-0000-000000000001"
        val b="00000000-0000-0000-0000-000000000002"
        repeat(3) {
            AppMessageSubscriptions.remember(context,a,"A","tasker","copied")
            AppMessageSubscriptions.remember(context,b,"B","observe","copied")
            AppMessageSubscriptions.remember(context,a,"A","observe","copied")
        }
        assertTrue(AppMessageSubscriptions.restore(context))
        val desired=org.json.JSONArray(commands.last().args["subscriptions_json"])
        assertEquals(2,desired.length())
        assertEquals("tasker",desired.getJSONObject(0).getString("ownership"))
        AppMessageSubscriptions.forget(context,AppMessageFilter("tasker","copied","A",a))
        assertTrue(AppMessageSubscriptions.restore(context))
        val edited=org.json.JSONArray(commands.last().args["subscriptions_json"])
        assertEquals(2,edited.length())
        assertEquals("observe",edited.getJSONObject(0).getString("ownership"))
        assertEquals(b,edited.getJSONObject(1).getString("uuid"))
    }
}
