package com.ffboostx.ui.boost

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffboostx.core.BoostPhase
import com.ffboostx.core.BoostProgress
import com.ffboostx.core.BoostStep
import com.ffboostx.core.StepOutcome
import com.ffboostx.ui.components.DialogButton
import com.ffboostx.ui.components.PremiumDialog
import com.ffboostx.ui.theme.BoostColors
import com.ffboostx.ui.theme.LocalMotion

/**
 * The boost overlay popup.
 *
 * Rows appear as the engine finishes each step, each carrying the outcome the
 * platform actually returned. Nothing is pre-populated with ticks, and the
 * progress bar tracks real completion rather than a timer.
 */
@Composable
fun BoostPopup(
    progress: BoostProgress,
    onDismiss: () -> Unit,
    onLaunchGame: () -> Unit
) {
    val motion = LocalMotion.current
    val done = progress.phase == BoostPhase.DONE
    val closeInteraction = remember { MutableInteractionSource() }

    val fraction by animateFloatAsState(
        targetValue = progress.fraction.coerceIn(0f, 1f),
        animationSpec = tween(motion.durationMs(320)),
        label = "boostFraction"
    )

    PremiumDialog(
        onDismissRequest = { if (done) onDismiss() },
        // Closing halfway would leave the sequence part applied.
        dismissOnOutsideTouch = done
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(BoostColors.Ember.copy(alpha = 0.18f))
                    .border(
                        BorderStroke(1.dp, BoostColors.Ember.copy(alpha = 0.45f)),
                        RoundedCornerShape(9.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 15.sp)
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (done) "Game Ready" else "Boosting",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done) BoostColors.Success else BoostColors.TextPrimary
                )
                Text(
                    text = progress.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = BoostColors.TextSecondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            if (done) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BoostColors.Surface)
                        .border(BorderStroke(1.dp, BoostColors.Line), CircleShape)
                        .clickable(
                            interactionSource = closeInteraction,
                            indication = null,
                            onClick = onDismiss
                        )
                        .semantics { contentDescription = "Close boost report" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = BoostColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        ProgressBar(fraction)

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Capped so the popup stays compact on a short screen.
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            progress.steps.forEach { step -> StepLine(step) }
        }

        if (done) {
            Spacer(Modifier.height(14.dp))
            Tally(progress)
            Spacer(Modifier.height(14.dp))

            if (progress.launchPackage != null) {
                DialogButton(
                    label = "Launch game",
                    filled = true,
                    onClick = onLaunchGame,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DialogButton(
                    label = "Close",
                    filled = false,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                DialogButton(
                    label = "Done",
                    filled = true,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(BoostColors.Line)
                .drawBehind {
                    drawRoundRect(
                        color = BoostColors.Ember,
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = "${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = BoostColors.TextSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StepLine(step: BoostStep) {
    val color = when (step.outcome) {
        StepOutcome.APPLIED -> BoostColors.Success
        StepOutcome.LIMITED -> BoostColors.Warning
        StepOutcome.SKIPPED, StepOutcome.UNAVAILABLE -> BoostColors.Muted
        StepOutcome.FAILED -> BoostColors.Danger
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${step.title}: ${step.outcome.label}. ${step.detail}"
            }
    ) {
        Text(
            text = step.outcome.glyph,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 9.dp, top = 2.dp)
        )
        Column(Modifier.weight(1f)) {
            Row {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoostColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = step.reading ?: step.outcome.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
            Text(
                text = step.detail,
                style = MaterialTheme.typography.labelSmall,
                color = BoostColors.TextTertiary
            )
        }
    }
}

@Composable
private fun Tally(progress: BoostProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Cell("Applied", progress.count(StepOutcome.APPLIED), BoostColors.Success)
        Cell("Limited", progress.count(StepOutcome.LIMITED), BoostColors.Warning)
        Cell(
            "Unavailable",
            progress.count(StepOutcome.UNAVAILABLE) + progress.count(StepOutcome.SKIPPED),
            BoostColors.Muted
        )
        Cell("Failed", progress.count(StepOutcome.FAILED), BoostColors.Danger)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    label: String,
    value: Int,
    color: Color
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(11.dp))
            .background(BoostColors.Void.copy(alpha = 0.55f))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = BoostColors.TextTertiary,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}
