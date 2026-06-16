package com.nickbether.pebbletasker.util

import android.app.Activity
import android.view.WindowManager

/**
 * Applies FLAG_SECURE to sensitive config activities (FINAL DESIGN §5).
 *
 * Use on config activities that surface notification text, health data, AppMessage dictionaries, or
 * watch serials — to keep them out of screenshots and the recents thumbnail. Call from onCreate
 * BEFORE setContentView.
 */
object SecureWindow {
    fun apply(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }
}
