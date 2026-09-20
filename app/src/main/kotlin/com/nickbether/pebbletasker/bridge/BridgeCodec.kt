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
    fun decodeHello(raw: String?): BridgeResult<BridgeHello> {
        val result = decode(raw) { json.decodeFromString(BridgeHello.serializer(), it) }
        if (result is BridgeResult.Ok) {
            val hello = result.value
            if (hello.v != 1 || hello.kind != "hello" || hello.protocolVersion != 1)
                return BridgeResult.err(ErrCodes.UNSUPPORTED_VERSION, "Update both Pebble apps: unsupported bridge protocol")
            if (hello.bootId.isBlank() || hello.clientToken.isBlank() || hello.appVersion.isBlank() || hello.latestSeq < 0)
                return BridgeResult.err(ErrCodes.INTERNAL, "Invalid bridge identity")
        }
        return result
    }

    /** getState() -> StateResult, or error envelope (NOT_AUTHORIZED on master-off session). */
    fun decodeState(raw: String?): BridgeResult<StateResult> {
        val result = decode(raw) { json.decodeFromString(StateResult.serializer(), it) }
        if (result is BridgeResult.Ok && (result.value.v != 1 || result.value.kind != "state"))
            return BridgeResult.err(ErrCodes.UNSUPPORTED_VERSION, "Unsupported state envelope")
        return result
    }

    /** getEventsSince() -> EventBatch, or error envelope. */
    fun decodeBatch(raw: String?): BridgeResult<EventBatch> {
        val result = decode(raw) { json.decodeFromString(EventBatch.serializer(), it) }
        if (result is BridgeResult.Ok) {
            val batch = result.value
            if (batch.v != 1 || batch.kind != "batch")
                return BridgeResult.err(ErrCodes.UNSUPPORTED_VERSION, "Unsupported event envelope")
            if (batch.bootId.isBlank() || (batch.cursor != null && batch.cursor < 0) ||
                batch.events.any { it.v != 1 || it.kind != "event" || it.bootId != batch.bootId || it.seq < 0 })
                return BridgeResult.err(ErrCodes.INTERNAL, "Invalid event batch identity")
        }
        return result
    }

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
