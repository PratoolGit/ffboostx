package com.ffboostx.core

import android.app.ActivityManager
import android.content.Context

/**
 * Visual/refresh budget for the app itself. This governs how much work FF BoostX
 * is allowed to do on screen - it is not a claim about changing the device's
 * hardware performance.
 */
enum class PerformanceProfile(val label: String, val blurb: String) {
    LOW(
        label = "Low",
        blurb = "Minimal animation, static background, slow stat refresh. Best for 3 GB or low-RAM devices."
    ),
    BALANCED(
        label = "Balanced",
        blurb = "Normal animation and effects with a moderate stat refresh rate."
    ),
    HIGH(
        label = "High",
        blurb = "Full effects, animated backgrounds and the fastest stat refresh."
    );

    /** How often device statistics may be sampled, in milliseconds. */
    val statsIntervalMs: Long
        get() = when (this) {
            LOW -> 4_000L
            BALANCED -> 2_000L
            HIGH -> 1_000L
        }

    /** Whether continuously running (infinite) animations are permitted. */
    val allowsAmbientAnimation: Boolean
        get() = this != LOW

    /** Duration multiplier applied to one-shot transitions. */
    val motionScale: Float
        get() = when (this) {
            LOW -> 0.6f
            BALANCED -> 1f
            HIGH -> 1.15f
        }

    companion object {
        /**
         * Picks a profile from real device characteristics: the OS low-RAM flag,
         * total physical memory and the CPU core count.
         */
        fun detect(context: Context): PerformanceProfile {
            val am = context.applicationContext
                .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return BALANCED

            if (am.isLowRamDevice) return LOW

            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val totalGb = info.totalMem / 1_073_741_824.0
            val cores = Runtime.getRuntime().availableProcessors()

            return when {
                totalGb < 3.6 || cores <= 4 -> LOW
                totalGb < 6.5 -> BALANCED
                else -> HIGH
            }
        }
    }
}

/** User-facing override for how much motion the interface uses. */
enum class AnimationIntensity(val label: String) {
    OFF("Off"),
    SUBTLE("Subtle"),
    FULL("Full");
}

/**
 * The resolved motion budget for the current frame, combining the detected or
 * user-selected performance profile with the animation intensity preference.
 */
data class MotionBudget(
    val profile: PerformanceProfile,
    val intensity: AnimationIntensity
) {
    /** One-shot transitions (press feedback, screen changes, popup entry). */
    val transitionsEnabled: Boolean get() = intensity != AnimationIntensity.OFF

    /** Never-ending animations such as glow pulses and drifting backgrounds. */
    val ambientEnabled: Boolean
        get() = intensity == AnimationIntensity.FULL && profile.allowsAmbientAnimation

    fun durationMs(base: Int): Int = when (intensity) {
        AnimationIntensity.OFF -> 0
        AnimationIntensity.SUBTLE -> (base * 0.7f * profile.motionScale).toInt().coerceAtLeast(1)
        AnimationIntensity.FULL -> (base * profile.motionScale).toInt().coerceAtLeast(1)
    }
}
