package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Spatial mixing parameters computed for a 3D sound emitter relative to the active listener.
 */
data class SpatialAudioParams(
    val leftGain: Float,
    val rightGain: Float,
    val lowPassAlpha: Float,
    val distance: Float,
    val audible: Boolean
)

/**
 * Real-time 3D Spatial Audio Manager for Chicken Gun 3D.
 *
 * Uses a single low-latency stereo PCM AudioTrack mixer (22,050 Hz, 16-bit stereo) supporting:
 * - Up to 24 simultaneous polyphonic 3D spatialized sound voices (weapon firing, explosions, impacts, horns, builds)
 * - Equal-power stereo panning, inverse-distance attenuation, air-absorption low-pass filtering, and rear warmth
 * - Live per-block re-spatialization as the listener rotates their head/camera
 * - Continuous procedural 3D vehicle engine synthesis tailored to all 5 vehicle classes (with RPM modulation,
 *   nitro turbo whine, tank track clatter, and Doppler pitch shift for passing vehicles)
 */
class SoundManager(
    startAudioThread: Boolean = true
) {
    companion object {
        const val SAMPLE_RATE = 22050
        private const val CHUNK_FRAMES = 512
        private const val MAX_VOICES = 24
        private const val MAX_ENGINE_EMITTERS = 4
        private const val SPEED_OF_SOUND = 343f
    }

    @Volatile
    var muted: Boolean = false

    @Volatile
    var paused: Boolean = false

    @Volatile
    private var appBackgrounded: Boolean = false

    @Volatile
    var masterVolume: Float = 0.88f

    @Volatile
    var sfxVolume: Float = 0.92f

    @Volatile
    var engineVolume: Float = 0.78f

    @Volatile
    var footstepsAndUiEnabled: Boolean = true

    // Thread-safe 3D Listener state updated every simulation tick
    @Volatile
    private var listenerX: Float = 0f

    @Volatile
    private var listenerY: Float = 1.6f

    @Volatile
    private var listenerZ: Float = 0f

    @Volatile
    private var listenerYawDeg: Float = 0f

    @Volatile
    private var listenerPitchDeg: Float = 0f

    @Volatile
    private var listenerVx: Float = 0f

    @Volatile
    private var listenerVz: Float = 0f

    private val running = AtomicBoolean(false)
    private val voiceLock = Any()
    private val engineLock = Any()

    private val activeVoices = ArrayList<SpatialVoice>(MAX_VOICES)
    private val engineSnapshots = ArrayList<EngineSnapshot>(MAX_ENGINE_EMITTERS)
    private val engineStates = HashMap<String, EngineOscillatorState>()
    private var mixerThread: Thread? = null

    // Pre-synthesized mono 16-bit PCM waveforms for crisp zero-allocation playback
    private val pistolPcm: ShortArray by lazy { generatePistolShot() }
    private val riflePcm: ShortArray by lazy { generateRifleShot() }
    private val rpgPcm: ShortArray by lazy { generateRpgLaunch() }
    private val tankCannonPcm: ShortArray by lazy { generateTankCannon() }
    private val explosionPcm: ShortArray by lazy { generateExplosionImpact() }
    private val collisionLightPcm: ShortArray by lazy { generateLightVehicleCollision() }
    private val collisionHeavyPcm: ShortArray by lazy { generateHeavyVehicleCollision() }
    private val collisionArmorPcm: ShortArray by lazy { generateArmoredVehicleCollision() }
    private val collisionCyberPcm: ShortArray by lazy { generateCyberVehicleCollision() }
    private val vehicleLandPcm: ShortArray by lazy { generateVehicleLandingThud() }
    private val hitPcm: ShortArray by lazy { generateBlip(0.06f, 960f, 1420f, 0.72f) }
    private val jumpPcm: ShortArray by lazy { generateBlip(0.11f, 260f, 540f, 0.55f) }
    private val buildPcm: ShortArray by lazy { generateBuildClack() }
    private val reloadPcm: ShortArray by lazy { generateReloadClick() }
    private val hornPcm: ShortArray by lazy { generateHorn() }
    private val footstepPcm: ShortArray by lazy { generateFootstep() }
    private val landingPcm: ShortArray by lazy { generateLandingThud() }
    private val killConfirmPcm: ShortArray by lazy { generateKillConfirmChime() }
    private val weaponSwitchPcm: ShortArray by lazy { generateWeaponSwitch() }
    private val playerHurtPcm: ShortArray by lazy { generatePlayerHurt() }
    private val uiClickPcm: ShortArray by lazy { generateBlip(0.032f, 1180f, 880f, 0.36f) }
    private val pigOinkPcm: ShortArray by lazy { generatePigOink() }
    private val pigSquealPcm: ShortArray by lazy { generatePigSqueal() }
    private val jumpPadPcm: ShortArray by lazy { generateJumpPadSpring() }
    private val boostPadPcm: ShortArray by lazy { generateBoostPadWhoosh() }
    private val crusherSlamPcm: ShortArray by lazy { generateCrusherSlam() }
    private val waterSplashPcm: ShortArray by lazy { generateWaterSplash() }
    private val bowlingStrikePcm: ShortArray by lazy { generateBowlingStrike() }
    private val targetDingPcm: ShortArray by lazy { generateTargetDing() }
    private val foodCrunchPcm: ShortArray by lazy { generateFoodCrunch() }
    private val drinkSipPcm: ShortArray by lazy { generateDrinkSip() }
    private val lapFanfarePcm: ShortArray by lazy { generateLapFanfare() }

    private class SpatialVoice(
        val pcm: ShortArray,
        val baseVolume: Float,
        val x: Float,
        val y: Float,
        val z: Float,
        val refDist: Float,
        val maxDist: Float,
        val spatial: Boolean,
        val playbackRate: Float
    ) {
        var cursor: Float = 0f
        var lpPrev: Float = 0f
    }

    private data class EngineSnapshot(
        val id: String,
        val type: VehicleType,
        val x: Float,
        val y: Float,
        val z: Float,
        val vx: Float,
        val vz: Float,
        val speed: Float,
        val maxSpeed: Float,
        val lateralSpeed: Float,
        val grounded: Boolean,
        val nitroActive: Boolean,
        val gasPressed: Boolean,
        val brakePressed: Boolean,
        val isPlayerDriven: Boolean
    )

    private class EngineOscillatorState(
        var primaryPhase: Double = 0.0,
        var subPhase: Double = 0.0,
        var whinePhase: Double = 0.0,
        var turboPhase: Double = 0.0,
        var tireRollPhase: Double = 0.0,
        var screechPhaseA: Double = 0.0,
        var screechPhaseB: Double = 0.0,
        var trackPhase: Double = 0.0,
        var smoothRpm: Float = 0.18f,
        var smoothGain: Float = 0f,
        var smoothTireRollGain: Float = 0f,
        var smoothScreechGain: Float = 0f,
        var lpState: Float = 0f,
        var tireNoiseLp: Float = 0f
    )

    init {
        if (startAudioThread) {
            startMixerThread()
        }
    }

    fun setAppBackgrounded(backgrounded: Boolean) {
        appBackgrounded = backgrounded
    }

    fun applySettings(settings: com.example.data.GameSettings) {
        muted = false
        masterVolume = settings.masterVolume.coerceIn(0f, 1f)
        sfxVolume = settings.sfxVolume.coerceIn(0f, 1f)
        engineVolume = settings.engineVolume.coerceIn(0f, 1f)
        footstepsAndUiEnabled = settings.footstepsAndUiEnabled
    }

    /**
     * Updates the 3D audio listener transform (matches GameEngine coordinate system:
     * yaw = 0 faces -Z, yaw = 90 faces +X, right vector = (cos(yaw), 0, sin(yaw))).
     */
    fun updateListener(
        x: Float,
        y: Float,
        z: Float,
        yawDeg: Float,
        pitchDeg: Float,
        vx: Float = 0f,
        vz: Float = 0f
    ) {
        listenerX = x
        listenerY = y
        listenerZ = z
        listenerYawDeg = yawDeg
        listenerPitchDeg = pitchDeg
        listenerVx = vx
        listenerVz = vz
    }

    /**
     * Computes equal-power stereo panning, inverse-distance rolloff, and air-absorption
     * low-pass filtering for any 3D sound emitter relative to the listener.
     */
    fun computeSpatialParams(
        sourceX: Float,
        sourceY: Float,
        sourceZ: Float,
        refDistance: Float,
        maxDistance: Float
    ): SpatialAudioParams {
        val dx = sourceX - listenerX
        val dy = sourceY - listenerY
        val dz = sourceZ - listenerZ
        val dist = sqrt(dx * dx + dy * dy + dz * dz)

        if (dist >= maxDistance) {
            return SpatialAudioParams(0f, 0f, 0.25f, dist, audible = false)
        }

        val safeRef = max(0.5f, refDistance)
        val inverseAtten = safeRef / max(safeRef, dist)
        val windowFade = (1f - (dist / maxDistance)).coerceIn(0f, 1f)
        val distanceGain = (inverseAtten * (0.35f + 0.65f * windowFade)).coerceIn(0f, 1f)

        val yawRad = listenerYawDeg * PI.toFloat() / 180f
        val fwdX = sin(yawRad)
        val fwdZ = -cos(yawRad)
        val rightX = cos(yawRad)
        val rightZ = sin(yawRad)

        val horizDist = sqrt(dx * dx + dz * dz)
        val pan = if (horizDist < 0.4f) {
            0f
        } else {
            // Smoothly taper pan width very close to listener so sounds don't hard-snap left/right
            val nearBlend = (horizDist / 2.2f).coerceIn(0f, 1f)
            val rawPan = ((dx * rightX + dz * rightZ) / horizDist).coerceIn(-1f, 1f)
            rawPan * nearBlend
        }

        val frontDot = if (horizDist < 0.4f) 1f else ((dx * fwdX + dz * fwdZ) / horizDist).coerceIn(-1f, 1f)
        // Equal-power panning law
        val panAngle = (pan + 1f) * (PI / 4.0)
        val rearAtten = if (frontDot < 0f) 1f + frontDot * 0.14f else 1f
        val leftGain = (cos(panAngle).toFloat() * distanceGain * rearAtten).coerceIn(0f, 1f)
        val rightGain = (sin(panAngle).toFloat() * distanceGain * rearAtten).coerceIn(0f, 1f)

        // Air-absorption & rear-hemisphere low-pass alpha (1.0 = full brightness, lower = warmer/muffled)
        val distFactor = (dist / maxDistance).coerceIn(0f, 1f)
        val rearMuffle = if (frontDot < 0f) (-frontDot) * 0.22f else 0f
        val lowPassAlpha = (1f - distFactor * 0.68f - rearMuffle).coerceIn(0.16f, 1f)

        return SpatialAudioParams(
            leftGain = leftGain,
            rightGain = rightGain,
            lowPassAlpha = lowPassAlpha,
            distance = dist,
            audible = (leftGain + rightGain) > 0.008f
        )
    }

    // --- 3D SPATIAL WEAPON FIRING ---
    fun playWeaponFire3D(
        weapon: WeaponType,
        x: Float,
        y: Float,
        z: Float,
        isLocalPlayer: Boolean = false
    ) {
        val pitchJitter = 0.95f + Random.nextFloat() * 0.10f
        when (weapon) {
            WeaponType.PISTOL -> enqueueVoice(
                pcm = pistolPcm,
                volume = if (isLocalPlayer) 0.54f else 0.48f,
                x = x, y = y, z = z,
                refDist = 9f,
                maxDist = 85f,
                spatial = !isLocalPlayer,
                playbackRate = pitchJitter
            )
            WeaponType.RIFLE -> enqueueVoice(
                pcm = riflePcm,
                volume = if (isLocalPlayer) 0.46f else 0.44f,
                x = x, y = y, z = z,
                refDist = 11f,
                maxDist = 105f,
                spatial = !isLocalPlayer,
                playbackRate = pitchJitter
            )
            WeaponType.RPG -> enqueueVoice(
                pcm = rpgPcm,
                volume = if (isLocalPlayer) 0.72f else 0.68f,
                x = x, y = y, z = z,
                refDist = 14f,
                maxDist = 135f,
                spatial = !isLocalPlayer,
                playbackRate = 0.97f + Random.nextFloat() * 0.06f
            )
        }
    }

    fun playTankCannon3D(x: Float, y: Float, z: Float, isLocalPlayer: Boolean = false) {
        enqueueVoice(
            pcm = tankCannonPcm,
            volume = if (isLocalPlayer) 0.88f else 0.82f,
            x = x, y = y, z = z,
            refDist = 18f,
            maxDist = 165f,
            spatial = !isLocalPlayer,
            playbackRate = 0.96f + Random.nextFloat() * 0.08f
        )
    }

    // --- 3D SPATIAL EXPLOSION IMPACTS ---
    fun playExplosion3D(
        x: Float,
        y: Float,
        z: Float,
        radius: Float = 8.4f,
        intensity: Float = 1.0f
    ) {
        val scale = (radius / 8.4f).coerceIn(0.75f, 1.45f)
        enqueueVoice(
            pcm = explosionPcm,
            volume = (0.94f * intensity).coerceIn(0.35f, 1.05f),
            x = x, y = y, z = z,
            refDist = 18f * scale,
            maxDist = 165f * scale,
            spatial = true,
            playbackRate = (0.92f + Random.nextFloat() * 0.14f) / sqrt(scale)
        )
    }

    // --- 3D SPATIAL VEHICLE ENGINE & TIRE UPDATES ---
    fun updateVehicleEngines(
        activeVehicle: VehicleEntity?,
        vehicles: List<VehicleEntity>,
        gasPressed: Boolean,
        brakePressed: Boolean = false
    ) {
        val selected = ArrayList<EngineSnapshot>(MAX_ENGINE_EMITTERS)

        if (activeVehicle != null) {
            val rad = activeVehicle.yaw * PI.toFloat() / 180f
            selected.add(
                EngineSnapshot(
                    id = activeVehicle.id,
                    type = activeVehicle.type,
                    x = activeVehicle.x,
                    y = activeVehicle.y + 0.8f,
                    z = activeVehicle.z,
                    vx = sin(rad) * activeVehicle.speed,
                    vz = -cos(rad) * activeVehicle.speed,
                    speed = abs(activeVehicle.speed),
                    maxSpeed = activeVehicle.type.maxSpeed,
                    lateralSpeed = abs(activeVehicle.lateralVel),
                    grounded = activeVehicle.grounded,
                    nitroActive = activeVehicle.nitroTimer > 0f,
                    gasPressed = gasPressed,
                    brakePressed = brakePressed,
                    isPlayerDriven = true
                )
            )
        }

        // Track up to 3 closest nearby vehicles in the 3D world (moving or nearby idling)
        val candidates = ArrayList<Pair<Float, VehicleEntity>>()
        for (v in vehicles) {
            if (v === activeVehicle) continue
            val dx = v.x - listenerX
            val dy = v.y - listenerY
            val dz = v.z - listenerZ
            val distSq = dx * dx + dy * dy + dz * dz
            val maxEngineRange = if (abs(v.speed) > 1.0f) 78f else 28f
            if (distSq < maxEngineRange * maxEngineRange) {
                candidates.add(distSq to v)
            }
        }
        candidates.sortBy { it.first }

        for (i in 0 until min(candidates.size, MAX_ENGINE_EMITTERS - selected.size)) {
            val v = candidates[i].second
            val rad = v.yaw * PI.toFloat() / 180f
            selected.add(
                EngineSnapshot(
                    id = v.id,
                    type = v.type,
                    x = v.x,
                    y = v.y + 0.8f,
                    z = v.z,
                    vx = sin(rad) * v.speed,
                    vz = -cos(rad) * v.speed,
                    speed = abs(v.speed),
                    maxSpeed = v.type.maxSpeed,
                    lateralSpeed = abs(v.lateralVel),
                    grounded = v.grounded,
                    nitroActive = v.nitroTimer > 0f,
                    gasPressed = abs(v.speed) > 2.5f,
                    brakePressed = false,
                    isPlayerDriven = false
                )
            )
        }

        synchronized(engineLock) {
            engineSnapshots.clear()
            engineSnapshots.addAll(selected)
        }
    }

    // --- ADDITIONAL 3D WORLD & VEHICLE COLLISION EFFECTS ---
    fun playVehicleCollision3D(
        x: Float,
        y: Float,
        z: Float,
        impactSpeed: Float,
        vehicleType: VehicleType = VehicleType.PICKUP_4X4,
        isLanding: Boolean = false
    ) {
        val profile = VehicleAudioCatalog.profileFor(vehicleType)
        val strength = ((impactSpeed - 3.5f) / 24f).coerceIn(0.24f, 1.0f)
        val pcm = if (isLanding) {
            vehicleLandPcm
        } else {
            when (profile.collisionStyle) {
                CollisionAcousticStyle.LIGHT_SPORT -> collisionLightPcm
                CollisionAcousticStyle.HEAVY_CHASSIS -> collisionHeavyPcm
                CollisionAcousticStyle.ARMORED_STEEL -> collisionArmorPcm
                CollisionAcousticStyle.CYBER_COMPOSITE -> collisionCyberPcm
            }
        }
        // Shift collision pitch dynamically with impact speed & vehicle mass class
        val speedPitch = (0.90f + strength * 0.24f)
        val jitter = 0.94f + Random.nextFloat() * 0.12f
        val rate = (profile.collisionPitchMultiplier * speedPitch * jitter).coerceIn(0.58f, 1.58f)
        val baseVol = if (isLanding) 0.56f else 0.72f
        enqueueVoice(
            pcm = pcm,
            volume = baseVol * strength,
            x = x, y = y, z = z,
            refDist = 11f,
            maxDist = 72f,
            spatial = true,
            playbackRate = rate
        )
    }

    fun playHorn3D(
        x: Float,
        y: Float,
        z: Float,
        isLocalPlayer: Boolean = false,
        vehicleType: VehicleType = VehicleType.PICKUP_4X4
    ) {
        val profile = VehicleAudioCatalog.profileFor(vehicleType)
        enqueueVoice(
            pcm = hornPcm,
            volume = if (isLocalPlayer) 0.46f else 0.42f,
            x = x, y = y, z = z,
            refDist = 12f,
            maxDist = 85f,
            spatial = !isLocalPlayer,
            playbackRate = profile.hornPitchMultiplier.coerceIn(0.58f, 1.50f)
        )
    }

    fun playJump3D(x: Float, y: Float, z: Float, isLocalPlayer: Boolean = true) {
        enqueueVoice(
            pcm = jumpPcm,
            volume = 0.34f,
            x = x, y = y, z = z,
            refDist = 6f,
            maxDist = 35f,
            spatial = !isLocalPlayer,
            playbackRate = 0.96f + Random.nextFloat() * 0.08f
        )
    }

    fun playBuild3D(x: Float, y: Float, z: Float) {
        enqueueVoice(
            pcm = buildPcm,
            volume = 0.44f,
            x = x, y = y, z = z,
            refDist = 8f,
            maxDist = 55f,
            spatial = true,
            playbackRate = 0.94f + Random.nextFloat() * 0.12f
        )
    }

    fun playReload() {
        enqueueVoice(
            pcm = reloadPcm,
            volume = 0.42f,
            x = listenerX, y = listenerY, z = listenerZ,
            refDist = 5f,
            maxDist = 20f,
            spatial = false,
            playbackRate = 1.0f
        )
    }

    fun playHit() {
        enqueueVoice(
            pcm = hitPcm,
            volume = 0.48f,
            x = listenerX, y = listenerY, z = listenerZ,
            refDist = 5f,
            maxDist = 20f,
            spatial = false,
            playbackRate = 0.97f + Random.nextFloat() * 0.06f
        )
    }

    fun playKillConfirm() {
        enqueueVoice(
            pcm = killConfirmPcm,
            volume = 0.54f,
            x = listenerX, y = listenerY, z = listenerZ,
            refDist = 5f,
            maxDist = 20f,
            spatial = false,
            playbackRate = 1.0f
        )
    }

    fun playWeaponSwitch() {
        if (!footstepsAndUiEnabled) return
        enqueueVoice(
            pcm = weaponSwitchPcm,
            volume = 0.38f,
            x = listenerX, y = listenerY, z = listenerZ,
            refDist = 5f,
            maxDist = 20f,
            spatial = false,
            playbackRate = 0.96f + Random.nextFloat() * 0.08f
        )
    }

    fun playPlayerHurt() {
        enqueueVoice(
            pcm = playerHurtPcm,
            volume = 0.56f,
            x = listenerX, y = listenerY, z = listenerZ,
            refDist = 5f,
            maxDist = 20f,
            spatial = false,
            playbackRate = 0.94f + Random.nextFloat() * 0.12f
        )
    }

    fun playFootstep3D(x: Float, y: Float, z: Float, sprinting: Boolean = false, isLocalPlayer: Boolean = true) {
        if (!footstepsAndUiEnabled) return
        enqueueVoice(
            pcm = footstepPcm,
            volume = if (sprinting) 0.30f else 0.21f,
            x = x, y = y, z = z,
            refDist = 3f,
            maxDist = 22f,
            spatial = !isLocalPlayer,
            playbackRate = (if (sprinting) 1.08f else 0.94f) + Random.nextFloat() * 0.14f
        )
    }

    fun playLanding3D(x: Float, y: Float, z: Float, impactSpeed: Float, isLocalPlayer: Boolean = true) {
        if (!footstepsAndUiEnabled) return
        val strength = ((impactSpeed - 5f) / 14f).coerceIn(0.25f, 1f)
        enqueueVoice(
            pcm = landingPcm,
            volume = 0.24f + strength * 0.32f,
            x = x, y = y, z = z,
            refDist = 4f,
            maxDist = 28f,
            spatial = !isLocalPlayer,
            playbackRate = 0.90f + Random.nextFloat() * 0.16f
        )
    }

    fun playUiClick() {
        if (!footstepsAndUiEnabled) return
        enqueueVoice(
            pcm = uiClickPcm,
            volume = 0.28f,
            x = listenerX, y = listenerY, z = listenerZ,
            refDist = 5f,
            maxDist = 20f,
            spatial = false,
            playbackRate = 1.0f
        )
    }

    // --- NEW MECHANICS & INTERACTIVE WORLD 3D SFX ---
    fun playPigOink3D(x: Float, y: Float, z: Float, panicked: Boolean = false) {
        enqueueVoice(
            pcm = if (panicked) pigSquealPcm else pigOinkPcm,
            volume = if (panicked) 0.64f else 0.48f,
            x = x, y = y, z = z,
            refDist = 9f,
            maxDist = 55f,
            spatial = true,
            playbackRate = (if (panicked) 1.05f else 0.94f) + Random.nextFloat() * 0.16f
        )
    }

    fun playJumpPad3D(x: Float, y: Float, z: Float, isLocalPlayer: Boolean = true) {
        enqueueVoice(
            pcm = jumpPadPcm,
            volume = 0.66f,
            x = x, y = y, z = z,
            refDist = 10f,
            maxDist = 65f,
            spatial = !isLocalPlayer,
            playbackRate = 0.96f + Random.nextFloat() * 0.10f
        )
    }

    fun playBoostPad3D(x: Float, y: Float, z: Float, isLocalPlayer: Boolean = true) {
        enqueueVoice(
            pcm = boostPadPcm,
            volume = 0.68f,
            x = x, y = y, z = z,
            refDist = 12f,
            maxDist = 75f,
            spatial = !isLocalPlayer,
            playbackRate = 0.98f + Random.nextFloat() * 0.08f
        )
    }

    fun playCrusherImpact3D(x: Float, y: Float, z: Float, intensity: Float = 1.0f) {
        enqueueVoice(
            pcm = crusherSlamPcm,
            volume = (0.72f * intensity.coerceIn(0.4f, 1.35f)).coerceAtMost(0.95f),
            x = x, y = y, z = z,
            refDist = 12f,
            maxDist = 70f,
            spatial = true,
            playbackRate = 0.90f + Random.nextFloat() * 0.16f
        )
    }

    fun playWaterSplash3D(x: Float, y: Float, z: Float) {
        enqueueVoice(
            pcm = waterSplashPcm,
            volume = 0.64f,
            x = x, y = y, z = z,
            refDist = 10f,
            maxDist = 60f,
            spatial = true,
            playbackRate = 0.92f + Random.nextFloat() * 0.16f
        )
    }

    fun playBowlingStrike3D(x: Float, y: Float, z: Float, intensity: Float = 1.0f) {
        enqueueVoice(
            pcm = bowlingStrikePcm,
            volume = (0.62f * intensity.coerceIn(0.4f, 1.35f)).coerceAtMost(0.90f),
            x = x, y = y, z = z,
            refDist = 10f,
            maxDist = 65f,
            spatial = true,
            playbackRate = 0.92f + Random.nextFloat() * 0.18f
        )
    }

    fun playTargetDing3D(x: Float, y: Float, z: Float) {
        enqueueVoice(
            pcm = targetDingPcm,
            volume = 0.58f,
            x = x, y = y, z = z,
            refDist = 11f,
            maxDist = 70f,
            spatial = true,
            playbackRate = 0.96f + Random.nextFloat() * 0.12f
        )
    }

    fun playFoodCrunch3D(x: Float, y: Float, z: Float, isDrink: Boolean = false) {
        enqueueVoice(
            pcm = if (isDrink) drinkSipPcm else foodCrunchPcm,
            volume = 0.58f,
            x = x, y = y, z = z,
            refDist = 6f,
            maxDist = 30f,
            spatial = false,
            playbackRate = 0.95f + Random.nextFloat() * 0.10f
        )
    }

    fun playLapFanfare(isRecord: Boolean = false) {
        enqueueVoice(
            pcm = lapFanfarePcm,
            volume = if (isRecord) 0.72f else 0.58f,
            x = listenerX, y = listenerY, z = listenerZ,
            refDist = 10f,
            maxDist = 40f,
            spatial = false,
            playbackRate = if (isRecord) 1.08f else 1.0f
        )
    }

    fun release() {
        running.set(false)
        mixerThread?.interrupt()
        mixerThread = null
        synchronized(voiceLock) { activeVoices.clear() }
        synchronized(engineLock) {
            engineSnapshots.clear()
            engineStates.clear()
        }
    }

    private fun enqueueVoice(
        pcm: ShortArray,
        volume: Float,
        x: Float,
        y: Float,
        z: Float,
        refDist: Float,
        maxDist: Float,
        spatial: Boolean,
        playbackRate: Float
    ) {
        if (muted || paused || appBackgrounded) return
        if (spatial) {
            val preview = computeSpatialParams(x, y, z, refDist, maxDist)
            if (!preview.audible) return
        }
        synchronized(voiceLock) {
            if (activeVoices.size >= MAX_VOICES) {
                activeVoices.removeAt(0)
            }
            activeVoices.add(
                SpatialVoice(
                    pcm = pcm,
                    baseVolume = volume,
                    x = x,
                    y = y,
                    z = z,
                    refDist = refDist,
                    maxDist = maxDist,
                    spatial = spatial,
                    playbackRate = playbackRate.coerceIn(0.55f, 1.65f)
                )
            )
        }
    }

    private fun startMixerThread() {
        if (!running.compareAndSet(false, true)) return
        val thread = Thread({ runAudioMixerLoop() }, "SoundManager-3D-Mixer").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }
        mixerThread = thread
        thread.start()
    }

    private fun runAudioMixerLoop() {
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return
            val bufferBytes = max(minBuf, CHUNK_FRAMES * 4 * 2)

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build()

            track.play()

            val leftMix = FloatArray(CHUNK_FRAMES)
            val rightMix = FloatArray(CHUNK_FRAMES)
            val interleaved = ShortArray(CHUNK_FRAMES * 2)
            val localEngines = ArrayList<EngineSnapshot>(MAX_ENGINE_EMITTERS)

            while (running.get()) {
                val silent = muted || paused || appBackgrounded
                if (silent) {
                    synchronized(voiceLock) { activeVoices.clear() }
                    synchronized(engineLock) {
                        for (st in engineStates.values) {
                            st.smoothGain = 0f
                            st.smoothTireRollGain = 0f
                            st.smoothScreechGain = 0f
                        }
                    }
                    if (appBackgrounded) {
                        try {
                            Thread.sleep(24L)
                        } catch (_: InterruptedException) {
                            break
                        }
                    } else {
                        interleaved.fill(0)
                        val written = track.write(interleaved, 0, interleaved.size)
                        if (written < 0) break
                    }
                    continue
                }

                leftMix.fill(0f)
                rightMix.fill(0f)

                val mVol = masterVolume.coerceIn(0f, 1f)
                val sfxScale = mVol * sfxVolume.coerceIn(0f, 1f)

                // 1. Mix all active 3D one-shot voices (re-spatialized per 23ms chunk)
                synchronized(voiceLock) {
                    var idx = activeVoices.size - 1
                    while (idx >= 0) {
                        val v = activeVoices[idx]
                        val params = if (v.spatial) {
                            computeSpatialParams(v.x, v.y, v.z, v.refDist, v.maxDist)
                        } else {
                            SpatialAudioParams(0.707f, 0.707f, 1.0f, 0f, audible = true)
                        }

                        val lGain = params.leftGain * v.baseVolume * sfxScale
                        val rGain = params.rightGain * v.baseVolume * sfxScale
                        val lpAlpha = params.lowPassAlpha
                        val pcm = v.pcm
                        val pcmLast = pcm.size - 1
                        var cursor = v.cursor
                        var lp = v.lpPrev

                        for (f in 0 until CHUNK_FRAMES) {
                            val i0 = cursor.toInt()
                            if (i0 >= pcmLast) {
                                cursor = pcm.size.toFloat()
                                break
                            }
                            val frac = cursor - i0
                            val raw = (pcm[i0] + (pcm[i0 + 1] - pcm[i0]) * frac) / 32768f
                            lp += (raw - lp) * lpAlpha
                            leftMix[f] += lp * lGain
                            rightMix[f] += lp * rGain
                            cursor += v.playbackRate
                        }

                        v.cursor = cursor
                        v.lpPrev = lp
                        if (cursor >= pcmLast) {
                            activeVoices.removeAt(idx)
                        }
                        idx--
                    }
                }

                // 2. Synthesize & spatialize continuous 3D vehicle engines
                localEngines.clear()
                synchronized(engineLock) {
                    localEngines.addAll(engineSnapshots)
                }
                if (localEngines.isNotEmpty()) {
                    mixVehicleEngines(localEngines, leftMix, rightMix)
                }

                // 3. Soft-clip master bus into interleaved stereo 16-bit PCM
                for (f in 0 until CHUNK_FRAMES) {
                    val l = tanh(leftMix[f].toDouble()).toFloat()
                    val r = tanh(rightMix[f].toDouble()).toFloat()
                    interleaved[f * 2] = (l * 31000f).toInt().coerceIn(-32767, 32767).toShort()
                    interleaved[f * 2 + 1] = (r * 31000f).toInt().coerceIn(-32767, 32767).toShort()
                }

                val written = track.write(interleaved, 0, interleaved.size)
                if (written < 0) break
            }
        } catch (_: InterruptedException) {
            // Normal shutdown
        } catch (_: Throwable) {
            // Gracefully ignore audio backend unavailability on headless devices
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun mixVehicleEngines(
        emitters: List<EngineSnapshot>,
        leftMix: FloatArray,
        rightMix: FloatArray
    ) {
        val activeIds = HashSet<String>(emitters.size)
        val engineBusScale = masterVolume.coerceIn(0f, 1f) * engineVolume.coerceIn(0f, 1f)
        val twoPiOverSr = (2.0 * PI) / SAMPLE_RATE

        for (e in emitters) {
            activeIds.add(e.id)
            val state = synchronized(engineLock) { engineStates.getOrPut(e.id) { EngineOscillatorState() } }
            val profile = VehicleAudioCatalog.profileFor(e.type)
            val metrics = VehicleAudioCatalog.computeMetrics(
                type = e.type,
                speed = e.speed,
                maxSpeed = e.maxSpeed,
                nitroActive = e.nitroActive,
                gasPressed = e.gasPressed,
                brakePressed = e.brakePressed,
                lateralSpeed = e.lateralSpeed,
                grounded = e.grounded
            )

            val params = if (e.isPlayerDriven) {
                SpatialAudioParams(0.707f, 0.707f, 0.84f, 0f, audible = true)
            } else {
                computeSpatialParams(
                    sourceX = e.x,
                    sourceY = e.y,
                    sourceZ = e.z,
                    refDistance = 8.0f,
                    maxDistance = if (e.speed > 1.0f) 78f else 28f
                )
            }

            // Realistic Doppler pitch shift for passing 3D vehicles
            val doppler = if (!e.isPlayerDriven && params.distance > 0.5f) {
                val dx = e.x - listenerX
                val dz = e.z - listenerZ
                val invD = 1f / max(0.5f, sqrt(dx * dx + dz * dz))
                val relRadialVel = ((e.vx - listenerVx) * dx + (e.vz - listenerVz) * dz) * invD
                (SPEED_OF_SOUND / (SPEED_OF_SOUND + relRadialVel).coerceIn(260f, 430f)).coerceIn(0.84f, 1.22f)
            } else 1.0f

            val speedRatio = (e.speed / max(1f, e.maxSpeed)).coerceIn(0f, 1.35f)
            val baseLevel = when {
                e.isPlayerDriven -> 0.21f + speedRatio * 0.14f + (if (e.gasPressed) 0.04f else 0f) + (if (e.nitroActive) 0.07f else 0f)
                e.speed > 1.0f -> 0.24f + speedRatio * 0.10f
                else -> 0.09f
            }
            val targetGain = if (params.audible) baseLevel * engineBusScale else 0f
            val targetTireRollGain = if (params.audible) metrics.tireRollGain * engineBusScale else 0f
            val targetScreechGain = if (params.audible) metrics.tireScreechGain * engineBusScale else 0f

            val lRatio = params.leftGain
            val rRatio = params.rightGain
            val lpAlpha = (params.lowPassAlpha * profile.engineLpScale).coerceIn(0.14f, 0.84f)
            val tireRollHz = metrics.tireRollFreqHz * doppler
            val screechHzA = metrics.tireScreechFreqHz * doppler
            val screechHzB = screechHzA * 1.185f

            for (f in 0 until CHUNK_FRAMES) {
                state.smoothRpm += (metrics.rpm - state.smoothRpm) * 0.0032f
                state.smoothGain += (targetGain - state.smoothGain) * 0.0045f
                state.smoothTireRollGain += (targetTireRollGain - state.smoothTireRollGain) * 0.0042f
                state.smoothScreechGain += (targetScreechGain - state.smoothScreechGain) * 0.0065f

                if (state.smoothGain < 0.0008f && state.smoothTireRollGain < 0.0008f && state.smoothScreechGain < 0.0008f) {
                    continue
                }

                // 1. Per-sample RPM-shifted engine fundamental & vehicle-class harmonics
                val currentHz = (profile.idleHz + state.smoothRpm * profile.rpmHzSpan) * doppler
                state.primaryPhase += twoPiOverSr * currentHz
                state.subPhase += twoPiOverSr * (currentHz * 0.5)
                if (state.primaryPhase > 2.0 * PI) state.primaryPhase -= 2.0 * PI
                if (state.subPhase > 2.0 * PI) state.subPhase -= 2.0 * PI

                val p = state.primaryPhase
                val s1 = sin(p).toFloat()
                val s2 = sin(p * 2.0).toFloat() * profile.harmonic2
                val s3 = sin(p * 3.0).toFloat() * profile.harmonic3
                val s4 = sin(p * 4.0).toFloat() * profile.harmonic4
                val sub = sin(state.subPhase).toFloat() * profile.subWeight
                val noiseSample = Random.nextFloat() * 2f - 1f
                val exhaustChop = noiseSample * profile.exhaustGrit * (0.28f + state.smoothRpm * 0.42f)
                var engineSample = (s1 + s2 + s3 + s4 + sub + exhaustChop) * 0.42f

                // Electric / turbine / supercharger whine (Cyber Supercar, Sports, Police, Tank turbine)
                if (profile.electricWhineWeight > 0.01f) {
                    val whineHz = (profile.electricWhineBaseHz * (0.45f + state.smoothRpm * 1.15f)) * doppler
                    state.whinePhase += twoPiOverSr * whineHz
                    if (state.whinePhase > 2.0 * PI) state.whinePhase -= 2.0 * PI
                    engineSample += sin(state.whinePhase).toFloat() * profile.electricWhineWeight * (0.35f + state.smoothRpm * 0.65f)
                }

                if (e.nitroActive) {
                    val turboHz = (620.0 + state.smoothRpm * 380.0) * doppler
                    state.turboPhase += twoPiOverSr * turboHz
                    if (state.turboPhase > 2.0 * PI) state.turboPhase -= 2.0 * PI
                    engineSample += sin(state.turboPhase).toFloat() * 0.24f
                }

                state.lpState += (engineSample - state.lpState) * lpAlpha
                var mixedSample = state.lpState * state.smoothGain

                // 2. Speed-pitched tire rolling & track clatter layer
                if (state.smoothTireRollGain > 0.001f) {
                    state.tireRollPhase += twoPiOverSr * tireRollHz
                    if (state.tireRollPhase > 2.0 * PI) state.tireRollPhase -= 2.0 * PI

                    val tireLpCoeff = when (profile.tireStyle) {
                        TireAcousticStyle.STREET_SPORT -> 0.22f
                        TireAcousticStyle.CYBER_SLICK -> 0.28f
                        TireAcousticStyle.OFFROAD_KNOBBY -> 0.16f
                        TireAcousticStyle.HEAVY_DUAL -> 0.14f
                        TireAcousticStyle.TANK_TRACKS -> 0.30f
                    }
                    state.tireNoiseLp += (noiseSample - state.tireNoiseLp) * tireLpCoeff

                    val tireSample = when (profile.tireStyle) {
                        TireAcousticStyle.TANK_TRACKS -> {
                            val trackHz = (5.8 + e.speed * 1.45) * doppler
                            state.trackPhase += twoPiOverSr * trackHz
                            if (state.trackPhase > 2.0 * PI) state.trackPhase -= 2.0 * PI
                            val clackPulse = if (state.trackPhase < 0.62) 0.72f else 0.08f
                            state.tireNoiseLp * clackPulse + sin(state.tireRollPhase).toFloat() * 0.28f
                        }
                        TireAcousticStyle.OFFROAD_KNOBBY -> {
                            val treadThrum = sin(state.tireRollPhase).toFloat() * 0.42f +
                                sin(state.tireRollPhase * 2.0).toFloat() * 0.18f
                            treadThrum + state.tireNoiseLp * 0.85f
                        }
                        TireAcousticStyle.HEAVY_DUAL -> {
                            sin(state.tireRollPhase).toFloat() * 0.36f + state.tireNoiseLp * 0.75f
                        }
                        TireAcousticStyle.STREET_SPORT, TireAcousticStyle.CYBER_SLICK -> {
                            sin(state.tireRollPhase).toFloat() * 0.22f + state.tireNoiseLp * 0.92f
                        }
                    }
                    mixedSample += tireSample * state.smoothTireRollGain
                }

                // 3. Drift / hard-braking / launch tire screech & gravel skid layer
                if (state.smoothScreechGain > 0.001f) {
                    state.screechPhaseA += twoPiOverSr * screechHzA
                    state.screechPhaseB += twoPiOverSr * screechHzB
                    if (state.screechPhaseA > 2.0 * PI) state.screechPhaseA -= 2.0 * PI
                    if (state.screechPhaseB > 2.0 * PI) state.screechPhaseB -= 2.0 * PI

                    val screechSample = when (profile.tireStyle) {
                        TireAcousticStyle.OFFROAD_KNOBBY -> {
                            // Sandy/gravelly off-road drift skid + muted tire howl
                            val howl = (sin(state.screechPhaseA).toFloat() * 0.35f + sin(state.screechPhaseB).toFloat() * 0.22f)
                            howl + state.tireNoiseLp * 1.15f
                        }
                        TireAcousticStyle.TANK_TRACKS -> {
                            // Metallic track pivot/skid grind
                            (sin(state.screechPhaseA).toFloat() * 0.48f + noiseSample * 0.42f) * 0.75f
                        }
                        else -> {
                            // Crisp dual-band rubber tire screech on asphalt
                            val squeal = sin(state.screechPhaseA).toFloat() * 0.52f +
                                sin(state.screechPhaseB).toFloat() * 0.38f +
                                sin(state.screechPhaseA * 1.5).toFloat() * 0.18f
                            squeal * 0.68f + state.tireNoiseLp * 0.45f
                        }
                    }
                    mixedSample += screechSample * state.smoothScreechGain
                }

                leftMix[f] += mixedSample * lRatio
                rightMix[f] += mixedSample * rRatio
            }
        }

        // Prune states for vehicles no longer nearby
        synchronized(engineLock) {
            if (engineStates.size > MAX_ENGINE_EMITTERS * 2) {
                val iter = engineStates.keys.iterator()
                while (iter.hasNext()) {
                    if (iter.next() !in activeIds) iter.remove()
                }
            }
        }
    }

    // --- PROCEDURAL WAVEFORM SYNTHESIZERS ---
    private fun generatePistolShot(): ShortArray {
        val count = (SAMPLE_RATE * 0.13f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val snapEnv = exp(-t * 42f)
            val bodyEnv = exp(-t * 24f)
            val freq = 380f * exp(-t * 18f) + 75f
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val punch = sin(phase).toFloat() * bodyEnv * 0.55f
            val crack = (Random.nextFloat() * 2f - 1f) * (snapEnv * 0.65f + bodyEnv * 0.25f)
            out[i] = ((punch + crack).coerceIn(-1f, 1f) * 30000f).toInt().toShort()
        }
        return out
    }

    private fun generateRifleShot(): ShortArray {
        val count = (SAMPLE_RATE * 0.095f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 34f)
            val freq = 260f * exp(-t * 16f) + 58f
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val thump = sin(phase).toFloat() * 0.42f
            val bolt = sin(2.0 * PI * 1450.0 * t).toFloat() * exp(-t * 95f) * 0.28f
            val crack = (Random.nextFloat() * 2f - 1f) * 0.72f
            out[i] = (((thump + crack) * env + bolt).coerceIn(-1f, 1f) * 30000f).toInt().toShort()
        }
        return out
    }

    private fun generateRpgLaunch(): ShortArray {
        val count = (SAMPLE_RATE * 0.40f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val tubePop = sin(2.0 * PI * (145.0 * exp(-t * 9.0) + 45.0) * t).toFloat() * exp(-t * 12f)
            val whooshEnv = sin((t / 0.40f) * PI).toFloat() * exp(-t * 2.8f)
            val noise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.84f + noise * 0.16f
            val sample = tubePop * 0.65f + lp * 2.2f * whooshEnv
            out[i] = (sample.coerceIn(-1f, 1f) * 31000f).toInt().toShort()
        }
        return out
    }

    private fun generateTankCannon(): ShortArray {
        val count = (SAMPLE_RATE * 0.58f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val crack = (Random.nextFloat() * 2f - 1f) * exp(-t * 28f) * 0.85f
            val rumbleNoise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.92f + rumbleNoise * 0.08f
            val subBoom = sin(2.0 * PI * (92.0 * exp(-t * 4.5) + 32.0) * t).toFloat() * exp(-t * 4.2f)
            val breechRing = sin(2.0 * PI * 420.0 * t).toFloat() * exp(-t * 16f) * 0.18f
            val sample = crack + lp * 2.4f * exp(-t * 4.6f) + subBoom * 0.9f + breechRing
            out[i] = (sample.coerceIn(-1f, 1f) * 31500f).toInt().toShort()
        }
        return out
    }

    private fun generateExplosionImpact(): ShortArray {
        val count = (SAMPLE_RATE * 0.85f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val initialCrack = (Random.nextFloat() * 2f - 1f) * exp(-t * 32f) * 0.9f
            val noise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.93f + noise * 0.07f
            val tailEnv = exp(-t * 3.4f)
            val subShock = sin(2.0 * PI * (78.0 * exp(-t * 3.2) + 26.0) * t).toFloat() * exp(-t * 3.8f)
            val debris = if (t > 0.08f && Random.nextFloat() < 0.04f) (Random.nextFloat() * 2f - 1f) * tailEnv * 0.55f else 0f
            val sample = initialCrack + lp * 2.5f * tailEnv + subShock * 0.85f + debris
            out[i] = (sample.coerceIn(-1f, 1f) * 31500f).toInt().toShort()
        }
        return out
    }

    private fun generateLightVehicleCollision(): ShortArray {
        val count = (SAMPLE_RATE * 0.22f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 17f)
            val noise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.74f + noise * 0.26f
            // Crisp sheet-metal crunch + bumper snap + light glass/debris rattle
            val crunch = (sin(2.0 * PI * 310.0 * t) + sin(2.0 * PI * 545.0 * t) * 0.55).toFloat() * env * 0.44f
            val snap = noise * exp(-t * 44f) * 0.52f
            val thud = sin(2.0 * PI * (135.0 * exp(-t * 12.0) + 48.0) * t).toFloat() * env * 0.45f
            out[i] = ((lp * 1.45f * env + crunch + snap + thud).coerceIn(-1f, 1f) * 29500f).toInt().toShort()
        }
        return out
    }

    private fun generateHeavyVehicleCollision(): ShortArray {
        val count = (SAMPLE_RATE * 0.29f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 12.5f)
            val noise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.86f + noise * 0.14f
            // Heavy frame thud + deep fender crunch + suspension clunk
            val frameBoom = sin(2.0 * PI * (98.0 * exp(-t * 9.0) + 32.0) * t).toFloat() * exp(-t * 10f) * 0.72f
            val metalCrunch = (sin(2.0 * PI * 185.0 * t) + sin(2.0 * PI * 295.0 * t) * 0.6).toFloat() * env * 0.42f
            out[i] = ((lp * 1.7f * env + frameBoom + metalCrunch).coerceIn(-1f, 1f) * 30500f).toInt().toShort()
        }
        return out
    }

    private fun generateArmoredVehicleCollision(): ShortArray {
        val count = (SAMPLE_RATE * 0.34f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 9.5f)
            val noise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.88f + noise * 0.12f
            // Thick steel armor clang + heavy kinetic shockwave
            val armorRing = (sin(2.0 * PI * 240.0 * t) * 0.55 + sin(2.0 * PI * 412.0 * t) * 0.35 + sin(2.0 * PI * 688.0 * t) * 0.18).toFloat() * exp(-t * 11f)
            val subShock = sin(2.0 * PI * (76.0 * exp(-t * 7.5) + 28.0) * t).toFloat() * env * 0.78f
            out[i] = ((lp * 1.5f * env + armorRing * 0.58f + subShock).coerceIn(-1f, 1f) * 31000f).toInt().toShort()
        }
        return out
    }

    private fun generateCyberVehicleCollision(): ShortArray {
        val count = (SAMPLE_RATE * 0.24f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 15f)
            val noise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.76f + noise * 0.24f
            // Carbon composite crack + futuristic dampener shimmer pulse
            val shimmer = sin(2.0 * PI * (780.0 * exp(-t * 11.0) + 260.0) * t).toFloat() * exp(-t * 18f) * 0.44f
            val bass = sin(2.0 * PI * (120.0 * exp(-t * 10.0) + 44.0) * t).toFloat() * env * 0.62f
            out[i] = ((lp * 1.35f * env + shimmer + bass).coerceIn(-1f, 1f) * 30000f).toInt().toShort()
        }
        return out
    }

    private fun generateVehicleLandingThud(): ShortArray {
        val count = (SAMPLE_RATE * 0.22f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val noise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.87f + noise * 0.13f
            // Rubber tires slapping ground + coil-spring suspension compression thump
            val tireSlap = lp * 1.6f * exp(-t * 22f)
            val springThump = sin(2.0 * PI * (92.0 * exp(-t * 11.0) + 34.0) * t).toFloat() * exp(-t * 13f) * 0.84f
            val chassisClunk = sin(2.0 * PI * 195.0 * t).toFloat() * exp(-t * 26f) * 0.25f
            out[i] = ((tireSlap + springThump + chassisClunk).coerceIn(-1f, 1f) * 30000f).toInt().toShort()
        }
        return out
    }

    private fun generateBuildClack(): ShortArray {
        val count = (SAMPLE_RATE * 0.09f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 38f)
            val wood = sin(2.0 * PI * (460.0 - t * 1800.0).coerceAtLeast(180.0) * t).toFloat()
            val tap = (Random.nextFloat() * 2f - 1f) * exp(-t * 75f) * 0.35f
            out[i] = ((wood * env * 0.7f + tap).coerceIn(-1f, 1f) * 26000f).toInt().toShort()
        }
        return out
    }

    private fun generateReloadClick(): ShortArray {
        val count = (SAMPLE_RATE * 0.18f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val click1 = if (t < 0.045f) sin(2.0 * PI * 1150.0 * t).toFloat() * exp(-t * 85f) else 0f
            val t2 = t - 0.095f
            val click2 = if (t2 >= 0f) sin(2.0 * PI * 1580.0 * t2).toFloat() * exp(-t2 * 70f) else 0f
            out[i] = (((click1 + click2) * 0.75f).coerceIn(-1f, 1f) * 26000f).toInt().toShort()
        }
        return out
    }

    private fun generateBlip(durationSec: Float, startFreq: Float, endFreq: Float, gain: Float): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val frac = i.toFloat() / count
            val freq = startFreq + (endFreq - startFreq) * frac
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = (1f - frac)
            out[i] = ((sin(phase).toFloat() * env * gain).coerceIn(-1f, 1f) * 28000f).toInt().toShort()
        }
        return out
    }

    private fun generateHorn(): ShortArray {
        val count = (SAMPLE_RATE * 0.24f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = if (i < 280) i / 280f else if (i > count - 600) (count - i) / 600f else 1f
            val s1 = sin(2.0 * PI * 370.0 * t).toFloat()
            val s2 = sin(2.0 * PI * 466.0 * t).toFloat()
            val s3 = sin(2.0 * PI * 740.0 * t).toFloat() * 0.22f
            out[i] = (((s1 + s2 + s3) * 0.40f * env).coerceIn(-1f, 1f) * 26000f).toInt().toShort()
        }
        return out
    }

    private fun generateFootstep(): ShortArray {
        val count = (SAMPLE_RATE * 0.065f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val thump = sin(2.0 * PI * (155.0 - 80.0 * (t / 0.065)) * t).toFloat() * exp(-t * 58f) * 0.55f
            val grit = (Random.nextFloat() * 2f - 1f) * exp(-t * 72f) * 0.45f
            lp += (grit - lp) * 0.34f
            out[i] = ((thump + lp).coerceIn(-1f, 1f) * 24000f).toInt().toShort()
        }
        return out
    }

    private fun generateLandingThud(): ShortArray {
        val count = (SAMPLE_RATE * 0.14f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val body = sin(2.0 * PI * (115.0 * exp(-t * 14f) + 38.0) * t).toFloat() * exp(-t * 26f) * 0.78f
            val dirt = (Random.nextFloat() * 2f - 1f) * exp(-t * 36f) * 0.38f
            lp += (dirt - lp) * 0.25f
            out[i] = ((body + lp).coerceIn(-1f, 1f) * 29000f).toInt().toShort()
        }
        return out
    }

    private fun generateKillConfirmChime(): ShortArray {
        val count = (SAMPLE_RATE * 0.20f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val note1 = if (t < 0.085f) {
                sin(2.0 * PI * 988.0 * t).toFloat() * exp(-t * 28f) * 0.65f
            } else 0f
            val t2 = t - 0.065f
            val note2 = if (t2 >= 0f) {
                (sin(2.0 * PI * 1480.0 * t2).toFloat() * 0.70f +
                    sin(2.0 * PI * 2220.0 * t2).toFloat() * 0.25f) * exp(-t2 * 22f)
            } else 0f
            out[i] = ((note1 + note2).coerceIn(-1f, 1f) * 28000f).toInt().toShort()
        }
        return out
    }

    private fun generateWeaponSwitch(): ShortArray {
        val count = (SAMPLE_RATE * 0.11f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val slide = (Random.nextFloat() * 2f - 1f) * exp(-t * 48f) * 0.30f
            val click = sin(2.0 * PI * 1850.0 * t).toFloat() * exp(-t * 75f) * 0.55f
            val t2 = t - 0.045f
            val lock = if (t2 >= 0f) sin(2.0 * PI * 1240.0 * t2).toFloat() * exp(-t2 * 90f) * 0.50f else 0f
            out[i] = ((slide + click + lock).coerceIn(-1f, 1f) * 25000f).toInt().toShort()
        }
        return out
    }

    private fun generatePlayerHurt(): ShortArray {
        val count = (SAMPLE_RATE * 0.15f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val low = sin(2.0 * PI * (135.0 * exp(-t * 10f) + 45.0) * t).toFloat() * exp(-t * 24f) * 0.85f
            val snap = (Random.nextFloat() * 2f - 1f) * exp(-t * 65f) * 0.35f
            out[i] = ((low + snap).coerceIn(-1f, 1f) * 30000f).toInt().toShort()
        }
        return out
    }

    private fun generatePigOink(): ShortArray {
        val count = (SAMPLE_RATE * 0.28f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val burst1 = if (t < 0.11f) sin(t / 0.11f * PI).toFloat() else 0f
            val t2 = t - 0.13f
            val burst2 = if (t2 in 0f..0.13f) sin(t2 / 0.13f * PI).toFloat() else 0f
            val env = max(burst1, burst2)
            val freq = 145.0 + 75.0 * sin(t * 28.0) + (if (t > 0.13f) 35.0 else 0.0)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val nasal = sin(phase).toFloat() * 0.55f +
                sin(phase * 2.0).toFloat() * 0.35f +
                sin(phase * 3.0).toFloat() * 0.22f
            val snort = (Random.nextFloat() * 2f - 1f) * 0.18f
            val s = tanh((nasal + snort) * 1.45) .toFloat() * env
            out[i] = (s.coerceIn(-1f, 1f) * 28000f).toInt().toShort()
        }
        return out
    }

    private fun generatePigSqueal(): ShortArray {
        val count = (SAMPLE_RATE * 0.36f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val frac = i.toFloat() / count
            val env = sin(frac * PI).toFloat().coerceIn(0f, 1f) * exp(-t * 1.6f)
            val pitch = 560.0 + 420.0 * sin(frac * PI * 1.1) + 55.0 * sin(t * 70.0)
            phase += 2.0 * PI * pitch / SAMPLE_RATE
            val sig = sin(phase).toFloat() * 0.62f + sin(phase * 2.0).toFloat() * 0.28f + sin(phase * 3.0).toFloat() * 0.14f
            out[i] = (tanh(sig * 1.35).toFloat() * env * 27000f).toInt().toShort()
        }
        return out
    }

    private fun generateJumpPadSpring(): ShortArray {
        val count = (SAMPLE_RATE * 0.34f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 7.5f)
            val wobble = sin(2.0 * PI * 17.0 * t) * 75.0 * exp(-t * 3.5f)
            val freq = 180.0 + t * 920.0 + wobble
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val s = (sin(phase).toFloat() * 0.72f + sin(phase * 2.0).toFloat() * 0.28f) * env
            out[i] = (s.coerceIn(-1f, 1f) * 28500f).toInt().toShort()
        }
        return out
    }

    private fun generateBoostPadWhoosh(): ShortArray {
        val count = (SAMPLE_RATE * 0.42f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val frac = i.toFloat() / count
            val env = sin(frac * PI).toFloat() * exp(-t * 1.8f)
            val freq = 260.0 + frac * 1180.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val turbine = sin(phase).toFloat() * 0.48f + sin(phase * 1.5).toFloat() * 0.24f
            val jet = (Random.nextFloat() * 2f - 1f) * 0.55f
            lp += (jet - lp) * (0.12f + frac * 0.32f)
            out[i] = ((turbine + lp) * env * 28000f).toInt().toShort()
        }
        return out
    }

    private fun generateCrusherSlam(): ShortArray {
        val count = (SAMPLE_RATE * 0.32f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val sub = sin(2.0 * PI * (92.0 * exp(-t * 12f) + 32.0) * t).toFloat() * exp(-t * 10f) * 0.85f
            val clang = (sin(2.0 * PI * 318.0 * t).toFloat() + sin(2.0 * PI * 547.0 * t).toFloat() * 0.6f) * exp(-t * 18f) * 0.38f
            val noise = (Random.nextFloat() * 2f - 1f) * exp(-t * 28f) * 0.45f
            lp += (noise - lp) * 0.35f
            out[i] = (tanh((sub + clang + lp) * 1.4).toFloat() * 30000f).toInt().toShort()
        }
        return out
    }

    private fun generateWaterSplash(): ShortArray {
        val count = (SAMPLE_RATE * 0.42f).toInt()
        val out = ShortArray(count)
        var lp = 0f
        var hpPrev = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = (1f - exp(-t * 55f)) * exp(-t * 6.5f)
            val n = Random.nextFloat() * 2f - 1f
            lp += (n - lp) * 0.26f
            val band = lp - hpPrev * 0.55f
            hpPrev = lp
            val bubble = sin(2.0 * PI * (420.0 + 360.0 * sin(t * 26.0)) * t).toFloat() * exp(-t * 9f) * 0.22f
            out[i] = ((band * 1.25f + bubble) * env * 27000f).toInt().toShort()
        }
        return out
    }

    private fun generateBowlingStrike(): ShortArray {
        val count = (SAMPLE_RATE * 0.38f).toInt()
        val out = ShortArray(count)
        val hitTimes = floatArrayOf(0.0f, 0.032f, 0.068f, 0.115f, 0.17f)
        val freqs = doubleArrayOf(780.0, 920.0, 660.0, 1080.0, 840.0)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            var acc = 0f
            for (k in hitTimes.indices) {
                val dt = t - hitTimes[k]
                if (dt >= 0f) {
                    val env = exp(-dt * 32f)
                    acc += (sin(2.0 * PI * freqs[k] * dt).toFloat() * 0.45f +
                        sin(2.0 * PI * freqs[k] * 1.47 * dt).toFloat() * 0.25f +
                        (Random.nextFloat() * 2f - 1f) * exp(-dt * 90f) * 0.28f) * env
                }
            }
            out[i] = (tanh(acc * 0.85).toFloat() * 28000f).toInt().toShort()
        }
        return out
    }

    private fun generateTargetDing(): ShortArray {
        val count = (SAMPLE_RATE * 0.26f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 14f)
            val bell = sin(2.0 * PI * 1320.0 * t).toFloat() * 0.55f +
                sin(2.0 * PI * 2640.0 * t).toFloat() * 0.28f +
                sin(2.0 * PI * 3520.0 * t).toFloat() * 0.15f
            out[i] = (bell * env * 27000f).toInt().toShort()
        }
        return out
    }

    private fun generateFoodCrunch(): ShortArray {
        val count = (SAMPLE_RATE * 0.24f).toInt()
        val out = ShortArray(count)
        val bites = floatArrayOf(0f, 0.08f, 0.15f)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            var acc = 0f
            for (b in bites) {
                val dt = t - b
                if (dt in 0f..0.065f) {
                    val env = exp(-dt * 48f)
                    val crackle = (Random.nextFloat() * 2f - 1f) * (if ((i % 7) < 3) 1f else 0.35f)
                    acc += crackle * env * 0.72f
                }
            }
            out[i] = (acc.coerceIn(-1f, 1f) * 28000f).toInt().toShort()
        }
        return out
    }

    private fun generateDrinkSip(): ShortArray {
        val count = (SAMPLE_RATE * 0.28f).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = sin((i.toFloat() / count) * PI).toFloat()
            val fizz = (Random.nextFloat() * 2f - 1f) * 0.26f
            val freq = 340.0 + 380.0 * (i.toFloat() / count) + 80.0 * sin(t * 44.0)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val gulp = sin(phase).toFloat() * 0.45f
            out[i] = ((gulp + fizz) * env * 25000f).toInt().toShort()
        }
        return out
    }

    private fun generateLapFanfare(): ShortArray {
        val count = (SAMPLE_RATE * 0.68f).toInt()
        val out = ShortArray(count)
        // Bright arcade brass arpeggio: C5 -> E5 -> G5 -> C6 chord!
        val notes = arrayOf(
            Triple(0.00f, 0.14f, 523.25),
            Triple(0.11f, 0.15f, 659.25),
            Triple(0.22f, 0.16f, 783.99),
            Triple(0.33f, 0.35f, 1046.50),
            Triple(0.33f, 0.35f, 783.99),
            Triple(0.33f, 0.35f, 523.25)
        )
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            var sum = 0f
            for ((start, dur, freq) in notes) {
                val dt = t - start
                if (dt in 0f..dur) {
                    val env = min(1f, dt / 0.012f) * exp(-dt * 4.5f)
                    val brass = sin(2.0 * PI * freq * dt).toFloat() * 0.52f +
                        sin(2.0 * PI * freq * 2.0 * dt).toFloat() * 0.24f +
                        sin(2.0 * PI * freq * 3.0 * dt).toFloat() * 0.12f
                    sum += brass * env * 0.38f
                }
            }
            out[i] = (tanh(sum.toDouble()).toFloat() * 29000f).toInt().toShort()
        }
        return out
    }
}

/**
 * Energetic procedural arcade-funk synth music loop for the Main Menu & Lobby screens.
 * Synthesized completely in real time via AudioTrack (zero external MP3 assets),
 * fades in smoothly in lobby and fades out cleanly when entering gameplay.
 */
object LobbyMusicPlayer {
    private const val SAMPLE_RATE = 22050
    private const val CHUNK_FRAMES = 1024
    private const val BPM = 128.0
    private const val STEP_SECONDS = 60.0 / BPM / 4.0 // 16th-note grid

    @Volatile private var targetPlaying: Boolean = false
    @Volatile private var appBackgrounded: Boolean = false
    @Volatile private var masterVolume: Float = 0.88f
    @Volatile private var musicVolume: Float = 0.72f

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    // Catchy 32-step (2-bar) upbeat Chicken Gun / arcade action melody & bassline (MIDI notes)
    private val leadMidi = intArrayOf(
        72, 0, 75, 79, 0, 79, 82, 79,
        77, 0, 75, 72, 75, 0, 74, 0,
        72, 0, 75, 79, 84, 0, 82, 79,
        82, 0, 79, 77, 75, 74, 72, 0
    )
    private val bassMidi = intArrayOf(
        48, 0, 48, 0, 51, 0, 55, 0,
        53, 0, 53, 0, 55, 0, 50, 0,
        48, 0, 48, 0, 51, 0, 55, 58,
        53, 0, 55, 0, 58, 0, 55, 0
    )
    private val chordRoots = intArrayOf(60, 63, 65, 67) // Cm -> Eb -> Fm -> Gm stabs

    fun updateSettings(settings: com.example.data.GameSettings) {
        masterVolume = settings.masterVolume.coerceIn(0f, 1f)
        musicVolume = settings.musicVolume.coerceIn(0f, 1f)
    }

    fun setLobbyActive(active: Boolean) {
        targetPlaying = active
        if (active && running.compareAndSet(false, true)) {
            startThread()
        }
    }

    fun setAppBackgrounded(bg: Boolean) {
        appBackgrounded = bg
    }

    fun stopAndRelease() {
        targetPlaying = false
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    private fun midiToHz(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    private fun startThread() {
        thread = Thread({
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) {
                    running.set(false)
                    return@Thread
                }
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuf, CHUNK_FRAMES * 4 * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                val outBuf = ShortArray(CHUNK_FRAMES * 2)

                var sampleClock = 0L
                var fadeGain = 0f
                var leadPhase = 0.0
                var bassPhase = 0.0
                var chordPhaseA = 0.0
                var chordPhaseB = 0.0
                var chordPhaseC = 0.0
                var noiseSeed = 1337
                val samplesPerStep = (STEP_SECONDS * SAMPLE_RATE).toInt().coerceAtLeast(1)

                while (running.get()) {
                    val wantVol = if (targetPlaying && !appBackgrounded) {
                        (masterVolume * musicVolume * 0.34f).coerceIn(0f, 0.45f)
                    } else 0f

                    // If faded completely silent while inactive, sleep gently without spinning CPU
                    if (wantVol <= 0.0005f && fadeGain <= 0.0005f) {
                        try {
                            Thread.sleep(45L)
                        } catch (_: InterruptedException) {
                            break
                        }
                        continue
                    }

                    val fadeRate = if (wantVol > fadeGain) 0.00022f else 0.00045f

                    for (f in 0 until CHUNK_FRAMES) {
                        fadeGain += (wantVol - fadeGain).coerceIn(-fadeRate, fadeRate)

                        val stepIdx = ((sampleClock / samplesPerStep) % 32L).toInt()
                        val stepSample = (sampleClock % samplesPerStep).toInt()
                        val stepT = stepSample.toFloat() / SAMPLE_RATE
                        val stepFrac = stepSample.toFloat() / samplesPerStep

                        // 1. Punchy 4-on-the-floor kick drum on beats 0, 4, 8, 12...
                        var drums = 0f
                        if ((stepIdx and 3) == 0 && stepT < 0.11f) {
                            val kEnv = exp(-stepT * 28f)
                            val kFreq = 46.0 + 105.0 * exp(-stepT * 42f)
                            drums += sin(2.0 * PI * kFreq * stepT).toFloat() * kEnv * 0.62f
                        }
                        // 2. Crisp snare on steps 4, 12, 20, 28
                        if ((stepIdx % 8) == 4 && stepT < 0.10f) {
                            noiseSeed = noiseSeed * 1664525 + 1013904223
                            val rnd = ((noiseSeed ushr 16) and 0x7FFF) / 16384f - 1f
                            drums += (rnd * 0.42f + sin(2.0 * PI * 210.0 * stepT).toFloat() * 0.25f) * exp(-stepT * 34f)
                        }
                        // 3. Driving offbeat hi-hat
                        if ((stepIdx and 1) == 1 && stepT < 0.04f) {
                            noiseSeed = noiseSeed * 1664525 + 1013904223
                            val rnd = ((noiseSeed ushr 16) and 0x7FFF) / 16384f - 1f
                            drums += rnd * exp(-stepT * 95f) * 0.16f
                        }

                        // 4. Funky saw-sine bassline
                        val bNote = bassMidi[stepIdx]
                        var bass = 0f
                        if (bNote > 0) {
                            bassPhase += 2.0 * PI * midiToHz(bNote) / SAMPLE_RATE
                            if (bassPhase > 2.0 * PI) bassPhase -= 2.0 * PI
                            val bEnv = min(1f, stepT / 0.008f) * exp(-stepT * 7.5f)
                            bass = (sin(bassPhase).toFloat() * 0.65f + sin(bassPhase * 2.0).toFloat() * 0.28f) * bEnv * 0.44f
                        }

                        // 5. Energetic brassy arcade lead melody
                        val lNote = leadMidi[stepIdx]
                        var leadL = 0f
                        var leadR = 0f
                        if (lNote > 0) {
                            val vibrato = 1.0 + 0.004 * sin(stepT * 36.0)
                            leadPhase += 2.0 * PI * midiToHz(lNote) * vibrato / SAMPLE_RATE
                            if (leadPhase > 2.0 * PI) leadPhase -= 2.0 * PI
                            val lEnv = min(1f, stepT / 0.006f) * (1f - stepFrac * 0.45f) * exp(-stepT * 4.2f)
                            val sq = if (sin(leadPhase) >= 0.0) 0.35f else -0.35f
                            val wave = (sin(leadPhase).toFloat() * 0.55f + sq * 0.45f) * lEnv * 0.34f
                            leadL = wave * 0.92f
                            leadR = wave * 1.08f
                        }

                        // 6. Syncopated synth chord stabs
                        val chordBar = (stepIdx / 8) % chordRoots.size
                        val stab = (stepIdx % 8 == 2 || stepIdx % 8 == 6)
                        var padL = 0f
                        var padR = 0f
                        if (stab) {
                            val root = chordRoots[chordBar]
                            chordPhaseA += 2.0 * PI * midiToHz(root) / SAMPLE_RATE
                            chordPhaseB += 2.0 * PI * midiToHz(root + 3) / SAMPLE_RATE
                            chordPhaseC += 2.0 * PI * midiToHz(root + 7) / SAMPLE_RATE
                            val cEnv = exp(-stepT * 10f) * 0.16f
                            val cWave = (sin(chordPhaseA) + sin(chordPhaseB) + sin(chordPhaseC)).toFloat() * cEnv
                            padL = cWave * 1.12f
                            padR = cWave * 0.88f
                        }

                        val mixL = tanh(((drums + bass + leadL + padL) * fadeGain * 1.35f).toDouble()).toFloat()
                        val mixR = tanh(((drums + bass + leadR + padR) * fadeGain * 1.35f).toDouble()).toFloat()
                        outBuf[f * 2] = (mixL * 32000f).toInt().coerceIn(-32767, 32767).toShort()
                        outBuf[f * 2 + 1] = (mixR * 32000f).toInt().coerceIn(-32767, 32767).toShort()
                        sampleClock++
                    }

                    val written = track.write(outBuf, 0, outBuf.size)
                    if (written < 0) break
                }
            } catch (_: Throwable) {
            } finally {
                running.set(false)
                try {
                    track?.stop()
                } catch (_: Throwable) {
                }
                try {
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }, "LobbyMusicSynthThread").apply {
            isDaemon = true
            start()
        }
    }
}

