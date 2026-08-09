package com.nickbether.pebbletasker.tasker.base

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.log.PLog
import com.nickbether.pebbletasker.setup.SetupState
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.ui.BridgeWarning

/**
 * Base for ALL action plugins (FINAL DESIGN §2.3 / §4.2, FIX C5/C6).
 *
 * RESULT MODEL — success-with-ok=false:
 *   - Bridge SUCCESS and bridge-returned ERRORS (NOT_AUTHORIZED, INVALID_ARGS, ...) BOTH return
 *     TaskerPluginResultSucess(output). The output carries %pbl_ok / %pbl_err / %pbl_errmsg / %pbl_json,
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
     * result.data fields into named %pbl_* outputs and set %pbl_ok/%pbl_err/%pbl_errmsg/%pbl_json.
     */
    abstract fun buildOutput(input: TaskerInput<TInput>, result: CommandResult): TOutput

    @Suppress("UNCHECKED_CAST")
    final override fun run(context: Context, input: TaskerInput<TInput>): TaskerPluginResult<TOutput> {
        val name = this::class.simpleName
        PLog.d { "action[$name]: run" }
        // Never set up on this device (e.g. Tasker config restored onto a new phone with no Pebble-app
        // authorization): fail HARD so Tasker surfaces %err via its own notification — the plugin itself
        // may lack POST_NOTIFICATIONS, but Tasker doesn't. This is the restore-safety bubble-up.
        if (!SetupState.isSetupComplete(context)) {
            PLog.w { "action[$name]: NOT SET UP -> hard error (finish setup in the Pebble Tasker app)" }
            BridgeWarning.warnIfUsedWhileUnbridged(context)
            return TaskerPluginResultError(
                ErrCodes.NOT_SET_UP,
                context.getString(R.string.error_not_set_up),
            ) as TaskerPluginResult<TOutput>
        }
        // Warn (throttled notification) if this action is run while we're not bridged to the Pebble app.
        BridgeWarning.warnIfUsedWhileUnbridged(context)
        val bridgeResult = execute(context, input)
        when (bridgeResult) {
            is BridgeResult.Ok -> PLog.i { "action[$name]: ok (${bridgeResult.value.size} field(s))" }
            is BridgeResult.Err -> PLog.w {
                "action[$name]: err code=${bridgeResult.code} msg=${bridgeResult.message} " +
                    "hard=${isHardFailure(bridgeResult.code)}"
            }
        }
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
                    // Bridge-reported error: deliver it AS output so the task can branch on %pbl_ok.
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
            code == ErrCodes.INTERNAL ||
            code == ErrCodes.NOT_SET_UP
}
