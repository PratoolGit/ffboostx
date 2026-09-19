package com.ffboostx.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffboostx.core.CapabilityState
import com.ffboostx.ui.theme.BoostColors
import com.ffboostx.ui.theme.LocalMotion

/**
 * Scales a component slightly while pressed. Uses the graphics layer so the
 * effect runs on the render thread rather than re-laying out the subtree, and
 * collapses to a no-op when the motion budget disables transitions.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.975f
): Modifier {
    val motion = LocalMotion.current
    if (!motion.transitionsEnabled) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(motion.durationMs(110)),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The standard container: a dark translucent panel with a hairline border. No
 * blur is used anywhere in the app - it is expensive below flagship hardware and
 * unsupported before Android 12 - so the glass effect comes from alpha and the
 * border instead.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val borderColor = accent?.copy(alpha = 0.34f) ?: BoostColors.Line

    var base = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.pressScale(interactionSource) else Modifier)
        .clip(RoundedCornerShape(20.dp))
        .background(
            Brush.verticalGradient(
                listOf(
                    BoostColors.SurfaceRaised.copy(alpha = 0.92f),
                    BoostColors.Surface.copy(alpha = 0.86f)
                )
            )
        )
        .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(20.dp))

    if (onClick != null) {
        base = base.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }

    Column(modifier = base.padding(contentPadding), content = content)
}

/**
 * Section heading. Sentence case rather than a tracked-out all-caps eyebrow, with
 * an optional trailing note carrying real information.
 */
@Composable
fun SectionTitle(
    title: String,
    note: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = BoostColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = BoostColors.TextTertiary
            )
        }
    }
}

/**
 * A single measurement. [value] is the number, [unit] its unit, [caption] the
 * label. When a statistic is unavailable the caller passes the reason as [value]
 * with a null unit so the tile reads honestly instead of showing a zero.
 */
@Composable
fun StatTile(
    caption: String,
    value: String,
    unit: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    fraction: Float? = null,
    /** True when [value] is an explanation ("Restricted by Android") rather than a number. */
    dim: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BoostColors.Surface.copy(alpha = 0.9f))
            .border(BorderStroke(1.dp, BoostColors.LineSoft), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .semantics {
                contentDescription = if (unit != null) "$caption: $value $unit" else "$caption: $value"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = BoostColors.TextSecondary,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = if (dim) 12.5.sp else 20.sp,
                lineHeight = if (dim) 16.sp else 24.sp,
                fontWeight = if (dim) FontWeight.Medium else FontWeight.Bold,
                color = if (dim) BoostColors.TextTertiary else BoostColors.TextPrimary,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
        if (fraction != null) {
            val clamped = fraction.coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(BoostColors.Line)
                    .drawBehind {
                        drawRoundRect(
                            color = accent,
                            size = androidx.compose.ui.geometry.Size(size.width * clamped, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                size.height / 2f, size.height / 2f
                            )
                        )
                    }
            )
        }
    }
}

/**
 * Status chip. Each state has its own colour *and* its own glyph, so the meaning
 * survives for anyone who cannot distinguish the colours.
 */
@Composable
fun StatusPill(state: CapabilityState, modifier: Modifier = Modifier) {
    val (color, glyph) = when (state) {
        CapabilityState.ACTIVE -> BoostColors.Success to "●"
        CapabilityState.AVAILABLE -> BoostColors.Ice to "○"
        CapabilityState.PERMISSION_REQUIRED -> BoostColors.Warning to "!"
        CapabilityState.UNSUPPORTED -> BoostColors.Muted to "×"
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.13f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.34f)), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = state.label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(glyph, color = color, fontSize = 10.sp, modifier = Modifier.clearAndSetSemantics {})
        Spacer(Modifier.width(5.dp))
        Text(
            text = state.label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/**
 * The single loudest element in the app. A slow ember pulse runs behind it only
 * while ambient animation is allowed and only while it is composed, so it stops
 * the moment the screen is backgrounded.
 */
@Composable
fun BoostButton(
    active: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalMotion.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val glow: State<Float> = if (motion.ambientEnabled && !busy) {
        val transition = rememberInfiniteTransition(label = "boostGlow")
        transition.animateFloat(
            initialValue = 0.28f,
            targetValue = 0.62f,
            animationSpec = infiniteRepeatable(
                animation = tween(2_200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "boostGlowAlpha"
        )
    } else {
        remember { mutableFloatStateOf(0.34f) }
    }

    val label = when {
        busy -> "Boosting"
        active -> "Boost again"
        else -> "Boost now"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .pressScale(interactionSource, pressedScale = 0.965f)
            // The halo is drawn behind the button rather than as a shadow layer.
            .drawBehind {
                drawRoundRect(
                    color = BoostColors.Ember.copy(alpha = glow.value * 0.30f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.30f),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(56f, 56f)
                )
            }
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (busy) {
                    Brush.horizontalGradient(
                        listOf(BoostColors.EmberDim, BoostColors.EmberDim.copy(alpha = 0.7f))
                    )
                } else {
                    BoostColors.EmberGradient
                }
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !busy
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "$label. Optimises this device using safe, reversible settings."
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = Color(0xFF200A00)
            )
            Text(
                text = if (busy) "Applying safe settings" else "Safe, reversible optimisation",
                fontSize = 11.sp,
                color = Color(0xFF3A1200).copy(alpha = 0.85f)
            )
        }
    }
}

/** A labelled switch row with a required explanation line beneath it. */
@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trailingBadge: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) BoostColors.TextPrimary else BoostColors.TextTertiary
                )
                if (trailingBadge != null) {
                    Spacer(Modifier.width(8.dp))
                    trailingBadge()
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF200A00),
                checkedTrackColor = BoostColors.Ember,
                checkedBorderColor = BoostColors.Ember,
                uncheckedThumbColor = BoostColors.TextTertiary,
                uncheckedTrackColor = BoostColors.Surface,
                uncheckedBorderColor = BoostColors.Line
            )
        )
    }
}

/**
 * Horizontal option selector. Replaces Material's FilterChip row so the selected
 * item can carry the ember accent and the touch targets stay above 48dp.
 */
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BoostColors.Void.copy(alpha = 0.55f))
            .border(BorderStroke(1.dp, BoostColors.LineSoft), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val motion = LocalMotion.current
            val bg by animateColorAsState(
                targetValue = if (isSelected) BoostColors.Ember.copy(alpha = 0.18f) else Color.Transparent,
                animationSpec = tween(motion.durationMs(160)),
                label = "segmentBg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                BorderStroke(1.dp, BoostColors.Ember.copy(alpha = 0.55f)),
                                RoundedCornerShape(11.dp)
                            )
                        } else Modifier
                    )
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) BoostColors.Ember else BoostColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/** Circular determinate progress used by the boost popup. */
@Composable
fun BoostRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 116.dp,
    content: @Composable () -> Unit
) {
    val motion = LocalMotion.current
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(motion.durationMs(500)),
        label = "boostRing"
    )
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val stroke = Stroke(width = 9f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val inset = stroke.width / 2f
                val arcSize = androidx.compose.ui.geometry.Size(
                    this.size.width - stroke.width,
                    this.size.height - stroke.width
                )
                drawArc(
                    color = BoostColors.Line,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = stroke
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(BoostColors.Ember, BoostColors.Ice, BoostColors.Ember)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = stroke
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Outlined action used across every screen for a secondary tap target. */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = BoostColors.Ember,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tint = if (enabled) accent else BoostColors.Muted
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .then(if (enabled) Modifier.pressScale(interactionSource) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.45f)), RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = if (enabled) 0.09f else 0.04f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 15.dp, vertical = 11.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * A label/value pair. A null [value] renders as "not measured" in muted type,
 * which is how every unavailable reading in the app is drawn.
 */
@Composable
fun MetricRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    accent: Color = BoostColors.TextPrimary,
    hint: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = BoostColors.TextSecondary
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = BoostColors.TextTertiary
                )
            }
        }
        Text(
            text = value ?: "not measured",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
            color = if (value != null) accent else BoostColors.TextTertiary,
            textAlign = TextAlign.End
        )
    }
}

/** Thin horizontal rule used between rows inside a card. */
@Composable
fun CardDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BoostColors.LineSoft)
    )
}

/** A labelled slider with the current value shown on the right of the label. */
@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = BoostColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = BoostColors.Ember,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = BoostColors.Ember,
                activeTrackColor = BoostColors.Ember,
                inactiveTrackColor = BoostColors.Line
            ),
            modifier = Modifier.semantics { contentDescription = "$label, $valueText" }
        )
    }
}
