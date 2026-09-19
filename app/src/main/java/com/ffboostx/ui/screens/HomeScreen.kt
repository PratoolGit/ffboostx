package com.ffboostx.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffboostx.core.DeviceSnapshot
import com.ffboostx.core.NetworkQuality
import com.ffboostx.core.PerformanceProfile
import com.ffboostx.core.ThermalAdvice
import com.ffboostx.core.ThermalLevel
import com.ffboostx.core.reasonOrNull
import com.ffboostx.core.toGbString
import com.ffboostx.core.valueOrNull
import com.ffboostx.ui.components.BoostButton
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.components.StatTile
import com.ffboostx.ui.theme.BoostColors

@Composable
fun HomeScreen(
    snapshot: DeviceSnapshot?,
    profile: PerformanceProfile,
    thermalLevel: ThermalLevel,
    advice: ThermalAdvice,
    quality: NetworkQuality?,
    transport: String,
    gamingModeOn: Boolean,
    overlayRunning: Boolean,
    blockerActive: Boolean,
    sessionMinutes: Int?,
    boosting: Boolean,
    onBoost: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "FF BOOSTX",
                style = MaterialTheme.typography.displaySmall,
                color = BoostColors.TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = snapshot?.memory?.totalBytes?.let { "${it.toGbString()} device" }
                        ?: "Reading device",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoostColors.TextSecondary
                )
                Spacer(Modifier.padding(horizontal = 5.dp))
                Badge("${profile.label} profile", BoostColors.Ice)
            }
        }

        BoostButton(active = gamingModeOn, busy = boosting, onClick = onBoost)

        StatusStrip(
            gamingModeOn = gamingModeOn,
            overlayRunning = overlayRunning,
            blockerActive = blockerActive,
            sessionMinutes = sessionMinutes
        )

        SectionTitle("Device", note = if (snapshot == null) "reading" else "live")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                caption = "Memory in use",
                value = snapshot?.memory?.let { "${(it.usedFraction * 100).toInt()}" } ?: "–",
                unit = "%",
                accent = memoryAccent(snapshot),
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CpuTile(snapshot, Modifier.weight(1f))
            StatTile(
                caption = if (snapshot?.battery?.charging == true) "Battery (charging)"
                else "Battery",
                value = snapshot?.battery?.percent?.toString() ?: "–",
                unit = "%",
                accent = batteryAccent(snapshot),
                fraction = snapshot?.battery?.percent?.let { it / 100f },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TemperatureTile(snapshot, Modifier.weight(1f))
            StatTile(
                caption = "Throttling",
                value = thermalLevel.label,
                unit = null,
                accent = when (thermalLevel) {
                    ThermalLevel.NONE, ThermalLevel.LIGHT -> BoostColors.Success
                    ThermalLevel.MODERATE -> BoostColors.Warning
                    else -> BoostColors.Danger
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (advice.severity != ThermalAdvice.Severity.OK) {
            GlassCard(accent = BoostColors.Warning) {
                Text(
                    text = advice.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = BoostColors.Warning
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = advice.suggestions.firstOrNull()
                        ?: "Keep an eye on the temperature.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoostColors.TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Open the Thermal tab for the rest. FF BoostX cannot disable " +
                        "throttling and will not pretend to.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BoostColors.TextTertiary
                )
            }
        }

        SectionTitle("Network", note = transport)

        GlassCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniFact(
                    "Latency",
                    quality?.avgMs?.let { "${it.toInt()} ms" } ?: "not measured",
                    Modifier.weight(1f)
                )
                MiniFact(
                    "Jitter",
                    quality?.jitterMs?.let { "${it.toInt()} ms" } ?: "not measured",
                    Modifier.weight(1f)
                )
                MiniFact(
                    "Stability",
                    quality?.let { "${it.stabilityPercent}%" } ?: "—",
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "TCP handshake round trip, not ICMP ping and not your in-match ping. " +
                    "Run a full test from the Network tab.",
                style = MaterialTheme.typography.labelSmall,
                color = BoostColors.TextTertiary
            )
        }
    }
}

@Composable
private fun StatusStrip(
    gamingModeOn: Boolean,
    overlayRunning: Boolean,
    blockerActive: Boolean,
    sessionMinutes: Int?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BoostColors.Surface.copy(alpha = 0.8f))
            .border(BorderStroke(1.dp, BoostColors.LineSoft), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = when {
                    sessionMinutes != null -> "Session running · $sessionMinutes min"
                    gamingModeOn -> "Gaming Mode is on"
                    else -> "Gaming Mode is off"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = BoostColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = buildList {
                    if (overlayRunning) add("overlay up")
                    if (blockerActive) add("background apps blocked")
                    if (isEmpty()) add("nothing is currently applied")
                }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
        }
        Text(
            text = if (gamingModeOn) "●" else "○",
            color = if (gamingModeOn) BoostColors.Success else BoostColors.Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CpuTile(snapshot: DeviceSnapshot?, modifier: Modifier) {
    val cpu = snapshot?.cpu
    val load = cpu?.load?.valueOrNull()
    if (load != null) {
        StatTile(
            caption = "CPU load",
            value = "${(load * 100).toInt()}",
            unit = "%",
            accent = if (load > 0.85f) BoostColors.Warning else BoostColors.Ice,
            fraction = load,
            modifier = modifier
        )
    } else {
        // Restricted on most modern builds. Show the facts the platform does
        // expose rather than inventing a percentage.
        val clock = cpu?.maxClockMhz?.valueOrNull()
        StatTile(
            caption = "CPU",
            value = when {
                cpu == null -> "–"
                clock != null -> "${cpu.cores} cores · ${"%.1f".format(clock / 1000f)} GHz"
                else -> "${cpu.cores} cores"
            },
            unit = null,
            accent = BoostColors.Ice,
            dim = true,
            modifier = modifier
        )
    }
}

@Composable
private fun TemperatureTile(snapshot: DeviceSnapshot?, modifier: Modifier) {
    val reading = snapshot?.battery?.temperatureC
    val temp = reading?.valueOrNull()
    when {
        temp != null -> StatTile(
            caption = "Battery temp",
            value = "%.1f".format(temp),
            unit = "°C",
            accent = when {
                temp >= 45f -> BoostColors.Danger
                temp >= 40f -> BoostColors.Warning
                else -> BoostColors.Success
            },
            modifier = modifier
        )
        reading != null -> StatTile(
            caption = "Battery temp",
            value = reading.reasonOrNull() ?: "Unavailable",
            unit = null,
            accent = BoostColors.Muted,
            dim = true,
            modifier = modifier
        )
        else -> StatTile("Battery temp", "–", "°C", BoostColors.Ice, modifier)
    }
}

@Composable
private fun MiniFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(BoostColors.Void.copy(alpha = 0.5f))
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = BoostColors.TextTertiary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = BoostColors.TextPrimary
        )
    }
}

private fun memoryAccent(snapshot: DeviceSnapshot?): Color {
    val memory = snapshot?.memory ?: return BoostColors.Ice
    return when {
        memory.lowMemory || memory.usedFraction > 0.90f -> BoostColors.Danger
        memory.usedFraction > 0.75f -> BoostColors.Warning
        else -> BoostColors.Success
    }
}

private fun batteryAccent(snapshot: DeviceSnapshot?): Color {
    val battery = snapshot?.battery ?: return BoostColors.Ice
    return when {
        battery.percent <= 15 -> BoostColors.Danger
        battery.percent <= 30 -> BoostColors.Warning
        else -> BoostColors.Success
    }
}
