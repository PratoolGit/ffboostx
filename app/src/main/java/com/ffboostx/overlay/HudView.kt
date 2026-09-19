package com.ffboostx.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.ffboostx.core.HudMetric
import com.ffboostx.core.OverlayConfig
import kotlin.math.max

/** One row of the HUD. A null [value] renders as "N/A", never as a zero. */
data class HudRow(val label: String, val value: String?, val accent: Int = Color.WHITE)

/**
 * A deliberately cheap HUD: one Canvas pass, two Paints, no child views, no
 * layout, no bitmaps. It redraws only when the service pushes new values, which
 * happens at the polling interval rather than every frame.
 *
 * The FPS row is always present and always reads N/A. Android does not expose
 * another process's frame rate, and printing the display refresh rate in an FPS
 * slot would be a fabricated number.
 */
@SuppressLint("ViewConstructor")
class HudView(
    context: Context,
    private var config: OverlayConfig
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private var rows: List<HudRow> = emptyList()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun update(newRows: List<HudRow>, newConfig: OverlayConfig) {
        rows = newRows
        config = newConfig
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val textSize = config.hudTextSizeSp * scaledDensity
        labelPaint.textSize = textSize
        valuePaint.textSize = textSize

        val padding = 9f * density
        val rowHeight = textSize * 1.65f

        var widest = 0f
        for (row in rows) {
            val labelWidth = labelPaint.measureText(row.label)
            val valueWidth = valuePaint.measureText(row.value ?: NOT_AVAILABLE)
            widest = max(widest, labelWidth + valueWidth + 16f * density)
        }

        val width = (widest + padding * 2).toInt().coerceAtLeast((92 * density).toInt())
        val height = (rows.size * rowHeight + padding * 2).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rows.isEmpty()) return

        val alpha = (config.hudOpacity.coerceIn(0.25f, 1f) * 255).toInt()
        val textSize = config.hudTextSizeSp * scaledDensity
        val padding = 9f * density
        val rowHeight = textSize * 1.65f
        val corner = 10f * density

        backgroundPaint.color = Color.argb(alpha, 8, 10, 16)
        borderPaint.color = Color.argb((alpha * 0.55f).toInt(), 255, 107, 44)

        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, corner, corner, backgroundPaint)
        canvas.drawRoundRect(
            RectF(0.5f * density, 0.5f * density, width - 0.5f * density, height - 0.5f * density),
            corner,
            corner,
            borderPaint
        )

        labelPaint.textSize = textSize
        valuePaint.textSize = textSize
        labelPaint.color = Color.argb(alpha, 154, 165, 188)

        var y = padding + textSize
        for (row in rows) {
            canvas.drawText(row.label, padding, y, labelPaint)
            valuePaint.color = if (row.value == null) {
                Color.argb(alpha, 107, 118, 144)
            } else {
                Color.argb(alpha, Color.red(row.accent), Color.green(row.accent), Color.blue(row.accent))
            }
            canvas.drawText(row.value ?: NOT_AVAILABLE, width - padding, y, valuePaint)
            y += rowHeight
        }
    }

    companion object {
        const val NOT_AVAILABLE = "N/A"

        /** The metric order the HUD always uses, regardless of selection order. */
        val ORDER = listOf(
            HudMetric.PING,
            HudMetric.JITTER,
            HudMetric.TEMPERATURE,
            HudMetric.BATTERY,
            HudMetric.REFRESH,
            HudMetric.MEMORY
        )
    }
}
