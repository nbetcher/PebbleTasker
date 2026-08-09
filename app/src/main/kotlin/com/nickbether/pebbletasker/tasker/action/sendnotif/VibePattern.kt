package com.nickbether.pebbletasker.tasker.action.sendnotif

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.style.ImageSpan
import android.widget.EditText
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.nickbether.pebbletasker.R

/**
 * Pure helpers for the Send-Notification custom vibration field (mirrors the bridge's
 * CommandArgs.vibePattern wire format: a CSV of on/off ms durations starting with an "on" buzz).
 *
 * [decorate] paints render-only buzz/pause glyphs onto the editable CSV: each comma is drawn as the
 * glyph for the value it follows (buzz for the 1st/3rd/… value, pause for the 2nd/4th/…). Crucially it
 * only adds [ImageSpan]s — the underlying characters are never touched, so `edit.text.toString()` always
 * returns a clean CSV and the saved value can't be corrupted by the decoration.
 */
object VibePattern {

    /** The named (non-custom) vibe choices that are NOT CSV patterns. */
    val KEYWORDS = setOf("none", "short", "long", "double")

    fun isKeyword(s: String?): Boolean = s?.trim()?.lowercase() in KEYWORDS

    fun isVariable(s: String?): Boolean = s?.contains('%') == true

    /** Parse a CSV ("200,100,200") to ms ints; junk dropped, each clamped to 10..10000. */
    fun parse(csv: String?): List<Int> {
        val s = csv?.trim().orEmpty()
        if (s.isEmpty() || isKeyword(s) || isVariable(s)) return emptyList()
        return s.split(',', ' ', ';', '\t')
            .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toIntOrNull() }
            .filter { it > 0 }
            .map { it.coerceIn(10, 10_000) }
    }

    fun format(values: List<Int>): String = values.joinToString(",")

    /**
     * Apply render-only buzz/pause glyph spans to [edit]'s CSV. Removes any prior glyphs first; no-op
     * (just clears) for keywords/%variables. Never changes the text content.
     */
    fun decorate(edit: EditText) {
        val editable = edit.text ?: return
        editable.getSpans(0, editable.length, ImageSpan::class.java).forEach { editable.removeSpan(it) }
        val text = editable.toString()
        if (isKeyword(text) || isVariable(text) || text.isEmpty()) return

        val ctx = edit.context
        val size = (edit.textSize * 1.05f).toInt().coerceAtLeast(1)
        // Build each glyph once and share the instance across all its spans (ImageSpan is render-only).
        val buzz = tintedIcon(ctx, R.drawable.ic_vibe_buzz, R.color.ng_yellow, size)
        val pause = tintedIcon(ctx, R.drawable.ic_vibe_pause, R.color.ng_text_secondary, size)

        var valueIndex = 0 // index of the value PRECEDING the current comma (even = buzz, odd = pause)
        for (i in text.indices) {
            if (text[i] != ',') continue
            val d = if (valueIndex % 2 == 0) buzz else pause
            valueIndex++
            if (d != null) {
                editable.setSpan(ImageSpan(d, ImageSpan.ALIGN_BASELINE), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun tintedIcon(ctx: Context, @DrawableRes res: Int, @ColorRes colorRes: Int, size: Int): Drawable? {
        val d = AppCompatResources.getDrawable(ctx, res)?.mutate() ?: return null
        DrawableCompat.setTint(d, ContextCompat.getColor(ctx, colorRes))
        d.setBounds(0, 0, size, size)
        return d
    }
}
