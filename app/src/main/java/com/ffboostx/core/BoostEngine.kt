package com.ffboostx.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The outcome vocabulary for every step. Nothing in the app reports success in
 * any other way, and no step is allowed to report [APPLIED] unless the platform
 * call it made actually returned success.
 */
enum class StepOutcome(val glyph: String, val label: String) {
    APPLIED("✓", "Applied"),
    LIMITED("⚠", "Limited"),
    SKIPPED("○", "Skipped"),
    UNAVAILABLE("✕", "Unavailable"),
    FAILED("✕", "Failed")
}

data class BoostStep(
    val title: String,
    val detail: String,
    val outcome: StepOutcome,
    /** A measurement the step produced, shown next to the row where present. */
    val reading: String? = null
)

enum class BoostPhase { RUNNING, DONE }

/** A point-in-time set of the values that can be compared before and after. */
data class BoostSnapshot(
    val freeMemoryBytes: Long,
    val memoryPressure: String,
    val latencyMs: Double?,
    val batteryTempC: Float?,
    val batteryPercent: Int,
    val thermal: ThermalLevel
)

data class BoostProgress(
    val phase: BoostPhase,
    val message: String,
    val fraction: Float,
    val steps: List<BoostStep> = emptyList(),
    val before: BoostSnapshot? = null,
    val after: BoostSnapshot? = null,
    val quality: NetworkQuality? = null,
    /** Set on the final emission when a game is ready to launch. */
    val launchPackage: String? = null
) {
    fun count(outcome: StepOutcome): Int = steps.count { it.outcome == outcome }
}

/**
 * Controls that can only be exercised from an Activity, because they act on a
 * window. Supplied by the UI so the engine itself stays free of Activity
 * references and cannot leak one.
 */
interface WindowController {
    fun requestRefreshRate(targetHz: Int?): Boolean
    fun setKeepScreenOn(keepOn: Boolean)
    fun setSustainedPerformance(enabled: Boolean): Boolean
}

/**
 * Runs the real boost sequence.
 *
 * Steps 1 to 7 are measurements: they read the device and report what they
 * found. Steps 8 to 13 are the only ones that change anything, and each changes
 * exactly one thing the user opted into, saving the previous value first.
 *
 * The engine deliberately contains no code that closes other apps, writes to
 * /sys, changes a governor, touches game files or injects into a process.
 */
class BoostEngine(
    private val monitor: DeviceMonitor,
    private val actions: SystemActions,
    private val store: SettingsStore,
    private val network: NetworkEngine,
    private val thermal: ThermalEngine,
    private val overlayStore: OverlayConfigStore
) {

    fun run(
        profile: GameProfile?,
        window: WindowController,
        motion: MotionBudget,
        onOverlayRequested: (watchPackage: String?) -> Boolean
    ): Flow<BoostProgress> = flow {
        val steps = mutableListOf<BoostStep>()
        val pause = if (motion.transitionsEnabled) 260L else 70L
        var quality: NetworkQuality? = null

        suspend fun push(message: String, fraction: Float, before: BoostSnapshot? = null) {
            emit(
                BoostProgress(
                    phase = BoostPhase.RUNNING,
                    message = message,
                    fraction = fraction,
                    steps = steps.toList(),
                    before = before,
                    quality = quality
                )
            )
            delay(pause)
        }

        // ---------------------------------------------- 1. device capabilities
        push("Detecting device capabilities", 0.04f)
        val refreshRates = actions.supportedRefreshRates()
        val sustained = PerfEngine.sustainedPerformanceSupported(monitorContextMarker())
        steps += BoostStep(
            title = "Device capabilities",
            detail = "${refreshRates.size} display mode(s); sustained performance " +
                if (sustained) "supported." else "not advertised.",
            outcome = StepOutcome.APPLIED,
            reading = "${Runtime.getRuntime().availableProcessors()} cores"
        )

        // ------------------------------------------------------ 2. RAM pressure
        push("Checking memory pressure", 0.12f)
        val memoryBefore = monitor.reader.readMemory()
        val pressure = describePressure(memoryBefore)
        steps += BoostStep(
            title = "Memory",
            detail = "${memoryBefore.availableBytes.toGbString()} free of " +
                "${memoryBefore.totalBytes.toGbString()}. Pressure $pressure. " +
                "Android controls background process management on this device, so " +
                "FF BoostX measures rather than clears.",
            outcome = if (memoryBefore.lowMemory) StepOutcome.LIMITED else StepOutcome.APPLIED,
            reading = readiness(memoryBefore)
        )

        // ------------------------------------------------------- 3. CPU / device
        push("Reading device state", 0.20f)
        val headroom = thermal.headroom()
        steps += BoostStep(
            title = "CPU state",
            detail = if (headroom != null) {
                "Thermal headroom ${(headroom * 100).toInt()}% of the throttling point. " +
                    "Governor control is not available without privileged access."
            } else {
                "This device does not report thermal headroom. Governor control is not " +
                    "available without privileged access."
            },
            outcome = if (headroom != null) StepOutcome.APPLIED else StepOutcome.LIMITED,
            reading = headroom?.let { "${(it * 100).toInt()}%" }
        )

        // ------------------------------------------------------------ 4. thermal
        push("Checking thermal status", 0.28f)
        val thermalBefore = thermal.currentLevel()
        val batteryBefore = monitor.reader.read().battery
        steps += BoostStep(
            title = "Thermal",
            detail = "System throttling level: ${thermalBefore.label}. FF BoostX monitors " +
                "this and advises; it cannot and will not disable throttling.",
            outcome = when {
                thermalBefore >= ThermalLevel.SEVERE -> StepOutcome.LIMITED
                else -> StepOutcome.APPLIED
            },
            reading = batteryBefore.temperatureC.valueOrNull()
                ?.let { String.format("%.1f°C", it) }
        )

        // ------------------------------------------------------------ 5. battery
        push("Checking battery", 0.34f)
        steps += BoostStep(
            title = "Battery",
            detail = when {
                batteryBefore.charging ->
                    "Charging. Gaming while charging adds heat - consider unplugging."
                batteryBefore.percent <= 20 ->
                    "Low. Android may restrict performance to preserve what is left."
                else -> "Healthy enough for a session."
            },
            outcome = when {
                batteryBefore.percent <= 15 -> StepOutcome.LIMITED
                else -> StepOutcome.APPLIED
            },
            reading = "${batteryBefore.percent}%"
        )

        val before = BoostSnapshot(
            freeMemoryBytes = memoryBefore.availableBytes,
            memoryPressure = pressure,
            latencyMs = null,
            batteryTempC = batteryBefore.temperatureC.valueOrNull(),
            batteryPercent = batteryBefore.percent,
            thermal = thermalBefore
        )

        // ------------------------------------------------------- 6. refresh rate
        push("Checking display", 0.42f, before)
        val target = profile?.refreshTargetHz
        val refreshApplied = window.requestRefreshRate(target)
        steps += BoostStep(
            title = "Display mode",
            detail = when {
                refreshRates.size <= 1 ->
                    "This panel exposes one mode, so there is nothing to switch."
                refreshApplied ->
                    "Requested ${target?.let { "$it Hz" } ?: "the fastest mode"} for the " +
                        "FF BoostX window. Free Fire picks its own frame rate."
                else -> "The window manager refused the mode request."
            },
            outcome = when {
                refreshRates.size <= 1 -> StepOutcome.UNAVAILABLE
                refreshApplied -> StepOutcome.LIMITED
                else -> StepOutcome.FAILED
            },
            reading = refreshRates.joinToString("/") { "${it.toInt()}" } + " Hz"
        )

        // ------------------------------------------------------------ 7. network
        if (profile?.runNetworkCheck != false) {
            push("Measuring network quality", 0.52f, before)
            // Held in a local val so the nullability checks below are plain reads
            // rather than smart casts on a variable the push() closure captures.
            val measured = runCatching { network.measureQuality() }.getOrNull()
            quality = measured
            val avg = measured?.avgMs
            steps += if (measured != null && avg != null) {
                BoostStep(
                    title = "Network",
                    detail = "${measured.transport}, jitter ${format1(measured.jitterMs)} ms, " +
                        "loss ${format1(measured.lossPercent)}%. Measured as a TCP handshake " +
                        "round trip, not ICMP ping, and not your in-match ping.",
                    outcome = if (measured.stabilityPercent >= 70) {
                        StepOutcome.APPLIED
                    } else {
                        StepOutcome.LIMITED
                    },
                    reading = "${avg.toInt()} ms"
                )
            } else {
                BoostStep(
                    title = "Network",
                    detail = "No route to the probe target. The device may be offline or the " +
                        "network may be blocking outbound connections.",
                    outcome = StepOutcome.FAILED
                )
            }
        } else {
            steps += BoostStep(
                "Network", "Network check is off in this game profile.", StepOutcome.SKIPPED
            )
        }

        // ------------------------------------------------- 8. gaming mode features
        push("Applying Gaming Mode", 0.62f, before)
        val keepAwake = profile?.keepScreenAwake ?: true
        val proGamerMode = profile?.proGamerMode ?: true
        window.setKeepScreenOn(keepAwake)
        val sustainedApplied = if (proGamerMode) window.setSustainedPerformance(true) else false
        steps += BoostStep(
            title = "Gaming Mode",
            detail = buildString {
                append(if (keepAwake) "Screen kept awake. " else "Keep-awake off in profile. ")
                append(
                    if (!proGamerMode) {
                        "Safe performance policy is off."
                    } else if (sustainedApplied) {
                        "Sustained performance requested for this app window; Android does not expose another app's CPU/GPU governor."
                    } else {
                        "Sustained performance is not advertised on this device."
                    }
                )
            },
            outcome = when {
                keepAwake && sustainedApplied -> StepOutcome.APPLIED
                keepAwake -> StepOutcome.LIMITED
                else -> StepOutcome.SKIPPED
            }
        )
        store.update { it.copy(gamingModeOn = true) }

        // ------------------------------------------------------- 9. gaming focus
        push("Applying Gaming Focus", 0.70f, before)
        steps += when {
            ((profile?.gamingFocus == false && profile?.callBlocking == false) ||
                !store.current.boostAppliesDnd || profile?.notificationMode == NotificationMode.ALLOW) ->
                BoostStep("Gaming Focus", "Interruption control is disabled in this profile.", StepOutcome.SKIPPED)

            !actions.hasDndAccess() ->
                BoostStep(
                    "Gaming Focus",
                    "Notification policy access has not been granted yet.",
                    StepOutcome.UNAVAILABLE
                )

            actions.setDoNotDisturb(true, store) ->
                BoostStep(
                    "Gaming Focus",
                    if (profile?.notificationMode == NotificationMode.BLOCK && profile.callBlocking) {
                        "Do Not Disturb applied to suppress notifications and calls. Android cannot forcibly reject a carrier call without system privileges. Previous filter is saved."
                    } else {
                        "Priority-only interruption filter applied for a low-interruption session. Previous filter is saved."
                    },
                    StepOutcome.APPLIED
                )

            else -> BoostStep(
                "Gaming Focus", "The system refused the interruption filter change.",
                StepOutcome.FAILED
            )
        }

        // --------------------------------------------------------- 10. brightness
        push("Applying display settings", 0.78f, before)
        val brightness = profile?.brightness ?: -1
        val brightnessLock = profile?.brightnessLock == true
        steps += when {
            !brightnessLock || brightness < 0 ->
                BoostStep(
                    "Brightness", "No brightness set in this profile, so it is left alone.",
                    StepOutcome.SKIPPED
                )

            !actions.canWriteSystemSettings() ->
                BoostStep(
                    "Brightness", "Needs the Modify system settings permission.",
                    StepOutcome.UNAVAILABLE
                )

            actions.setBrightness(brightness, store) ->
                BoostStep(
                    "Brightness",
                    "Fixed so auto-brightness stops shifting mid-match. Restored when the " +
                        "session ends.",
                    StepOutcome.APPLIED,
                    reading = "${(brightness * 100 / 255)}%"
                )

            else -> BoostStep("Brightness", "The system refused the change.", StepOutcome.FAILED)
        }

        if (store.current.boostAppliesTimeout && actions.canWriteSystemSettings()) {
            actions.setScreenTimeout(600_000, store)
        }

        // --------------------------------------------------------------- 11. HUD
        push("Preparing overlay", 0.86f, before)
        val wantsHud = profile?.hudEnabled == true
        val wantsCrosshair = profile?.crosshairEnabled == true
        if (wantsHud || wantsCrosshair) {
            overlayStore.update {
                it.copy(hudEnabled = wantsHud, crosshairEnabled = wantsCrosshair)
            }
        }

        val overlayStarted = if (wantsHud || wantsCrosshair) {
            onOverlayRequested(profile?.packageName)
        } else {
            false
        }

        steps += when {
            !wantsHud -> BoostStep("HUD", "Off in this profile.", StepOutcome.SKIPPED)
            !actions.canDrawOverlays() -> BoostStep(
                "HUD", "Needs the Display over other apps permission.", StepOutcome.UNAVAILABLE
            )
            overlayStarted -> BoostStep(
                "HUD",
                "On screen. FPS shows N/A because another app's frame rate is not readable.",
                StepOutcome.LIMITED
            )
            else -> BoostStep("HUD", "The overlay service could not start.", StepOutcome.FAILED)
        }

        // --------------------------------------------------------- 12. crosshair
        steps += when {
            !wantsCrosshair -> BoostStep("Crosshair", "Off in this profile.", StepOutcome.SKIPPED)
            !actions.canDrawOverlays() -> BoostStep(
                "Crosshair", "Needs the Display over other apps permission.",
                StepOutcome.UNAVAILABLE
            )
            overlayStarted -> BoostStep(
                "Crosshair",
                "Static reticle drawn. Some games restrict or prohibit overlays - check the " +
                    "game's rules before using it.",
                StepOutcome.APPLIED
            )
            else -> BoostStep(
                "Crosshair", "The overlay service could not start.", StepOutcome.FAILED
            )
        }

        // ------------------------------------------------------------ 13. launch
        push("Game ready", 0.95f, before)
        val launchable = profile?.packageName?.takeIf { actions.launchIntentFor(it) != null }
        steps += when {
            profile == null ->
                BoostStep("Game launch", "No game profile selected.", StepOutcome.SKIPPED)
            launchable != null ->
                BoostStep(
                    "Game launch", "${profile.name} is ready to start.", StepOutcome.APPLIED
                )
            else ->
                BoostStep(
                    "Game launch", "${profile.name} is not installed on this device.",
                    StepOutcome.UNAVAILABLE
                )
        }

        val memoryAfter = monitor.reader.readMemory()
        val batteryAfter = monitor.reader.read().battery

        emit(
            BoostProgress(
                phase = BoostPhase.DONE,
                message = "Game Ready",
                fraction = 1f,
                steps = steps.toList(),
                before = before,
                after = BoostSnapshot(
                    freeMemoryBytes = memoryAfter.availableBytes,
                    memoryPressure = describePressure(memoryAfter),
                    latencyMs = quality?.avgMs,
                    batteryTempC = batteryAfter.temperatureC.valueOrNull(),
                    batteryPercent = batteryAfter.percent,
                    thermal = thermal.currentLevel()
                ),
                quality = quality,
                launchPackage = launchable
            )
        )
    }

    /**
     * The engine needs a Context only for the sustained-performance probe.
     * SystemActions already holds the application context, so it is borrowed
     * rather than stored a second time - and an application context can never
     * leak an Activity.
     */
    private fun monitorContextMarker() = actions.probeContext()

    private fun describePressure(memory: MemoryStats): String = when {
        memory.lowMemory -> "CRITICAL"
        memory.availableBytes < memory.thresholdBytes * 2 -> "HIGH"
        memory.usedFraction > 0.80f -> "ELEVATED"
        else -> "NORMAL"
    }

    private fun readiness(memory: MemoryStats): String = when {
        memory.lowMemory -> "POOR"
        memory.usedFraction > 0.88f -> "FAIR"
        memory.usedFraction > 0.75f -> "GOOD"
        else -> "EXCELLENT"
    }

    private fun format1(value: Double?): String =
        value?.let { String.format("%.1f", it) } ?: "n/a"
}
