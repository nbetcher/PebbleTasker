package com.nickbether.pebbletasker.tasker

/**
 * FROZEN CONTRACT (FINAL DESIGN "CONTRACTS TO FREEZE" #2).
 *
 * Maps the bridge's string ErrorCode vocabulary to stable integer codes surfaced as Tasker's
 * %err on hard-failure actions, and carried as %pbl_err on the success-with-ok=false model. These
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
    const val ACCESS_DENIED = 9

    // --- plugin-local infrastructure failures ---
    const val BRIDGE_UNREACHABLE = 20
    const val TIMEOUT = 21

    /** Command or per-watch cooldown; the message carries the remaining seconds. */
    const val RATE_LIMITED = 22

    /**
     * Plugin has never been set up on this device (never authorized against the Pebble app) — e.g. a
     * Tasker config restored onto a new phone. A hard failure so Tasker surfaces %err at run time
     * telling the user to finish setup. See [com.nickbether.pebbletasker.setup.SetupState].
     */
    const val NOT_SET_UP = 23

    // --- watch control / firmware (appended; never renumber) ---
    const val PREF_UNSUPPORTED = 24
    const val FIRMWARE_UPDATE_UNAVAILABLE = 25
    const val FIRMWARE_CHECK_STALE = 26
    const val WATCH_BUSY = 27
    /** Plugin-local: a screenshot/log job reported failure, or its file could not be read or saved. */
    const val JOB_FAILED = 28
    /** Plugin-local: Run Watch Diagnostics had a failed check or could not start. */
    const val DIAGNOSTICS_FAILED = 29

    /** Fallback for any unrecognized future bridge code. */
    const val UNKNOWN = 99

    private val byName: Map<String, Int> = mapOf(
        "NOT_AUTHORIZED" to NOT_AUTHORIZED,
        "COMMAND_NOT_AUTHORIZED" to NOT_AUTHORIZED,
        "CONSENT_PENDING" to CONSENT_PENDING,
        "CERT_MISMATCH" to CERT_MISMATCH,
        "CATEGORY_DISABLED" to CATEGORY_DISABLED,
        "UNSUPPORTED_COMMAND" to UNSUPPORTED_COMMAND,
        "UNSUPPORTED_VERSION" to UNSUPPORTED_VERSION,
        "INVALID_ARGS" to INVALID_ARGS,
        "INTERNAL" to INTERNAL,
        "ACCESS_DENIED" to ACCESS_DENIED,
        "BRIDGE_UNREACHABLE" to BRIDGE_UNREACHABLE,
        "TIMEOUT" to TIMEOUT,
        "RATE_LIMITED" to RATE_LIMITED,
        "PREF_UNSUPPORTED" to PREF_UNSUPPORTED,
        "FIRMWARE_UPDATE_UNAVAILABLE" to FIRMWARE_UPDATE_UNAVAILABLE,
        "FIRMWARE_CHECK_STALE" to FIRMWARE_CHECK_STALE,
        "WATCH_BUSY" to WATCH_BUSY,
    )

    /** Bridge string code -> stable int. Unknown strings map to [UNKNOWN]. */
    fun toInt(code: String?): Int = code?.let { byName[it] } ?: UNKNOWN

    /** True for error codes that auto-resolve by retry/polling (vs. needing user action). */
    fun isTransient(code: Int): Boolean =
        code == CONSENT_PENDING || code == BRIDGE_UNREACHABLE || code == TIMEOUT || code == RATE_LIMITED ||
            code == WATCH_BUSY
}
