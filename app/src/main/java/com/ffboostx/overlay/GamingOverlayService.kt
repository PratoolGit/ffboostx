package com.ffboostx.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import com.ffboostx.MainActivity
import com.ffboostx.R
import com.ffboostx.core.DeviceStatsReader
import com.ffboostx.core.HudMetric
import com.ffboostx.core.Logx
import com.ffboostx.core.NetworkEngine
import com.ffboostx.core.NetworkQuality
import com.ffboostx.core.OverlayConfig
import com.ffboostx.core.OverlayConfigStore
import com.ffboostx.core.OverlayCorner
import com.ffboostx.core.SessionStore
import com.ffboostx.core.SystemActions
import com.ffboostx.core.ThermalEngine
import com.ffboostx.core.valueOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Hosts the HUD and crosshair windows for the length of a gaming session.
 *
 * It runs as a foreground service because that is the only honest way to keep
 * overlay windows alive: the user always sees a notification saying the overlay
 * is up, with a one-tap stop action. When the session ends - by the game
 * closing, by the notification action, or by the app - the windows come down and
 * the service stops itself. Nothing is left running afterwards.
 */
class GamingOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var metricsJob: Job? = null
    private var watcherJob: Job? = null

    private lateinit var windowManager: WindowManager
    private lateinit var overlayStore: OverlayConfigStore
    private lateinit var statsReader: DeviceStatsReader
    private lateinit var networkEngine: NetworkEngine
    private lateinit var thermalEngine: ThermalEngine
    private lateinit var actions: SystemActions
    private lateinit var sessions: SessionStore

    private var hudView: HudView? = null
    private var crosshairView: CrosshairView? = null
    private var watchedPackage: String? = null
    private var lastQuality: NetworkQuality? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayStore = OverlayConfigStore.get(this)
        statsReader = DeviceStatsReader(this)
        networkEngine = NetworkEngine.get(this)
        thermalEngine = ThermalEngine.get(this)
        actions = SystemActions.get(this)
        sessions = SessionStore.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
        }

        // Without the overlay permission there is nothing this service can do,
        // so it refuses to start rather than sitting in the foreground doing
        // nothing.
        if (!actions.canDrawOverlays()) {
            Logx.w("Overlay service started without the overlay permission")
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()
        watchedPackage = intent?.getStringExtra(EXTRA_WATCH_PACKAGE)

        attachWindows(overlayStore.current)
        startMetricsLoop()
        startGameWatcher()

        // Deliberately not sticky: if Android kills this service under memory
        // pressure, the overlay should stay down rather than reappear over
        // whatever the user is doing later.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        detachWindows()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------- windows

    private fun attachWindows(config: OverlayConfig) {
        if (config.hudEnabled && hudView == null) {
            val view = HudView(this, config)
            val params = baseParams().apply {
                gravity = when (config.hudCorner) {
                    OverlayCorner.TOP_LEFT -> Gravity.TOP or Gravity.START
                    OverlayCorner.TOP_RIGHT -> Gravity.TOP or Gravity.END
                    OverlayCorner.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
                    OverlayCorner.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
                }
                x = (12 * resources.displayMetrics.density).toInt()
                y = (56 * resources.displayMetrics.density).toInt()
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            if (addView(view, params)) hudView = view
        }

        if (config.crosshairEnabled && crosshairView == null) {
            val view = CrosshairView(this, config)
            val params = baseParams().apply {
                gravity = Gravity.CENTER
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
            }
            if (addView(view, params)) crosshairView = view
        }
    }

    private fun addView(view: android.view.View, params: WindowManager.LayoutParams): Boolean =
        runCatching {
            windowManager.addView(view, params)
            true
        }.getOrElse {
            Logx.w("Could not add overlay window", it)
            false
        }

    private fun detachWindows() {
        hudView?.let { view -> runCatching { windowManager.removeView(view) } }
        crosshairView?.let { view -> runCatching { windowManager.removeView(view) } }
        hudView = null
        crosshairView = null
    }

    /**
     * Not focusable and not touchable, so every tap passes straight through to
     * the game. The overlay can draw over the screen; it can never read it or
     * intercept input.
     */
    private fun baseParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    // ------------------------------------------------------------- metrics

    private fun startMetricsLoop() {
        metricsJob?.cancel()
        metricsJob = scope.launch {
            var tick = 0
            while (true) {
                val config = overlayStore.current
                if (!config.hudEnabled && !config.crosshairEnabled) {
                    stopEverything()
                    return@launch
                }

                crosshairView?.apply(config)

                if (config.hudEnabled) {
                    // The network probe opens real sockets, so it runs far less
                    // often than the cheap local reads.
                    if (tick % NETWORK_EVERY_TICKS == 0) {
                        val quality = runCatching {
                            networkEngine.measureQuality(count = 4, timeoutMs = 1_000)
                        }.getOrNull()
                        if (quality != null) {
                            lastQuality = quality
                            sessions.recordNetworkSample(quality)
                        }
                    }
                    hudView?.update(buildRows(config), config)
                }

                sessions.recordThermal(thermalEngine.currentLevel())
                tick++
                delay(HUD_INTERVAL_MS)
            }
        }
    }

    private fun buildRows(config: OverlayConfig): List<HudRow> {
        val snapshot = statsReader.read()
        val quality = lastQuality

        val rows = mutableListOf<HudRow>()

        // Always first, always N/A, always honest.
        rows += HudRow("FPS", null)

        for (metric in HudView.ORDER) {
            if (metric !in config.hudMetrics) continue
            rows += when (metric) {
                HudMetric.PING -> HudRow(
                    "Latency",
                    quality?.avgMs?.let { "${it.toInt()} ms" },
                    accent = Color.rgb(56, 189, 248)
                )

                HudMetric.JITTER -> HudRow(
                    "Jitter",
                    quality?.jitterMs?.let { "${it.toInt()} ms" },
                    accent = Color.rgb(56, 189, 248)
                )

                HudMetric.TEMPERATURE -> HudRow(
                    "Temp",
                    snapshot.battery.temperatureC.valueOrNull()
                        ?.let { String.format("%.1f°C", it) },
                    accent = Color.rgb(74, 222, 128)
                )

                HudMetric.BATTERY -> HudRow(
                    "Battery",
                    "${snapshot.battery.percent}%",
                    accent = Color.rgb(74, 222, 128)
                )

                HudMetric.REFRESH -> HudRow(
                    "Refresh",
                    "${snapshot.display.currentRefreshHz.toInt()} Hz",
                    accent = Color.rgb(255, 138, 61)
                )

                HudMetric.MEMORY -> HudRow(
                    "Free RAM",
                    String.format("%.1f GB", snapshot.memory.availableBytes / 1_073_741_824.0),
                    accent = Color.rgb(255, 138, 61)
                )
            }
        }
        return rows
    }

    // -------------------------------------------------------- game watcher

    /**
     * Notices the watched game leaving the foreground so the overlay can come
     * down on its own.
     *
     * This uses usage access, which the user grants explicitly, and reads only
     * foreground-app events. An accessibility service would also work and is
     * what many boosters use, but it can read screen content, which is far more
     * access than this needs.
     */
    private fun startGameWatcher() {
        watcherJob?.cancel()
        val target = watchedPackage ?: return
        if (!overlayStore.current.hudGamingOnly) return
        if (!actions.hasUsageAccess()) return

        watcherJob = scope.launch {
            // Give the game time to come to the foreground before watching for
            // it leaving.
            delay(GAME_START_GRACE_MS)
            var sawGame = false
            while (true) {
                val foreground = actions.foregroundPackage()
                if (foreground == target) {
                    sawGame = true
                } else if (sawGame && foreground != null && foreground != packageName) {
                    Logx.w("Watched game left the foreground; taking the overlay down")
                    stopEverything()
                    return@launch
                }
                delay(WATCH_INTERVAL_MS)
            }
        }
    }

    // ------------------------------------------------------- service plumbing

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gaming overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while the FF BoostX HUD or crosshair is on screen."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, GamingOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Gaming overlay active")
            .setContentText("HUD and crosshair are on screen. Tap stop to remove them.")
            .setSmallIcon(R.drawable.ic_stat_boost)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null as android.graphics.drawable.Icon?,
                    "Stop overlay",
                    stop
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopEverything() {
        metricsJob?.cancel()
        watcherJob?.cancel()
        detachWindows()
        overlayStore.update { it.copy(hudEnabled = false, crosshairEnabled = false) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "ffboostx.overlay"
        private const val NOTIFICATION_ID = 4201
        private const val HUD_INTERVAL_MS = 1_500L
        private const val NETWORK_EVERY_TICKS = 10      // a probe roughly every 15s
        private const val WATCH_INTERVAL_MS = 3_000L
        private const val GAME_START_GRACE_MS = 8_000L

        const val ACTION_STOP = "com.ffboostx.overlay.STOP"
        const val EXTRA_WATCH_PACKAGE = "watch_package"

        fun start(context: Context, watchPackage: String?) {
            val intent = Intent(context, GamingOverlayService::class.java)
                .putExtra(EXTRA_WATCH_PACKAGE, watchPackage)
            runCatching { context.startForegroundService(intent) }
                .onFailure { Logx.w("Could not start the overlay service", it) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GamingOverlayService::class.java)
                .setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
                .onFailure { Logx.w("Could not stop the overlay service", it) }
        }
    }
}
