package com.nickbether.pebbletasker.bridge

import com.nickbether.pebbletasker.bridge.dto.BridgeHello
import com.nickbether.pebbletasker.bridge.dto.CommandEnvelope
import com.nickbether.pebbletasker.bridge.dto.EventBatch
import com.nickbether.pebbletasker.bridge.dto.ResultEnvelope
import com.nickbether.pebbletasker.bridge.dto.StateResult
import com.nickbether.pebbletasker.tasker.ErrCodes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ENVELOPE-AWARE decoding for every String-returning AIDL call (FINAL DESIGN §0 FIX A3/A4).
 *
 * The bridge can return EITHER its nominal success type (BridgeHello / StateResult / EventBatch)
 * OR an error envelope { "ok": false, "error": { code, message } } from the SAME method
 * (handshake, getState, getEventsSince, execute can all return NOT_AUTHORIZED / CONSENT_PENDING /
 * CERT_MISMATCH / CATEGORY_DISABLED instead of their nominal type).
 *
 * ResultEnvelope.ok is non-null with NO default, so trial-decoding a BridgeHello/StateResult into a
 * ResultEnvelope throws MissingFieldException, and vice-versa. We therefore PEEK at the parsed JSON:
 *   - if a top-level "ok" key is present -> it's a ResultEnvelope; if ok==false -> Err.
 *   - else -> decode the nominal success type.
 *
 * We do NOT discriminate on "kind": BridgeHello.kind and ClientHello.kind are both "hello", and a
 * success ResultEnvelope (kind "result") theoretically exists too. The "ok" key is the only safe
 * discriminator.
 *
 * Any structural/parse failure (null, blank, malformed JSON, wrong shape) -> Err(INTERNAL) so the
 * caller never sees a partially-decoded object.
 */
internal object BridgeCodec {

    private val json: Json get() = BridgePluginJson.json

    /** True if the JSON is an object carrying a top-level boolean "ok" == false. */
    private fun JsonObject.isErrorEnvelope(): Boolean {
        val ok = this["ok"] ?: return false
        return runCatching { !ok.jsonPrimitive.boolean }.getOrDefault(false)
    }

    /** True if the JSON is an object carrying a top-level "ok" key at all (success OR error). */
    private fun JsonObject.hasOk(): Boolean = this["ok"] != null

    private fun parseObject(raw: String?): JsonObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
    }

    /**
     * Decode a nominal-or-error payload. On {ok:false} -> Err from the error body. On a payload that
     * is NOT an error envelope -> [decodeSuccess]. On parse failure -> Err(INTERNAL).
     */
    private inline fun <T> decode(
        raw: String?,
        crossinline decodeSuccess: (String) -> T,
    ): BridgeResult<T> {
        val obj = parseObject(raw)
            ?: return BridgeResult.err(ErrCodes.INTERNAL, "bridge returned empty/invalid JSON")
        if (obj.isErrorEnvelope()) {
            val env = runCatching { json.decodeFromString(ResultEnvelope.serializer(), raw!!) }.getOrNull()
            return BridgeResult.fromError(env?.error)
        }
        return runCatching { BridgeResult.Ok(decodeSuccess(raw!!)) }
            .getOrElse { t -> BridgeResult.err(ErrCodes.INTERNAL, t.message ?: "decode failed") }
    }

    /** handshake() -> BridgeHello, or error envelope (CONSENT_PENDING / NOT_AUTHORIZED / ...). */
    fun decodeHello(raw: String?): BridgeResult<BridgeHello> =
        decode(raw) { json.decodeFromString(BridgeHello.serializer(), it) }

    /** getState() -> StateResult, or error envelope (NOT_AUTHORIZED on master-off session). */
    fun decodeState(raw: String?): BridgeResult<StateResult> =
        decode(raw) { json.decodeFromString(StateResult.serializer(), it) }

    /** getEventsSince() -> EventBatch, or error envelope. */
    fun decodeBatch(raw: String?): BridgeResult<EventBatch> =
        decode(raw) { json.decodeFromString(EventBatch.serializer(), it) }

    /**
     * execute() -> ResultEnvelope (success OR error). Unlike the others, the SUCCESS type here is
     * itself a ResultEnvelope with ok==true (carrying optional `data`). So we decode the whole thing
     * and branch on `ok`: ok==true -> Ok(envelope), ok==false -> Err.
     */
    fun decodeResult(raw: String?): BridgeResult<ResultEnvelope> {
        val obj = parseObject(raw)
            ?: return BridgeResult.err(ErrCodes.INTERNAL, "bridge returned empty/invalid JSON")
        if (!obj.hasOk()) {
            return BridgeResult.err(ErrCodes.INTERNAL, "execute() returned a non-result payload")
        }
        val env = runCatching { json.decodeFromString(ResultEnvelope.serializer(), raw!!) }
            .getOrElse { t -> return BridgeResult.err(ErrCodes.INTERNAL, t.message ?: "decode failed") }
        return if (env.ok) BridgeResult.Ok(env) else BridgeResult.fromError(env.error)
    }

    // --- encoders ---

    fun encodeHello(hello: com.nickbether.pebbletasker.bridge.dto.ClientHello): String =
        json.encodeToString(com.nickbether.pebbletasker.bridge.dto.ClientHello.serializer(), hello)

    fun encodeCommand(cmd: CommandEnvelope): String =
        json.encodeToString(CommandEnvelope.serializer(), cmd)
}
