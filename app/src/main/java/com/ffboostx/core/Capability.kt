package com.ffboostx.core

import android.app.GameManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * The five honesty buckets every feature in FF BoostX is sorted into.
 *
 * A feature is never described in any other terms. If it cannot be classified as
 * [AVAILABLE], the interface says which of the other four it is and why.
 */
enum class CapabilityClass(val label: String, val glyph: String) {
    /** Works right now on this device with no further input. */
    AVAILABLE("Available", "✓"),

    /** Real API, but whether it does anything depends on the device or OEM. */
    DEVICE_DEPENDENT("Device dependent", "⚠"),

    /** Real and supported, but the user has to grant something first. */
    PERMISSION("Permission required", "!"),

    /** Android forbids this for ordinary apps. No workaround exists. */
    RESTRICTED("Restricted by Android", "✕"),

    /** Would need root, a signed system app or ADB. Out of scope by design. */
    ROOT_ONLY("Not possible without root", "✕")
}

enum class CapabilityGroup(val title: String) {
    PERFORMANCE("Performance"),
    DISPLAY("Display and FPS"),
    MEMORY("Memory"),
    NETWORK("Network"),
    THERMAL("Thermal"),
    OVERLAY("Overlay"),
    SYSTEM("System settings")
}

data class Capability(
    val name: String,
    val group: CapabilityGroup,
    val classification: CapabilityClass,
    /** Plain-language explanation. Always says *why*, never just *what*. */
    val detail: String
)

/**
 * Builds the device capability list by actually probing the platform, not by
 * consulting a hard-coded table. Two different phones will produce two different
 * lists from the same build of the app.
 */
object CapabilityRegistry {

    fun build(context: Context, actions: SystemActions): List<Capability> {
        val app = context.applicationContext
        val sdk = Build.VERSION.SDK_INT
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val list = mutableListOf<Capability>()

        // ------------------------------------------------------- performance
        list += Capability(
            name = "Sustained performance mode",
            group = CapabilityGroup.PERFORMANCE,
            classification = if (power.isSustainedPerformanceModeSupported) {
                CapabilityClass.AVAILABLE
            } else {
                CapabilityClass.DEVICE_DEPENDENT
            },
            detail = if (power.isSustainedPerformanceModeSupported) {
                "This device supports a stable, thermally sustainable clock ceiling. " +
                    "FF BoostX can request it for its own window; the game must request it " +
                    "for itself."
            } else {
                "This device does not advertise sustained performance mode."
            }
        )

        list += Capability(
            name = "Performance hints (ADPF)",
            group = CapabilityGroup.PERFORMANCE,
            classification = when {
                sdk < Build.VERSION_CODES.S -> CapabilityClass.RESTRICTED
                PerfEngine.hintManagerAvailable(app) -> CapabilityClass.DEVICE_DEPENDENT
                else -> CapabilityClass.RESTRICTED
            },
            detail = if (sdk < Build.VERSION_CODES.S) {
                "Android's Dynamic Performance Framework arrived in Android 12. This device " +
                    "runs Android ${Build.VERSION.RELEASE}."
            } else {
                "The framework exists here, but a performance hint session only covers the " +
                    "threads of the app that creates it. FF BoostX cannot create one on Free " +
                    "Fire's behalf - the game itself has to."
            }
        )

        list += Capability(
            name = "Game Mode",
            group = CapabilityGroup.PERFORMANCE,
            classification = when {
                sdk < Build.VERSION_CODES.S -> CapabilityClass.RESTRICTED
                PerfEngine.gameManagerAvailable(app) -> CapabilityClass.DEVICE_DEPENDENT
                else -> CapabilityClass.RESTRICTED
            },
            detail = "Android's GameManager reports and sets the game mode for the calling app " +
                "only. Setting Free Fire's game mode needs the system Game Dashboard, which is " +
                "part of the OS, not something a third-party app can drive."
        )

        list += Capability(
            name = "CPU governor control",
            group = CapabilityGroup.PERFORMANCE,
            classification = CapabilityClass.ROOT_ONLY,
            detail = "Writing to /sys/devices/system/cpu needs root. FF BoostX does not read or " +
                "write those paths at all."
        )

        list += Capability(
            name = "GPU governor control",
            group = CapabilityGroup.PERFORMANCE,
            classification = CapabilityClass.ROOT_ONLY,
            detail = "GPU frequency and governor interfaces are kernel-owned and root-only."
        )

        list += Capability(
            name = "Kernel tweaks and DPI override",
            group = CapabilityGroup.PERFORMANCE,
            classification = CapabilityClass.ROOT_ONLY,
            detail = "Both need ADB or root. Android's own display size setting is the supported " +
                "route, and FF BoostX links to it."
        )

        // ----------------------------------------------------------- display
        val modes = actions.supportedRefreshRates()
        list += Capability(
            name = "Refresh rate detection",
            group = CapabilityGroup.DISPLAY,
            classification = CapabilityClass.AVAILABLE,
            detail = if (modes.size > 1) {
                "This panel reports ${modes.size} modes: " +
                    modes.joinToString(", ") { "${it.toInt()} Hz" } + "."
            } else {
                "This panel reports one mode: ${modes.firstOrNull()?.toInt() ?: 60} Hz."
            }
        )

        list += Capability(
            name = "Refresh rate request",
            group = CapabilityGroup.DISPLAY,
            classification = if (modes.size > 1) {
                CapabilityClass.AVAILABLE
            } else {
                CapabilityClass.DEVICE_DEPENDENT
            },
            detail = "An app may request a display mode for its own window. The request applies " +
                "to FF BoostX, not to Free Fire, which picks its own frame rate."
        )

        list += Capability(
            name = "Game FPS measurement",
            group = CapabilityGroup.DISPLAY,
            classification = CapabilityClass.RESTRICTED,
            detail = "Another app's frame rate is not readable. Frame timing comes from that " +
                "app's own Choreographer, and SurfaceFlinger statistics are not exposed to " +
                "third-party apps. FF BoostX shows FPS as N/A rather than guessing from the " +
                "refresh rate."
        )

        list += Capability(
            name = "Own render rate measurement",
            group = CapabilityGroup.DISPLAY,
            classification = CapabilityClass.AVAILABLE,
            detail = "FF BoostX can measure the frame pacing of its own window through " +
                "Choreographer. That is shown on the Performance screen and clearly labelled as " +
                "the app's rate, not the game's."
        )

        list += Capability(
            name = "Brightness and screen timeout",
            group = CapabilityGroup.SYSTEM,
            classification = if (actions.canWriteSystemSettings()) {
                CapabilityClass.AVAILABLE
            } else {
                CapabilityClass.PERMISSION
            },
            detail = "Uses the Modify system settings permission. Previous values are saved " +
                "before any change and restored when the session ends."
        )

        // ------------------------------------------------------------ memory
        list += Capability(
            name = "Memory statistics",
            group = CapabilityGroup.MEMORY,
            classification = CapabilityClass.AVAILABLE,
            detail = "Total, available, low-memory threshold and the system's own low-memory " +
                "flag come from ActivityManager and are accurate."
        )

        list += Capability(
            name = "Closing background apps",
            group = CapabilityGroup.MEMORY,
            classification = CapabilityClass.RESTRICTED,
            detail = "Since Android 8, killBackgroundProcesses only affects the caller's own " +
                "processes. Android controls background process management on this device. " +
                "FF BoostX does not attempt it and links to the system screen instead."
        )

        list += Capability(
            name = "Other apps' memory usage",
            group = CapabilityGroup.MEMORY,
            classification = CapabilityClass.RESTRICTED,
            detail = "getRunningAppProcesses returns only this app since Android 8, so a " +
                "per-app memory breakdown is not possible."
        )

        // ----------------------------------------------------------- network
        list += Capability(
            name = "Latency, jitter and loss",
            group = CapabilityGroup.NETWORK,
            classification = CapabilityClass.AVAILABLE,
            detail = "Measured with repeated TCP handshakes to a public resolver. ICMP ping " +
                "needs a raw socket, which apps cannot open, so the figure is handshake round " +
                "trip - close to ping but not identical, and labelled as such."
        )

        list += Capability(
            name = "DNS latency comparison",
            group = CapabilityGroup.NETWORK,
            classification = CapabilityClass.AVAILABLE,
            detail = "FF BoostX sends real DNS queries over UDP to each resolver and times the " +
                "replies. Switching the system resolver is done through Android's Private DNS " +
                "setting, which the app links to."
        )

        list += Capability(
            name = "Wi-Fi signal strength",
            group = CapabilityGroup.NETWORK,
            classification = CapabilityClass.RESTRICTED,
            detail = "Android ties Wi-Fi RSSI to location permission. FF BoostX does not ask for " +
                "location, so it reports transport type and the link bandwidth estimate instead."
        )

        list += Capability(
            name = "Background network blocking",
            group = CapabilityGroup.NETWORK,
            classification = CapabilityClass.PERMISSION,
            detail = "A local VpnService can stop every other app reaching the network while you " +
                "play, leaving the connection to the game. Nothing leaves the device and no " +
                "external server is involved."
        )

        list += Capability(
            name = "Multi-network routing",
            group = CapabilityGroup.NETWORK,
            classification = CapabilityClass.RESTRICTED,
            detail = "Splitting or merging one game's packets across Wi-Fi and mobile at once is " +
                "not something Android exposes, and a single TCP or UDP flow cannot be bonded " +
                "from the client side anyway. Multi-network routing is unavailable on this device."
        )

        list += Capability(
            name = "Reducing ping",
            group = CapabilityGroup.NETWORK,
            classification = CapabilityClass.RESTRICTED,
            detail = "Latency is set by your ISP, the route and the game server's location. No " +
                "app on the phone can shorten it. What FF BoostX can do is measure it and stop " +
                "other apps competing for the link."
        )

        // ----------------------------------------------------------- thermal
        list += Capability(
            name = "Thermal status",
            group = CapabilityGroup.THERMAL,
            classification = if (sdk >= Build.VERSION_CODES.Q) {
                CapabilityClass.AVAILABLE
            } else {
                CapabilityClass.RESTRICTED
            },
            detail = "PowerManager reports the system throttling level from Android 10 onward, " +
                "and FF BoostX subscribes to changes rather than polling."
        )

        list += Capability(
            name = "Thermal headroom",
            group = CapabilityGroup.THERMAL,
            classification = when {
                sdk < Build.VERSION_CODES.R -> CapabilityClass.RESTRICTED
                else -> CapabilityClass.DEVICE_DEPENDENT
            },
            detail = "getThermalHeadroom forecasts how close the device is to throttling. It " +
                "needs Android 11 and a device whose thermal HAL implements it; some return no " +
                "value at all."
        )

        list += Capability(
            name = "Battery temperature",
            group = CapabilityGroup.THERMAL,
            classification = CapabilityClass.AVAILABLE,
            detail = "Read from the battery service. It is the battery's temperature, which " +
                "trails the SoC, so it is labelled as battery temperature everywhere."
        )

        list += Capability(
            name = "Disabling thermal throttling",
            group = CapabilityGroup.THERMAL,
            classification = CapabilityClass.ROOT_ONLY,
            detail = "Throttling protects the hardware and is enforced below the Android " +
                "framework. FF BoostX will never claim to disable it - it warns and suggests " +
                "backing off instead."
        )

        // ----------------------------------------------------------- overlay
        list += Capability(
            name = "HUD and crosshair overlay",
            group = CapabilityGroup.OVERLAY,
            classification = if (actions.canDrawOverlays()) {
                CapabilityClass.AVAILABLE
            } else {
                CapabilityClass.PERMISSION
            },
            detail = "Uses the Display over other apps permission and a foreground service. " +
                "Some games restrict or prohibit overlays - check the game's rules before using " +
                "this feature."
        )

        list += Capability(
            name = "Game launch and exit detection",
            group = CapabilityGroup.OVERLAY,
            classification = if (actions.hasUsageAccess()) {
                CapabilityClass.AVAILABLE
            } else {
                CapabilityClass.PERMISSION
            },
            detail = "Usage access lets FF BoostX notice when the game closes so it can hide the " +
                "overlay and restore your settings. Without it you end the session manually. " +
                "No accessibility service is used."
        )

        // ------------------------------------------------------------ system
        list += Capability(
            name = "Gaming Focus (Do Not Disturb)",
            group = CapabilityGroup.SYSTEM,
            classification = if (actions.hasDndAccess()) {
                CapabilityClass.AVAILABLE
            } else {
                CapabilityClass.PERMISSION
            },
            detail = "Uses notification policy access. FF BoostX sets an interruption filter and " +
                "restores the previous one. It never rejects calls."
        )

        list += Capability(
            name = "Display size / DPI",
            group = CapabilityGroup.SYSTEM,
            classification = if (displaySizeSettingExists(app)) {
                CapabilityClass.AVAILABLE
            } else {
                CapabilityClass.RESTRICTED
            },
            detail = "Display scaling is controlled by Android. FF BoostX links to the system " +
                "display settings rather than overriding density."
        )

        list += Capability(
            name = "Touch sampling rate",
            group = CapabilityGroup.SYSTEM,
            classification = CapabilityClass.RESTRICTED,
            detail = "Touch hardware controls are managed by your device. No public API exposes " +
                "sampling rate or touch driver parameters; apps only receive the events."
        )

        return list
    }

    private fun displaySizeSettingExists(context: Context): Boolean = runCatching {
        context.packageManager.resolveActivity(
            android.content.Intent(Settings.ACTION_DISPLAY_SETTINGS),
            0
        ) != null
    }.getOrDefault(false)
}

/**
 * Probes for Android's performance frameworks. Everything here reports what is
 * present; nothing here claims to speed up another app.
 */
object PerfEngine {

    fun hintManagerAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            context.getSystemService(Context.PERFORMANCE_HINT_SERVICE) != null
        }.getOrDefault(false)
    }

    fun gameManagerAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            context.getSystemService(GameManager::class.java) != null
        }.getOrDefault(false)
    }

    fun sustainedPerformanceSupported(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isSustainedPerformanceModeSupported
    }.getOrDefault(false)

    /**
     * Thermal headroom, 0f..1f+, where 1f means throttling is imminent. Returns
     * null on devices whose thermal HAL does not implement the forecast.
     */
    fun thermalHeadroom(context: Context, forecastSeconds: Int = 10): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val value = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .getThermalHeadroom(forecastSeconds)
            if (value.isNaN() || value < 0f) null else value
        }.getOrNull()
    }
}
