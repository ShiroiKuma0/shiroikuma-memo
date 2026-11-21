package org.fossify.notes.views

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The black-yellow theme's switch as a drawable — a round outline with a solid dot, right and in [on]
 * when checked, left and in the darker [off] when not. Never filled. The button of a checkable menu row.
 */
class OutlinedSwitchDrawable(
    private val on: Int,
    private val off: Int,
    private val widthPx: Int,
    private val heightPx: Int,
    strokePx: Int,
) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx.toFloat()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private var checked = false

    override fun draw(canvas: Canvas) {
        val b = bounds
        val color = if (checked) on else off
        val inset = stroke.strokeWidth / 2f
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        stroke.color = color
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, stroke)
        fill.color = color
        val cx = if (checked) b.right - b.height() / 2f else b.left + b.height() / 2f
        canvas.drawCircle(cx, b.exactCenterY(), b.height() * DOT_RADIUS_SHARE, fill)
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val now = state.contains(android.R.attr.state_checked)
        if (now == checked) return false
        checked = now
        invalidateSelf()
        return true
    }

    override fun getIntrinsicWidth() = widthPx

    override fun getIntrinsicHeight() = heightPx

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val DOT_RADIUS_SHARE = 0.23f // a dot 46 % of the track's height
    }
}
