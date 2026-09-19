package com.ffboostx.core

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Single source of device statistics for the whole app.
 *
 * The flow is cold: sampling only happens while something is collecting it, and
 * collectors are tied to the composition lifecycle, so no timer, handler or
 * service keeps running once the app leaves the foreground.
 */
class DeviceMonitor private constructor(context: Context) {

    val reader = DeviceStatsReader(context)

    /** Emits a fresh snapshot immediately, then once per [intervalMs]. */
    fun snapshots(intervalMs: Long): Flow<DeviceSnapshot> = flow {
        while (true) {
            emit(reader.read())
            delay(intervalMs)
        }
    }

    companion object {
        @Volatile
        private var instance: DeviceMonitor? = null

        fun get(context: Context): DeviceMonitor =
            instance ?: synchronized(this) {
                instance ?: DeviceMonitor(context.applicationContext).also { instance = it }
            }
    }
}
