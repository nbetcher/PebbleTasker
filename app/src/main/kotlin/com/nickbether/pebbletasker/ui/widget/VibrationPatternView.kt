package com.nickbether.pebbletasker.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.nickbether.pebbletasker.R

/**
 * Neon timeline that renders a vibration pattern (alternating on/off durations in ms, starting with an
 * "on" buzz) as a left-to-right strip: filled neon bars for buzzes, dim gaps for pauses. In the full
 * (non-[compact]) form it also labels each segment with its ms value and a buzz/pause glyph; the compact
 * form is bars only, for the small inline preview on the config screen.
 *
 * Purely presentational — [pattern] is the source of truth, set by the owning screen/dialog.
 */
class VibrationPatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Show ms labels + glyphs (false = bars only, for the small inline preview). */
    var compact: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private var pattern: List<Int> = emptyList()

    /** Replace the displayed pattern (alternating on/off ms, starting with an "on"). */
    fun setPattern(values: List<Int>) {
        pattern = values.filter { it > 0 }
        invalidate()
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val buzzColor = ContextCompat.getColor(context, R.color.ng_yellow)
    private val trackColor = 0x33FFFFFF
    private val labelColor = ContextCompat.getColor(context, R.color.ng_text_secondary)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = trackColor
        strokeWidth = dp(2f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
    }

    private val buzzIcon = AppCompatResources.getDrawable(context, R.drawable.ic_vibe_buzz)
        ?.let { DrawableCompat.wrap(it.mutate()).also { d -> DrawableCompat.setTint(d, buzzColor) } }
    private val pauseIcon = AppCompatResources.getDrawable(context, R.drawable.ic_vibe_pause)
        ?.let { DrawableCompat.wrap(it.mutate()).also { d -> DrawableCompat.setTint(d, labelColor) } }

    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(suggestedMinimumWidth.coerceAtLeast(dp(120f).toInt()), widthMeasureSpec)
        val desiredH = dp(if (compact) 28f else 76f).toInt()
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val avail = (right - left).coerceAtLeast(1f)
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()

        val labelH = if (compact) 0f else dp(16f)
        val iconH = if (compact) 0f else dp(14f)
        val barTop = top + iconH
        val barBottom = bottom - labelH
        if (barBottom <= barTop) return

        // Empty: a dim baseline so the strip still reads as "a place for a pattern".
        if (pattern.isEmpty()) {
            val midY = (barTop + barBottom) / 2f
            canvas.drawLine(left, midY, right, midY, trackPaint)
            return
        }

        val total = pattern.sum().coerceAtLeast(1)
        val minSeg = dp(6f)
        var widths = pattern.map { maxOf(minSeg, it.toFloat() / total * avail) }
        val sum = widths.sum()
        if (sum > avail) widths = widths.map { it / sum * avail }

        val radius = dp(3f)
        var x = left
        val midY = (barTop + barBottom) / 2f
        for ((i, w) in widths.withIndex()) {
            val isBuzz = i % 2 == 0
            val segRight = (x + w).coerceAtMost(right)
            if (isBuzz) {
                glowPaint.color = (buzzColor and 0x00FFFFFF) or 0x33000000
                rect.set(x, barTop - dp(1f), segRight, barBottom + dp(1f))
                canvas.drawRoundRect(rect, radius, radius, glowPaint)
                barPaint.color = buzzColor
                rect.set(x, barTop, segRight, barBottom)
                canvas.drawRoundRect(rect, radius, radius, barPaint)
            } else {
                // Pause: a dim baseline across the gap.
                canvas.drawLine(x + dp(1f), midY, segRight - dp(1f), midY, trackPaint)
            }

            if (!compact) {
                val cx = (x + segRight) / 2f
                val icon = if (isBuzz) buzzIcon else pauseIcon
                val iconW = iconH
                if (icon != null && w > iconW + dp(2f)) {
                    val il = (cx - iconW / 2f).toInt()
                    val it0 = top.toInt()
                    icon.setBounds(il, it0, (il + iconW).toInt(), (it0 + iconH).toInt())
                    icon.draw(canvas)
                }
                if (w > dp(22f)) {
                    canvas.drawText(pattern[i].toString(), cx, bottom - dp(3f), textPaint)
                }
            }
            x = segRight
        }
    }
}
