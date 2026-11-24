package com.example.engine

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.roundToInt

/**
 * Tactile haptic controller contract used by [GameEngine] for weapon recoil,
 * tank cannon fire, vehicle collisions/landings/ramming, and nearby explosions.
 */
interface HapticController {
    var enabled: Boolean
    var paused: Boolean

    fun onWeaponRecoil(weapon: WeaponType)
    fun onTankCannonRecoil()
    fun onVehicleCollision(impactSpeed: Float, vehicleType: VehicleType, isLanding: Boolean = false)
    fun onVehicleRam(impactSpeed: Float, vehicleType: VehicleType)
    fun onExplosionShockwave(distance: Float)
    fun cancel()
}

/**
 * Safe default controller for headless simulations and tests that do not inspect haptics.
 */
object NoOpHapticController : HapticController {
    override var enabled: Boolean = true
    override var paused: Boolean = false
    override fun onWeaponRecoil(weapon: WeaponType) = Unit
    override fun onTankCannonRecoil() = Unit
    override fun onVehicleCollision(impactSpeed: Float, vehicleType: VehicleType, isLanding: Boolean) = Unit
    override fun onVehicleRam(impactSpeed: Float, vehicleType: VehicleType) = Unit
    override fun onExplosionShockwave(distance: Float) = Unit
    override fun cancel() = Unit
}

/**
 * Immutable vibration waveform profile (timings in ms, amplitudes in 0..255).
 * Single-element arrays correspond to a crisp one-shot pulse; multi-element arrays
 * produce multi-stage mechanical/impact waveforms.
 */
data class HapticWaveform(
    val timingsMs: LongArray,
    val amplitudes: IntArray,
    val priority: Int,
    val cooldownMs: Long
) {
    val isOneShot: Boolean get() = timingsMs.size == 1 && amplitudes.size == 1
    val peakAmplitude: Int get() = amplitudes.maxOrNull() ?: 0
    val totalDurationMs: Long get() = timingsMs.sum()
}

/**
 * Pure tactile profile catalog mapping weapons, vehicle weights, and collision velocities
 * to crisp, distinct vibration patterns.
 */
object HapticFeedbackCatalog {

    fun weaponRecoilProfile(weapon: WeaponType): HapticWaveform = when (weapon) {
        WeaponType.PISTOL -> HapticWaveform(
            timingsMs = longArrayOf(22L),
            amplitudes = intArrayOf(140),
            priority = 2,
            cooldownMs = 55L
        )
        WeaponType.RIFLE -> HapticWaveform(
            timingsMs = longArrayOf(18L),
            amplitudes = intArrayOf(118),
            priority = 2,
            cooldownMs = 68L
        )
        WeaponType.RPG -> HapticWaveform(
            // Two-stage launch kick: sharp ignition snap + heavy rocket backblast thump
            timingsMs = longArrayOf(0L, 38L, 20L, 58L),
            amplitudes = intArrayOf(0, 240, 0, 175),
            priority = 4,
            cooldownMs = 180L
        )
    }

    fun tankCannonProfile(): HapticWaveform = HapticWaveform(
        // Heavy armored recoil boom + breech kickback
        timingsMs = longArrayOf(0L, 52L, 22L, 72L),
        amplitudes = intArrayOf(0, 255, 0, 195),
        priority = 5,
        cooldownMs = 220L
    )

    fun vehicleWeightFactor(vehicleType: VehicleType): Float = when (vehicleType) {
        VehicleType.BATTLE_TANK -> 1.30f
        VehicleType.FIRE_TRUCK -> 1.24f
        VehicleType.ARMORED_SWAT -> 1.20f
        VehicleType.MONSTER_TRUCK -> 1.18f
        VehicleType.PICKUP_4X4, VehicleType.POLICE_SUV -> 1.05f
        VehicleType.SPORTS_CAR, VehicleType.CYBER_SUPERCAR -> 0.96f
        VehicleType.DUNE_BUGGY -> 0.88f
    }

    fun vehicleCollisionProfile(
        impactSpeed: Float,
        vehicleType: VehicleType,
        isLanding: Boolean = false
    ): HapticWaveform {
        val weight = vehicleWeightFactor(vehicleType)
        val norm = ((impactSpeed - 4.0f) / 24.0f).coerceIn(0.08f, 1.0f)
        val weighted = (norm * weight).coerceIn(0.12f, 1.0f)

        if (isLanding) {
            val durMs = (22f + weighted * 32f).roundToInt().toLong()
            val amp = (90f + weighted * 130f).roundToInt().coerceIn(70, 235)
            return HapticWaveform(
                timingsMs = longArrayOf(durMs),
                amplitudes = intArrayOf(amp),
                priority = if (weighted > 0.65f) 4 else 3,
                cooldownMs = 130L
            )
        }

        // High-speed crunch produces a two-stage metallic slam + secondary frame shudder
        if (impactSpeed >= 18f || weighted >= 0.82f) {
            val slamAmp = (195f + weighted * 60f).roundToInt().coerceIn(195, 255)
            val tailAmp = (115f + weighted * 70f).roundToInt().coerceIn(110, 200)
            val slamMs = (36f + weighted * 22f).roundToInt().toLong()
            val tailMs = (42f + weighted * 34f).roundToInt().toLong()
            return HapticWaveform(
                timingsMs = longArrayOf(0L, slamMs, 18L, tailMs),
                amplitudes = intArrayOf(0, slamAmp, 0, tailAmp),
                priority = 4,
                cooldownMs = 145L
            )
        }

        // Light-to-medium bump or scrape
        val durMs = (20f + weighted * 34f).roundToInt().toLong()
        val amp = (82f + weighted * 140f).roundToInt().coerceIn(75, 230)
        return HapticWaveform(
            timingsMs = longArrayOf(durMs),
            amplitudes = intArrayOf(amp),
            priority = 3,
            cooldownMs = 115L
        )
    }

    fun vehicleRamProfile(impactSpeed: Float, vehicleType: VehicleType): HapticWaveform {
        val weight = vehicleWeightFactor(vehicleType)
        val norm = ((impactSpeed - 4.0f) / 22.0f).coerceIn(0.15f, 1.0f)
        val durMs = (20f + norm * 24f * weight).roundToInt().coerceIn(18, 48).toLong()
        val amp = (105f + norm * 110f * weight).roundToInt().coerceIn(95, 240)
        return HapticWaveform(
            timingsMs = longArrayOf(durMs),
            amplitudes = intArrayOf(amp),
            priority = 3,
            cooldownMs = 110L
        )
    }

    fun explosionShockwaveProfile(distance: Float): HapticWaveform? {
        if (distance >= 26f) return null
        val proximity = ((26f - distance) / 26f).coerceIn(0.12f, 1.0f)
        if (proximity > 0.55f) {
            val peak = (165f + proximity * 90f).roundToInt().coerceIn(165, 255)
            val rumble = (85f + proximity * 80f).roundToInt().coerceIn(85, 175)
            return HapticWaveform(
                timingsMs = longArrayOf(0L, 46L, 20L, (52f + proximity * 45f).roundToInt().toLong()),
                amplitudes = intArrayOf(0, peak, 0, rumble),
                priority = 5,
                cooldownMs = 180L
            )
        }
        return HapticWaveform(
            timingsMs = longArrayOf((26f + proximity * 32f).roundToInt().toLong()),
            amplitudes = intArrayOf((80f + proximity * 120f).roundToInt().coerceIn(75, 210)),
            priority = 3,
            cooldownMs = 140L
        )
    }
}

/**
 * Hardware-backed Android [HapticController] using [VibratorManager] (API 31+)
 * and [VibrationEffect] waveforms (API 26+) with safe priority throttling.
 */
class AndroidHapticController(context: Context) : HapticController {
    private val vibrator: Vibrator? = run {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    private val hasHardwareVibrator: Boolean = try {
        vibrator?.hasVibrator() == true
    } catch (_: Throwable) {
        false
    }

    private val supportsAmplitude: Boolean = try {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (vibrator?.hasAmplitudeControl() == true)
    } catch (_: Throwable) {
        false
    }

    @Volatile
    override var enabled: Boolean = true

    @Volatile
    override var paused: Boolean = false

    private val lock = Any()
    private var lastTriggerMs: Long = 0L
    private var lastCooldownMs: Long = 0L
    private var lastPriority: Int = 0

    override fun onWeaponRecoil(weapon: WeaponType) {
        play(HapticFeedbackCatalog.weaponRecoilProfile(weapon))
    }

    override fun onTankCannonRecoil() {
        play(HapticFeedbackCatalog.tankCannonProfile())
    }

    override fun onVehicleCollision(impactSpeed: Float, vehicleType: VehicleType, isLanding: Boolean) {
        if (impactSpeed < 4.5f) return
        play(HapticFeedbackCatalog.vehicleCollisionProfile(impactSpeed, vehicleType, isLanding))
    }

    override fun onVehicleRam(impactSpeed: Float, vehicleType: VehicleType) {
        if (impactSpeed < 4.5f) return
        play(HapticFeedbackCatalog.vehicleRamProfile(impactSpeed, vehicleType))
    }

    override fun onExplosionShockwave(distance: Float) {
        val waveform = HapticFeedbackCatalog.explosionShockwaveProfile(distance) ?: return
        play(waveform)
    }

    override fun cancel() {
        synchronized(lock) {
            lastPriority = 0
            lastCooldownMs = 0L
        }
        if (!hasHardwareVibrator) return
        try {
            vibrator?.cancel()
        } catch (_: Throwable) {
        }
    }

    private fun play(waveform: HapticWaveform) {
        if (!enabled || paused || !hasHardwareVibrator) return
        val vib = vibrator ?: return

        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val elapsed = now - lastTriggerMs
            // Respect cooldown unless a strictly higher-priority impact arrives
            if (elapsed < lastCooldownMs && waveform.priority <= lastPriority) {
                return
            }
            lastTriggerMs = now
            lastCooldownMs = waveform.cooldownMs
            lastPriority = waveform.priority
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (waveform.isOneShot) {
                    val amp = if (supportsAmplitude) waveform.amplitudes[0].coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
                    VibrationEffect.createOneShot(waveform.timingsMs[0].coerceAtLeast(1L), amp)
                } else {
                    if (supportsAmplitude) {
                        VibrationEffect.createWaveform(waveform.timingsMs, waveform.amplitudes, -1)
                    } else {
                        VibrationEffect.createWaveform(waveform.timingsMs, -1)
                    }
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(waveform.totalDurationMs.coerceAtLeast(15L))
            }
        } catch (_: SecurityException) {
            // Gracefully ignore if platform blocks vibration
        } catch (_: RuntimeException) {
        }
    }
}
