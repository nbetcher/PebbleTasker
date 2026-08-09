package com.nickbether.pebbletasker.log

import android.util.Log

/**
 * Single-tag logcat facade for the plugin — the diagnostics counterpart to the host app's
 * "AutomationBridge" Kermit tag. Watch both halves of the bridge conversation together with:
 *
 *     adb logcat -s PebbleTasker AutomationBridge
 *
 * Design notes:
 *  - **Not** gated on BuildConfig.DEBUG. The bridge TOFU-pins the plugin's signing cert
 *    (CallerVerifier), so only a *release*-signed plugin (same cert as the host app) is ever
 *    trusted. A debug build would fail cert-match and never authorize — useless for reproducing
 *    the real "watch state rarely fires" path. Hence logging must live in release builds too.
 *  - R8 keeps `android.util.Log` calls (no log-stripping rule is configured for this module), so
 *    these survive minification. To silence once diagnosis is done, flip [ENABLED] to false or add
 *    an `-assumenosideeffects` rule for android.util.Log to the release proguard file.
 *  - Message builders are inline lambdas: when [ENABLED] is false nothing is concatenated.
 *
 * Keep call sites terse and greppable — prefix a short subsystem token, e.g. "conn: bind ok".
 */
object PLog {
    const val TAG = "PebbleTasker"

    /** Master switch. Left on so the shipped diagnostic build talks; set false to mute. */
    @Volatile
    var ENABLED: Boolean = true

    inline fun d(msg: () -> String) {
        if (ENABLED) Log.d(TAG, msg())
    }

    inline fun i(msg: () -> String) {
        if (ENABLED) Log.i(TAG, msg())
    }

    inline fun w(msg: () -> String) {
        if (ENABLED) Log.w(TAG, msg())
    }

    inline fun w(t: Throwable?, msg: () -> String) {
        if (ENABLED) Log.w(TAG, msg(), t)
    }

    inline fun e(t: Throwable?, msg: () -> String) {
        if (ENABLED) Log.e(TAG, msg(), t)
    }
}
