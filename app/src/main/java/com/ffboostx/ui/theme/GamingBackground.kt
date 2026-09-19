package com.ffboostx.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ffboostx.core.BackgroundStyle
import kotlin.math.sin

/**
 * Full-screen background. Everything is drawn in a single [drawBehind] pass with
 * no layers, no blur and no bitmaps, so the cost is a handful of gradient and
 * line operations per frame. Only [BackgroundStyle.AURORA] animates, and only
 * when the motion budget allows ambient animation - otherwise it renders as a
 * static wash.
 */
@Composable
fun GamingBackground(
    style: BackgroundStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val motion = LocalMotion.current
    val animated = style == BackgroundStyle.AURORA && motion.ambientEnabled

    // The phase is read inside drawBehind, so it invalidates the draw phase only
    // and never triggers recomposition of the content above it.
    val phase: State<Float> = if (animated) {
        val transition = rememberInfiniteTransition(label = "aurora")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "auroraPhase"
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawStyle(style, phase.value, size) }
    ) {
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStyle(
    style: BackgroundStyle,
    phase: Float,
    size: Size
) {
    when (style) {
        BackgroundStyle.CYBER -> drawCyber(size)
        BackgroundStyle.NEON -> drawNeon(size)
        BackgroundStyle.DARK -> drawDark(size)
        BackgroundStyle.AURORA -> drawAurora(size, phase)
        BackgroundStyle.MINIMAL -> drawMinimal(size)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCyber(size: Size) {
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF07090F),
            0.55f to Color(0xFF0B0F1A),
            1f to Color(0xFF05060A)
        )
    )
    // A faint horizon grid: density is fixed, not derived from screen size, so the
    // number of draw calls stays constant on every device.
    val grid = Color(0xFF1B2333).copy(alpha = 0.55f)
    val columns = 10
    val rows = 8
    val step = size.width / columns
    for (i in 1 until columns) {
        val x = step * i
        drawLine(grid, Offset(x, size.height * 0.34f), Offset(x, size.height), strokeWidth = 1f)
    }
    for (i in 1..rows) {
        // Rows bunch towards the horizon to suggest perspective.
        val t = (i.toFloat() / rows)
        val y = size.height * (0.34f + 0.66f * t * t)
        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
    drawRect(
        Brush.radialGradient(
            colors = listOf(BoostColors.Ember.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.1f),
            radius = size.width * 0.85f
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNeon(size: Size) {
    drawRect(Color(0xFF06070C))
    drawRect(
        Brush.radialGradient(
            colors = listOf(BoostColors.Ember.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(size.width * 0.15f, size.height * 0.12f),
            radius = size.width * 0.9f
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(BoostColors.Ice.copy(alpha = 0.13f), Color.Transparent),
            center = Offset(size.width * 0.9f, size.height * 0.72f),
            radius = size.width * 0.95f
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDark(size: Size) {
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF0A0C13), Color(0xFF06070B))
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAurora(size: Size, phase: Float) {
    drawRect(Color(0xFF05060B))
    val drift = sin(phase * Math.PI.toFloat()) * 0.22f
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color(0xFF7C3AED).copy(alpha = 0.20f), Color.Transparent),
            center = Offset(size.width * (0.28f + drift), size.height * 0.22f),
            radius = size.width * 1.0f
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(BoostColors.Ice.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(size.width * (0.78f - drift), size.height * 0.58f),
            radius = size.width * 0.95f
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(BoostColors.Ember.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * (0.95f - drift * 0.5f)),
            radius = size.width * 0.8f
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMinimal(size: Size) {
    drawRect(Color(0xFF0B0C11))
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, BoostColors.Ember.copy(alpha = 0.5f), Color.Transparent)
        ),
        start = Offset(0f, size.height * 0.16f),
        end = Offset(size.width, size.height * 0.16f),
        strokeWidth = 1.5f
    )
}
