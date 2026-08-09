package com.nickbether.pebbletasker.tasker.action.sendnotif

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.ui.widget.VibrationPatternView
import kotlin.math.roundToInt

/**
 * "Tap out a vibration" popup for the Send-Notification custom vibe (A2).
 *
 * Hold the pad to buzz (the phone vibrates for as long as you hold), release to pause; each hold/gap is
 * appended to an on,off,on,… pattern that's visualised live and editable as a CSV. Preview replays the
 * whole pattern on the phone. On "Use pattern" the CSV is handed back via [onSave].
 *
 * Phone-only preview by design: the bridge has no bare-vibrate command, and previewing on the watch
 * would require pushing a real notification.
 */
class VibeBuilderDialog(
    private val context: Context,
    initial: List<Int>,
    private val onSave: (String) -> Unit,
) {
    private var pattern: MutableList<Int> = initial.filter { it > 0 }.toMutableList()

    private lateinit var patternView: VibrationPatternView
    private lateinit var editPattern: TextInputEditText

    private val handler = Handler(Looper.getMainLooper())
    private var recordStart = 0L
    private var lastReleaseAt = 0L
    private var updating = false
    private var lifecycleObserver: DefaultLifecycleObserver? = null

    private val vibrator: Vibrator? = run {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        v?.takeIf { it.hasVibrator() }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (recordStart <= 0L) return
            patternView.setPattern(pattern + clamp(SystemClock.uptimeMillis() - recordStart))
            handler.postDelayed(this, 40)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_vibe_builder, null)
        patternView = root.findViewById(R.id.patternView)
        editPattern = root.findViewById(R.id.editPattern)
        val padRecord = root.findViewById<MaterialCardView>(R.id.padRecord)
        val btnClear = root.findViewById<MaterialButton>(R.id.btnClear)
        val btnPreview = root.findViewById<MaterialButton>(R.id.btnPreview)

        syncFromPattern()
        editPattern.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                pattern = VibePattern.parse(s?.toString()).toMutableList()
                lastReleaseAt = 0L // a manual edit invalidates any pending inter-press gap
                patternView.setPattern(pattern)
            }
        })

        padRecord.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { onPressDown(); v.isPressed = true; true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onPressUp()
                    v.isPressed = false
                    if (ev.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                    true
                }
                else -> false
            }
        }

        btnClear.setOnClickListener {
            pattern = mutableListOf()
            recordStart = 0L
            lastReleaseAt = 0L
            syncFromPattern()
        }
        btnPreview.setOnClickListener { preview() }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.vibe_builder_title)
            .setView(root)
            .setPositiveButton(R.string.vibe_save) { _, _ ->
                onSave(VibePattern.format(VibePattern.parse(editPattern.text?.toString())))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnDismissListener {
            stopBuzz()
            handler.removeCallbacks(tick)
            detachLifecycle()
        }
        // Stop buzzing + dismiss if the host is backgrounded / destroyed (e.g. rotation) mid-build, so
        // the vibrator can't be left running and the dialog can't leak the destroyed activity.
        (context as? LifecycleOwner)?.let { host ->
            val obs = object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    stopBuzz()
                    handler.removeCallbacks(tick)
                    dialog.dismiss()
                }
            }
            lifecycleObserver = obs
            host.lifecycle.addObserver(obs)
        }
        dialog.show()
    }

    private fun detachLifecycle() {
        val obs = lifecycleObserver ?: return
        (context as? LifecycleOwner)?.lifecycle?.removeObserver(obs)
        lifecycleObserver = null
    }

    private fun onPressDown() {
        val now = SystemClock.uptimeMillis()
        // A press after a previous release closes out the pause between buzzes.
        if (pattern.isNotEmpty() && lastReleaseAt > 0L) {
            pattern.add(clamp(now - lastReleaseAt))
            lastReleaseAt = 0L
        }
        recordStart = now
        startBuzz()
        syncFromPattern()
        handler.postDelayed(tick, 40)
    }

    private fun onPressUp() {
        val now = SystemClock.uptimeMillis()
        if (recordStart > 0L) {
            pattern.add(clamp(now - recordStart))
            recordStart = 0L
        }
        stopBuzz()
        handler.removeCallbacks(tick)
        lastReleaseAt = now
        syncFromPattern()
    }

    /** Push [pattern] into the editable CSV (guarded) and the visual. */
    private fun syncFromPattern() {
        updating = true
        editPattern.setText(VibePattern.format(pattern))
        editPattern.setSelection(editPattern.text?.length ?: 0)
        updating = false
        patternView.setPattern(pattern)
    }

    private fun preview() {
        val v = vibrator ?: run {
            Toast.makeText(context, R.string.vibe_no_vibrator, Toast.LENGTH_SHORT).show()
            return
        }
        val p = VibePattern.parse(editPattern.text?.toString())
        if (p.isEmpty()) return
        // createWaveform timings alternate OFF/ON starting with OFF; our pattern starts ON, so lead a 0.
        val timings = (listOf(0) + p).map { it.toLong() }.toLongArray()
        val played = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        }.isSuccess
        if (!played) Toast.makeText(context, R.string.vibe_no_vibrator, Toast.LENGTH_SHORT).show()
    }

    private fun startBuzz() {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(60_000L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(60_000L)
            }
        }
    }

    private fun stopBuzz() {
        runCatching { vibrator?.cancel() }
    }

    /** Clamp a raw hold/gap to a tidy 20..10000 ms rounded to the nearest 10. */
    private fun clamp(ms: Long): Int =
        ((ms.coerceIn(20L, 10_000L) / 10.0).roundToInt() * 10).coerceIn(20, 10_000)

    companion object {
        /** Open the builder seeded with [csv]'s pattern. */
        fun show(context: Context, csv: String?, onSave: (String) -> Unit) {
            VibeBuilderDialog(context, VibePattern.parse(csv), onSave).show()
        }
    }
}
