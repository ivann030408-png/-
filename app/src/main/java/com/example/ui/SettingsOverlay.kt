package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GameSettings
import com.example.data.GraphicsQuality
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.BunkerSlate
import com.example.ui.theme.CombCrimson
import com.example.ui.theme.ExplosiveOrange
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.SteelSurface
import com.example.ui.theme.TacticalNavy
import kotlin.math.roundToInt

private enum class SettingsTab(
    val titleRes: Int,
    val fallbackTitle: String,
    val icon: ImageVector,
    val accent: Color,
    val tag: String
) {
    GRAPHICS(R.string.settings_tab_graphics, "Графика", Icons.Filled.Tune, BeakAmber, "settings_tab_graphics"),
    CONTROLS(R.string.settings_tab_controls, "Управление и звук", Icons.Filled.SportsEsports, NitroEmerald, "settings_tab_controls"),
    CAMERA(R.string.settings_tab_camera, "Общие", Icons.Filled.Visibility, ArenaSkyCyan, "settings_tab_camera"),
    AUDIO(R.string.settings_tab_audio, "Звук", Icons.AutoMirrored.Filled.VolumeUp, ExplosiveOrange, "settings_tab_audio")
}

/**
 * Landscape-optimized Mobile Shooter Settings Overlay:
 * - Графика (quality presets, target FPS 60/90/120, render scale 70/85/100%, render distance, shadows, particles, texture smoothing)
 * - Управление и звук (custom HUD layout, sensitivity, Gyroscope + Neutral Calibration button, audio sliders)
 * - Общие и камера (FPS counter, Auto-Aim toggle, FOV, default 1P/3P camera, damage numbers, minimap)
 */
@Composable
fun SettingsOverlay(
    settings: GameSettings,
    onUpdate: ((GameSettings) -> GameSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    var activeTab by remember { mutableStateOf(SettingsTab.GRAPHICS) }
    var hudEditorOpen by remember { mutableStateOf(false) }

    if (hudEditorOpen) {
        HudLayoutEditorOverlay(
            settings = settings,
            onSaveLayout = { layout ->
                onUpdate { it.copy(customHudLayout = layout) }
            },
            onDismiss = { hudEditorOpen = false }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalNavy.copy(alpha = 0.84f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = BunkerSlate,
            border = BorderStroke(1.6.dp, BeakAmber.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .testTag("settings_overlay_root")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalNavy.copy(alpha = 0.72f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = BeakAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = FeatherWhite
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReset,
                            border = BorderStroke(1.dp, MutedSteel.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("settings_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = null,
                                tint = BeakAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.settings_reset),
                                style = MaterialTheme.typography.labelMedium,
                                color = FeatherWhite
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NitroEmerald,
                                contentColor = TacticalNavy
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("settings_done_button")
                        ) {
                            Text(
                                text = stringResource(R.string.settings_done),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SteelSurface)
                                .testTag("settings_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close_settings),
                                tint = FeatherWhite
                            )
                        }
                    }
                }

                // Body: Left category rail + Right scrollable settings panel
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .width(164.dp)
                            .fillMaxHeight()
                            .background(TacticalNavy.copy(alpha = 0.45f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) tab.accent.copy(alpha = 0.22f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = if (selected) 1.2.dp else 1.dp,
                                        color = if (selected) tab.accent else SteelSurface,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { activeTab = tab }
                                    .padding(horizontal = 12.dp)
                                    .testTag(tab.tag),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (selected) tab.accent else MutedSteel,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tab.fallbackTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (selected) FeatherWhite else MutedSteel
                                )
                            }
                        }
                    }

                    // Active tab content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (activeTab) {
                            SettingsTab.GRAPHICS -> GraphicsSettingsSection(settings, onUpdate)
                            SettingsTab.CONTROLS -> ControlsSettingsSection(
                                settings = settings,
                                onUpdate = onUpdate,
                                onOpenHudEditor = { hudEditorOpen = true }
                            )
                            SettingsTab.CAMERA -> CameraSettingsSection(settings, onUpdate)
                            SettingsTab.AUDIO -> AudioSettingsSection(settings, onUpdate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphicsSettingsSection(
    settings: GameSettings,
    onUpdate: ((GameSettings) -> GameSettings) -> Unit
) {
    SettingsGroupLabel("Качество графики (пресеты)")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GraphicsQuality.entries.forEach { preset ->
            val selected = settings.graphicsQuality == preset
            ChoiceChipButton(
                label = preset.label,
                selected = selected,
                accent = BeakAmber,
                testTag = "settings_quality_${preset.name.lowercase()}",
                onClick = { onUpdate { it.withPreset(preset) } },
                modifier = Modifier.weight(1f)
            )
        }
    }

    SettingsGroupLabel("Лимит кадров (60 / 90 / 120 FPS)")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        GameSettings.SUPPORTED_FPS_OPTIONS.forEach { fpsOpt ->
            val selected = settings.targetFps == fpsOpt
            val accent = when {
                fpsOpt <= 30 -> ArenaSkyCyan
                fpsOpt <= 60 -> NitroEmerald
                else -> BeakAmber
            }
            ChoiceChipButton(
                label = "$fpsOpt FPS",
                selected = selected,
                accent = accent,
                testTag = "settings_fps_$fpsOpt",
                onClick = { onUpdate { it.copy(targetFps = fpsOpt) } },
                modifier = Modifier.weight(1f)
            )
        }
    }

    SettingsGroupLabel("Разрешение 3D-рендера (Масштаб буфера OpenGL)")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GameSettings.RENDER_SCALE_OPTIONS.forEach { scaleOpt ->
            val pct = (scaleOpt * 100f).roundToInt()
            val selected = kotlin.math.abs(settings.renderScale - scaleOpt) < 0.04f
            ChoiceChipButton(
                label = "$pct%",
                selected = selected,
                accent = NitroEmerald,
                testTag = "settings_render_scale_$pct",
                onClick = { onUpdate { it.copy(renderScale = scaleOpt) } },
                modifier = Modifier.weight(1f)
            )
        }
    }

    SettingsGroupLabel("Дальность прорисовки (Близкая / Средняя / Дальняя)")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val distPresets = listOf(
            Triple("Близкая", 125f, "settings_render_dist_near"),
            Triple("Средняя", 195f, "settings_render_dist_medium"),
            Triple("Дальняя", 280f, "settings_render_dist_far")
        )
        distPresets.forEach { (label, meters, tag) ->
            val selected = kotlin.math.abs(settings.renderDistance - meters) < 25f
            ChoiceChipButton(
                label = "$label (${meters.toInt()}м)",
                selected = selected,
                accent = ArenaSkyCyan,
                testTag = tag,
                onClick = { onUpdate { it.copy(renderDistance = meters) } },
                modifier = Modifier.weight(1f)
            )
        }
    }

    SettingsSliderRow(
        title = "Точная дальность прорисовки",
        subtitle = "Туман и радиус отсечения 3D-объектов",
        valueText = "${settings.renderDistance.roundToInt()} м",
        value = settings.renderDistance,
        valueRange = 90f..320f,
        accent = BeakAmber,
        testTag = "settings_render_distance_slider",
        onValueChange = { v -> onUpdate { it.copy(renderDistance = v) } }
    )

    SettingsGroupLabel("Качество частиц (Низкие / Высокие)")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val lowParticles = settings.particleDensity <= 0.65f
        ChoiceChipButton(
            label = "Низкие (Макс. FPS)",
            selected = lowParticles,
            accent = NitroEmerald,
            testTag = "settings_particles_low",
            onClick = { onUpdate { it.copy(particleDensity = 0.50f) } },
            modifier = Modifier.weight(1f)
        )
        ChoiceChipButton(
            label = "Высокие (Эффекты)",
            selected = !lowParticles,
            accent = BeakAmber,
            testTag = "settings_particles_high",
            onClick = { onUpdate { it.copy(particleDensity = 1.00f) } },
            modifier = Modifier.weight(1f)
        )
    }

    SettingsSliderRow(
        title = "Плотность частиц и взрывов",
        subtitle = "Меньше частиц = выше FPS на слабых телефонах",
        valueText = "${(settings.particleDensity * 100f).roundToInt()}%",
        value = settings.particleDensity,
        valueRange = 0.35f..1.30f,
        accent = BeakAmber,
        testTag = "settings_particles_slider",
        onValueChange = { v -> onUpdate { it.copy(particleDensity = v) } }
    )

    SettingsToggleRow(
        title = "Сглаживание текстур .GLB (GL_LINEAR)",
        subtitle = "Плавная фильтрация 3D-текстур (выкл = чёткий пиксельный рендеринг)",
        checked = settings.textureSmoothing,
        accent = ArenaSkyCyan,
        testTag = "settings_texture_smoothing_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(textureSmoothing = c) } }
    )

    SettingsToggleRow(
        title = "Качество теней (Простые контактные тени)",
        subtitle = "Тени под курицами, машинами и объектами (Выкл = прирост FPS)",
        checked = settings.shadowsEnabled,
        accent = BeakAmber,
        testTag = "settings_shadows_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(shadowsEnabled = c) } }
    )

    SettingsToggleRow(
        title = "Режим слабых устройств (ЛВП)",
        subtitle = "Упрощает дальние 3D-детали и шейдеры для стабильного FPS",
        checked = settings.lowEndMode,
        accent = NitroEmerald,
        testTag = "settings_low_end_toggle",
        onCheckedChange = { c ->
            onUpdate {
                if (c) it.copy(lowEndMode = true, shadowsEnabled = false, skyDetailsEnabled = false)
                else it.copy(lowEndMode = false)
            }
        }
    )

    SettingsToggleRow(
        title = "Облака и дальние горы",
        subtitle = "Атмосферные детали горизонта",
        checked = settings.skyDetailsEnabled,
        accent = BeakAmber,
        testTag = "settings_sky_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(skyDetailsEnabled = c) } }
    )
}

@Composable
private fun CameraSettingsSection(
    settings: GameSettings,
    onUpdate: ((GameSettings) -> GameSettings) -> Unit
) {
    SettingsGroupLabel("Общие игровые параметры")

    SettingsToggleRow(
        title = "Счётчик FPS на экране",
        subtitle = "Показывать текущую частоту кадров в левом верхнем углу HUD",
        checked = settings.showFps,
        accent = NitroEmerald,
        testTag = "settings_show_fps_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(showFps = c) } }
    )

    SettingsToggleRow(
        title = "Помощь в прицеливании (Автоприцел)",
        subtitle = "Плавное магнитное наведение прицела на ближайшую цель",
        checked = settings.autoAimEnabled,
        accent = BeakAmber,
        testTag = "settings_auto_aim_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(autoAimEnabled = c) } }
    )

    SettingsToggleRow(
        title = "Всплывающие цифры урона",
        subtitle = "Показывать урон и криты над целями при попадании",
        checked = settings.showDamageNumbers,
        accent = ExplosiveOrange,
        testTag = "settings_damage_numbers_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(showDamageNumbers = c) } }
    )

    SettingsToggleRow(
        title = "Миникарта (радар)",
        subtitle = "Отображать радар врагов, игроков и машин",
        checked = settings.showMinimap,
        accent = ArenaSkyCyan,
        testTag = "settings_show_minimap_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(showMinimap = c) } }
    )

    SettingsGroupLabel("Камера и угол обзора")

    SettingsSliderRow(
        title = "Угол обзора (FOV)",
        subtitle = "Шире обзор — лучше видно боковые атаки",
        valueText = "${settings.fovDegrees.roundToInt()}°",
        value = settings.fovDegrees,
        valueRange = 55f..105f,
        accent = ArenaSkyCyan,
        testTag = "settings_fov_slider",
        onValueChange = { v -> onUpdate { it.copy(fovDegrees = v) } }
    )

    SettingsSliderRow(
        title = "Чувствительность в прицеле (ADS)",
        subtitle = "Замедление камеры при включённом прицеливании",
        valueText = "${(settings.aimSensitivityMultiplier * 100f).roundToInt()}%",
        value = settings.aimSensitivityMultiplier,
        valueRange = 0.25f..1.20f,
        accent = ArenaSkyCyan,
        testTag = "settings_aim_sens_slider",
        onValueChange = { v -> onUpdate { it.copy(aimSensitivityMultiplier = v) } }
    )

    SettingsGroupLabel("Камера при старте матча")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChoiceChipButton(
            label = "1-е лицо (Из глаз)",
            selected = !settings.defaultThirdPerson,
            accent = ArenaSkyCyan,
            testTag = "settings_default_cam_1p",
            onClick = { onUpdate { it.copy(defaultThirdPerson = false) } },
            modifier = Modifier.weight(1f)
        )
        ChoiceChipButton(
            label = "3-е лицо (Со спины)",
            selected = settings.defaultThirdPerson,
            accent = BeakAmber,
            testTag = "settings_default_cam_3p",
            onClick = { onUpdate { it.copy(defaultThirdPerson = true) } },
            modifier = Modifier.weight(1f)
        )
    }

    SettingsToggleRow(
        title = "Инверсия оси Y",
        subtitle = "Перевернуть вертикальный поворот камеры",
        checked = settings.invertY,
        accent = ArenaSkyCyan,
        testTag = "settings_invert_y_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(invertY = c) } }
    )

    SettingsToggleRow(
        title = "Тряска камеры",
        subtitle = "Отдача от взрывов, столкновений и РПГ",
        checked = settings.cameraShakeEnabled,
        accent = ArenaSkyCyan,
        testTag = "settings_camera_shake_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(cameraShakeEnabled = c) } }
    )
}

@Composable
private fun ControlsSettingsSection(
    settings: GameSettings,
    onUpdate: ((GameSettings) -> GameSettings) -> Unit,
    onOpenHudEditor: () -> Unit
) {
    val context = LocalContext.current
    val hasGyroHardware = remember(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null ||
            sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED) != null ||
            sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null ||
            sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }
    var gyroCalibratedFeedback by remember { mutableStateOf(false) }

    // Prominent Custom HUD Button Layout Editor Card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BeakAmber.copy(alpha = 0.16f),
        border = BorderStroke(1.5.dp, BeakAmber.copy(alpha = 0.78f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenWith,
                    contentDescription = null,
                    tint = BeakAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Кастомная настройка кнопок",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = FeatherWhite
                    )
                    Text(
                        text = if (settings.customHudLayout.customized) "Активна своя раскладка — перетащите кнопки и размер"
                        else "Перемещайте джойстик, огонь, прыжок и прицел по экрану",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.customHudLayout.customized) NitroEmerald else MutedSteel
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onOpenHudEditor,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BeakAmber,
                    contentColor = TacticalNavy
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("settings_open_hud_editor_button")
            ) {
                Text("НАСТРОИТЬ", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
            }
        }
    }

    SettingsSliderRow(
        title = "Чувствительность сенсора (обзор)",
        subtitle = "Скорость поворота камеры пальцем",
        valueText = "${"%.2f".format(settings.lookSensitivity)}x",
        value = settings.lookSensitivity,
        valueRange = 0.30f..2.50f,
        accent = NitroEmerald,
        testTag = "settings_look_sens_slider",
        onValueChange = { v -> onUpdate { it.copy(lookSensitivity = v) } }
    )

    SettingsGroupLabel("Гироскоп и калибровка нейтрального положения")

    SettingsToggleRow(
        title = "Гироскоп для прицеливания",
        subtitle = if (hasGyroHardware) "Плавное прицеливание наклоном телефона без рывков"
        else "Датчик гироскопа не обнаружен на этом устройстве (настройка сохранится)",
        checked = settings.gyroEnabled,
        accent = ArenaSkyCyan,
        testTag = "settings_gyro_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(gyroEnabled = c) } }
    )

    // Gyroscope Neutral Calibration Card & Button
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ArenaSkyCyan.copy(alpha = 0.14f),
        border = BorderStroke(1.2.dp, ArenaSkyCyan.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Калибровка гироскопа (Центр 0, 0)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FeatherWhite
                )
                Text(
                    text = if (gyroCalibratedFeedback) {
                        "Откалибровано! Текущий наклон телефона зафиксирован как нейтральный центр (0, 0)"
                    } else {
                        "Держите телефон в удобном положении и нажмите, чтобы сбросить дрейф и задать центр"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (gyroCalibratedFeedback) NitroEmerald else MutedSteel
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = {
                    gyroCalibratedFeedback = true
                    onUpdate { it.copy(gyroCalibrationNonce = it.gyroCalibrationNonce + 1) }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArenaSkyCyan,
                    contentColor = TacticalNavy
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("settings_gyro_calibrate_button")
            ) {
                Text(
                    text = if (gyroCalibratedFeedback) "ОТКАЛИБРОВАНО" else "КАЛИБРОВАТЬ",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }

    SettingsToggleRow(
        title = "Гироскоп только в прицеле (ADS)",
        subtitle = "Включает доводку наклоном только при нажатом прицеле",
        checked = settings.gyroAimOnly,
        accent = ArenaSkyCyan,
        testTag = "settings_gyro_aim_only_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(gyroAimOnly = c) } }
    )

    SettingsSliderRow(
        title = "Чувствительность гироскопа",
        subtitle = "Скорость доводки прицела наклоном устройства",
        valueText = "${"%.2f".format(settings.gyroSensitivity)}x",
        value = settings.gyroSensitivity,
        valueRange = 0.25f..4.00f,
        accent = ArenaSkyCyan,
        testTag = "settings_gyro_sens_slider",
        onValueChange = { v -> onUpdate { it.copy(gyroSensitivity = v) } }
    )

    SettingsToggleRow(
        title = "Инверсия гироскопа по горизонтали (X)",
        subtitle = "Перевернуть поворот влево/вправо от гироскопа",
        checked = settings.gyroInvertX,
        accent = ArenaSkyCyan,
        testTag = "settings_gyro_invert_x_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(gyroInvertX = c) } }
    )

    SettingsToggleRow(
        title = "Инверсия гироскопа по вертикали (Y)",
        subtitle = "Перевернуть наклон вверх/вниз от гироскопа",
        checked = settings.gyroInvertY,
        accent = ArenaSkyCyan,
        testTag = "settings_gyro_invert_y_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(gyroInvertY = c) } }
    )

    SettingsGroupLabel("Кнопки и интерфейс")

    SettingsSliderRow(
        title = "Размер кнопок и джойстика",
        subtitle = "Подстройте кнопки под размер экрана и пальцев",
        valueText = "${(settings.buttonScale * 100f).roundToInt()}%",
        value = settings.buttonScale,
        valueRange = 0.80f..1.35f,
        accent = NitroEmerald,
        testTag = "settings_button_scale_slider",
        onValueChange = { v -> onUpdate { it.copy(buttonScale = v) } }
    )

    SettingsSliderRow(
        title = "Прозрачность кнопок HUD",
        subtitle = "Сделайте кнопки полупрозрачными для лучшего обзора",
        valueText = "${(settings.buttonOpacity * 100f).roundToInt()}%",
        value = settings.buttonOpacity,
        valueRange = 0.45f..1.00f,
        accent = NitroEmerald,
        testTag = "settings_button_opacity_slider",
        onValueChange = { v -> onUpdate { it.copy(buttonOpacity = v) } }
    )

    SettingsToggleRow(
        title = "Режим левши (зеркальные кнопки)",
        subtitle = "Меняет местами джойстик и кнопки стрельбы / педали",
        checked = settings.leftHandedLayout,
        accent = NitroEmerald,
        testTag = "settings_left_handed_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(leftHandedLayout = c) } }
    )

    SettingsToggleRow(
        title = "Авто-бег при полном наклоне джойстика",
        subtitle = "Ускорение при движении вперёд",
        checked = settings.autoSprint,
        accent = NitroEmerald,
        testTag = "settings_auto_sprint_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(autoSprint = c) } }
    )

    SettingsToggleRow(
        title = "Виброотклик и отдача",
        subtitle = "Вибрация при отдаче оружия, столкновениях машин, взрывах и нажатиях",
        checked = settings.hapticsEnabled,
        accent = NitroEmerald,
        testTag = "settings_haptics_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(hapticsEnabled = c) } }
    )

    SettingsGroupLabel("Громкость звука и музыки")
    AudioSettingsSection(settings = settings, onUpdate = onUpdate)
}

@Composable
private fun AudioSettingsSection(
    settings: GameSettings,
    onUpdate: ((GameSettings) -> GameSettings) -> Unit
) {
    SettingsSliderRow(
        title = "Общая громкость",
        subtitle = "Главный уровень громкости всей игры",
        valueText = "${(settings.masterVolume * 100f).roundToInt()}%",
        value = settings.masterVolume,
        valueRange = 0f..1f,
        accent = ExplosiveOrange,
        testTag = "settings_master_vol_slider",
        onValueChange = { v -> onUpdate { it.copy(masterVolume = v) } }
    )

    SettingsSliderRow(
        title = "Музыка в лобби",
        subtitle = "Энергичный саундтрек в меню (плавно затихает на карте)",
        valueText = "${(settings.musicVolume * 100f).roundToInt()}%",
        value = settings.musicVolume,
        valueRange = 0f..1f,
        accent = BeakAmber,
        testTag = "settings_music_vol_slider",
        onValueChange = { v -> onUpdate { it.copy(musicVolume = v) } }
    )

    SettingsSliderRow(
        title = "Выстрелы, взрывы и столкновения",
        subtitle = "3D-звук оружия, РПГ, пушки танка, ударов и попаданий",
        valueText = "${(settings.sfxVolume * 100f).roundToInt()}%",
        value = settings.sfxVolume,
        valueRange = 0f..1f,
        accent = ExplosiveOrange,
        testTag = "settings_sfx_vol_slider",
        onValueChange = { v -> onUpdate { it.copy(sfxVolume = v) } }
    )

    SettingsSliderRow(
        title = "Двигатели, шины и дрифт машин",
        subtitle = "3D-моторы по оборотам RPM, визг шин, гусеницы и эффект Доплера",
        valueText = "${(settings.engineVolume * 100f).roundToInt()}%",
        value = settings.engineVolume,
        valueRange = 0f..1f,
        accent = ExplosiveOrange,
        testTag = "settings_engine_vol_slider",
        onValueChange = { v -> onUpdate { it.copy(engineVolume = v) } }
    )

    SettingsToggleRow(
        title = "Шаги, приземления и смена оружия",
        subtitle = "Тактические звуки передвижения и интерфейса",
        checked = settings.footstepsAndUiEnabled,
        accent = ExplosiveOrange,
        testTag = "settings_footsteps_toggle",
        onCheckedChange = { c -> onUpdate { it.copy(footstepsAndUiEnabled = c) } }
    )
}

@Composable
private fun SettingsGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = BeakAmber
    )
}

@Composable
private fun ChoiceChipButton(
    label: String,
    selected: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) accent else SteelSurface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.2.dp, if (selected) accent else MutedSteel.copy(alpha = 0.35f)),
        modifier = modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) TacticalNavy else FeatherWhite,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    subtitle: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: Color,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = TacticalNavy.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, SteelSurface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = FeatherWhite
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSteel
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = accent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = SteelSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = TacticalNavy.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, SteelSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FeatherWhite
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSteel
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TacticalNavy,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = FeatherWhite,
                    uncheckedTrackColor = SteelSurface
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
