package com.nickbether.pebbletasker.tasker.action.common

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.bridge.AndroidBridgePort
import com.nickbether.pebbletasker.bridge.BridgePort
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.JobFiles
import com.nickbether.pebbletasker.bridge.Jobs
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars
import kotlinx.coroutines.runBlocking

/** Outputs shared by the job actions (Take Screenshot, Gather Logs). */
open class JobActionOutput(
    @get:TaskerOutputVariable(PbVars.FILE) @field:TaskerInputField("pb_file") open var file: String? = null,
    @get:TaskerOutputVariable(PbVars.URI) @field:TaskerInputField("pb_uri") open var uri: String? = null,
    @get:TaskerOutputVariable(PbVars.MIME) @field:TaskerInputField("pb_mime") open var mime: String? = null,
    @get:TaskerOutputVariable(PbVars.JOB_ID) @field:TaskerInputField("pb_job_id") open var jobId: String? = null,
) : ActionResultOutput()

/** The settings every job action shares. */
interface JobInput {
    val serial: String?
    val timeoutS: String?
    val save: String?
    val folder: String?
}

/**
 * Starts a job, waits for its `job.done` (matched by job_id), then copies the file into shared
 * storage at once (the host deletes it after an hour).
 *
 * Result keys: job_id, command, status, mime, width?, height?, source_uri, and when saved file + uri.
 */
object JobAction {
    suspend fun run(
        context: Context,
        port: BridgePort,
        command: String,
        watch: String?,
        timeoutMs: Long,
        kind: JobFiles.Kind,
        save: Boolean,
        folder: String?,
        saver: (ByteArray, String?) -> JobFiles.Saved = { bytes, mime -> JobFiles.save(context, bytes, kind, mime, folder) },
    ): BridgeResult<Map<String, String>> {
        val started = when (val s = Jobs.start(port, command, watch)) {
            is Jobs.Start.Refused -> return s.err
            is Jobs.Start.Started -> s
        }
        val done = Jobs.await(port, started, timeoutMs)
            ?: return BridgeResult.Err(ErrCodes.TIMEOUT,
                "No result for job ${started.jobId} within ${timeoutMs / 1000} s. It may still finish; its file is kept for an hour.", "TIMEOUT")
        val out = linkedMapOf("job_id" to done.jobId, "status" to if (done.ok) "ok" else "failed")
        done.command?.let { out["command"] = it }
        done.mime?.let { out["mime"] = it }
        done.width?.let { out["width"] = it.toString() }
        done.height?.let { out["height"] = it.toString() }
        if (!done.ok) return BridgeResult.Err(ErrCodes.JOB_FAILED, "Job ${done.jobId} failed" + (done.error?.let { ": $it" } ?: ""), "JOB_FAILED")
        val source = done.uri ?: return BridgeResult.Err(ErrCodes.JOB_FAILED, "Job ${done.jobId} finished without a file", "JOB_FAILED")
        out["source_uri"] = source
        if (!save) { out["uri"] = source; return BridgeResult.Ok(out) }
        val bytes = port.read(source)
            ?: return BridgeResult.Err(ErrCodes.JOB_FAILED, "Could not read the job file at $source", "JOB_FAILED")
        val saved = runCatching { saver(bytes, done.mime) }.getOrElse {
            return BridgeResult.Err(ErrCodes.JOB_FAILED, "Could not save the job file: ${it.message}", "JOB_FAILED")
        }
        out["file"] = saved.path
        out["uri"] = saved.uri
        out["size"] = bytes.size.toString()
        return BridgeResult.Ok(out)
    }
}

abstract class JobActionRunner<TInput : JobInput, TOutput : JobActionOutput> : PebbleActionRunner<TInput, TOutput>() {
    abstract val command: String
    abstract val kind: JobFiles.Kind
    abstract val defaultTimeoutS: Int
    override val commandType: String? get() = command
    abstract fun newOutput(): TOutput
    open fun fill(output: TOutput, data: Map<String, String>) {}

    override fun execute(context: Context, input: TaskerInput<TInput>): BridgeResult<Map<String, String>> {
        val i = input.regular
        val timeoutS: Int
        val save: Boolean
        try {
            timeoutS = Args.int("timeout", i.timeoutS, defaultTimeoutS, 5, JobTimeouts.MAX_S)
            save = Args.bool(i.save, true)
        } catch (e: Args.Invalid) {
            return BridgeResult.Err(ErrCodes.INVALID_ARGS, e.message ?: "Invalid arguments", "INVALID_ARGS")
        }
        return runBlocking {
            JobAction.run(context, AndroidBridgePort(context), command, i.serial, timeoutS * 1000L, kind, save, i.folder)
        }
    }

    override fun buildOutput(input: TaskerInput<TInput>, result: CommandResult): TOutput = newOutput().also { o ->
        if (result.ok) {
            o.file = result.data["file"]; o.uri = result.data["uri"]; o.mime = result.data["mime"]; o.jobId = result.data["job_id"]
            fill(o, result.data)
        }
        o.fillResult(result)
    }

    // A timed-out job may still finish: report it as output, not a hard failure.
    override fun isHardFailure(code: Int) = code != ErrCodes.TIMEOUT && code != ErrCodes.JOB_FAILED && super.isHardFailure(code)
}

object JobTimeouts {
    const val MAX_S = 3_000
    /**
     * Tasker waits this long for the action: the job wait plus readiness and the command itself.
     * A %variable is resolved only at run time, so it gets the longest allowed wait.
     */
    fun taskerSeconds(timeoutS: String?, defaultS: Int): Int {
        val raw = timeoutS?.trim().orEmpty()
        val wait = when {
            raw.isEmpty() -> defaultS
            raw.contains('%') -> MAX_S
            else -> raw.toIntOrNull() ?: defaultS
        }
        return (wait.coerceIn(5, MAX_S) + 45).coerceAtMost(3_599)
    }
}

abstract class JobActionHelper<TInput : JobInput, TOutput : JobActionOutput, TRunner : JobActionRunner<TInput, TOutput>>(
    config: TaskerPluginConfig<TInput>,
    private val defaultTimeoutS: Int,
) : WatchCommandHelper<TInput, TOutput, TRunner>(config) {
    override val timeoutSeconds: Int
        get() = JobTimeouts.taskerSeconds(runCatching { config.inputForTasker.regular.timeoutS }.getOrNull(), defaultTimeoutS)
}
