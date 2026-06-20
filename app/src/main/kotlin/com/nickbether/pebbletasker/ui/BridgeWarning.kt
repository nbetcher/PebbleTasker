package com.nickbether.pebbletasker.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
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
            "Automation access is off in the Pebble app. Open it and turn it on for this plugin."
        is ConnectionStatus.ConsentPending ->
            "Waiting for you to approve this plugin in the Pebble app."
        is ConnectionStatus.CertMismatch ->
            "The Pebble app's signature changed — re-trust this plugin to reconnect."
        is ConnectionStatus.Error ->
            "Can't reach the Pebble app right now — Pebble automations may not work."
        ConnectionStatus.Idle, ConnectionStatus.Disconnected ->
            "Not connected to the Pebble app. Open it and enable automation access for this plugin."
    }

    /**
     * Called by every runner. If the bridge isn't established, post a throttled warning notification
     * and return true; otherwise cancel any existing warning and return false.
     */
    fun warnIfUsedWhileUnbridged(context: Context): Boolean {
        val status = BridgeClient.get(context).status.value
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
        lastNotifiedElapsed = 0L
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIF_ID) }
    }

    @Volatile private var lastNotifiedElapsed = 0L

    private fun maybeNotify(appCtx: Context, status: ConnectionStatus) {
        val now = SystemClock.elapsedRealtime()
        // The same-id notification persists until tapped or bridged; re-warn at most every 30 min so a
        // frequently-polled state condition can't spam the shade.
        if (lastNotifiedElapsed != 0L && now - lastNotifiedElapsed < COOLDOWN_MS) return
        lastNotifiedElapsed = now
        post(appCtx, status)
    }

    private fun post(appCtx: Context, status: ConnectionStatus) {
        val text = messageFor(status) ?: return
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
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        // Best-effort: no-ops if the user hasn't granted POST_NOTIFICATIONS (the config banner still warns).
        runCatching { NotificationManagerCompat.from(appCtx).notify(NOTIF_ID, notif) }
    }

    private const val CHANNEL_ID = "pb_bridge_warning"
    private const val NOTIF_ID = 0x9B01
    private const val COOLDOWN_MS = 30 * 60 * 1000L
}
