package com.ffboostx.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackgroundStyle(val label: String, val blurb: String) {
    CYBER("Cyber", "Dark gradient with a faint perspective grid."),
    NEON("Neon", "Deep base with two soft violet and cyan glows."),
    DARK("Dark", "Flat near-black. The cheapest option to draw."),
    AURORA("Aurora", "Slow drifting colour wash. Uses motion when enabled."),
    MINIMAL("Minimal", "Plain charcoal with a single thin accent line.")
}

/** Statistics sampling rate, either derived from the profile or pinned by the user. */
enum class RefreshRate(val label: String, val intervalMs: Long?) {
    AUTO("Auto", null),
    FAST("1 second", 1_000L),
    NORMAL("2 seconds", 2_000L),
    SLOW("5 seconds", 5_000L),
    BATTERY_SAVER("10 seconds", 10_000L)
}

data class AppSettings(
    val autoStartGamingMode: Boolean = false,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.CYBER,
    val animationIntensity: AnimationIntensity = AnimationIntensity.FULL,
    /** null means "follow the automatically detected device tier". */
    val profileOverride: PerformanceProfile? = null,
    val refreshRate: RefreshRate = RefreshRate.AUTO,
    val lowRamMode: Boolean = false,
    val gamingModeOn: Boolean = false,
    // Which safe system changes the boost is allowed to apply.
    val boostAppliesDnd: Boolean = true,
    val boostAppliesTimeout: Boolean = true,
    val boostAppliesBrightness: Boolean = false,
    val selectedGamePackage: String? = null,
    // Saved originals so every applied change can be reverted.
    val savedScreenTimeoutMs: Int = -1,
    val savedDndFilter: Int = -1,
    val savedBrightness: Int = -1,
    val savedBrightnessMode: Int = -1
)

/**
 * Small SharedPreferences wrapper. Writes are applied asynchronously and the
 * in-memory [StateFlow] is the single source of truth for the UI, so no screen
 * ever touches disk while composing.
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("ffboostx.settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    private fun load(): AppSettings = AppSettings(
        autoStartGamingMode = prefs.getBoolean(KEY_AUTO_START, false),
        backgroundStyle = prefs.enum(KEY_BACKGROUND, BackgroundStyle.CYBER),
        animationIntensity = prefs.enum(KEY_ANIMATION, AnimationIntensity.FULL),
        profileOverride = prefs.getString(KEY_PROFILE, null)
            ?.let { name -> PerformanceProfile.entries.firstOrNull { it.name == name } },
        refreshRate = prefs.enum(KEY_REFRESH, RefreshRate.AUTO),
        lowRamMode = prefs.getBoolean(KEY_LOW_RAM, false),
        gamingModeOn = prefs.getBoolean(KEY_GAMING_MODE, false),
        boostAppliesDnd = prefs.getBoolean(KEY_APPLY_DND, true),
        boostAppliesTimeout = prefs.getBoolean(KEY_APPLY_TIMEOUT, true),
        boostAppliesBrightness = prefs.getBoolean(KEY_APPLY_BRIGHTNESS, false),
        selectedGamePackage = prefs.getString(KEY_GAME_PACKAGE, null),
        savedScreenTimeoutMs = prefs.getInt(KEY_SAVED_TIMEOUT, -1),
        savedDndFilter = prefs.getInt(KEY_SAVED_DND, -1),
        savedBrightness = prefs.getInt(KEY_SAVED_BRIGHTNESS, -1),
        savedBrightnessMode = prefs.getInt(KEY_SAVED_BRIGHTNESS_MODE, -1)
    )

    /** Mutates the in-memory state and persists the whole record. */
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        persist(next)
    }

    private fun persist(s: AppSettings) {
        prefs.edit().apply {
            putBoolean(KEY_AUTO_START, s.autoStartGamingMode)
            putString(KEY_BACKGROUND, s.backgroundStyle.name)
            putString(KEY_ANIMATION, s.animationIntensity.name)
            if (s.profileOverride == null) remove(KEY_PROFILE) else putString(KEY_PROFILE, s.profileOverride.name)
            putString(KEY_REFRESH, s.refreshRate.name)
            putBoolean(KEY_LOW_RAM, s.lowRamMode)
            putBoolean(KEY_GAMING_MODE, s.gamingModeOn)
            putBoolean(KEY_APPLY_DND, s.boostAppliesDnd)
            putBoolean(KEY_APPLY_TIMEOUT, s.boostAppliesTimeout)
            putBoolean(KEY_APPLY_BRIGHTNESS, s.boostAppliesBrightness)
            putString(KEY_GAME_PACKAGE, s.selectedGamePackage)
            putInt(KEY_SAVED_TIMEOUT, s.savedScreenTimeoutMs)
            putInt(KEY_SAVED_DND, s.savedDndFilter)
            putInt(KEY_SAVED_BRIGHTNESS, s.savedBrightness)
            putInt(KEY_SAVED_BRIGHTNESS_MODE, s.savedBrightnessMode)
        }.apply()
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, fallback: T): T {
        val name = getString(key, null) ?: return fallback
        return enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }

    companion object {
        private const val KEY_AUTO_START = "auto_start_gaming_mode"
        private const val KEY_BACKGROUND = "background_style"
        private const val KEY_ANIMATION = "animation_intensity"
        private const val KEY_PROFILE = "performance_profile"
        private const val KEY_REFRESH = "refresh_rate"
        private const val KEY_LOW_RAM = "low_ram_mode"
        private const val KEY_GAMING_MODE = "gaming_mode_on"
        private const val KEY_APPLY_DND = "boost_applies_dnd"
        private const val KEY_APPLY_TIMEOUT = "boost_applies_timeout"
        private const val KEY_APPLY_BRIGHTNESS = "boost_applies_brightness"
        private const val KEY_GAME_PACKAGE = "selected_game_package"
        private const val KEY_SAVED_TIMEOUT = "saved_screen_timeout"
        private const val KEY_SAVED_DND = "saved_dnd_filter"
        private const val KEY_SAVED_BRIGHTNESS = "saved_brightness"
        private const val KEY_SAVED_BRIGHTNESS_MODE = "saved_brightness_mode"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
