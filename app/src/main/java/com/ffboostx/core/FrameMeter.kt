package com.ffboostx.core

import android.view.Choreographer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Measures how fast **this app's own window** is producing frames, using
 * Choreographer vsync callbacks.
 *
 * This is deliberately never presented as the game's FPS. Android gives an app
 * frame timing for its own rendering only; another process's frame rate is not
 * readable, and inferring it from the display refresh rate would be a guess.
 * Everywhere a game FPS figure would go, FF BoostX prints N/A.
 *
 * The flow is cold, so the vsync callback is only posted while something is
 * collecting and is cancelled as soon as collection stops.
 */
class FrameMeter {

    fun rates(windowMs: Long = 1_000L): Flow<Float> = callbackFlow {
        var frames = 0
        var windowStartNanos = 0L
        val windowNanos = windowMs * 1_000_000L

        val choreographer = Choreographer.getInstance()
        var active = true

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!active) return
                if (windowStartNanos == 0L) {
                    windowStartNanos = frameTimeNanos
                } else {
                    frames++
                    val elapsed = frameTimeNanos - windowStartNanos
                    if (elapsed >= windowNanos) {
                        trySend(frames * 1_000_000_000f / elapsed)
                        frames = 0
                        windowStartNanos = frameTimeNanos
                    }
                }
                choreographer.postFrameCallback(this)
            }
        }

        choreographer.postFrameCallback(callback)

        awaitClose {
            active = false
            runCatching { choreographer.removeFrameCallback(callback) }
        }
    }
}
