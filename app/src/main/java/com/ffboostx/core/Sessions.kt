package com.ffboostx.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A finished gaming session.
 *
 * Every numeric field is nullable on purpose. A null is rendered as "not
 * measured" rather than as a zero, so a report never implies a measurement that
 * was not taken.
 */
data class SessionRecord(
    val id: Long,
    val gameName: String,
    val startedAtEpochMs: Long,
    val durationMinutes: Int,
    val avgPingMs: Double?,
    val maxPingMs: Double?,
    val lossPercent: Double?,
    val jitterMs: Double?,
    val tempStartC: Float?,
    val tempEndC: Float?,
    val batteryStartPercent: Int?,
    val batteryEndPercent: Int?,
    val freeMemoryStartBytes: Long?,
    val freeMemoryEndBytes: Long?,
    val peakThermal: ThermalLevel?,
    val appliedCount: Int,
    val limitedCount: Int,
    val unavailableCount: Int,
    val failedCount: Int
) {
    /** FPS is never stored: another app's frame rate is not measurable. */
    val fpsNote: String get() = "Not measurable for another app"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("gameName", gameName)
        put("startedAt", startedAtEpochMs)
        put("duration", durationMinutes)
        putOrNull("avgPing", avgPingMs)
        putOrNull("maxPing", maxPingMs)
        putOrNull("loss", lossPercent)
        putOrNull("jitter", jitterMs)
        putOrNull("tempStart", tempStartC?.toDouble())
        putOrNull("tempEnd", tempEndC?.toDouble())
        putOrNull("batteryStart", batteryStartPercent?.toDouble())
        putOrNull("batteryEnd", batteryEndPercent?.toDouble())
        putOrNull("memStart", freeMemoryStartBytes?.toDouble())
        putOrNull("memEnd", freeMemoryEndBytes?.toDouble())
        put("peakThermal", peakThermal?.name ?: JSONObject.NULL)
        put("applied", appliedCount)
        put("limited", limitedCount)
        put("unavailable", unavailableCount)
        put("failed", failedCount)
    }

    companion object {
        fun fromJson(json: JSONObject) = SessionRecord(
            id = json.optLong("id"),
            gameName = json.optString("gameName", "Session"),
            startedAtEpochMs = json.optLong("startedAt"),
            durationMinutes = json.optInt("duration"),
            avgPingMs = json.optDoubleOrNull("avgPing"),
            maxPingMs = json.optDoubleOrNull("maxPing"),
            lossPercent = json.optDoubleOrNull("loss"),
            jitterMs = json.optDoubleOrNull("jitter"),
            tempStartC = json.optDoubleOrNull("tempStart")?.toFloat(),
            tempEndC = json.optDoubleOrNull("tempEnd")?.toFloat(),
            batteryStartPercent = json.optDoubleOrNull("batteryStart")?.toInt(),
            batteryEndPercent = json.optDoubleOrNull("batteryEnd")?.toInt(),
            freeMemoryStartBytes = json.optDoubleOrNull("memStart")?.toLong(),
            freeMemoryEndBytes = json.optDoubleOrNull("memEnd")?.toLong(),
            peakThermal = ThermalLevel.entries
                .firstOrNull { it.name == json.optString("peakThermal") },
            appliedCount = json.optInt("applied"),
            limitedCount = json.optInt("limited"),
            unavailableCount = json.optInt("unavailable"),
            failedCount = json.optInt("failed")
        )
    }
}

private fun JSONObject.putOrNull(key: String, value: Double?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

/** Live state while a session is in progress. */
data class LiveSession(
    val gameName: String,
    val startedAtEpochMs: Long,
    val startedAtElapsedMs: Long,
    val tempStartC: Float?,
    val batteryStartPercent: Int?,
    val freeMemoryStartBytes: Long?,
    val appliedCount: Int,
    val limitedCount: Int,
    val unavailableCount: Int,
    val failedCount: Int,
    val pingSamples: List<Double> = emptyList(),
    val lossSamples: List<Double> = emptyList(),
    val jitterSamples: List<Double> = emptyList(),
    val peakThermal: ThermalLevel = ThermalLevel.NONE
)

/**
 * Owns the current session and the saved history. History is capped so the
 * preferences file cannot grow without bound.
 */
class SessionStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ffboostx.sessions", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<SessionRecord>> = _history.asStateFlow()

    private val _live = MutableStateFlow<LiveSession?>(null)
    val live: StateFlow<LiveSession?> = _live.asStateFlow()

    fun start(session: LiveSession) {
        _live.value = session
    }

    fun recordNetworkSample(quality: NetworkQuality) {
        val current = _live.value ?: return
        _live.value = current.copy(
            pingSamples = current.pingSamples + listOfNotNull(quality.avgMs),
            lossSamples = current.lossSamples + quality.lossPercent,
            jitterSamples = current.jitterSamples + listOfNotNull(quality.jitterMs)
        )
    }

    fun recordThermal(level: ThermalLevel) {
        val current = _live.value ?: return
        if (level > current.peakThermal) {
            _live.value = current.copy(peakThermal = level)
        }
    }

    /**
     * Closes the session and writes it to history. Returns the record so the UI
     * can show the report immediately.
     */
    fun finish(
        elapsedNowMs: Long,
        tempEndC: Float?,
        batteryEndPercent: Int?,
        freeMemoryEndBytes: Long?
    ): SessionRecord? {
        val current = _live.value ?: return null
        _live.value = null

        val record = SessionRecord(
            id = current.startedAtEpochMs,
            gameName = current.gameName,
            startedAtEpochMs = current.startedAtEpochMs,
            durationMinutes = (
                (elapsedNowMs - current.startedAtElapsedMs) / 60_000L
                ).toInt().coerceAtLeast(0),
            avgPingMs = current.pingSamples.takeIf { it.isNotEmpty() }?.average(),
            maxPingMs = current.pingSamples.maxOrNull(),
            lossPercent = current.lossSamples.takeIf { it.isNotEmpty() }?.average(),
            jitterMs = current.jitterSamples.takeIf { it.isNotEmpty() }?.average(),
            tempStartC = current.tempStartC,
            tempEndC = tempEndC,
            batteryStartPercent = current.batteryStartPercent,
            batteryEndPercent = batteryEndPercent,
            freeMemoryStartBytes = current.freeMemoryStartBytes,
            freeMemoryEndBytes = freeMemoryEndBytes,
            peakThermal = current.peakThermal,
            appliedCount = current.appliedCount,
            limitedCount = current.limitedCount,
            unavailableCount = current.unavailableCount,
            failedCount = current.failedCount
        )

        _history.value = (listOf(record) + _history.value).take(MAX_HISTORY)
        persist()
        return record
    }

    fun clearHistory() {
        _history.value = emptyList()
        persist()
    }

    private fun load(): List<SessionRecord> = runCatching {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { SessionRecord.fromJson(array.getJSONObject(it)) }
    }.getOrElse {
        Logx.w("Could not read session history", it)
        emptyList()
    }

    private fun persist() {
        val array = JSONArray()
        _history.value.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY = 30

        @Volatile
        private var instance: SessionStore? = null

        fun get(context: Context): SessionStore =
            instance ?: synchronized(this) {
                instance ?: SessionStore(context).also { instance = it }
            }
    }
}
