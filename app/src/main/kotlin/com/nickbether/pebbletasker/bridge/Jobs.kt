package com.nickbether.pebbletasker.bridge

import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.tasker.ErrCodes

/**
 * Screenshots and log dumps outlast the IPC deadline: the command returns a `job_id` and the result
 * arrives later as a `system`/`job.done` event addressed only to this package.
 */
object Jobs {
    const val EVENT_TYPE = "job.done"

    /** The finished job, as reported by its `job.done` event. */
    data class Done(
        val jobId: String,
        val command: String?,
        val ok: Boolean,
        val error: String?,
        val uri: String?,
        val mime: String?,
        val width: Int?,
        val height: Int?,
        val data: Map<String, String>,
    ) {
        companion object {
            fun from(e: CachedEvent) = Done(
                jobId = e.str("job_id").orEmpty(),
                command = e.str("command"),
                ok = e.str("status") == "ok",
                error = e.str("error"),
                uri = e.str("uri"),
                mime = e.str("mime"),
                width = e.int("width"),
                height = e.int("height"),
                data = e.data,
            )
        }
    }

    fun matches(event: CachedEvent, jobId: String): Boolean =
        event.type == EVENT_TYPE && jobId.isNotEmpty() && event.str("job_id") == jobId

    /** Result of starting a job; [Started.jobId] is then awaited with [await]. */
    sealed class Start {
        data class Started(val jobId: String, val mark: Long) : Start()
        data class Refused(val err: BridgeResult.Err) : Start()
    }

    suspend fun start(port: BridgePort, command: String, watch: String?, args: Map<String, String> = emptyMap()): Start {
        // Mark before sending: a fast job can finish before execute() returns.
        val mark = port.eventMark()
        return when (val r = port.execute(command, watch, args)) {
            is BridgeResult.Err -> Start.Refused(r)
            is BridgeResult.Ok -> {
                val id = r.value["job_id"]?.trim().orEmpty()
                if (id.isEmpty()) Start.Refused(BridgeResult.Err(ErrCodes.INTERNAL, "Pebble did not return a job_id for $command"))
                else Start.Started(id, mark)
            }
        }
    }

    /** Waits for the matching `job.done`; null on timeout. */
    suspend fun await(port: BridgePort, started: Start.Started, timeoutMs: Long): Done? =
        port.awaitEvent(started.mark, timeoutMs) { matches(it, started.jobId) }?.let(Done::from)
}
