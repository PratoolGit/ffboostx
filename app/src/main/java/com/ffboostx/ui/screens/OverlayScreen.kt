package com.ffboostx.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.ffboostx.core.CrosshairStyle
import com.ffboostx.core.HudMetric
import com.ffboostx.core.OverlayConfig
import com.ffboostx.core.OverlayCorner
import com.ffboostx.ui.components.ActionButton
import com.ffboostx.ui.components.CardDivider
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.LabeledSlider
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.components.SegmentedSelector
import com.ffboostx.ui.components.ToggleRow
import com.ffboostx.ui.theme.BoostColors

private val CROSSHAIR_COLORS = listOf(
    0xFF4ADE80.toInt(),
    0xFFFF6B2C.toInt(),
    0xFF38BDF8.toInt(),
    0xFFF87171.toInt(),
    0xFFFFFFFF.toInt(),
    0xFFFBBF24.toInt()
)

@Composable
fun OverlayScreen(
    config: OverlayConfig,
    overlayPermitted: Boolean,
    usageAccessGranted: Boolean,
    serviceRunning: Boolean,
    onUpdate: ((OverlayConfig) -> OverlayConfig) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Overlay",
            style = MaterialTheme.typography.displaySmall,
            color = BoostColors.TextPrimary
        )
        Text(
            text = "Some games restrict or prohibit overlays. Check the game's rules before " +
                "using this feature. FF BoostX makes no claim that any overlay is undetectable.",
            style = MaterialTheme.typography.bodySmall,
            color = BoostColors.Warning
        )

        if (!overlayPermitted) {
            GlassCard(accent = BoostColors.Warning) {
                Text(
                    text = "Permission required",
                    style = MaterialTheme.typography.titleMedium,
                    color = BoostColors.TextPrimary
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Drawing over other apps needs the Display over other apps " +
                        "permission. The overlay is not touchable and not focusable, so every " +
                        "tap goes straight through to the game.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoostColors.TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    label = "Grant overlay permission",
                    onClick = onRequestOverlayPermission,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SectionTitle("Gaming HUD")

        GlassCard(accent = if (config.hudEnabled) BoostColors.Ember else null) {
            ToggleRow(
                title = "Show HUD",
                description = "A small panel of live values drawn over the game.",
                checked = config.hudEnabled,
                enabled = overlayPermitted,
                onCheckedChange = { on -> onUpdate { it.copy(hudEnabled = on) } }
            )
            CardDivider()
            Spacer(Modifier.height(10.dp))

            Text(
                text = "FPS is always shown as N/A",
                style = MaterialTheme.typography.bodyMedium,
                color = BoostColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Android does not expose another app's frame rate, and printing the " +
                    "display refresh rate in an FPS slot would be a fabricated number.",
                style = MaterialTheme.typography.labelSmall,
                color = BoostColors.TextTertiary
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Metrics",
                style = MaterialTheme.typography.bodyMedium,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            HudMetric.entries.forEach { metric ->
                val selected = metric in config.hudMetrics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onUpdate { current ->
                                val next = if (selected) {
                                    current.hudMetrics - metric
                                } else {
                                    current.hudMetrics + metric
                                }
                                current.copy(hudMetrics = next)
                            }
                        }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selected) "●" else "○",
                        color = if (selected) BoostColors.Ember else BoostColors.Muted
                    )
                    Spacer(Modifier.size(9.dp))
                    Text(
                        text = metric.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BoostColors.TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Position",
                style = MaterialTheme.typography.bodyMedium,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            SegmentedSelector(
                options = OverlayCorner.entries,
                selected = config.hudCorner,
                label = { corner -> corner.label.split(" ").joinToString("") { it.first().uppercase() } },
                onSelect = { corner -> onUpdate { it.copy(hudCorner = corner) } }
            )

            Spacer(Modifier.height(14.dp))
            LabeledSlider(
                label = "Opacity",
                value = config.hudOpacity,
                valueText = "${(config.hudOpacity * 100).toInt()}%",
                range = 0.25f..1f,
                onValueChange = { value -> onUpdate { it.copy(hudOpacity = value) } }
            )
            LabeledSlider(
                label = "Text size",
                value = config.hudTextSizeSp,
                valueText = "${config.hudTextSizeSp.toInt()} sp",
                range = 9f..18f,
                steps = 8,
                onValueChange = { value -> onUpdate { it.copy(hudTextSizeSp = value) } }
            )

            CardDivider()
            ToggleRow(
                title = "Auto-hide when the game closes",
                description = if (usageAccessGranted) {
                    "Usage access is granted, so the overlay comes down on its own."
                } else {
                    "Needs usage access. Without it you close the overlay from its notification."
                },
                checked = config.hudGamingOnly,
                onCheckedChange = { on -> onUpdate { it.copy(hudGamingOnly = on) } }
            )
            if (!usageAccessGranted) {
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    label = "Grant usage access",
                    onClick = onRequestUsageAccess,
                    accent = BoostColors.Ice,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SectionTitle("Crosshair")

        GlassCard(accent = if (config.crosshairEnabled) BoostColors.Ember else null) {
            ToggleRow(
                title = "Show crosshair",
                description = "A static reticle drawn at the centre of the screen. It does not " +
                    "move, track anything or read the screen - an overlay can draw, but it " +
                    "cannot see what is beneath it.",
                checked = config.crosshairEnabled,
                enabled = overlayPermitted,
                onCheckedChange = { on -> onUpdate { it.copy(crosshairEnabled = on) } }
            )
            CardDivider()
            Spacer(Modifier.height(12.dp))

            CrosshairPreview(config)

            Spacer(Modifier.height(14.dp))
            SegmentedSelector(
                options = CrosshairStyle.entries,
                selected = config.crosshairStyle,
                label = { it.label },
                onSelect = { style -> onUpdate { it.copy(crosshairStyle = style) } }
            )

            Spacer(Modifier.height(14.dp))
            LabeledSlider(
                label = "Size",
                value = config.crosshairSizeDp,
                valueText = "${config.crosshairSizeDp.toInt()} dp",
                range = 6f..40f,
                onValueChange = { value -> onUpdate { it.copy(crosshairSizeDp = value) } }
            )
            LabeledSlider(
                label = "Thickness",
                value = config.crosshairThicknessDp,
                valueText = String.format("%.1f dp", config.crosshairThicknessDp),
                range = 1f..6f,
                steps = 9,
                onValueChange = { value -> onUpdate { it.copy(crosshairThicknessDp = value) } }
            )
            LabeledSlider(
                label = "Opacity",
                value = config.crosshairOpacity,
                valueText = "${(config.crosshairOpacity * 100).toInt()}%",
                range = 0.2f..1f,
                onValueChange = { value -> onUpdate { it.copy(crosshairOpacity = value) } }
            )
            LabeledSlider(
                label = "Horizontal offset",
                value = config.crosshairOffsetXDp,
                valueText = "${config.crosshairOffsetXDp.toInt()} dp",
                range = -60f..60f,
                onValueChange = { value -> onUpdate { it.copy(crosshairOffsetXDp = value) } }
            )
            LabeledSlider(
                label = "Vertical offset",
                value = config.crosshairOffsetDp,
                valueText = "${config.crosshairOffsetDp.toInt()} dp",
                range = -60f..60f,
                onValueChange = { value -> onUpdate { it.copy(crosshairOffsetDp = value) } }
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Colour",
                style = MaterialTheme.typography.bodyMedium,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CROSSHAIR_COLORS.forEach { argb ->
                    val selected = argb == config.crosshairColor
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                BorderStroke(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) BoostColors.TextPrimary else BoostColors.Line
                                ),
                                CircleShape
                            )
                            .clickable { onUpdate { it.copy(crosshairColor = argb) } }
                    )
                }
            }
        }

        SectionTitle("Overlay service")
        GlassCard {
            Text(
                text = if (serviceRunning) {
                    "Running. A notification is showing with a stop action, and the windows come " +
                        "down when the session ends."
                } else {
                    "Not running. Starting it puts a foreground notification up for as long as " +
                        "the overlay is on screen."
                },
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    label = "Start overlay",
                    onClick = onStartOverlay,
                    enabled = overlayPermitted &&
                        (config.hudEnabled || config.crosshairEnabled) &&
                        !serviceRunning,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = "Stop overlay",
                    onClick = onStopOverlay,
                    accent = BoostColors.Danger,
                    enabled = serviceRunning,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Draws the reticle exactly as the overlay service will, at the same scale. */
@Composable
private fun CrosshairPreview(config: OverlayConfig) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF05060A))
            .border(BorderStroke(1.dp, BoostColors.LineSoft), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val cx = size.width / 2f + config.crosshairOffsetXDp.dp.toPx()
            val cy = size.height / 2f + config.crosshairOffsetDp.dp.toPx()
            val radius = config.crosshairSizeDp.dp.toPx()
            val thickness = config.crosshairThicknessDp.dp.toPx()
            val color = Color(config.crosshairColor).copy(alpha = config.crosshairOpacity)

            when (config.crosshairStyle) {
                CrosshairStyle.DOT ->
                    drawCircle(color, radius = thickness.coerceAtLeast(2f), center = Offset(cx, cy))

                CrosshairStyle.PLUS -> {
                    drawLine(color, Offset(cx - radius, cy), Offset(cx + radius, cy), thickness)
                    drawLine(color, Offset(cx, cy - radius), Offset(cx, cy + radius), thickness)
                }

                CrosshairStyle.CIRCLE ->
                    drawCircle(
                        color,
                        radius = radius,
                        center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(thickness)
                    )

                CrosshairStyle.PRECISION -> {
                    val gap = radius * 0.42f
                    drawLine(
                        color, Offset(cx - radius, cy), Offset(cx - gap, cy), thickness,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color, Offset(cx + gap, cy), Offset(cx + radius, cy), thickness,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color, Offset(cx, cy - radius), Offset(cx, cy - gap), thickness,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color, Offset(cx, cy + gap), Offset(cx, cy + radius), thickness,
                        cap = StrokeCap.Round
                    )
                    drawCircle(color, radius = thickness * 0.6f, center = Offset(cx, cy))
                }

                CrosshairStyle.MINIMAL -> {
                    val short = radius * 0.5f
                    drawLine(color, Offset(cx - short, cy), Offset(cx + short, cy), thickness)
                }
            }
        }
    }
}
