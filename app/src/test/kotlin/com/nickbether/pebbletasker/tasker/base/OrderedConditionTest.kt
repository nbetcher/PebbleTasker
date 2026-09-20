package com.nickbether.pebbletasker.tasker.base

import android.app.Application
import android.content.*
import android.os.*
import androidx.test.core.app.ApplicationProvider
import com.joaomgcd.taskerpluginlibrary.TaskerPluginConstants
import io.mockk.*
import net.dinglisch.android.tasker.TaskerPlugin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class, sdk=[34])
class OrderedConditionTest {
    @Test fun serviceAndBroadcastShareOrderedEvaluationAndCompletion() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val entered=CountDownLatch(1); val release=CountDownLatch(1); val firstDone=CountDownLatch(1)
        val seen=Collections.synchronizedList(mutableListOf<Int>())
        mockkObject(ConditionQueryDemand)
        every {ConditionQueryDemand.reconcile(any(),any())} answers {
            if(secondArg<Intent>().getIntExtra("order",0)==1) {entered.countDown(); check(release.await(4,TimeUnit.SECONDS))}
            true
        }
        val controller=Robolectric.buildService(ConditionQueryService::class.java).create()
        val receiver=ConditionQueryReceiver()
        // Register the production receiver explicitly: Robolectric's manifest receiver registry
        // includes the dependency manifest even when AGP replaced its discovery filters.
        app.registerReceiver(receiver,IntentFilter("review.ordered.query"),Context.RECEIVER_NOT_EXPORTED)
        try {
            val callback=object:ResultReceiver(null){
                override fun onReceiveResult(code:Int,extras:Bundle?) {seen+=1; firstDone.countDown()}
            }
            // Stub only the Binder result-receiver extraction: Robolectric does not relay
            // a parcelled ResultReceiver back to its original process callback.
            mockkStatic(TaskerPlugin.Condition::class)
            every {TaskerPlugin.Condition.getResultReceiver(any())} returns callback
            val first=Intent().putExtra("order",1)
            controller.get().onStartCommand(first,0,1)
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            app.sendOrderedBroadcast(Intent("review.ordered.query").putExtra("order",2),null,object:BroadcastReceiver(){
                override fun onReceive(context:Context,intent:Intent){seen+=2}
            },null,0,null,null)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(seen.isEmpty())
            release.countDown()
            assertTrue(firstDone.await(3,TimeUnit.SECONDS))
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
            while(seen.size<2 && System.nanoTime()<until){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)}
            assertEquals(listOf(1,2),seen.toList())
        } finally { release.countDown();app.unregisterReceiver(receiver);controller.destroy();unmockkObject(ConditionQueryDemand);unmockkStatic(TaskerPlugin.Condition::class) }
    }
    @Test fun expiredQueryBudgetNeverStartsAnotherReadinessWait() {
        ConditionQueryBudget.deadline.set(SystemClock.elapsedRealtime()-1)
        try {assertEquals(0L,ConditionQueryBudget.remaining(4000))}
        finally {ConditionQueryBudget.deadline.remove()}
    }
}
