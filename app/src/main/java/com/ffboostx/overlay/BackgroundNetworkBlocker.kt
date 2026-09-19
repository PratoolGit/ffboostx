package com.ffboostx.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.ffboostx.MainActivity
import com.ffboostx.R
import com.ffboostx.core.Logx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Background Network Blocker.
 *
 * This is a local VpnService whose entire job is to stop *other* apps using the
 * connection while you play. Every app except the game (and FF BoostX itself) is
 * routed into a tunnel that goes nowhere, so their traffic is dropped at the
 * device. The game keeps the real network to itself.
 *
 * What it explicitly does not do, and cannot do as written:
 *  - It never reads, parses, forwards or logs a single packet. The tunnel file
 *    descriptor is opened and then left alone; there is no read loop in this
 *    class at all.
 *  - There is no remote server. Nothing leaves the device through FF BoostX.
 *  - It does not touch the game's traffic. The game is excluded from the tunnel,
 *    so its packets never enter it.
 *
 * It also cannot reduce your ping. It frees bandwidth and removes radio
 * contention from background apps, which helps a congested link and does nothing
 * at all on an idle one. The UI says exactly that.
 */
class BackgroundNetworkBlocker : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }

        val allowed = intent?.getStringExtra(EXTRA_ALLOW_PACKAGE)
        if (establish(allowed)) {
            startInForeground(allowed)
            _active.value = true
        } else {
            shutdown()
        }
        return START_NOT_STICKY
    }

    private fun establish(allowedPackage: String?): Boolean = runCatching {
        val builder = Builder()
            .setSession("FF BoostX background blocker")
            // A private, non-routable address space. Nothing is listening on it.
            .addAddress("10.111.222.1", 32)
            .addRoute("0.0.0.0", 0)
            .setBlocking(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // The game is excluded, so its packets bypass the tunnel entirely and
        // reach the network normally. Everything else falls into the tunnel and
        // is dropped because nothing ever reads from it.
        allowedPackage?.let { pkg ->
            runCatching { builder.addDisallowedApplication(pkg) }
                .onFailure { Logx.w("Could not exclude $pkg from the tunnel", it) }
        }
        runCatching { builder.addDisallowedApplication(packageName) }

        val descriptor = builder.establish()
        if (descriptor == null) {
            Logx.w("VPN consent was not granted or the tunnel could not be established")
            return false
        }
        tunnel = descriptor
        true
    }.getOrElse {
        Logx.w("Could not establish the background blocker", it)
        false
    }

    private fun startInForeground(allowedPackage: String?) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Background network blocker",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shown while other apps are blocked from the network."
                    setShowBadge(false)
                }
            )
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, BackgroundNetworkBlocker::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (allowedPackage != null) {
            "Other apps are offline so the game keeps the connection."
        } else {
            "Other apps are offline."
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Background apps blocked")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_boost)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null as android.graphics.drawable.Icon?,
                    "Stop blocking",
                    stop
                ).build()
            )
            .build()

        // Android 14 requires every foreground service to declare a type. A VPN
        // is covered by systemExempted, which is declared in the manifest too.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun shutdown() {
        runCatching { tunnel?.close() }
        tunnel = null
        _active.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // The user turned the VPN off from system settings. Respect it silently.
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { tunnel?.close() }
        tunnel = null
        _active.value = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "ffboostx.netblock"
        private const val NOTIFICATION_ID = 4202

        const val ACTION_STOP = "com.ffboostx.netblock.STOP"
        const val EXTRA_ALLOW_PACKAGE = "allow_package"

        private val _active = MutableStateFlow(false)

        /** True while the tunnel is up. Survives screen changes, not process death. */
        val active: StateFlow<Boolean> = _active.asStateFlow()

        /**
         * Returns the consent intent when Android still needs the user to approve
         * a VPN, or null when consent already exists.
         */
        fun consentIntent(context: Context): Intent? = runCatching {
            VpnService.prepare(context)
        }.getOrNull()

        fun start(context: Context, allowPackage: String?) {
            val intent = Intent(context, BackgroundNetworkBlocker::class.java)
                .putExtra(EXTRA_ALLOW_PACKAGE, allowPackage)
            runCatching { context.startService(intent) }
                .onFailure { Logx.w("Could not start the background blocker", it) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundNetworkBlocker::class.java)
                .setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
                .onFailure { Logx.w("Could not stop the background blocker", it) }
        }
    }
}
