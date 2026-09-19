package com.ffboostx.core

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** How aggressively the user wants FF BoostX itself to behave when heat rises. */
enum class ThermalProfile(val label: String, val blurb: String) {
    COOL(
        "Cool",
        "Warn early and suggest backing off at the first sign of throttling. " +
            "FF BoostX also drops to its slowest refresh rate when the device warms."
    ),
    BALANCED(
        "Balanced",
        "Warn once Android reports moderate throttling. The default."
    ),
    PERFORMANCE(
        "Performance",
        "Only warn when throttling is severe. FF BoostX will not reduce its own " +
            "activity until then."
    );

    /** The level at which this profile starts warning. */
    val warnAt: ThermalLevel
        get() = when (this) {
            COOL -> ThermalLevel.LIGHT
            BALANCED -> ThermalLevel.MODERATE
            PERFORMANCE -> ThermalLevel.SEVERE
        }
}

data class ThermalAdvice(
    val severity: Severity,
    val headline: String,
    val suggestions: List<String>
) {
    enum class Severity { OK, WATCH, WARN, CRITICAL }
}

/**
 * Real thermal monitoring.
 *
 * This engine never claims to stop throttling - that is enforced below the
 * Android framework to protect the hardware. What it does is notice throttling
 * early, say so, and suggest the things that genuinely reduce heat.
 */
class ThermalEngine(context: Context) {

    private val appContext = context.applicationContext
    private val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    /**
     * Pushes a new level whenever the system's thermal status changes. This is a
     * listener, not a poll, so an idle device costs nothing. Falls back to a
     * single emission on Android 9 and below, where the API does not exist.
     */
    fun statusChanges(): Flow<ThermalLevel> = callbackFlow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            trySend(ThermalLevel.NONE)
            awaitClose { }
            return@callbackFlow
        }

        trySend(currentLevel())
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            trySend(mapStatus(status))
        }
        val registered = runCatching {
            power.addThermalStatusListener(listener)
            true
        }.getOrElse {
            Logx.w("Thermal listener unavailable", it)
            false
        }

        awaitClose {
            if (registered) {
                runCatching { power.removeThermalStatusListener(listener) }
            }
        }
    }

    fun currentLevel(): ThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalLevel.NONE
        return runCatching { mapStatus(power.currentThermalStatus) }
            .getOrDefault(ThermalLevel.NONE)
    }

    /** 0f..1f+ where 1f means throttling is imminent, or null if unsupported. */
    fun headroom(forecastSeconds: Int = 10): Float? =
        PerfEngine.thermalHeadroom(appContext, forecastSeconds)

    /**
     * Turns the raw level, battery temperature and profile into something the
     * user can act on. Every suggestion is something they can actually do; none
     * of them is a claim that the app will do it for them.
     */
    fun advise(
        level: ThermalLevel,
        batteryTempC: Float?,
        profile: ThermalProfile,
        charging: Boolean
    ): ThermalAdvice {
        val severity = when {
            level >= ThermalLevel.CRITICAL -> ThermalAdvice.Severity.CRITICAL
            level >= ThermalLevel.SEVERE -> ThermalAdvice.Severity.WARN
            level >= profile.warnAt -> ThermalAdvice.Severity.WATCH
            batteryTempC != null && batteryTempC >= 44f -> ThermalAdvice.Severity.WATCH
            else -> ThermalAdvice.Severity.OK
        }

        val suggestions = mutableListOf<String>()
        if (severity != ThermalAdvice.Severity.OK) {
            suggestions += "Lower screen brightness - the display is usually the largest " +
                "single heat source while gaming."
            suggestions += "Drop your FPS target one step. Fewer frames means less GPU work."
            if (charging) {
                suggestions += "Unplug the charger. Charging and gaming together is the " +
                    "fastest way to heat a phone."
            }
            suggestions += "Take the case off and keep the phone out of direct sunlight."
        }
        if (severity == ThermalAdvice.Severity.WARN ||
            severity == ThermalAdvice.Severity.CRITICAL
        ) {
            suggestions += "Android is already reducing performance to protect the hardware. " +
                "No app can override that."
        }
        if (severity == ThermalAdvice.Severity.CRITICAL) {
            suggestions += "Stop playing and let the device cool down before continuing."
        }

        val headline = when (severity) {
            ThermalAdvice.Severity.OK -> "Normal"
            ThermalAdvice.Severity.WATCH -> "Warm"
            ThermalAdvice.Severity.WARN -> "Hot - Android is throttling"
            ThermalAdvice.Severity.CRITICAL -> "Critical - stop and cool down"
        }

        return ThermalAdvice(severity, headline, suggestions)
    }

    private fun mapStatus(status: Int): ThermalLevel = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
        PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.SHUTDOWN
        else -> ThermalLevel.NONE
    }

    companion object {
        @Volatile
        private var instance: ThermalEngine? = null

        fun get(context: Context): ThermalEngine =
            instance ?: synchronized(this) {
                instance ?: ThermalEngine(context).also { instance = it }
            }
    }
}
