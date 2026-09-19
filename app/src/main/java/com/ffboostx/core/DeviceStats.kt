package com.ffboostx.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import java.io.File

/**
 * A value that the device may or may not expose. Every statistic in FF BoostX is
 * modelled this way so the interface can say "unavailable" instead of inventing
 * a number.
 */
sealed interface Reading<out T> {
    data class Value<T>(val value: T) : Reading<T>
    data class Unavailable(val reason: String) : Reading<Nothing>
}

/** The measurement, or null when the platform did not expose it. */
@Suppress("UNCHECKED_CAST")
fun <T> Reading<T>.valueOrNull(): T? = (this as? Reading.Value<T>)?.value

/** The explanation, or null when a real measurement was returned. */
fun Reading<*>.reasonOrNull(): String? = (this as? Reading.Unavailable)?.reason

data class MemoryStats(
    val totalBytes: Long,
    val availableBytes: Long,
    val lowMemory: Boolean,
    val thresholdBytes: Long
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

data class CpuStats(
    val cores: Int,
    /** Max clock in MHz, when the kernel exposes cpufreq to apps. */
    val maxClockMhz: Reading<Int>,
    /** 0f..1f system-wide load, when /proc/stat is readable. */
    val load: Reading<Float>
)

data class GpuStats(
    /** Best-effort GPU busy percentage from a read-only vendor node, when exposed. */
    val load: Reading<Float>
)

data class BatteryStats(
    val percent: Int,
    val charging: Boolean,
    /** Battery temperature in Celsius, reported by the battery service. */
    val temperatureC: Reading<Float>,
    /** PowerManager thermal throttling status (API 29+). */
    val thermalStatus: Reading<ThermalLevel>
)

enum class ThermalLevel(val label: String) {
    NONE("Normal"),
    LIGHT("Light"),
    MODERATE("Moderate"),
    SEVERE("Severe"),
    CRITICAL("Critical"),
    EMERGENCY("Emergency"),
    SHUTDOWN("Shutdown");
}

data class DisplayStats(
    val currentRefreshHz: Float,
    val maxRefreshHz: Float
)

data class DeviceSnapshot(
    val memory: MemoryStats,
    val cpu: CpuStats,
    val gpu: GpuStats,
    val battery: BatteryStats,
    val display: DisplayStats
)

/**
 * Reads live device statistics. All readers are cheap, synchronous and failure
 * tolerant: anything the platform refuses to expose comes back as
 * [Reading.Unavailable] rather than a fabricated value.
 */
class DeviceStatsReader(context: Context) {

    private val appContext = context.applicationContext
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val displayManager =
        appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    /** Core count and max clock never change, so they are read once. */
    private val cores: Int = Runtime.getRuntime().availableProcessors()
    private val maxClock: Reading<Int> by lazy { readMaxClockMhz() }

    private var previousCpuTotal = 0L
    private var previousCpuIdle = 0L
    private var procStatBlocked = false

    fun read(): DeviceSnapshot = DeviceSnapshot(
        memory = readMemory(),
        cpu = CpuStats(cores = cores, maxClockMhz = maxClock, load = readCpuLoad()),
        gpu = GpuStats(load = readGpuLoad()),
        battery = readBattery(),
        display = readDisplay()
    )

    fun readMemory(): MemoryStats {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return MemoryStats(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            lowMemory = info.lowMemory,
            thresholdBytes = info.threshold
        )
    }


    private fun readGpuLoad(): Reading<Float> {
        val candidates = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy",
            "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/devfreq/gpu/load",
            "/sys/class/devfreq/17000000.gpu/load"
        )
        for (path in candidates) {
            val value = runCatching { File(path).readText().trim() }.getOrNull() ?: continue
            val match = Regex("(\\d+(?:\\.\\d+)?)").find(value) ?: continue
            val n = match.groupValues[1].toFloatOrNull() ?: continue
            if (n in 0f..100f) return Reading.Value(n / 100f)
        }
        return Reading.Unavailable("GPU load is not exposed by this device")
    }

    private fun readBattery(): BatteryStats {
        // ACTION_BATTERY_CHANGED is sticky: a null receiver returns the last value
        // immediately without registering anything that could leak.
        val intent: Intent? = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else 0

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        // EXTRA_TEMPERATURE is documented in tenths of a degree Celsius.
        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        val temperature: Reading<Float> = if (rawTemp != Int.MIN_VALUE && rawTemp > 0) {
            Reading.Value(rawTemp / 10f)
        } else {
            Reading.Unavailable("Not reported by this device")
        }

        return BatteryStats(
            percent = percent,
            charging = charging,
            temperatureC = temperature,
            thermalStatus = readThermal()
        )
    }

    private fun readThermal(): Reading<ThermalLevel> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Reading.Unavailable("Requires Android 10+")
        }
        return try {
            val level = when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.SHUTDOWN
                else -> ThermalLevel.NONE
            }
            Reading.Value(level)
        } catch (t: Throwable) {
            Logx.w("Thermal status unavailable", t)
            Reading.Unavailable("Not exposed by this device")
        }
    }

    private fun readDisplay(): DisplayStats {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val current = display?.refreshRate ?: 60f
        val max = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: current
        } else {
            current
        }
        return DisplayStats(currentRefreshHz = current, maxRefreshHz = max)
    }

    private fun readMaxClockMhz(): Reading<Int> {
        // cpufreq is readable on many kernels but blocked by SELinux on others.
        var best = 0
        for (cpu in 0 until cores) {
            val khz = runCatching {
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
                    .takeIf { it.canRead() }
                    ?.readText()
                    ?.trim()
                    ?.toIntOrNull()
            }.getOrNull() ?: continue
            if (khz > best) best = khz
        }
        return if (best > 0) {
            Reading.Value(best / 1000)
        } else {
            Reading.Unavailable("Kernel does not expose cpufreq")
        }
    }

    /**
     * System-wide CPU load from /proc/stat. Android restricts this on most
     * devices running Oreo and above; when that happens we stop trying and the
     * interface falls back to showing core count and clock instead of guessing.
     */
    private fun readCpuLoad(): Reading<Float> {
        if (procStatBlocked) return Reading.Unavailable("Restricted by Android")

        val fields = runCatching {
            File("/proc/stat").bufferedReader().use { it.readLine() }
                ?.split(" ")
                ?.filter { it.isNotBlank() }
        }.getOrNull()

        if (fields == null || fields.size < 5 || fields[0] != "cpu") {
            procStatBlocked = true
            return Reading.Unavailable("Restricted by Android")
        }

        val values = fields.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) {
            procStatBlocked = true
            return Reading.Unavailable("Restricted by Android")
        }

        val total = values.sum()
        val idle = values[3]
        val totalDelta = total - previousCpuTotal
        val idleDelta = idle - previousCpuIdle
        previousCpuTotal = total
        previousCpuIdle = idle

        if (totalDelta <= 0L) return Reading.Unavailable("Sampling")
        val load = ((totalDelta - idleDelta).toFloat() / totalDelta).coerceIn(0f, 1f)
        return Reading.Value(load)
    }
}

/** Formats a byte count as GB with one decimal, e.g. "3.8 GB". */
fun Long.toGbString(): String = String.format("%.1f GB", this / 1_073_741_824.0)

/** Formats a byte count as MB, e.g. "412 MB". */
fun Long.toMbString(): String = "${(this / 1_048_576.0).toInt()} MB"
