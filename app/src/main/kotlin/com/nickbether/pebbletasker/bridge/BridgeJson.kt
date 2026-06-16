package com.nickbether.pebbletasker.bridge

import kotlinx.serialization.json.Json

/**
 * The single JSON instance used for all bridge IPC (de)serialization on the plugin side.
 *
 * Mirrors the bridge's BridgeJson:
 *  - ignoreUnknownKeys = true  -> the bridge can append fields without breaking us.
 *  - encodeDefaults    = true  -> ClientHello/CommandEnvelope serialize their default fields
 *    (v, kind, ...) so the bridge sees a complete, self-describing payload.
 *  - isLenient is left at the default (false): the wire is always strict JSON from the bridge.
 */
internal object BridgePluginJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
