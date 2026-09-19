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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ffboostx.core.LiveSession
import com.ffboostx.core.SessionRecord
import com.ffboostx.core.toGbString
import com.ffboostx.ui.components.ActionButton
import com.ffboostx.ui.components.CardDivider
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.MetricRow
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.theme.BoostColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    live: LiveSession?,
    liveMinutes: Int,
    history: List<SessionRecord>,
    onEndSession: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "History",
            style = MaterialTheme.typography.displaySmall,
            color = BoostColors.TextPrimary
        )

        if (live != null) {
            SectionTitle("Session in progress")
            GlassCard(accent = BoostColors.Success) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = live.gameName,
                            style = MaterialTheme.typography.titleMedium,
                            color = BoostColors.TextPrimary
                        )
                        Text(
                            text = "$liveMinutes min so far · " +
                                "${live.pingSamples.size} network samples",
                            style = MaterialTheme.typography.bodySmall,
                            color = BoostColors.TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    label = "End session and write report",
                    onClick = onEndSession,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SectionTitle("Past sessions", note = "${history.size} saved")

        if (history.isEmpty()) {
            GlassCard {
                Text(
                    text = "No sessions recorded yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = BoostColors.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Boost and launch a game from the Game screen, then end the session " +
                        "to write the first report.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoostColors.TextSecondary
                )
            }
        } else {
            history.forEach { record -> SessionCard(record) }

            ActionButton(
                label = "Clear history",
                onClick = onClearHistory,
                accent = BoostColors.Danger,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SessionCard(record: SessionRecord) {
    GlassCard {
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.gameName,
                    style = MaterialTheme.typography.titleMedium,
                    color = BoostColors.TextPrimary
                )
                Text(
                    text = formatDate(record.startedAtEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = BoostColors.TextTertiary
                )
            }
            Text(
                text = "${record.durationMinutes} min",
                style = MaterialTheme.typography.titleMedium,
                color = BoostColors.Ember,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))
        CardDivider()

        MetricRow(
            label = "Average latency",
            value = record.avgPingMs?.let { "${it.toInt()} ms" },
            hint = "TCP handshake round trip, sampled while the overlay was up."
        )
        CardDivider()
        MetricRow(
            label = "Worst latency",
            value = record.maxPingMs?.let { "${it.toInt()} ms" }
        )
        CardDivider()
        MetricRow(
            label = "Jitter",
            value = record.jitterMs?.let { String.format("%.1f ms", it) }
        )
        CardDivider()
        MetricRow(
            label = "Packet loss",
            value = record.lossPercent?.let { String.format("%.1f%%", it) }
        )
        CardDivider()
        MetricRow(
            label = "Temperature",
            value = pair(
                record.tempStartC?.let { String.format("%.1f°C", it) },
                record.tempEndC?.let { String.format("%.1f°C", it) }
            )
        )
        CardDivider()
        MetricRow(
            label = "Battery",
            value = pair(
                record.batteryStartPercent?.let { "$it%" },
                record.batteryEndPercent?.let { "$it%" }
            )
        )
        CardDivider()
        MetricRow(
            label = "Free memory",
            value = pair(
                record.freeMemoryStartBytes?.toGbString(),
                record.freeMemoryEndBytes?.toGbString()
            )
        )
        CardDivider()
        MetricRow(
            label = "Peak throttling",
            value = record.peakThermal?.label,
            accent = if (record.peakThermal != null &&
                record.peakThermal.ordinal >= 3
            ) {
                BoostColors.Warning
            } else {
                BoostColors.TextPrimary
            }
        )
        CardDivider()
        MetricRow(
            label = "FPS",
            value = null,
            hint = record.fpsNote
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Tally("Applied", record.appliedCount, BoostColors.Success)
            Tally("Limited", record.limitedCount, BoostColors.Warning)
            Tally("Unavailable", record.unavailableCount, BoostColors.Muted)
            Tally("Failed", record.failedCount, BoostColors.Danger)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "A small change between start and end is not proof of an improvement. " +
                "Conditions move on their own, so these are measurements, not a verdict.",
            style = MaterialTheme.typography.labelSmall,
            color = BoostColors.TextTertiary
        )
    }
}

@Composable
private fun Tally(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = BoostColors.TextTertiary
        )
    }
}

/** Renders a start/end pair, or null when neither end was measured. */
private fun pair(start: String?, end: String?): String? = when {
    start != null && end != null -> "$start → $end"
    start != null -> "$start → not measured"
    end != null -> "not measured → $end"
    else -> null
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMs))
