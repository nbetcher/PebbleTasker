package com.nickbether.pebbletasker.bridge

import androidx.test.core.app.ApplicationProvider
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.EventTap
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.action.common.JobAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JobsTest {
    private var seq = 0L
    private fun ev(type: String, data: Map<String, String>) = CachedEvent(type, "boot", ++seq, 0, "system", null, data)
    private fun done(id: String, status: String = "ok", uri: String? = "content://host/$id") =
        ev("job.done", buildMap { put("job_id", id); put("status", status); put("command", "watch.screenshot"); put("mime", "image/png"); uri?.let { put("uri", it) } })

    private class Port(val tap: EventTap, val onExecute: (String) -> BridgeResult<Map<String, String>>) : BridgePort {
        val files = mutableMapOf<String, ByteArray>()
        override val session: BridgeSession? = null
        override suspend fun ready() = BridgeResult.err(ErrCodes.INTERNAL, "unused")
        override suspend fun execute(type: String, watch: String?, args: Map<String, String>) = onExecute(type)
        override suspend fun watches(): BridgeResult<List<WatchRef>> = BridgeResult.Ok(emptyList())
        override fun eventMark() = tap.mark()
        override suspend fun awaitEvent(after: Long, timeoutMs: Long, predicate: (CachedEvent) -> Boolean) = tap.await(after, timeoutMs, predicate)
        override suspend fun read(uri: String) = files[uri]
    }

    @Test fun `job_done matches only its own job id`() {
        assertTrue(Jobs.matches(done("a"), "a"))
        assertFalse(Jobs.matches(done("b"), "a"))
        assertFalse(Jobs.matches(ev("watch.pref", mapOf("job_id" to "a")), "a"))
        assertFalse(Jobs.matches(done(""), ""))
    }

    @Test fun `result that arrives before execute returns is still matched`() = runTest {
        val tap = EventTap()
        val port = Port(tap) { tap.publish(listOf(done("other"), done("j1"))); BridgeResult.Ok(mapOf("job_id" to "j1")) }
        val started = Jobs.start(port, "watch.screenshot", null) as Jobs.Start.Started
        val result = Jobs.await(port, started, 1_000)!!
        assertEquals("j1", result.jobId)
        assertTrue(result.ok)
    }

    @Test fun `stale results from before the command are ignored and later ones matched`() = runTest {
        val tap = EventTap()
        tap.publish(listOf(done("j1", status = "failed")))
        val port = Port(tap) { BridgeResult.Ok(mapOf("job_id" to "j1")) }
        val started = Jobs.start(port, "watch.screenshot", null) as Jobs.Start.Started
        launch { kotlinx.coroutines.delay(500); tap.publish(listOf(done("j1"))) }
        assertTrue(Jobs.await(port, started, 1_000)!!.ok)
    }

    @Test fun `timeout, failure and refusal map to errors`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tap = EventTap()
        val silent = Port(tap) { BridgeResult.Ok(mapOf("job_id" to "j9")) }
        val timeout = JobAction.run(context, silent, "watch.screenshot", null, 2_000, JobFiles.Kind.SCREENSHOT, true, null) as BridgeResult.Err
        assertEquals(ErrCodes.TIMEOUT, timeout.code)

        val failing = Port(tap) { tap.publish(listOf(done("j2", status = "failed").copy(data = mapOf("job_id" to "j2", "status" to "failed", "error" to "watch refused")))); BridgeResult.Ok(mapOf("job_id" to "j2")) }
        val failed = JobAction.run(context, failing, "watch.screenshot", null, 2_000, JobFiles.Kind.SCREENSHOT, true, null) as BridgeResult.Err
        assertEquals(ErrCodes.JOB_FAILED, failed.code)
        assertTrue(failed.message.contains("watch refused"))
        assertEquals("a failed job keeps a code, not free text, in the wire-code slot", "JOB_FAILED", failed.bridgeCode)

        val refused = Port(tap) { BridgeResult.Err(ErrCodes.CATEGORY_DISABLED, "system off", "CATEGORY_DISABLED") }
        val r = JobAction.run(context, refused, "watch.screenshot", null, 2_000, JobFiles.Kind.SCREENSHOT, true, null) as BridgeResult.Err
        assertEquals("CATEGORY_DISABLED", r.bridgeCode)

        val noId = Port(tap) { BridgeResult.Ok(emptyMap()) }
        assertTrue(Jobs.start(noId, "watch.screenshot", null) is Jobs.Start.Refused)
    }

    @Test fun `finished job is copied at once and paths are output`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tap = EventTap()
        val port = Port(tap) { tap.publish(listOf(done("j3"))); BridgeResult.Ok(mapOf("job_id" to "j3")) }
        port.files["content://host/j3"] = byteArrayOf(1, 2, 3)
        var saved: ByteArray? = null
        val r = JobAction.run(context, port, "watch.screenshot", null, 2_000, JobFiles.Kind.SCREENSHOT, true, "Shots",
            saver = { bytes, mime -> saved = bytes; assertEquals("image/png", mime); JobFiles.Saved("/sdcard/Pictures/Shots/x.png", "content://media/1") }) as BridgeResult.Ok
        assertEquals(3, saved!!.size)
        assertEquals("/sdcard/Pictures/Shots/x.png", r.value["file"])
        assertEquals("content://media/1", r.value["uri"])
        assertEquals("content://host/j3", r.value["source_uri"])

        val noSave = JobAction.run(context, Port(tap) { tap.publish(listOf(done("j4"))); BridgeResult.Ok(mapOf("job_id" to "j4")) },
            "watch.screenshot", null, 2_000, JobFiles.Kind.SCREENSHOT, false, null) as BridgeResult.Ok
        assertEquals("content://host/j4", noSave.value["uri"])
        assertNull(noSave.value["file"])
    }

    @Test fun `Tasker wait covers the job timeout, including one set by a variable`() {
        val max = com.nickbether.pebbletasker.tasker.action.common.JobTimeouts.MAX_S
        assertEquals(60 + 45, com.nickbether.pebbletasker.tasker.action.common.JobTimeouts.taskerSeconds(null, 60))
        assertEquals(600 + 45, com.nickbether.pebbletasker.tasker.action.common.JobTimeouts.taskerSeconds("600", 60))
        assertEquals(max + 45, com.nickbether.pebbletasker.tasker.action.common.JobTimeouts.taskerSeconds("%wait", 60))
        assertEquals(60 + 45, com.nickbether.pebbletasker.tasker.action.common.JobTimeouts.taskerSeconds("abc", 60))
    }

    @Test fun `folder names are confined below the shared directory`() {
        assertEquals("PebbleTasker", JobFiles.sanitizeFolder(null))
        assertEquals("PebbleTasker", JobFiles.sanitizeFolder("../.."))
        assertEquals("a/b", JobFiles.sanitizeFolder("/a/../b/"))
        assertEquals("My_Shots", JobFiles.sanitizeFolder("My*Shots"))
        assertEquals("txt", JobFiles.extensionFor("text/plain; charset=utf-8", JobFiles.Kind.LOGS))
        assertEquals("png", JobFiles.extensionFor(null, JobFiles.Kind.SCREENSHOT))
    }
}
