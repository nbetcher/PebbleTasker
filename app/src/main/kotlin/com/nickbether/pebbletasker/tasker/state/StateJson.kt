package com.nickbether.pebbletasker.tasker.state

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Builds the one structured `%pbl_json` blob each state plugin emits (FINAL DESIGN §2 conventions /
 * §4.1 manner 5). Tasker 5.12+ reads `%pbl_json.field`; document the sub-paths in each plugin's blurb.
 *
 * Values are flat strings (matching the bridge's flat-string event/state convention), so the blob is
 * a single-level JSON object of string values. Null entries are dropped so absent fields don't appear
 * as the literal "null".
 */
internal object StateJson {

    /** Encode [pairs] (dropping null values) to a compact JSON object string. */
    fun obj(vararg pairs: Pair<String, String?>): String =
        buildJsonObject {
            for ((k, v) in pairs) {
                if (v != null) put(k, JsonPrimitive(v))
            }
        }.toString()
}
