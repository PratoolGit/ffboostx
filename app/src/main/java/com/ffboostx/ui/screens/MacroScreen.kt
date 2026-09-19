package com.ffboostx.ui.screens

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ffboostx.core.Macro
import com.ffboostx.core.MacroStore
import com.ffboostx.overlay.MacroAccessibilityService
import com.ffboostx.ui.components.DialogButton
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.theme.BoostColors

@Composable
fun MacroScreen(
    context: Context,
    macros: List<Macro>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = isServiceEnabled(context)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Touch Macros", style = MaterialTheme.typography.displaySmall, color = BoostColors.TextPrimary)
        Text(
            "Record a tap, hold or swipe sequence, save it locally, then add its own replay button to the in-game floating panel.",
            style = MaterialTheme.typography.bodySmall, color = BoostColors.TextSecondary
        )

        GlassCard {
            Text("Accessibility macro engine", style = MaterialTheme.typography.titleMedium, color = BoostColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                if (enabled) "Enabled — the floating macro panel can appear over games."
                else "Enable FF BoostX in Android Accessibility settings before recording or replaying.",
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) BoostColors.Success else BoostColors.Warning
            )
            Spacer(Modifier.height(12.dp))
            DialogButton(
                label = if (enabled) "Open Accessibility Settings" else "Enable Macro Engine",
                filled = true,
                onClick = { MacroAccessibilityService.openAccessibilitySettings(context) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            DialogButton(
                label = "Refresh floating buttons",
                filled = false,
                onClick = {
                    context.sendBroadcast(android.content.Intent(MacroAccessibilityService.ACTION_REFRESH))
                    onRefresh()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionTitle("Saved recordings", note = "${macros.size}")
        if (macros.isEmpty()) {
            GlassCard {
                Text("No recordings yet.", color = BoostColors.TextSecondary)
                Spacer(Modifier.height(5.dp))
                Text("After enabling the service, use the floating ● RECORD button while your game is open.", style = MaterialTheme.typography.bodySmall, color = BoostColors.TextTertiary)
            }
        } else {
            macros.forEach { macro ->
                GlassCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(macro.name, style = MaterialTheme.typography.titleMedium, color = BoostColors.TextPrimary)
                            Text("${macro.actionCount} gesture(s) • ${macro.steps.firstOrNull()?.type ?: "empty"}", style = MaterialTheme.typography.bodySmall, color = BoostColors.TextSecondary)
                            Text(if (macro.overlayButton) "Replay button: ON" else "Replay button: OFF", style = MaterialTheme.typography.labelSmall, color = BoostColors.TextTertiary)
                        }
                        DialogButton(
                            label = if (macro.overlayButton) "Hide" else "Add button",
                            filled = macro.overlayButton,
                            onClick = {
                                MacroStore.get(context).setOverlayButton(macro.id, !macro.overlayButton)
                                context.sendBroadcast(android.content.Intent(MacroAccessibilityService.ACTION_REFRESH))
                                onRefresh()
                            }
                        )
                    }
                }
            }
        }

        GlassCard {
            Text("Important Android limitation", style = MaterialTheme.typography.titleMedium, color = BoostColors.TextPrimary)
            Spacer(Modifier.height(5.dp))
            Text(
                "This is an Accessibility-based automation feature, not a root or kernel touch hack. Android 14+ exposes the motion-event path needed for detailed recordings; OEM accessibility behavior can vary. Use macros only where the game/service rules permit automation.",
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
        }
    }
}

private fun isServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, MacroAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
