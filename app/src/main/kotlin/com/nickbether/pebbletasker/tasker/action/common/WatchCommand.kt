package com.nickbether.pebbletasker.tasker.action.common

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * Result block shared by the watch-control actions: %pbl_ok, %pbl_err, %pbl_errmsg, %pbl_err_code and
 * %pbl_json. The library discovers inherited getters (Class.getMethods), as with BaseEventOutput.
 */
open class ActionResultOutput(
    @get:TaskerOutputVariable(PbVars.JSON)
    @field:TaskerInputField("pb_json")
    open var pbJson: String? = null,
    @get:TaskerOutputVariable(PbVars.OK)
    @field:TaskerInputField("pb_ok")
    open var pbOk: String? = null,
    @get:TaskerOutputVariable(PbVars.ERR)
    @field:TaskerInputField("pb_err")
    open var pbErr: String? = null,
    @get:TaskerOutputVariable(PbVars.ERRMSG)
    @field:TaskerInputField("pb_errmsg")
    open var pbErrmsg: String? = null,
    @get:TaskerOutputVariable(PbVars.ERR_CODE)
    @field:TaskerInputField("pb_err_code")
    open var pbErrCode: String? = null,
) {
    fun fillResult(result: PebbleActionRunner.CommandResult) {
        pbJson = ActionOutputs.jsonBlob(result)
        pbOk = ActionOutputs.okStr(result)
        pbErr = ActionOutputs.errStr(result)
        pbErrmsg = ActionOutputs.errMsgStr(result)
        pbErrCode = ActionOutputs.wireCodeStr(result)
    }
}

/** Argument parsing shared by runners; failures become INVALID_ARGS before anything is sent. */
object Args {
    class Invalid(message: String) : Exception(message)

    fun int(name: String, raw: String?, default: Int, min: Int, max: Int): Int {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return default
        val n = v.toIntOrNull() ?: throw Invalid("$name must be a whole number, got \"$v\"")
        if (n < min || n > max) throw Invalid("$name must be between $min and $max, got $n")
        return n
    }

    fun choice(name: String, raw: String?, allowed: List<String>, default: String? = null): String {
        val v = raw?.trim()?.lowercase().orEmpty()
        if (v.isEmpty()) return default ?: throw Invalid("$name is required (${allowed.joinToString("/")})")
        if (v !in allowed) throw Invalid("$name must be one of ${allowed.joinToString("/")}, got \"$v\"")
        return v
    }

    fun bool(raw: String?, default: Boolean): Boolean = when (raw?.trim()?.lowercase()) {
        null, "" -> default
        "true", "on", "1", "yes" -> true
        "false", "off", "0", "no" -> false
        else -> throw Invalid("Expected true or false, got \"$raw\"")
    }

    fun required(name: String, raw: String?): String =
        raw?.trim()?.ifEmpty { null } ?: throw Invalid("$name is required")
}

/**
 * A runner that sends one command: validates inputs into args, sends, and copies result keys into
 * its output. Local validation failures are delivered as %pbl_ok=false with INVALID_ARGS.
 */
abstract class WatchCommandRunner<TInput : Any, TOutput : ActionResultOutput> : PebbleActionRunner<TInput, TOutput>() {
    abstract val command: String
    override val commandType: String? get() = command
    abstract fun watchOf(input: TInput): String?
    /** Command arguments; throw [Args.Invalid] to refuse locally. */
    open fun args(input: TInput): Map<String, String> = emptyMap()
    abstract fun newOutput(): TOutput
    open fun fill(output: TOutput, data: Map<String, String>) {}

    override fun execute(context: Context, input: TaskerInput<TInput>): BridgeResult<Map<String, String>> {
        val args = try { args(input.regular) } catch (e: Args.Invalid) {
            return BridgeResult.Err(ErrCodes.INVALID_ARGS, e.message ?: "Invalid arguments", "INVALID_ARGS")
        }
        return ActionSend.send(context, command, watch = watchOf(input.regular), args = args)
    }

    override fun buildOutput(input: TaskerInput<TInput>, result: CommandResult): TOutput =
        newOutput().also { if (result.ok) fill(it, result.data); it.fillResult(result) }
}

/** Helper whose blurb is a fixed label plus the watch selector. */
abstract class WatchCommandHelper<TInput : Any, TOutput : Any, TRunner : PebbleActionRunner<TInput, TOutput>>(
    config: TaskerPluginConfig<TInput>,
) : ActionHelper<TInput, TOutput, TRunner>(config) {
    protected fun withWatch(label: String, serial: String?): String =
        serial?.trim()?.takeIf { it.isNotEmpty() }?.let { "$label: $it" } ?: "$label: active watch"
}

/** Blank -> null. */
fun String?.orNullIfBlank(): String? = this?.trim()?.ifEmpty { null }
