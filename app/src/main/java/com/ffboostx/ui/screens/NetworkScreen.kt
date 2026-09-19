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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ffboostx.core.DnsResult
import com.ffboostx.core.NetworkQuality
import com.ffboostx.ui.components.ActionButton
import com.ffboostx.ui.components.CardDivider
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.MetricRow
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.components.StatTile
import com.ffboostx.ui.components.ToggleRow
import com.ffboostx.ui.theme.BoostColors

@Composable
fun NetworkScreen(
    quality: NetworkQuality?,
    testing: Boolean,
    dnsResults: List<DnsResult>,
    dnsTesting: Boolean,
    transport: String,
    blockerActive: Boolean,
    blockerAvailable: Boolean,
    onRunTest: () -> Unit,
    onRunDnsTest: () -> Unit,
    onToggleBlocker: (Boolean) -> Unit,
    onOpenPrivateDns: () -> Unit,
    onOpenDataUsage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Network",
            style = MaterialTheme.typography.displaySmall,
            color = BoostColors.TextPrimary
        )
        Text(
            text = "Latency here is a TCP handshake round trip to a public resolver. An app " +
                "cannot open the raw socket that ICMP ping needs, and this is not your " +
                "in-match ping either - that depends on Free Fire's own servers.",
            style = MaterialTheme.typography.bodySmall,
            color = BoostColors.TextSecondary
        )

        SectionTitle("Measured quality", note = transport)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                caption = "Latency",
                value = quality?.avgMs?.let { "${it.toInt()}" } ?: "–",
                unit = "ms",
                accent = latencyAccent(quality?.avgMs),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                caption = "Jitter",
                value = quality?.jitterMs?.let { "${it.toInt()}" } ?: "–",
                unit = "ms",
                accent = if ((quality?.jitterMs ?: 0.0) > 25) {
                    BoostColors.Warning
                } else {
                    BoostColors.Ice
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                caption = "Packet loss",
                value = quality?.let { String.format("%.1f", it.lossPercent) } ?: "–",
                unit = "%",
                accent = if ((quality?.lossPercent ?: 0.0) > 1.0) {
                    BoostColors.Danger
                } else {
                    BoostColors.Success
                },
                modifier = Modifier.weight(1f)
            )
            StatTile(
                caption = "Stability",
                value = quality?.stabilityPercent?.toString() ?: "–",
                unit = "%",
                accent = BoostColors.Success,
                fraction = quality?.let { it.stabilityPercent / 100f },
                modifier = Modifier.weight(1f)
            )
        }

        GlassCard {
            MetricRow(
                label = "Verdict",
                value = quality?.rating,
                accent = BoostColors.Ember
            )
            CardDivider()
            MetricRow(
                label = "Best / worst probe",
                value = quality?.let { q ->
                    val min = q.minMs?.toInt()
                    val max = q.maxMs?.toInt()
                    if (min != null && max != null) "$min / $max ms" else null
                }
            )
            CardDivider()
            MetricRow(
                label = "Link estimate",
                value = quality?.downstreamKbps?.let { "${it / 1000} Mbps" },
                hint = "Android's own estimate for this network, not a speed test."
            )
            CardDivider()
            MetricRow(
                label = "Internet validated",
                value = quality?.let { if (it.validated) "Yes" else "No" }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Stability is 100 minus 2.5 points per millisecond of jitter and 4 " +
                    "points per percent of loss. Absolute latency is left out because nothing " +
                    "on the phone can change it.",
                style = MaterialTheme.typography.labelSmall,
                color = BoostColors.TextTertiary
            )
            Spacer(Modifier.height(12.dp))
            ActionButton(
                label = if (testing) "Measuring…" else "Run network test",
                onClick = onRunTest,
                enabled = !testing,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionTitle("DNS", note = "real queries")

        GlassCard {
            Text(
                text = "Each resolver is sent a genuine DNS query over UDP with a random label, " +
                    "so nothing can answer from cache and look artificially quick.",
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))

            if (dnsResults.isEmpty()) {
                Text(
                    text = "No measurement yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoostColors.TextTertiary
                )
            } else {
                val fastest = dnsResults.mapNotNull { it.latencyMs }.minOrNull()
                dnsResults.forEachIndexed { index, result ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = result.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BoostColors.TextPrimary
                            )
                            Text(
                                text = result.note ?: result.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = BoostColors.TextTertiary
                            )
                        }
                        Text(
                            text = result.latencyMs?.let { "${it.toInt()} ms" } ?: "no reply",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                result.latencyMs == null -> BoostColors.TextTertiary
                                result.latencyMs == fastest -> BoostColors.Success
                                else -> BoostColors.TextSecondary
                            }
                        )
                    }
                    if (index != dnsResults.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "A faster resolver shortens name lookups, which affects how quickly " +
                        "a connection starts. It does not change in-game ping once you are " +
                        "connected, so FF BoostX will not claim that it does.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BoostColors.TextTertiary
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    label = if (dnsTesting) "Testing…" else "Test DNS",
                    onClick = onRunDnsTest,
                    enabled = !dnsTesting,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = "Private DNS settings",
                    onClick = onOpenPrivateDns,
                    accent = BoostColors.Ice,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SectionTitle("Smart routing")

        GlassCard(accent = if (blockerActive) BoostColors.Success else null) {
            ToggleRow(
                title = "Block background apps from the network",
                description = "A local VPN routes every app except the game into a tunnel that " +
                    "goes nowhere, so their traffic is dropped on the device and the game keeps " +
                    "the connection. Nothing is forwarded, logged or sent anywhere.",
                checked = blockerActive,
                enabled = blockerAvailable,
                onCheckedChange = onToggleBlocker
            )
            CardDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This frees bandwidth on a busy link. It cannot lower your ping, and on " +
                    "an idle connection it will change nothing at all.",
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Multi-network routing is unavailable on this device. Android does not " +
                    "let an app split one game's packets across Wi-Fi and mobile at once, and a " +
                    "single connection cannot be bonded from the client side.",
                style = MaterialTheme.typography.labelSmall,
                color = BoostColors.Muted
            )
            Spacer(Modifier.height(12.dp))
            ActionButton(
                label = "Review which apps use data",
                onClick = onOpenDataUsage,
                accent = BoostColors.Ice,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun latencyAccent(avgMs: Double?) = when {
    avgMs == null -> BoostColors.Ice
    avgMs < 60 -> BoostColors.Success
    avgMs < 120 -> BoostColors.Warning
    else -> BoostColors.Danger
}
