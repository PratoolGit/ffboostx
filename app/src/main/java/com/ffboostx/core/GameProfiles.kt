package com.ffboostx.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything FF BoostX will apply for one game, and put back afterwards.
 *
 * A field set to null or -1 means "leave the system alone", which is always the
 * safe default: the app only touches a setting the user explicitly configured.
 */
enum class NotificationMode(val label: String) {
    BLOCK("Block all"),
    BULLET("Bullet / low interruption"),
    ALLOW("Allow")
}

data class GameProfile(
    val id: String,
    val name: String,
    val packageName: String,
    /** 10..255, or -1 to leave brightness alone. */
    val brightness: Int = -1,
    /** Preferred display mode in Hz for this app's window, or null for automatic. */
    val refreshTargetHz: Int? = null,
    /** The FPS the player is aiming for. Informational - see [fpsNote]. */
    val fpsTargetHz: Int? = null,
    val keepScreenAwake: Boolean = true,
    val gamingFocus: Boolean = true,
    val hudEnabled: Boolean = false,
    val crosshairEnabled: Boolean = false,
    val blockBackgroundNetwork: Boolean = false,
    val thermalProfile: ThermalProfile = ThermalProfile.BALANCED,
    val runNetworkCheck: Boolean = true,
    val proGamerMode: Boolean = true,
    val notificationMode: NotificationMode = NotificationMode.BLOCK,
    val callBlocking: Boolean = true,
    val mistouchProtection: Boolean = false,
    val brightnessLock: Boolean = false,
    val touchSensitivity: Int = 50,
    val controlLayout: String = "Default",
    val macroProfile: String = "None"
) {
    val fpsNote: String
        get() = "Actual game FPS depends on game and device support. FF BoostX cannot set a " +
            "frame rate inside Free Fire; this target is what the display is asked for and " +
            "what the thermal advice is tuned against."

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("packageName", packageName)
        put("brightness", brightness)
        put("refreshTargetHz", refreshTargetHz ?: JSONObject.NULL)
        put("fpsTargetHz", fpsTargetHz ?: JSONObject.NULL)
        put("keepScreenAwake", keepScreenAwake)
        put("gamingFocus", gamingFocus)
        put("hudEnabled", hudEnabled)
        put("crosshairEnabled", crosshairEnabled)
        put("blockBackgroundNetwork", blockBackgroundNetwork)
        put("thermalProfile", thermalProfile.name)
        put("runNetworkCheck", runNetworkCheck)
        put("proGamerMode", proGamerMode)
        put("notificationMode", notificationMode.name)
        put("callBlocking", callBlocking)
        put("mistouchProtection", mistouchProtection)
        put("brightnessLock", brightnessLock)
        put("touchSensitivity", touchSensitivity)
        put("controlLayout", controlLayout)
        put("macroProfile", macroProfile)
    }

    companion object {
        fun fromJson(json: JSONObject): GameProfile = GameProfile(
            id = json.optString("id", System.currentTimeMillis().toString()),
            name = json.optString("name", "Profile"),
            packageName = json.optString("packageName", ""),
            brightness = json.optInt("brightness", -1),
            refreshTargetHz = json.optIntOrNull("refreshTargetHz"),
            fpsTargetHz = json.optIntOrNull("fpsTargetHz"),
            keepScreenAwake = json.optBoolean("keepScreenAwake", true),
            gamingFocus = json.optBoolean("gamingFocus", true),
            hudEnabled = json.optBoolean("hudEnabled", false),
            crosshairEnabled = json.optBoolean("crosshairEnabled", false),
            blockBackgroundNetwork = json.optBoolean("blockBackgroundNetwork", false),
            thermalProfile = ThermalProfile.entries
                .firstOrNull { it.name == json.optString("thermalProfile") }
                ?: ThermalProfile.BALANCED,
            runNetworkCheck = json.optBoolean("runNetworkCheck", true),
            proGamerMode = json.optBoolean("proGamerMode", true),
            notificationMode = NotificationMode.entries.firstOrNull { it.name == json.optString("notificationMode") } ?: NotificationMode.BLOCK,
            callBlocking = json.optBoolean("callBlocking", true),
            mistouchProtection = json.optBoolean("mistouchProtection", false),
            brightnessLock = json.optBoolean("brightnessLock", false),
            touchSensitivity = json.optInt("touchSensitivity", 50).coerceIn(0, 100),
            controlLayout = json.optString("controlLayout", "Default"),
            macroProfile = json.optString("macroProfile", "None")
        )

        fun defaultFor(game: GameApp): GameProfile = GameProfile(
            id = game.packageName,
            name = game.label,
            packageName = game.packageName
        )
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key).takeIf { it > 0 }

class GameProfileStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ffboostx.profiles", Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<GameProfile>> = _profiles.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    val active: GameProfile?
        get() = _profiles.value.firstOrNull { it.id == _activeId.value }

    private fun load(): List<GameProfile> = runCatching {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { GameProfile.fromJson(array.getJSONObject(it)) }
    }.getOrElse {
        Logx.w("Could not read saved profiles", it)
        emptyList()
    }

    fun save(profile: GameProfile) {
        val next = _profiles.value.filterNot { it.id == profile.id } + profile
        _profiles.value = next.sortedBy { it.name }
        persist()
        if (_activeId.value == null) setActive(profile.id)
    }

    fun delete(id: String) {
        _profiles.value = _profiles.value.filterNot { it.id == id }
        if (_activeId.value == id) setActive(_profiles.value.firstOrNull()?.id)
        persist()
    }

    /** Returns the profile to its defaults without losing the name or package. */
    fun reset(id: String) {
        val existing = _profiles.value.firstOrNull { it.id == id } ?: return
        save(
            GameProfile(
                id = existing.id,
                name = existing.name,
                packageName = existing.packageName
            )
        )
    }

    fun setActive(id: String?) {
        _activeId.value = id
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Creates a profile for each installed known game that does not have one. */
    fun seed(games: List<GameApp>) {
        var changed = false
        val existing = _profiles.value.map { it.id }.toSet()
        val additions = games.filterNot { it.packageName in existing }
            .map { GameProfile.defaultFor(it) }
        if (additions.isNotEmpty()) {
            _profiles.value = (_profiles.value + additions).sortedBy { it.name }
            changed = true
        }
        if (changed) {
            persist()
            if (_activeId.value == null) setActive(_profiles.value.firstOrNull()?.id)
        }
    }

    private fun persist() {
        val array = JSONArray()
        _profiles.value.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    companion object {
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active_profile"

        @Volatile
        private var instance: GameProfileStore? = null

        fun get(context: Context): GameProfileStore =
            instance ?: synchronized(this) {
                instance ?: GameProfileStore(context).also { instance = it }
            }
    }
}
