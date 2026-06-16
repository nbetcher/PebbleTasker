package com.nickbether.pebbletasker.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient.ConnectionStatus

/**
 * Shared, stateless helpers for the app-shell UI (Main / Onboarding / ConsentGuidance / Diagnostics).
 *
 * Centralizes:
 *  - the bridge package/action constants the UI references;
 *  - mapping [ConnectionStatus] to a short chip label, a Neon-Grid status-chip style, and a longer
 *    human description, so every screen describes the connection identically;
 *  - a best-effort "open the Pebble app" launch intent.
 */
object UiSupport {

    /** The Pebble (Core Devices) app package that hosts the automation bridge. */
    const val PEBBLE_PACKAGE = "coredevices.coreapp"

    /** Short status-chip label resource for a given connection status. */
    @StringRes
    fun statusLabel(status: ConnectionStatus): Int = when (status) {
        is ConnectionStatus.Idle -> R.string.main_status_idle
        is ConnectionStatus.Disconnected -> R.string.main_status_disconnected
        is ConnectionStatus.AppAbsent -> R.string.main_status_app_absent
        is ConnectionStatus.Ready -> R.string.main_status_ready
        is ConnectionStatus.NotAuthorized -> R.string.main_status_not_authorized
        is ConnectionStatus.ConsentPending -> R.string.main_status_consent_pending
        is ConnectionStatus.CertMismatch -> R.string.main_status_cert_mismatch
        is ConnectionStatus.Error -> R.string.main_status_error
    }

    /**
     * Apply the Neon-Grid status-pill colors (bg / stroke / text) to an existing status [Chip] for the
     * given [status], in place. Used by Main + Onboarding so the verify chip looks identical to home.
     *   Ready -> green/Success, ConsentPending -> cyan/Info, NotAuthorized -> yellow/Warning,
     *   CertMismatch/AppAbsent/Error -> red/Danger, Idle/Disconnected -> neutral.
     */
    fun styleStatusChip(chip: Chip, status: ConnectionStatus) {
        val ctx = chip.context
        fun c(id: Int) = ContextCompat.getColor(ctx, id)
        val (bg, stroke, text) = when (status) {
            is ConnectionStatus.Ready ->
                Triple(R.color.ng_green_10, R.color.ng_stroke_green, R.color.ng_green)
            is ConnectionStatus.ConsentPending ->
                Triple(R.color.ng_cyan_10, R.color.ng_stroke_cyan, R.color.ng_cyan)
            is ConnectionStatus.NotAuthorized ->
                Triple(R.color.ng_yellow_18, R.color.ng_stroke_yellow, R.color.ng_yellow)
            is ConnectionStatus.CertMismatch,
            is ConnectionStatus.AppAbsent,
            is ConnectionStatus.Error ->
                Triple(R.color.ng_red_10, R.color.ng_stroke_red, R.color.ng_red)
            is ConnectionStatus.Idle,
            is ConnectionStatus.Disconnected ->
                Triple(R.color.ng_surface_higher, R.color.ng_outline, R.color.ng_text_secondary)
        }
        chip.chipBackgroundColor = ColorStateList.valueOf(c(bg))
        chip.chipStrokeColor = ColorStateList.valueOf(c(stroke))
        chip.setTextColor(c(text))
    }

    /** Longer human description for the status, including any bridge-provided message. */
    fun statusDetail(context: Context, status: ConnectionStatus): String = when (status) {
        is ConnectionStatus.Ready -> context.getString(R.string.main_status_ready_detail)
        is ConnectionStatus.Idle -> context.getString(R.string.main_status_connecting)
        is ConnectionStatus.Disconnected -> context.getString(R.string.main_status_disconnected)
        is ConnectionStatus.AppAbsent -> context.getString(R.string.consent_app_absent_body)
        is ConnectionStatus.NotAuthorized ->
            status.message.ifBlank { context.getString(R.string.consent_not_authorized_body) }
        is ConnectionStatus.ConsentPending ->
            status.message.ifBlank { context.getString(R.string.consent_pending_body) }
        is ConnectionStatus.CertMismatch -> context.getString(R.string.consent_cert_mismatch_body)
        is ConnectionStatus.Error -> "${context.getString(R.string.consent_error_body)} (${status.code})"
    }

    /** True when the status requires the user to do something (vs. Ready or transient connecting). */
    fun needsUserAction(status: ConnectionStatus): Boolean = when (status) {
        is ConnectionStatus.NotAuthorized,
        is ConnectionStatus.ConsentPending,
        is ConnectionStatus.CertMismatch,
        is ConnectionStatus.AppAbsent,
        is ConnectionStatus.Error -> true
        is ConnectionStatus.Ready,
        is ConnectionStatus.Idle,
        is ConnectionStatus.Disconnected -> false
    }

    /**
     * Launch intent for the Pebble app, or null if it isn't installed. Uses the package manager's
     * declared launcher so the user lands on the app's home (where Automation access lives).
     */
    fun pebbleLaunchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(PEBBLE_PACKAGE)

    /** True if the Pebble app is installed (queryable via the manifest <queries> entry). */
    fun isPebbleInstalled(context: Context): Boolean =
        pebbleLaunchIntent(context) != null || runCatching {
            context.packageManager.getPackageInfo(PEBBLE_PACKAGE, 0); true
        }.getOrDefault(false)
}
