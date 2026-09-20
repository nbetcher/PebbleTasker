package com.nickbether.pebbletasker.setup

import android.content.Context

/**
 * Durable "has this plugin ever been set up on this device?" flag.
 *
 * Set ONCE, the first time [com.nickbether.pebbletasker.bridge.BridgeClient] reaches
 * [com.nickbether.pebbletasker.bridge.BridgeClient.ConnectionStatus.Ready] — i.e. the Pebble app is
 * installed, its Automation master switch is on, the user approved this plugin, and the signing cert
 * matched. That is the reliable "setup is complete" signal, and it is the RIGHT gate for two reasons:
 *
 *  - It is **persistent**, so a plugin process that Android killed and hasn't rebound yet is still
 *    "set up" — a transient disconnect (e.g. the watch is simply off) never reads as "not set up".
 *  - It is **false on a fresh install / Tasker-restore-onto-a-new-phone**, which is exactly the case
 *    we must catch: valid Tasker plugin configs but no bind/authorization to the Pebble app. There we
 *    block the config from saving and bubble a hard error up through Tasker at run time.
 *
 * Deliberately NOT gated on a live watch being connected: whether a *watch* is paired lives in the
 * Pebble app, and getState only lists currently-connected watches, so keying on it would false-block a
 * fully-set-up user whose watch happened to be off. Authorization is the durable, unambiguous signal.
 */
object SetupState {

    private const val PREFS = "pb_setup"
    private const val KEY_EVER_READY = "ever_ready"

    /** True once the bridge has reached Ready at least once on this device (persisted). */
    fun isSetupComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EVER_READY, false)

    /** Record that setup completed (bridge reached Ready). Idempotent; safe from any thread. */
    fun markSetupComplete(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_EVER_READY, false)) {
            p.edit().putBoolean(KEY_EVER_READY, true).apply()
        }
    }

    /** Local terminal decision cache. Never copied by Android backup or Tasker profile restore. */
    fun isAccessDenied(context: Context): Boolean = java.io.File(context.noBackupFilesDir, "pb_denied").exists()

    fun setAccessDenied(context: Context, denied: Boolean) {
        val file = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, "pb_denied"))
        if (!denied) { file.delete(); return }
        val stream = file.startWrite()
        try { stream.write(1); file.finishWrite(stream) }
        catch (t: Throwable) { file.failWrite(stream); throw t }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
