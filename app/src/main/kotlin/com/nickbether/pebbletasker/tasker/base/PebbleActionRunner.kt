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

/** All actions resolve current readiness first. Consent failures use native Tasker errors, preserving
 * the authoritative code/message. Other bridge command failures retain the pbl_ok output contract.
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
        // BridgeClient.execute owns the complete readiness + IPC deadline. No setup flag may
        // replace its authoritative pending/denied result after a Tasker restore.
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
        code == ErrCodes.ACCESS_DENIED ||
            code == ErrCodes.NOT_AUTHORIZED ||
            code == ErrCodes.CONSENT_PENDING ||
            code == ErrCodes.CERT_MISMATCH ||
            code == ErrCodes.TIMEOUT ||
            code == ErrCodes.BRIDGE_UNREACHABLE ||
            code == ErrCodes.INTERNAL ||
            code == ErrCodes.NOT_SET_UP
}
