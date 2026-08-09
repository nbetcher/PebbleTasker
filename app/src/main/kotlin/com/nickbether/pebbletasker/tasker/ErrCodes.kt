package com.nickbether.pebbletasker.tasker

/**
 * FROZEN CONTRACT (FINAL DESIGN "CONTRACTS TO FREEZE" #2).
 *
 * Maps the bridge's string ErrorCode vocabulary to stable integer codes surfaced as Tasker's
 * %err on hard-failure actions, and carried as %pb_err on the success-with-ok=false model. These
 * integers are a permanent contract: user Tasker tasks may branch on `%err == 21`, so NEVER
 * renumber an existing code. New codes are append-only.
 *
 * 1..8  mirror the bridge's ErrorCode enum (Contract.kt ErrorCode).
 * 20+   are plugin-local infrastructure failures the bridge never emits.
 */
object ErrCodes {
    // --- bridge ErrorCode vocabulary (Contract.kt) ---
    const val NOT_AUTHORIZED = 1
    const val CONSENT_PENDING = 2
    const val CERT_MISMATCH = 3
    const val CATEGORY_DISABLED = 4
    const val UNSUPPORTED_COMMAND = 5
    const val UNSUPPORTED_VERSION = 6
    const val INVALID_ARGS = 7
    const val INTERNAL = 8

    // --- plugin-local infrastructure failures ---
    const val BRIDGE_UNREACHABLE = 20
    const val TIMEOUT = 21

    /** Optional: only if the bridge later adds a RATE_LIMITED ErrorCode. */
    const val RATE_LIMITED = 22

    /**
     * Plugin has never been set up on this device (never authorized against the Pebble app) — e.g. a
     * Tasker config restored onto a new phone. A hard failure so Tasker surfaces %err at run time
     * telling the user to finish setup. See [com.nickbether.pebbletasker.setup.SetupState].
     */
    const val NOT_SET_UP = 23

    /** Fallback for any unrecognized future bridge code. */
    const val UNKNOWN = 99

    private val byName: Map<String, Int> = mapOf(
        "NOT_AUTHORIZED" to NOT_AUTHORIZED,
        "CONSENT_PENDING" to CONSENT_PENDING,
        "CERT_MISMATCH" to CERT_MISMATCH,
        "CATEGORY_DISABLED" to CATEGORY_DISABLED,
        "UNSUPPORTED_COMMAND" to UNSUPPORTED_COMMAND,
        "UNSUPPORTED_VERSION" to UNSUPPORTED_VERSION,
        "INVALID_ARGS" to INVALID_ARGS,
        "INTERNAL" to INTERNAL,
        "BRIDGE_UNREACHABLE" to BRIDGE_UNREACHABLE,
        "TIMEOUT" to TIMEOUT,
        "RATE_LIMITED" to RATE_LIMITED,
    )

    /** Bridge string code -> stable int. Unknown strings map to [UNKNOWN]. */
    fun toInt(code: String?): Int = code?.let { byName[it] } ?: UNKNOWN

    /** True for error codes that auto-resolve by retry/polling (vs. needing user action). */
    fun isTransient(code: Int): Boolean =
        code == CONSENT_PENDING || code == BRIDGE_UNREACHABLE || code == TIMEOUT || code == RATE_LIMITED
}
