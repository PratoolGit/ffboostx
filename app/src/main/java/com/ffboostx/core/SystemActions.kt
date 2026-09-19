package com.ffboostx.core

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Window
import android.view.WindowManager.LayoutParams
import android.os.Process

/**
 * How well the current device supports a given feature. The UI renders each of
 * these states differently so nothing is ever presented as working when it is not.
 */
enum class CapabilityState(val label: String) {
    /** Supported and currently switched on. */
    ACTIVE("Active"),

    /** Supported, permission granted, currently off. */
    AVAILABLE("Available"),

    /** Supported but a special Android permission has not been granted yet. */
    PERMISSION_REQUIRED("Permission required"),

    /** The Android version, OEM or hardware does not offer this at all. */
    UNSUPPORTED("Unsupported")
}

data class GameApp(val packageName: String, val label: String)

/**
 * Wraps the system settings FF BoostX is allowed to touch.
 *
 * Every mutating call records the previous value first so [revertAll] can put the
 * device back exactly as it was. Nothing here needs root, and nothing pretends to
 * modify another app's process, memory or network traffic.
 */
class SystemActions(context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // ---------------------------------------------------------------- permissions

    fun canWriteSystemSettings(): Boolean = Settings.System.canWrite(appContext)

    fun hasDndAccess(): Boolean = runCatching {
        notificationManager.isNotificationPolicyAccessGranted
    }.getOrDefault(false)

    fun writeSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun dndAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---------------------------------------------------------------- capabilities

    fun dndState(): CapabilityState = when {
        !hasDndAccess() -> CapabilityState.PERMISSION_REQUIRED
        runCatching { notificationManager.currentInterruptionFilter }
            .getOrDefault(NotificationManager.INTERRUPTION_FILTER_ALL) !=
            NotificationManager.INTERRUPTION_FILTER_ALL -> CapabilityState.ACTIVE
        else -> CapabilityState.AVAILABLE
    }

    fun writeSettingsState(): CapabilityState =
        if (canWriteSystemSettings()) CapabilityState.AVAILABLE else CapabilityState.PERMISSION_REQUIRED

    /**
     * High refresh rate can only be requested for this app's own window. A game
     * always controls its own frame rate, so this is presented as a display check
     * rather than a game optimisation.
     */
    fun refreshRateState(): CapabilityState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return CapabilityState.UNSUPPORTED
        val display = (appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return CapabilityState.UNSUPPORTED
        val modes = display.supportedModes ?: return CapabilityState.UNSUPPORTED
        return if (modes.size > 1) CapabilityState.AVAILABLE else CapabilityState.UNSUPPORTED
    }

    // ---------------------------------------------------------------- actions

    /**
     * Turns Do Not Disturb on or off. Returns false when policy access is missing.
     */
    fun setDoNotDisturb(enabled: Boolean, store: SettingsStore): Boolean {
        if (!hasDndAccess()) return false
        return runCatching {
            if (enabled) {
                val previous = notificationManager.currentInterruptionFilter
                store.update { it.copy(savedDndFilter = previous) }
                notificationManager.setInterruptionFilter(
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                )
            } else {
                val previous = store.current.savedDndFilter
                    .takeIf { it > 0 } ?: NotificationManager.INTERRUPTION_FILTER_ALL
                notificationManager.setInterruptionFilter(previous)
                store.update { it.copy(savedDndFilter = -1) }
            }
            true
        }.getOrElse {
            Logx.w("Could not change Do Not Disturb", it)
            false
        }
    }

    /** Extends the screen-off timeout so the display does not sleep mid-match. */
    fun setScreenTimeout(millis: Int, store: SettingsStore): Boolean {
        if (!canWriteSystemSettings()) return false
        return runCatching {
            if (store.current.savedScreenTimeoutMs < 0) {
                val previous = Settings.System.getInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    60_000
                )
                store.update { it.copy(savedScreenTimeoutMs = previous) }
            }
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                millis
            )
        }.getOrElse {
            Logx.w("Could not change screen timeout", it)
            false
        }
    }

    /** Sets a fixed brightness level (0..255) and disables auto-brightness. */
    fun setBrightness(level: Int, store: SettingsStore): Boolean {
        if (!canWriteSystemSettings()) return false
        return runCatching {
            val resolver = appContext.contentResolver
            if (store.current.savedBrightness < 0) {
                val previousLevel =
                    Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                val previousMode = Settings.System.getInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                )
                store.update {
                    it.copy(savedBrightness = previousLevel, savedBrightnessMode = previousMode)
                }
            }
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                level.coerceIn(10, 255)
            )
        }.getOrElse {
            Logx.w("Could not change brightness", it)
            false
        }
    }

    /** Restores every system value FF BoostX changed. Safe to call at any time. */
    fun revertAll(store: SettingsStore) {
        val s = store.current

        if (s.savedDndFilter > 0 && hasDndAccess()) {
            runCatching { notificationManager.setInterruptionFilter(s.savedDndFilter) }
                .onFailure { Logx.w("Could not restore Do Not Disturb", it) }
        }

        if (canWriteSystemSettings()) {
            val resolver = appContext.contentResolver
            if (s.savedScreenTimeoutMs > 0) {
                runCatching {
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        s.savedScreenTimeoutMs
                    )
                }.onFailure { Logx.w("Could not restore screen timeout", it) }
            }
            if (s.savedBrightness >= 0) {
                runCatching {
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        s.savedBrightnessMode.takeIf { it >= 0 }
                            ?: Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    )
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        s.savedBrightness
                    )
                }.onFailure { Logx.w("Could not restore brightness", it) }
            }
        }

        store.update {
            it.copy(
                savedDndFilter = -1,
                savedScreenTimeoutMs = -1,
                savedBrightness = -1,
                savedBrightnessMode = -1
            )
        }
    }

    /** True when at least one system value is currently overridden by the app. */
    fun hasPendingChanges(s: AppSettings): Boolean =
        s.savedDndFilter > 0 || s.savedScreenTimeoutMs > 0 || s.savedBrightness >= 0

    /**
     * Asks this app's window for the highest refresh rate the panel supports.
     * This affects the FF BoostX window only; games set their own frame rate.
     */
    fun requestHighestRefreshRate(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return runCatching {
            val modes = activity.windowManager.defaultDisplay?.supportedModes ?: return false
            val best = modes.maxByOrNull { it.refreshRate } ?: return false
            val params: WindowManager.LayoutParams = activity.window.attributes
            params.preferredDisplayModeId = best.modeId
            activity.window.attributes = params
            true
        }.getOrElse {
            Logx.w("Could not request refresh rate", it)
            false
        }
    }


    /** The application context, exposed for capability probes only. */
    fun probeContext(): Context = appContext

    // ------------------------------------------------------- overlay access

    /** Whether the Display over other apps permission has been granted. */
    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(appContext)

    fun overlayPermissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${appContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlayState(): CapabilityState =
        if (canDrawOverlays()) CapabilityState.AVAILABLE else CapabilityState.PERMISSION_REQUIRED

    // --------------------------------------------------------- usage access

    /**
     * Usage access lets the app notice the game closing. It is checked through
     * AppOpsManager rather than by catching an exception from a query, so no
     * failed read is needed to find out.
     */
    fun hasUsageAccess(): Boolean = runCatching {
        val ops = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun usageAccessState(): CapabilityState =
        if (hasUsageAccess()) CapabilityState.AVAILABLE else CapabilityState.PERMISSION_REQUIRED

    /**
     * The package currently in the foreground, from usage events over the last
     * minute. Returns null without usage access. Only the package name is read;
     * no screen content is ever inspected, which is why this uses usage stats
     * rather than an accessibility service.
     */
    fun foregroundPackage(): String? {
        if (!hasUsageAccess()) return null
        return runCatching {
            val usage = appContext.getSystemService(Context.USAGE_STATS_SERVICE)
                as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usage.queryEvents(now - 60_000L, now)
            val event = android.app.usage.UsageEvents.Event()
            var latestPackage: String? = null
            var latestTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND &&
                    event.timeStamp >= latestTime
                ) {
                    latestTime = event.timeStamp
                    latestPackage = event.packageName
                }
            }
            latestPackage
        }.getOrElse {
            Logx.w("Could not read foreground package", it)
            null
        }
    }

    // --------------------------------------------------------- display modes

    /** Every refresh rate the panel advertises, de-duplicated and sorted. */
    fun supportedRefreshRates(): List<Float> = runCatching {
        val display = (appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return listOf(60f)
        display.supportedModes
            ?.map { it.refreshRate }
            ?.map { kotlin.math.round(it) }
            ?.distinct()
            ?.sorted()
            ?: listOf(display.refreshRate)
    }.getOrDefault(listOf(60f))

    /**
     * Asks for a specific refresh rate for the given window. This only ever
     * affects the requesting app's own window - a game sets its own frame rate,
     * and no app can set another's.
     */
    fun requestRefreshRate(activity: Activity, targetHz: Int?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return runCatching {
            val modes = activity.windowManager.defaultDisplay?.supportedModes ?: return false
            val mode = if (targetHz == null) {
                modes.maxByOrNull { it.refreshRate }
            } else {
                modes.filter { kotlin.math.round(it.refreshRate).toInt() <= targetHz }
                    .maxByOrNull { it.refreshRate }
                    ?: modes.minByOrNull { it.refreshRate }
            } ?: return false

            val params: LayoutParams = activity.window.attributes
            params.preferredDisplayModeId = mode.modeId
            activity.window.attributes = params
            true
        }.getOrElse {
            Logx.w("Could not request refresh rate", it)
            false
        }
    }

    /** Keeps the screen on for as long as the given window is showing. */
    fun setKeepScreenOn(window: Window, keepOn: Boolean) {
        runCatching {
            if (keepOn) {
                window.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }.onFailure { Logx.w("Could not change the keep-awake flag", it) }
    }

    /**
     * Requests sustained performance mode for a window where the device supports
     * it: a lower but thermally stable clock ceiling. It applies to this app's
     * window only.
     */
    fun setSustainedPerformance(window: Window, enabled: Boolean): Boolean {
        if (!PerfEngine.sustainedPerformanceSupported(appContext)) return false
        return runCatching {
            window.setSustainedPerformanceMode(enabled)
            true
        }.getOrElse {
            Logx.w("Could not set sustained performance mode", it)
            false
        }
    }

    // ------------------------------------------------------ extra shortcuts

    /** Android's Private DNS setting - the supported way to change resolver. */
    fun privateDnsIntent(): Intent =
        Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---------------------------------------------------------------- games

    /**
     * Looks up the known Free Fire packages declared in the manifest <queries>
     * block. Returns only what is genuinely installed.
     */
    fun installedGames(): List<GameApp> {
        val pm = appContext.packageManager
        val result = linkedMapOf<String, GameApp>()
        KNOWN_GAMES.forEach { (pkg, label) ->
            runCatching { pm.getLaunchIntentForPackage(pkg)?.let { result[pkg] = GameApp(pkg, label) } }
        }
        // Android exposes launcher activities without requiring QUERY_ALL_PACKAGES on
        // supported versions. Prefer apps explicitly categorized as games, then add
        // obvious game labels as an OEM-compatible fallback.
        runCatching {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, 0).forEach { info ->
                val app = info.activityInfo.applicationInfo
                val pkg = app.packageName
                if (pkg == appContext.packageName || result.containsKey(pkg)) return@forEach
                val label = app.loadLabel(pm).toString()
                val isGameCategory = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    app.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
                val looksLikeGame = listOf("game", "free fire", "pubg", "minecraft", "cod", "roblox", "bgmi", "valorant")
                    .any { label.contains(it, ignoreCase = true) }
                if (isGameCategory || looksLikeGame) result[pkg] = GameApp(pkg, label)
            }
        }.onFailure { Logx.w("Could not enumerate launcher games", it) }
        return result.values.sortedBy { it.label.lowercase() }
    }

    fun launchIntentFor(packageName: String): Intent? =
        appContext.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---------------------------------------------------------------- shortcuts

    fun appDetailsIntent(): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${appContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the system screen where running/background apps can be managed. */
    fun backgroundAppsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun developerOptionsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun dataUsageIntent(): Intent =
        Intent(Settings.ACTION_DATA_USAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun displaySettingsIntent(): Intent =
        Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Describes the active transport, e.g. "Wi-Fi" or "Mobile data". */
    fun networkSummary(): String = runCatching {
        val network = connectivityManager.activeNetwork ?: return "Offline"
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }.getOrDefault("Unknown")

    /** Safely starts a settings intent, returning false if no activity handles it. */
    fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrElse {
        Logx.w("No activity for intent ${intent.action}", it)
        false
    }

    companion object {
        private val KNOWN_GAMES = listOf(
            "com.dts.freefireth" to "Free Fire",
            "com.dts.freefiremax" to "Free Fire MAX"
        )

        @Volatile
        private var instance: SystemActions? = null

        fun get(context: Context): SystemActions =
            instance ?: synchronized(this) {
                instance ?: SystemActions(context).also { instance = it }
            }
    }
}
