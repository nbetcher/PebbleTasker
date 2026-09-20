package com.nickbether.pebbletasker.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nickbether.pebbletasker.tasker.ErrCodes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeClient.ConnectionStatus

/**
 * Surfaces "the plugin isn't bridged to the Pebble app" so the user never silently configures or runs
 * Pebble automations that can't work — e.g. after restoring a Tasker backup onto a new phone where the
 * Pebble app isn't installed or hasn't authorized this plugin.
 *
 * Two surfaces:
 *  - the config UI shows an inline warning banner while a plugin is configured unbridged
 *    ([isUnbridged] / [messageFor]);
 *  - every event/state/action runner calls [warnIfUsedWhileUnbridged]; when the bridge isn't Ready it
 *    posts a throttled notification deep-linking to the setup guidance, and cancels it once Ready.
 *
 * "Bridged/established" means [ConnectionStatus.Ready]; every other status (app absent, not authorized,
 * consent pending, cert mismatch, disconnected, error) is treated as unbridged.
 */
object BridgeWarning {

    /** True when the bridge is NOT established (any status other than Ready). */
    fun isUnbridged(context: Context): Boolean =
        BridgeClient.get(context).status.value !is ConnectionStatus.Ready

    /** A short, user-facing reason for a non-Ready [status], or null when Ready (nothing to warn about). */
    fun messageFor(status: ConnectionStatus): String? = when (status) {
        is ConnectionStatus.Ready -> null
        is ConnectionStatus.AppAbsent ->
            "The Pebble app isn't installed. Pebble events, states, and actions won't work until it is."
        is ConnectionStatus.NotAuthorized ->
            status.message
        is ConnectionStatus.ConsentPending ->
            "Waiting for you to approve this plugin in the Pebble app."
        is ConnectionStatus.CertMismatch ->
            "Review the Pebble app fingerprint and explicitly trust it to connect."
        is ConnectionStatus.Error ->
            when (status.code) {
                ErrCodes.ACCESS_DENIED -> BridgeClient.DENIED_MESSAGE
                ErrCodes.CERT_MISMATCH -> "Pebble rejected this plugin's signature. Review this plugin identity in Pebble."
                else -> status.message
            }
        ConnectionStatus.Idle, ConnectionStatus.Disconnected ->
            "Not connected to the Pebble app. Open it and enable automation access for this plugin."
    }

    /**
     * Called by every runner. If the bridge isn't established, post a throttled warning notification
     * and return true; otherwise cancel any existing warning and return false.
     */
    fun warnIfUsedWhileUnbridged(context: Context): Boolean {
        val client = BridgeClient.get(context)
        val status = client.status.value
        if (status is ConnectionStatus.Ready) {
            cancel(context)
            return false
        }
        maybeNotify(context.applicationContext, status)
        return true
    }

    /** Create the notification channel (idempotent). Call once from the Application. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Connection warnings", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description =
                        "Warns when Pebble events, states, or actions can't run because the Pebble app isn't connected."
                },
            )
        }
    }

    /** Remove the warning (the bridge is Ready again). */
    fun cancel(context: Context) {
        context.getSharedPreferences("pb_bridge_diagnostic", Context.MODE_PRIVATE).edit().clear().apply()
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIF_ID) }
    }

    /** Retains the actionable reason even when Android blocks notifications. Configuration and
     * guidance use the live status; this persisted text is available for diagnostic/support screens. */
    fun lastDiagnostic(context: Context): String? =
        context.getSharedPreferences("pb_bridge_diagnostic", Context.MODE_PRIVATE).getString("message", null)

    @Synchronized private fun maybeNotify(appCtx: Context, status: ConnectionStatus) {
        val message = messageFor(status) ?: return
        val prefs = appCtx.getSharedPreferences("pb_bridge_diagnostic", Context.MODE_PRIVATE)
        prefs.edit().putString("message", message).apply()
        if (prefs.getString("notified", null) == message) return
        ensureChannel(appCtx)
        val manager = NotificationManagerCompat.from(appCtx)
        if (!manager.areNotificationsEnabled()) return
        val channel = appCtx.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_ID)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return
        if (post(appCtx, status)) prefs.edit().putString("notified", message).apply()
    }
    private fun post(appCtx: Context, status: ConnectionStatus): Boolean {
        val text = messageFor(status) ?: return false
        ensureChannel(appCtx)
        val tap = PendingIntent.getActivity(
            appCtx,
            0,
            ConsentGuidanceActivity.intentFor(appCtx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("Pebble automations aren't running")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(status is ConnectionStatus.ConsentPending)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        // Best-effort: no-ops if the user hasn't granted POST_NOTIFICATIONS (the config banner still warns).
        return try {
            NotificationManagerCompat.from(appCtx).notify(NOTIF_ID, notif)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private const val CHANNEL_ID = "pb_bridge_warning"
    private const val NOTIF_ID = 0x9B01

}
