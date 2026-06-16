package com.nickbether.pebbletasker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimization helpers (FINAL DESIGN §1, onboarding).
 *
 * The sticky bind survives best when the app is exempt from Doze app-standby. Onboarding offers to
 * request the exemption; this util provides the check + the request intent.
 */
object BatteryOpt {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Intent that opens the system dialog asking the user to exempt this app from battery
     * optimization. Requires the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission (declared in the
     * manifest). Start with startActivity from an Activity context.
     */
    fun requestIgnoreIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /** Fallback intent that opens the full battery-optimization settings list. */
    fun settingsListIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
