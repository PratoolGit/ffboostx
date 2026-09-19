package com.ffboostx.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MacroPoint(val x: Float, val y: Float, val tMs: Long)

data class MacroStep(
    val type: String,
    val points: List<MacroPoint>,
    val durationMs: Long
)

data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageName: String? = null,
    val steps: List<MacroStep>,
    val speed: Float = 1f,
    val repeatCount: Int = 1,
    val overlayButton: Boolean = true
) {
    val actionCount: Int get() = steps.size

    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name)
        put("packageName", packageName ?: JSONObject.NULL)
        put("speed", speed); put("repeatCount", repeatCount); put("overlayButton", overlayButton)
        put("steps", JSONArray().also { arr ->
            steps.forEach { step ->
                arr.put(JSONObject().apply {
                    put("type", step.type); put("durationMs", step.durationMs)
                    put("points", JSONArray().also { pts ->
                        step.points.forEach { p -> pts.put(JSONObject().apply { put("x", p.x); put("y", p.y); put("tMs", p.tMs) }) }
                    })
                })
            }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): Macro {
            val stepsJson = o.optJSONArray("steps") ?: JSONArray()
            val steps = buildList {
                for (i in 0 until stepsJson.length()) {
                    val s = stepsJson.getJSONObject(i)
                    val pts = s.optJSONArray("points") ?: JSONArray()
                    add(MacroStep(s.optString("type", "tap"), buildList {
                        for (j in 0 until pts.length()) {
                            val p = pts.getJSONObject(j)
                            add(MacroPoint(p.optDouble("x").toFloat(), p.optDouble("y").toFloat(), p.optLong("tMs")))
                        }
                    }, s.optLong("durationMs", 0L)))
                }
            }
            return Macro(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Macro"),
                packageName = if (o.isNull("packageName")) null else o.optString("packageName"),
                steps = steps,
                speed = o.optDouble("speed", 1.0).toFloat().coerceIn(.25f, 4f),
                repeatCount = o.optInt("repeatCount", 1).coerceIn(1, 100),
                overlayButton = o.optBoolean("overlayButton", true)
            )
        }
    }
}

class MacroStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ffboostx.macros", Context.MODE_PRIVATE)
    private val key = "library"

    @Synchronized fun all(): List<Macro> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until a.length()).map { Macro.fromJson(a.getJSONObject(it)) }
    }.getOrElse { emptyList() }

    @Synchronized fun save(macro: Macro) {
        val next = all().filterNot { it.id == macro.id } + macro
        persist(next.takeLast(200))
    }

    @Synchronized fun delete(id: String) = persist(all().filterNot { it.id == id })

    @Synchronized fun setOverlayButton(id: String, enabled: Boolean) {
        save(all().firstOrNull { it.id == id }?.copy(overlayButton = enabled) ?: return)
    }

    private fun persist(items: List<Macro>) {
        val a = JSONArray(); items.forEach { a.put(it.toJson()) }
        prefs.edit().putString(key, a.toString()).apply()
    }

    companion object {
        @Volatile private var instance: MacroStore? = null
        fun get(context: Context): MacroStore = instance ?: synchronized(this) {
            instance ?: MacroStore(context).also { instance = it }
        }
    }
}
