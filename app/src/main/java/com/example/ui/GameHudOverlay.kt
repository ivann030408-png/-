package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.GameSettings
import com.example.data.HudControlId
import com.example.data.RoomMessage
import com.example.engine.BlipKind
import com.example.engine.BuildCategory
import com.example.engine.CameraMode
import com.example.engine.GameEngine
import com.example.engine.HudSnapshot
import com.example.engine.MinimapBlip
import com.example.engine.PropType
import com.example.engine.VehicleType
import com.example.engine.WeaponSkin
import com.example.engine.WeaponType
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.BunkerSlate
import com.example.ui.theme.CombCrimson
import com.example.ui.theme.ExplosiveOrange
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.HudBorderGlow
import com.example.ui.theme.HudDarkGlass
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.TacticalNavy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ergonomic Landscape Mobile Shooter & Peaceful Sandbox HUD layered transparently over OpenGL 3D.
 */
@Composable
fun GameHudOverlay(
    engine: GameEngine,
    hudState: State<HudSnapshot>,
    settings: GameSettings = GameSettings(),
    roomCode: String?,
    onOpenPause: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenHostControls: () -> Unit = {},
    onPetPigBurger: () -> Unit = {},
    onToggleDoor: () -> Unit = {},
    onToggleWeaponSkin: () -> Unit = {},
    quickChatOpen: Boolean = false,
    quickChatUnreadCount: Int = 0,
    quickChatMessages: List<RoomMessage> = emptyList(),
    quickChatLiveItems: List<RoomMessage> = emptyList(),
    currentUserId: String = "local_player",
    isOnlineMatch: Boolean = false,
    quickChatCooldownMs: Long = 0L,
    onToggleQuickChat: () -> Unit = {},
    onSendQuickChat: (String) -> Unit = {},
    onCloseQuickChat: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val triggerHaptic = remember(haptic, settings.hapticsEnabled) {
        {
            if (settings.hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val btnScale = settings.buttonScale.coerceIn(0.80f, 1.35f)
    val btnAlpha = settings.buttonOpacity.coerceIn(0.45f, 1.0f)
    fun scaledDp(base: Float): Dp = (base * btnScale).coerceAtLeast(48f).dp

    // Coarse structural flags via derivedStateOf: outer HUD tree NEVER recomposes on FPS/speed/yaw/minimap ticks!
    val alive by remember(hudState) { derivedStateOf { hudState.value.alive } }
    val inVehicle by remember(hudState) { derivedStateOf { hudState.value.inVehicle } }
    val freeCamActive by remember(hudState) { derivedStateOf { hudState.value.freeCamActive } }
    val buildModeOpen by remember(hudState) { derivedStateOf { hudState.value.buildModeOpen } }
    val isOnlineHost by remember(hudState) { derivedStateOf { hudState.value.isOnlineHost } }
    val canBuildOrSpawn by remember(hudState) {
        derivedStateOf { hudState.value.canBuildProps || hudState.value.canSpawnVehicles }
    }
    val cameraMode by remember(hudState) { derivedStateOf { hudState.value.cameraMode } }
    val aiming by remember(hudState) { derivedStateOf { hudState.value.aiming } }
    val isTankActive by remember(hudState) {
        derivedStateOf { hudState.value.activeVehicleName == VehicleType.BATTLE_TANK.label }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. TOUCH CAMERA LOOK / VEHICLE ORBIT PAD
        // Covers the full open viewport in Vehicle or Free Camera modes for effortless 360° orbit & flight look!
        val fullViewportLook = inVehicle || freeCamActive
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (fullViewportLook) 1f else 0.58f)
                .align(
                    if (fullViewportLook) Alignment.Center
                    else if (settings.leftHandedLayout) Alignment.CenterStart else Alignment.CenterEnd
                )
                .pointerInput(engine) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        engine.input.addLookDelta(dragAmount.x, dragAmount.y)
                    }
                }
        )

        // 2. DAMAGE RED VIGNETTE FLASH (draw-phase graphicsLayer alpha: zero recomposition!)
        DamageVignetteLayer(hudState = hudState)

        // 2B. FLOATING 3D-PROJECTED DAMAGE NUMBERS OVER TARGETS (isolated leaf)
        if (settings.showDamageNumbers) {
            FloatingDamageLayer(hudState = hudState)
        }

        // 3. CENTER DYNAMIC CROSSHAIR + CRIT HITMARKER + RELOAD RING (draw-phase Canvas read: zero recomposition!)
        if (alive && !inVehicle && !freeCamActive) {
            CrosshairReticle(
                hudState = hudState,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 4. SAFE-AREA HUD CONTROLS LAYER (spacious layout keeping 75%+ open 3D viewport)
        val useCustomLayout = settings.customHudLayout.customized && !inVehicle && !freeCamActive
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            val density = LocalDensity.current
            val areaWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
            val areaHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

            fun customSlotOffset(id: HudControlId, wDp: Dp, hDp: Dp): IntOffset {
                val slot = settings.customHudLayout.slotFor(id, settings.leftHandedLayout)
                val wPx = with(density) { wDp.toPx() }
                val hPx = with(density) { hDp.toPx() }
                val left = (slot.xFrac * areaWidthPx - wPx * 0.5f).coerceIn(0f, (areaWidthPx - wPx).coerceAtLeast(0f))
                val top = (slot.yFrac * areaHeightPx - hPx * 0.5f).coerceIn(0f, (areaHeightPx - hPx).coerceAtLeast(0f))
                return IntOffset(left.roundToInt(), top.roundToInt())
            }

            fun customSizeDp(id: HudControlId, baseDp: Float): Dp {
                val slot = settings.customHudLayout.slotFor(id, settings.leftHandedLayout)
                return (baseDp * btnScale * slot.scale).coerceIn(48f, 185f).dp
            }

            fun customAlpha(id: HudControlId): Float {
                val slot = settings.customHudLayout.slotFor(id, settings.leftHandedLayout)
                return (btnAlpha * slot.alpha).coerceIn(0.32f, 1f)
            }

            // --- TOP-LEFT: Clean Compact Pause, Settings, Host Controls, HP Bar & Room Code ---
            Row(
                modifier = Modifier.align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HudMiniIconButton(
                    icon = Icons.Filled.Pause,
                    contentDescription = stringResource(R.string.cd_pause_menu),
                    testTag = "hud_pause_button",
                    onClick = onOpenPause
                )
                HudMiniIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_open_settings),
                    testTag = "hud_settings_button",
                    onClick = onOpenSettings
                )
                if (isOnlineHost) {
                    HudMiniIconButton(
                        icon = Icons.Filled.AdminPanelSettings,
                        contentDescription = "Управление комнатой",
                        testTag = "hud_host_controls_button",
                        tint = ArenaSkyCyan,
                        onClick = onOpenHostControls
                    )
                }
                QuickChatHudButton(
                    isOpen = quickChatOpen,
                    unreadCount = quickChatUnreadCount,
                    onClick = {
                        triggerHaptic()
                        onToggleQuickChat()
                    }
                )

                // Isolated HP + Kills + FPS pill (only recomposes when HP, Kills, or FPS actually changes)
                TopLeftStatsPill(
                    hudState = hudState,
                    showFps = settings.showFps
                )

                SpawnShieldBadge(hudState = hudState)

                if (roomCode != null) {
                    OnlineRoomPlayersBadge(hudState = hudState, roomCode = roomCode)
                }
            }

            // --- TOP-LEFT UNDER BAR: Non-blocking Live Quick Chat Feed ---
            if (!quickChatOpen) {
                QuickChatLiveFeed(
                    messages = quickChatLiveItems,
                    currentUserId = currentUserId,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 54.dp)
                )
            }

            // --- TOP-CENTER: Compact Race Lap Timer Only (isolated leaf composable) ---
            RaceLapTimerPill(
                hudState = hudState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            )

            // --- TOP-RIGHT: Camera Toggle (1P/3P), Free Cam Toggle, Build Toggle & Compact Minimap ---
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (!useCustomLayout) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    triggerHaptic()
                                    engine.toggleFreeCamera()
                                },
                                color = if (freeCamActive) ArenaSkyCyan else HudDarkGlass,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, ArenaSkyCyan),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("hud_free_cam_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.OpenWith,
                                        contentDescription = stringResource(R.string.cd_free_cam),
                                        tint = if (freeCamActive) TacticalNavy else ArenaSkyCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.hud_free_cam),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (freeCamActive) TacticalNavy else FeatherWhite
                                    )
                                }
                            }

                            if (!inVehicle) {
                                Surface(
                                    onClick = { engine.toggleCameraMode() },
                                    color = HudDarkGlass,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ArenaSkyCyan.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("hud_camera_toggle_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Videocam,
                                            contentDescription = stringResource(R.string.cd_camera_toggle),
                                            tint = ArenaSkyCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = when (cameraMode) {
                                                CameraMode.FIRST_PERSON -> "1P"
                                                CameraMode.THIRD_PERSON -> "3P"
                                                CameraMode.FREE_CAM -> "3P"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = FeatherWhite
                                        )
                                    }
                                }
                            }
                        }

                        if (!inVehicle && !freeCamActive && canBuildOrSpawn) {
                            Surface(
                                onClick = {
                                    triggerHaptic()
                                    engine.toggleBuildMenu()
                                },
                                color = if (buildModeOpen) BeakAmber else HudDarkGlass,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BeakAmber),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("hud_build_menu_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (buildModeOpen) Icons.Filled.Close else Icons.Filled.Construction,
                                        contentDescription = stringResource(R.string.cd_build_menu),
                                        tint = if (buildModeOpen) TacticalNavy else BeakAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.hud_build),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (buildModeOpen) TacticalNavy else FeatherWhite
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Always expose Free Camera pill beside minimap even when custom HUD layout is enabled
                    Surface(
                        onClick = {
                            triggerHaptic()
                            engine.toggleFreeCamera()
                        },
                        color = if (freeCamActive) ArenaSkyCyan else HudDarkGlass,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.2.dp, ArenaSkyCyan),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("hud_free_cam_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.OpenWith,
                                contentDescription = stringResource(R.string.cd_free_cam),
                                tint = if (freeCamActive) TacticalNavy else ArenaSkyCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.hud_free_cam),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (freeCamActive) TacticalNavy else FeatherWhite
                            )
                        }
                    }
                }

                if (settings.showMinimap) {
                    MinimapRadar(
                        hudState = hudState,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            // Custom-positioned CAMERA & BUILD buttons when user customized HUD layout
            if (useCustomLayout && alive) {
                val camW = customSizeDp(HudControlId.CAMERA, 80f)
                val camH = customSizeDp(HudControlId.CAMERA, 48f)
                Surface(
                    onClick = { engine.toggleCameraMode() },
                    color = HudDarkGlass,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ArenaSkyCyan.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .offset { customSlotOffset(HudControlId.CAMERA, camW, camH) }
                        .size(width = camW, height = camH)
                        .graphicsLayer { alpha = customAlpha(HudControlId.CAMERA) }
                        .testTag("hud_camera_toggle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Videocam,
                            contentDescription = stringResource(R.string.cd_camera_toggle),
                            tint = ArenaSkyCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (cameraMode == CameraMode.FIRST_PERSON) "1P" else "3P",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = FeatherWhite
                        )
                    }
                }

                if (canBuildOrSpawn) {
                    val bldW = customSizeDp(HudControlId.BUILD, 96f)
                    val bldH = customSizeDp(HudControlId.BUILD, 48f)
                    Surface(
                        onClick = {
                            triggerHaptic()
                            engine.toggleBuildMenu()
                        },
                        color = if (buildModeOpen) BeakAmber else HudDarkGlass,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BeakAmber),
                        modifier = Modifier
                            .offset { customSlotOffset(HudControlId.BUILD, bldW, bldH) }
                            .size(width = bldW, height = bldH)
                            .graphicsLayer { alpha = customAlpha(HudControlId.BUILD) }
                            .testTag("hud_build_menu_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (buildModeOpen) Icons.Filled.Close else Icons.Filled.Construction,
                                contentDescription = stringResource(R.string.cd_build_menu),
                                tint = if (buildModeOpen) TacticalNavy else BeakAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.hud_build),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (buildModeOpen) TacticalNavy else FeatherWhite
                            )
                        }
                    }
                }
            }

            val movementAlignment = if (settings.leftHandedLayout) Alignment.BottomEnd else Alignment.BottomStart
            val combatAlignment = if (settings.leftHandedLayout) Alignment.BottomStart else Alignment.BottomEnd
            val combatHorizontalAlignment = if (settings.leftHandedLayout) Alignment.Start else Alignment.End

            // --- MOVEMENT CLUSTER: Free Cam Flight Joystick, Virtual Joystick, OR Vehicle Steering Arrows ---
            if (alive) {
                if (freeCamActive) {
                    VirtualJoystick(
                        modifier = Modifier
                            .align(movementAlignment)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .size((112f * btnScale).coerceIn(96f, 152f).dp)
                            .graphicsLayer { alpha = btnAlpha }
                            .testTag("freecam_joystick"),
                        onMove = { mx, my ->
                            engine.input.moveX = mx
                            engine.input.moveY = my
                        }
                    )
                } else if (inVehicle) {
                    Row(
                        modifier = Modifier
                            .align(movementAlignment)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .graphicsLayer { alpha = btnAlpha },
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HoldActionCircle(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            label = "ЛЕВО",
                            size = scaledDp(64f),
                            accent = ArenaSkyCyan,
                            testTag = "car_steer_left",
                            onPressedChange = {
                                if (it) triggerHaptic()
                                engine.input.steerLeft = it
                            }
                        )
                        HoldActionCircle(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            label = "ПРАВО",
                            size = scaledDp(64f),
                            accent = ArenaSkyCyan,
                            testTag = "car_steer_right",
                            onPressedChange = {
                                if (it) triggerHaptic()
                                engine.input.steerRight = it
                            }
                        )
                    }
                } else if (useCustomLayout) {
                    val joySize = customSizeDp(HudControlId.JOYSTICK, 112f)
                    VirtualJoystick(
                        modifier = Modifier
                            .offset { customSlotOffset(HudControlId.JOYSTICK, joySize, joySize) }
                            .size(joySize)
                            .graphicsLayer { alpha = customAlpha(HudControlId.JOYSTICK) }
                            .testTag("virtual_joystick"),
                        onMove = { mx, my ->
                            engine.input.moveX = mx
                            engine.input.moveY = my
                        }
                    )
                } else {
                    VirtualJoystick(
                        modifier = Modifier
                            .align(movementAlignment)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .size((112f * btnScale).coerceIn(96f, 152f).dp)
                            .graphicsLayer { alpha = btnAlpha }
                            .testTag("virtual_joystick"),
                        onMove = { mx, my ->
                            engine.input.moveX = mx
                            engine.input.moveY = my
                        }
                    )
                }
            }

            // --- BOTTOM-CENTER: Weapon Bar / Speedometer + Enter/Exit Car Prompt (isolated leaves!) ---
            if (alive && !freeCamActive) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BottomCenterInteractionPrompts(
                        hudState = hudState,
                        engine = engine,
                        triggerHaptic = triggerHaptic,
                        onPetPigBurger = onPetPigBurger,
                        onToggleDoor = onToggleDoor
                    )

                    if (inVehicle) {
                        VehicleSpeedometerHud(hudState = hudState)
                    } else if (buildModeOpen && canBuildOrSpawn) {
                        SandboxBuildDockSection(
                            hudState = hudState,
                            engine = engine,
                            triggerHaptic = triggerHaptic
                        )
                    } else if (!useCustomLayout) {
                        WeaponSelectorBar(
                            hudState = hudState,
                            engine = engine,
                            onToggleWeaponSkin = {
                                triggerHaptic()
                                onToggleWeaponSkin()
                            }
                        )
                    }
                }
            }

            // Custom-positioned Weapon Bar when user customized HUD layout
            if (useCustomLayout && alive && !buildModeOpen) {
                val wbW = customSizeDp(HudControlId.WEAPON_BAR, 286f)
                val wbH = customSizeDp(HudControlId.WEAPON_BAR, 44f)
                Box(
                    modifier = Modifier
                        .offset { customSlotOffset(HudControlId.WEAPON_BAR, wbW, wbH) }
                        .graphicsLayer { alpha = customAlpha(HudControlId.WEAPON_BAR) }
                ) {
                    WeaponSelectorBar(
                        hudState = hudState,
                        engine = engine,
                        onToggleWeaponSkin = {
                            triggerHaptic()
                            onToggleWeaponSkin()
                        }
                    )
                }
            }

            // --- ACTION CLUSTER: Free Cam Altitude/Exit, Vehicle Pedals, OR Combat Action Buttons ---
            if (alive) {
                if (freeCamActive) {
                    Column(
                        modifier = Modifier
                            .align(combatAlignment)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .graphicsLayer { alpha = btnAlpha },
                        horizontalAlignment = combatHorizontalAlignment,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TapActionCircle(
                            icon = Icons.Filled.Close,
                            label = stringResource(R.string.hud_free_cam_exit),
                            size = scaledDp(52f),
                            accent = CombCrimson,
                            testTag = "freecam_exit_button",
                            onClick = {
                                triggerHaptic()
                                engine.toggleFreeCamera()
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HoldActionCircle(
                                icon = Icons.Filled.ArrowDownward,
                                label = stringResource(R.string.hud_fly_down),
                                size = scaledDp(60f),
                                accent = BeakAmber,
                                testTag = "freecam_down_button",
                                onPressedChange = {
                                    if (it) triggerHaptic()
                                    engine.input.flyDown = it
                                }
                            )
                            HoldActionCircle(
                                icon = Icons.Filled.ArrowUpward,
                                label = stringResource(R.string.hud_fly_up),
                                size = scaledDp(64f),
                                accent = ArenaSkyCyan,
                                testTag = "freecam_up_button",
                                onPressedChange = {
                                    if (it) triggerHaptic()
                                    engine.input.flyUp = it
                                }
                            )
                        }
                    }
                } else if (inVehicle) {
                    val isTank = isTankActive
                    Column(
                        modifier = Modifier
                            .align(combatAlignment)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .graphicsLayer { alpha = btnAlpha },
                        horizontalAlignment = combatHorizontalAlignment,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TapActionCircle(
                            icon = if (isTank) Icons.Filled.LocalFireDepartment else Icons.Filled.Bolt,
                            label = if (isTank) stringResource(R.string.hud_tank_cannon) else stringResource(R.string.hud_turbo),
                            size = scaledDp(52f),
                            accent = if (isTank) ExplosiveOrange else BeakAmber,
                            testTag = "car_turbo_button",
                            onClick = {
                                triggerHaptic()
                                engine.input.turboQueued = true
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HoldActionCircle(
                                icon = Icons.Filled.DirectionsCar,
                                label = stringResource(R.string.hud_brake),
                                size = scaledDp(60f),
                                accent = CombCrimson,
                                testTag = "car_brake_pedal",
                                onPressedChange = {
                                    if (it) triggerHaptic()
                                    engine.input.brakePressed = it
                                }
                            )
                            HoldActionCircle(
                                icon = Icons.Filled.ArrowUpward,
                                label = stringResource(R.string.hud_gas),
                                size = scaledDp(70f),
                                accent = NitroEmerald,
                                testTag = "car_gas_pedal",
                                onPressedChange = {
                                    if (it) triggerHaptic()
                                    engine.input.gasPressed = it
                                }
                            )
                        }
                    }
                } else if (useCustomLayout) {
                    val aimSize = customSizeDp(HudControlId.AIM, 50f)
                    Box(
                        modifier = Modifier
                            .offset { customSlotOffset(HudControlId.AIM, aimSize, aimSize) }
                            .graphicsLayer { alpha = customAlpha(HudControlId.AIM) }
                    ) {
                        TapActionCircle(
                            icon = Icons.Filled.CenterFocusStrong,
                            label = stringResource(R.string.hud_aim),
                            size = aimSize,
                            accent = if (aiming) NitroEmerald else ArenaSkyCyan,
                            active = aiming,
                            testTag = "hud_aim_button",
                            onClick = {
                                triggerHaptic()
                                engine.toggleAim()
                            }
                        )
                    }

                    val swSize = customSizeDp(HudControlId.SWITCH_WEAPON, 50f)
                    Box(
                        modifier = Modifier
                            .offset { customSlotOffset(HudControlId.SWITCH_WEAPON, swSize, swSize) }
                            .graphicsLayer { alpha = customAlpha(HudControlId.SWITCH_WEAPON) }
                    ) {
                        TapActionCircle(
                            icon = Icons.Filled.SwapHoriz,
                            label = "ОРУЖ.",
                            size = swSize,
                            accent = BeakAmber,
                            testTag = "hud_switch_weapon_button",
                            onClick = {
                                triggerHaptic()
                                engine.switchWeapon()
                            }
                        )
                    }

                    val jumpSize = customSizeDp(HudControlId.JUMP, 54f)
                    Box(
                        modifier = Modifier
                            .offset { customSlotOffset(HudControlId.JUMP, jumpSize, jumpSize) }
                            .graphicsLayer { alpha = customAlpha(HudControlId.JUMP) }
                    ) {
                        TapActionCircle(
                            icon = Icons.Filled.ArrowUpward,
                            label = stringResource(R.string.hud_jump),
                            size = jumpSize,
                            accent = NitroEmerald,
                            testTag = "hud_jump_button",
                            onClick = {
                                triggerHaptic()
                                engine.input.jumpQueued = true
                            }
                        )
                    }

                    val fireSize = customSizeDp(HudControlId.FIRE, 74f)
                    Box(
                        modifier = Modifier
                            .offset { customSlotOffset(HudControlId.FIRE, fireSize, fireSize) }
                            .graphicsLayer { alpha = customAlpha(HudControlId.FIRE) }
                    ) {
                        HoldActionCircle(
                            icon = Icons.Filled.GpsFixed,
                            label = stringResource(R.string.hud_fire),
                            size = fireSize,
                            accent = ExplosiveOrange,
                            testTag = "hud_fire_button",
                            onPressedChange = {
                                if (it) triggerHaptic()
                                engine.input.firing = it
                            }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .align(combatAlignment)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .graphicsLayer { alpha = btnAlpha },
                        horizontalAlignment = combatHorizontalAlignment,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TapActionCircle(
                                icon = Icons.Filled.CenterFocusStrong,
                                label = stringResource(R.string.hud_aim),
                                size = scaledDp(50f),
                                accent = if (aiming) NitroEmerald else ArenaSkyCyan,
                                active = aiming,
                                testTag = "hud_aim_button",
                                onClick = {
                                    triggerHaptic()
                                    engine.toggleAim()
                                }
                            )
                            TapActionCircle(
                                icon = Icons.Filled.SwapHoriz,
                                label = "ОРУЖ.",
                                size = scaledDp(50f),
                                accent = BeakAmber,
                                testTag = "hud_switch_weapon_button",
                                onClick = {
                                    triggerHaptic()
                                    engine.switchWeapon()
                                }
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            TapActionCircle(
                                icon = Icons.Filled.ArrowUpward,
                                label = stringResource(R.string.hud_jump),
                                size = scaledDp(54f),
                                accent = NitroEmerald,
                                testTag = "hud_jump_button",
                                onClick = {
                                    triggerHaptic()
                                    engine.input.jumpQueued = true
                                }
                            )
                            HoldActionCircle(
                                icon = Icons.Filled.GpsFixed,
                                label = stringResource(R.string.hud_fire),
                                size = scaledDp(74f),
                                accent = ExplosiveOrange,
                                testTag = "hud_fire_button",
                                onPressedChange = {
                                    if (it) triggerHaptic()
                                    engine.input.firing = it
                                }
                            )
                        }
                    }
                }
            }

            // --- TOP-Z FLOATING QUICK CHAT PRESET SELECTOR ---
            QuickChatPanelOverlay(
                visible = quickChatOpen,
                recentMessages = quickChatMessages,
                currentUserId = currentUserId,
                isOnlineMatch = isOnlineMatch,
                cooldownRemainingMs = quickChatCooldownMs,
                onSelectMessage = { phrase ->
                    triggerHaptic()
                    onSendQuickChat(phrase)
                },
                onClose = onCloseQuickChat,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 54.dp)
            )
        }

        // 5. DEFEATED / RESPAWN OVERLAY
        if (!alive) {
            DefeatedRespawnOverlay(hudState = hudState, engine = engine)
        }
    }
}

@Composable
private fun DamageVignetteLayer(hudState: State<HudSnapshot>) {
    val brush = remember {
        Brush.radialGradient(
            colors = listOf(Color.Transparent, CombCrimson)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = (hudState.value.damageVignetteAlpha * 0.55f).coerceIn(0f, 1f)
            }
            .background(brush)
    )
}

@Composable
private fun FloatingDamageLayer(hudState: State<HudSnapshot>) {
    val popups by remember(hudState) {
        derivedStateOf {
            val all = hudState.value.damagePopups
            if (all.size > GameEngine.MAX_DAMAGE_POPUPS) {
                all.takeLast(GameEngine.MAX_DAMAGE_POPUPS)
            } else {
                all
            }
        }
    }
    if (popups.isNotEmpty()) {
        FloatingDamageOverlay(popups = popups)
    }
}

@Composable
private fun TopLeftStatsPill(
    hudState: State<HudSnapshot>,
    showFps: Boolean
) {
    val hp by remember(hudState) { derivedStateOf { hudState.value.hp } }
    val kills by remember(hudState) { derivedStateOf { hudState.value.kills } }
    val peaceful by remember(hudState) { derivedStateOf { hudState.value.peacefulMode } }
    val godMode by remember(hudState) { derivedStateOf { hudState.value.godMode } }
    val fps by remember(hudState) { derivedStateOf { hudState.value.fps } }

    val invincible = peaceful || godMode
    val lowHpAlert = !invincible && hp <= 30
    Surface(
        color = if (lowHpAlert) Color(0xCC3B0D14) else HudDarkGlass,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (lowHpAlert) 1.4.dp else 1.dp, if (lowHpAlert) CombCrimson else HudBorderGlow)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column {
                Text(
                    text = if (invincible) "HP ∞" else "HP $hp",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (invincible || hp > 35) NitroEmerald else CombCrimson
                )
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(if (invincible) 1f else (hp / 100f).coerceIn(0f, 1f))
                            .background(if (invincible || hp > 35) NitroEmerald else CombCrimson)
                    )
                }
            }
            if (!peaceful) {
                Text(
                    text = "⚔ $kills",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BeakAmber
                )
            }
            if (showFps) {
                Text(
                    text = "$fps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSteel
                )
            }
        }
    }
}

@Composable
private fun SpawnShieldBadge(hudState: State<HudSnapshot>) {
    val shieldSecs by remember(hudState) {
        derivedStateOf {
            val h = hudState.value
            val inv = h.peacefulMode || h.godMode
            if (!inv && h.alive && h.spawnProtectionSec > 0.05f) {
                h.spawnProtectionSec.roundToInt().coerceAtLeast(1)
            } else 0
        }
    }
    if (shieldSecs > 0) {
        Surface(
            color = BeakAmber.copy(alpha = 0.22f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BeakAmber),
            modifier = Modifier.testTag("hud_spawn_shield_badge")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = stringResource(R.string.hud_spawn_shield),
                    tint = BeakAmber,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "${shieldSecs}с",
                    style = MaterialTheme.typography.labelSmall,
                    color = BeakAmber
                )
            }
        }
    }
}

@Composable
private fun OnlineRoomPlayersBadge(
    hudState: State<HudSnapshot>,
    roomCode: String
) {
    val count by remember(hudState) { derivedStateOf { hudState.value.onlinePlayersCount } }
    Surface(
        color = ArenaSkyCyan.copy(alpha = 0.20f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ArenaSkyCyan.copy(alpha = 0.8f))
    ) {
        Text(
            text = "$roomCode • $count",
            style = MaterialTheme.typography.labelSmall,
            color = FeatherWhite,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun RaceLapTimerPill(
    hudState: State<HudSnapshot>,
    modifier: Modifier = Modifier
) {
    val visible by remember(hudState) {
        derivedStateOf {
            val h = hudState.value
            h.alive && (h.raceActive || h.raceBestLapSec > 0f)
        }
    }
    if (!visible) return
    val lap by remember(hudState) { derivedStateOf { hudState.value.raceLapCount.coerceAtLeast(1) } }
    val curTenths by remember(hudState) { derivedStateOf { (hudState.value.raceCurrentLapSec * 10f).toInt() } }
    val bestTenths by remember(hudState) { derivedStateOf { (hudState.value.raceBestLapSec * 10f).toInt() } }

    Surface(
        color = HudDarkGlass,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ArenaSkyCyan.copy(alpha = 0.72f)),
        modifier = modifier.testTag("hud_race_timer_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "К$lap • ${curTenths / 10}.${curTenths % 10}с",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = ArenaSkyCyan
            )
            if (bestTenths > 0) {
                Text(
                    text = "РЕК ${bestTenths / 10}.${bestTenths % 10}с",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = BeakAmber
                )
            }
        }
    }
}

@Composable
private fun BottomCenterInteractionPrompts(
    hudState: State<HudSnapshot>,
    engine: GameEngine,
    triggerHaptic: () -> Unit,
    onPetPigBurger: () -> Unit,
    onToggleDoor: () -> Unit
) {
    val inVehicle by remember(hudState) { derivedStateOf { hudState.value.inVehicle } }
    val nearPig by remember(hudState) { derivedStateOf { hudState.value.nearPigBurger } }
    val nearDoor by remember(hudState) { derivedStateOf { hudState.value.nearDoorProp } }
    val doorOpen by remember(hudState) { derivedStateOf { hudState.value.nearDoorIsOpen } }
    val doorLabel by remember(hudState) { derivedStateOf { hudState.value.nearDoorLabel } }
    val nearVehicle by remember(hudState) { derivedStateOf { hudState.value.nearVehicle } }
    val nearVehicleName by remember(hudState) { derivedStateOf { hudState.value.nearVehicleName } }
    val showOrbitReset by remember(hudState) {
        derivedStateOf { hudState.value.inVehicle && abs(hudState.value.vehicleCamOrbitYaw) > 8f }
    }

    if (nearPig && !inVehicle) {
        Button(
            onClick = {
                triggerHaptic()
                onPetPigBurger()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF472B6),
                contentColor = TacticalNavy
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .height(48.dp)
                .testTag("hud_pet_pig_button")
        ) {
            PigBurgerPreviewIcon(modifier = Modifier.size(width = 24.dp, height = 18.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Погладить",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }

    if (nearDoor || nearVehicle || inVehicle || showOrbitReset) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (nearDoor) {
                Button(
                    onClick = {
                        triggerHaptic()
                        onToggleDoor()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (doorOpen) BeakAmber else ArenaSkyCyan,
                        contentColor = TacticalNavy
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("hud_door_toggle_button")
                ) {
                    Icon(
                        Icons.Filled.MeetingRoom,
                        contentDescription = if (doorOpen) "Закрыть $doorLabel" else "Открыть $doorLabel",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (doorOpen) "Закрыть $doorLabel" else "Открыть $doorLabel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (nearVehicle || inVehicle) {
                Button(
                    onClick = { engine.toggleVehicle() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (inVehicle) CombCrimson else NitroEmerald,
                        contentColor = TacticalNavy
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("hud_vehicle_toggle_button")
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = stringResource(R.string.cd_vehicle_action),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (inVehicle) stringResource(R.string.hud_exit_car) else nearVehicleName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (showOrbitReset) {
                Surface(
                    onClick = {
                        triggerHaptic()
                        engine.resetVehicleCameraOrbit()
                    },
                    color = HudDarkGlass,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, ArenaSkyCyan),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("car_cam_reset_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.CenterFocusStrong,
                            contentDescription = stringResource(R.string.cd_cam_center),
                            tint = ArenaSkyCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.hud_cam_center),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = FeatherWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleSpeedometerHud(hudState: State<HudSnapshot>) {
    val speedKmh by remember(hudState) { derivedStateOf { hudState.value.vehicleSpeedKmh } }
    val gear by remember(hudState) { derivedStateOf { hudState.value.vehicleGear } }
    val isTank by remember(hudState) {
        derivedStateOf { hudState.value.activeVehicleName == VehicleType.BATTLE_TANK.label }
    }
    val rpmBucket by remember(hudState) {
        derivedStateOf {
            val frac = ((hudState.value.vehicleRpm - 0.14f) / 1.15f).coerceIn(0f, 1f)
            when {
                frac > 0.82f -> 2
                frac > 0.56f -> 1
                else -> 0
            }
        }
    }
    val nitroReady by remember(hudState) { derivedStateOf { hudState.value.vehicleNitroFrac >= 0.99f } }
    val rpmColor = when (rpmBucket) {
        2 -> CombCrimson
        1 -> BeakAmber
        else -> NitroEmerald
    }

    Surface(
        color = HudDarkGlass,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BeakAmber.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "$speedKmh КМ/Ч",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = BeakAmber
                )
                Surface(
                    color = rpmColor.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(1.dp, rpmColor.copy(alpha = 0.8f))
                ) {
                    Text(
                        text = "G$gear",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = rpmColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "RPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = rpmColor,
                    fontSize = 8.sp
                )
                // Draw-phase Canvas bar: updates smoothly at 60-120Hz with ZERO recomposition!
                Canvas(
                    modifier = Modifier
                        .width(52.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                ) {
                    val frac = ((hudState.value.vehicleRpm - 0.14f) / 1.15f).coerceIn(0f, 1f)
                    val barColor = when {
                        frac > 0.82f -> CombCrimson
                        frac > 0.56f -> BeakAmber
                        else -> NitroEmerald
                    }
                    drawRect(Color.White.copy(alpha = 0.18f))
                    drawRect(barColor, size = Size(size.width * frac, size.height))
                }
                Text(
                    text = if (isTank) "ЗАРЯД" else "НИТРО",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nitroReady) NitroEmerald else MutedSteel,
                    fontSize = 8.sp
                )
                Canvas(
                    modifier = Modifier
                        .width(46.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                ) {
                    val frac = hudState.value.vehicleNitroFrac.coerceIn(0f, 1f)
                    drawRect(Color.White.copy(alpha = 0.20f))
                    drawRect(
                        color = if (isTank) ExplosiveOrange else ArenaSkyCyan,
                        size = Size(size.width * frac, size.height)
                    )
                }
            }
        }
    }
}

@Composable
private fun SandboxBuildDockSection(
    hudState: State<HudSnapshot>,
    engine: GameEngine,
    triggerHaptic: () -> Unit
) {
    val category by remember(hudState) { derivedStateOf { hudState.value.buildCategory } }
    val selectedProp by remember(hudState) { derivedStateOf { hudState.value.selectedBuildProp } }
    val selectedVehicle by remember(hudState) { derivedStateOf { hudState.value.selectedVehicleType } }
    val gridSnap by remember(hudState) { derivedStateOf { hudState.value.buildGridSnap } }
    val ghostValid by remember(hudState) { derivedStateOf { hudState.value.ghostPlacementValid } }
    val placedCount by remember(hudState) { derivedStateOf { hudState.value.userPlacedCount } }
    val pigCount by remember(hudState) { derivedStateOf { hudState.value.pigBurgerCount } }
    val peaceful by remember(hudState) { derivedStateOf { hudState.value.peacefulMode } }
    val isOnline by remember(hudState) { derivedStateOf { hudState.value.isOnlineMode } }
    val canBuild by remember(hudState) { derivedStateOf { hudState.value.canBuildProps } }
    val canSpawn by remember(hudState) { derivedStateOf { hudState.value.canSpawnVehicles } }

    CompactSandboxBuildDock(
        category = category,
        selectedProp = selectedProp,
        selectedVehicle = selectedVehicle,
        gridSnap = gridSnap,
        ghostValid = ghostValid,
        placedCount = placedCount,
        pigBurgerCount = pigCount,
        peacefulMode = peaceful,
        isOnlineMode = isOnline,
        canBuildProps = canBuild,
        canSpawnVehicles = canSpawn,
        onSelectCategory = { engine.selectBuildCategory(it) },
        onTapProp = { type ->
            triggerHaptic()
            if (category == type.buildTab && selectedProp == type) {
                engine.placeCurrentGhost()
            } else {
                engine.selectBuildProp(type)
            }
        },
        onTapVehicle = { vType ->
            triggerHaptic()
            if (category == BuildCategory.VEHICLES && selectedVehicle == vType) {
                engine.placeCurrentGhost()
            } else {
                engine.selectBuildVehicle(vType)
            }
        },
        onPlaceCurrent = {
            triggerHaptic()
            engine.placeCurrentGhost()
        },
        onToggleGrid = {
            triggerHaptic()
            engine.toggleBuildGridSnap()
        },
        onRotate = {
            triggerHaptic()
            engine.rotateBuildGhost()
        },
        onUndo = {
            triggerHaptic()
            engine.undoLastUserProp()
        },
        onClearAll = {
            triggerHaptic()
            engine.clearAllUserProps()
        }
    )
}

@Composable
private fun DefeatedRespawnOverlay(
    hudState: State<HudSnapshot>,
    engine: GameEngine
) {
    val kills by remember(hudState) { derivedStateOf { hudState.value.kills } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalNavy.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = BunkerSlate,
            border = BorderStroke(2.dp, CombCrimson)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "КУРИЦА ПОВЕРЖЕНА!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CombCrimson
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Уничтожено врагов: $kills",
                    style = MaterialTheme.typography.titleMedium,
                    color = FeatherWhite
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { engine.respawnPlayer() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NitroEmerald,
                        contentColor = TacticalNavy
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("hud_respawn_button")
                ) {
                    Text(
                        text = stringResource(R.string.hud_respawn),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun WeaponSelectorBar(
    hudState: State<HudSnapshot>,
    engine: GameEngine,
    onToggleWeaponSkin: () -> Unit = {}
) {
    val weapon by remember(hudState) { derivedStateOf { hudState.value.weapon } }
    val weaponSkin by remember(hudState) { derivedStateOf { hudState.value.weaponSkin } }
    val ammo by remember(hudState) { derivedStateOf { hudState.value.ammo } }
    val maxAmmo by remember(hudState) { derivedStateOf { hudState.value.maxAmmo } }
    val reloading by remember(hudState) { derivedStateOf { hudState.value.reloading } }
    val infinite by remember(hudState) {
        derivedStateOf { hudState.value.peacefulMode || hudState.value.infiniteAmmo }
    }

    Surface(
        color = HudDarkGlass,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, HudBorderGlow)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WeaponType.entries.forEach { w ->
                val selected = w == weapon
                Surface(
                    onClick = { engine.switchWeapon(w) },
                    color = if (selected) BeakAmber else Color.Transparent,
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.testTag("weapon_tab_${w.name.lowercase()}")
                ) {
                    Text(
                        text = w.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (selected) TacticalNavy else FeatherWhite,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            val isTactical = weaponSkin == WeaponSkin.TACTICAL
            Surface(
                onClick = onToggleWeaponSkin,
                color = if (isTactical) NitroEmerald.copy(alpha = 0.25f) else ArenaSkyCyan.copy(alpha = 0.18f),
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, if (isTactical) NitroEmerald else ArenaSkyCyan),
                modifier = Modifier.testTag("hud_weapon_skin_button")
            ) {
                Text(
                    text = if (isTactical) "Такт." else "Класс.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isTactical) NitroEmerald else ArenaSkyCyan,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            Surface(
                onClick = { engine.startReload() },
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.testTag("hud_reload_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.cd_reload),
                        tint = BeakAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = when {
                            infinite -> "∞"
                            reloading -> "..."
                            else -> "$ammo/$maxAmmo"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = FeatherWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSandboxBuildDock(
    category: BuildCategory,
    selectedProp: PropType,
    selectedVehicle: VehicleType,
    gridSnap: Boolean,
    ghostValid: Boolean,
    placedCount: Int,
    pigBurgerCount: Int,
    peacefulMode: Boolean,
    isOnlineMode: Boolean,
    canBuildProps: Boolean,
    canSpawnVehicles: Boolean,
    onSelectCategory: (BuildCategory) -> Unit,
    onTapProp: (PropType) -> Unit,
    onTapVehicle: (VehicleType) -> Unit,
    onPlaceCurrent: () -> Unit,
    onToggleGrid: () -> Unit,
    onRotate: () -> Unit,
    onUndo: () -> Unit,
    onClearAll: () -> Unit
) {
    Surface(
        color = HudDarkGlass,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, BeakAmber.copy(alpha = 0.72f)),
        modifier = Modifier
            .widthIn(max = 470.dp)
            .testTag("compact_build_dock")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Compact top utility row: Category Tabs + Grid Snap + Rotate + Undo + Confirm Place
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                if (canBuildProps) {
                    CategoryTabPill(
                        label = BuildCategory.STUNTS_TRAPS.label,
                        selected = category == BuildCategory.STUNTS_TRAPS,
                        accent = ExplosiveOrange,
                        testTag = "build_tab_stunts",
                        onClick = { onSelectCategory(BuildCategory.STUNTS_TRAPS) }
                    )
                    CategoryTabPill(
                        label = BuildCategory.BLOCKS.label,
                        selected = category == BuildCategory.BLOCKS,
                        accent = BeakAmber,
                        testTag = "build_tab_blocks",
                        onClick = { onSelectCategory(BuildCategory.BLOCKS) }
                    )
                    CategoryTabPill(
                        label = BuildCategory.FOOD_DRINKS.label,
                        selected = category == BuildCategory.FOOD_DRINKS,
                        accent = ArenaSkyCyan,
                        testTag = "build_tab_food",
                        onClick = { onSelectCategory(BuildCategory.FOOD_DRINKS) }
                    )
                }
                if (!isOnlineMode) {
                    CategoryTabPill(
                        label = "${BuildCategory.NPCS.label} ($pigBurgerCount)",
                        selected = category == BuildCategory.NPCS,
                        accent = Color(0xFFF472B6),
                        testTag = "build_tab_npcs",
                        onClick = { onSelectCategory(BuildCategory.NPCS) }
                    )
                }
                if (canSpawnVehicles) {
                    CategoryTabPill(
                        label = "${BuildCategory.VEHICLES.label} (${VehicleType.entries.size})",
                        selected = category == BuildCategory.VEHICLES,
                        accent = NitroEmerald,
                        testTag = "build_tab_vehicles",
                        onClick = { onSelectCategory(BuildCategory.VEHICLES) }
                    )
                }

                Surface(
                    onClick = onToggleGrid,
                    color = if (gridSnap) NitroEmerald.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(7.dp),
                    border = if (gridSnap) BorderStroke(1.dp, NitroEmerald) else null,
                    modifier = Modifier.testTag("build_grid_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.GridOn,
                            contentDescription = "Сетка привязки",
                            tint = if (gridSnap) NitroEmerald else MutedSteel,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Сетка", style = MaterialTheme.typography.labelSmall, color = FeatherWhite, fontSize = 9.sp)
                    }
                }

                Surface(
                    onClick = onRotate,
                    color = ArenaSkyCyan.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.testTag("build_rotate_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.hud_rotate_prop),
                            tint = ArenaSkyCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("45°", style = MaterialTheme.typography.labelSmall, color = FeatherWhite, fontSize = 9.sp)
                    }
                }

                if (placedCount > 0) {
                    Surface(
                        onClick = onUndo,
                        color = CombCrimson.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.testTag("build_undo_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.hud_undo_prop),
                                tint = CombCrimson,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    if (peacefulMode) {
                        Surface(
                            onClick = onClearAll,
                            color = CombCrimson.copy(alpha = 0.30f),
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier.testTag("build_clear_all_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = stringResource(R.string.hud_clear_props),
                                    tint = FeatherWhite,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                // Clear confirmation button: green when ghost spot is free, red when obstructed
                Surface(
                    onClick = onPlaceCurrent,
                    color = if (ghostValid) NitroEmerald else CombCrimson.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("build_place_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (ghostValid) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = "Поставить объект",
                            tint = if (ghostValid) TacticalNavy else FeatherWhite,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (ghostValid) "ПОСТАВИТЬ" else "БЛИЗКО",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (ghostValid) TacticalNavy else FeatherWhite,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // Horizontally scrollable compact item row covering ALL 45 .glb assets across 5 tabs!
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                when (category) {
                    BuildCategory.STUNTS_TRAPS, BuildCategory.BLOCKS, BuildCategory.FOOD_DRINKS -> {
                        val accent = when (category) {
                            BuildCategory.STUNTS_TRAPS -> ExplosiveOrange
                            BuildCategory.FOOD_DRINKS -> ArenaSkyCyan
                            else -> BeakAmber
                        }
                        PropType.propsForTab(category).forEach { type ->
                            val isSel = type == selectedProp
                            Surface(
                                onClick = { onTapProp(type) },
                                color = if (isSel) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(9.dp),
                                border = if (isSel) BorderStroke(1.3.dp, accent) else null,
                                modifier = Modifier.testTag("build_spawn_${type.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    BlockPreviewIcon(type = type, modifier = Modifier.size(width = 30.dp, height = 22.dp))
                                    Text(type.label, style = MaterialTheme.typography.labelSmall, color = FeatherWhite, fontSize = 9.sp)
                                }
                            }
                        }
                    }

                    BuildCategory.NPCS -> {
                        if (!isOnlineMode) {
                            Surface(
                                onClick = onPlaceCurrent,
                                color = Color(0xFFF472B6).copy(alpha = 0.26f),
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.3.dp, Color(0xFFF472B6)),
                                modifier = Modifier.testTag("build_spawn_pig_burger")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PigBurgerPreviewIcon(modifier = Modifier.size(width = 34.dp, height = 24.dp))
                                    Text(
                                        text = "Хряк-Бургер",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = FeatherWhite
                                    )
                                }
                            }
                        }
                    }

                    BuildCategory.VEHICLES -> {
                        VehicleType.entries.forEach { vType ->
                            val isSel = vType == selectedVehicle
                            val accent = Color(vType.defaultR, vType.defaultG, vType.defaultB)
                            Surface(
                                onClick = { onTapVehicle(vType) },
                                color = if (isSel) NitroEmerald.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(9.dp),
                                border = if (isSel) BorderStroke(1.3.dp, NitroEmerald) else BorderStroke(0.6.dp, accent.copy(alpha = 0.45f)),
                                modifier = Modifier.testTag("build_spawn_car_${vType.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    VehiclePreviewIcon(type = vType, modifier = Modifier.size(width = 44.dp, height = 22.dp))
                                    Text(vType.label, style = MaterialTheme.typography.labelSmall, color = FeatherWhite, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTabPill(
    label: String,
    selected: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) accent else Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) TacticalNavy else FeatherWhite,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun VirtualJoystick(
    modifier: Modifier = Modifier,
    onMove: (Float, Float) -> Unit
) {
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }
    val currentOnMove by rememberUpdatedState(onMove)

    DisposableEffect(Unit) {
        onDispose { currentOnMove(0f, 0f) }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                active = true
                val radius = (size.width * 0.38f).coerceAtLeast(24f)
                val centerX = size.width * 0.5f
                val centerY = size.height * 0.5f

                fun updatePos(pos: Offset) {
                    val dx = pos.x - centerX
                    val dy = pos.y - centerY
                    val dist = sqrt(dx * dx + dy * dy)
                    val clampedDist = dist.coerceAtMost(radius)
                    val nx = if (dist > 0.001f) (dx / dist) * (clampedDist / radius) else 0f
                    val ny = if (dist > 0.001f) (dy / dist) * (clampedDist / radius) else 0f
                    knobX = nx
                    knobY = ny
                    currentOnMove(nx, -ny)
                }

                try {
                    updatePos(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) break
                        pointer.consume()
                        updatePos(pointer.position)
                    }
                } finally {
                    active = false
                    knobX = 0f
                    knobY = 0f
                    currentOnMove(0f, 0f)
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerR = size.minDimension * 0.45f
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val sprinting = sqrt(knobX * knobX + knobY * knobY) > 0.85f
            drawCircle(color = Color(0x550F172A), radius = outerR, center = center)
            drawCircle(
                color = if (sprinting) BeakAmber else Color.White.copy(alpha = 0.45f),
                radius = outerR,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
            val knobCenter = Offset(
                center.x + knobX * outerR * 0.78f,
                center.y + knobY * outerR * 0.78f
            )
            drawCircle(
                color = if (active) BeakAmber.copy(alpha = 0.92f) else FeatherWhite.copy(alpha = 0.75f),
                radius = outerR * 0.42f,
                center = knobCenter
            )
        }
    }
}

@Composable
private fun HoldActionCircle(
    icon: ImageVector,
    label: String,
    size: Dp,
    accent: Color,
    testTag: String,
    onPressedChange: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPressedChange by rememberUpdatedState(onPressedChange)

    // Prevent stuck fire/gas/brake/steer states if composable leaves composition while pressed!
    DisposableEffect(Unit) {
        onDispose {
            currentOnPressedChange(false)
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) accent.copy(alpha = 0.88f) else HudDarkGlass)
            .border(2.2.dp, accent, CircleShape)
            .testTag(testTag)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    currentOnPressedChange(true)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val ptr = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ptr.pressed) break
                            ptr.consume()
                        }
                    } finally {
                        pressed = false
                        currentOnPressedChange(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (pressed) TacticalNavy else accent,
                modifier = Modifier.size(size * 0.36f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (pressed) TacticalNavy else FeatherWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun TapActionCircle(
    icon: ImageVector,
    label: String,
    size: Dp,
    accent: Color,
    active: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) accent.copy(alpha = 0.82f) else HudDarkGlass)
            .border(1.8.dp, accent, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) TacticalNavy else accent,
                modifier = Modifier.size(size * 0.38f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) TacticalNavy else FeatherWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun HudMiniIconButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    tint: Color = FeatherWhite,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HudDarkGlass)
            .border(1.dp, HudBorderGlow, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CrosshairReticle(
    hudState: State<HudSnapshot>,
    modifier: Modifier = Modifier
) {
    // Reads HudSnapshot strictly inside Canvas draw lambda: ZERO recomposition during combat/reloading!
    Canvas(modifier = modifier.size(60.dp)) {
        val h = hudState.value
        val aiming = h.aiming
        val hitAlpha = h.hitMarkerAlpha
        val critHit = h.critHitFlash
        val weapon = h.weapon
        val spread = h.weaponSpread
        val reloading = h.reloading
        val reloadProgress = h.reloadProgress

        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val baseGap = if (aiming) 4.5.dp.toPx() else if (weapon == WeaponType.RPG) 10.5.dp.toPx() else 7.5.dp.toPx()
        val gap = baseGap + (spread.coerceIn(0f, 1f) * 10.dp.toPx())
        val arm = if (aiming) 8.dp.toPx() else 7.dp.toPx()
        val stroke = 2.dp.toPx()
        val shadowColor = Color.Black.copy(alpha = 0.55f)
        val color = if (aiming) NitroEmerald else Color.White.copy(alpha = 0.92f)

        // Subtle dark outline behind crosshair arms so reticle stays readable against bright sky
        drawLine(shadowColor, Offset(cx - gap - arm, cy), Offset(cx - gap, cy), stroke + 1.4.dp.toPx(), StrokeCap.Round)
        drawLine(shadowColor, Offset(cx + gap, cy), Offset(cx + gap + arm, cy), stroke + 1.4.dp.toPx(), StrokeCap.Round)
        drawLine(shadowColor, Offset(cx, cy - gap - arm), Offset(cx, cy - gap), stroke + 1.4.dp.toPx(), StrokeCap.Round)
        drawLine(shadowColor, Offset(cx, cy + gap), Offset(cx, cy + gap + arm), stroke + 1.4.dp.toPx(), StrokeCap.Round)

        drawLine(color, Offset(cx - gap - arm, cy), Offset(cx - gap, cy), stroke, StrokeCap.Round)
        drawLine(color, Offset(cx + gap, cy), Offset(cx + gap + arm, cy), stroke, StrokeCap.Round)
        drawLine(color, Offset(cx, cy - gap - arm), Offset(cx, cy - gap), stroke, StrokeCap.Round)
        drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + gap + arm), stroke, StrokeCap.Round)
        drawCircle(color, radius = 1.6.dp.toPx(), center = Offset(cx, cy))

        // Circular Reload Progress Arc around center reticle
        if (reloading) {
            val arcR = 17.dp.toPx()
            val topLeft = Offset(cx - arcR, cy - arcR)
            val arcSize = Size(arcR * 2f, arcR * 2f)
            drawArc(
                color = Color.White.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = BeakAmber,
                startAngle = -90f,
                sweepAngle = 360f * reloadProgress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Hitmarker (blazing gold larger X on critical headshot, crimson X on normal hit)
        if (hitAlpha > 0.05f) {
            val hColor = if (critHit) BeakAmber.copy(alpha = hitAlpha) else CombCrimson.copy(alpha = hitAlpha)
            val d1 = if (critHit) 5.dp.toPx() else 4.dp.toPx()
            val d2 = if (critHit) 15.dp.toPx() else 12.dp.toPx()
            val hStroke = if (critHit) 3.2.dp.toPx() else 2.5.dp.toPx()
            drawLine(hColor, Offset(cx - d2, cy - d2), Offset(cx - d1, cy - d1), hStroke, StrokeCap.Round)
            drawLine(hColor, Offset(cx + d1, cy - d1), Offset(cx + d2, cy - d2), hStroke, StrokeCap.Round)
            drawLine(hColor, Offset(cx - d2, cy + d2), Offset(cx - d1, cy + d1), hStroke, StrokeCap.Round)
            drawLine(hColor, Offset(cx + d1, cy + d1), Offset(cx + d2, cy + d2), hStroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun MinimapRadar(
    hudState: State<HudSnapshot>,
    modifier: Modifier = Modifier
) {
    // Reads blips & playerYawDeg strictly inside Canvas draw lambda: ZERO recomposition on camera turns!
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .background(HudDarkGlass)
            .border(1.5.dp, ArenaSkyCyan.copy(alpha = 0.65f), CircleShape)
    ) {
        val h = hudState.value
        val blips = h.minimapBlips
        val playerYawDeg = h.playerYawDeg

        val c = Offset(size.width * 0.5f, size.height * 0.5f)
        val r = size.minDimension * 0.45f
        drawCircle(Color.White.copy(alpha = 0.14f), radius = r * 0.55f, center = c, style = Stroke(1.dp.toPx()))

        // Forward field-of-view cone at top of radar
        drawArc(
            color = NitroEmerald.copy(alpha = 0.18f),
            startAngle = -120f,
            sweepAngle = 60f,
            useCenter = true,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f)
        )

        // Rotating North compass pip along outer radar rim
        val northRad = (-playerYawDeg * PI.toFloat() / 180f) - (PI.toFloat() * 0.5f)
        val nx = c.x + cos(northRad) * (r * 0.96f)
        val ny = c.y + sin(northRad) * (r * 0.96f)
        drawCircle(BeakAmber, radius = 3.2.dp.toPx(), center = Offset(nx, ny))

        for (i in blips.indices) {
            val b = blips[i]
            val bx = c.x + b.relX * r
            val by = c.y + b.relZ * r
            val col = when (b.kind) {
                BlipKind.BOT -> CombCrimson
                BlipKind.ONLINE_PLAYER -> ArenaSkyCyan
                BlipKind.VEHICLE -> BeakAmber
                BlipKind.BARREL -> ExplosiveOrange
                BlipKind.PIG_BURGER -> Color(0xFFF472B6)
                BlipKind.PICKUP -> NitroEmerald
            }
            val dotR = when (b.kind) {
                BlipKind.BARREL, BlipKind.PICKUP -> 2.2.dp.toPx()
                BlipKind.PIG_BURGER -> 2.8.dp.toPx()
                else -> 3.3.dp.toPx()
            }
            drawCircle(col, radius = dotR, center = Offset(bx, by))
        }
        drawCircle(NitroEmerald, radius = 3.8.dp.toPx(), center = c)
    }
}
