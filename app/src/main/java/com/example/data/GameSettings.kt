package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.engine.ChickenSkin
import com.example.engine.GameMap
import com.example.engine.WeaponSkin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GraphicsQuality(
    val label: String,
    val renderDistance: Float,
    val particleDensity: Float,
    val shadowsEnabled: Boolean,
    val skyDetailsEnabled: Boolean,
    val targetFps: Int,
    val lowEndMode: Boolean
) {
    LOW(
        label = "Низкая",
        renderDistance = 115f,
        particleDensity = 0.35f,
        shadowsEnabled = false,
        skyDetailsEnabled = false,
        targetFps = 30,
        lowEndMode = true
    ),
    MEDIUM(
        label = "Средняя",
        renderDistance = 175f,
        particleDensity = 0.70f,
        shadowsEnabled = true,
        skyDetailsEnabled = true,
        targetFps = 90,
        lowEndMode = false
    ),
    HIGH(
        label = "Высокая",
        renderDistance = 235f,
        particleDensity = 1.00f,
        shadowsEnabled = true,
        skyDetailsEnabled = true,
        targetFps = 120,
        lowEndMode = false
    ),
    ULTRA(
        label = "Ультра",
        renderDistance = 310f,
        particleDensity = 1.25f,
        shadowsEnabled = true,
        skyDetailsEnabled = true,
        targetFps = 120,
        lowEndMode = false
    );

    companion object {
        fun fromNameSafe(raw: String?): GraphicsQuality =
            entries.firstOrNull { it.name == raw } ?: HIGH
    }
}

/**
 * Full mobile-shooter settings persisted locally via SharedPreferences.
 * Covers Graphics (including Low-End / LVP mode & 30/45/60/90/120 FPS),
 * Camera/FOV, Controls/Custom HUD Buttons/Gyroscope, Personalization, and Multi-Channel 3D Audio.
 */
data class GameSettings(
    // --- Graphics & FPS ---
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val renderDistance: Float = GraphicsQuality.HIGH.renderDistance,
    val particleDensity: Float = GraphicsQuality.HIGH.particleDensity,
    val shadowsEnabled: Boolean = true,
    val skyDetailsEnabled: Boolean = true,
    val lowEndMode: Boolean = false,
    val targetFps: Int = 120,
    val showFps: Boolean = true,
    val showMinimap: Boolean = true,
    val showDamageNumbers: Boolean = true,

    // --- Camera / View ---
    val fovDegrees: Float = 72f,
    val invertY: Boolean = false,
    val cameraShakeEnabled: Boolean = true,
    val aimSensitivityMultiplier: Float = 0.55f,
    val defaultThirdPerson: Boolean = false,

    // --- Controls / Custom Buttons / Gyroscope ---
    val lookSensitivity: Float = 1.0f,
    val buttonScale: Float = 1.0f,
    val buttonOpacity: Float = 0.88f,
    val leftHandedLayout: Boolean = false,
    val autoSprint: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val gyroEnabled: Boolean = false,
    val gyroAimOnly: Boolean = false,
    val gyroSensitivity: Float = 1.0f,
    val gyroInvertX: Boolean = false,
    val gyroInvertY: Boolean = false,
    val customHudLayout: CustomHudLayout = CustomHudLayout(),

    // --- Personalization (Skin, Weapon Skin & Map) ---
    val selectedSkin: ChickenSkin = ChickenSkin.CLASSIC,
    val weaponSkin: WeaponSkin = WeaponSkin.CLASSIC,
    val selectedMap: GameMap = GameMap.MAIN_ARENA,

    // --- Audio ---
    val muted: Boolean = false,
    val masterVolume: Float = 0.88f,
    val sfxVolume: Float = 0.92f,
    val engineVolume: Float = 0.78f,
    val musicVolume: Float = 0.72f,
    val footstepsAndUiEnabled: Boolean = true
) {
    fun withPreset(preset: GraphicsQuality): GameSettings = copy(
        graphicsQuality = preset,
        renderDistance = preset.renderDistance,
        particleDensity = preset.particleDensity,
        shadowsEnabled = preset.shadowsEnabled,
        skyDetailsEnabled = preset.skyDetailsEnabled,
        lowEndMode = preset.lowEndMode,
        targetFps = preset.targetFps
    )

    companion object {
        val SUPPORTED_FPS_OPTIONS = listOf(30, 45, 60, 90, 120)

        fun normalizeFps(raw: Int): Int = when {
            raw <= 37 -> 30
            raw <= 52 -> 45
            raw <= 75 -> 60
            raw <= 105 -> 90
            else -> 120
        }
    }
}

class GameSettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    fun update(transform: (GameSettings) -> GameSettings) {
        val next = sanitize(transform(_settings.value))
        _settings.value = next
        save(next)
    }

    fun resetToDefaults() {
        val defaults = GameSettings()
        _settings.value = defaults
        save(defaults)
    }

    private fun sanitize(s: GameSettings): GameSettings = s.copy(
        renderDistance = s.renderDistance.coerceIn(90f, 320f),
        particleDensity = s.particleDensity.coerceIn(0.35f, 1.30f),
        targetFps = GameSettings.normalizeFps(s.targetFps),
        fovDegrees = s.fovDegrees.coerceIn(55f, 105f),
        aimSensitivityMultiplier = s.aimSensitivityMultiplier.coerceIn(0.25f, 1.25f),
        lookSensitivity = s.lookSensitivity.coerceIn(0.30f, 2.50f),
        buttonScale = s.buttonScale.coerceIn(0.80f, 1.35f),
        buttonOpacity = s.buttonOpacity.coerceIn(0.45f, 1.00f),
        gyroSensitivity = s.gyroSensitivity.coerceIn(0.25f, 4.00f),
        muted = false,
        masterVolume = s.masterVolume.coerceIn(0f, 1f),
        sfxVolume = s.sfxVolume.coerceIn(0f, 1f),
        engineVolume = s.engineVolume.coerceIn(0f, 1f),
        musicVolume = s.musicVolume.coerceIn(0f, 1f)
    )

    private fun load(): GameSettings {
        val defaults = GameSettings()
        return sanitize(
            GameSettings(
                graphicsQuality = GraphicsQuality.fromNameSafe(prefs.getString(KEY_QUALITY, defaults.graphicsQuality.name)),
                renderDistance = prefs.getFloat(KEY_RENDER_DIST, defaults.renderDistance),
                particleDensity = prefs.getFloat(KEY_PARTICLES, defaults.particleDensity),
                shadowsEnabled = prefs.getBoolean(KEY_SHADOWS, defaults.shadowsEnabled),
                skyDetailsEnabled = prefs.getBoolean(KEY_SKY_DETAILS, defaults.skyDetailsEnabled),
                lowEndMode = prefs.getBoolean(KEY_LOW_END_MODE, defaults.lowEndMode),
                targetFps = prefs.getInt(KEY_TARGET_FPS, defaults.targetFps),
                showFps = prefs.getBoolean(KEY_SHOW_FPS, defaults.showFps),
                showMinimap = prefs.getBoolean(KEY_SHOW_MINIMAP, defaults.showMinimap),
                showDamageNumbers = prefs.getBoolean(KEY_SHOW_DAMAGE_NUMBERS, defaults.showDamageNumbers),
                fovDegrees = prefs.getFloat(KEY_FOV, defaults.fovDegrees),
                invertY = prefs.getBoolean(KEY_INVERT_Y, defaults.invertY),
                cameraShakeEnabled = prefs.getBoolean(KEY_CAMERA_SHAKE, defaults.cameraShakeEnabled),
                aimSensitivityMultiplier = prefs.getFloat(KEY_AIM_SENS, defaults.aimSensitivityMultiplier),
                defaultThirdPerson = prefs.getBoolean(KEY_DEFAULT_3P, defaults.defaultThirdPerson),
                lookSensitivity = prefs.getFloat(KEY_LOOK_SENS, defaults.lookSensitivity),
                buttonScale = prefs.getFloat(KEY_BTN_SCALE, defaults.buttonScale),
                buttonOpacity = prefs.getFloat(KEY_BTN_OPACITY, defaults.buttonOpacity),
                leftHandedLayout = prefs.getBoolean(KEY_LEFT_HANDED, defaults.leftHandedLayout),
                autoSprint = prefs.getBoolean(KEY_AUTO_SPRINT, defaults.autoSprint),
                hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, defaults.hapticsEnabled),
                gyroEnabled = prefs.getBoolean(KEY_GYRO_ENABLED, defaults.gyroEnabled),
                gyroAimOnly = prefs.getBoolean(KEY_GYRO_AIM_ONLY, defaults.gyroAimOnly),
                gyroSensitivity = prefs.getFloat(KEY_GYRO_SENS, defaults.gyroSensitivity),
                gyroInvertX = prefs.getBoolean(KEY_GYRO_INVERT_X, defaults.gyroInvertX),
                gyroInvertY = prefs.getBoolean(KEY_GYRO_INVERT_Y, defaults.gyroInvertY),
                customHudLayout = CustomHudLayout.deserialize(prefs.getString(KEY_CUSTOM_HUD_LAYOUT, "")),
                selectedSkin = ChickenSkin.fromNameSafe(prefs.getString(KEY_SKIN, defaults.selectedSkin.name)),
                weaponSkin = WeaponSkin.fromNameSafe(prefs.getString(KEY_WEAPON_SKIN, defaults.weaponSkin.name)),
                selectedMap = GameMap.fromNameSafe(prefs.getString(KEY_MAP, defaults.selectedMap.name)),
                muted = prefs.getBoolean(KEY_MUTED, defaults.muted),
                masterVolume = prefs.getFloat(KEY_MASTER_VOL, defaults.masterVolume),
                sfxVolume = prefs.getFloat(KEY_SFX_VOL, defaults.sfxVolume),
                engineVolume = prefs.getFloat(KEY_ENGINE_VOL, defaults.engineVolume),
                musicVolume = prefs.getFloat(KEY_MUSIC_VOL, defaults.musicVolume),
                footstepsAndUiEnabled = prefs.getBoolean(KEY_FOOTSTEPS_UI, defaults.footstepsAndUiEnabled)
            )
        )
    }

    private fun save(s: GameSettings) {
        prefs.edit()
            .putString(KEY_QUALITY, s.graphicsQuality.name)
            .putFloat(KEY_RENDER_DIST, s.renderDistance)
            .putFloat(KEY_PARTICLES, s.particleDensity)
            .putBoolean(KEY_SHADOWS, s.shadowsEnabled)
            .putBoolean(KEY_SKY_DETAILS, s.skyDetailsEnabled)
            .putBoolean(KEY_LOW_END_MODE, s.lowEndMode)
            .putInt(KEY_TARGET_FPS, s.targetFps)
            .putBoolean(KEY_SHOW_FPS, s.showFps)
            .putBoolean(KEY_SHOW_MINIMAP, s.showMinimap)
            .putBoolean(KEY_SHOW_DAMAGE_NUMBERS, s.showDamageNumbers)
            .putFloat(KEY_FOV, s.fovDegrees)
            .putBoolean(KEY_INVERT_Y, s.invertY)
            .putBoolean(KEY_CAMERA_SHAKE, s.cameraShakeEnabled)
            .putFloat(KEY_AIM_SENS, s.aimSensitivityMultiplier)
            .putBoolean(KEY_DEFAULT_3P, s.defaultThirdPerson)
            .putFloat(KEY_LOOK_SENS, s.lookSensitivity)
            .putFloat(KEY_BTN_SCALE, s.buttonScale)
            .putFloat(KEY_BTN_OPACITY, s.buttonOpacity)
            .putBoolean(KEY_LEFT_HANDED, s.leftHandedLayout)
            .putBoolean(KEY_AUTO_SPRINT, s.autoSprint)
            .putBoolean(KEY_HAPTICS, s.hapticsEnabled)
            .putBoolean(KEY_GYRO_ENABLED, s.gyroEnabled)
            .putBoolean(KEY_GYRO_AIM_ONLY, s.gyroAimOnly)
            .putFloat(KEY_GYRO_SENS, s.gyroSensitivity)
            .putBoolean(KEY_GYRO_INVERT_X, s.gyroInvertX)
            .putBoolean(KEY_GYRO_INVERT_Y, s.gyroInvertY)
            .putString(KEY_CUSTOM_HUD_LAYOUT, s.customHudLayout.serialize())
            .putString(KEY_SKIN, s.selectedSkin.name)
            .putString(KEY_WEAPON_SKIN, s.weaponSkin.name)
            .putString(KEY_MAP, s.selectedMap.name)
            .putBoolean(KEY_MUTED, s.muted)
            .putFloat(KEY_MASTER_VOL, s.masterVolume)
            .putFloat(KEY_SFX_VOL, s.sfxVolume)
            .putFloat(KEY_ENGINE_VOL, s.engineVolume)
            .putFloat(KEY_MUSIC_VOL, s.musicVolume)
            .putBoolean(KEY_FOOTSTEPS_UI, s.footstepsAndUiEnabled)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "chicken_gun_3d_settings_v1"
        const val KEY_QUALITY = "graphics_quality"
        const val KEY_RENDER_DIST = "render_distance"
        const val KEY_PARTICLES = "particle_density"
        const val KEY_SHADOWS = "shadows_enabled"
        const val KEY_SKY_DETAILS = "sky_details_enabled"
        const val KEY_LOW_END_MODE = "low_end_mode"
        const val KEY_TARGET_FPS = "target_fps"
        const val KEY_SHOW_FPS = "show_fps"
        const val KEY_SHOW_MINIMAP = "show_minimap"
        const val KEY_SHOW_DAMAGE_NUMBERS = "show_damage_numbers"
        const val KEY_FOV = "fov_degrees"
        const val KEY_INVERT_Y = "invert_y"
        const val KEY_CAMERA_SHAKE = "camera_shake_enabled"
        const val KEY_AIM_SENS = "aim_sensitivity_multiplier"
        const val KEY_DEFAULT_3P = "default_third_person"
        const val KEY_LOOK_SENS = "look_sensitivity"
        const val KEY_BTN_SCALE = "button_scale"
        const val KEY_BTN_OPACITY = "button_opacity"
        const val KEY_LEFT_HANDED = "left_handed_layout"
        const val KEY_AUTO_SPRINT = "auto_sprint"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_GYRO_ENABLED = "gyro_enabled"
        const val KEY_GYRO_AIM_ONLY = "gyro_aim_only"
        const val KEY_GYRO_SENS = "gyro_sensitivity"
        const val KEY_GYRO_INVERT_X = "gyro_invert_x"
        const val KEY_GYRO_INVERT_Y = "gyro_invert_y"
        const val KEY_CUSTOM_HUD_LAYOUT = "custom_hud_layout"
        const val KEY_SKIN = "selected_skin"
        const val KEY_WEAPON_SKIN = "weapon_skin"
        const val KEY_MAP = "selected_map"
        const val KEY_MUTED = "muted"
        const val KEY_MASTER_VOL = "master_volume"
        const val KEY_SFX_VOL = "sfx_volume"
        const val KEY_ENGINE_VOL = "engine_volume"
        const val KEY_MUSIC_VOL = "music_volume"
        const val KEY_FOOTSTEPS_UI = "footsteps_and_ui_enabled"
    }
}
