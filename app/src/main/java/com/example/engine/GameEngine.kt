package com.example.engine

import com.example.data.GameSettings
import com.example.data.OnlineGameMode
import com.example.data.OnlineRoomRules
import com.example.data.RoomPlayer
import com.example.data.RoomProp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Core 3D physics, combat, multi-vehicle, bot AI, and peaceful sandbox engine for Chicken Gun 3D.
 * Thread-safe snapshot synchronization between simulation and OpenGL rendering thread.
 */
class GameEngine(
    val soundManager: SoundManager,
    initialSettings: GameSettings = GameSettings(),
    private val isOnlineMode: Boolean = false,
    val peacefulMode: Boolean = false,
    openBuildInitially: Boolean = false,
    private val localUserId: String = "local_player",
    private val initialBotRoster: List<LobbyBotSlot> = if (isOnlineMode) emptyList() else LobbyBotSlot.defaultRoster(5),
    private val onRemotePlayerDamaged: ((targetUid: String, newHp: Int) -> Unit)? = null,
    private val onRemotePropSpawned: ((PropType, Float, Float, Float, Float) -> Unit)? = null,
    private val onRemotePropDestroyed: ((remoteId: String) -> Unit)? = null,
    val hapticController: HapticController = NoOpHapticController
) {
    companion object {
        const val ARENA_HALF_SIZE = 150f
        const val GRAVITY = 28f
        const val PLAYER_EYE_HEIGHT = 1.62f
        const val PLAYER_RADIUS = 0.58f
        const val MAX_PROPS = Int.MAX_VALUE
        const val MAX_VEHICLES = Int.MAX_VALUE
        const val MAX_PIG_BURGERS = Int.MAX_VALUE
        const val MAX_PARTICLES = 380
        const val MAX_EXPLOSIONS = 28
        const val SPAWN_PROTECTION_SEC = 3.0f
        const val SAFE_SPAWN_Y = 2.0f
        const val PLAYER_MAX_HP = 100
        const val HP_REGEN_INTERVAL_SEC = 1.3f
        const val MAX_DAMAGE_POPUPS = 7
    }

    val stateLock = Any()
    val input = GameInputState()

    @Volatile
    var paused: Boolean = false

    @Volatile
    var settings: GameSettings = initialSettings
        private set

    @Volatile
    var activeMap: GameMap = initialSettings.selectedMap
        private set

    @Volatile
    var onlineRoomRules: OnlineRoomRules = OnlineRoomRules(mapId = initialSettings.selectedMap.name)
        private set

    @Volatile
    var isOnlineHost: Boolean = false
        private set

    @Volatile
    var personalGodMode: Boolean = false
        private set

    @Volatile
    var personalCanBuildOverride: Boolean? = null
        private set

    private fun canLocalBuildProps(): Boolean {
        if (!isOnlineMode) return true
        personalCanBuildOverride?.let { return it }
        return isOnlineHost || onlineRoomRules.allowGuestPropSpawn
    }

    private fun canLocalSpawnVehicles(): Boolean {
        if (!isOnlineMode) return true
        personalCanBuildOverride?.let { return it }
        return isOnlineHost || onlineRoomRules.allowGuestVehicleSpawn
    }

    private fun isInfiniteAmmoActive(): Boolean =
        peacefulMode || (isOnlineMode && onlineRoomRules.infiniteAmmo)

    private fun isGodModeActive(): Boolean =
        peacefulMode || (isOnlineMode && (onlineRoomRules.godMode || personalGodMode))

    private fun canDamageRemotePlayers(): Boolean =
        !peacefulMode && (!isOnlineMode || (onlineRoomRules.friendlyFire && onlineRoomRules.gameMode != OnlineGameMode.RACING && !onlineRoomRules.godMode))

    private var footstepTimer: Float = 0f
    private var hpRegenAccum: Float = 0f

    // Player state: spawn safely above ground at y = 2.0f with full 100 HP & 3s Spawn Protection
    var playerX = 0f
    var playerY = SAFE_SPAWN_Y
    var playerZ = 18f
    var playerVx = 0f
    var playerVy = 0f
    var playerVz = 0f
    var playerYaw = 0f // degrees; 0 = -Z (facing central arena), 90 = +X, 180 = +Z
    var playerPitch = -4f
    var playerGrounded = false
    var playerWalkCycle = 0f
    var playerHp = 100
    var playerAlive = true
    var spawnProtectionTimer = SPAWN_PROTECTION_SEC
    var playerKills = 0
    var playerStreak = 0
    var playerBestStreak = 0
    var sessionDriveMeters = 0f
    var sessionPropsBuilt = 0
    var sessionPropsDestroyed = 0
    var sessionVehicleKills = 0
    var sessionExplosions = 0

    // Weapon state
    var currentWeapon = WeaponType.RIFLE
    private val weaponAmmo = mutableMapOf(
        WeaponType.PISTOL to WeaponType.PISTOL.maxAmmo,
        WeaponType.RIFLE to WeaponType.RIFLE.maxAmmo,
        WeaponType.RPG to WeaponType.RPG.maxAmmo
    )
    var fireCooldown = 0f
    var reloadTimer = 0f
    var weaponRecoil = 0f
    var muzzleFlashTimer = 0f
    var rpgRocketLoaded = true
    var aiming = false
    var aimBlend = 0f // smoothly interpolated 0..1 ADS progress for 1P viewmodel & FOV
    var dynamicSpread = 0f
    var cameraMode = if (initialSettings.defaultThirdPerson) CameraMode.THIRD_PERSON else CameraMode.FIRST_PERSON
    private var preFreeCamMode = cameraMode
    var freeCamX = 0f
    var freeCamY = 6f
    var freeCamZ = 24f
    var freeCamYaw = 0f
    var freeCamPitch = -10f
    private var freeCamVx = 0f
    private var freeCamVy = 0f
    private var freeCamVz = 0f
    // Free 360-degree vehicle orbit camera offset (relative to car.yaw) and elevation pitch; preserves user angle!
    var vehicleCamOrbitYaw = 0f
    var vehicleCamOrbitPitch = 16f
    private var prevFirePressed = false
    var justFiredFlag = false

    // Latest world muzzle position captured from the active GLB weapon's "muzzle" node
    @Volatile var rendererMuzzleX = 0f
    @Volatile var rendererMuzzleY = 0f
    @Volatile var rendererMuzzleZ = 0f
    @Volatile var rendererMuzzleValid = false

    fun setRendererMuzzleWorld(x: Float, y: Float, z: Float) {
        rendererMuzzleX = x
        rendererMuzzleY = y
        rendererMuzzleZ = z
        rendererMuzzleValid = true
    }

    // Responsive parkour physics helpers
    private var coyoteTimer = 0f
    private var jumpBufferTimer = 0f
    var landingCameraDip = 0f

    // Vehicle state
    var activeVehicle: VehicleEntity? = null
    var nearestVehicle: VehicleEntity? = null

    // Living NPC Хряк-Бургер state
    val pigBurgers = mutableListOf<PigBurgerNpc>()
    var nearestPigBurger: PigBurgerNpc? = null
    var nearestDoorProp: WorldProp? = null
    private var pigSeq = 1

    // Racing Lap Timer state (driven by finish_arch.glb)
    var raceActive = false
    var raceLapCount = 0
    var raceCurrentLapSec = 0f
    var raceBestLapSec = 0f
    private var raceCheckpointPassed = false
    private var raceBannerText = ""
    private var raceBannerTimer = 0f
    private var raceCrossCooldown = 0f
    private var raceArchX = 0f
    private var raceArchZ = 0f

    // Sandbox build state
    var buildModeOpen = openBuildInitially
    var buildCategory = BuildCategory.STUNTS_TRAPS
    var selectedBuildProp = PropType.LOOP_THE_LOOP
    var selectedVehicleType = VehicleType.POLICE_SUV
    var buildGridSnap = true
    var ghostPlacementValid = true
    var ghostYawOffset = 0f
    var ghostX = 0f
    var ghostY = 0f
    var ghostZ = 0f
    var ghostYaw = 0f

    // Visual feedback
    var hitMarkerAlpha = 0f
    var critHitFlashTimer = 0f
    var damageVignetteAlpha = 0f
    var screenShake = 0f
    var worldTime = 0f
    private var killStreakBanner = ""
    private var killStreakBannerTimer = 0f

    // World entities
    val props = mutableListOf<WorldProp>()
    val vehicles = mutableListOf<VehicleEntity>()
    val bots = mutableListOf<BotChicken>()
    val remotePlayers = mutableMapOf<String, RemoteChicken>()
    val projectiles = mutableListOf<Projectile>()
    val explosions = mutableListOf<ExplosionEffect>()
    val particles = mutableListOf<Particle3D>()
    val damagePopups3D = mutableListOf<DamagePopup3D>()

    private val killFeed = ArrayDeque<KillFeedEntry>()
    private val destroyedRemotePropIds = mutableSetOf<String>()
    private var killFeedSeq = 1L
    private var damagePopupSeq = 1L
    private var propSeq = 1
    private var hudAccum = 0f
    private var fpsFrames = 0
    private var fpsTimer = 0f
    private var currentFps = 120

    // Pre-allocated collision scratch outputs to avoid Pair/Triple allocations in hot loops
    private var scratchResX = 0f
    private var scratchResZ = 0f
    private var scratchHitWall = false

    // 2D spatial hash grid for O(1) broad-phase prop queries + 25 Hz AI logic throttling
    private val propGrid = PropSpatialGrid()
    private var propGridDirty = true
    private var aiDecisionAccum = 0f
    private val AI_DECISION_INTERVAL = 0.04f // 25 Hz heavy AI decisions, 120 Hz smooth motion

    private fun ensurePropGridFresh() {
        if (propGridDirty || propGrid.indexedCount != props.size) {
            propGrid.rebuild(props)
            propGridDirty = false
        }
    }

    @Volatile
    var latestHud: HudSnapshot = HudSnapshot()
        private set

    init {
        soundManager.applySettings(initialSettings)
        hapticController.enabled = initialSettings.hapticsEnabled
        buildInitialArena()
        spawnInitialVehicles()
        spawnInitialBots()
        spawnInitialPigBurgers()
        if (buildModeOpen) updateBuildGhost()
        if (peacefulMode) {
            pushKillFeed("Мирный режим песочницы активен! Стройте трассы и гладьте Хряков-Бургеров.")
        }
        refreshHudSnapshot()
    }

    fun updateSettings(newSettings: GameSettings) {
        settings = newSettings
        soundManager.applySettings(newSettings)
        hapticController.enabled = newSettings.hapticsEnabled
    }

    fun applyOnlineRoomRules(rules: OnlineRoomRules, isHost: Boolean) = synchronized(stateLock) {
        onlineRoomRules = rules
        isOnlineHost = isHost
        if (rules.godMode && playerAlive) {
            playerHp = 100
        }
        if (rules.infiniteAmmo) {
            reloadTimer = 0f
            WeaponType.entries.forEach { weaponAmmo[it] = it.maxAmmo }
        }
        if (isOnlineMode && buildCategory == BuildCategory.NPCS) {
            buildCategory = BuildCategory.STUNTS_TRAPS
        }
        if (!canLocalBuildProps() && !canLocalSpawnVehicles() && buildModeOpen) {
            buildModeOpen = false
        }
        val targetMap = GameMap.fromNameSafe(rules.mapId)
        if (targetMap != activeMap) {
            switchMapLocked(targetMap)
            pushKillFeed("Хост переключил карту: ${targetMap.label}")
        }
        refreshHudSnapshot()
    }

    fun switchMap(newMap: GameMap) = synchronized(stateLock) {
        if (newMap == activeMap) return@synchronized
        switchMapLocked(newMap)
        refreshHudSnapshot()
    }

    private fun switchMapLocked(newMap: GameMap) {
        activeMap = newMap
        activeVehicle = null
        nearestVehicle = null
        nearestDoorProp = null
        nearestPigBurger = null
        raceActive = false
        raceLapCount = 0
        raceCurrentLapSec = 0f
        raceBestLapSec = 0f
        raceBannerText = ""
        projectiles.clear()
        explosions.clear()
        particles.clear()
        damagePopups3D.clear()
        buildInitialArena()
        spawnInitialVehicles()
        spawnInitialBots()
        spawnInitialPigBurgers()
        // Place player cleanly on the new map's spawn plaza
        playerX = 0f
        playerZ = 18f
        playerY = max(SAFE_SPAWN_Y, getSurfaceHeightAt(playerX, playerZ, 6f) + 1.2f)
        playerVx = 0f
        playerVy = 0f
        playerVz = 0f
        playerYaw = 0f
        playerPitch = -4f
        playerGrounded = false
        spawnProtectionTimer = SPAWN_PROTECTION_SEC
    }

    private fun buildInitialArena() {
        props.clear()
        propGridDirty = true
        val specs = when (activeMap) {
            GameMap.MAIN_ARENA -> MapLayouts.buildMainArenaSpecs()
            GameMap.TURBO_SPEEDWAY -> MapLayouts.buildTurboSpeedwaySpecs()
            GameMap.TACTICAL_COMPOUND -> MapLayouts.buildTacticalCompoundSpecs()
        }
        for (i in specs.indices) {
            val s = specs[i]
            addStaticProp(s.type, s.x, s.y, s.z, s.yaw, s.phaseOffset, s.startOpen)
        }
    }

    private fun addStaticProp(
        type: PropType,
        x: Float,
        y: Float,
        z: Float,
        yaw: Float,
        phaseOffset: Float = 0f,
        startOpen: Boolean = false
    ) {
        val openInitially = startOpen && type.hasInteractiveDoor
        props.add(
            WorldProp(
                id = "init_${propSeq++}",
                type = type,
                x = x,
                y = y,
                z = z,
                yaw = yaw,
                userPlaced = false,
                trapPhaseOffset = phaseOffset,
                doorOpen = openInitially,
                doorTouched = openInitially,
                doorAnimTime = if (openInitially) 2.5f else 0f
            )
        )
    }

    private fun spawnInitialVehicles() {
        vehicles.clear()
        vehicles.addAll(MapLayouts.initialVehiclesForMap(activeMap))
    }

    private fun spawnInitialBots() {
        bots.clear()
        // Online rooms are strictly humans-only — never spawn bots in online matches!
        if (isOnlineMode) return
        val roster = initialBotRoster
        if (roster.isEmpty()) return
        for (i in roster.indices) {
            val slot = roster[i]
            val angle = (-PI.toFloat() * 0.9f) + (i.toFloat() / max(1, roster.size - 1)) * (PI.toFloat() * 0.8f)
            val dist = 30f + (i % 3) * 8f
            val rawX = clampArena(cos(angle) * dist)
            val rawZ = clampArena(sin(angle) * dist - 14f)
            val safeXZ = resolveHorizontalCollision(rawX, 0.2f, rawZ, PLAYER_RADIUS + 0.35f)
            val safeY = getSurfaceHeightAt(safeXZ.first, safeXZ.second, 4f) + 0.8f
            bots.add(
                BotChicken(
                    id = slot.id,
                    name = slot.name,
                    x = safeXZ.first,
                    y = safeY,
                    z = safeXZ.second,
                    yaw = (atan2(playerX - safeXZ.first, -(playerZ - safeXZ.second)) * 180f / PI.toFloat()),
                    weapon = slot.weapon,
                    shootCooldown = 2.8f + i * 0.45f,
                    accentR = slot.accentR,
                    accentG = slot.accentG,
                    accentB = slot.accentB
                )
            )
        }
    }

    private fun spawnInitialPigBurgers() {
        pigBurgers.clear()
        // Keep online rooms strictly human-only (no AI NPCs in multiplayer rooms)
        if (isOnlineMode) return
        val names = listOf("Хряк-Бургер", "Биг-Хряк")
        val spots = listOf(Pair(-8f, 6f), Pair(11f, -6f))
        for (i in spots.indices) {
            val (rx, rz) = spots[i]
            val safeXZ = resolveHorizontalCollision(clampArena(rx), 0.2f, clampArena(rz), 0.85f)
            val safeY = getSurfaceHeightAt(safeXZ.first, safeXZ.second, 3f)
            pigBurgers.add(
                PigBurgerNpc(
                    id = "pig_${pigSeq++}",
                    name = names[i % names.size],
                    x = safeXZ.first,
                    y = safeY,
                    z = safeXZ.second,
                    yaw = (i * 115f) % 360f,
                    state = if (i % 2 == 0) PigBurgerState.WALK else PigBurgerState.IDLE,
                    stateTimer = 1.8f + i * 1.1f,
                    animTime = i * 0.45f
                )
            )
        }
    }

    // --- PUBLIC USER ACTIONS ---
    fun toggleCameraMode() = synchronized(stateLock) {
        if (cameraMode == CameraMode.FREE_CAM) {
            cameraMode = if (preFreeCamMode == CameraMode.FREE_CAM) CameraMode.FIRST_PERSON else preFreeCamMode
            freeCamVx = 0f
            freeCamVy = 0f
            freeCamVz = 0f
            input.flyUp = false
            input.flyDown = false
        } else {
            cameraMode = if (cameraMode == CameraMode.FIRST_PERSON) {
                CameraMode.THIRD_PERSON
            } else {
                CameraMode.FIRST_PERSON
            }
            preFreeCamMode = cameraMode
        }
        refreshHudSnapshot()
    }

    fun toggleFreeCamera() = synchronized(stateLock) {
        if (cameraMode == CameraMode.FREE_CAM) {
            cameraMode = if (preFreeCamMode == CameraMode.FREE_CAM) CameraMode.FIRST_PERSON else preFreeCamMode
            freeCamVx = 0f
            freeCamVy = 0f
            freeCamVz = 0f
            input.resetMotionControls()
        } else {
            preFreeCamMode = cameraMode
            aiming = false
            buildModeOpen = false
            input.resetMotionControls()
            val car = activeVehicle
            if (car != null) {
                val orbitYaw = (car.yaw + vehicleCamOrbitYaw) % 360f
                val orbitYawRad = orbitYaw * PI.toFloat() / 180f
                val orbitPitchRad = vehicleCamOrbitPitch.coerceIn(-8f, 65f) * PI.toFloat() / 180f
                val dist = 7.2f + car.type.halfL * 0.60f
                freeCamX = clampArena(car.x - sin(orbitYawRad) * dist * cos(orbitPitchRad))
                freeCamY = (car.y + 1.68f + dist * sin(orbitPitchRad)).coerceIn(1.2f, 120f)
                freeCamZ = clampArena(car.z + cos(orbitYawRad) * dist * cos(orbitPitchRad))
                freeCamYaw = orbitYaw
                freeCamPitch = (-vehicleCamOrbitPitch * 0.65f).coerceIn(-75f, 75f)
            } else {
                val yawRad = playerYaw * PI.toFloat() / 180f
                val pitchRad = playerPitch * PI.toFloat() / 180f
                val backDist = if (preFreeCamMode == CameraMode.THIRD_PERSON) 5.2f else 0.6f
                freeCamX = clampArena(playerX - sin(yawRad) * cos(pitchRad) * backDist)
                freeCamY = (playerY + if (preFreeCamMode == CameraMode.THIRD_PERSON) 2.5f else PLAYER_EYE_HEIGHT).coerceIn(1.2f, 120f)
                freeCamZ = clampArena(playerZ + cos(yawRad) * cos(pitchRad) * backDist)
                freeCamYaw = playerYaw
                freeCamPitch = playerPitch.coerceIn(-80f, 80f)
            }
            freeCamVx = 0f
            freeCamVy = 0f
            freeCamVz = 0f
            cameraMode = CameraMode.FREE_CAM
        }
        refreshHudSnapshot()
    }

    fun resetVehicleCameraOrbit() = synchronized(stateLock) {
        vehicleCamOrbitYaw = 0f
        vehicleCamOrbitPitch = 16f
        refreshHudSnapshot()
    }

    fun toggleAim() = synchronized(stateLock) {
        if (activeVehicle != null || cameraMode == CameraMode.FREE_CAM) {
            aiming = false
        } else {
            aiming = !aiming
        }
        refreshHudSnapshot()
    }

    fun switchWeapon(target: WeaponType? = null) = synchronized(stateLock) {
        val nextWeapon = target ?: currentWeapon.next()
        if (nextWeapon == currentWeapon) return@synchronized
        currentWeapon = nextWeapon
        reloadTimer = 0f
        weaponRecoil = 0.08f
        prevFirePressed = false
        soundManager.playWeaponSwitch()
        refreshHudSnapshot()
    }

    fun startReload() = synchronized(stateLock) {
        val ammo = weaponAmmo[currentWeapon] ?: 0
        if (reloadTimer <= 0f && ammo < currentWeapon.maxAmmo) {
            reloadTimer = currentWeapon.reloadSec
            soundManager.playReload()
            refreshHudSnapshot()
        }
    }

    fun toggleVehicle() = synchronized(stateLock) {
        if (!playerAlive) return@synchronized
        val currentCar = activeVehicle
        input.resetMotionControls()
        if (currentCar != null) {
            val rad = currentCar.yaw * PI.toFloat() / 180f
            val sideX = -cos(rad)
            val sideZ = -sin(rad)
            val backX = -sin(rad)
            val backZ = cos(rad)
            val offset = currentCar.type.halfW + 1.45f
            val candidates = listOf(
                Pair(currentCar.x + sideX * offset, currentCar.z + sideZ * offset),
                Pair(currentCar.x - sideX * offset, currentCar.z - sideZ * offset),
                Pair(currentCar.x + backX * (currentCar.type.halfL + 1.4f), currentCar.z + backZ * (currentCar.type.halfL + 1.4f))
            )
            var chosenX = currentCar.x
            var chosenZ = currentCar.z
            var chosenY = currentCar.y + 2.1f
            for ((cx, cz) in candidates) {
                val clampedX = clampArena(cx)
                val clampedZ = clampArena(cz)
                val resolved = resolveHorizontalCollision(clampedX, currentCar.y + 0.35f, clampedZ, PLAYER_RADIUS)
                if (hypot2(resolved.first - clampedX, resolved.second - clampedZ) < 0.25f) {
                    chosenX = resolved.first
                    chosenZ = resolved.second
                    chosenY = max(currentCar.y + 0.35f, getSurfaceHeightAt(chosenX, chosenZ, currentCar.y + 1.1f))
                    break
                }
            }
            playerX = chosenX
            playerZ = chosenZ
            playerY = chosenY
            playerVx = 0f
            playerVy = 2.5f
            playerVz = 0f
            activeVehicle = null
            aiming = false
            soundManager.playJump3D(playerX, playerY, playerZ, isLocalPlayer = true)
        } else {
            val near = findNearestVehicle(playerX, playerZ, 6.2f)
            if (near != null) {
                activeVehicle = near
                vehicleCamOrbitYaw = 0f
                vehicleCamOrbitPitch = 16f
                aiming = false
                buildModeOpen = false
                soundManager.playHorn3D(near.x, near.y + 1.0f, near.z, isLocalPlayer = true, vehicleType = near.type)
                pushKillFeed("За рулём: ${near.type.label}")
            }
        }
        refreshHudSnapshot()
    }

    fun petNearestPigBurger() = synchronized(stateLock) {
        if (!playerAlive || activeVehicle != null) return@synchronized
        val pig = findNearestPigBurger(playerX, playerZ, 4.2f) ?: return@synchronized
        pig.state = PigBurgerState.HAPPY_HOP
        pig.stateTimer = 2.2f
        pig.happyTimer = 2.6f
        pig.vy = max(pig.vy, 9.2f)
        pig.grounded = false
        pig.yaw = atan2(playerX - pig.x, -(playerZ - pig.z)) * 180f / PI.toFloat()
        soundManager.playPigOink3D(pig.x, pig.y + 0.9f, pig.z, panicked = false)
        // Joyful pink & golden hearts/sparkles burst!
        spawnDustBurst(pig.x, pig.y + 1.45f, pig.z, 1.0f, 0.32f, 0.68f, 10)
        spawnDustBurst(pig.x, pig.y + 1.65f, pig.z, 1.0f, 0.88f, 0.22f, 6)
        val healed = (100 - playerHp).coerceAtMost(12)
        if (healed > 0) {
            playerHp += healed
            pushKillFeed("🐷 ${pig.name} доволен! +$healed HP")
        } else {
            pushKillFeed("🐷 ${pig.name} радостно хрюкает!")
        }
        refreshHudSnapshot()
    }

    fun toggleNearestDoor(): Boolean = synchronized(stateLock) {
        if (!playerAlive || cameraMode == CameraMode.FREE_CAM) return@synchronized false
        val car = activeVehicle
        val refX = car?.x ?: playerX
        val refZ = car?.z ?: playerZ
        val maxDist = if (car != null) 9.8f else 6.8f
        val doorProp = findNearestDoorProp(refX, refZ, maxDist) ?: return@synchronized false
        doorProp.doorOpen = !doorProp.doorOpen
        doorProp.doorTouched = true
        doorProp.doorAnimTime = 0f
        nearestDoorProp = doorProp
        soundManager.playWeaponSwitch()
        val label = doorProp.type.doorActionLabel.ifEmpty { doorProp.type.label }
        pushKillFeed(if (doorProp.doorOpen) "🚪 Открыто: $label" else "🔒 Закрыто: $label")
        refreshHudSnapshot()
        true
    }

    private fun findNearestDoorProp(x: Float, z: Float, maxDist: Float): WorldProp? {
        ensurePropGridFresh()
        var best: WorldProp? = null
        var bestDist = maxDist
        propGrid.forEachCandidate(x, z, maxDist + 4f, props) { prop ->
            if (!prop.type.hasInteractiveDoor) return@forEachCandidate
            // Front door/gate lies on local -Z (world offset +halfD*sinYaw, -halfD*cosYaw)
            val doorX = prop.x + prop.sinYaw * prop.type.halfD
            val doorZ = prop.z - prop.cosYaw * prop.type.halfD
            val distDoor = hypot2(x - doorX, z - doorZ)
            val distCenter = hypot2(x - prop.x, z - prop.z)
            val d = if (prop.type == PropType.WALL_ARCH_GATE) {
                min(distDoor, distCenter)
            } else {
                min(distDoor, distCenter - prop.type.halfD * 0.45f).coerceAtLeast(0f)
            }
            if (d < bestDist) {
                bestDist = d
                best = prop
            }
        }
        return best
    }

    fun toggleBuildMenu() = synchronized(stateLock) {
        if (activeVehicle != null) return@synchronized
        if (!canLocalBuildProps() && !canLocalSpawnVehicles()) {
            pushKillFeed("Хост отключил стройку и спавн авто")
            refreshHudSnapshot()
            return@synchronized
        }
        buildModeOpen = !buildModeOpen
        if (buildModeOpen) {
            if (!canLocalBuildProps() && canLocalSpawnVehicles()) {
                buildCategory = BuildCategory.VEHICLES
            } else if (isOnlineMode && buildCategory == BuildCategory.NPCS) {
                buildCategory = BuildCategory.STUNTS_TRAPS
            }
            updateBuildGhost()
        }
        refreshHudSnapshot()
    }

    fun selectBuildCategory(category: BuildCategory) = synchronized(stateLock) {
        if (isOnlineMode && category == BuildCategory.NPCS) return@synchronized
        if (category == BuildCategory.VEHICLES && !canLocalSpawnVehicles()) {
            pushKillFeed("Хост отключил спавн машин")
            refreshHudSnapshot()
            return@synchronized
        }
        if (category != BuildCategory.VEHICLES && !canLocalBuildProps()) {
            pushKillFeed("Хост отключил стройку пропов")
            refreshHudSnapshot()
            return@synchronized
        }
        buildCategory = category
        val tabProps = PropType.propsForTab(category)
        if (tabProps.isNotEmpty() && selectedBuildProp !in tabProps) {
            selectedBuildProp = tabProps.first()
        }
        updateBuildGhost()
        refreshHudSnapshot()
    }

    fun rotateBuildGhost() = synchronized(stateLock) {
        ghostYawOffset = (ghostYawOffset + 45f) % 360f
        updateBuildGhost()
        soundManager.playBuild3D(ghostX, ghostY, ghostZ)
        refreshHudSnapshot()
    }

    fun toggleBuildGridSnap() = synchronized(stateLock) {
        buildGridSnap = !buildGridSnap
        updateBuildGhost()
        soundManager.playWeaponSwitch()
        refreshHudSnapshot()
    }

    fun selectBuildProp(type: PropType) = synchronized(stateLock) {
        buildCategory = type.buildTab
        selectedBuildProp = type
        updateBuildGhost()
        refreshHudSnapshot()
    }

    fun selectBuildVehicle(vehicleType: VehicleType) = synchronized(stateLock) {
        buildCategory = BuildCategory.VEHICLES
        selectedVehicleType = vehicleType
        updateBuildGhost()
        refreshHudSnapshot()
    }

    fun selectAndSpawnProp(type: PropType) = synchronized(stateLock) {
        buildCategory = type.buildTab
        selectedBuildProp = type
        updateBuildGhost()
        spawnCurrentGhostProp()
        refreshHudSnapshot()
    }

    fun selectAndSpawnVehicle(vehicleType: VehicleType) = synchronized(stateLock) {
        buildCategory = BuildCategory.VEHICLES
        selectedVehicleType = vehicleType
        updateBuildGhost()
        spawnVehicleInFront(vehicleType)
        refreshHudSnapshot()
    }

    /**
     * Spawns any VehicleType cleanly in front of the player (works from HUD, sandbox dock, or code/tests).
     */
    fun spawnVehicle(vehicleType: VehicleType): VehicleEntity? = synchronized(stateLock) {
        if (!playerAlive || activeVehicle != null) return@synchronized null
        if (!canLocalSpawnVehicles()) {
            pushKillFeed("Хост отключил спавн машин")
            refreshHudSnapshot()
            return@synchronized null
        }
        buildCategory = BuildCategory.VEHICLES
        selectedVehicleType = vehicleType
        updateBuildGhost()
        val spawned = spawnVehicleInFront(vehicleType)
        refreshHudSnapshot()
        spawned
    }

    fun placeCurrentGhost() = synchronized(stateLock) {
        if (!playerAlive || activeVehicle != null) return@synchronized
        updateBuildGhost()
        if (!ghostPlacementValid) {
            pushKillFeed("Слишком близко! Отойдите чуть дальше.")
            refreshHudSnapshot()
            return@synchronized
        }
        spawnCurrentGhostProp()
        refreshHudSnapshot()
    }

    fun spawnCurrentGhostProp() = synchronized(stateLock) {
        if (!playerAlive || activeVehicle != null) return@synchronized
        updateBuildGhost()
        if (!ghostPlacementValid) return@synchronized
        if (buildCategory == BuildCategory.VEHICLES) {
            spawnVehicleInFront(selectedVehicleType)
            return@synchronized
        }
        if (buildCategory == BuildCategory.NPCS) {
            // Online rooms are strictly humans-only (no NPC spawning)
            if (isOnlineMode) return@synchronized
            val pig = PigBurgerNpc(
                id = "pig_user_${pigSeq++}",
                name = "Хряк-Бургер #${pigSeq - 1}",
                x = ghostX,
                y = ghostY + 0.1f,
                z = ghostZ,
                yaw = ghostYaw,
                state = PigBurgerState.HAPPY_HOP,
                stateTimer = 1.2f,
                vy = 7.2f,
                grounded = false,
                userPlaced = true
            )
            pigBurgers.add(pig)
            sessionPropsBuilt++
            soundManager.playPigOink3D(ghostX, ghostY + 0.8f, ghostZ, panicked = false)
            spawnDustBurst(ghostX, ghostY + 0.8f, ghostZ, 1.0f, 0.55f, 0.72f, 10)
            pushKillFeed("🐷 Заспавнен Хряк-Бургер!")
            return@synchronized
        }
        val type = selectedBuildProp
        if (type == PropType.CAR) {
            spawnVehicleInFront(selectedVehicleType)
            return@synchronized
        }
        if (!canLocalBuildProps()) {
            pushKillFeed("Хост отключил стройку пропов")
            return@synchronized
        }

        // Unlimited prop spawning (no artificial hard cap)
        props.add(
            WorldProp(
                id = "user_${propSeq++}",
                type = type,
                x = ghostX,
                y = ghostY,
                z = ghostZ,
                yaw = ghostYaw,
                userPlaced = true,
                trapPhaseOffset = Random.nextFloat() * 3.14f
            )
        )
        sessionPropsBuilt++
        soundManager.playBuild3D(ghostX, ghostY, ghostZ)
        spawnDustBurst(ghostX, ghostY + 0.6f, ghostZ, 0.9f, 0.9f, 0.85f, 8)
        onRemotePropSpawned?.invoke(type, ghostX, ghostY, ghostZ, ghostYaw)
    }

    private fun spawnVehicleInFront(vehicleType: VehicleType): VehicleEntity? {
        if (!playerAlive || activeVehicle != null) return null
        if (!canLocalSpawnVehicles()) {
            pushKillFeed("Хост отключил спавн машин")
            return null
        }
        // Unlimited vehicle spawning (no artificial hard cap)
        val entity = VehicleEntity(
            id = "car_user_${propSeq++}",
            type = vehicleType,
            x = ghostX,
            y = ghostY + 0.35f,
            z = ghostZ,
            yaw = ghostYaw
        )
        vehicles.add(entity)
        sessionPropsBuilt++
        soundManager.playHorn3D(ghostX, ghostY + 0.8f, ghostZ, vehicleType = vehicleType)
        spawnDustBurst(ghostX, ghostY + 0.5f, ghostZ, 0.95f, 0.88f, 0.55f, 10)
        pushKillFeed("Заспавнен: ${vehicleType.label}!")
        onRemotePropSpawned?.invoke(PropType.CAR, ghostX, ghostY, ghostZ, ghostYaw)
        return entity
    }

    fun undoLastUserProp() = synchronized(stateLock) {
        if (buildCategory == BuildCategory.VEHICLES) {
            val carIdx = vehicles.indexOfLast { it.id.startsWith("car_user_") && it !== activeVehicle }
            if (carIdx >= 0) {
                val removedCar = vehicles.removeAt(carIdx)
                soundManager.playBuild3D(removedCar.x, removedCar.y + 0.8f, removedCar.z)
                refreshHudSnapshot()
                return@synchronized
            }
        }
        if (buildCategory == BuildCategory.NPCS) {
            val pigIdx = pigBurgers.indexOfLast { it.userPlaced }
            if (pigIdx >= 0) {
                val removedPig = pigBurgers.removeAt(pigIdx)
                soundManager.playPigOink3D(removedPig.x, removedPig.y + 0.8f, removedPig.z, panicked = true)
                refreshHudSnapshot()
                return@synchronized
            }
        }
        val idx = props.indexOfLast { it.userPlaced && !it.destroyed }
        if (idx >= 0) {
            val removed = props.removeAt(idx)
            removed.remoteId?.let {
                destroyedRemotePropIds.add(it)
                onRemotePropDestroyed?.invoke(it)
            }
            soundManager.playBuild3D(removed.x, removed.y + 0.6f, removed.z)
            refreshHudSnapshot()
        }
    }

    fun clearAllUserProps() = synchronized(stateLock) {
        val removedProps = props.filter { it.userPlaced }
        props.removeAll { it.userPlaced }
        removedProps.forEach { prop ->
            prop.remoteId?.let {
                destroyedRemotePropIds.add(it)
                onRemotePropDestroyed?.invoke(it)
            }
        }
        val removedCars = vehicles.filter { it.id.startsWith("car_user_") && it !== activeVehicle }
        vehicles.removeAll { it.id.startsWith("car_user_") && it !== activeVehicle }
        removedCars.forEach { car ->
            car.remoteId?.let {
                destroyedRemotePropIds.add(it)
                onRemotePropDestroyed?.invoke(it)
            }
        }
        pigBurgers.removeAll { it.userPlaced }
        soundManager.playBuild3D(playerX, playerY + 1.0f, playerZ)
        pushKillFeed("Постройки очищены")
        refreshHudSnapshot()
    }

    fun respawnPlayer() = synchronized(stateLock) {
        input.resetMotionControls()
        playerHp = PLAYER_MAX_HP
        playerAlive = true
        hpRegenAccum = 0f
        spawnProtectionTimer = SPAWN_PROTECTION_SEC
        playerStreak = 0
        activeVehicle = null
        val rawX = Random.nextFloat() * 12f - 6f
        val rawZ = 18f + Random.nextFloat() * 6f
        val safeXZ = resolveHorizontalCollision(rawX, 0.3f, rawZ, PLAYER_RADIUS + 0.3f)
        playerX = safeXZ.first
        playerZ = safeXZ.second
        playerY = max(SAFE_SPAWN_Y, getSurfaceHeightAt(playerX, playerZ, 6f) + 1.2f)
        playerVx = 0f
        playerVy = 0f
        playerVz = 0f
        playerGrounded = false
        playerYaw = 0f
        playerPitch = -4f
        aiming = false
        aimBlend = 0f
        buildModeOpen = false
        fireCooldown = 0f
        WeaponType.entries.forEach { weaponAmmo[it] = it.maxAmmo }
        reloadTimer = 0f
        prevFirePressed = false
        damageVignetteAlpha = 0f
        if (cameraMode == CameraMode.FREE_CAM) {
            cameraMode = if (preFreeCamMode == CameraMode.FREE_CAM) CameraMode.FIRST_PERSON else preFreeCamMode
        }
        vehicleCamOrbitYaw = 0f
        vehicleCamOrbitPitch = 16f
        footstepTimer = 0f
        refreshHudSnapshot()
    }

    // --- MULTIPLAYER SYNC HOOKS ---
    fun applyRemotePlayers(list: List<RoomPlayer>) = synchronized(stateLock) {
        val activeIds = mutableSetOf<String>()
        var privilegesChanged = false
        for (rp in list) {
            if (rp.userId == localUserId) {
                val prevGod = personalGodMode
                val prevBuild = personalCanBuildOverride
                if (prevGod != rp.godMode) {
                    personalGodMode = rp.godMode
                    privilegesChanged = true
                    if (rp.godMode) {
                        if (playerAlive) playerHp = PLAYER_MAX_HP
                        hpRegenAccum = 0f
                        pushKillFeed("🛡 Хост выдал вам Режим бога!")
                    } else {
                        pushKillFeed("🛡 Режим бога отключён хостом")
                    }
                }
                if (prevBuild != rp.canBuild) {
                    personalCanBuildOverride = rp.canBuild
                    privilegesChanged = true
                    if (!canLocalBuildProps() && !canLocalSpawnVehicles()) {
                        buildModeOpen = false
                        pushKillFeed("🔨 Хост отключил вам строительство")
                    } else if (rp.canBuild == true) {
                        pushKillFeed("🔨 Хост разрешил вам строительство!")
                    }
                }
                if (spawnProtectionTimer > 0f || isGodModeActive()) continue
                val serverHp = rp.hp.toInt().coerceIn(0, PLAYER_MAX_HP)
                if (serverHp < playerHp && playerAlive) {
                    playerHp = serverHp
                    damageVignetteAlpha = 0.7f
                    soundManager.playPlayerHurt()
                    if (playerHp <= 0) {
                        playerAlive = false
                        hpRegenAccum = 0f
                        activeVehicle = null
                        input.resetMotionControls()
                        pushKillFeed("Вас подбил онлайн-игрок!")
                    }
                }
                continue
            }
            activeIds.add(rp.userId)
            val weapon = WeaponType.entries.firstOrNull { it.name == rp.weapon } ?: WeaponType.RIFLE
            val existing = remotePlayers[rp.userId]
            if (existing == null) {
                remotePlayers[rp.userId] = RemoteChicken(
                    userId = rp.userId,
                    displayName = rp.displayName,
                    targetX = rp.x.toFloat(),
                    targetY = rp.y.toFloat(),
                    targetZ = rp.z.toFloat(),
                    targetYaw = rp.yaw.toFloat(),
                    hp = rp.hp.toInt(),
                    kills = rp.kills.toInt(),
                    weapon = weapon,
                    drivingVehicle = rp.drivingVehicle,
                    shooting = rp.shooting,
                    muzzleFlashTimer = if (rp.shooting) 0.14f else 0f,
                    godMode = rp.godMode
                )
            } else {
                val startedShooting = !existing.shooting && rp.shooting
                val prevHp = existing.hp
                val nextHp = rp.hp.toInt()
                val nextX = rp.x.toFloat()
                val nextY = rp.y.toFloat()
                val nextZ = rp.z.toFloat()
                val nextYaw = rp.yaw.toFloat()
                existing.displayName = rp.displayName
                existing.targetX = nextX
                existing.targetY = nextY
                existing.targetZ = nextZ
                existing.targetYaw = nextYaw
                // Snap immediately on respawn or large teleport instead of sliding across the map
                if ((prevHp <= 0 && nextHp > 0) || hypot2(nextX - existing.x, nextZ - existing.z) > 16f) {
                    existing.x = nextX
                    existing.y = nextY
                    existing.z = nextZ
                    existing.yaw = nextYaw
                }
                existing.hp = nextHp
                existing.kills = rp.kills.toInt()
                existing.weapon = weapon
                existing.drivingVehicle = rp.drivingVehicle
                existing.shooting = rp.shooting
                existing.godMode = rp.godMode
                if (startedShooting && existing.hp > 0) {
                    existing.muzzleFlashTimer = 0.14f
                    soundManager.playWeaponFire3D(
                        weapon = weapon,
                        x = existing.x,
                        y = existing.y + 1.25f,
                        z = existing.z,
                        isLocalPlayer = false
                    )
                }
            }
        }
        val toRemove = remotePlayers.keys.filter { it !in activeIds }
        toRemove.forEach { remotePlayers.remove(it) }
        if (privilegesChanged) {
            refreshHudSnapshot()
        }
    }

    fun applyRemoteProps(list: List<RoomProp>) = synchronized(stateLock) {
        for (rp in list) {
            if (rp.id in destroyedRemotePropIds) continue
            val type = PropType.fromNameSafe(rp.propType)
            if (type == PropType.CAR) {
                val carId = "remote_car_${rp.id}"
                val existingCar = vehicles.firstOrNull { it.id == carId || it.remoteId == rp.id }
                if (rp.destroyed) {
                    destroyedRemotePropIds.add(rp.id)
                    if (existingCar != null && existingCar !== activeVehicle) {
                        vehicles.remove(existingCar)
                    }
                    continue
                }
                if (existingCar == null) {
                    // Link to our own locally spawned vehicle so we never duplicate it!
                    val localMatch = if (rp.ownerId == localUserId) {
                        vehicles.firstOrNull {
                            it.remoteId == null &&
                                it.id.startsWith("car_user_") &&
                                hypot2(it.x - rp.x.toFloat(), it.z - rp.z.toFloat()) < 3.5f
                        }
                    } else null
                    if (localMatch != null) {
                        localMatch.remoteId = rp.id
                    } else {
                        val vType = VehicleType.entries[vehicles.size % VehicleType.entries.size]
                        vehicles.add(
                            VehicleEntity(
                                id = carId,
                                type = vType,
                                x = rp.x.toFloat(),
                                y = rp.y.toFloat(),
                                z = rp.z.toFloat(),
                                yaw = rp.yaw.toFloat(),
                                remoteId = rp.id
                            )
                        )
                    }
                }
                continue
            }
            val existing = props.firstOrNull { it.remoteId == rp.id }
            if (existing == null) {
                if (rp.destroyed) {
                    destroyedRemotePropIds.add(rp.id)
                    continue
                }
                // Match our own freshly placed local prop to prevent duplication on echo snapshot!
                val localMatch = if (rp.ownerId == localUserId) {
                    props.firstOrNull {
                        it.userPlaced && !it.destroyed && it.remoteId == null &&
                            it.type.firestoreType == rp.propType &&
                            hypot2(it.x - rp.x.toFloat(), it.z - rp.z.toFloat()) < 2.0f
                    }
                } else null
                if (localMatch != null) {
                    localMatch.remoteId = rp.id
                } else {
                    props.add(
                        WorldProp(
                            id = "remote_${rp.id}",
                            type = type,
                            x = rp.x.toFloat(),
                            y = rp.y.toFloat(),
                            z = rp.z.toFloat(),
                            yaw = rp.yaw.toFloat(),
                            hp = rp.hp.toInt(),
                            destroyed = false,
                            userPlaced = true,
                            remoteId = rp.id
                        )
                    )
                }
            } else if (rp.destroyed && !existing.destroyed) {
                existing.destroyed = true
                destroyedRemotePropIds.add(rp.id)
                if (existing.type.explosive) {
                    triggerExplosion(existing.x, existing.y + 0.8f, existing.z, 7.5f, 90, fromPlayer = false)
                }
            }
        }
    }

    fun exportLocalPlayerSnapshot(): LocalNetworkState = synchronized(stateLock) {
        val fired = justFiredFlag
        justFiredFlag = false
        LocalNetworkState(
            x = playerX.toDouble(),
            y = playerY.toDouble(),
            z = playerZ.toDouble(),
            yaw = playerYaw.toDouble(),
            pitch = playerPitch.toDouble(),
            hp = playerHp,
            kills = playerKills,
            weapon = currentWeapon.name,
            drivingVehicle = activeVehicle != null,
            shooting = fired
        )
    }

    // --- MAIN FIXED-STEP SIMULATION ---
    fun step(rawDt: Float) = synchronized(stateLock) {
        val dt = rawDt.coerceIn(0.001f, 0.05f)
        val realDt = rawDt.coerceIn(0.001f, 0.5f)

        fpsFrames++
        fpsTimer += realDt
        if (fpsTimer >= 0.45f) {
            currentFps = (fpsFrames / fpsTimer).toInt().coerceIn(1, 144)
            fpsFrames = 0
            fpsTimer = 0f
        }

        // Pause freezes simulation cleanly in local Solo / Peaceful modes!
        if (paused && !isOnlineMode) {
            input.consumeLookDelta()
            input.consumeGyroDelta()
            return@synchronized
        }

        worldTime += dt

        // Decay visual effects & 3s Spawn Protection timer
        spawnProtectionTimer = max(0f, spawnProtectionTimer - dt)
        hitMarkerAlpha = max(0f, hitMarkerAlpha - dt * 3.8f)
        critHitFlashTimer = max(0f, critHitFlashTimer - dt * 2.6f)
        damageVignetteAlpha = max(0f, damageVignetteAlpha - dt * 1.9f)
        screenShake = max(0f, screenShake - dt * 3.2f)
        weaponRecoil = max(0f, weaponRecoil - dt * 3.8f)
        muzzleFlashTimer = max(0f, muzzleFlashTimer - dt)
        fireCooldown = max(0f, fireCooldown - dt)
        landingCameraDip += (0f - landingCameraDip) * min(1f, dt * 11f)
        val targetAim = if (aiming && activeVehicle == null) 1f else 0f
        aimBlend += (targetAim - aimBlend) * min(1f, dt * 14f)
        if (killStreakBannerTimer > 0f) {
            killStreakBannerTimer = max(0f, killStreakBannerTimer - dt)
            if (killStreakBannerTimer <= 0f) killStreakBanner = ""
        }

        // Smooth passive health regeneration: +1 HP every 1.3s while alive and below max HP
        if (playerAlive && playerHp in 1 until PLAYER_MAX_HP) {
            hpRegenAccum += dt
            if (hpRegenAccum >= HP_REGEN_INTERVAL_SEC) {
                val regenTicks = (hpRegenAccum / HP_REGEN_INTERVAL_SEC).toInt()
                hpRegenAccum -= regenTicks * HP_REGEN_INTERVAL_SEC
                playerHp = (playerHp + regenTicks).coerceAtMost(PLAYER_MAX_HP)
                if (playerHp >= PLAYER_MAX_HP) {
                    hpRegenAccum = 0f
                }
            }
        } else {
            hpRegenAccum = 0f
        }

        // Reload timer & RPG rocket node visibility
        if (reloadTimer > 0f) {
            reloadTimer -= dt
            if (reloadTimer <= 0f) {
                weaponAmmo[currentWeapon] = currentWeapon.maxAmmo
                if (currentWeapon == WeaponType.RPG) rpgRocketLoaded = true
            }
        } else if (currentWeapon == WeaponType.RPG && fireCooldown <= 0.15f && (weaponAmmo[WeaponType.RPG] ?: 0) > 0) {
            rpgRocketLoaded = true
        }

        // Race timer & banner progression
        if (raceActive) {
            raceCurrentLapSec += dt
            if (!raceCheckpointPassed && hypot2(playerX - raceArchX, playerZ - raceArchZ) > 32f) {
                raceCheckpointPassed = true
            }
        }
        raceCrossCooldown = max(0f, raceCrossCooldown - dt)
        if (raceBannerTimer > 0f) {
            raceBannerTimer = max(0f, raceBannerTimer - dt)
            if (raceBannerTimer <= 0f) raceBannerText = ""
        }

        // Consume look deltas with user sensitivity, ADS multiplier & Y-inversion settings
        val (lookDx, lookDy) = input.consumeLookDelta()
        val (gyroYawDeg, gyroPitchDeg) = input.consumeGyroDelta()
        val curSettings = settings
        val baseSens = 0.28f * curSettings.lookSensitivity
        val sensitivity = if (aiming) baseSens * curSettings.aimSensitivityMultiplier else baseSens
        val ySign = if (curSettings.invertY) 1f else -1f
        val isFreeCam = cameraMode == CameraMode.FREE_CAM

        when {
            isFreeCam -> {
                freeCamYaw = (freeCamYaw + lookDx * baseSens) % 360f
                freeCamPitch = (freeCamPitch + lookDy * baseSens * ySign).coerceIn(-85f, 85f)
            }
            activeVehicle != null -> {
                // Free 360-degree vehicle orbit: swipe horizontally to orbit around car, vertically to tilt elevation.
                // Chosen orbit angle is preserved while driving with ZERO forced auto-snap back!
                vehicleCamOrbitYaw = normalizeAngle180(vehicleCamOrbitYaw + lookDx * baseSens)
                // Dragging down moves camera higher (looking down at car); dragging up lowers camera toward bumper
                vehicleCamOrbitPitch = (vehicleCamOrbitPitch + lookDy * baseSens * (-ySign)).coerceIn(-8f, 65f)
            }
            else -> {
                playerYaw = (playerYaw + lookDx * sensitivity) % 360f
                playerPitch = (playerPitch + lookDy * sensitivity * ySign).coerceIn(-75f, 75f)
            }
        }

        // Apply calibrated hardware gyroscope fine-aim assistance when on foot (not in free cam or car)
        if (!isFreeCam && activeVehicle == null && curSettings.gyroEnabled && (!curSettings.gyroAimOnly || aiming)) {
            val gyroSens = curSettings.gyroSensitivity * (if (aiming) curSettings.aimSensitivityMultiplier else 1f)
            val gyroXSign = if (curSettings.gyroInvertX) -1f else 1f
            val gyroYSign = if (curSettings.gyroInvertY) -1f else 1f
            playerYaw = (playerYaw + gyroYawDeg * gyroSens * gyroXSign) % 360f
            playerPitch = (playerPitch + gyroPitchDeg * gyroSens * gyroYSign).coerceIn(-75f, 75f)
        }

        if (isFreeCam) {
            updateFreeCamera(dt)
            // Keep standing character damped & grounded while player explores in Free Camera
            if (playerAlive && activeVehicle == null) {
                playerVx *= max(0f, 1f - dt * 10f)
                playerVz *= max(0f, 1f - dt * 10f)
                playerVy -= GRAVITY * dt
                playerY += playerVy * dt
                val groundY = getSurfaceHeightAt(playerX, playerZ, playerY + 0.65f)
                if (playerY <= groundY) {
                    playerY = groundY
                    playerVy = 0f
                    playerGrounded = true
                }
            }
        } else if (playerAlive) {
            if (activeVehicle != null) {
                updatePlayerInVehicle(dt)
            } else {
                updatePlayerOnFoot(dt)
                updatePlayerShooting()
            }
        }

        ensurePropGridFresh()
        updateVehiclesPhysics(dt)
        updateDynamicProps(dt)
        updateInteractivePropsAndTraps(dt)

        // Heavy AI decision/pathfinding/LOS runs at ~25 Hz while movement & animations stay smooth at 120 Hz
        aiDecisionAccum += dt
        val runAiDecisionTick = aiDecisionAccum >= AI_DECISION_INTERVAL
        val aiDt = if (runAiDecisionTick) {
            val stepDt = aiDecisionAccum.coerceAtMost(0.12f)
            aiDecisionAccum = 0f
            stepDt
        } else 0f

        updateBots(dt, runAiDecisionTick, aiDt)
        updatePigBurgers(dt, runAiDecisionTick, aiDt)
        updateRemotePlayersInterpolation(dt)
        updateProjectiles(dt)
        updateExplosionsAndParticles(dt)
        updateDamagePopups(dt)

        // Update 3D spatial audio listener (aligned with active camera: FreeCam, Vehicle Orbit, or Player)
        val drivenCar = activeVehicle
        if (isFreeCam) {
            soundManager.updateListener(freeCamX, freeCamY, freeCamZ, freeCamYaw, freeCamPitch, freeCamVx, freeCamVz)
        } else {
            val listenerY = if (drivenCar != null) drivenCar.y + 1.85f else playerY + PLAYER_EYE_HEIGHT
            val listenerYaw = if (drivenCar != null) (drivenCar.yaw + vehicleCamOrbitYaw) % 360f else playerYaw
            val listenerVx = if (drivenCar != null) sin(drivenCar.yaw * PI.toFloat() / 180f) * drivenCar.speed else playerVx
            val listenerVz = if (drivenCar != null) -cos(drivenCar.yaw * PI.toFloat() / 180f) * drivenCar.speed else playerVz
            soundManager.updateListener(playerX, listenerY, playerZ, listenerYaw, playerPitch, listenerVx, listenerVz)
        }
        soundManager.updateVehicleEngines(drivenCar, vehicles, !isFreeCam && input.gasPressed, !isFreeCam && input.brakePressed)

        if (buildModeOpen && activeVehicle == null && !isFreeCam) {
            updateBuildGhost()
        }

        nearestVehicle = if (activeVehicle == null && !isFreeCam) {
            findNearestVehicle(playerX, playerZ, 6.0f)
        } else null

        nearestPigBurger = if (activeVehicle == null && !isFreeCam && playerAlive) {
            findNearestPigBurger(playerX, playerZ, 4.0f)
        } else null

        nearestDoorProp = if (!isFreeCam && playerAlive) {
            val refX = drivenCar?.x ?: playerX
            val refZ = drivenCar?.z ?: playerZ
            findNearestDoorProp(refX, refZ, if (drivenCar != null) 9.8f else 6.8f)
        } else null

        // Refresh HUD faster (~30 Hz) while floating damage numbers, reload, race timer, or vehicle orbit changes
        val hudInterval = if (damagePopups3D.isNotEmpty() || reloadTimer > 0f || raceActive || drivenCar != null) 0.033f else 0.06f
        hudAccum += dt
        if (hudAccum >= hudInterval) {
            hudAccum = 0f
            refreshHudSnapshot()
        }
    }

    private fun updateFreeCamera(dt: Float) {
        val yawRad = freeCamYaw * PI.toFloat() / 180f
        val pitchRad = freeCamPitch * PI.toFloat() / 180f
        val fwdX = sin(yawRad) * cos(pitchRad)
        val fwdY = sin(pitchRad)
        val fwdZ = -cos(yawRad) * cos(pitchRad)
        val rightX = cos(yawRad)
        val rightZ = sin(yawRad)

        val mx = input.moveX.coerceIn(-1f, 1f)
        val my = input.moveY.coerceIn(-1f, 1f)
        val vertInput = (if (input.flyUp) 1f else 0f) - (if (input.flyDown) 1f else 0f)
        val flySpeed = 26f

        val targetVx = (fwdX * my + rightX * mx) * flySpeed
        val targetVy = (fwdY * my + vertInput) * flySpeed
        val targetVz = (fwdZ * my + rightZ * mx) * flySpeed

        val smooth = min(1f, dt * 9.5f)
        freeCamVx += (targetVx - freeCamVx) * smooth
        freeCamVy += (targetVy - freeCamVy) * smooth
        freeCamVz += (targetVz - freeCamVz) * smooth

        val limit = ARENA_HALF_SIZE - 1.5f
        freeCamX = (freeCamX + freeCamVx * dt).coerceIn(-limit, limit)
        freeCamZ = (freeCamZ + freeCamVz * dt).coerceIn(-limit, limit)
        val groundFloor = getSurfaceHeightAt(freeCamX, freeCamZ, freeCamY + 2f) + 0.75f
        freeCamY = (freeCamY + freeCamVy * dt).coerceIn(groundFloor, 120f)
    }

    private fun normalizeAngle180(deg: Float): Float {
        var a = deg % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }

    private fun updatePlayerOnFoot(dt: Float) {
        val rad = playerYaw * PI.toFloat() / 180f
        val fwdX = sin(rad)
        val fwdZ = -cos(rad)
        val rightX = cos(rad)
        val rightZ = sin(rad)

        val mx = input.moveX.coerceIn(-1f, 1f)
        val my = input.moveY.coerceIn(-1f, 1f)
        val mag = sqrt(mx * mx + my * my)
        val sprinting = !aiming && settings.autoSprint && mag > 0.78f && my > 0.25f
        val moveSpeed = when {
            aiming -> 6.6f
            sprinting -> 13.4f
            mag > 0.85f -> 11.6f
            else -> 9.6f
        }

        val desiredVx = (fwdX * my + rightX * mx) * moveSpeed
        val desiredVz = (fwdZ * my + rightZ * mx) * moveSpeed

        // Crisp ground response + controllable mid-air maneuvering
        val blend = if (playerGrounded) min(1f, dt * 15f) else min(1f, dt * 5.2f)
        playerVx += (desiredVx - playerVx) * blend
        playerVz += (desiredVz - playerVz) * blend

        // Coyote time & jump buffering so jumping on touch controls feels instant and reliable
        if (playerGrounded) {
            coyoteTimer = 0.12f
        } else {
            coyoteTimer = max(0f, coyoteTimer - dt)
        }
        if (input.jumpQueued) {
            input.jumpQueued = false
            jumpBufferTimer = 0.14f
        } else {
            jumpBufferTimer = max(0f, jumpBufferTimer - dt)
        }
        if (jumpBufferTimer > 0f && coyoteTimer > 0f) {
            playerVy = 12.1f
            playerGrounded = false
            coyoteTimer = 0f
            jumpBufferTimer = 0f
            soundManager.playJump3D(playerX, playerY, playerZ, isLocalPlayer = true)
        }

        val wasGrounded = playerGrounded
        val impactVy = playerVy
        playerVy -= GRAVITY * dt

        val nextX = clampArena(playerX + playerVx * dt)
        val nextZ = clampArena(playerZ + playerVz * dt)
        val previewSurface = getSurfaceHeightExcluding(nextX, nextZ, playerY + 0.65f, null, isRising = playerVy > 1.5f)
        // Smooth step-up support (allows walking & jumping smoothly onto ramps and under arches/bridges)
        resolveHorizontalCollisionIntoScratch(nextX, max(playerY, previewSurface) + 0.20f, nextZ, PLAYER_RADIUS)
        playerX = scratchResX
        playerZ = scratchResZ

        val prevPlayerY = playerY
        playerY += playerVy * dt
        if (playerVy > 0f) {
            val ceilingY = getCeilingHeight(playerX, playerZ, prevPlayerY)
            if (ceilingY < Float.MAX_VALUE && playerY + 1.75f > ceilingY) {
                playerY = max(0f, ceilingY - 1.75f)
                playerVy = 0f
            }
        }
        val groundY = getSurfaceHeightExcluding(playerX, playerZ, playerY + 0.65f, null, isRising = playerVy > 1.5f)
        if (playerY <= groundY) {
            playerY = groundY
            if (!wasGrounded && impactVy < -5.8f) {
                landingCameraDip = (-abs(impactVy) * 0.018f).coerceIn(-0.24f, -0.04f)
                soundManager.playLanding3D(playerX, playerY, playerZ, abs(impactVy), isLocalPlayer = true)
            }
            playerVy = 0f
            playerGrounded = true
        } else if (playerY > groundY + 0.25f) {
            playerGrounded = false
        }

        val horizSpeed = sqrt(playerVx * playerVx + playerVz * playerVz)
        val targetSpread = ((horizSpeed / 13f) * 0.45f + weaponRecoil * 1.4f + if (!playerGrounded) 0.45f else 0f) * (1f - aimBlend * 0.72f)
        dynamicSpread += (targetSpread - dynamicSpread) * min(1f, dt * 12f)

        if (playerGrounded && horizSpeed > 1.2f) {
            playerWalkCycle += dt * horizSpeed * 1.45f
            footstepTimer -= dt
            if (footstepTimer <= 0f) {
                footstepTimer = if (sprinting) 0.27f else if (aiming) 0.44f else 0.34f
                soundManager.playFootstep3D(playerX, playerY, playerZ, sprinting = sprinting, isLocalPlayer = true)
            }
        } else {
            footstepTimer = min(footstepTimer, 0.08f)
        }
    }

    private fun updatePlayerInVehicle(dt: Float) {
        val car = activeVehicle ?: return
        val spec = car.type
        val accel = spec.accel
        val maxSpeed = if (car.nitroTimer > 0f) spec.turboSpeed else spec.maxSpeed
        val reverseMax = -spec.maxSpeed * 0.42f

        if (input.turboQueued) {
            input.turboQueued = false
            if (spec == VehicleType.BATTLE_TANK && fireCooldown <= 0f) {
                // Battle Tank fires an explosive cannon shell aligned with the rendered turret!
                fireCooldown = 1.2f
                car.cannonFlashTimer = 0.14f
                car.pitchAngle = (car.pitchAngle + 5.5f).coerceAtMost(14f)
                val aimRad = (car.yaw + car.turretYaw) * PI.toFloat() / 180f
                val fx = sin(aimRad)
                val fz = -cos(aimRad)
                projectiles.add(
                    Projectile(
                        x = car.x + fx * 3.8f,
                        y = car.y + 1.85f,
                        z = car.z + fz * 3.8f,
                        vx = fx * 56f,
                        vy = 2.4f,
                        vz = fz * 56f,
                        damage = 115,
                        fromPlayer = true,
                        explosive = true,
                        life = 3.5f
                    )
                )
                soundManager.playTankCannon3D(
                    x = car.x + fx * 3.8f,
                    y = car.y + 1.85f,
                    z = car.z + fz * 3.8f,
                    isLocalPlayer = true
                )
                hapticController.onTankCannonRecoil()
                screenShake = max(screenShake, 0.35f)
            } else if (car.nitroTimer <= 0f) {
                car.nitroTimer = 1.8f
                car.speed = max(car.speed, spec.maxSpeed * 0.85f)
                car.pitchAngle = (car.pitchAngle + 6.5f).coerceAtMost(14f)
                soundManager.playHorn3D(car.x, car.y + 1.0f, car.z, isLocalPlayer = true, vehicleType = spec)
                scareNearbyPigBurgers(car.x, car.z, 18f)
            }
        }
        car.nitroTimer = max(0f, car.nitroTimer - dt)

        val prevSpeed = car.speed
        when {
            input.gasPressed -> car.speed = min(maxSpeed, car.speed + accel * dt)
            input.brakePressed -> {
                if (car.speed > 1.5f) {
                    car.speed = max(0f, car.speed - 52f * dt)
                } else {
                    car.speed = max(reverseMax, car.speed - 22f * dt)
                }
            }
            else -> {
                val drag = 13f * dt
                car.speed = when {
                    abs(car.speed) <= drag -> 0f
                    car.speed > 0f -> car.speed - drag
                    else -> car.speed + drag
                }
            }
        }

        val steerInput = (if (input.steerRight) 1f else 0f) - (if (input.steerLeft) 1f else 0f)
        car.steerAngle += (steerInput * 28f - car.steerAngle) * min(1f, dt * 12f)
        if (abs(car.speed) > 0.5f) {
            val turnDir = if (car.speed >= 0f) 1f else -1f
            val speedFactor = (abs(car.speed) / 14f).coerceIn(0.35f, 1.15f)
            car.yaw = (car.yaw + steerInput * spec.turnRate * speedFactor * turnDir * dt) % 360f
            // Subtle drift slip velocity when turning aggressively at high speed
            if (car.grounded && abs(car.speed) > 14f && abs(steerInput) > 0.1f && spec != VehicleType.BATTLE_TANK) {
                val targetSlip = -steerInput * (abs(car.speed) * 0.18f) * turnDir
                car.lateralVel += (targetSlip - car.lateralVel) * min(1f, dt * 5.5f)
            }
        }
        if (spec == VehicleType.BATTLE_TANK) {
            if (steerInput != 0f) {
                car.turretYaw = (car.turretYaw + steerInput * 28f * dt).coerceIn(-35f, 35f)
            } else {
                car.turretYaw += (0f - car.turretYaw) * min(1f, dt * 3.5f)
            }
        }

        // Dynamic body pitch (squat under accel, dive under braking) & roll (lean outward in turns)
        val longAccel = (car.speed - prevSpeed) / max(0.004f, dt)
        val targetPitch = if (!car.grounded) {
            (car.vy * 1.35f).coerceIn(-16f, 20f)
        } else {
            (longAccel * 0.14f + if (car.nitroTimer > 0f) 3.8f else 0f).coerceIn(-8f, 9f)
        }
        val rollFactor = if (spec == VehicleType.MONSTER_TRUCK) 1.35f else if (spec == VehicleType.BATTLE_TANK) 0.45f else 1.0f
        val targetRoll = if (car.grounded) {
            (-steerInput * (car.speed / spec.maxSpeed).coerceIn(-1f, 1f) * 7.5f * rollFactor).coerceIn(-11f, 11f)
        } else 0f
        car.pitchAngle += (targetPitch - car.pitchAngle) * min(1f, dt * 8.5f)
        car.rollAngle += (targetRoll - car.rollAngle) * min(1f, dt * 9.5f)

        // Keep playerYaw aligned with the driver's chosen free-orbit camera direction without forcing snap-back!
        playerYaw = (car.yaw + vehicleCamOrbitYaw) % 360f
        playerPitch = (-vehicleCamOrbitPitch * 0.5f).coerceIn(-35f, 30f)

        // Nitro flame & tire drift dust particles (scaled by particleDensity for low-end devices)
        val rad = car.yaw * PI.toFloat() / 180f
        val backX = car.x - sin(rad) * (spec.halfL + 0.15f)
        val backZ = car.z + cos(rad) * (spec.halfL + 0.15f)
        val partChance = settings.particleDensity.coerceIn(0.35f, 1.2f)
        if (car.nitroTimer > 0f && Random.nextFloat() < 0.78f * partChance) {
            particles.add(
                Particle3D(
                    x = backX, y = car.y + 0.55f, z = backZ,
                    vx = -sin(rad) * 7f + (Random.nextFloat() - 0.5f) * 2f,
                    vy = 1.6f,
                    vz = cos(rad) * 7f + (Random.nextFloat() - 0.5f) * 2f,
                    size = 0.36f, age = 0f, maxAge = 0.24f,
                    r = 1f, g = 0.52f, b = 0.10f
                )
            )
        } else if (car.grounded && (abs(car.speed) > 13f || abs(car.lateralVel) > 2.2f) && Random.nextFloat() < 0.42f * partChance) {
            val drifting = abs(car.lateralVel) > 2.0f
            particles.add(
                Particle3D(
                    x = backX + (Random.nextFloat() - 0.5f) * spec.halfW,
                    y = car.y + 0.18f,
                    z = backZ + (Random.nextFloat() - 0.5f) * spec.halfW,
                    vx = -sin(rad) * 2.2f + (Random.nextFloat() - 0.5f) * 1.8f,
                    vy = 1.2f + Random.nextFloat() * 1.3f,
                    vz = cos(rad) * 2.2f + (Random.nextFloat() - 0.5f) * 1.8f,
                    size = if (drifting) 0.36f else 0.26f, age = 0f, maxAge = 0.36f,
                    r = if (drifting) 0.88f else 0.78f,
                    g = if (drifting) 0.88f else 0.75f,
                    b = if (drifting) 0.90f else 0.68f
                )
            )
        }
    }

    private fun updateVehiclesPhysics(dt: Float) {
        for (i in vehicles.indices) {
            val car = vehicles[i]
            val isDriven = (car === activeVehicle)
            car.cannonFlashTimer = max(0f, car.cannonFlashTimer - dt)
            car.collisionSoundCooldown = max(0f, car.collisionSoundCooldown - dt)

            // Active 360° Loop-the-Loop stunt ride physics!
            if (car.loopPropId != null) {
                updateVehicleInLoop(car, dt, isDriven)
                continue
            }
            if (!isDriven) {
                if (abs(car.speed) > 0.05f) {
                    val drag = 18f * dt
                    car.speed = if (abs(car.speed) <= drag) 0f else car.speed - drag * if (car.speed > 0f) 1f else -1f
                }
                car.pitchAngle += (0f - car.pitchAngle) * min(1f, dt * 6f)
                car.rollAngle += (0f - car.rollAngle) * min(1f, dt * 6f)
            }
            // Lateral grip damping
            car.lateralVel *= max(0f, 1f - dt * 6.5f)

            // Spring-damped suspension physics (bounces on ramp landings & impacts!)
            val springForce = -52f * car.suspensionOffset - 9.5f * car.suspensionVel
            car.suspensionVel += springForce * dt
            car.suspensionOffset = (car.suspensionOffset + car.suspensionVel * dt).coerceIn(-0.32f, 0.28f)

            val rad = car.yaw * PI.toFloat() / 180f
            val fwdX = sin(rad)
            val fwdZ = -cos(rad)
            val rightX = cos(rad)
            val rightZ = sin(rad)

            val prevX = car.x
            val prevZ = car.z
            val prevSurface = getSurfaceHeightExcluding(car.x, car.z, car.y + 0.85f, null, isRising = car.vy > 1.5f)
            val nx = car.x + (fwdX * car.speed + rightX * car.lateralVel) * dt
            val nz = car.z + (fwdZ * car.speed + rightZ * car.lateralVel) * dt

            // Preview climbable ramp surface BEFORE wall collision so fast cars never slam into invisible walls at ramp/arch entries!
            val aheadSurface = getSurfaceHeightExcluding(nx, nz, car.y + 1.15f, null, isRising = car.vy > 1.5f)
            resolveVehicleCollisionIntoScratch(nx, max(car.y, aheadSurface), nz, car.type.halfW)
            car.x = scratchResX
            car.z = scratchResZ
            val hitWall = scratchHitWall
            if (isDriven) {
                sessionDriveMeters += hypot2(car.x - prevX, car.z - prevZ)
            }
            if (hitWall) {
                val impactSpeed = abs(car.speed)
                car.speed *= 0.38f
                car.lateralVel *= 0.3f
                if (abs(car.speed) < 2.2f) car.speed = 0f
                if (impactSpeed > 5.5f) {
                    car.suspensionVel -= min(2.4f, impactSpeed * 0.08f)
                    if (car.collisionSoundCooldown <= 0f) {
                        car.collisionSoundCooldown = 0.16f
                        soundManager.playVehicleCollision3D(
                            x = car.x,
                            y = car.y + 0.8f,
                            z = car.z,
                            impactSpeed = impactSpeed,
                            vehicleType = car.type,
                            isLanding = false
                        )
                    }
                    if (isDriven) {
                        hapticController.onVehicleCollision(impactSpeed, car.type, isLanding = false)
                    }
                    spawnDustBurst(car.x + fwdX * car.type.halfL * 0.7f, car.y + 0.7f, car.z + fwdZ * car.type.halfL * 0.7f, 1f, 0.82f, 0.35f, 5)
                }
                if (isDriven && impactSpeed > 8f) screenShake = max(screenShake, 0.24f)
            }

            // Smooth ramp climb & stunt crest launch + vertical gravity (never snaps upward through overpass ceilings!)
            val nextSurface = getSurfaceHeightExcluding(car.x, car.z, car.y + 1.05f, null, isRising = car.vy > 1.5f)
            if (car.grounded && nextSurface > prevSurface + 0.015f && abs(car.speed) > 4.5f) {
                val slopeRise = (nextSurface - prevSurface) / max(0.05f, abs(car.speed) * dt)
                car.vy = max(car.vy, abs(car.speed) * slopeRise.coerceIn(0.22f, 0.68f) * car.type.jumpBoost)
            }

            val wasAirborne = !car.grounded
            val impactVy = car.vy
            val prevCarY = car.y
            car.vy -= GRAVITY * dt
            car.y += car.vy * dt
            if (car.vy > 0f) {
                val ceilingY = getCeilingHeight(car.x, car.z, prevCarY)
                val roofHeight = 2.1f
                if (ceilingY < Float.MAX_VALUE && car.y + roofHeight > ceilingY) {
                    car.y = max(0f, ceilingY - roofHeight)
                    car.vy = 0f
                }
            }
            if (car.y <= nextSurface) {
                car.y = nextSurface
                if (wasAirborne && impactVy < -5.2f) {
                    car.suspensionVel += (impactVy * 0.24f).coerceIn(-3.6f, -0.5f)
                    if (impactVy < -8.0f && car.collisionSoundCooldown <= 0f) {
                        car.collisionSoundCooldown = 0.18f
                        if (isDriven) {
                            hapticController.onVehicleCollision(abs(impactVy), car.type, isLanding = true)
                            if (impactVy < -10f) screenShake = max(screenShake, 0.24f)
                        }
                        soundManager.playVehicleCollision3D(
                            x = car.x,
                            y = car.y + 0.45f,
                            z = car.z,
                            impactSpeed = abs(impactVy),
                            vehicleType = car.type,
                            isLanding = true
                        )
                    }
                }
                car.vy = 0f
                car.grounded = true
            } else if (car.y > nextSurface + 0.35f) {
                car.grounded = false
            }

            car.wheelSpin = (car.wheelSpin + car.speed * dt * 115f) % 360f

            // Sync live RPM & automatic gear state for vehicle HUD + telemetry
            val audioMetrics = VehicleAudioCatalog.computeMetrics(
                type = car.type,
                speed = car.speed,
                maxSpeed = car.type.maxSpeed,
                nitroActive = car.nitroTimer > 0f,
                gasPressed = if (isDriven) input.gasPressed else abs(car.speed) > 2.5f,
                brakePressed = if (isDriven) input.brakePressed else false,
                lateralSpeed = abs(car.lateralVel),
                grounded = car.grounded
            )
            car.currentRpm += (audioMetrics.rpm - car.currentRpm) * min(1f, dt * 12f)
            car.currentGear = audioMetrics.gear

            // Ramming bots, remote players, barrels, crates & targets
            val absSpeed = abs(car.speed)
            if (absSpeed > 1.8f) {
                if (absSpeed > 6.5f && !peacefulMode) {
                    for (bot in bots) {
                        if (!bot.alive) continue
                        val dist = hypot2(car.x - bot.x, car.z - bot.z)
                        if (dist < car.type.halfW + 1.0f && abs(car.y - bot.y) < 2.2f) {
                            bot.vx = fwdX * absSpeed * 1.25f
                            bot.vz = fwdZ * absSpeed * 1.25f
                            bot.vy = 11.2f
                            if (car.collisionSoundCooldown <= 0f) {
                                car.collisionSoundCooldown = 0.14f
                                soundManager.playVehicleCollision3D(bot.x, bot.y + 0.9f, bot.z, absSpeed, car.type)
                            }
                            if (isDriven) hapticController.onVehicleRam(absSpeed, car.type)
                            damageBot(bot, 100, byPlayer = isDriven, viaVehicle = true, damageKind = DamageKind.VEHICLE_RAM)
                        }
                    }
                    if (isDriven && canDamageRemotePlayers()) {
                        for (rp in remotePlayers.values) {
                            if (rp.hp <= 0 || rp.godMode || rp.ramCooldown > 0f) continue
                            val dist = hypot2(car.x - rp.x, car.z - rp.z)
                            if (dist < car.type.halfW + 1.05f && abs(car.y - rp.y) < 2.2f) {
                                rp.ramCooldown = 0.85f
                                val dmg = (absSpeed * 3.8f).toInt().coerceIn(30, 100)
                                val newHp = (rp.hp - dmg).coerceAtLeast(0)
                                rp.hp = newHp
                                rp.hitFlash = 1f
                                hitMarkerAlpha = 1f
                                spawnDamagePopup(rp.x, rp.y + 2.15f, rp.z, dmg, DamageKind.VEHICLE_RAM)
                                soundManager.playVehicleCollision3D(rp.x, rp.y + 0.9f, rp.z, absSpeed, car.type)
                                hapticController.onVehicleRam(absSpeed, car.type)
                                onRemotePlayerDamaged?.invoke(rp.userId, newHp)
                                if (newHp == 0) {
                                    recordPlayerKill(viaVehicle = true, targetName = rp.displayName, verb = "Вы сбили", suffix = " машиной!")
                                }
                            }
                        }
                    }
                }
                // Ramming interactive props: Barrels, Crates, Targets, Fragile Brick Walls, Bowling Pins & Tire Stacks!
                val propCount = props.size
                for (pIdx in 0 until propCount) {
                    val prop = props[pIdx]
                    if (prop.destroyed) continue
                    val dist = hypot2(car.x - prop.x, car.z - prop.z)
                    when (prop.type) {
                        PropType.BARREL, PropType.GAS_BARREL, PropType.WATER_BARREL, PropType.CRATE, PropType.TARGET -> {
                            if (dist < car.type.halfW + 0.95f && abs(car.y - prop.y) < 1.8f) {
                                if ((prop.type.explosive || prop.type.isWaterBarrel) && absSpeed > 5.5f) {
                                    if (isDriven) hapticController.onVehicleRam(absSpeed, car.type)
                                    destroyProp(prop, fromPlayer = isDriven)
                                } else {
                                    val knockDir = if (car.speed >= 0f) 1f else -1f
                                    prop.vx = fwdX * knockDir * max(4.5f, absSpeed * 1.1f)
                                    prop.vz = fwdZ * knockDir * max(4.5f, absSpeed * 1.1f)
                                    prop.vy = if (absSpeed > 6.5f) 7.5f else 2.2f
                                    if (absSpeed > 6.5f) {
                                        if (car.collisionSoundCooldown <= 0f) {
                                            car.collisionSoundCooldown = 0.14f
                                            soundManager.playVehicleCollision3D(prop.x, prop.y + 0.8f, prop.z, absSpeed, car.type)
                                        }
                                        if (isDriven) hapticController.onVehicleRam(absSpeed, car.type)
                                        damageProp(prop, 35, fromPlayer = isDriven)
                                    }
                                }
                            }
                        }
                        PropType.FRAGILE_BRICK_WALL -> {
                            if (absSpeed > 9.5f && dist < car.type.halfW + 2.1f && abs(car.y - prop.y) < 2.4f) {
                                destroyProp(prop, fromPlayer = isDriven)
                                car.speed *= 0.84f
                                if (isDriven) {
                                    hapticController.onVehicleCollision(absSpeed, car.type, isLanding = false)
                                    screenShake = max(screenShake, 0.28f)
                                }
                            }
                        }
                        PropType.BOWLING_PIN -> {
                            if (dist < car.type.halfW + 0.85f && abs(car.y - prop.y) < 2.2f) {
                                val knockDir = if (car.speed >= 0f) 1f else -1f
                                val sideKick = (Random.nextFloat() - 0.5f) * 6.5f
                                prop.vx = fwdX * knockDir * max(8.5f, absSpeed * 1.35f) + rightX * sideKick
                                prop.vz = fwdZ * knockDir * max(8.5f, absSpeed * 1.35f) + rightZ * sideKick
                                prop.vy = max(prop.vy, 5.5f + absSpeed * 0.22f)
                                prop.tiltVel = (if (Random.nextBoolean()) 1f else -1f) * (240f + absSpeed * 10f)
                                if (car.collisionSoundCooldown <= 0f) {
                                    car.collisionSoundCooldown = 0.12f
                                    soundManager.playBowlingStrike3D(prop.x, prop.y + 1.0f, prop.z, (absSpeed / 22f).coerceIn(0.6f, 1.3f))
                                }
                                spawnDustBurst(prop.x, prop.y + 1.0f, prop.z, 1.0f, 0.94f, 0.72f, 5)
                            }
                        }
                        PropType.TIRE_STACK -> {
                            if (dist < car.type.halfW + 1.05f && abs(car.y - prop.y) < 1.8f) {
                                val knockDir = if (car.speed >= 0f) 1f else -1f
                                prop.vx = fwdX * knockDir * max(5.0f, absSpeed * 0.9f)
                                prop.vz = fwdZ * knockDir * max(5.0f, absSpeed * 0.9f)
                                prop.vy = 4.2f
                                car.speed *= 0.72f
                                car.suspensionVel -= 1.4f
                                if (isDriven && absSpeed > 5.5f) hapticController.onVehicleRam(absSpeed * 0.8f, car.type)
                            }
                        }
                        else -> Unit
                    }
                }

                // Ramming Pig-Burgers sends them flying & drops tasty food loot!
                if (absSpeed > 5.5f) {
                    for (pig in pigBurgers) {
                        if (pig.knockbackTimer > 0f) continue
                        val dist = hypot2(car.x - pig.x, car.z - pig.z)
                        if (dist < car.type.halfW + 0.95f && abs(car.y - pig.y) < 2.0f) {
                            val knockDir = if (car.speed >= 0f) 1f else -1f
                            hitPigBurger(
                                pig = pig,
                                impulseX = fwdX * knockDir * max(11f, absSpeed * 1.15f),
                                impulseZ = fwdZ * knockDir * max(11f, absSpeed * 1.15f),
                                upVy = 11.5f,
                                dropLoot = true
                            )
                        }
                    }
                }
            }
        }

        // Elastic vehicle-to-vehicle separation with true 2D vector relative impact velocity
        for (i in 0 until vehicles.size) {
            val a = vehicles[i]
            val aRad = a.yaw * PI.toFloat() / 180f
            val aFwdX = sin(aRad)
            val aFwdZ = -cos(aRad)
            val aVx = aFwdX * a.speed
            val aVz = aFwdZ * a.speed
            for (j in i + 1 until vehicles.size) {
                val b = vehicles[j]
                if (abs(a.y - b.y) > 2.2f) continue
                val dx = b.x - a.x
                val dz = b.z - a.z
                val minDist = (a.type.halfW + b.type.halfW) * 1.15f
                if (abs(dx) >= minDist || abs(dz) >= minDist) continue
                val distSq = dx * dx + dz * dz
                if (distSq >= minDist * minDist) continue
                val dist = sqrt(distSq).coerceAtLeast(0.001f)
                if (dist < minDist) {
                    val push = (minDist - dist) * 0.52f
                    val nx = dx / dist
                    val nz = dz / dist
                    a.x = clampVehicle(a.x - nx * push, a.type.halfW)
                    a.z = clampVehicle(a.z - nz * push, a.type.halfW)
                    b.x = clampVehicle(b.x + nx * push, b.type.halfW)
                    b.z = clampVehicle(b.z + nz * push, b.type.halfW)

                    val bRad = b.yaw * PI.toFloat() / 180f
                    val bFwdX = sin(bRad)
                    val bFwdZ = -cos(bRad)
                    val bVx = bFwdX * b.speed
                    val bVz = bFwdZ * b.speed
                    val relSpeed = hypot2(aVx - bVx, aVz - bVz)

                    if (relSpeed > 4.5f) {
                        val midX = (a.x + b.x) * 0.5f
                        val midZ = (a.z + b.z) * 0.5f
                        val primaryType = if (a === activeVehicle) a.type else if (b === activeVehicle) b.type else a.type
                        if (a.collisionSoundCooldown <= 0f || b.collisionSoundCooldown <= 0f) {
                            a.collisionSoundCooldown = 0.16f
                            b.collisionSoundCooldown = 0.16f
                            soundManager.playVehicleCollision3D(
                                x = midX,
                                y = max(a.y, b.y) + 0.8f,
                                z = midZ,
                                impactSpeed = relSpeed,
                                vehicleType = primaryType,
                                isLanding = false
                            )
                        }
                        a.suspensionVel -= min(2.2f, relSpeed * 0.08f)
                        b.suspensionVel -= min(2.2f, relSpeed * 0.08f)
                        if (a === activeVehicle || b === activeVehicle) {
                            hapticController.onVehicleCollision(relSpeed, primaryType, isLanding = false)
                            screenShake = max(screenShake, (relSpeed / 45f).coerceIn(0.10f, 0.28f))
                        }
                    }

                    // Realistic directional momentum transfer along each car's heading
                    val approachSpeed = ((aVx - bVx) * nx + (aVz - bVz) * nz)
                    if (approachSpeed > 0.5f) {
                        val impulse = approachSpeed * 0.55f
                        val aAlign = aFwdX * nx + aFwdZ * nz
                        val bAlign = bFwdX * nx + bFwdZ * nz
                        a.speed = (a.speed - impulse * aAlign).coerceIn(-a.type.maxSpeed * 0.5f, a.type.turboSpeed)
                        b.speed = (b.speed + impulse * bAlign).coerceIn(-b.type.maxSpeed * 0.5f, b.type.turboSpeed)
                    }
                }
            }
        }

        // Keep seated player synced with active vehicle after all collisions
        activeVehicle?.let { car ->
            playerX = car.x
            playerY = car.y + 0.55f + car.suspensionOffset
            playerZ = car.z
        }
    }

    private fun updatePlayerShooting() {
        val triggerHeld = input.firing
        val shouldFire = if (currentWeapon.automatic) {
            triggerHeld
        } else {
            triggerHeld && !prevFirePressed
        }
        prevFirePressed = triggerHeld

        if (!shouldFire || fireCooldown > 0f || reloadTimer > 0f) return

        val infiniteAmmo = isInfiniteAmmoActive()
        val ammo = weaponAmmo[currentWeapon] ?: 0
        if (!infiniteAmmo && ammo <= 0) {
            reloadTimer = currentWeapon.reloadSec
            soundManager.playReload()
            return
        }

        if (!infiniteAmmo) {
            weaponAmmo[currentWeapon] = ammo - 1
        }
        fireCooldown = currentWeapon.fireIntervalSec
        weaponRecoil = min(0.45f, weaponRecoil + currentWeapon.recoilKick)
        muzzleFlashTimer = 0.065f
        justFiredFlag = true

        val yawRad = playerYaw * PI.toFloat() / 180f
        val pitchRad = playerPitch * PI.toFloat() / 180f
        val spread = if (aiming) 0.005f else 0.020f
        val dirX = (sin(yawRad) * cos(pitchRad)) + (Random.nextFloat() - 0.5f) * spread
        val dirY = sin(pitchRad) + (Random.nextFloat() - 0.5f) * spread
        val dirZ = (-cos(yawRad) * cos(pitchRad)) + (Random.nextFloat() - 0.5f) * spread
        val len = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ).coerceAtLeast(0.001f)
        val nx = dirX / len
        val ny = dirY / len
        val nz = dirZ / len

        // Spawn slightly in front of eye (0.38f < wall halfD 0.48f) so standing flush against a wall cannot shoot through it
        val muzzleX = if (cameraMode == CameraMode.THIRD_PERSON && rendererMuzzleValid) rendererMuzzleX else playerX + nx * 0.38f
        val muzzleY = if (cameraMode == CameraMode.THIRD_PERSON && rendererMuzzleValid) rendererMuzzleY else playerY + PLAYER_EYE_HEIGHT - 0.10f + ny * 0.38f
        val muzzleZ = if (cameraMode == CameraMode.THIRD_PERSON && rendererMuzzleValid) rendererMuzzleZ else playerZ + nz * 0.38f
        soundManager.playWeaponFire3D(currentWeapon, muzzleX, muzzleY, muzzleZ, isLocalPlayer = true)
        hapticController.onWeaponRecoil(currentWeapon)
        scareNearbyPigBurgers(playerX, playerZ, 22f)

        projectiles.add(
            Projectile(
                x = muzzleX,
                y = muzzleY,
                z = muzzleZ,
                vx = nx * currentWeapon.projectileSpeed,
                vy = ny * currentWeapon.projectileSpeed,
                vz = nz * currentWeapon.projectileSpeed,
                damage = currentWeapon.damage,
                fromPlayer = true,
                explosive = currentWeapon.explosive,
                life = if (currentWeapon.explosive) 4.0f else 1.5f
            )
        )

        if (currentWeapon == WeaponType.RPG) {
            rpgRocketLoaded = false
            screenShake = max(screenShake, 0.22f)
        }
        if (!infiniteAmmo && (weaponAmmo[currentWeapon] ?: 0) == 0) {
            reloadTimer = currentWeapon.reloadSec
            soundManager.playReload()
        }
    }

    private fun updateBots(dt: Float, runAiDecisionTick: Boolean, aiDt: Float) {
        for (i in bots.indices) {
            val bot = bots[i]
            if (!bot.alive) {
                bot.respawnTimer -= dt
                if (bot.respawnTimer <= 0f) {
                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                    val dist = 30f + Random.nextFloat() * 32f
                    val rawX = clampArena(playerX + cos(angle) * dist)
                    val rawZ = clampArena(playerZ + sin(angle) * dist)
                    resolveHorizontalCollisionIntoScratch(rawX, 0.2f, rawZ, PLAYER_RADIUS + 0.35f)
                    bot.x = scratchResX
                    bot.z = scratchResZ
                    bot.y = getSurfaceHeightAt(bot.x, bot.z, 4f) + 0.8f
                    bot.vx = 0f
                    bot.vy = 0f
                    bot.vz = 0f
                    bot.hp = 100
                    bot.alive = true
                    bot.shootCooldown = 2.0f
                }
                continue
            }

            bot.hitFlash = max(0f, bot.hitFlash - dt * 4f)
            bot.muzzleFlash = max(0f, bot.muzzleFlash - dt)

            // 25 Hz AI Brain: target tracking, strafe/wander decisions, jump triggers, LOS & firing
            if (runAiDecisionTick) {
                bot.strafeTimer -= aiDt
                if (bot.strafeTimer <= 0f) {
                    bot.strafeTimer = 1.4f + Random.nextFloat() * 1.8f
                    bot.strafeDir = if (Random.nextBoolean()) 1f else -1f
                    if (Random.nextFloat() < 0.28f && bot.y <= getSurfaceHeightAt(bot.x, bot.z, bot.y + 0.5f) + 0.15f) {
                        bot.vy = 9.5f
                    }
                }

                val dx = playerX - bot.x
                val dz = playerZ - bot.z
                val distSq = dx * dx + dz * dz
                val dist = sqrt(distSq).coerceAtLeast(0.001f)

                if (peacefulMode) {
                    val wanderAngle = (worldTime * 0.35f + bot.id.hashCode() * 0.7f)
                    val targetVx = cos(wanderAngle) * 3.0f
                    val targetVz = sin(wanderAngle) * 3.0f
                    val blend = min(1f, aiDt * 4f)
                    bot.vx += (targetVx - bot.vx) * blend
                    bot.vz += (targetVz - bot.vz) * blend
                    bot.yaw = atan2(bot.vx, -bot.vz) * 180f / PI.toFloat()
                } else {
                    bot.yaw = (atan2(dx, -dz) * 180f / PI.toFloat())
                    val dirX = dx / dist
                    val dirZ = dz / dist
                    val strafeX = -dirZ * bot.strafeDir
                    val strafeZ = dirX * bot.strafeDir

                    val moveSpeed = 5.6f
                    val approach = when {
                        dist > 26f -> 1f
                        dist < 10f -> -0.5f
                        else -> 0.2f
                    }

                    val targetVx = (dirX * approach + strafeX * 0.72f) * moveSpeed
                    val targetVz = (dirZ * approach + strafeZ * 0.72f) * moveSpeed
                    val blend = min(1f, aiDt * 6f)
                    bot.vx += (targetVx - bot.vx) * blend
                    bot.vz += (targetVz - bot.vz) * blend

                    bot.shootCooldown -= aiDt
                    if (playerAlive && spawnProtectionTimer <= 0f && distSq < 44f * 44f && bot.shootCooldown <= 0f) {
                        if (!hasLineOfSight(bot.x, bot.y + 1.25f, bot.z, playerX, playerY + 1.15f, playerZ)) {
                            bot.shootCooldown = 0.45f
                        } else {
                            bot.shootCooldown = when (bot.weapon) {
                                WeaponType.RPG -> 3.4f + Random.nextFloat() * 1.4f
                                WeaponType.PISTOL -> 1.3f + Random.nextFloat() * 0.7f
                                WeaponType.RIFLE -> 0.85f + Random.nextFloat() * 0.65f
                            }
                            bot.muzzleFlash = 0.09f
                            val aimY = (playerY + 1.15f) - (bot.y + 1.25f)
                            val aimLen = sqrt(dx * dx + aimY * aimY + dz * dz).coerceAtLeast(0.01f)
                            val inaccuracy = 0.08f
                            val bx = dx / aimLen + (Random.nextFloat() - 0.5f) * inaccuracy
                            val by = aimY / aimLen + (Random.nextFloat() - 0.5f) * inaccuracy
                            val bz = dz / aimLen + (Random.nextFloat() - 0.5f) * inaccuracy
                            val speed = if (bot.weapon.explosive) 26f else 52f
                            val shotX = bot.x + bx * 1.1f
                            val shotY = bot.y + 1.25f
                            val shotZ = bot.z + bz * 1.1f
                            projectiles.add(
                                Projectile(
                                    x = shotX,
                                    y = shotY,
                                    z = shotZ,
                                    vx = bx * speed,
                                    vy = by * speed,
                                    vz = bz * speed,
                                    damage = if (bot.weapon.explosive) 24 else 8,
                                    fromPlayer = false,
                                    explosive = bot.weapon.explosive,
                                    life = 2.4f
                                )
                            )
                            soundManager.playWeaponFire3D(bot.weapon, shotX, shotY, shotZ, isLocalPlayer = false)
                        }
                    }
                }
            }

            // Smooth 120 Hz physics & walk-cycle integration (zero Pair allocations)
            bot.vy -= GRAVITY * dt
            val desiredX = clampArena(bot.x + bot.vx * dt)
            val desiredZ = clampArena(bot.z + bot.vz * dt)
            resolveHorizontalCollisionIntoScratch(desiredX, bot.y, desiredZ, PLAYER_RADIUS)
            val blockedDist = hypot2(scratchResX - desiredX, scratchResZ - desiredZ)
            bot.x = scratchResX
            bot.z = scratchResZ
            bot.y += bot.vy * dt
            val groundY = getSurfaceHeightAt(bot.x, bot.z, bot.y + 0.6f)
            if (bot.y <= groundY) {
                bot.y = groundY
                bot.vy = 0f
            }

            // Obstacle avoidance: steer around walls & vault low crates instead of getting stuck
            if (blockedDist > 0.04f) {
                if (bot.strafeTimer < 1.1f) {
                    bot.strafeDir = -bot.strafeDir
                    bot.strafeTimer = 1.6f
                }
                val perpX = -bot.vz * bot.strafeDir
                val perpZ = bot.vx * bot.strafeDir
                val pLen = hypot2(perpX, perpZ).coerceAtLeast(0.01f)
                bot.vx = (perpX / pLen) * 5.2f
                bot.vz = (perpZ / pLen) * 5.2f
                if (bot.y <= groundY + 0.15f) {
                    bot.vy = max(bot.vy, 7.6f)
                }
            }

            bot.walkCycle += dt * sqrt(bot.vx * bot.vx + bot.vz * bot.vz) * 1.6f
        }
    }

    private fun updateRemotePlayersInterpolation(dt: Float) {
        val alpha = min(1f, dt * 12f)
        for (rp in remotePlayers.values) {
            val prevX = rp.x
            val prevZ = rp.z
            rp.x += (rp.targetX - rp.x) * alpha
            rp.y += (rp.targetY - rp.y) * alpha
            rp.z += (rp.targetZ - rp.z) * alpha
            var dyaw = rp.targetYaw - rp.yaw
            while (dyaw > 180f) dyaw -= 360f
            while (dyaw < -180f) dyaw += 360f
            rp.yaw += dyaw * alpha
            rp.hitFlash = max(0f, rp.hitFlash - dt * 4f)
            rp.muzzleFlashTimer = max(0f, rp.muzzleFlashTimer - dt)
            rp.ramCooldown = max(0f, rp.ramCooldown - dt)
            val moved = hypot2(rp.x - prevX, rp.z - prevZ)
            if (moved > 0.01f) rp.walkCycle += moved * 2.2f
        }
    }

    private fun updateDynamicProps(dt: Float) {
        var anyMovedHorizontally = false
        for (i in props.indices) {
            val prop = props[i]
            if (prop.destroyed) continue
            prop.bounceTimer = max(0f, prop.bounceTimer - dt)
            prop.trapCooldown = max(0f, prop.trapCooldown - dt)
            if (prop.targetDownTimer > 0f) {
                prop.targetDownTimer = max(0f, prop.targetDownTimer - dt)
            }
            if (!prop.type.movableByBlast) continue
            val floorY = getSurfaceHeightExcluding(prop.x, prop.z, prop.y + 0.35f, prop)
            val moving = abs(prop.vx) > 0.05f || abs(prop.vy) > 0.05f || abs(prop.vz) > 0.05f || prop.y > floorY + 0.02f
            if (moving) {
                val prevX = prop.x
                val prevZ = prop.z
                prop.vy -= GRAVITY * dt
                prop.x = clampArena(prop.x + prop.vx * dt)
                prop.z = clampArena(prop.z + prop.vz * dt)
                if (abs(prop.x - prevX) > 0.01f || abs(prop.z - prevZ) > 0.01f) {
                    anyMovedHorizontally = true
                }
                prop.y += prop.vy * dt
                val landedY = getSurfaceHeightExcluding(prop.x, prop.z, prop.y + 0.45f, prop)
                if (prop.y <= landedY) {
                    prop.y = landedY
                    val bounce = if (prop.type == PropType.TIRE_STACK) 0.48f else 0.28f
                    if (prop.vy < -4.5f) prop.vy = -prop.vy * bounce else prop.vy = 0f
                    prop.vx *= max(0f, 1f - dt * 5.2f)
                    prop.vz *= max(0f, 1f - dt * 5.2f)
                    prop.tiltVel *= max(0f, 1f - dt * 4.5f)
                }
                if (prop.type == PropType.BOWLING_PIN) {
                    prop.tiltAngle = (prop.tiltAngle + prop.tiltVel * dt).coerceIn(-88f, 88f)
                    prop.spinAngle = (prop.spinAngle + (abs(prop.vx) + abs(prop.vz)) * dt * 26f) % 360f
                    // Domino collision with neighboring bowling pins via spatial grid!
                    val spd = hypot2(prop.vx, prop.vz)
                    if (spd > 2.2f) {
                        propGrid.forEachCandidate(prop.x, prop.z, 1.5f, props) { other ->
                            if (other === prop || other.type != PropType.BOWLING_PIN) return@forEachCandidate
                            val d = hypot2(other.x - prop.x, other.z - prop.z)
                            if (d in 0.05f..1.35f && abs(other.y - prop.y) < 1.8f) {
                                val nx = (other.x - prop.x) / d
                                val nz = (other.z - prop.z) / d
                                other.vx += nx * spd * 0.65f
                                other.vz += nz * spd * 0.65f
                                other.vy = max(other.vy, 3.6f)
                                other.tiltVel = (if (nx >= 0f) 1f else -1f) * 260f
                                if (other.trapCooldown <= 0f) {
                                    other.trapCooldown = 0.35f
                                    soundManager.playBowlingStrike3D(other.x, other.y + 0.9f, other.z, 0.72f)
                                }
                            }
                        }
                    }
                }
            } else if (prop.type == PropType.BOWLING_PIN && abs(prop.tiltAngle) > 2f && abs(prop.tiltVel) < 4f) {
                // Slowly settle knocked pins
                prop.tiltAngle += ((if (prop.tiltAngle >= 0f) 84f else -84f) - prop.tiltAngle) * min(1f, dt * 3.5f)
            }
        }
        if (anyMovedHorizontally) {
            propGridDirty = true
        }
    }

    /**
     * Drives all interactive GLB track & obstacle course mechanics:
     * - `BOOST_PAD` turbo acceleration + flames
     * - `JUMP_PAD` spring launch + pad bounce
     * - `LOOP_THE_LOOP` 360° vertical loop ride
     * - `FIRE_RING` ambient flames + fiery stunt bonus
     * - `PENDULUM_HAMMER`, `CRUSHER`, `SPINNER_TRAP` animated hazard knockback
     * - `FINISH_ARCH` lap timer, best record, confetti burst & fanfare
     * - `GAZYAVA_*` / `HRUMKI_*` food & drink auto-heal pickups
     */
    private fun updateInteractivePropsAndTraps(dt: Float) {
        val drivenCar = activeVehicle
        val pX = drivenCar?.x ?: playerX
        val pY = drivenCar?.y ?: playerY
        val pZ = drivenCar?.z ?: playerZ
        val pRad = drivenCar?.type?.halfW ?: PLAYER_RADIUS

        for (i in props.indices) {
            val prop = props[i]
            if (prop.destroyed) continue

            if (prop.type.hasInteractiveDoor && prop.doorTouched && prop.doorAnimTime < 2.5f) {
                prop.doorAnimTime = (prop.doorAnimTime + dt).coerceAtMost(2.5f)
            }

            // Auto-heal food & drink pickups (Gazyava & Hrumki)
            if (prop.type.isHealPickup) {
                if (playerAlive && hypot2(pX - prop.x, pZ - prop.z) < (pRad + 1.65f) && abs(pY - prop.y) < 2.2f) {
                    prop.destroyed = true
                    val before = playerHp
                    playerHp = (playerHp + 35).coerceAtMost(100)
                    val gained = playerHp - before
                    soundManager.playFoodCrunch3D(prop.x, prop.y + 0.7f, prop.z, isDrink = prop.type.isDrink)
                    spawnDustBurst(prop.x, prop.y + 0.8f, prop.z, 0.25f, 0.98f, 0.45f, 12)
                    spawnDustBurst(prop.x, prop.y + 1.0f, prop.z, 1.0f, 0.88f, 0.24f, 6)
                    pushKillFeed(if (gained > 0) "🍔 ${prop.type.label}: +$gained HP!" else "🥤 ${prop.type.label}: вкуснота!")
                }
                continue
            }

            when (prop.type) {
                PropType.BOOST_PAD -> {
                    if (prop.trapCooldown <= 0f) {
                        // Check player on foot
                        if (drivenCar == null && playerAlive &&
                            hypot2(playerX - prop.x, playerZ - prop.z) < 2.6f && abs(playerY - prop.y) < 1.3f
                        ) {
                            val fx = sin(prop.yaw * PI.toFloat() / 180f)
                            val fz = -cos(prop.yaw * PI.toFloat() / 180f)
                            playerVx = fx * 22f
                            playerVz = fz * 22f
                            playerVy = max(playerVy, 3.2f)
                            prop.trapCooldown = 0.45f
                            soundManager.playBoostPad3D(prop.x, prop.y + 0.4f, prop.z)
                            spawnDustBurst(prop.x, prop.y + 0.5f, prop.z, 0.16f, 0.92f, 1.0f, 10)
                        }
                        // Check vehicles
                        for (car in vehicles) {
                            if (car.loopPropId != null) continue
                            if (hypot2(car.x - prop.x, car.z - prop.z) < 3.0f && abs(car.y - prop.y) < 1.5f) {
                                car.speed = max(car.speed, car.type.turboSpeed * 1.06f)
                                car.nitroTimer = max(car.nitroTimer, 2.0f)
                                car.pitchAngle = (car.pitchAngle + 5.5f).coerceAtMost(14f)
                                prop.trapCooldown = 0.40f
                                soundManager.playBoostPad3D(prop.x, prop.y + 0.4f, prop.z)
                                spawnDustBurst(car.x, car.y + 0.6f, car.z, 0.18f, 0.90f, 1.0f, 12)
                                if (car === drivenCar) screenShake = max(screenShake, 0.18f)
                                break
                            }
                        }
                    }
                }

                PropType.JUMP_PAD -> {
                    if (prop.trapCooldown <= 0f) {
                        if (drivenCar == null && playerAlive &&
                            hypot2(playerX - prop.x, playerZ - prop.z) < 1.85f && abs(playerY - prop.y) < 1.2f
                        ) {
                            playerVy = 19.5f
                            playerGrounded = false
                            prop.bounceTimer = 0.55f
                            prop.trapCooldown = 0.45f
                            soundManager.playJumpPad3D(prop.x, prop.y + 0.5f, prop.z)
                            spawnDustBurst(prop.x, prop.y + 0.5f, prop.z, 1.0f, 0.88f, 0.22f, 12)
                        }
                        for (car in vehicles) {
                            if (car.loopPropId != null) continue
                            if (hypot2(car.x - prop.x, car.z - prop.z) < 2.35f && abs(car.y - prop.y) < 1.4f) {
                                car.vy = 20.5f
                                car.grounded = false
                                car.pitchAngle = 14f
                                prop.bounceTimer = 0.55f
                                prop.trapCooldown = 0.45f
                                soundManager.playJumpPad3D(prop.x, prop.y + 0.5f, prop.z)
                                spawnDustBurst(prop.x, prop.y + 0.5f, prop.z, 1.0f, 0.88f, 0.22f, 14)
                                break
                            }
                        }
                    }
                }

                PropType.LOOP_THE_LOOP -> {
                    for (car in vehicles) {
                        if (car.loopPropId != null) continue
                        val absSpd = abs(car.speed)
                        if (absSpd < 13.5f || car.y > prop.y + 2.0f) continue
                        val dx = car.x - prop.x
                        val dz = car.z - prop.z
                        val lx = dx * prop.cosYaw + dz * prop.sinYaw
                        val lz = -dx * prop.sinYaw + dz * prop.cosYaw
                        // Enter near loop base entry/exit ramp along the loop's center lane
                        if (abs(lx) < 3.4f && abs(lz) in 1.2f..5.6f) {
                            val loopFwdX = sin(prop.yaw * PI.toFloat() / 180f)
                            val loopFwdZ = -cos(prop.yaw * PI.toFloat() / 180f)
                            val carRad = car.yaw * PI.toFloat() / 180f
                            val carFwdX = sin(carRad) * (if (car.speed >= 0f) 1f else -1f)
                            val carFwdZ = -cos(carRad) * (if (car.speed >= 0f) 1f else -1f)
                            val alignment = loopFwdX * carFwdX + loopFwdZ * carFwdZ
                            if (abs(alignment) > 0.55f) {
                                car.loopPropId = prop.id
                                car.loopProgress = 0.02f
                                car.loopSpeed = max(absSpd, 34f)
                                car.loopDirection = if (alignment >= 0f) 1f else -1f
                                car.nitroTimer = max(car.nitroTimer, 1.8f)
                                soundManager.playBoostPad3D(car.x, car.y + 1.0f, car.z)
                                if (car === drivenCar) {
                                    pushKillFeed("🌀 МЁРТВАЯ ПЕТЛЯ 360°!")
                                    screenShake = max(screenShake, 0.22f)
                                }
                            }
                        }
                    }
                }

                PropType.FIRE_RING -> {
                    // Ambient roaring flames on the ring when player is within 48m
                    if (hypot2(pX - prop.x, pZ - prop.z) < 48f && Random.nextFloat() < 0.35f * settings.particleDensity) {
                        val ang = Random.nextFloat() * 2f * PI.toFloat()
                        val ringR = 2.6f
                        val rx = cos(ang) * ringR
                        val ry = 3.2f + sin(ang) * ringR
                        val wx = prop.x + prop.cosYaw * rx
                        val wz = prop.z + prop.sinYaw * rx
                        particles.add(
                            Particle3D(
                                x = wx, y = prop.y + ry, z = wz,
                                vx = (Random.nextFloat() - 0.5f) * 1.4f,
                                vy = 2.6f + Random.nextFloat() * 2.2f,
                                vz = (Random.nextFloat() - 0.5f) * 1.4f,
                                size = 0.34f, age = 0f, maxAge = 0.32f,
                                r = 1.0f, g = 0.45f + Random.nextFloat() * 0.4f, b = 0.08f
                            )
                        )
                    }
                    // Stunt bonus when leaping or driving through the flaming arch ring!
                    val frDx = pX - prop.x
                    val frDz = pZ - prop.z
                    val frLx = frDx * prop.cosYaw + frDz * prop.sinYaw
                    val frLz = -frDx * prop.sinYaw + frDz * prop.cosYaw
                    if (prop.trapCooldown <= 0f &&
                        abs(frLx) < prop.type.halfW + 0.55f &&
                        abs(frLz) < 2.25f &&
                        pY in (prop.y + 0.35f)..(prop.y + 8.6f)
                    ) {
                        prop.trapCooldown = 1.6f
                        soundManager.playBoostPad3D(prop.x, prop.y + 3.0f, prop.z)
                        spawnDustBurst(pX, pY + 1.2f, pZ, 1.0f, 0.58f, 0.12f, 16)
                        if (drivenCar != null) {
                            drivenCar.nitroTimer = max(drivenCar.nitroTimer, 1.6f)
                            drivenCar.speed = max(drivenCar.speed, drivenCar.type.turboSpeed)
                        }
                        pushKillFeed("🔥 ПРЫЖОК СКВОЗЬ ОГНЕННОЕ КОЛЬЦО!")
                    }
                }

                PropType.PENDULUM_HAMMER -> {
                    if (prop.trapCooldown <= 0f) {
                        val swingRad = sin((worldTime * 2.15f + prop.trapPhaseOffset).toDouble()).toFloat() * 1.02f
                        val bobLocalX = sin(swingRad) * 3.1f
                        val bobY = prop.y + 4.3f - cos(swingRad) * 3.1f
                        val bobWorldX = prop.x + prop.cosYaw * bobLocalX
                        val bobWorldZ = prop.z + prop.sinYaw * bobLocalX
                        val hitDist = hypot2(pX - bobWorldX, pZ - bobWorldZ)
                        if (hitDist < pRad + 1.45f && abs((pY + 0.9f) - bobY) < 1.85f) {
                            val dir = if (cos((worldTime * 2.15f + prop.trapPhaseOffset).toDouble()) >= 0.0) 1f else -1f
                            applyTrapKnockback(prop.cosYaw * dir * 18f, prop.sinYaw * dir * 18f, 11.5f, bobWorldX, bobY, bobWorldZ)
                            prop.trapCooldown = 0.85f
                        }
                    }
                }

                PropType.CRUSHER -> {
                    val cycle = ((worldTime * 1.45f + prop.trapPhaseOffset) % 2.4f)
                    val headDrop = if (cycle < 0.35f) cycle / 0.35f else if (cycle < 0.65f) 1f else max(0f, 1f - (cycle - 0.65f) / 1.1f)
                    if (headDrop > 0.72f && prop.trapCooldown <= 0f) {
                        if (hypot2(pX - prop.x, pZ - prop.z) < pRad + 1.85f && abs(pY - prop.y) < 2.2f) {
                            val pushX = if (pX >= prop.x) 14f else -14f
                            val pushZ = if (pZ >= prop.z) 14f else -14f
                            applyTrapKnockback(pushX, pushZ, 10.5f, prop.x, prop.y + 1.2f, prop.z)
                            prop.trapCooldown = 1.05f
                        }
                    }
                }

                PropType.SPINNER_TRAP -> {
                    if (prop.trapCooldown <= 0f) {
                        val spinRad = (worldTime * 2.65f + prop.trapPhaseOffset)
                        val armX = cos(spinRad)
                        val armZ = sin(spinRad)
                        val dx = pX - prop.x
                        val dz = pZ - prop.z
                        val dist = hypot2(dx, dz)
                        if (dist in 0.4f..3.3f && abs(pY - prop.y) < 1.65f) {
                            // Distance from the rotating beam segment
                            val proj = abs(dx * armX + dz * armZ)
                            val perp = abs(-dx * armZ + dz * armX)
                            if (proj < 3.2f && perp < pRad + 0.55f) {
                                val tangX = -armZ * 17f
                                val tangZ = armX * 17f
                                applyTrapKnockback(tangX, tangZ, 9.8f, pX, pY + 0.8f, pZ)
                                prop.trapCooldown = 0.70f
                            }
                        }
                    }
                }

                PropType.FINISH_ARCH -> {
                    if (raceCrossCooldown <= 0f && playerAlive) {
                        val dx = pX - prop.x
                        val dz = pZ - prop.z
                        val lx = dx * prop.cosYaw + dz * prop.sinYaw
                        val lz = -dx * prop.sinYaw + dz * prop.cosYaw
                        if (abs(lx) < prop.type.halfW + 0.75f &&
                            abs(lz) < 2.35f &&
                            pY in (prop.y - 0.5f)..(prop.y + prop.type.height + 2.5f)
                        ) {
                            raceCrossCooldown = 2.6f
                            raceArchX = prop.x
                            raceArchZ = prop.z
                            if (!raceActive) {
                                raceActive = true
                                raceCurrentLapSec = 0f
                                raceCheckpointPassed = false
                                raceBannerText = "🏁 СТАРТ! ПОГНАЛИ!"
                                raceBannerTimer = 2.6f
                                soundManager.playLapFanfare(isRecord = false)
                                spawnConfettiBurst(prop.x, prop.y + 6.3f, prop.z, 24)
                                pushKillFeed("🏁 Гонка началась! Пройдите круг до арки!")
                            } else if (raceCheckpointPassed) {
                                raceLapCount++
                                val lapTime = raceCurrentLapSec
                                val isRecord = raceBestLapSec <= 0f || lapTime < raceBestLapSec
                                if (isRecord) raceBestLapSec = lapTime
                                raceCurrentLapSec = 0f
                                raceCheckpointPassed = false
                                val formatted = String.format(java.util.Locale.US, "%.1f", lapTime)
                                raceBannerText = if (isRecord) {
                                    "🏆 КРУГ #$raceLapCount — ${formatted}с (РЕКОРД!)"
                                } else {
                                    "🏁 КРУГ #$raceLapCount — ${formatted}с"
                                }
                                raceBannerTimer = 3.6f
                                soundManager.playLapFanfare(isRecord = isRecord)
                                spawnConfettiBurst(prop.x, prop.y + 6.3f, prop.z, 38)
                                pushKillFeed(raceBannerText)
                            }
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    private fun updateVehicleInLoop(car: VehicleEntity, dt: Float, isDriven: Boolean) {
        val loopProp = props.firstOrNull { it.id == car.loopPropId && !it.destroyed }
        if (loopProp == null) {
            car.loopPropId = null
            car.pitchAngle = 0f
            return
        }
        val radius = 6.1f
        val circumference = (2f * PI.toFloat() * radius)
        val spd = max(car.loopSpeed, 30f)
        car.loopProgress += (spd * dt) / circumference
        val fwdX = sin(loopProp.yaw * PI.toFloat() / 180f) * car.loopDirection
        val fwdZ = -cos(loopProp.yaw * PI.toFloat() / 180f) * car.loopDirection
        val sideX = cos(loopProp.yaw * PI.toFloat() / 180f)
        val sideZ = sin(loopProp.yaw * PI.toFloat() / 180f)

        if (car.loopProgress >= 1.0f) {
            car.loopPropId = null
            car.loopProgress = 0f
            car.pitchAngle = 0f
            car.y = loopProp.y + 0.25f
            car.x = clampVehicle(loopProp.x + fwdX * 5.8f + sideX * 1.4f, car.type.halfW)
            car.z = clampVehicle(loopProp.z + fwdZ * 5.8f + sideZ * 1.4f, car.type.halfW)
            car.speed = max(car.speed, car.type.turboSpeed)
            car.nitroTimer = max(car.nitroTimer, 1.4f)
            car.grounded = true
            soundManager.playBoostPad3D(car.x, car.y + 0.8f, car.z)
            if (isDriven) {
                playerX = car.x
                playerY = car.y + 0.55f
                playerZ = car.z
                pushKillFeed("✨ ПЕТЛЯ 360° ПРОЙДЕНА!")
            }
            return
        }

        val theta = car.loopProgress * 2f * PI.toFloat()
        val along = sin(theta) * radius
        val height = (1f - cos(theta)) * radius
        // Smooth helical lane offset (-1.3 -> +1.3) matching the loop_the_loop.glb track lanes!
        val lateral = (-1.25f + car.loopProgress * 2.5f) * car.loopDirection
        car.x = clampVehicle(loopProp.x + fwdX * along + sideX * lateral, car.type.halfW)
        car.y = loopProp.y + height + 0.15f
        car.z = clampVehicle(loopProp.z + fwdZ * along + sideZ * lateral, car.type.halfW)
        car.yaw = if (car.loopDirection >= 0f) loopProp.yaw else (loopProp.yaw + 180f) % 360f
        car.pitchAngle = (car.loopProgress * 360f) % 360f
        car.rollAngle = 0f
        car.wheelSpin = (car.wheelSpin + spd * dt * 120f) % 360f
        car.grounded = true
        if (isDriven) {
            playerX = car.x
            playerY = car.y + 0.55f
            playerZ = car.z
            sessionDriveMeters += spd * dt
        }
        if (Random.nextFloat() < 0.65f * settings.particleDensity) {
            particles.add(
                Particle3D(
                    x = car.x, y = car.y + 0.4f, z = car.z,
                    vx = (Random.nextFloat() - 0.5f) * 3f,
                    vy = 1.5f,
                    vz = (Random.nextFloat() - 0.5f) * 3f,
                    size = 0.30f, age = 0f, maxAge = 0.28f,
                    r = 1.0f, g = 0.65f, b = 0.14f
                )
            )
        }
    }

    private fun applyTrapKnockback(kx: Float, kz: Float, upVy: Float, fx: Float, fy: Float, fz: Float) {
        val car = activeVehicle
        if (car != null) {
            car.lateralVel += kx * 0.45f
            car.speed = (car.speed * 0.45f).coerceIn(-20f, 20f)
            car.vy = max(car.vy, upVy)
            car.grounded = false
            car.suspensionVel -= 2.8f
        } else {
            playerVx += kx
            playerVz += kz
            playerVy = max(playerVy, upVy)
            playerGrounded = false
        }
        screenShake = max(screenShake, 0.36f)
        soundManager.playCrusherImpact3D(fx, fy, fz, intensity = 1.15f)
        spawnDustBurst(fx, fy, fz, 1.0f, 0.72f, 0.18f, 12)
    }

    private fun spawnConfettiBurst(x: Float, y: Float, z: Float, count: Int) {
        val palette = arrayOf(
            floatArrayOf(1.0f, 0.24f, 0.36f),
            floatArrayOf(1.0f, 0.86f, 0.14f),
            floatArrayOf(0.18f, 0.94f, 0.52f),
            floatArrayOf(0.20f, 0.72f, 1.0f),
            floatArrayOf(0.92f, 0.30f, 1.0f)
        )
        val scaled = (count * settings.particleDensity).toInt().coerceIn(10, 48)
        repeat(scaled) { idx ->
            val col = palette[idx % palette.size]
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val spd = 3.0f + Random.nextFloat() * 8.5f
            particles.add(
                Particle3D(
                    x = x + (Random.nextFloat() - 0.5f) * 4.5f,
                    y = y + (Random.nextFloat() - 0.5f) * 0.8f,
                    z = z + (Random.nextFloat() - 0.5f) * 1.5f,
                    vx = cos(ang) * spd,
                    vy = 4.0f + Random.nextFloat() * 6.5f,
                    vz = sin(ang) * spd,
                    size = 0.28f,
                    age = 0f,
                    maxAge = 1.1f + Random.nextFloat() * 0.7f,
                    r = col[0], g = col[1], b = col[2]
                )
            )
        }
    }

    // --- LIVING NPC ХРЯК-БУРГЕР AI (25 Hz brain + 120 Hz smooth physics) ---
    private fun updatePigBurgers(dt: Float, runAiDecisionTick: Boolean, aiDt: Float) {
        for (i in pigBurgers.indices) {
            val pig = pigBurgers[i]
            pig.animTime += dt
            pig.hitFlash = max(0f, pig.hitFlash - dt * 3.8f)
            pig.happyTimer = max(0f, pig.happyTimer - dt)
            pig.oinkCooldown = max(0f, pig.oinkCooldown - dt)
            pig.lootCooldown = max(0f, pig.lootCooldown - dt)
            pig.knockbackTimer = max(0f, pig.knockbackTimer - dt)

            if (runAiDecisionTick) {
                pig.stateTimer -= aiDt

                // Soft bump panic when player runs right into Pig-Burger
                if (activeVehicle == null && playerAlive && pig.state != PigBurgerState.FLEE_RUN && pig.state != PigBurgerState.HAPPY_HOP) {
                    val dx = playerX - pig.x
                    val dz = playerZ - pig.z
                    if (dx * dx + dz * dz < 1.35f * 1.35f) {
                        val pSpdSq = playerVx * playerVx + playerVz * playerVz
                        if (pSpdSq > 8.5f * 8.5f) {
                            triggerPigPanic(pig, playerX, playerZ)
                        }
                    }
                }

                if (pig.stateTimer <= 0f) {
                    when (pig.state) {
                        PigBurgerState.PANIC_JUMP -> {
                            pig.state = PigBurgerState.FLEE_RUN
                            pig.stateTimer = 2.6f + Random.nextFloat() * 1.4f
                        }
                        PigBurgerState.FLEE_RUN, PigBurgerState.HAPPY_HOP -> {
                            pig.state = PigBurgerState.IDLE
                            pig.stateTimer = 1.6f + Random.nextFloat() * 1.8f
                        }
                        PigBurgerState.IDLE -> {
                            pig.state = PigBurgerState.WALK
                            pig.yaw = Random.nextFloat() * 360f
                            pig.stateTimer = 2.4f + Random.nextFloat() * 3.0f
                        }
                        PigBurgerState.WALK -> {
                            if (Random.nextFloat() < 0.26f && pig.grounded) {
                                pig.vy = 7.4f
                                pig.grounded = false
                                if (pig.oinkCooldown <= 0f && hypot2(playerX - pig.x, playerZ - pig.z) < 28f) {
                                    pig.oinkCooldown = 4.5f
                                    soundManager.playPigOink3D(pig.x, pig.y + 0.8f, pig.z, panicked = false)
                                }
                            }
                            pig.state = if (Random.nextFloat() < 0.45f) PigBurgerState.IDLE else PigBurgerState.WALK
                            if (pig.state == PigBurgerState.WALK) {
                                pig.yaw = (pig.yaw + (Random.nextFloat() - 0.5f) * 140f + 360f) % 360f
                            }
                            pig.stateTimer = 1.8f + Random.nextFloat() * 2.5f
                        }
                    }
                }

                if (pig.knockbackTimer <= 0f) {
                    val rad = pig.yaw * PI.toFloat() / 180f
                    val fwdX = sin(rad)
                    val fwdZ = -cos(rad)
                    val targetSpd = when (pig.state) {
                        PigBurgerState.IDLE -> 0f
                        PigBurgerState.WALK -> 2.8f
                        PigBurgerState.PANIC_JUMP -> 5.2f
                        PigBurgerState.FLEE_RUN -> 9.6f
                        PigBurgerState.HAPPY_HOP -> 0.6f
                    }
                    val blend = min(1f, aiDt * 7f)
                    pig.vx += (fwdX * targetSpd - pig.vx) * blend
                    pig.vz += (fwdZ * targetSpd - pig.vz) * blend
                    if (pig.state == PigBurgerState.HAPPY_HOP && pig.grounded) {
                        pig.vy = 7.8f
                        pig.grounded = false
                    }
                }
            }

            // Smooth 120 Hz position & collision integration
            pig.vy -= GRAVITY * dt
            val desiredX = clampArena(pig.x + pig.vx * dt)
            val desiredZ = clampArena(pig.z + pig.vz * dt)
            resolveHorizontalCollisionIntoScratch(desiredX, pig.y + 0.15f, desiredZ, 0.72f)
            val blocked = hypot2(scratchResX - desiredX, scratchResZ - desiredZ) > 0.05f
            pig.x = scratchResX
            pig.z = scratchResZ
            if (blocked) {
                pig.yaw = (pig.yaw + 125f) % 360f
            }
            pig.y += pig.vy * dt
            val groundY = getSurfaceHeightAt(pig.x, pig.z, pig.y + 0.65f)
            if (pig.y <= groundY) {
                pig.y = groundY
                pig.vy = 0f
                pig.grounded = true
            } else if (pig.y > groundY + 0.25f) {
                pig.grounded = false
            }
        }
    }

    private fun scareNearbyPigBurgers(ox: Float, oz: Float, radius: Float) {
        for (i in pigBurgers.indices) {
            val pig = pigBurgers[i]
            val d = hypot2(pig.x - ox, pig.z - oz)
            if (d < radius) {
                triggerPigPanic(pig, ox, oz)
            }
        }
    }

    private fun triggerPigPanic(pig: PigBurgerNpc, threatX: Float, threatZ: Float) {
        val dx = pig.x - threatX
        val dz = pig.z - threatZ
        pig.yaw = (atan2(dx, -dz) * 180f / PI.toFloat() + (Random.nextFloat() - 0.5f) * 35f + 360f) % 360f
        pig.state = PigBurgerState.PANIC_JUMP
        pig.stateTimer = 0.55f
        if (pig.grounded) {
            pig.vy = 11.2f
            pig.grounded = false
        }
        if (pig.oinkCooldown <= 0f) {
            pig.oinkCooldown = 1.6f
            soundManager.playPigOink3D(pig.x, pig.y + 0.8f, pig.z, panicked = true)
        }
    }

    fun hitPigBurger(
        pig: PigBurgerNpc,
        impulseX: Float,
        impulseZ: Float,
        upVy: Float = 10.5f,
        dropLoot: Boolean = true
    ) {
        pig.vx = impulseX
        pig.vz = impulseZ
        pig.vy = max(pig.vy, upVy)
        pig.grounded = false
        pig.hitFlash = 1.0f
        pig.knockbackTimer = 0.55f
        pig.state = PigBurgerState.FLEE_RUN
        pig.stateTimer = 3.2f
        pig.yaw = (atan2(impulseX, -impulseZ) * 180f / PI.toFloat() + 360f) % 360f
        soundManager.playPigOink3D(pig.x, pig.y + 0.9f, pig.z, panicked = true)
        spawnDustBurst(pig.x, pig.y + 1.0f, pig.z, 1.0f, 0.62f, 0.72f, 10)
        if (dropLoot && pig.lootCooldown <= 0f) {
            pig.lootCooldown = 3.2f
            val lootPool = PropType.foodAndDrinks
            val chosen = lootPool[Random.nextInt(lootPool.size)]
            val dropX = clampArena(pig.x + (Random.nextFloat() - 0.5f) * 1.4f)
            val dropZ = clampArena(pig.z + (Random.nextFloat() - 0.5f) * 1.4f)
            val dropY = getSurfaceHeightAt(dropX, dropZ, pig.y + 1.0f)
            props.add(
                WorldProp(
                    id = "loot_${propSeq++}",
                    type = chosen,
                    x = dropX,
                    y = dropY + 0.35f,
                    z = dropZ,
                    yaw = Random.nextFloat() * 360f,
                    vy = 5.4f,
                    userPlaced = false
                )
            )
            pushKillFeed("🍔 Из ${pig.name} выпала ${chosen.label}!")
        }
    }

    private fun findNearestPigBurger(x: Float, z: Float, maxDist: Float): PigBurgerNpc? {
        var best: PigBurgerNpc? = null
        var bestD = maxDist
        for (i in pigBurgers.indices) {
            val pig = pigBurgers[i]
            val d = hypot2(pig.x - x, pig.z - z)
            if (d < bestD) {
                bestD = d
                best = pig
            }
        }
        return best
    }

    private fun updateProjectiles(dt: Float) {
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            if (p.life <= 0f) {
                iter.remove()
                continue
            }
            if (p.explosive) {
                p.vy -= 4.2f * dt
                if (Random.nextFloat() < 0.78f) {
                    particles.add(
                        Particle3D(
                            x = p.x, y = p.y, z = p.z,
                            vx = (Random.nextFloat() - 0.5f) * 1.2f,
                            vy = 0.8f,
                            vz = (Random.nextFloat() - 0.5f) * 1.2f,
                            size = 0.28f, age = 0f, maxAge = 0.35f,
                            r = 0.94f, g = 0.84f, b = 0.66f
                        )
                    )
                }
            }

            val projSpeed = sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz)
            val subSteps = kotlin.math.ceil((projSpeed * dt) / 0.45f).toInt().coerceIn(3, 10)
            val subDt = dt / subSteps
            var hit = false

            for (s in 0 until subSteps) {
                p.x += p.vx * subDt
                p.y += p.vy * subDt
                p.z += p.vz * subDt

                if (p.y <= 0.05f || abs(p.x) >= ARENA_HALF_SIZE || abs(p.z) >= ARENA_HALF_SIZE) {
                    hit = true
                    break
                }

                val hitProp = findPropHitByPoint(p.x, p.y, p.z)
                if (hitProp != null) {
                    if (!p.explosive) {
                        damageProp(hitProp, p.damage, fromPlayer = p.fromPlayer)
                        spawnDustBurst(p.x, p.y, p.z, 0.95f, 0.85f, 0.45f, 5)
                    }
                    hit = true
                    break
                }

                if (p.fromPlayer) {
                    if (!peacefulMode) {
                        for (bot in bots) {
                            if (!bot.alive) continue
                            if (abs(p.x - bot.x) < 1.0f && abs(p.z - bot.z) < 1.0f &&
                                hypot2(p.x - bot.x, p.z - bot.z) < 0.95f && p.y in (bot.y..bot.y + 2.02f)
                            ) {
                                if (!p.explosive) {
                                    val isHeadshot = p.y >= bot.y + 1.34f
                                    val dealt = if (isHeadshot) (p.damage * 1.5f).toInt() else p.damage
                                    // Tactile hit knockback impulse on bots
                                    val pLen = sqrt(p.vx * p.vx + p.vz * p.vz).coerceAtLeast(1f)
                                    bot.vx += (p.vx / pLen) * 3.4f
                                    bot.vz += (p.vz / pLen) * 3.4f
                                    damageBot(
                                        bot = bot,
                                        amount = dealt,
                                        byPlayer = true,
                                        viaVehicle = false,
                                        damageKind = if (isHeadshot) DamageKind.CRITICAL else DamageKind.NORMAL,
                                        hitX = p.x,
                                        hitY = max(p.y + 0.25f, bot.y + 1.85f),
                                        hitZ = p.z
                                    )
                                    spawnDustBurst(p.x, p.y, p.z, 1f, if (isHeadshot) 0.85f else 1f, if (isHeadshot) 0.2f else 1f, 7)
                                }
                                hit = true
                                break
                            }
                        }
                        if (hit) break

                        for (rp in remotePlayers.values) {
                            if (rp.hp <= 0 || rp.godMode) continue
                            if (canDamageRemotePlayers() &&
                                abs(p.x - rp.x) < 1.0f && abs(p.z - rp.z) < 1.0f &&
                                hypot2(p.x - rp.x, p.z - rp.z) < 0.95f && p.y in (rp.y..rp.y + 2.02f)
                            ) {
                                if (!p.explosive) {
                                    val isHeadshot = p.y >= rp.y + 1.34f
                                    val dealt = if (isHeadshot) (p.damage * 1.5f).toInt() else p.damage
                                    val newHp = (rp.hp - dealt).coerceAtLeast(0)
                                    rp.hp = newHp
                                    rp.hitFlash = 1f
                                    hitMarkerAlpha = 1f
                                    if (isHeadshot) critHitFlashTimer = 1f
                                    spawnDamagePopup(
                                        x = rp.x,
                                        y = max(p.y + 0.25f, rp.y + 1.95f),
                                        z = rp.z,
                                        amount = dealt,
                                        kind = if (isHeadshot) DamageKind.CRITICAL else DamageKind.NORMAL
                                    )
                                    soundManager.playHit()
                                    onRemotePlayerDamaged?.invoke(rp.userId, newHp)
                                    if (newHp == 0) {
                                        recordPlayerKill(
                                            viaVehicle = false,
                                            targetName = rp.displayName,
                                            verb = if (isHeadshot) "Хедшот!" else "Вы подбили",
                                            suffix = "!"
                                        )
                                    }
                                }
                                hit = true
                                break
                            }
                        }
                        if (hit) break
                    }

                    // Shooting a Pig-Burger sends it flying and drops tasty food/drink loot (works in all modes!)
                    for (pig in pigBurgers) {
                        if (abs(p.x - pig.x) < 1.05f && abs(p.z - pig.z) < 1.05f &&
                            hypot2(p.x - pig.x, p.z - pig.z) < 1.0f && p.y in (pig.y..pig.y + 1.85f)
                        ) {
                            val pLen = sqrt(p.vx * p.vx + p.vz * p.vz).coerceAtLeast(1f)
                            hitPigBurger(
                                pig = pig,
                                impulseX = (p.vx / pLen) * 14f,
                                impulseZ = (p.vz / pLen) * 14f,
                                upVy = 9.6f,
                                dropLoot = true
                            )
                            hitMarkerAlpha = 0.85f
                            hit = true
                            break
                        }
                    }
                    if (hit) break
                } else if (playerAlive) {
                    val playerHitRadius = if (activeVehicle != null) 1.8f else 0.78f
                    if (abs(p.x - playerX) < playerHitRadius && abs(p.z - playerZ) < playerHitRadius &&
                        hypot2(p.x - playerX, p.z - playerZ) < playerHitRadius &&
                        p.y in (playerY..playerY + 1.95f)
                    ) {
                        val dmg = if (activeVehicle != null) p.damage / 2 else p.damage
                        damagePlayer(dmg)
                        hit = true
                        break
                    }
                }
            }

            if (hit) {
                if (p.explosive) {
                    triggerExplosion(p.x, max(0.3f, p.y), p.z, radius = 8.4f, maxDamage = p.damage, fromPlayer = p.fromPlayer)
                }
                iter.remove()
            }
        }
    }

    private fun triggerExplosion(
        ex: Float,
        ey: Float,
        ez: Float,
        radius: Float,
        maxDamage: Int,
        fromPlayer: Boolean
    ) {
        if (fromPlayer) {
            sessionExplosions++
        }
        if (explosions.size >= MAX_EXPLOSIONS) {
            explosions.removeAt(0)
        }
        explosions.add(ExplosionEffect(ex, ey, ez, maxRadius = radius * 0.85f))
        soundManager.playExplosion3D(
            x = ex,
            y = ey,
            z = ez,
            radius = radius,
            intensity = (maxDamage / 95f).coerceIn(0.65f, 1.2f)
        )

        val distToPlayer = sqrt((playerX - ex) * (playerX - ex) + (playerZ - ez) * (playerZ - ez))
        if (distToPlayer < 28f) {
            screenShake = max(screenShake, ((28f - distToPlayer) / 28f) * 0.75f)
            hapticController.onExplosionShockwave(distToPlayer)
        }

        // Fiery sparks + rising smoke particles scaled by graphics particleDensity setting
        val burstCount = (30 * settings.particleDensity).toInt().coerceIn(10, 42)
        val fieryCutoff = (burstCount * 0.6f).toInt()
        repeat(burstCount) {
            if (particles.size >= MAX_PARTICLES) return@repeat
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val spd = 5f + Random.nextFloat() * 14f
            val fiery = it < fieryCutoff
            particles.add(
                Particle3D(
                    x = ex, y = ey + 0.4f, z = ez,
                    vx = cos(a) * spd,
                    vy = 4.5f + Random.nextFloat() * 12f,
                    vz = sin(a) * spd,
                    size = if (fiery) 0.42f else 0.38f,
                    age = 0f,
                    maxAge = 0.55f + Random.nextFloat() * 0.45f,
                    r = if (fiery) 1f else 0.25f,
                    g = if (fiery) (0.35f + Random.nextFloat() * 0.5f) else 0.26f,
                    b = if (fiery) 0.08f else 0.28f
                )
            )
        }

        if (!peacefulMode) {
            for (bot in bots) {
                if (!bot.alive) continue
                val dx = bot.x - ex
                val dy = (bot.y + 0.9f) - ey
                val dz = bot.z - ez
                val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.1f)
                if (d < radius) {
                    val falloff = 1f - (d / radius)
                    bot.vx += (dx / d) * 20f * falloff
                    bot.vz += (dz / d) * 20f * falloff
                    bot.vy = max(bot.vy, 11f * falloff + 3f)
                    damageBot(
                        bot = bot,
                        amount = (maxDamage * falloff).toInt().coerceAtLeast(18),
                        byPlayer = fromPlayer,
                        viaVehicle = false,
                        damageKind = DamageKind.EXPLOSIVE
                    )
                }
            }

            if (fromPlayer && canDamageRemotePlayers()) {
                for (rp in remotePlayers.values) {
                    if (rp.hp <= 0 || rp.godMode) continue
                    val dx = rp.x - ex
                    val dy = (rp.y + 0.9f) - ey
                    val dz = rp.z - ez
                    val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.1f)
                    if (d < radius) {
                        val falloff = 1f - (d / radius)
                        val dmg = (maxDamage * falloff).toInt().coerceAtLeast(18)
                        val newHp = (rp.hp - dmg).coerceAtLeast(0)
                        rp.hp = newHp
                        rp.hitFlash = 1f
                        hitMarkerAlpha = 1f
                        spawnDamagePopup(rp.x, rp.y + 2.15f, rp.z, dmg, DamageKind.EXPLOSIVE)
                        soundManager.playHit()
                        onRemotePlayerDamaged?.invoke(rp.userId, newHp)
                        if (newHp == 0) {
                            recordPlayerKill(viaVehicle = false, targetName = rp.displayName, verb = "Вы взорвали", suffix = "!")
                        }
                    }
                }
            }
        }

        val chainBarrels = mutableListOf<WorldProp>()
        val expPropCount = props.size
        for (pIdx in 0 until expPropCount) {
            val prop = props[pIdx]
            if (prop.destroyed) continue
            val dx = prop.x - ex
            val dz = prop.z - ez
            val d = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
            if (d < radius) {
                val falloff = 1f - (d / radius)
                if (prop.type.movableByBlast) {
                    prop.vx += (dx / d) * 18f * falloff
                    prop.vz += (dz / d) * 18f * falloff
                    prop.vy = max(prop.vy, 9f * falloff + 2.5f)
                }
                if (prop.type.explosive) {
                    chainBarrels.add(prop)
                } else if (prop.type.destructible) {
                    damageProp(prop, (maxDamage * falloff).toInt(), fromPlayer = fromPlayer)
                }
            }
        }
        for (barrel in chainBarrels) {
            if (!barrel.destroyed) {
                destroyProp(barrel, fromPlayer = fromPlayer)
            }
        }

        for (car in vehicles) {
            val dx = car.x - ex
            val dz = car.z - ez
            val d = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
            if (d < radius) {
                val falloff = 1f - (d / radius)
                car.vy = max(car.vy, 8.8f * falloff)
                car.suspensionVel -= 2.2f * falloff
            }
        }

        scareNearbyPigBurgers(ex, ez, radius * 2.4f)
        for (pig in pigBurgers) {
            val dx = pig.x - ex
            val dz = pig.z - ez
            val d = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
            if (d < radius) {
                val falloff = 1f - (d / radius)
                hitPigBurger(
                    pig = pig,
                    impulseX = (dx / d) * 19f * falloff,
                    impulseZ = (dz / d) * 19f * falloff,
                    upVy = 12f * falloff + 4f,
                    dropLoot = true
                )
            }
        }

        if (playerAlive && distToPlayer < radius * 0.85f) {
            val falloff = 1f - (distToPlayer / (radius * 0.85f))
            // Fun rocket-jump / explosion blast impulse on player on foot
            if (activeVehicle == null && distToPlayer > 0.05f) {
                val kx = (playerX - ex) / distToPlayer
                val kz = (playerZ - ez) / distToPlayer
                playerVx += kx * 14f * falloff
                playerVz += kz * 14f * falloff
                playerVy = max(playerVy, 10.5f * falloff + 3.2f)
                playerGrounded = false
            }
            if (!peacefulMode && distToPlayer < radius * 0.72f) {
                val dmgFalloff = 1f - (distToPlayer / (radius * 0.72f))
                val selfScale = if (fromPlayer) 0.35f else 0.8f
                damagePlayer((maxDamage * dmgFalloff * selfScale).toInt().coerceAtLeast(5))
            }
        }
    }

    private fun recordPlayerKill(viaVehicle: Boolean, targetName: String, verb: String, suffix: String) {
        playerKills++
        playerStreak++
        playerBestStreak = max(playerBestStreak, playerStreak)
        if (viaVehicle) {
            sessionVehicleKills++
        }
        soundManager.playKillConfirm()
        pushKillFeed("$verb $targetName$suffix")
        val streakCallout = when (playerStreak) {
            2 -> "🔥 ДВОЙНОЕ УБИЙСТВО!"
            3 -> "⚡ ТРОЙНАЯ СЕРИЯ x3!"
            4 -> "💥 МЕГА-СЕРИЯ x4!"
            5 -> "👑 ДОМИНАЦИЯ x5!"
            else -> if (playerStreak >= 6) "🏆 НЕУДЕРЖИМЫЙ x$playerStreak!" else ""
        }
        if (streakCallout.isNotEmpty()) {
            killStreakBanner = streakCallout
            killStreakBannerTimer = 2.4f
        }
    }

    private fun damageBot(
        bot: BotChicken,
        amount: Int,
        byPlayer: Boolean,
        viaVehicle: Boolean,
        damageKind: DamageKind = DamageKind.NORMAL,
        hitX: Float = bot.x,
        hitY: Float = bot.y + 2.05f,
        hitZ: Float = bot.z
    ) {
        if (!bot.alive || peacefulMode) return
        bot.hp -= amount
        bot.hitFlash = 1f
        if (byPlayer) {
            hitMarkerAlpha = 1f
            if (damageKind == DamageKind.CRITICAL) critHitFlashTimer = 1f
            spawnDamagePopup(hitX, hitY, hitZ, amount, damageKind)
            soundManager.playHit()
        }
        if (bot.hp <= 0) {
            bot.hp = 0
            bot.alive = false
            bot.respawnTimer = 4.0f
            spawnDustBurst(bot.x, bot.y + 1.0f, bot.z, 0.98f, 0.98f, 0.98f, 14)
            spawnDustBurst(bot.x, bot.y + 1.2f, bot.z, 0.95f, 0.22f, 0.18f, 5)
            if (byPlayer) {
                val verb = when {
                    damageKind == DamageKind.CRITICAL -> "Хедшот!"
                    else -> "Повержен"
                }
                val suffix = if (viaVehicle) " (таран машиной!)" else ""
                recordPlayerKill(viaVehicle = viaVehicle, targetName = bot.name, verb = verb, suffix = suffix)
            }
        }
    }

    private fun damagePlayer(amount: Int) {
        if (!playerAlive || amount <= 0 || spawnProtectionTimer > 0f || isGodModeActive()) return
        playerHp = (playerHp - amount).coerceAtLeast(0)
        damageVignetteAlpha = 0.72f
        screenShake = max(screenShake, 0.2f)
        soundManager.playPlayerHurt()
        if (playerHp <= 0) {
            playerAlive = false
            hpRegenAccum = 0f
            activeVehicle = null
            aiming = false
            input.resetMotionControls()
            pushKillFeed("Ваша курица повержена!")
        }
    }

    private fun damageProp(prop: WorldProp, amount: Int, fromPlayer: Boolean) {
        if (prop.destroyed) return
        if (prop.type == PropType.TARGET) {
            prop.targetDownTimer = 2.6f
            if (fromPlayer) {
                hitMarkerAlpha = max(hitMarkerAlpha, 0.9f)
                spawnDamagePopup(prop.x, prop.y + prop.type.height + 0.35f, prop.z, amount, DamageKind.PROP)
                soundManager.playTargetDing3D(prop.x, prop.y + 1.2f, prop.z)
                pushKillFeed("🎯 Попадание по мишени!")
            }
            return
        }
        if (prop.type == PropType.BOWLING_PIN) {
            prop.vy = max(prop.vy, 6.2f)
            prop.vx += (Random.nextFloat() - 0.5f) * 9f
            prop.vz += (Random.nextFloat() - 0.5f) * 9f
            prop.tiltVel = (if (Random.nextBoolean()) 1f else -1f) * 320f
            soundManager.playBowlingStrike3D(prop.x, prop.y + 1.0f, prop.z, 0.9f)
            return
        }
        if (!prop.type.destructible) return
        prop.hp -= amount
        if (fromPlayer) {
            hitMarkerAlpha = max(hitMarkerAlpha, 0.7f)
            spawnDamagePopup(prop.x, prop.y + prop.type.height + 0.35f, prop.z, amount, DamageKind.PROP)
        }
        if (prop.hp <= 0) {
            destroyProp(prop, fromPlayer)
        }
    }

    private fun destroyProp(prop: WorldProp, fromPlayer: Boolean) {
        if (prop.destroyed) return
        prop.destroyed = true
        prop.hp = 0
        if (fromPlayer) {
            sessionPropsDestroyed++
        }
        prop.remoteId?.let {
            destroyedRemotePropIds.add(it)
            onRemotePropDestroyed?.invoke(it)
        }
        when {
            prop.type.explosive -> {
                triggerExplosion(prop.x, prop.y + 0.8f, prop.z, radius = 8.6f, maxDamage = 95, fromPlayer = fromPlayer)
            }
            prop.type.isWaterBarrel -> {
                // Bright cyan/white water splash burst + splash SFX (no fire!)
                soundManager.playWaterSplash3D(prop.x, prop.y + 0.8f, prop.z)
                spawnDustBurst(prop.x, prop.y + 0.9f, prop.z, 0.22f, 0.78f, 1.0f, 18)
                spawnDustBurst(prop.x, prop.y + 1.1f, prop.z, 0.88f, 0.96f, 1.0f, 10)
            }
            prop.type == PropType.FRAGILE_BRICK_WALL -> {
                // Brick fragments burst!
                soundManager.playCrusherImpact3D(prop.x, prop.y + 1.2f, prop.z, intensity = 0.85f)
                spawnDustBurst(prop.x, prop.y + 1.2f, prop.z, 0.76f, 0.32f, 0.22f, 20)
                spawnDustBurst(prop.x, prop.y + 1.4f, prop.z, 0.62f, 0.58f, 0.54f, 8)
            }
            else -> {
                spawnDustBurst(prop.x, prop.y + 0.9f, prop.z, 0.78f, 0.56f, 0.28f, 12)
                soundManager.playBuild3D(prop.x, prop.y + 0.9f, prop.z)
            }
        }
    }

    private fun updateExplosionsAndParticles(dt: Float) {
        val expIter = explosions.iterator()
        while (expIter.hasNext()) {
            val e = expIter.next()
            e.age += dt
            if (e.age >= e.maxAge) expIter.remove()
        }
        val partIter = particles.iterator()
        while (partIter.hasNext()) {
            val pt = partIter.next()
            pt.age += dt
            if (pt.age >= pt.maxAge) {
                partIter.remove()
                continue
            }
            pt.vy -= 16f * dt
            pt.x += pt.vx * dt
            pt.y = max(0.05f, pt.y + pt.vy * dt)
            pt.z += pt.vz * dt
        }
        val maxParticles = (if (settings.lowEndMode) 65 else (180 * settings.particleDensity).toInt()).coerceIn(45, 240)
        if (particles.size > maxParticles) {
            particles.subList(0, particles.size - maxParticles).clear()
        }
    }

    fun spawnDamagePopup(x: Float, y: Float, z: Float, amount: Int, kind: DamageKind) {
        if (amount <= 0 || !settings.showDamageNumbers) return
        val jitterX = (Random.nextFloat() - 0.5f) * 0.55f
        val jitterZ = (Random.nextFloat() - 0.5f) * 0.55f
        val riseSpeed = when (kind) {
            DamageKind.CRITICAL -> 2.15f
            DamageKind.EXPLOSIVE, DamageKind.VEHICLE_RAM -> 2.35f
            else -> 1.85f
        }
        val lifetime = when (kind) {
            DamageKind.CRITICAL, DamageKind.EXPLOSIVE, DamageKind.VEHICLE_RAM -> 0.62f
            DamageKind.PROP -> 0.45f
            else -> 0.54f
        }
        damagePopups3D.add(
            DamagePopup3D(
                id = damagePopupSeq++,
                x = x + jitterX,
                y = y,
                z = z + jitterZ,
                vx = jitterX * 1.25f,
                vy = riseSpeed,
                vz = jitterZ * 1.25f,
                amount = amount,
                kind = kind,
                age = 0f,
                maxAge = lifetime
            )
        )
        while (damagePopups3D.size > MAX_DAMAGE_POPUPS) {
            damagePopups3D.removeAt(0)
        }
    }

    private fun updateDamagePopups(dt: Float) {
        if (damagePopups3D.isEmpty()) return
        val iter = damagePopups3D.iterator()
        while (iter.hasNext()) {
            val dp = iter.next()
            dp.age += dt
            if (dp.age >= dp.maxAge) {
                iter.remove()
                continue
            }
            dp.x += dp.vx * dt
            dp.y += dp.vy * dt
            dp.z += dp.vz * dt
            dp.vx *= max(0f, 1f - dt * 3.5f)
            dp.vz *= max(0f, 1f - dt * 3.5f)
            dp.vy = max(0.35f, dp.vy - dt * 2.1f)
        }
    }

    private fun spawnDustBurst(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, count: Int) {
        val densityScale = if (settings.lowEndMode) settings.particleDensity * 0.55f else settings.particleDensity
        val scaledCount = (count * densityScale).toInt().coerceAtLeast(2)
        repeat(scaledCount) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val spd = 2.5f + Random.nextFloat() * 5.5f
            particles.add(
                Particle3D(
                    x = x, y = y, z = z,
                    vx = cos(a) * spd,
                    vy = 2.5f + Random.nextFloat() * 5.5f,
                    vz = sin(a) * spd,
                    size = 0.22f, age = 0f, maxAge = 0.45f + Random.nextFloat() * 0.25f,
                    r = r, g = g, b = b
                )
            )
        }
    }

    private fun updateBuildGhost() {
        val rad = playerYaw * PI.toFloat() / 180f
        val baseDist = when {
            buildCategory == BuildCategory.VEHICLES -> 7.6f
            buildCategory == BuildCategory.NPCS -> 4.6f
            selectedBuildProp == PropType.LOOP_THE_LOOP || selectedBuildProp == PropType.ROAD_ROUNDABOUT -> 12.5f
            selectedBuildProp == PropType.GARAGE_HANGAR || selectedBuildProp == PropType.HOUSE_SMALL ||
                selectedBuildProp == PropType.WATCHTOWER || selectedBuildProp == PropType.WALL_ARCH_GATE -> 10.5f
            selectedBuildProp == PropType.FINISH_ARCH || selectedBuildProp == PropType.ROAD ||
                selectedBuildProp == PropType.ROAD_TURN || selectedBuildProp == PropType.ROAD_RAMP -> 9.6f
            selectedBuildProp == PropType.RAMP || selectedBuildProp == PropType.DOUBLE_RAMP || selectedBuildProp == PropType.BRIDGE -> 7.6f
            selectedBuildProp == PropType.TOWER || selectedBuildProp == PropType.FIRE_RING -> 6.8f
            else -> 4.8f
        }
        // Subtle pitch-responsive distance so looking down brings placement closer, looking forward extends it
        val pitchOffset = ((playerPitch + 8f) / 35f).coerceIn(-1.4f, 3.2f)
        val dist = (baseDist + pitchOffset).coerceIn(3.2f, 15.5f)
        var rawX = clampArena(playerX + sin(rad) * dist)
        var rawZ = clampArena(playerZ - cos(rad) * dist)
        var rawYaw = (playerYaw + ghostYawOffset) % 360f
        if (buildGridSnap) {
            val step = if (buildCategory == BuildCategory.VEHICLES ||
                selectedBuildProp == PropType.ROAD || selectedBuildProp == PropType.ROAD_TURN ||
                selectedBuildProp == PropType.ROAD_ROUNDABOUT || selectedBuildProp == PropType.ROAD_RAMP
            ) 2.0f else 1.0f
            rawX = clampArena(kotlin.math.round(rawX / step) * step)
            rawZ = clampArena(kotlin.math.round(rawZ / step) * step)
            rawYaw = (kotlin.math.round(rawYaw / 45f) * 45f) % 360f
        }
        ghostX = rawX
        ghostZ = rawZ
        ghostY = getSurfaceHeightAt(ghostX, ghostZ, playerY + 2.6f)
        ghostYaw = rawYaw

        val minClearance = when {
            buildCategory == BuildCategory.VEHICLES -> max(selectedVehicleType.halfW, selectedVehicleType.halfL) + PLAYER_RADIUS + 0.6f
            buildCategory == BuildCategory.NPCS -> 1.8f
            selectedBuildProp.isPassThrough -> 1.6f
            else -> max(selectedBuildProp.halfW, selectedBuildProp.halfD) + PLAYER_RADIUS + 0.25f
        }
        val distToPlayer = hypot2(ghostX - playerX, ghostZ - playerZ)
        ghostPlacementValid = distToPlayer >= minClearance || abs(ghostY - playerY) > 1.8f
    }

    /**
     * Checks whether a local-space point `(lx, lz)` is passing through an OPEN interactive
     * door/gate (`WALL_ARCH_GATE`, `GARAGE_HANGAR`, `HOUSE_SMALL`, `WATCHTOWER`) or inside its
     * hollow interior room. Front doors/gates are always located on local `lz < 0`.
     */
    private fun isInsideOpenDoorwayOrInterior(prop: WorldProp, lx: Float, lz: Float, radius: Float): Boolean {
        if (!prop.type.hasInteractiveDoor || !prop.doorOpen) return false
        return when (prop.type) {
            PropType.WALL_ARCH_GATE -> {
                // Wide arched pass-through between side pillars (total width ~9.8m, open arch ~6.4m)
                abs(lx) < (3.20f - radius * 0.70f).coerceAtLeast(1.1f)
            }
            PropType.GARAGE_HANGAR -> {
                val inFrontGate = lz <= 0.6f && abs(lx) < (3.35f - radius * 0.65f).coerceAtLeast(1.2f)
                val inInterior = abs(lx) < (prop.type.halfW - 0.85f - radius * 0.65f) &&
                    abs(lz) < (prop.type.halfD - 0.85f - radius * 0.65f)
                inFrontGate || inInterior
            }
            PropType.HOUSE_SMALL, PropType.WATCHTOWER -> {
                val inFrontDoor = lz <= 0.4f && abs(lx) < (1.50f - radius * 0.45f).coerceAtLeast(0.75f)
                val inInterior = abs(lx) < (prop.type.halfW - 0.85f - radius * 0.55f) &&
                    abs(lz) < (prop.type.halfD - 0.85f - radius * 0.55f)
                inFrontDoor || inInterior
            }
            else -> false
        }
    }

    private fun isRampProp(type: PropType): Boolean =
        type == PropType.RAMP || type == PropType.DOUBLE_RAMP || type == PropType.ROAD_RAMP

    private fun isSolidVehicleObstacle(type: PropType): Boolean = when (type) {
        PropType.WALL,
        PropType.WALL_BRICK,
        PropType.WALL_WOOD,
        PropType.REINFORCED_FENCE,
        PropType.METAL_GATE,
        PropType.HOUSE_SMALL,
        PropType.GARAGE_HANGAR,
        PropType.WATCHTOWER,
        PropType.WALL_ARCH_GATE,
        PropType.TOWER,
        PropType.RAMP,
        PropType.DOUBLE_RAMP,
        PropType.ROAD_RAMP,
        PropType.BARRIER,
        PropType.STREET_LAMP,
        PropType.BILLBOARD_SIGN,
        PropType.RADAR_DISH,
        PropType.RADIO_MAST,
        PropType.FIRE_BRAZIER -> true
        else -> false
    }

    // --- FAST BROAD-PHASE COLLISION & RAMP SLOPE SURFACE PHYSICS (ZERO TRIG PER CALL) ---
    fun getSurfaceHeightAt(x: Float, z: Float, maxFootY: Float): Float =
        getSurfaceHeightExcluding(x, z, maxFootY, null, isRising = false)

    private fun getSurfaceHeightExcluding(
        x: Float,
        z: Float,
        maxFootY: Float,
        exclude: WorldProp?,
        isRising: Boolean = false
    ): Float {
        ensurePropGridFresh()
        var bestY = 0f
        propGrid.forEachCandidate(x, z, 0.55f, props) { prop ->
            if (prop === exclude) return@forEachCandidate
            if (prop.type.isPassThrough && !prop.type.isWalkableSurface) return@forEachCandidate
            val dx = x - prop.x
            val dz = z - prop.z
            val br = prop.boundRadius + 0.45f
            if (abs(dx) > br || abs(dz) > br) return@forEachCandidate
            val lx = dx * prop.cosYaw + dz * prop.sinYaw
            val lz = -dx * prop.sinYaw + dz * prop.cosYaw
            if (isRampProp(prop.type)) {
                if (abs(lx) <= prop.type.halfW + 0.35f && abs(lz) <= prop.type.halfD + 0.25f) {
                    val t = ((prop.type.halfD - lz) / (2f * prop.type.halfD)).coerceIn(0f, 1f)
                    val rampY = prop.y + t * prop.type.height
                    // Generous ramp climb capture so fast cars & jumping players smoothly ride ramps up into arches!
                    if (rampY <= maxFootY + 1.65f && rampY > bestY) {
                        bestY = rampY
                    }
                }
            } else if (abs(lx) <= prop.type.halfW && abs(lz) <= prop.type.halfD) {
                if (isInsideOpenDoorwayOrInterior(prop, lx, lz, 0.25f)) return@forEachCandidate
                val topY = prop.y + prop.type.height
                // Prevent jumping underneath bridges/overpasses from snapping up through the ceiling!
                val flatLimit = if (isRising) maxFootY - 0.55f else maxFootY
                if (topY <= flatLimit && topY > bestY) {
                    bestY = topY
                }
            }
        }
        return bestY
    }

    /**
     * Returns the lowest solid underside ceiling (e.g. elevated BRIDGE deck) directly above an entity
     * standing underneath it, so jumping under overpasses never phases through onto the upper deck,
     * while climbing ramps onto the bridge remains completely unobstructed.
     */
    private fun getCeilingHeight(x: Float, z: Float, footY: Float): Float {
        ensurePropGridFresh()
        var lowestCeiling = Float.MAX_VALUE
        propGrid.forEachCandidate(x, z, 0.45f, props) { prop ->
            if (prop.type != PropType.BRIDGE) return@forEachCandidate
            if (prop.y <= 0.5f || footY + 0.65f >= prop.y) return@forEachCandidate
            val dx = x - prop.x
            val dz = z - prop.z
            val br = prop.boundRadius + 0.35f
            if (abs(dx) > br || abs(dz) > br) return@forEachCandidate
            val lx = dx * prop.cosYaw + dz * prop.sinYaw
            val lz = -dx * prop.sinYaw + dz * prop.cosYaw
            if (abs(lx) <= prop.type.halfW && abs(lz) <= prop.type.halfD) {
                if (prop.y < lowestCeiling) {
                    lowestCeiling = prop.y
                }
            }
        }
        return lowestCeiling
    }

    private fun resolveHorizontalCollisionIntoScratch(x: Float, y: Float, z: Float, radius: Float) {
        ensurePropGridFresh()
        var rx = x
        var rz = z
        propGrid.forEachCandidate(x, z, radius + 0.85f, props) { prop ->
            if (prop.type == PropType.BRIDGE || prop.type.isPassThrough) return@forEachCandidate
            if (y >= prop.y + prop.type.height - 0.35f) return@forEachCandidate
            val dx = rx - prop.x
            val dz = rz - prop.z
            val broad = prop.boundRadius + radius
            if (abs(dx) > broad || abs(dz) > broad) return@forEachCandidate
            val c = prop.cosYaw
            val s = prop.sinYaw
            val lx = dx * c + dz * s
            val lz = -dx * s + dz * c
            val extX = prop.type.halfW + radius
            val extZ = prop.type.halfD + radius
            if (abs(lx) < extX && abs(lz) < extZ) {
                if (isInsideOpenDoorwayOrInterior(prop, lx, lz, radius)) return@forEachCandidate
                if (isRampProp(prop.type)) {
                    val t = ((prop.type.halfD - lz) / (2f * prop.type.halfD)).coerceIn(0f, 1f)
                    val rampY = prop.y + t * prop.type.height
                    // Allow free climbing across the entire ramp deck & entry without ever hitting an invisible wall!
                    if (lz > -prop.type.halfD * 0.72f || y >= rampY - 1.35f) return@forEachCandidate
                }
                val penX = extX - abs(lx)
                val penZ = extZ - abs(lz)
                var pushLx = 0f
                var pushLz = 0f
                if (penX < penZ) {
                    pushLx = if (lx >= 0f) penX else -penX
                } else {
                    pushLz = if (lz >= 0f) penZ else -penZ
                }
                val wx = pushLx * c - pushLz * s
                val wz = pushLx * s + pushLz * c
                rx = clampArena(rx + wx)
                rz = clampArena(rz + wz)
            }
        }
        scratchResX = rx
        scratchResZ = rz
    }

    private fun resolveHorizontalCollision(x: Float, y: Float, z: Float, radius: Float): Pair<Float, Float> {
        resolveHorizontalCollisionIntoScratch(x, y, z, radius)
        return Pair(scratchResX, scratchResZ)
    }

    private fun resolveVehicleCollisionIntoScratch(x: Float, y: Float, z: Float, carHalfW: Float) {
        ensurePropGridFresh()
        val maxExtent = ARENA_HALF_SIZE - carHalfW - 1.5f
        var rx = x
        var rz = z
        var hit = false
        if (rx < -maxExtent || rx > maxExtent) {
            rx = rx.coerceIn(-maxExtent, maxExtent)
            hit = true
        }
        if (rz < -maxExtent || rz > maxExtent) {
            rz = rz.coerceIn(-maxExtent, maxExtent)
            hit = true
        }
        propGrid.forEachCandidate(x, z, carHalfW + 1.2f, props) { prop ->
            if (prop.type.isPassThrough || prop.type == PropType.BRIDGE) return@forEachCandidate
            if (!isSolidVehicleObstacle(prop.type)) return@forEachCandidate
            if (y >= prop.y + prop.type.height - 0.4f) return@forEachCandidate
            val dx = rx - prop.x
            val dz = rz - prop.z
            val broad = prop.boundRadius + carHalfW
            if (abs(dx) > broad || abs(dz) > broad) return@forEachCandidate
            val c = prop.cosYaw
            val s = prop.sinYaw
            val lx = dx * c + dz * s
            val lz = -dx * s + dz * c
            val extX = prop.type.halfW + carHalfW
            val extZ = prop.type.halfD + carHalfW
            if (abs(lx) < extX && abs(lz) < extZ) {
                if (isInsideOpenDoorwayOrInterior(prop, lx, lz, carHalfW)) return@forEachCandidate
                if (isRampProp(prop.type)) {
                    val t = ((prop.type.halfD - lz) / (2f * prop.type.halfD)).coerceIn(0f, 1f)
                    val rampY = prop.y + t * prop.type.height
                    // Never block vehicles entering or climbing the ramp deck toward arches/rings!
                    if (lz > -prop.type.halfD * 0.72f || y >= rampY - 1.55f) return@forEachCandidate
                }
                val penX = extX - abs(lx)
                val penZ = extZ - abs(lz)
                var pushLx = 0f
                var pushLz = 0f
                if (penX < penZ) {
                    pushLx = if (lx >= 0f) penX else -penX
                } else {
                    pushLz = if (lz >= 0f) penZ else -penZ
                }
                val wx = pushLx * c - pushLz * s
                val wz = pushLx * s + pushLz * c
                rx = (rx + wx).coerceIn(-maxExtent, maxExtent)
                rz = (rz + wz).coerceIn(-maxExtent, maxExtent)
                hit = true
            }
        }
        scratchResX = rx
        scratchResZ = rz
        scratchHitWall = hit
    }

    private fun clampVehicle(v: Float, halfW: Float): Float {
        val maxExtent = ARENA_HALF_SIZE - halfW - 1.5f
        return v.coerceIn(-maxExtent, maxExtent)
    }

    private fun findPropHitByPoint(px: Float, py: Float, pz: Float): WorldProp? {
        ensurePropGridFresh()
        propGrid.forEachCandidate(px, pz, 0.35f, props) { prop ->
            if (prop.type.isPassThrough) return@forEachCandidate
            if (py < prop.y || py > prop.y + prop.type.height) return@forEachCandidate
            val dx = px - prop.x
            val dz = pz - prop.z
            val br = prop.boundRadius
            if (abs(dx) > br || abs(dz) > br) return@forEachCandidate
            val lx = dx * prop.cosYaw + dz * prop.sinYaw
            val lz = -dx * prop.sinYaw + dz * prop.cosYaw
            if (abs(lx) <= prop.type.halfW && abs(lz) <= prop.type.halfD) {
                if (isInsideOpenDoorwayOrInterior(prop, lx, lz, 0.15f)) return@forEachCandidate
                if (isRampProp(prop.type)) {
                    val t = ((prop.type.halfD - lz) / (2f * prop.type.halfD)).coerceIn(0f, 1f)
                    if (py <= prop.y + t * prop.type.height) return prop
                } else {
                    return prop
                }
            }
        }
        return null
    }

    private fun hasLineOfSight(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val dz = z2 - z1
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist < 2.0f) return true
        val steps = (dist / 2.2f).toInt().coerceIn(2, 18)
        for (s in 1 until steps) {
            val t = s.toFloat() / steps
            val hit = findPropHitByPoint(x1 + dx * t, y1 + dy * t, z1 + dz * t)
            if (hit != null && isSolidVehicleObstacle(hit.type) && !isRampProp(hit.type)) {
                return false
            }
        }
        return true
    }

    private fun findNearestVehicle(x: Float, z: Float, maxDist: Float): VehicleEntity? {
        var best: VehicleEntity? = null
        var bestD = maxDist
        for (i in vehicles.indices) {
            val v = vehicles[i]
            val d = hypot2(v.x - x, v.z - z)
            if (d < bestD) {
                bestD = d
                best = v
            }
        }
        return best
    }

    private fun pushKillFeed(msg: String) {
        killFeed.addFirst(KillFeedEntry(killFeedSeq++, msg))
        while (killFeed.size > 3) killFeed.removeLast()
    }

    private fun refreshHudSnapshot() {
        val blips = ArrayList<MinimapBlip>(bots.size + remotePlayers.size + vehicles.size + 12)
        val rad = playerYaw * PI.toFloat() / 180f
        val cosY = cos(rad)
        val sinY = sin(rad)
        val radarRange = if (activeVehicle != null) 125f else 95f

        fun addBlip(wx: Float, wz: Float, kind: BlipKind) {
            val dx = wx - playerX
            val dz = wz - playerZ
            val rx = (dx * cosY + dz * sinY) / radarRange
            val rz = (-dx * sinY + dz * cosY) / radarRange
            val mag = sqrt(rx * rx + rz * rz)
            val clampScale = if (mag > 0.92f) 0.92f / mag else 1f
            blips.add(MinimapBlip(rx * clampScale, rz * clampScale, kind))
        }

        for (i in bots.indices) {
            val b = bots[i]
            if (b.alive) addBlip(b.x, b.z, BlipKind.BOT)
        }
        for (rp in remotePlayers.values) {
            if (rp.hp > 0) addBlip(rp.x, rp.z, BlipKind.ONLINE_PLAYER)
        }
        for (i in vehicles.indices) {
            val v = vehicles[i]
            if (v !== activeVehicle) addBlip(v.x, v.z, BlipKind.VEHICLE)
        }
        for (i in props.indices) {
            val p = props[i]
            if (p.destroyed) continue
            when {
                p.type.explosive -> addBlip(p.x, p.z, BlipKind.BARREL)
                p.type.isHealPickup -> addBlip(p.x, p.z, BlipKind.PICKUP)
                else -> Unit
            }
        }
        for (i in pigBurgers.indices) {
            val pig = pigBurgers[i]
            addBlip(pig.x, pig.z, BlipKind.PIG_BURGER)
        }

        val projectedPopups = if (damagePopups3D.isEmpty() || !settings.showDamageNumbers) {
            emptyList()
        } else {
            val out = ArrayList<ProjectedDamageText>(damagePopups3D.size)
            for (i in damagePopups3D.indices) {
                val dp = damagePopups3D[i]
                if (!dp.screenVisible) continue
                out.add(
                    ProjectedDamageText(
                        id = dp.id,
                        screenX = dp.screenX,
                        screenY = dp.screenY,
                        amount = dp.amount,
                        kind = dp.kind,
                        progress = (dp.age / dp.maxAge).coerceIn(0f, 1f),
                        scale = dp.screenScale
                    )
                )
            }
            out
        }

        val car = activeVehicle
        val nearCar = nearestVehicle
        val nearPig = nearestPigBurger
        val nearDoor = nearestDoorProp
        val reloadProg = if (reloadTimer > 0f && currentWeapon.reloadSec > 0.01f) {
            (1f - (reloadTimer / currentWeapon.reloadSec)).coerceIn(0f, 1f)
        } else 0f
        val nitroFrac = when {
            car == null -> 0f
            car.type == VehicleType.BATTLE_TANK -> (1f - (fireCooldown / 1.2f)).coerceIn(0f, 1f)
            else -> if (car.nitroTimer > 0f) (car.nitroTimer / 1.8f).coerceIn(0f, 1f) else 1f
        }

        var userPlacedCount = 0
        for (i in props.indices) {
            val p = props[i]
            if (p.userPlaced && !p.destroyed) userPlacedCount++
        }
        for (i in vehicles.indices) {
            if (vehicles[i].id.startsWith("car_user_")) userPlacedCount++
        }
        for (i in pigBurgers.indices) {
            if (pigBurgers[i].userPlaced) userPlacedCount++
        }
        var botsAliveCount = 0
        for (i in bots.indices) {
            if (bots[i].alive) botsAliveCount++
        }

        latestHud = HudSnapshot(
            hp = playerHp,
            alive = playerAlive,
            peacefulMode = peacefulMode,
            spawnProtectionSec = spawnProtectionTimer,
            kills = playerKills,
            streak = playerStreak,
            killStreakBanner = killStreakBanner,
            weapon = currentWeapon,
            weaponSkin = settings.weaponSkin,
            ammo = weaponAmmo[currentWeapon] ?: 0,
            maxAmmo = currentWeapon.maxAmmo,
            reloading = reloadTimer > 0f,
            reloadProgress = reloadProg,
            weaponSpread = dynamicSpread,
            cameraMode = cameraMode,
            freeCamActive = cameraMode == CameraMode.FREE_CAM,
            vehicleCamOrbitYaw = vehicleCamOrbitYaw,
            aiming = aiming,
            inVehicle = car != null,
            nearVehicle = nearCar != null,
            activeVehicleName = car?.type?.label ?: "",
            nearVehicleName = nearCar?.type?.label ?: "",
            vehicleSpeedKmh = if (car != null) (abs(car.speed) * 3.6f).toInt() else 0,
            vehicleNitroFrac = nitroFrac,
            vehicleRpm = car?.currentRpm ?: 0f,
            vehicleGear = car?.currentGear ?: 1,
            nearPigBurger = nearPig != null,
            nearPigBurgerName = nearPig?.name ?: "",
            pigBurgerCount = pigBurgers.size,
            nearDoorProp = nearDoor != null,
            nearDoorLabel = nearDoor?.type?.doorActionLabel ?: "",
            nearDoorIsOpen = nearDoor?.doorOpen == true,
            raceActive = raceActive,
            raceLapCount = raceLapCount,
            raceCurrentLapSec = raceCurrentLapSec,
            raceBestLapSec = raceBestLapSec,
            raceBannerText = raceBannerText,
            buildModeOpen = buildModeOpen,
            buildCategory = buildCategory,
            selectedBuildProp = selectedBuildProp,
            selectedVehicleType = selectedVehicleType,
            buildGridSnap = buildGridSnap,
            ghostPlacementValid = ghostPlacementValid,
            ghostYawOffset = ghostYawOffset,
            userPlacedCount = userPlacedCount,
            hitMarkerAlpha = hitMarkerAlpha,
            critHitFlash = critHitFlashTimer > 0.05f,
            damageVignetteAlpha = damageVignetteAlpha,
            botsAlive = botsAliveCount,
            onlinePlayersCount = remotePlayers.size + if (isOnlineMode) 1 else 0,
            isOnlineMode = isOnlineMode,
            isOnlineHost = isOnlineHost,
            onlineGameModeLabel = if (isOnlineMode) onlineRoomRules.gameMode.label else "",
            canBuildProps = canLocalBuildProps(),
            canSpawnVehicles = canLocalSpawnVehicles(),
            godMode = isGodModeActive(),
            infiniteAmmo = isInfiniteAmmoActive(),
            activeMapLabel = activeMap.label,
            fps = currentFps,
            playerYawDeg = playerYaw,
            minimapBlips = blips,
            killFeed = killFeed.toList(),
            damagePopups = projectedPopups
        )
    }

    private fun clampArena(v: Float): Float = v.coerceIn(-(ARENA_HALF_SIZE - 1.2f), ARENA_HALF_SIZE - 1.2f)

    private fun hypot2(a: Float, b: Float): Float = sqrt(a * a + b * b)
}

data class LocalNetworkState(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Double,
    val pitch: Double,
    val hp: Int,
    val kills: Int,
    val weapon: String,
    val drivingVehicle: Boolean,
    val shooting: Boolean
)
