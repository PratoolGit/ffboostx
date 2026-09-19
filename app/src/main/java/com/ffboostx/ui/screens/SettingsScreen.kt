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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ffboostx.core.AnimationIntensity
import com.ffboostx.core.AppSettings
import com.ffboostx.core.BackgroundStyle
import com.ffboostx.core.PerformanceProfile
import com.ffboostx.core.RefreshRate
import com.ffboostx.ui.components.GlassCard
import com.ffboostx.ui.components.SectionTitle
import com.ffboostx.ui.components.SegmentedSelector
import com.ffboostx.ui.components.ToggleRow
import com.ffboostx.ui.theme.BoostColors

/** Sentinel used in the profile selector to mean "follow the detected tier". */
private const val AUTO_PROFILE = "Auto"

@Composable
fun SettingsScreen(
    settings: AppSettings,
    detectedProfile: PerformanceProfile,
    effectiveIntervalMs: Long,
    appVersion: String,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            color = BoostColors.TextPrimary
        )

        // ------------------------------------------------------------ General
        SectionTitle("General")

        GlassCard {
            ToggleRow(
                title = "Auto-start Gaming Mode",
                description = "Turn Gaming Mode on automatically when FF BoostX opens. " +
                    "It still only applies the settings selected below.",
                checked = settings.autoStartGamingMode,
                onCheckedChange = { value -> onUpdate { it.copy(autoStartGamingMode = value) } }
            )
        }

        GlassCard {
            SettingHeader(
                title = "Background",
                description = settings.backgroundStyle.blurb
            )
            SegmentedSelector(
                options = BackgroundStyle.entries,
                selected = settings.backgroundStyle,
                label = { it.label },
                onSelect = { value -> onUpdate { it.copy(backgroundStyle = value) } }
            )
        }

        GlassCard {
            SettingHeader(
                title = "Animation",
                description = when (settings.animationIntensity) {
                    AnimationIntensity.OFF ->
                        "All motion disabled. Screens change instantly and nothing animates in " +
                            "the background. The cheapest option."
                    AnimationIntensity.SUBTLE ->
                        "Short press and screen transitions only. No glow pulse, no drifting " +
                            "background."
                    AnimationIntensity.FULL ->
                        "Everything the current performance profile allows, including the boost " +
                            "button glow."
                }
            )
            SegmentedSelector(
                options = AnimationIntensity.entries,
                selected = settings.animationIntensity,
                label = { it.label },
                onSelect = { value -> onUpdate { it.copy(animationIntensity = value) } }
            )
        }

        GlassCard {
            SettingHeader(
                title = "Theme",
                description = "FF BoostX is a dark-only interface. A light theme would wash out " +
                    "the neon accents and is not offered."
            )
        }

        // ------------------------------------------------------------- Gaming
        SectionTitle("Gaming")

        GlassCard {
            ToggleRow(
                title = "Gaming Mode",
                description = "Master switch. Mirrors the toggle on the Gaming Mode screen.",
                checked = settings.gamingModeOn,
                onCheckedChange = { value -> onUpdate { it.copy(gamingModeOn = value) } }
            )
            Divider()
            ToggleRow(
                title = "Apply Do Not Disturb",
                description = "During a boost, silence calls and notifications. Requires " +
                    "notification policy access.",
                checked = settings.boostAppliesDnd,
                onCheckedChange = { value -> onUpdate { it.copy(boostAppliesDnd = value) } }
            )
            Divider()
            ToggleRow(
                title = "Extend screen timeout",
                description = "During a boost, raise the screen-off timeout to 10 minutes so " +
                    "the display does not sleep mid-match. Requires Modify system settings.",
                checked = settings.boostAppliesTimeout,
                onCheckedChange = { value -> onUpdate { it.copy(boostAppliesTimeout = value) } }
            )
            Divider()
            ToggleRow(
                title = "Fix brightness",
                description = "During a boost, disable auto-brightness and hold a steady level " +
                    "so the screen stops shifting. Off by default because it is noticeable.",
                checked = settings.boostAppliesBrightness,
                onCheckedChange = { value -> onUpdate { it.copy(boostAppliesBrightness = value) } }
            )
        }

        // -------------------------------------------------------- Performance
        SectionTitle("Performance", note = "detected: ${detectedProfile.label}")

        GlassCard {
            SettingHeader(
                title = "Performance profile",
                description = settings.profileOverride?.blurb
                    ?: "Auto. Matched to this device from its RAM, core count and the Android " +
                    "low-RAM flag, which gives ${detectedProfile.label}. " + detectedProfile.blurb
            )
            SegmentedSelector(
                options = listOf<String>(AUTO_PROFILE) + PerformanceProfile.entries.map { it.label },
                selected = settings.profileOverride?.label ?: AUTO_PROFILE,
                label = { it },
                onSelect = { label ->
                    val chosen = PerformanceProfile.entries.firstOrNull { it.label == label }
                    onUpdate { it.copy(profileOverride = chosen) }
                }
            )
        }

        GlassCard {
            SettingHeader(
                title = "Statistics refresh",
                description = "How often the dashboard re-reads memory, battery and thermal " +
                    "values. Slower means less wake-up work. Currently sampling every " +
                    "${effectiveIntervalMs / 1000} seconds."
            )
            SegmentedSelector(
                options = RefreshRate.entries,
                selected = settings.refreshRate,
                label = { if (it == RefreshRate.AUTO) "Auto" else "${it.intervalMs!! / 1000}s" },
                onSelect = { value -> onUpdate { it.copy(refreshRate = value) } }
            )
        }

        GlassCard {
            ToggleRow(
                title = "Low-RAM mode",
                description = "Forces the Low profile regardless of the detected tier: no " +
                    "ambient animation, static background, 4-second stat refresh.",
                checked = settings.lowRamMode,
                onCheckedChange = { value ->
                    onUpdate {
                        it.copy(
                            lowRamMode = value,
                            profileOverride = if (value) PerformanceProfile.LOW else it.profileOverride
                        )
                    }
                }
            )
        }

        // -------------------------------------------------------------- About
        SectionTitle("About")

        GlassCard {
            AboutRow("Version", appVersion)
            Divider()
            AboutRow("Developer", "FF BoostX project")
            Divider()
            AboutRow("Licences", "Apache 2.0 components", onClick = onOpenLicenses)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "FF BoostX does not modify Free Fire files or memory, intercept game " +
                    "traffic, automate aiming or interfere with anti-cheat. It only uses public " +
                    "Android APIs and the system settings you grant it.",
                style = MaterialTheme.typography.bodySmall,
                color = BoostColors.TextTertiary
            )
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SettingHeader(title: String, description: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = BoostColors.TextPrimary
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = BoostColors.TextSecondary
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Divider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BoostColors.LineSoft)
    )
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(vertical = 11.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = BoostColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) BoostColors.Ember else BoostColors.TextPrimary
        )
    }
}
