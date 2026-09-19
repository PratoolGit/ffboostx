package com.ffboostx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffboostx.core.ThermalAdvice
import com.ffboostx.core.ThermalLevel
import com.ffboostx.core.ThermalProfile
import com.ffboostx.ui.components.CardDivider
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.MetricRow
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.components.SegmentedSelector
import com.ffboostx.ui.theme.BoostColors

@Composable
fun ThermalScreen(
    level: ThermalLevel,
    batteryTempC: Float?,
    headroom: Float?,
    advice: ThermalAdvice,
    profile: ThermalProfile,
    charging: Boolean,
    onSelectProfile: (ThermalProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = severityColor(advice.severity)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Thermal",
            style = MaterialTheme.typography.displaySmall,
            color = BoostColors.TextPrimary
        )

        GlassCard(accent = accent) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = batteryTempC?.let { String.format("%.1f", it) } ?: "—",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = accent
                )
                Text(
                    text = if (batteryTempC != null) "°C" else "",
                    fontSize = 17.sp,
                    color = accent,
                    modifier = Modifier.padding(bottom = 8.dp, start = 3.dp)
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = advice.headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent
                    )
                    Text(
                        text = "Android level: ${level.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BoostColors.TextTertiary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "This is the battery's temperature, which is what Android exposes. It " +
                    "trails the processor, so the chip is usually a little hotter than the " +
                    "number above.",
                style = MaterialTheme.typography.labelSmall,
                color = BoostColors.TextTertiary
            )
        }

        GlassCard {
            MetricRow(
                label = "Thermal headroom",
                value = headroom?.let { "${(it * 100).toInt()}%" },
                hint = if (headroom == null) {
                    "Needs Android 11 and a thermal HAL that implements the forecast."
                } else {
                    "How close the device is to throttling. 100% means it is at the limit."
                },
                accent = when {
                    headroom == null -> BoostColors.TextTertiary
                    headroom >= 0.95f -> BoostColors.Danger
                    headroom >= 0.8f -> BoostColors.Warning
                    else -> BoostColors.Success
                }
            )
            CardDivider()
            MetricRow(
                label = "Charging",
                value = if (charging) "Yes" else "No",
                accent = if (charging) BoostColors.Warning else BoostColors.TextPrimary
            )
        }

        if (advice.suggestions.isNotEmpty()) {
            SectionTitle("What actually helps")
            GlassCard(accent = accent) {
                advice.suggestions.forEachIndexed { index, suggestion ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "→",
                            color = accent,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 9.dp, top = 1.dp)
                        )
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = BoostColors.TextSecondary
                        )
                    }
                    if (index != advice.suggestions.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }

        SectionTitle("Thermal profile")
        GlassCard {
            Text(
                text = profile.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            SegmentedSelector(
                options = ThermalProfile.entries,
                selected = profile,
                label = { it.label },
                onSelect = onSelectProfile
            )
        }

        GlassCard {
            Text(
                text = "FF BoostX will not claim to disable throttling",
                style = MaterialTheme.typography.titleMedium,
                color = BoostColors.TextPrimary
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Throttling is enforced below the Android framework to protect the " +
                    "hardware. No app, rooted or not, should be trying to switch it off. What " +
                    "this screen does is notice it early and tell you what genuinely reduces " +
                    "heat.",
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
        }
    }
}

private fun severityColor(severity: ThermalAdvice.Severity): Color = when (severity) {
    ThermalAdvice.Severity.OK -> BoostColors.Success
    ThermalAdvice.Severity.WATCH -> BoostColors.Warning
    ThermalAdvice.Severity.WARN -> BoostColors.Ember
    ThermalAdvice.Severity.CRITICAL -> BoostColors.Danger
}
