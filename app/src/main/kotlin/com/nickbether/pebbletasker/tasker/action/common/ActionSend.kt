package com.nickbether.pebbletasker.tasker.action.common

import android.content.Context
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender
import com.nickbether.pebbletasker.bridge.dto.CommandEnvelope

/**
 * Shared command-send seam for every action Runner (FINAL DESIGN §2.3 / §3.4).
 *
 * A Runner's [com.nickbether.pebbletasker.tasker.base.PebbleActionRunner.execute] builds a
 * [CommandEnvelope] and calls [send], which:
 *   - dispatches it via [CommandSender.sendBlocking] (runner threads are NOT coroutines), which
 *     applies the `commands.core` capability gate + the 9s plugin timeout + the RemoteException
 *     backstop (old bridge with no execute() -> UNSUPPORTED_COMMAND) inside [BridgeClient];
 *   - on bridge SUCCESS, unwraps the ResultEnvelope into its flat `data` map (the runner base then
 *     wraps it as CommandResult(ok=true));
 *   - on bridge ERROR (NOT_AUTHORIZED / INVALID_ARGS / RATE_LIMITED / UNSUPPORTED_COMMAND / timeout
 *     / unreachable), passes the BridgeResult.Err straight through (the runner base decides whether
 *     it is a soft error -> %pbl_ok=false output, or a hard infra error -> Tasker %err/%errmsg).
 *
 * Keeping this in one place means every action maps the bridge result identically.
 */
object ActionSend {

    /** Dispatch [cmd] and reduce a SUCCESS ResultEnvelope to its data map. Errors pass through. */
    fun send(context: Context, cmd: CommandEnvelope): BridgeResult<Map<String, String>> =
        when (val r = CommandSender(context.applicationContext).sendBlocking(cmd)) {
            is BridgeResult.Ok -> BridgeResult.Ok(r.value.data ?: emptyMap())
            is BridgeResult.Err -> r
        }

    /** Convenience: build + send in one call. */
    fun send(
        context: Context,
        type: String,
        watch: String? = null,
        args: Map<String, String> = emptyMap(),
    ): BridgeResult<Map<String, String>> =
        send(context, CommandSender(context.applicationContext).command(type, watch, args))
}
