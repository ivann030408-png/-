package com.example.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class TireAcousticStyle {
    STREET_SPORT,
    OFFROAD_KNOBBY,
    HEAVY_DUAL,
    TANK_TRACKS,
    CYBER_SLICK
}

enum class CollisionAcousticStyle {
    LIGHT_SPORT,
    HEAVY_CHASSIS,
    ARMORED_STEEL,
    CYBER_COMPOSITE
}

/**
 * Distinct acoustic signature for each of the 9 vehicles covering:
 * - Engine fundamental frequency range, harmonic recipe, simulated gear count & turbo/electric character
 * - Tire rolling surface hum & drift/brake screech frequencies
 * - Vehicle-class collision impact style, mass pitch scaling & horn pitch
 */
data class VehicleAudioProfile(
    val idleHz: Float,
    val rpmHzSpan: Float,
    val gearCount: Int,
    val harmonic2: Float,
    val harmonic3: Float,
    val harmonic4: Float,
    val subWeight: Float,
    val exhaustGrit: Float,
    val electricWhineWeight: Float,
    val electricWhineBaseHz: Float,
    val engineLpScale: Float,
    val tireStyle: TireAcousticStyle,
    val tireRollBaseHz: Float,
    val tireRollGainScale: Float,
    val tireScreechBaseHz: Float,
    val tireScreechGainScale: Float,
    val collisionStyle: CollisionAcousticStyle,
    val collisionPitchMultiplier: Float,
    val hornPitchMultiplier: Float
)

data class VehicleAudioMetrics(
    val rpm: Float,
    val gear: Int,
    val enginePitchHz: Float,
    val tireRollGain: Float,
    val tireRollFreqHz: Float,
    val tireScreechGain: Float,
    val tireScreechFreqHz: Float
)

object VehicleAudioCatalog {
    fun profileFor(type: VehicleType): VehicleAudioProfile = when (type) {
        VehicleType.SPORTS_CAR -> VehicleAudioProfile(
            idleHz = 62f,
            rpmHzSpan = 148f,
            gearCount = 5,
            harmonic2 = 0.56f,
            harmonic3 = 0.34f,
            harmonic4 = 0.16f,
            subWeight = 0.38f,
            exhaustGrit = 0.18f,
            electricWhineWeight = 0.08f,
            electricWhineBaseHz = 480f,
            engineLpScale = 0.80f,
            tireStyle = TireAcousticStyle.STREET_SPORT,
            tireRollBaseHz = 115f,
            tireRollGainScale = 0.22f,
            tireScreechBaseHz = 860f,
            tireScreechGainScale = 0.36f,
            collisionStyle = CollisionAcousticStyle.LIGHT_SPORT,
            collisionPitchMultiplier = 1.14f,
            hornPitchMultiplier = 1.16f
        )
        VehicleType.MONSTER_TRUCK -> VehicleAudioProfile(
            idleHz = 30f,
            rpmHzSpan = 74f,
            gearCount = 3,
            harmonic2 = 0.48f,
            harmonic3 = 0.28f,
            harmonic4 = 0.10f,
            subWeight = 0.78f,
            exhaustGrit = 0.32f,
            electricWhineWeight = 0f,
            electricWhineBaseHz = 0f,
            engineLpScale = 0.58f,
            tireStyle = TireAcousticStyle.OFFROAD_KNOBBY,
            tireRollBaseHz = 56f,
            tireRollGainScale = 0.32f,
            tireScreechBaseHz = 430f,
            tireScreechGainScale = 0.28f,
            collisionStyle = CollisionAcousticStyle.HEAVY_CHASSIS,
            collisionPitchMultiplier = 0.76f,
            hornPitchMultiplier = 0.68f
        )
        VehicleType.PICKUP_4X4 -> VehicleAudioProfile(
            idleHz = 40f,
            rpmHzSpan = 90f,
            gearCount = 4,
            harmonic2 = 0.46f,
            harmonic3 = 0.22f,
            harmonic4 = 0.08f,
            subWeight = 0.58f,
            exhaustGrit = 0.22f,
            electricWhineWeight = 0f,
            electricWhineBaseHz = 0f,
            engineLpScale = 0.64f,
            tireStyle = TireAcousticStyle.OFFROAD_KNOBBY,
            tireRollBaseHz = 74f,
            tireRollGainScale = 0.25f,
            tireScreechBaseHz = 540f,
            tireScreechGainScale = 0.26f,
            collisionStyle = CollisionAcousticStyle.HEAVY_CHASSIS,
            collisionPitchMultiplier = 0.94f,
            hornPitchMultiplier = 0.92f
        )
        VehicleType.POLICE_SUV -> VehicleAudioProfile(
            idleHz = 48f,
            rpmHzSpan = 114f,
            gearCount = 5,
            harmonic2 = 0.50f,
            harmonic3 = 0.26f,
            harmonic4 = 0.12f,
            subWeight = 0.52f,
            exhaustGrit = 0.16f,
            electricWhineWeight = 0.05f,
            electricWhineBaseHz = 420f,
            engineLpScale = 0.72f,
            tireStyle = TireAcousticStyle.STREET_SPORT,
            tireRollBaseHz = 96f,
            tireRollGainScale = 0.23f,
            tireScreechBaseHz = 760f,
            tireScreechGainScale = 0.34f,
            collisionStyle = CollisionAcousticStyle.HEAVY_CHASSIS,
            collisionPitchMultiplier = 1.00f,
            hornPitchMultiplier = 1.08f
        )
        VehicleType.BATTLE_TANK -> VehicleAudioProfile(
            idleHz = 24f,
            rpmHzSpan = 52f,
            gearCount = 3,
            harmonic2 = 0.42f,
            harmonic3 = 0.24f,
            harmonic4 = 0.08f,
            subWeight = 0.88f,
            exhaustGrit = 0.38f,
            electricWhineWeight = 0.06f,
            electricWhineBaseHz = 210f,
            engineLpScale = 0.52f,
            tireStyle = TireAcousticStyle.TANK_TRACKS,
            tireRollBaseHz = 42f,
            tireRollGainScale = 0.42f,
            tireScreechBaseHz = 340f,
            tireScreechGainScale = 0.30f,
            collisionStyle = CollisionAcousticStyle.ARMORED_STEEL,
            collisionPitchMultiplier = 0.72f,
            hornPitchMultiplier = 0.62f
        )
        VehicleType.DUNE_BUGGY -> VehicleAudioProfile(
            idleHz = 56f,
            rpmHzSpan = 138f,
            gearCount = 4,
            harmonic2 = 0.62f,
            harmonic3 = 0.38f,
            harmonic4 = 0.18f,
            subWeight = 0.28f,
            exhaustGrit = 0.34f,
            electricWhineWeight = 0f,
            electricWhineBaseHz = 0f,
            engineLpScale = 0.84f,
            tireStyle = TireAcousticStyle.OFFROAD_KNOBBY,
            tireRollBaseHz = 84f,
            tireRollGainScale = 0.27f,
            tireScreechBaseHz = 610f,
            tireScreechGainScale = 0.29f,
            collisionStyle = CollisionAcousticStyle.LIGHT_SPORT,
            collisionPitchMultiplier = 1.22f,
            hornPitchMultiplier = 1.24f
        )
        VehicleType.FIRE_TRUCK -> VehicleAudioProfile(
            idleHz = 32f,
            rpmHzSpan = 68f,
            gearCount = 4,
            harmonic2 = 0.44f,
            harmonic3 = 0.20f,
            harmonic4 = 0.06f,
            subWeight = 0.82f,
            exhaustGrit = 0.26f,
            electricWhineWeight = 0.04f,
            electricWhineBaseHz = 290f,
            engineLpScale = 0.54f,
            tireStyle = TireAcousticStyle.HEAVY_DUAL,
            tireRollBaseHz = 64f,
            tireRollGainScale = 0.30f,
            tireScreechBaseHz = 490f,
            tireScreechGainScale = 0.27f,
            collisionStyle = CollisionAcousticStyle.HEAVY_CHASSIS,
            collisionPitchMultiplier = 0.78f,
            hornPitchMultiplier = 0.64f
        )
        VehicleType.ARMORED_SWAT -> VehicleAudioProfile(
            idleHz = 36f,
            rpmHzSpan = 78f,
            gearCount = 4,
            harmonic2 = 0.45f,
            harmonic3 = 0.22f,
            harmonic4 = 0.08f,
            subWeight = 0.74f,
            exhaustGrit = 0.24f,
            electricWhineWeight = 0f,
            electricWhineBaseHz = 0f,
            engineLpScale = 0.58f,
            tireStyle = TireAcousticStyle.HEAVY_DUAL,
            tireRollBaseHz = 70f,
            tireRollGainScale = 0.28f,
            tireScreechBaseHz = 560f,
            tireScreechGainScale = 0.28f,
            collisionStyle = CollisionAcousticStyle.ARMORED_STEEL,
            collisionPitchMultiplier = 0.86f,
            hornPitchMultiplier = 0.76f
        )
        VehicleType.CYBER_SUPERCAR -> VehicleAudioProfile(
            idleHz = 76f,
            rpmHzSpan = 188f,
            gearCount = 6,
            harmonic2 = 0.54f,
            harmonic3 = 0.36f,
            harmonic4 = 0.24f,
            subWeight = 0.34f,
            exhaustGrit = 0.06f,
            electricWhineWeight = 0.34f,
            electricWhineBaseHz = 560f,
            engineLpScale = 0.90f,
            tireStyle = TireAcousticStyle.CYBER_SLICK,
            tireRollBaseHz = 132f,
            tireRollGainScale = 0.21f,
            tireScreechBaseHz = 980f,
            tireScreechGainScale = 0.38f,
            collisionStyle = CollisionAcousticStyle.CYBER_COMPOSITE,
            collisionPitchMultiplier = 1.18f,
            hornPitchMultiplier = 1.32f
        )
    }

    /**
     * Computes dynamic RPM (with automatic multi-gear shift sawtooth curve), engine fundamental pitch,
     * speed-pitched tire rolling hum, and drift/brake tire screech intensity for any vehicle.
     */
    fun computeMetrics(
        type: VehicleType,
        speed: Float,
        maxSpeed: Float,
        nitroActive: Boolean,
        gasPressed: Boolean,
        brakePressed: Boolean,
        lateralSpeed: Float,
        grounded: Boolean
    ): VehicleAudioMetrics {
        val profile = profileFor(type)
        val absSpeed = abs(speed)
        val safeMax = max(1f, maxSpeed)
        val speedNorm = (absSpeed / safeMax).coerceIn(0f, 1.45f)

        val gears = profile.gearCount.coerceAtLeast(1)
        val gearFloat = (speedNorm * gears).coerceIn(0f, gears.toFloat())
        val gear = (gearFloat.toInt() + 1).coerceIn(1, gears)
        val gearSpan = 1f / gears
        val gearFloor = (gear - 1) * gearSpan
        val inGearFrac = if (speedNorm >= 1f) {
            (0.86f + (speedNorm - 1f) * 0.65f).coerceIn(0.86f, 1.20f)
        } else {
            ((speedNorm - gearFloor) / gearSpan).coerceIn(0f, 1f)
        }

        // Gear-coupled RPM curve: starts at idle, climbs steeply per gear, drops slightly on upshift,
        // surges cleanly on throttle / airborne wheelspin / nitro overboost.
        val baseGearRpm = if (absSpeed < 0.4f) {
            0.16f + (if (gasPressed) 0.24f else 0f)
        } else {
            val gearBase = if (gear == 1) 0.22f else 0.40f + (gear - 2) * 0.03f
            gearBase + inGearFrac * (0.98f - gearBase)
        }
        val throttleLoad = if (gasPressed) 0.10f else if (brakePressed && absSpeed > 2f) -0.06f else 0f
        val airborneRev = if (!grounded && gasPressed) 0.20f else 0f
        val nitroBoost = if (nitroActive) 0.24f else 0f
        val rpm = (baseGearRpm + throttleLoad + airborneRev + nitroBoost).coerceIn(0.14f, 1.42f)

        val enginePitchHz = profile.idleHz + rpm * profile.rpmHzSpan

        // Tire rolling audio (scales with speed, silenced while airborne!)
        val tireRollGain = if (grounded && absSpeed > 0.7f) {
            val speedFactor = (absSpeed / safeMax).coerceIn(0f, 1.3f)
            (0.05f + speedFactor * profile.tireRollGainScale).coerceIn(0f, 0.55f)
        } else 0f
        val tireRollFreqHz = profile.tireRollBaseHz * (0.55f + min(1.35f, speedNorm) * 1.15f)

        // Tire screech / skid / gravel slip audio (lateral drift, hard braking, or nitro launch slip)
        val absLat = abs(lateralSpeed)
        val driftSlip = if (grounded && absSpeed > 6.5f) {
            ((absLat - 0.85f) / 3.6f).coerceIn(0f, 1f)
        } else 0f
        val brakeSlip = if (grounded && brakePressed && absSpeed > 7.5f) {
            ((absSpeed - 7.5f) / (safeMax * 0.55f)).coerceIn(0.18f, 0.92f)
        } else 0f
        val launchSlip = if (grounded && nitroActive && gasPressed && absSpeed in 1.2f..12f) {
            0.52f
        } else 0f
        val combinedSlip = max(driftSlip, max(brakeSlip, launchSlip))
        val tireScreechGain = if (combinedSlip > 0.04f) {
            (combinedSlip * profile.tireScreechGainScale).coerceIn(0f, 0.48f)
        } else 0f
        val tireScreechFreqHz = profile.tireScreechBaseHz * (0.84f + min(1.25f, speedNorm) * 0.34f + combinedSlip * 0.14f)

        return VehicleAudioMetrics(
            rpm = rpm,
            gear = gear,
            enginePitchHz = enginePitchHz,
            tireRollGain = tireRollGain,
            tireRollFreqHz = tireRollFreqHz,
            tireScreechGain = tireScreechGain,
            tireScreechFreqHz = tireScreechFreqHz
        )
    }
}
