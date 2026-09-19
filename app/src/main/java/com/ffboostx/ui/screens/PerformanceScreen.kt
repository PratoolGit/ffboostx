package com.ffboostx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ffboostx.core.DeviceSnapshot
import com.ffboostx.core.PerformanceProfile
import com.ffboostx.core.RefreshRate
import com.ffboostx.core.reasonOrNull
import com.ffboostx.core.toGbString
import com.ffboostx.core.valueOrNull
import com.ffboostx.ui.components.ActionButton
import com.ffboostx.ui.components.CardDivider
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.MetricRow
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.components.SegmentedSelector
import com.ffboostx.ui.components.StatTile
import com.ffboostx.ui.theme.BoostColors

@Composable
fun PerformanceScreen(
    snapshot: DeviceSnapshot?,
    appRenderFps: Float?,
    profile: PerformanceProfile,
    detectedProfile: PerformanceProfile,
    refreshRate: RefreshRate,
    intervalMs: Long,
    sustainedSupported: Boolean,
    hintFrameworkPresent: Boolean,
    gameManagerPresent: Boolean,
    onSelectProfile: (PerformanceProfile?) -> Unit,
    onSelectRefreshRate: (RefreshRate) -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Performance",
            style = MaterialTheme.typography.displaySmall,
            color = BoostColors.TextPrimary
        )

        SectionTitle("Live", note = "every ${intervalMs / 1000}s")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                caption = "Memory in use",
                value = snapshot?.memory?.let { "${(it.usedFraction * 100).toInt()}" } ?: "–",
                unit = "%",
                accent = BoostColors.Ember,
                fraction = snapshot?.memory?.usedFraction,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                caption = "Memory free",
                value = snapshot?.memory?.availableBytes?.toGbString() ?: "–",
                unit = null,
                accent = BoostColors.Ice,
                modifier = Modifier.weight(1f)
            )
        }

        GlassCard {
            val cpu = snapshot?.cpu
            MetricRow(
                label = "CPU load",
                value = cpu?.load?.valueOrNull()?.let { "${(it * 100).toInt()}%" },
                hint = cpu?.load?.reasonOrNull()
                    ?: "System-wide load from /proc/stat where the kernel allows it."
            )
            CardDivider()
            MetricRow(label = "Cores", value = cpu?.cores?.toString())
            CardDivider()
            MetricRow(
                label = "GPU load",
                value = snapshot?.gpu?.load?.valueOrNull()?.let { "${(it * 100).toInt()}%" },
                hint = snapshot?.gpu?.load?.reasonOrNull() ?: "Read-only vendor metric when Android exposes it.",
                accent = BoostColors.Ice
            )
            CardDivider()
            MetricRow(
                label = "Max clock",
                value = cpu?.maxClockMhz?.valueOrNull()?.let {
                    String.format("%.2f GHz", it / 1000f)
                },
                hint = cpu?.maxClockMhz?.reasonOrNull()
            )
            CardDivider()
            MetricRow(
                label = "Memory pressure",
                value = snapshot?.memory?.let {
                    when {
                        it.lowMemory -> "Critical"
                        it.usedFraction > 0.80f -> "Elevated"
                        else -> "Normal"
                    }
                },
                hint = "Android's own low-memory flag, not a threshold this app invented.",
                accent = if (snapshot?.memory?.lowMemory == true) {
                    BoostColors.Danger
                } else {
                    BoostColors.TextPrimary
                }
            )
        }

        SectionTitle("Frame rate")

        GlassCard {
            MetricRow(
                label = "Game FPS",
                value = null,
                hint = "Another app's frame rate is not readable on Android. FF BoostX prints " +
                    "N/A rather than echoing the refresh rate back at you."
            )
            CardDivider()
            MetricRow(
                label = "FF BoostX render rate",
                value = appRenderFps?.let { "${it.toInt()} fps" },
                hint = "Measured from this app's own Choreographer. It says how smoothly the " +
                    "booster itself is drawing, nothing more.",
                accent = BoostColors.Ice
            )
            CardDivider()
            MetricRow(
                label = "Display refresh",
                value = snapshot?.display?.let { display ->
                    val current = display.currentRefreshHz.toInt()
                    val max = display.maxRefreshHz.toInt()
                    if (max > current) "$current of $max Hz" else "$current Hz"
                },
                accent = BoostColors.Ember
            )
        }

        SectionTitle("Android performance frameworks")

        GlassCard {
            MetricRow(
                label = "Sustained performance mode",
                value = if (sustainedSupported) "Supported" else "Not advertised",
                hint = "A stable clock ceiling the app can request for its own window.",
                accent = if (sustainedSupported) BoostColors.Success else BoostColors.TextTertiary
            )
            CardDivider()
            MetricRow(
                label = "Performance hints (ADPF)",
                value = if (hintFrameworkPresent) "Present" else "Not available",
                hint = "A hint session only covers the creating app's own threads, so it cannot " +
                    "be used on Free Fire's behalf.",
                accent = if (hintFrameworkPresent) BoostColors.Warning else BoostColors.TextTertiary
            )
            CardDivider()
            MetricRow(
                label = "GameManager",
                value = if (gameManagerPresent) "Present" else "Not available",
                hint = "Reports and sets game mode for the calling app only. Free Fire's game " +
                    "mode belongs to the system Game Dashboard.",
                accent = if (gameManagerPresent) BoostColors.Warning else BoostColors.TextTertiary
            )
            CardDivider()
            MetricRow(
                label = "CPU / GPU governor",
                value = "Root only",
                hint = "FF BoostX does not read or write /sys and runs no shell commands.",
                accent = BoostColors.Muted
            )
            Spacer(Modifier.height(12.dp))
            ActionButton(
                label = "Open developer options",
                onClick = onOpenDeveloperOptions,
                accent = BoostColors.Ice,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionTitle("Booster profile", note = "detected: ${detectedProfile.label}")

        GlassCard {
            Text(
                text = profile.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "This governs how much work FF BoostX itself is allowed to do. It is not " +
                    "a claim about changing the device's hardware performance.",
                style = MaterialTheme.typography.labelSmall,
                color = BoostColors.TextTertiary
            )
            Spacer(Modifier.height(12.dp))
            SegmentedSelector(
                options = listOf("Auto") + PerformanceProfile.entries.map { it.label },
                selected = if (profile == detectedProfile) "Auto" else profile.label,
                label = { it },
                onSelect = { label ->
                    onSelectProfile(PerformanceProfile.entries.firstOrNull { it.label == label })
                }
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Statistics refresh",
                style = MaterialTheme.typography.bodyMedium,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            SegmentedSelector(
                options = RefreshRate.entries,
                selected = refreshRate,
                label = { if (it == RefreshRate.AUTO) "Auto" else "${it.intervalMs!! / 1000}s" },
                onSelect = onSelectRefreshRate
            )
        }
    }
}
