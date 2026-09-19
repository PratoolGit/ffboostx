package com.ffboostx.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ffboostx.core.Macro
import com.ffboostx.core.MacroPoint
import com.ffboostx.core.MacroStep
import com.ffboostx.core.MacroStore
import com.ffboostx.core.Logx
import java.util.concurrent.Executors

/**
 * No-root macro engine. Accessibility is an explicit Android user permission.
 * Saved recordings remain on-device. The service can replay taps/swipes using
 * AccessibilityService.dispatchGesture().
 *
 * Recording uses Android's motion-event accessibility pipeline on Android 14+
 * when available. Because Android reserves that pipeline for accessibility,
 * devices/OEMs can differ in how recording affects the live game touch stream;
 * the UI therefore labels recording as experimental and never silently enables it.
 */
class MacroAccessibilityService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private lateinit var store: MacroStore
    private var panel: View? = null
    private var recording = false
    private var currentPoints = mutableListOf<MacroPoint>()
    private var currentDown = 0L
    private var lastPackage: String? = null
    private var recorderButton: TextView? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_START_RECORDING -> startRecording()
                ACTION_STOP_RECORDING -> stopRecording()
                ACTION_REFRESH -> refreshPanel()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        store = MacroStore.get(this)
        registerReceiver(commandReceiver, IntentFilter().apply {
            addAction(ACTION_START_RECORDING); addAction(ACTION_STOP_RECORDING); addAction(ACTION_REFRESH)
        })
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityServiceInfo.DEFAULT
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            if (Build.VERSION.SDK_INT >= 34) {
                // Observe touchscreen motion without requesting touch-exploration mode.
                setMotionEventSources(android.view.InputDevice.SOURCE_TOUCHSCREEN)
            }
        }
        refreshPanel()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        event?.packageName?.toString()?.let { if (it != packageName) lastPackage = it }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removePanel()
        runCatching { unregisterReceiver(commandReceiver) }
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (!recording || Build.VERSION.SDK_INT < 34) return
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                currentPoints = mutableListOf()
                currentDown = SystemClock.uptimeMillis()
                currentPoints += MacroPoint(event.rawX, event.rawY, 0L)
            }
            MotionEvent.ACTION_MOVE -> {
                val t = (SystemClock.uptimeMillis() - currentDown).coerceAtLeast(0L)
                // Sample at most every ~12 ms to keep recordings compact.
                val previous = currentPoints.lastOrNull()
                if (previous == null || t - previous.tMs >= 12L) {
                    currentPoints += MacroPoint(event.rawX, event.rawY, t)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val t = (SystemClock.uptimeMillis() - currentDown).coerceAtLeast(0L)
                currentPoints += MacroPoint(event.rawX, event.rawY, t)
                if (currentPoints.isNotEmpty()) {
                    val dx = currentPoints.last().x - currentPoints.first().x
                    val dy = currentPoints.last().y - currentPoints.first().y
                    val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
                    val type = when {
                        action == MotionEvent.ACTION_CANCEL -> "cancel"
                        distance < 18.0 && t < 350L -> "tap"
                        distance < 18.0 -> "long_press"
                        else -> "swipe"
                    }
                    val step = MacroStep(type, currentPoints.toList(), t)
                    // One touch sequence = one editable macro step. Recording stops
                    // only when the user explicitly presses Stop.
                    pendingSteps += step
                }
            }
        }
    }

    private val pendingSteps = mutableListOf<MacroStep>()

    private fun startRecording() {
        if (recording) return
        pendingSteps.clear()
        recording = true
        recorderButton?.text = "■ STOP RECORDING"
        refreshPanel()
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        recorderButton?.text = "● RECORD"
        val steps = pendingSteps.toList()
        pendingSteps.clear()
        if (steps.isNotEmpty()) {
            val name = "Macro ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
            store.save(Macro(name = name, packageName = lastPackage, steps = steps, overlayButton = true))
        }
        refreshPanel()
    }

    private fun replay(macro: Macro) {
        if (recording) return
        executor.execute {
            repeat(macro.repeatCount) {
                for (step in macro.steps) {
                    if (step.points.isEmpty()) continue
                    val first = step.points.first()
                    val path = Path().apply { moveTo(first.x, first.y) }
                    step.points.drop(1).forEach { path.lineTo(it.x, it.y) }
                    val duration = (step.durationMs / macro.speed).toLong().coerceIn(1L, 10_000L)
                    val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    dispatchGesture(gesture, null, null)
                    SystemClock.sleep((duration + 25L).coerceAtMost(10_050L))
                }
            }
        }
    }

    private fun refreshPanel() {
        if (!::wm.isInitialized) return
        removePanel()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.argb(210, 10, 17, 32))
        }
        recorderButton = TextView(this).apply {
            text = if (recording) "■ STOP RECORDING" else "● RECORD"
            setTextColor(Color.WHITE)
            setTextSize(12f)
            setPadding(18, 14, 18, 14)
            setOnClickListener { if (recording) stopRecording() else startRecording() }
        }
        root.addView(recorderButton)
        store.all().filter { it.overlayButton }.take(8).forEach { macro ->
            val b = Button(this).apply {
                text = "▶ ${macro.name}"
                setTextColor(Color.WHITE)
                setOnClickListener { replay(macro) }
            }
            root.addView(b, LinearLayout.LayoutParams(-2, -2))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 8
        }
        runCatching { wm.addView(root, params); panel = root }
            .onFailure { Logx.w("Could not add macro overlay", it) }
    }

    private fun removePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        recorderButton = null
    }

    companion object {
        const val ACTION_START_RECORDING = "com.ffboostx.macro.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.ffboostx.macro.STOP_RECORDING"
        const val ACTION_REFRESH = "com.ffboostx.macro.REFRESH"

        fun openAccessibilitySettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
