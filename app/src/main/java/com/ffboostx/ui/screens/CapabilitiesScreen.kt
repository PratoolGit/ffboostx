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
import androidx.compose.foundation.shape.CircleShape
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
import com.ffboostx.core.Capability
import com.ffboostx.core.CapabilityClass
import com.ffboostx.core.CapabilityGroup
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.theme.BoostColors

@Composable
fun CapabilitiesScreen(
    capabilities: List<Capability>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Device capabilities",
            style = MaterialTheme.typography.displaySmall,
            color = BoostColors.TextPrimary
        )
        Text(
            text = "Built by probing this device, not from a fixed list. Two phones running " +
                "the same build of FF BoostX will show different results here.",
            style = MaterialTheme.typography.bodySmall,
            color = BoostColors.TextSecondary
        )

        Tally(capabilities)

        CapabilityGroup.entries.forEach { group ->
            val items = capabilities.filter { it.group == group }
            if (items.isEmpty()) return@forEach
            SectionTitle(group.title, note = "${items.size}")
            GlassCard(contentPadding = 14.dp) {
                items.forEachIndexed { index, capability ->
                    CapabilityRow(capability)
                    if (index != items.lastIndex) Spacer(Modifier.height(12.dp))
                }
            }
        }

        Text(
            text = "FF BoostX never requests root, ADB, Magisk, Shizuku or system-signature " +
                "permissions, and never runs shell commands. Anything marked as needing " +
                "privileged access stays unimplemented rather than faked.",
            style = MaterialTheme.typography.bodySmall,
            color = BoostColors.TextTertiary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun Tally(capabilities: List<Capability>) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                CapabilityClass.AVAILABLE,
                CapabilityClass.DEVICE_DEPENDENT,
                CapabilityClass.PERMISSION,
                CapabilityClass.RESTRICTED,
                CapabilityClass.ROOT_ONLY
            ).forEach { cls ->
                val count = capabilities.count { it.classification == cls }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$count",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = colorFor(cls)
                    )
                    Text(
                        text = cls.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = BoostColors.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(capability: Capability) {
    val color = colorFor(capability.classification)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .border(BorderStroke(1.dp, color.copy(alpha = 0.4f)), CircleShape)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = capability.classification.glyph,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.padding(horizontal = 5.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = capability.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = BoostColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = capability.classification.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = capability.detail,
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
        }
    }
}

private fun colorFor(cls: CapabilityClass): Color = when (cls) {
    CapabilityClass.AVAILABLE -> BoostColors.Success
    CapabilityClass.DEVICE_DEPENDENT -> BoostColors.Warning
    CapabilityClass.PERMISSION -> BoostColors.Ice
    CapabilityClass.RESTRICTED -> BoostColors.Muted
    CapabilityClass.ROOT_ONLY -> BoostColors.Danger
}
