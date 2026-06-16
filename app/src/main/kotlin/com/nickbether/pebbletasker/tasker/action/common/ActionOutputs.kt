package com.nickbether.pebbletasker.tasker.action.common

import com.nickbether.pebbletasker.bridge.dto.ResultEnvelope
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import org.json.JSONObject

/**
 * Shared helpers for ACTION plugin runners (FINAL DESIGN §2.3).
 *
 * Every action's output object carries the result block (%pb_ok / %pb_err / %pb_errmsg / %pb_json)
 * plus per-action named outputs pulled from [ResultEnvelope.data]. These helpers centralise the
 * normalisation so each plugin's Runner stays a thin mapping:
 *   - [PebbleActionRunner.execute] sends the command and returns BridgeResult<Map> (the data map).
 *   - [PebbleActionRunner.buildOutput] maps the normalised [PebbleActionRunner.CommandResult] into
 *     the typed output, using [jsonBlob] for %pb_json and [CommandResult.data] lookups for scalars.
 */
object ActionOutputs {

    /**
     * Build the canonical %pb_json blob for an action result. Includes ok + (on failure) err/errmsg,
     * and every key in the bridge data map. Tasker 5.12+ reads `%pb_json.<key>`.
     */
    fun jsonBlob(result: PebbleActionRunner.CommandResult): String {
        val o = JSONObject()
        o.put("ok", result.ok)
        if (!result.ok) {
            o.put("err", result.errCode)
            o.put("errmsg", result.errMsg)
        }
        for ((k, v) in result.data) o.put(k, v)
        return o.toString()
    }

    /** "true"/"false" string for an [PebbleActionRunner.CommandResult.ok], for the %pb_ok scalar. */
    fun okStr(result: PebbleActionRunner.CommandResult): String = result.ok.toString()

    /** %pb_err scalar: empty when ok, else the int code as a string. */
    fun errStr(result: PebbleActionRunner.CommandResult): String =
        if (result.ok) "" else result.errCode.toString()

    /** %pb_errmsg scalar: empty when ok, else the message. */
    fun errMsgStr(result: PebbleActionRunner.CommandResult): String =
        if (result.ok) "" else result.errMsg

    /** Read a data key, returning null if absent/blank (so the output stays unset). */
    fun data(result: PebbleActionRunner.CommandResult, key: String): String? =
        result.data[key]?.ifBlank { null }
}
