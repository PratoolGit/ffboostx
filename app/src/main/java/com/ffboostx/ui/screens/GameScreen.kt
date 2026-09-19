package com.ffboostx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffboostx.core.GameApp
import com.ffboostx.core.GameProfile
import com.ffboostx.core.NotificationMode
import com.ffboostx.core.ThermalProfile
import com.ffboostx.ui.components.ActionButton
import com.ffboostx.ui.components.CardDivider
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.LabeledSlider
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.components.SegmentedSelector
import com.ffboostx.ui.components.ToggleRow
import com.ffboostx.ui.theme.BoostColors

@Composable
fun GameScreen(
    profiles: List<GameProfile>,
    activeProfile: GameProfile?,
    installedGames: List<GameApp>,
    supportedRefreshRates: List<Float>,
    canWriteSettings: Boolean,
    onSelectProfile: (GameProfile) -> Unit,
    onSaveProfile: (GameProfile) -> Unit,
    onResetProfile: (GameProfile) -> Unit,
    onDeleteProfile: (GameProfile) -> Unit,
    onBoostAndLaunch: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Game",
            style = MaterialTheme.typography.displaySmall,
            color = BoostColors.TextPrimary
        )

        if (installedGames.isEmpty()) {
            GlassCard {
                Text(
                    text = "No supported game installed",
                    style = MaterialTheme.typography.titleMedium,
                    color = BoostColors.TextPrimary
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "FF BoostX looks for Free Fire and Free Fire MAX, declared in the " +
                        "manifest's queries block rather than by requesting access to your whole " +
                        "app list. Neither is installed here, so there is nothing to launch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoostColors.TextSecondary
                )
            }
            return@Column
        }

        SectionTitle("Profiles", note = "${profiles.size} saved")

        GlassCard {
            profiles.forEach { profile ->
                val selected = profile.id == activeProfile?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                BoostColors.Ember.copy(alpha = 0.10f)
                            } else {
                                BoostColors.Void.copy(alpha = 0.4f)
                            }
                        )
                        .clickable { onSelectProfile(profile) }
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selected) "●" else "○",
                        color = if (selected) BoostColors.Ember else BoostColors.Muted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.padding(horizontal = 5.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BoostColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = profile.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = BoostColors.TextTertiary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        val profile = activeProfile ?: return@Column

        SectionTitle("${profile.name} settings")

        GlassCard(accent = BoostColors.Ember) {
            ToggleRow(
                title = "Pro Gamer Mode",
                description = "Enables the safe performance policy: fastest supported display mode, sustained-performance hint when the device exposes it, and low-overhead monitoring. Android does not permit this app to overclock the game's CPU/GPU without privileged access.",
                checked = profile.proGamerMode,
                onCheckedChange = { onSaveProfile(profile.copy(proGamerMode = it)) }
            )
            CardDivider()
            ToggleRow(
                title = "Gaming Focus",
                description = "Set a priority-only interruption filter during the session and " +
                    "restore your previous one afterwards.",
                checked = profile.gamingFocus,
                onCheckedChange = { onSaveProfile(profile.copy(gamingFocus = it)) }
            )
            CardDivider()
            ToggleRow(
                title = "Keep screen awake",
                description = "Holds the screen on through the app window flag, which needs no " +
                    "permission and no wake lock.",
                checked = profile.keepScreenAwake,
                onCheckedChange = { onSaveProfile(profile.copy(keepScreenAwake = it)) }
            )
            CardDivider()
            ToggleRow(
                title = "Show HUD",
                description = "Live values drawn over the game. FPS reads N/A.",
                checked = profile.hudEnabled,
                onCheckedChange = { onSaveProfile(profile.copy(hudEnabled = it)) }
            )
            CardDivider()
            ToggleRow(
                title = "Show crosshair",
                description = "Static reticle. Check the game's rules on overlays first.",
                checked = profile.crosshairEnabled,
                onCheckedChange = { onSaveProfile(profile.copy(crosshairEnabled = it)) }
            )
            CardDivider()
            ToggleRow(
                title = "Block background apps from the network",
                description = "Local VPN that drops other apps' traffic. Nothing is forwarded " +
                    "or logged.",
                checked = profile.blockBackgroundNetwork,
                onCheckedChange = { onSaveProfile(profile.copy(blockBackgroundNetwork = it)) }
            )
            CardDivider()
            ToggleRow(
                title = "Run a network test during boost",
                description = "Adds about two seconds to the sequence and gives the session " +
                    "report something to compare against.",
                checked = profile.runNetworkCheck,
                onCheckedChange = { onSaveProfile(profile.copy(runNetworkCheck = it)) }
            )
        }

        SectionTitle("Interruption control")
        GlassCard {
            Text("Notification behavior", style = MaterialTheme.typography.titleMedium, color = BoostColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            SegmentedSelector(
                options = NotificationMode.entries,
                selected = profile.notificationMode,
                label = { it.label },
                onSelect = { onSaveProfile(profile.copy(notificationMode = it)) }
            )
            Spacer(Modifier.height(8.dp))
            Text("Block mode uses Android Do Not Disturb. Bullet mode is a saved preference for a low-interruption notification style; Android does not provide a public API for drawing a custom bullet ticker over another app.", style = MaterialTheme.typography.labelSmall, color = BoostColors.TextTertiary)
            CardDivider()
            ToggleRow(
                title = "Silence incoming calls",
                description = "Uses the same DND policy during a session. It silences calls; it cannot forcibly reject a carrier call without system privileges.",
                checked = profile.callBlocking,
                onCheckedChange = { onSaveProfile(profile.copy(callBlocking = it)) }
            )
            CardDivider()
            ToggleRow(
                title = "Mistouch protection",
                description = "Stores the preference for a gesture-safe game session. Android does not expose a public API for disabling another app's system-edge gestures.",
                checked = profile.mistouchProtection,
                onCheckedChange = { onSaveProfile(profile.copy(mistouchProtection = it)) }
            )
        }

        SectionTitle("Touch & controls")
        GlassCard {
            Text("Touch sensitivity calibration", style = MaterialTheme.typography.titleMedium, color = BoostColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Saved per game so you can keep a consistent calibration target. Android does not expose a public API to change the touch controller's sampling rate or raw sensitivity for another app.", style = MaterialTheme.typography.bodySmall, color = BoostColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "Calibration",
                value = profile.touchSensitivity.toFloat(),
                valueText = "${profile.touchSensitivity}%",
                range = 0f..100f,
                onValueChange = { onSaveProfile(profile.copy(touchSensitivity = it.toInt())) }
            )
            CardDivider()
            Text("Control layout", style = MaterialTheme.typography.titleMedium, color = BoostColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            SegmentedSelector(
                options = listOf("Default", "Claw", "Compact", "Custom"),
                selected = profile.controlLayout,
                label = { it },
                onSelect = { onSaveProfile(profile.copy(controlLayout = it)) }
            )
        }

        SectionTitle("Automation")
        GlassCard {
            Text("Macro profile", style = MaterialTheme.typography.titleMedium, color = BoostColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Select a saved macro profile for this game. Use the Macros tab to record touch/swipe sequences and decide which recordings get their own floating replay button.", style = MaterialTheme.typography.bodySmall, color = BoostColors.TextSecondary)
            Spacer(Modifier.height(10.dp))
            SegmentedSelector(
                options = listOf("None", "Quick actions", "Custom"),
                selected = profile.macroProfile,
                label = { it },
                onSelect = { onSaveProfile(profile.copy(macroProfile = it)) }
            )
        }

        GlassCard {
            Text(
                text = "Brightness",
                style = MaterialTheme.typography.titleMedium,
                color = BoostColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (canWriteSettings) {
                    "Set to auto to leave your brightness alone. Any fixed value is restored " +
                        "when the session ends."
                } else {
                    "Needs the Modify system settings permission before it can be applied."
                },
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "Level",
                value = if (profile.brightness < 0) 0f else profile.brightness.toFloat(),
                valueText = if (profile.brightness < 0) {
                    "Auto"
                } else {
                    "${profile.brightness * 100 / 255}%"
                },
                range = 0f..255f,
                onValueChange = { value ->
                    val level = if (value < 12f) -1 else value.toInt()
                    onSaveProfile(profile.copy(brightness = level))
                }
            )
            if (!canWriteSettings) {
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    label = "Grant permission",
                    onClick = onRequestWriteSettings,
                    accent = BoostColors.Warning,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        GlassCard {
            ToggleRow(
                title = "Brightness lock",
                description = "Locks the selected brightness and disables automatic dimming during the session. The previous brightness mode is restored afterwards.",
                checked = profile.brightnessLock,
                onCheckedChange = { onSaveProfile(profile.copy(brightnessLock = it)) }
            )
        }

        GlassCard {
            Text(
                text = "FPS target",
                style = MaterialTheme.typography.titleMedium,
                color = BoostColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = profile.fpsNote,
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            // Only the rates this panel actually advertises are offered.
            val options = listOf("Auto") + supportedRefreshRates.map { "${it.toInt()}" }
            SegmentedSelector(
                options = options,
                selected = profile.refreshTargetHz?.toString() ?: "Auto",
                label = { if (it == "Auto") "Auto" else "$it Hz" },
                onSelect = { choice ->
                    onSaveProfile(
                        profile.copy(
                            refreshTargetHz = choice.toIntOrNull(),
                            fpsTargetHz = choice.toIntOrNull()
                        )
                    )
                }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Targets above are the modes this display reports. A rate the panel does " +
                    "not support is never offered.",
                style = MaterialTheme.typography.labelSmall,
                color = BoostColors.TextTertiary
            )
        }

        GlassCard {
            Text(
                text = "Thermal profile",
                style = MaterialTheme.typography.titleMedium,
                color = BoostColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = profile.thermalProfile.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            SegmentedSelector(
                options = ThermalProfile.entries,
                selected = profile.thermalProfile,
                label = { it.label },
                onSelect = { onSaveProfile(profile.copy(thermalProfile = it)) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                label = "Reset",
                onClick = { onResetProfile(profile) },
                accent = BoostColors.Ice,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = "Delete",
                onClick = { onDeleteProfile(profile) },
                accent = BoostColors.Danger,
                modifier = Modifier.weight(1f)
            )
        }

        ActionButton(
            label = "Boost and launch ${profile.name}",
            onClick = onBoostAndLaunch,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Launching runs the boost sequence first, applies only what you enabled " +
                "above, then starts the game with a normal launch intent. No accessibility " +
                "service is used to watch for it.",
            style = MaterialTheme.typography.labelSmall,
            color = BoostColors.TextTertiary
        )
    }
}
