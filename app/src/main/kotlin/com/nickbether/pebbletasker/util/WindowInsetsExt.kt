package com.nickbether.pebbletasker.util

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Keep an activity's content out from under the status bar / nav bar / display cutout / keyboard.
 *
 * Android 15 (SDK 35+) enforces edge-to-edge, so a window draws behind the system bars by default.
 * Rather than going immersive, we opt into edge-to-edge explicitly (consistent on older versions too)
 * and pad the content view by the system-bar insets, so nothing renders under the bars.
 *
 * Call once from onCreate, AFTER setContentView. (Config activities don't need this — PebbleConfigActivity
 * already insets its toolbar scaffold; this is for the plain View-based UI activities.)
 */
fun Activity.applyContentInsets() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val content = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.updatePadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
        insets
    }
}
