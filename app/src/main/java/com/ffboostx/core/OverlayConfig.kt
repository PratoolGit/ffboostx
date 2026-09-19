package com.ffboostx.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CrosshairStyle(val label: String) {
    DOT("Dot"),
    PLUS("Plus"),
    CIRCLE("Circle"),
    PRECISION("Precision"),
    MINIMAL("Minimal")
}

enum class OverlayCorner(val label: String) {
    TOP_LEFT("Top left"),
    TOP_RIGHT("Top right"),
    BOTTOM_LEFT("Bottom left"),
    BOTTOM_RIGHT("Bottom right")
}

/** Which rows the HUD draws. FPS is absent by design - see [HudMetric]. */
enum class HudMetric(val label: String) {
    PING("Latency"),
    JITTER("Jitter"),
    TEMPERATURE("Temperature"),
    BATTERY("Battery"),
    REFRESH("Refresh rate"),
    MEMORY("Free memory")
}

data class OverlayConfig(
    val hudEnabled: Boolean = false,
    val hudMetrics: Set<HudMetric> = setOf(
        HudMetric.PING,
        HudMetric.TEMPERATURE,
        HudMetric.BATTERY,
        HudMetric.REFRESH
    ),
    val hudCorner: OverlayCorner = OverlayCorner.TOP_LEFT,
    /** 0.25f..1f */
    val hudOpacity: Float = 0.8f,
    /** Text size in sp. */
    val hudTextSizeSp: Float = 11f,
    val hudGamingOnly: Boolean = true,

    val crosshairEnabled: Boolean = false,
    val crosshairStyle: CrosshairStyle = CrosshairStyle.PRECISION,
    /** Radius in dp. */
    val crosshairSizeDp: Float = 14f,
    val crosshairThicknessDp: Float = 2f,
    val crosshairOpacity: Float = 0.9f,
    val crosshairColor: Int = 0xFF4ADE80.toInt(),
    /** Vertical nudge in dp from the exact centre, positive is down. */
    val crosshairOffsetDp: Float = 0f,
    /** Horizontal nudge in dp from the exact centre, positive is right. */
    val crosshairOffsetXDp: Float = 0f
)

class OverlayConfigStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ffboostx.overlay", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<OverlayConfig> = _state.asStateFlow()
    val current: OverlayConfig get() = _state.value

    private fun load(): OverlayConfig {
        val metrics = prefs.getStringSet(KEY_METRICS, null)
            ?.mapNotNull { name -> HudMetric.entries.firstOrNull { it.name == name } }
            ?.toSet()

        return OverlayConfig(
            hudEnabled = prefs.getBoolean(KEY_HUD, false),
            hudMetrics = metrics ?: OverlayConfig().hudMetrics,
            hudCorner = prefs.getString(KEY_CORNER, null)
                ?.let { n -> OverlayCorner.entries.firstOrNull { it.name == n } }
                ?: OverlayCorner.TOP_LEFT,
            hudOpacity = prefs.getFloat(KEY_HUD_OPACITY, 0.8f),
            hudTextSizeSp = prefs.getFloat(KEY_HUD_TEXT, 11f),
            hudGamingOnly = prefs.getBoolean(KEY_HUD_GAMING_ONLY, true),
            crosshairEnabled = prefs.getBoolean(KEY_CROSSHAIR, false),
            crosshairStyle = prefs.getString(KEY_CROSSHAIR_STYLE, null)
                ?.let { n -> CrosshairStyle.entries.firstOrNull { it.name == n } }
                ?: CrosshairStyle.PRECISION,
            crosshairSizeDp = prefs.getFloat(KEY_CROSSHAIR_SIZE, 14f),
            crosshairThicknessDp = prefs.getFloat(KEY_CROSSHAIR_THICKNESS, 2f),
            crosshairOpacity = prefs.getFloat(KEY_CROSSHAIR_OPACITY, 0.9f),
            crosshairColor = prefs.getInt(KEY_CROSSHAIR_COLOR, 0xFF4ADE80.toInt()),
            crosshairOffsetDp = prefs.getFloat(KEY_CROSSHAIR_OFFSET, 0f),
            crosshairOffsetXDp = prefs.getFloat(KEY_CROSSHAIR_OFFSET_X, 0f)
        )
    }

    fun update(transform: (OverlayConfig) -> OverlayConfig) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        prefs.edit().apply {
            putBoolean(KEY_HUD, next.hudEnabled)
            putStringSet(KEY_METRICS, next.hudMetrics.map { it.name }.toSet())
            putString(KEY_CORNER, next.hudCorner.name)
            putFloat(KEY_HUD_OPACITY, next.hudOpacity)
            putFloat(KEY_HUD_TEXT, next.hudTextSizeSp)
            putBoolean(KEY_HUD_GAMING_ONLY, next.hudGamingOnly)
            putBoolean(KEY_CROSSHAIR, next.crosshairEnabled)
            putString(KEY_CROSSHAIR_STYLE, next.crosshairStyle.name)
            putFloat(KEY_CROSSHAIR_SIZE, next.crosshairSizeDp)
            putFloat(KEY_CROSSHAIR_THICKNESS, next.crosshairThicknessDp)
            putFloat(KEY_CROSSHAIR_OPACITY, next.crosshairOpacity)
            putInt(KEY_CROSSHAIR_COLOR, next.crosshairColor)
            putFloat(KEY_CROSSHAIR_OFFSET, next.crosshairOffsetDp)
            putFloat(KEY_CROSSHAIR_OFFSET_X, next.crosshairOffsetXDp)
        }.apply()
    }

    companion object {
        const val KEY_HUD = "hud_enabled"
        const val KEY_METRICS = "hud_metrics"
        const val KEY_CORNER = "hud_corner"
        const val KEY_HUD_OPACITY = "hud_opacity"
        const val KEY_HUD_TEXT = "hud_text_size"
        const val KEY_HUD_GAMING_ONLY = "hud_gaming_only"
        const val KEY_CROSSHAIR = "crosshair_enabled"
        const val KEY_CROSSHAIR_STYLE = "crosshair_style"
        const val KEY_CROSSHAIR_SIZE = "crosshair_size"
        const val KEY_CROSSHAIR_THICKNESS = "crosshair_thickness"
        const val KEY_CROSSHAIR_OPACITY = "crosshair_opacity"
        const val KEY_CROSSHAIR_COLOR = "crosshair_color"
        const val KEY_CROSSHAIR_OFFSET = "crosshair_offset"
        const val KEY_CROSSHAIR_OFFSET_X = "crosshair_offset_x"

        @Volatile
        private var instance: OverlayConfigStore? = null

        fun get(context: Context): OverlayConfigStore =
            instance ?: synchronized(this) {
                instance ?: OverlayConfigStore(context).also { instance = it }
            }
    }

}
