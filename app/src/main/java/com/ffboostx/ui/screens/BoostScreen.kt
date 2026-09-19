package com.ffboostx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ffboostx.core.BoostProgress
import com.ffboostx.core.BoostSnapshot
import com.ffboostx.core.BoostStep
import com.ffboostx.core.StepOutcome
import com.ffboostx.core.toGbString
import com.ffboostx.ui.components.ActionButton
import com.ffboostx.ui.components.BoostButton
import com.ffboostx.ui.components.CardDivider
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.MetricRow
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.theme.BoostColors

@Composable
fun BoostScreen(
    lastResult: BoostProgress?,
    boosting: Boolean,
    gamingModeOn: Boolean,
    profileName: String?,
    pendingChanges: Boolean,
    dndGranted: Boolean,
    writeSettingsGranted: Boolean,
    overlayGranted: Boolean,
    onBoost: () -> Unit,
    onRestore: () -> Unit,
    onRequestDnd: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onRequestOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Boost",
            style = MaterialTheme.typography.displaySmall,
            color = BoostColors.TextPrimary
        )
        Text(
            text = profileName?.let { "Using the $it profile." }
                ?: "No game profile selected, so only the device checks will run.",
            style = MaterialTheme.typography.bodySmall,
            color = BoostColors.TextSecondary
        )

        BoostButton(active = gamingModeOn, busy = boosting, onClick = onBoost)

        if (pendingChanges) {
            GlassCard(accent = BoostColors.Ember) {
                Text(
                    text = "System settings are currently overridden",
                    style = MaterialTheme.typography.titleMedium,
                    color = BoostColors.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Do Not Disturb, brightness or the screen timeout are set by " +
                        "FF BoostX. Their previous values are saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoostColors.TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    label = "Restore everything",
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val missing = buildList {
            if (!dndGranted) {
                add(Triple("Notification policy", "Needed for Gaming Focus.", onRequestDnd))
            }
            if (!writeSettingsGranted) {
                add(
                    Triple(
                        "Modify system settings",
                        "Needed for brightness and screen timeout.",
                        onRequestWriteSettings
                    )
                )
            }
            if (!overlayGranted) {
                add(
                    Triple(
                        "Display over other apps",
                        "Needed for the HUD and crosshair.",
                        onRequestOverlay
                    )
                )
            }
        }

        if (missing.isNotEmpty()) {
            SectionTitle("Permissions", note = "${missing.size} not granted")
            GlassCard(accent = BoostColors.Warning) {
                Text(
                    text = "Steps that need one of these will report as unavailable rather " +
                        "than silently doing nothing. None of them is required to run a boost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoostColors.TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                missing.forEach { (title, why, request) ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BoostColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = why,
                        style = MaterialTheme.typography.labelSmall,
                        color = BoostColors.TextTertiary
                    )
                    Spacer(Modifier.height(7.dp))
                    ActionButton(
                        label = "Grant",
                        onClick = request,
                        accent = BoostColors.Warning,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        if (lastResult == null) {
            GlassCard {
                Text(
                    text = "What a boost actually does",
                    style = MaterialTheme.typography.titleMedium,
                    color = BoostColors.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Seven checks that read the device, then six steps that change only " +
                        "what you enabled: display mode for this window, keep-awake, sustained " +
                        "performance, Gaming Focus, brightness and the overlay. Each one reports " +
                        "applied, limited, skipped, unavailable or failed, and every change is " +
                        "put back when the session ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoostColors.TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "There is no step that clears RAM, raises a clock or speeds up the " +
                        "network, because no app can do any of those without root.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BoostColors.TextTertiary
                )
            }
            return@Column
        }

        SectionTitle("Last run", note = "${lastResult.steps.size} steps")

        GlassCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Count("Applied", lastResult.count(StepOutcome.APPLIED), BoostColors.Success)
                Count("Limited", lastResult.count(StepOutcome.LIMITED), BoostColors.Warning)
                Count(
                    "Unavailable",
                    lastResult.count(StepOutcome.UNAVAILABLE) +
                        lastResult.count(StepOutcome.SKIPPED),
                    BoostColors.Muted
                )
                Count("Failed", lastResult.count(StepOutcome.FAILED), BoostColors.Danger)
            }
        }

        GlassCard(contentPadding = 14.dp) {
            lastResult.steps.forEachIndexed { index, step ->
                StepRow(step)
                if (index != lastResult.steps.lastIndex) Spacer(Modifier.height(11.dp))
            }
        }

        val before = lastResult.before
        val after = lastResult.after
        if (before != null && after != null) {
            SectionTitle("Before and after", note = "measured only")
            GlassCard {
                Comparison(before, after)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "These are two readings a few seconds apart. Memory and temperature " +
                        "move on their own as other apps come and go, so a small difference is " +
                        "not evidence that anything improved. FF BoostX will not interpret it " +
                        "as one.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BoostColors.TextTertiary
                )
            }
        }
    }
}

@Composable
private fun Comparison(before: BoostSnapshot, after: BoostSnapshot) {
    MetricRow(
        label = "Free memory",
        value = "${before.freeMemoryBytes.toGbString()} → ${after.freeMemoryBytes.toGbString()}"
    )
    CardDivider()
    MetricRow(
        label = "Memory pressure",
        value = "${before.memoryPressure} → ${after.memoryPressure}"
    )
    CardDivider()
    MetricRow(
        label = "Latency",
        value = after.latencyMs?.let { "${it.toInt()} ms" },
        hint = "Measured once during the run; there is no before value to compare against."
    )
    CardDivider()
    MetricRow(
        label = "Battery temperature",
        value = if (before.batteryTempC != null && after.batteryTempC != null) {
            String.format("%.1f°C → %.1f°C", before.batteryTempC, after.batteryTempC)
        } else {
            null
        }
    )
    CardDivider()
    MetricRow(
        label = "Battery",
        value = "${before.batteryPercent}% → ${after.batteryPercent}%"
    )
    CardDivider()
    MetricRow(
        label = "Throttling",
        value = "${before.thermal.label} → ${after.thermal.label}"
    )
}

@Composable
private fun StepRow(step: BoostStep) {
    val color = when (step.outcome) {
        StepOutcome.APPLIED -> BoostColors.Success
        StepOutcome.LIMITED -> BoostColors.Warning
        StepOutcome.SKIPPED -> BoostColors.Muted
        StepOutcome.UNAVAILABLE -> BoostColors.Muted
        StepOutcome.FAILED -> BoostColors.Danger
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = step.outcome.glyph,
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 9.dp)
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
                step.reading?.let { reading ->
                    Text(
                        text = reading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BoostColors.Ice,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = step.detail,
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
        }
    }
}

@Composable
private fun Count(label: String, value: Int, color: Color) {
    Column {
        Text(
            text = "$value",
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = BoostColors.TextTertiary
        )
    }
}
