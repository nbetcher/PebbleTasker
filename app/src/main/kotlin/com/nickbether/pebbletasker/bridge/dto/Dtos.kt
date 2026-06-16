package com.nickbether.pebbletasker.bridge.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Plugin-side kotlinx.serialization mirrors of the bridge's Contract.kt / EventEnvelope.kt.
 *
 * These are SEPARATE from the bridge's own @Serializable classes on purpose: the plugin is an
 * independently-built, independently-signed app and must not depend on the bridge's commonMain
 * module. The wire shapes here are byte-compatible with the bridge contract verified against:
 *   coredevices/coreapp/automation/Contract.kt
 *   coredevices/coreapp/automation/events/EventEnvelope.kt
 *
 * All decoding goes through BridgeJson (ignoreUnknownKeys = true) so the bridge can append fields
 * without breaking us, and so an older bridge that omits future fields still decodes.
 *
 * Field defaults mirror the bridge EXACTLY. Where the bridge field is non-null with no default
 * (BridgeHello.bootId/capabilities/grants/latestSeq/appVersion/clientToken, ResultEnvelope.ok,
 * EventBatch.bootId/events, StateResult/StateData, WatchRef.serial/name) we keep it required so a
 * malformed/wrong-type payload fails loudly in BridgeCodec rather than silently producing garbage.
 */

/** Client -> bridge identification at handshake. Mirrors ClientHello. */
@Serializable
data class ClientHello(
    val v: Int = 1,
    val kind: String = "hello",
    val clientLabel: String = "",
    val clientProtocol: Int = 1,
    val wants: List<String> = emptyList(),
)

/** Bridge -> client handshake response. Mirrors BridgeHello. */
@Serializable
data class BridgeHello(
    val v: Int = 1,
    val kind: String = "hello",
    val bootId: String,
    val protocolVersion: Int = 1,
    val capabilities: List<String>,
    val grants: Grants,
    val latestSeq: Long,
    val appVersion: String,
    val clientToken: String,
)

/**
 * Grants block inside BridgeHello. The bridge's Grants shape is not in the files mirrored here, so
 * this is intentionally lenient: every field defaulted, unknown keys ignored. `categories`, `tier`
 * and `contentRedacted` are the fields the plugin reads (E5 redaction gate, command tiering).
 */
@Serializable
data class Grants(
    val categories: List<String> = emptyList(),
    val tier: String? = null,
    val contentRedacted: Boolean = true,
)

@Serializable
data class ErrorBody(val code: String, val message: String = "")

/**
 * Generic ok/error result. Mirrors ResultEnvelope.
 *
 * NOTE: `ok` is non-null with NO default in the bridge contract. BridgeCodec discriminates on the
 * presence of the "ok" JSON key BEFORE decoding into this class, so we never trial-decode a
 * BridgeHello/StateResult into a ResultEnvelope (that would throw MissingFieldException).
 *
 * `data` / `reqId` are the planned append-only extensions for execute() (bridge §6). They are
 * defaulted/nullable so an older bridge that omits them still decodes, and a flat Map<String,String>
 * to match EventEnvelope.data's convention.
 */
@Serializable
data class ResultEnvelope(
    val v: Int = 1,
    val kind: String = "result",
    val ok: Boolean,
    val error: ErrorBody? = null,
    val data: Map<String, String>? = null,
    val reqId: String? = null,
)

/** A batch of events for recovery / push. Mirrors EventBatch. */
@Serializable
data class EventBatch(
    val v: Int = 1,
    val kind: String = "batch",
    val bootId: String,
    val events: List<EventEnvelope> = emptyList(),
    val more: Boolean = false,
)

/** One automation event. Mirrors EventEnvelope. `data` is a flat string map (today's bridge). */
@Serializable
data class EventEnvelope(
    val v: Int = 1,
    val kind: String = "event",
    val bootId: String,
    val seq: Long,
    val ts: Long,
    val category: String = "",
    val type: String,
    val watch: WatchRef? = null,
    val data: Map<String, String> = emptyMap(),
)

/** Identity + headline state of a watch attached to an event. Mirrors WatchRef. */
@Serializable
data class WatchRef(
    val serial: String,
    val name: String,
    val nickname: String? = null,
    val model: String? = null,
    val fw: String? = null,
    val battery: Int? = null,
    val address: String? = null,
)

@Serializable
data class StateData(val watches: List<WatchRef> = emptyList())

/** State query response. Mirrors StateResult. */
@Serializable
data class StateResult(
    val v: Int = 1,
    val kind: String = "state",
    val data: StateData,
)

/**
 * Plugin -> bridge command, sent as the `commandJson` arg of the (planned) execute() AIDL method.
 * Bridge §6 contract: { v, kind:"command", type, watch?, args:Map<String,String>, idempotencyKey? }.
 *
 * `type` is a closed allowlist on the bridge (no reflection). `args` is a flat string map so every
 * field stays %variable-friendly. `watch` (serial-or-address) is optional -> bridge targets the
 * active watch when null.
 */
@Serializable
data class CommandEnvelope(
    val v: Int = 1,
    val kind: String = "command",
    val type: String,
    val watch: String? = null,
    val args: Map<String, String> = emptyMap(),
    @SerialName("idempotencyKey") val idempotencyKey: String? = null,
)
