package com.ffboostx.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.ffboostx.core.CrosshairStyle
import com.ffboostx.core.OverlayConfig

/**
 * A static reticle drawn into an overlay window.
 *
 * It is a visual aid only: it does not read the screen, track anything, or move
 * on its own. There is no aim assistance of any kind here and none is possible
 * from an overlay, which can draw but cannot see the window beneath it.
 */
@SuppressLint("ViewConstructor")
class CrosshairView(
    context: Context,
    private var config: OverlayConfig
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val density = context.resources.displayMetrics.density

    init {
        // Purely decorative and never interactive, so it is hidden from
        // accessibility services rather than announced as an unlabelled view.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun apply(newConfig: OverlayConfig) {
        config = newConfig
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f + config.crosshairOffsetXDp * density
        val cy = height / 2f + config.crosshairOffsetDp * density
        val radius = config.crosshairSizeDp * density
        val thickness = config.crosshairThicknessDp * density

        paint.color = config.crosshairColor
        paint.alpha = (config.crosshairOpacity.coerceIn(0f, 1f) * 255).toInt()
        paint.strokeWidth = thickness

        when (config.crosshairStyle) {
            CrosshairStyle.DOT -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, thickness.coerceAtLeast(2f), paint)
                paint.style = Paint.Style.STROKE
            }

            CrosshairStyle.PLUS -> {
                canvas.drawLine(cx - radius, cy, cx + radius, cy, paint)
                canvas.drawLine(cx, cy - radius, cx, cy + radius, paint)
            }

            CrosshairStyle.CIRCLE -> {
                canvas.drawCircle(cx, cy, radius, paint)
            }

            CrosshairStyle.PRECISION -> {
                // Four ticks around an open centre, so the exact aim point stays
                // visible instead of being covered by the reticle itself.
                val gap = radius * 0.42f
                canvas.drawLine(cx - radius, cy, cx - gap, cy, paint)
                canvas.drawLine(cx + gap, cy, cx + radius, cy, paint)
                canvas.drawLine(cx, cy - radius, cx, cy - gap, paint)
                canvas.drawLine(cx, cy + gap, cx, cy + radius, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, thickness * 0.6f, paint)
                paint.style = Paint.Style.STROKE
            }

            CrosshairStyle.MINIMAL -> {
                val short = radius * 0.5f
                canvas.drawLine(cx - short, cy, cx + short, cy, paint)
            }
        }
    }
}
