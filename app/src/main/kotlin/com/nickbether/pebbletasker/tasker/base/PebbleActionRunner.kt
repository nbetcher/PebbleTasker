package com.nickbether.pebbletasker.tasker.base

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.tasker.ErrCodes

/**
 * Base for ALL action plugins (FINAL DESIGN §2.3 / §4.2, FIX C5/C6).
 *
 * RESULT MODEL — success-with-ok=false:
 *   - Bridge SUCCESS and bridge-returned ERRORS (NOT_AUTHORIZED, INVALID_ARGS, ...) BOTH return
 *     TaskerPluginResultSucess(output). The output carries %pb_ok / %pb_err / %pb_errmsg / %pb_json,
 *     because outputs are NOT delivered on the TaskerPluginResultError path — so error detail would
 *     be invisible to the user's task otherwise.
 *   - HARD infrastructure failure ONLY (not bound, timeout, RemoteException) returns
 *     TaskerPluginResultError(code, message), which auto-sets Tasker's %err / %errmsg.
 *
 * Note the class name is the library's misspelling: TaskerPluginResultSucess.
 *
 * Subclasses implement [execute] returning a [BridgeResult] plus a function that builds the typed
 * output for both branches. The library already wraps run() in try/catch -> TaskerPluginResultError,
 * so an uncaught throw becomes a hard error automatically.
 *
 * @param TInput  the @TaskerInputRoot input class
 * @param TOutput the @TaskerInputRoot @TaskerOutputObject output class
 */
abstract class PebbleActionRunner<TInput : Any, TOutput : Any> :
    TaskerPluginRunnerAction<TInput, TOutput>() {

    /**
     * Carries the data an action needs to build its output object on either branch.
     *
     * @param ok        whether the bridge call succeeded.
     * @param errCode   ErrCodes int (0 / unset when ok).
     * @param errMsg    human-readable error (empty when ok).
     * @param data      bridge ResultEnvelope.data on success (flat string map), else empty.
     */
    data class CommandResult(
        val ok: Boolean,
        val errCode: Int,
        val errMsg: String,
        val data: Map<String, String>,
    )

    /**
     * Run the command. Implementations call CommandSender/BridgeClient and return a [BridgeResult].
     * Throw (or return Err with a hard-failure code) only for genuine infra failures.
     */
    abstract fun execute(context: Context, input: TaskerInput<TInput>): BridgeResult<Map<String, String>>

    /**
     * Build the typed output object from the normalized [CommandResult]. Implementations map
     * result.data fields into named %pb_* outputs and set %pb_ok/%pb_err/%pb_errmsg/%pb_json.
     */
    abstract fun buildOutput(input: TaskerInput<TInput>, result: CommandResult): TOutput

    @Suppress("UNCHECKED_CAST")
    final override fun run(context: Context, input: TaskerInput<TInput>): TaskerPluginResult<TOutput> {
        val bridgeResult = execute(context, input)
        return when (bridgeResult) {
            is BridgeResult.Ok -> {
                val out = buildOutput(
                    input,
                    CommandResult(ok = true, errCode = 0, errMsg = "", data = bridgeResult.value),
                )
                TaskerPluginResultSucess(out)
            }
            is BridgeResult.Err -> {
                if (isHardFailure(bridgeResult.code)) {
                    // Infra failure: %err / %errmsg via the error path (no output delivered).
                    TaskerPluginResultError(bridgeResult.code, bridgeResult.message.ifEmpty { "error" }) as TaskerPluginResult<TOutput>
                } else {
                    // Bridge-reported error: deliver it AS output so the task can branch on %pb_ok.
                    val out = buildOutput(
                        input,
                        CommandResult(
                            ok = false,
                            errCode = bridgeResult.code,
                            errMsg = bridgeResult.message,
                            data = emptyMap(),
                        ),
                    )
                    TaskerPluginResultSucess(out)
                }
            }
        }
    }

    /** Codes that warrant Tasker's hard-failure %err (infra), vs. the success-with-ok=false model. */
    protected open fun isHardFailure(code: Int): Boolean =
        code == ErrCodes.TIMEOUT ||
            code == ErrCodes.BRIDGE_UNREACHABLE ||
            code == ErrCodes.INTERNAL
}
