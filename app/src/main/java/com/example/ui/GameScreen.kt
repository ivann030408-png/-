package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.GameRoom
import com.example.data.GameSettings
import com.example.data.MatchMissionTelemetry
import com.example.data.RoomMessage
import com.example.data.RoomPlayer
import com.example.engine.AndroidHapticController
import com.example.engine.GameEngine
import com.example.engine.GameMap
import com.example.engine.GyroAimProcessor
import com.example.engine.HudSnapshot
import com.example.engine.LobbyBotSlot
import com.example.engine.LocalNetworkState
import com.example.engine.SoundManager
import com.example.renderer.GameGlSurfaceView
import com.example.renderer.SceneLoadState
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.BunkerSlate
import com.example.ui.theme.CombCrimson
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.TacticalNavy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

@Composable
fun GameScreen(
    onlineRoom: GameRoom?,
    lobbyViewModel: LobbyViewModel?,
    settings: GameSettings = GameSettings(),
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit = {},
    onResetSettings: () -> Unit = {},
    botRoster: List<LobbyBotSlot> = LobbyBotSlot.defaultRoster(if (onlineRoom != null) 3 else 5),
    peacefulMode: Boolean = false,
    openBuildInitially: Boolean = false,
    onExitToMenu: () -> Unit
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager() }
    val hapticController = remember(context) { AndroidHapticController(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var pauseOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var hostControlsOpen by remember { mutableStateOf(false) }
    var appInForeground by remember { mutableStateOf(true) }
    var quickChatOpen by remember { mutableStateOf(false) }
    var quickChatUnreadCount by remember { mutableIntStateOf(0) }
    var lastQuickChatSentMs by remember { mutableLongStateOf(0L) }
    var quickChatCooldownMs by remember { mutableLongStateOf(0L) }
    var localChatSeq by remember { mutableIntStateOf(0) }
    val localQuickMessages = remember(onlineRoom?.id) { mutableStateListOf<RoomMessage>() }

    val onlineSyncEnabled = onlineRoom != null && lobbyViewModel != null
    val currentUserId = lobbyViewModel?.currentUserId ?: "local_player"
    val liveOnlineRoom: GameRoom? = if (onlineSyncEnabled) {
        val active by lobbyViewModel.activeRoom.collectAsStateWithLifecycle()
        active ?: onlineRoom
    } else {
        onlineRoom
    }
    val liveOnlinePlayers: List<RoomPlayer> = if (onlineSyncEnabled) {
        val list by lobbyViewModel.roomPlayers.collectAsStateWithLifecycle()
        list
    } else {
        emptyList()
    }
    val liveOnlineMessages: List<RoomMessage> = if (onlineSyncEnabled) {
        val msgs by lobbyViewModel.roomMessages.collectAsStateWithLifecycle()
        msgs
    } else {
        emptyList()
    }
    val roomClosedNotice: String? = if (onlineSyncEnabled) {
        val notice by lobbyViewModel.roomClosedNotice.collectAsStateWithLifecycle()
        notice
    } else {
        null
    }

    val activeChatMessages: List<RoomMessage> = if (onlineSyncEnabled) {
        liveOnlineMessages
    } else {
        localQuickMessages
    }
    val seenMessageKeys = remember(onlineRoom?.id) {
        val initial = if (onlineSyncEnabled) lobbyViewModel.roomMessages.value else emptyList()
        initial.mapTo(mutableSetOf()) { it.id.ifBlank { "${it.senderId}_${it.text.hashCode()}" } }
    }
    val liveQuickChatEntries = remember(onlineRoom?.id) { mutableStateListOf<Pair<RoomMessage, Long>>() }

    LaunchedEffect(activeChatMessages.size, activeChatMessages.lastOrNull()?.id) {
        val now = System.currentTimeMillis()
        for (msg in activeChatMessages) {
            val key = msg.id.ifBlank { "${msg.senderId}_${msg.text.hashCode()}" }
            if (seenMessageKeys.add(key)) {
                liveQuickChatEntries.add(msg to (now + 7500L))
                while (liveQuickChatEntries.size > 3) {
                    liveQuickChatEntries.removeAt(0)
                }
                if (!quickChatOpen && msg.senderId != currentUserId) {
                    quickChatUnreadCount = (quickChatUnreadCount + 1).coerceAtMost(99)
                }
            }
        }
    }

    LaunchedEffect(liveQuickChatEntries.size, liveQuickChatEntries.firstOrNull()?.second) {
        while (liveQuickChatEntries.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val nextExpiry = liveQuickChatEntries.minOf { it.second }
            val waitMs = (nextExpiry - now).coerceAtLeast(100L)
            delay(waitMs)
            val cutoff = System.currentTimeMillis()
            liveQuickChatEntries.removeAll { it.second <= cutoff }
        }
    }

    LaunchedEffect(lastQuickChatSentMs) {
        if (lastQuickChatSentMs == 0L) return@LaunchedEffect
        val cooldownTotalMs = 1200L
        while (true) {
            val elapsed = System.currentTimeMillis() - lastQuickChatSentMs
            val remaining = (cooldownTotalMs - elapsed).coerceAtLeast(0L)
            quickChatCooldownMs = remaining
            if (remaining <= 0L) break
            delay(150L)
        }
    }

    val effectiveSoloMap = if (onlineRoom == null) settings.selectedMap else null
    val engine = remember(onlineRoom?.id, peacefulMode, effectiveSoloMap) {
        val initialEngineSettings = if (onlineRoom != null) {
            settings.copy(selectedMap = GameMap.fromNameSafe(onlineRoom.mapId))
        } else {
            settings
        }
        GameEngine(
            soundManager = soundManager,
            initialSettings = initialEngineSettings,
            isOnlineMode = onlineRoom != null,
            peacefulMode = if (onlineRoom != null) false else peacefulMode,
            openBuildInitially = openBuildInitially,
            localUserId = lobbyViewModel?.currentUserId ?: "local_player",
            initialBotRoster = if (onlineRoom != null) emptyList() else botRoster,
            onRemotePlayerDamaged = { targetUid, newHp ->
                lobbyViewModel?.damageRemotePlayer(targetUid, newHp)
            },
            onRemotePropSpawned = { type, x, y, z, yaw ->
                lobbyViewModel?.spawnSyncedProp(type, x, y, z, yaw)
            },
            onRemotePropDestroyed = { remoteId ->
                lobbyViewModel?.markSyncedPropDestroyed(remoteId)
            },
            hapticController = hapticController
        )
    }

    // Apply live Settings changes instantly to 3D renderer, controls, and 3D spatial audio!
    LaunchedEffect(settings, engine) {
        engine.updateSettings(settings)
    }

    // Apply live online room rules (map switch, mode, PvP, infinite ammo, god mode, guest spawn flags)
    LaunchedEffect(liveOnlineRoom, lobbyViewModel?.currentUserId, engine) {
        if (onlineRoom != null && liveOnlineRoom != null) {
            engine.applyOnlineRoomRules(
                rules = liveOnlineRoom.toRoomRules(),
                isHost = liveOnlineRoom.hostId == lobbyViewModel?.currentUserId
            )
        }
    }

    // Auto-exit cleanly if host deletes/closes room or kicks local player mid-match
    LaunchedEffect(roomClosedNotice) {
        if (onlineSyncEnabled && roomClosedNotice != null) {
            onExitToMenu()
        }
    }

    val expectedActiveMap = remember(onlineRoom?.id, liveOnlineRoom?.mapId, settings.selectedMap) {
        if (onlineRoom != null) {
            GameMap.fromNameSafe(liveOnlineRoom?.mapId ?: onlineRoom.mapId)
        } else {
            settings.selectedMap
        }
    }

    // Thread-safe loading state channel updated from ChickenGlRenderer on the GL thread
    val loadStateFlow = remember(engine) {
        MutableStateFlow(
            SceneLoadState(
                map = expectedActiveMap,
                progress = 0f,
                stageLabel = "Подготовка арены…",
                isReady = false,
                generation = 0
            )
        )
    }
    val sceneLoadState by loadStateFlow.collectAsStateWithLifecycle()
    val isSceneLoading = !sceneLoadState.isReady || sceneLoadState.map != expectedActiveMap

    // Immediately reset UI progress to 0% if an online host switches the map before the next GL frame
    LaunchedEffect(expectedActiveMap, loadStateFlow) {
        val current = loadStateFlow.value
        if (current.map != expectedActiveMap) {
            loadStateFlow.value = SceneLoadState(
                map = expectedActiveMap,
                progress = 0.04f,
                stageLabel = "Переключение карты…",
                isReady = false,
                generation = current.generation + 1
            )
        }
    }

    // Calibrated Gravity-Aware Hardware Gyroscope Aim Assistance:
    // Supports TYPE_GYROSCOPE -> TYPE_GYROSCOPE_UNCALIBRATED -> GAME_ROTATION_VECTOR / ROTATION_VECTOR fallback,
    // 125 Hz smooth sampling (~8ms, no HIGH_SAMPLING_RATE_SENSORS permission needed), all 4 rotations, and pause gating.
    DisposableEffect(
        context,
        engine,
        settings.gyroEnabled,
        pauseOpen,
        settingsOpen,
        hostControlsOpen,
        isSceneLoading,
        appInForeground
    ) {
        if (!settings.gyroEnabled || pauseOpen || settingsOpen || hostControlsOpen || isSceneLoading || !appInForeground) {
            engine.input.consumeGyroDelta()
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val primarySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

            if (sensorManager == null || primarySensor == null) {
                onDispose { }
            } else {
                val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                val accelSensor = if (gravitySensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                val hostActivity = context.findHostActivity()
                val processor = GyroAimProcessor()
                val outDelta = FloatArray(2)

                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        when (event.sensor.type) {
                            Sensor.TYPE_GRAVITY -> {
                                if (event.values.size >= 3) {
                                    processor.onGravitySample(
                                        gx = event.values[0],
                                        gy = event.values[1],
                                        gz = event.values[2],
                                        isRawAccelerometer = false
                                    )
                                }
                            }
                            Sensor.TYPE_ACCELEROMETER -> {
                                if (event.values.size >= 3) {
                                    processor.onGravitySample(
                                        gx = event.values[0],
                                        gy = event.values[1],
                                        gz = event.values[2],
                                        isRawAccelerometer = true
                                    )
                                }
                            }
                            Sensor.TYPE_GYROSCOPE -> {
                                if (event.values.size < 3) return
                                val rot = resolveDisplayRotation(hostActivity, displayManager, windowManager)
                                if (processor.processGyroSampleInto(
                                        wx = event.values[0],
                                        wy = event.values[1],
                                        wz = event.values[2],
                                        timestampNs = event.timestamp,
                                        displayRotation = rot,
                                        outDelta = outDelta
                                    )
                                ) {
                                    engine.input.addGyroDelta(outDelta[0], outDelta[1])
                                }
                            }
                            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> {
                                if (event.values.size < 3) return
                                // Subtract factory drift estimate values[3..5] when provided by Android HAL
                                val driftX = if (event.values.size >= 6) event.values[3] else 0f
                                val driftY = if (event.values.size >= 6) event.values[4] else 0f
                                val driftZ = if (event.values.size >= 6) event.values[5] else 0f
                                val rot = resolveDisplayRotation(hostActivity, displayManager, windowManager)
                                if (processor.processGyroSampleInto(
                                        wx = event.values[0] - driftX,
                                        wy = event.values[1] - driftY,
                                        wz = event.values[2] - driftZ,
                                        timestampNs = event.timestamp,
                                        displayRotation = rot,
                                        outDelta = outDelta
                                    )
                                ) {
                                    engine.input.addGyroDelta(outDelta[0], outDelta[1])
                                }
                            }
                            Sensor.TYPE_GAME_ROTATION_VECTOR,
                            Sensor.TYPE_ROTATION_VECTOR -> {
                                if (event.values.size < 3) return
                                val rot = resolveDisplayRotation(hostActivity, displayManager, windowManager)
                                val qw = if (event.values.size >= 4) event.values[3] else null
                                if (processor.processRotationVectorSampleInto(
                                        qxRaw = event.values[0],
                                        qyRaw = event.values[1],
                                        qzRaw = event.values[2],
                                        qwRaw = qw,
                                        timestampNs = event.timestamp,
                                        displayRotation = rot,
                                        outDelta = outDelta
                                    )
                                ) {
                                    engine.input.addGyroDelta(outDelta[0], outDelta[1])
                                }
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                // 8,000 us (~125 Hz) matches 120 FPS smoothly while staying safely under API 31+ 200 Hz cap
                val gyroSamplingPeriodUs = 8_000
                sensorManager.registerListener(listener, primarySensor, gyroSamplingPeriodUs)
                if (gravitySensor != null) {
                    sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
                } else if (accelSensor != null) {
                    sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
                }
                onDispose {
                    sensorManager.unregisterListener(listener)
                    processor.reset()
                    engine.input.consumeGyroDelta()
                }
            }
        }
    }

    // Properly pause 3D simulation, spatial audio & haptics when Loading/Pause/Settings/Host dialog is active
    LaunchedEffect(pauseOpen, settingsOpen, hostControlsOpen, isSceneLoading, engine) {
        val overlayPaused = pauseOpen || settingsOpen || hostControlsOpen || isSceneLoading
        engine.paused = overlayPaused
        soundManager.paused = isSceneLoading || ((pauseOpen || settingsOpen || hostControlsOpen) && onlineRoom == null)
        hapticController.paused = overlayPaused
        if (overlayPaused) {
            hapticController.cancel()
            engine.input.resetMotionControls()
        }
    }

    // Silence 3D spatial audio mixer, haptics & sensors cleanly when app is backgrounded
    DisposableEffect(lifecycleOwner, soundManager, hapticController, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    appInForeground = false
                    soundManager.setAppBackgrounded(true)
                    hapticController.paused = true
                    hapticController.cancel()
                    engine.input.resetMotionControls()
                }
                Lifecycle.Event.ON_RESUME -> {
                    appInForeground = true
                    soundManager.setAppBackgrounded(false)
                    hapticController.paused = pauseOpen || settingsOpen || hostControlsOpen || isSceneLoading
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hudState = remember(engine) { mutableStateOf(engine.latestHud) }

    LaunchedEffect(engine) {
        while (true) {
            val latest = engine.latestHud
            if (latest !== hudState.value) {
                hudState.value = latest
            }
            delay(33L)
        }
    }

    // Online multiplayer bidirectional sync (Firestore <-> 3D GameEngine)
    if (onlineSyncEnabled) {
        val remoteProps by lobbyViewModel.roomProps.collectAsStateWithLifecycle()

        LaunchedEffect(liveOnlinePlayers) {
            engine.applyRemotePlayers(liveOnlinePlayers)
        }
        LaunchedEffect(remoteProps) {
            engine.applyRemoteProps(remoteProps)
        }
        LaunchedEffect(onlineRoom.id, engine) {
            var lastSent: LocalNetworkState? = null
            var ticksSincePush = 0
            while (true) {
                delay(160L)
                val snap = engine.exportLocalPlayerSnapshot()
                ticksSincePush++
                val prev = lastSent
                val movedOrChanged = prev == null ||
                    snap.shooting ||
                    snap.hp != prev.hp ||
                    snap.kills != prev.kills ||
                    snap.weapon != prev.weapon ||
                    snap.drivingVehicle != prev.drivingVehicle ||
                    abs(snap.x - prev.x) > 0.12 ||
                    abs(snap.y - prev.y) > 0.12 ||
                    abs(snap.z - prev.z) > 0.12 ||
                    abs(snap.yaw - prev.yaw) > 2.5
                if (movedOrChanged || ticksSincePush >= 4) {
                    lobbyViewModel.pushLocalPlayerState(snap)
                    lastSent = snap
                    ticksSincePush = 0
                }
            }
        }
    }

    val latestEngine by rememberUpdatedState(engine)
    DisposableEffect(Unit) {
        onDispose {
            val activeEngine = latestEngine
            val vm = lobbyViewModel
            if (vm != null) {
                val playedCombat = !peacefulMode && (activeEngine.playerKills > 0 || activeEngine.worldTime >= 5f)
                val won = if (playedCombat) {
                    if (onlineRoom != null) {
                        val opponents = vm.roomPlayers.value.filter { it.userId != vm.currentUserId }
                        if (opponents.isNotEmpty()) {
                            val maxOpponentKills = opponents.maxOf { it.kills.toInt() }
                            activeEngine.playerKills > 0 && activeEngine.playerKills >= maxOpponentKills
                        } else {
                            activeEngine.playerKills >= 5
                        }
                    } else {
                        activeEngine.playerKills >= 5
                    }
                } else {
                    false
                }

                if (playedCombat) {
                    vm.saveMatchSummary(
                        kills = activeEngine.playerKills,
                        bestStreak = activeEngine.playerBestStreak,
                        won = won
                    )
                }

                val telemetry = MatchMissionTelemetry(
                    kills = if (peacefulMode) 0 else activeEngine.playerKills,
                    driveMeters = activeEngine.sessionDriveMeters.toInt(),
                    propsBuilt = activeEngine.sessionPropsBuilt,
                    propsDestroyed = activeEngine.sessionPropsDestroyed,
                    vehicleKills = if (peacefulMode) 0 else activeEngine.sessionVehicleKills,
                    explosions = activeEngine.sessionExplosions,
                    playedMatch = playedCombat,
                    wonMatch = won
                )
                val hasMissionActivity = telemetry.kills > 0 ||
                    telemetry.driveMeters > 0 ||
                    telemetry.propsBuilt > 0 ||
                    telemetry.propsDestroyed > 0 ||
                    telemetry.vehicleKills > 0 ||
                    telemetry.explosions > 0 ||
                    telemetry.playedMatch
                if (hasMissionActivity) {
                    vm.recordMissionProgress(telemetry)
                }
            }
            if (onlineRoom != null) {
                vm?.leaveActiveRoom()
            }
            hapticController.cancel()
            soundManager.release()
        }
    }

    BackHandler {
        when {
            isSceneLoading -> Unit
            quickChatOpen -> quickChatOpen = false
            hostControlsOpen -> hostControlsOpen = false
            settingsOpen -> settingsOpen = false
            else -> pauseOpen = !pauseOpen
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Hardware-Accelerated 3D OpenGL ES 2.0 Viewport
        GameGlSurfaceView(
            engine = engine,
            targetFps = settings.targetFps,
            onLoadStateChanged = { state -> loadStateFlow.value = state }
        )

        // 2. Clean Compact Touch Controls & Transparent Mobile Shooter HUD
        GameHudOverlay(
            engine = engine,
            hudState = hudState,
            settings = settings,
            roomCode = liveOnlineRoom?.roomCode,
            onOpenPause = {
                quickChatOpen = false
                settingsOpen = false
                hostControlsOpen = false
                pauseOpen = true
            },
            onOpenSettings = {
                quickChatOpen = false
                pauseOpen = false
                hostControlsOpen = false
                settingsOpen = true
            },
            onOpenHostControls = {
                quickChatOpen = false
                pauseOpen = false
                settingsOpen = false
                hostControlsOpen = true
            },
            onPetPigBurger = { engine.petNearestPigBurger() },
            onToggleDoor = { engine.toggleNearestDoor() },
            onToggleWeaponSkin = {
                onUpdateSettings { it.copy(weaponSkin = it.weaponSkin.next()) }
            },
            quickChatOpen = quickChatOpen,
            quickChatUnreadCount = quickChatUnreadCount,
            quickChatMessages = activeChatMessages,
            quickChatLiveItems = liveQuickChatEntries.map { it.first },
            currentUserId = currentUserId,
            isOnlineMatch = onlineSyncEnabled,
            quickChatCooldownMs = quickChatCooldownMs,
            onToggleQuickChat = {
                soundManager.playUiClick()
                val nextOpen = !quickChatOpen
                quickChatOpen = nextOpen
                if (nextOpen) {
                    quickChatUnreadCount = 0
                }
            },
            onSendQuickChat = { phrase ->
                val now = System.currentTimeMillis()
                if (now - lastQuickChatSentMs >= 1200L) {
                    lastQuickChatSentMs = now
                    quickChatCooldownMs = 1200L
                    quickChatOpen = false
                    quickChatUnreadCount = 0
                    soundManager.playUiClick()
                    if (onlineSyncEnabled) {
                        lobbyViewModel.sendChatMessage(phrase)
                    } else {
                        val senderLabel = lobbyViewModel?.initialDisplayName?.takeIf { it.isNotBlank() } ?: "Вы"
                        localQuickMessages.add(
                            RoomMessage(
                                id = "local_qc_${++localChatSeq}",
                                senderId = currentUserId,
                                senderName = senderLabel,
                                text = phrase
                            )
                        )
                        if (localQuickMessages.size > 20) {
                            localQuickMessages.removeAt(0)
                        }
                    }
                }
            },
            onCloseQuickChat = {
                quickChatOpen = false
            }
        )

        // 3. Pause Menu Overlay
        if (pauseOpen && !settingsOpen && !hostControlsOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TacticalNavy.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = BunkerSlate,
                    border = BorderStroke(1.8.dp, BeakAmber.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hud_pause),
                            style = MaterialTheme.typography.headlineMedium,
                            color = BeakAmber
                        )
                        val pauseHud = hudState.value
                        val subtitle = when {
                            liveOnlineRoom != null -> "${liveOnlineRoom.name} • ${pauseHud.activeMapLabel} • ${liveOnlineRoom.roomCode}"
                            peacefulMode -> "Мирный режим • ${pauseHud.activeMapLabel} • 9 машин"
                            else -> "${pauseHud.activeMapLabel} • Побед: ${pauseHud.kills}"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (peacefulMode) NitroEmerald else if (liveOnlineRoom != null) ArenaSkyCyan else MutedSteel
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { pauseOpen = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NitroEmerald,
                                contentColor = TacticalNavy
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pause_resume_button")
                        ) {
                            Text(stringResource(R.string.hud_resume), style = MaterialTheme.typography.labelLarge)
                        }

                        if (pauseHud.isOnlineHost && liveOnlineRoom != null) {
                            Button(
                                onClick = {
                                    pauseOpen = false
                                    hostControlsOpen = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ArenaSkyCyan,
                                    contentColor = TacticalNavy
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("pause_host_controls_button")
                            ) {
                                Text("Управление комнатой (Хост)", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        Button(
                            onClick = { settingsOpen = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BeakAmber,
                                contentColor = TacticalNavy
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pause_settings_button")
                        ) {
                            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = {
                                pauseOpen = false
                                onExitToMenu()
                            },
                            border = BorderStroke(1.5.dp, CombCrimson),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pause_exit_button")
                        ) {
                            Text(
                                stringResource(R.string.hud_leave_match),
                                style = MaterialTheme.typography.labelLarge,
                                color = FeatherWhite
                            )
                        }
                    }
                }
            }
        }

        // 4. Live In-Match Settings Overlay (Graphics, FOV, Controls, Audio)
        if (settingsOpen) {
            SettingsOverlay(
                settings = settings,
                onUpdate = onUpdateSettings,
                onReset = onResetSettings,
                onDismiss = { settingsOpen = false }
            )
        }

        // 5. Live In-Match Host Room Control Overlay (Map switch, rules, kick, delete room)
        if (hostControlsOpen && onlineSyncEnabled && liveOnlineRoom != null && lobbyViewModel != null) {
            HostRoomControlDialog(
                room = liveOnlineRoom,
                players = liveOnlinePlayers,
                currentUserId = lobbyViewModel.currentUserId,
                onUpdateSettings = { updates -> lobbyViewModel.updateActiveRoomSettings(updates) },
                onUpdatePlayerPrivileges = { targetUid, godMode, canBuild ->
                    lobbyViewModel.updatePlayerPrivileges(targetUid, godMode, canBuild)
                },
                onKickPlayer = { targetUid -> lobbyViewModel.kickPlayer(targetUid) },
                onDeleteRoom = {
                    hostControlsOpen = false
                    lobbyViewModel.deleteOrCloseActiveRoom(onClosed = onExitToMenu)
                },
                onDismiss = { hostControlsOpen = false }
            )
        }

        // 6. Smooth Fullscreen Map Loading Overlay (0..100% real shader/GLB warmup + 300ms fade-out)
        MapLoadingOverlay(
            map = expectedActiveMap,
            isLoading = isSceneLoading,
            progress = if (sceneLoadState.map == expectedActiveMap) sceneLoadState.progress else 0.04f,
            stageLabel = if (sceneLoadState.map == expectedActiveMap) sceneLoadState.stageLabel else "Переключение карты…",
            isOnline = onlineRoom != null,
            peacefulMode = peacefulMode && onlineRoom == null,
            roomCode = liveOnlineRoom?.roomCode
        )
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

private fun resolveDisplayRotation(
    activity: Activity?,
    displayManager: DisplayManager?,
    windowManager: WindowManager?
): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val activityRot = runCatching { activity?.display?.rotation }.getOrNull()
        if (activityRot != null) return activityRot
    }
    val dmRot = runCatching { displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation }.getOrNull()
    if (dmRot != null) return dmRot
    @Suppress("DEPRECATION")
    return runCatching { windowManager?.defaultDisplay?.rotation }.getOrNull() ?: Surface.ROTATION_90
}

