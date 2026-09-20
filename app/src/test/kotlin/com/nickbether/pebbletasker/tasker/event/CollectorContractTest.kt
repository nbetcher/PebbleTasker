package com.nickbether.pebbletasker.tasker.event

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.joaomgcd.taskerpluginlibrary.runner.*
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import com.nickbether.pebbletasker.tasker.event.notifsent.*
import com.nickbether.pebbletasker.tasker.event.notifaction.*
import com.nickbether.pebbletasker.tasker.event.appchanged.*
import com.nickbether.pebbletasker.tasker.event.appmsg.*
import com.nickbether.pebbletasker.tasker.event.bridgeerror.*
import com.nickbether.pebbletasker.tasker.event.call.*
import com.nickbether.pebbletasker.tasker.event.firmware.*
import com.nickbether.pebbletasker.tasker.event.health.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wire fixtures from CollectorRegressionTest/NotificationCollector/PerWatchCollector/SystemEventCollector.
 * This suite exercises real runner filtering/output mapping. Collector execution is separately tested
 * in the Watch App suite; generated collector envelopes are checked by GeneratedCollectorContractTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[34])
class CollectorContractTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun event(type:String,vararg fields:Pair<String,String>)=CachedEvent(type,"test",1,1,watch=WatchRef("SERIAL","Pebble"),data=mapOf(*fields))
    @Test fun `notification content and package filters match actual producer fields`() {
        val e=event("notif.sent","pkg" to "pkg","title" to "title","text" to "body","redacted" to "false")
        val r=NotifSentRunner().evaluate(context,NotifSentFilter(pkg="pkg",textContains="body"),e,null) as TaskerPluginResultConditionSatisfied
        assertEquals("body",r.regular!!.pbText); assertEquals("title",r.regular!!.pbTitle); assertEquals("pkg",r.regular!!.pbPkg)
        assertTrue(NotifSentRunner().evaluate(context,NotifSentFilter(pkg="other"),e,null) is TaskerPluginResultConditionUnsatisfied)
    }
    @Test fun `redacted notification cannot match text or leak a producer mistake`() {
        val e=event("notif.sent","pkg" to "pkg","redacted" to "true","text" to "secret")
        assertTrue(NotifSentRunner().evaluate(context,NotifSentFilter(textContains="secret"),e,null) is TaskerPluginResultConditionUnsatisfied)
        val r=NotifSentRunner().evaluate(context,NotifSentFilter(),e,null) as TaskerPluginResultConditionSatisfied
        assertEquals("(redacted)",r.regular!!.pbText)
        assertFalse(r.regular!!.pbJson!!.contains("secret"))
    }
    @Test fun `notification standard and custom action IDs and package survive`() {
        for(action in listOf("dismiss","custom label")) {
            val e=event("notif.action","action" to action,"action_id" to "2","pkg" to "pkg")
            val r=NotifActionRunner().evaluate(context,NotifActionFilter(action=action,pkg="pkg"),e,null) as TaskerPluginResultConditionSatisfied
            assertEquals(action,r.regular!!.pbAction); assertEquals("2",r.regular!!.pbActionId)
        }
    }
    @Test fun `dictionary key filter handles unsigned keys numbers bytes and empty strings`() {
        val dict="""{"4294967295":"button","3":"00ff","4":42,"5":""}"""
        val e=event("appmsg.received","uuid" to "uuid","transaction_id" to "255","dict_json" to dict,"dict_types_json" to """{"3":"bytes","4":"int"}""")
        val r=AppMessageRunner().evaluate(context,AppMessageFilter(uuid="uuid",key="4294967295",serial="SERIAL"),e,null) as TaskerPluginResultConditionSatisfied
        assertArrayEquals(arrayOf("4294967295","3","4","5"),r.regular!!.pbKeys)
        assertArrayEquals(arrayOf("button","00ff","42",""),r.regular!!.pbValues)
        assertTrue(AppMessageRunner().evaluate(context,AppMessageFilter(uuid="uuid",serial="OTHER"),e,null) is TaskerPluginResultConditionUnsatisfied)
        assertTrue(AppMessageRunner().evaluate(context,AppMessageFilter(uuid="uuid",key="absent"),e,null) is TaskerPluginResultConditionUnsatisfied)
    }
    @Test fun `app and watchface filters read explicit producer type and name`() {
        for(type in listOf("watchapp","watchface")) {
            val e=event("apps.run_state","uuid" to "uuid","app_type" to type,"app_name" to "Name","previous_uuid" to "previous")
            val r=AppChangedRunner().evaluate(context,AppChangedFilter(uuid="uuid",appType=type),e,null) as TaskerPluginResultConditionSatisfied
            assertEquals("Name",r.regular!!.pbAppName);assertEquals(type,r.regular!!.pbAppType);assertEquals("previous",r.regular!!.pbPrevUuid)
        }
    }
    @Test fun `real bridge errors keep error type and message without consulting a newer gap`() {
        val e=event("system.error","error_type" to "failed_to_scan","message" to "scan failed")
        val r=BridgeErrorRunner().evaluate(context,BridgeErrorFilter("failed_to_scan"),e,null) as TaskerPluginResultConditionSatisfied
        assertEquals("failed_to_scan",r.regular!!.pbErrorType);assertEquals("scan failed",r.regular!!.pbErrorMsg)
    }
    @Test fun `firmware canonical lifecycle and progress match filters`() {
        for(status in listOf("idle","failed","waiting","in_progress","rebooting","unavailable")) {
            val r=FirmwareRunner().evaluate(context,FirmwareFilter(status),event("fw.status","status" to status,"progress" to "71"),null) as TaskerPluginResultConditionSatisfied
            assertEquals(status,r.regular!!.pbFwStatus); assertEquals("71",r.regular!!.pbProgress)
        }
    }
    @Test fun `caller and health outputs come from current canonical producer payloads`() {
        val call=CallRunner().evaluate(context,CallFilter("ringing"),event("calls.state","state" to "ringing","caller_name" to "Caller","number" to "123"),null) as TaskerPluginResultConditionSatisfied
        assertEquals("Caller",call.regular!!.pbCallerName)
        val health=HealthRunner().evaluate(context,HealthFilter(),event("health.updated","steps_today" to "1234","latest_hr" to "67"),null) as TaskerPluginResultConditionSatisfied
        assertEquals("1234",health.regular!!.pbStepsToday);assertEquals("67",health.regular!!.pbLatestHr)
    }
    @Test fun `all legacy persisted call states remain compatible`() {
        for ((old,now) in mapOf("RingingCall" to "ringing","DialingCall" to "dialing","ActiveCall" to "active","HoldingCall" to "holding","Ended" to "ended")) {
            assertTrue(CallRunner().evaluate(context,CallFilter(old),event("calls.state","state" to now),null) is TaskerPluginResultConditionSatisfied)
        }
    }
    @Test fun `body-only redaction preserves title while hiding body from all outputs`() {
        val e=event("notif.sent","pkg" to "pkg","title" to "Allowed title","text" to "secret","redacted" to "true","title_shared" to "true")
        val r=NotifSentRunner().evaluate(context,NotifSentFilter(),e,null) as TaskerPluginResultConditionSatisfied
        assertEquals("Allowed title",r.regular!!.pbTitle)
        assertEquals("(redacted)",r.regular!!.pbText)
        assertFalse(r.regular!!.pbJson!!.contains("secret"))
    }

}
