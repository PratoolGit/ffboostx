package com.ffboostx.core

import android.util.Log
import com.ffboostx.BuildConfig

/**
 * Thin logging facade. Every call site is guarded by BuildConfig.DEBUG so R8 can
 * strip the bodies (and the string concatenations feeding them) out of release builds.
 */
internal object Logx {
    private const val TAG = "FFBoostX"

    fun w(message: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, t)
    }
}
