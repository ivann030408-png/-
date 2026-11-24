package com.example.engine

import android.view.Surface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Calibrated, gravity-aware hardware gyroscope processor for landscape mobile shooters.
 *
 * Key properties:
 * 1. Correct pitch & yaw signs across all 4 Surface.ROTATION_* orientations (portrait, landscape, reverse-landscape, tablets).
 * 2. Gravity-projected yaw stays intuitive from upright (90°) through tilted (45°) to flat (0°),
 *    while preventing horizontal inversion when reclining/holding the screen past vertical (overhead).
 * 3. Supports high-frequency & FIFO-batched IMUs (400–1000+ Hz) via continuous-time low-pass filtering
 *    without dropping sub-millisecond samples or resetting smoothing.
 * 4. Online stationary zero-rate bias estimation eliminates thermal MEMS drift while keeping micro-aiming crisp.
 * 5. Includes quaternion-delta fallback for devices exposing only GAME_ROTATION_VECTOR / ROTATION_VECTOR.
 */
class GyroAimProcessor {

    private var lastTimestampNs: Long = 0L

    // Normalized device-space "UP" vector (opposing gravity). Updated by TYPE_GRAVITY or gated ACCELEROMETER.
    private var hasGravity: Boolean = false
    private var upX: Float = 1f
    private var upY: Float = 0f
    private var upZ: Float = 0f

    // Continuous-time smoothed angular rates (rad/s)
    private var smoothYawRate: Float = 0f
    private var smoothPitchRate: Float = 0f

    // Online stationary zero-rate bias estimator (rad/s) to cancel thermal drift
    private var biasX: Float = 0f
    private var biasY: Float = 0f
    private var biasZ: Float = 0f
    private var stillAccumSec: Float = 0f
    private var prevRawX: Float = 0f
    private var prevRawY: Float = 0f
    private var prevRawZ: Float = 0f
    private var hasPrevRaw: Boolean = false

    // Previous quaternion state for rotation-vector fallback (x, y, z, w)
    private var hasPrevQuat: Boolean = false
    private var prevQx: Float = 0f
    private var prevQy: Float = 0f
    private var prevQz: Float = 0f
    private var prevQw: Float = 1f
    private var prevQuatTimestampNs: Long = 0L

    // Scratch output for Pair-returning convenience overload
    private val pairScratch = FloatArray(2)

    fun reset() {
        lastTimestampNs = 0L
        smoothYawRate = 0f
        smoothPitchRate = 0f
        biasX = 0f
        biasY = 0f
        biasZ = 0f
        stillAccumSec = 0f
        hasPrevRaw = false
        hasPrevQuat = false
        prevQuatTimestampNs = 0L
    }

    /**
     * Feeds a gravity or accelerometer sample (m/s² in device coordinates).
     * Android's gravity/accelerometer sensor points UP along whichever axis faces the sky (+9.81).
     */
    fun onGravitySample(gx: Float, gy: Float, gz: Float, isRawAccelerometer: Boolean) {
        if (!gx.isFinite() || !gy.isFinite() || !gz.isFinite()) return
        val mag = sqrt(gx * gx + gy * gy + gz * gz)
        // For raw accelerometer, reject recoil haptics & linear hand jolts away from 1g (~9.81 m/s²)
        val minMag = if (isRawAccelerometer) 8.2f else 2.0f
        val maxMag = if (isRawAccelerometer) 11.4f else 25.0f
        if (mag < minMag || mag > maxMag) return

        val nx = gx / mag
        val ny = gy / mag
        val nz = gz / mag
        if (!hasGravity) {
            upX = nx
            upY = ny
            upZ = nz
            hasGravity = true
        } else {
            val alpha = if (isRawAccelerometer) 0.05f else 0.22f
            upX += (nx - upX) * alpha
            upY += (ny - upY) * alpha
            upZ += (nz - upZ) * alpha
            val norm = sqrt(upX * upX + upY * upY + upZ * upZ)
            if (norm > 0.001f) {
                upX /= norm
                upY /= norm
                upZ /= norm
            }
        }
    }

    /**
     * Converts a raw gyroscope reading (wx, wy, wz in rad/s) into camera (dYawDeg, dPitchDeg) where:
     *   +dYawDeg   = turn camera RIGHT
     *   +dPitchDeg = tilt camera UP
     */
    fun processGyroSample(
        wx: Float,
        wy: Float,
        wz: Float,
        timestampNs: Long,
        displayRotation: Int
    ): Pair<Float, Float>? {
        return if (processGyroSampleInto(wx, wy, wz, timestampNs, displayRotation, pairScratch)) {
            pairScratch[0] to pairScratch[1]
        } else {
            null
        }
    }

    /**
     * Zero-allocation variant for high-frequency sensor callbacks.
     * Writes (dYawDeg, dPitchDeg) into outDelta[0..1] and returns true when non-zero output is produced.
     */
    fun processGyroSampleInto(
        wx: Float,
        wy: Float,
        wz: Float,
        timestampNs: Long,
        displayRotation: Int,
        outDelta: FloatArray
    ): Boolean {
        if (!wx.isFinite() || !wy.isFinite() || !wz.isFinite()) return false

        if (lastTimestampNs == 0L) {
            lastTimestampNs = timestampNs
            prevRawX = wx
            prevRawY = wy
            prevRawZ = wz
            hasPrevRaw = true
            return false
        }

        val deltaNs = timestampNs - lastTimestampNs
        // Ignore duplicate or out-of-order timestamps WITHOUT destroying smoothing state
        if (deltaNs <= 0L) return false
        lastTimestampNs = timestampNs

        val rawDt = deltaNs * 1e-9f
        if (rawDt > MAX_VALID_DT_SEC) {
            // Reset smoothing after genuine pause/background stall to prevent camera jump
            smoothYawRate = 0f
            smoothPitchRate = 0f
            stillAccumSec = 0f
            hasPrevRaw = false
            return false
        }

        // Reject impossible hardware spikes (> 12 rad/s ≈ 690 deg/s)
        if (abs(wx) > MAX_RATE_RAD || abs(wy) > MAX_RATE_RAD || abs(wz) > MAX_RATE_RAD) {
            stillAccumSec = 0f
            hasPrevRaw = false
            return false
        }

        // Gentle stationary bias estimation: adapts only when all 3 axes are steady & quiet
        val diffX = if (hasPrevRaw) abs(wx - prevRawX) else 0f
        val diffY = if (hasPrevRaw) abs(wy - prevRawY) else 0f
        val diffZ = if (hasPrevRaw) abs(wz - prevRawZ) else 0f
        prevRawX = wx
        prevRawY = wy
        prevRawZ = wz
        hasPrevRaw = true

        val isQuiet =
            abs(wx - biasX) < STILL_RATE_THRESHOLD_RAD &&
                abs(wy - biasY) < STILL_RATE_THRESHOLD_RAD &&
                abs(wz - biasZ) < STILL_RATE_THRESHOLD_RAD &&
                diffX < STILL_JERK_THRESHOLD_RAD &&
                diffY < STILL_JERK_THRESHOLD_RAD &&
                diffZ < STILL_JERK_THRESHOLD_RAD
        if (isQuiet) {
            stillAccumSec += rawDt
            if (stillAccumSec >= STILL_CALIBRATION_DELAY_SEC) {
                val biasAlpha = 1f - exp(-rawDt / BIAS_TAU_SEC)
                biasX = (biasX + (wx - biasX) * biasAlpha).coerceIn(-MAX_BIAS_RAD, MAX_BIAS_RAD)
                biasY = (biasY + (wy - biasY) * biasAlpha).coerceIn(-MAX_BIAS_RAD, MAX_BIAS_RAD)
                biasZ = (biasZ + (wz - biasZ) * biasAlpha).coerceIn(-MAX_BIAS_RAD, MAX_BIAS_RAD)
            }
        } else {
            stillAccumSec = 0f
        }

        val cx = wx - biasX
        val cy = wy - biasY
        val cz = wz - biasZ

        // Screen-space horizontal RIGHT axis and default upright UP axis in device coordinates
        val rightX: Float
        val rightY: Float
        val defaultUpX: Float
        val defaultUpY: Float
        when (displayRotation) {
            Surface.ROTATION_0 -> {
                rightX = 1f; rightY = 0f
                defaultUpX = 0f; defaultUpY = 1f
            }
            Surface.ROTATION_90 -> {
                // Standard landscape: top edge of phone is on the left, +X points up, +Y points left
                rightX = 0f; rightY = -1f
                defaultUpX = 1f; defaultUpY = 0f
            }
            Surface.ROTATION_180 -> {
                rightX = -1f; rightY = 0f
                defaultUpX = 0f; defaultUpY = -1f
            }
            Surface.ROTATION_270 -> {
                // Reverse landscape: top edge of phone is on the right, -X points up, +Y points right
                rightX = 0f; rightY = 1f
                defaultUpX = -1f; defaultUpY = 0f
            }
            else -> {
                rightX = 0f; rightY = -1f
                defaultUpX = 1f; defaultUpY = 0f
            }
        }

        // Pitch rate around screen-horizontal RIGHT axis:
        // Right-hand rule: rotating sightline (-Z_dev) UP toward screen-UP is +rotation around screen-RIGHT.
        val rawPitchRate = cx * rightX + cy * rightY

        // Yaw rate around world vertical UP axis (clamped to forward-upright hemisphere so reclining/overhead
        // posture never flips left/right steering).
        val rawYawRate = if (hasGravity) {
            val dotR = upX * rightX + upY * rightY
            var orthoUpX = upX - dotR * rightX
            var orthoUpY = upY - dotR * rightY
            // Never allow negative Z (screen facing downward while lying down) to invert horizontal steering
            var orthoUpZ = upZ.coerceAtLeast(0f)
            // Keep screen-plane component aligned with defaultUp (never inverted upside-down)
            val dotDefaultUp = orthoUpX * defaultUpX + orthoUpY * defaultUpY
            if (dotDefaultUp < 0f) {
                orthoUpX = 0f
                orthoUpY = 0f
            }
            val orthoLen = sqrt(orthoUpX * orthoUpX + orthoUpY * orthoUpY + orthoUpZ * orthoUpZ)
            if (orthoLen > 0.25f) {
                orthoUpX /= orthoLen
                orthoUpY /= orthoLen
                orthoUpZ /= orthoLen
                -(cx * orthoUpX + cy * orthoUpY + cz * orthoUpZ)
            } else {
                -(cx * defaultUpX + cy * defaultUpY)
            }
        } else {
            -(cx * defaultUpX + cy * defaultUpY)
        }

        // Frequency-independent continuous-time exponential smoothing (identical feel at 50 Hz – 1000 Hz)
        val smoothAlpha = (1f - exp(-rawDt / SMOOTH_TAU_SEC)).coerceIn(0.01f, 0.95f)
        smoothYawRate += (rawYawRate - smoothYawRate) * smoothAlpha
        smoothPitchRate += (rawPitchRate - smoothPitchRate) * smoothAlpha

        // Fine soft deadzone suppresses residual sensor noise without a sticky threshold
        val yawOut = applySoftDeadzone(smoothYawRate, DEADZONE_RAD)
        val pitchOut = applySoftDeadzone(smoothPitchRate, DEADZONE_RAD)
        if (yawOut == 0f && pitchOut == 0f) return false

        val dYawDeg = (yawOut * rawDt * RAD_TO_DEG).coerceIn(-12f, 12f)
        val dPitchDeg = (pitchOut * rawDt * RAD_TO_DEG).coerceIn(-10f, 10f)
        if (!dYawDeg.isFinite() || !dPitchDeg.isFinite()) return false

        outDelta[0] = dYawDeg
        outDelta[1] = dPitchDeg
        return true
    }

    /**
     * Fallback for devices that lack TYPE_GYROSCOPE / TYPE_GYROSCOPE_UNCALIBRATED and instead expose
     * TYPE_GAME_ROTATION_VECTOR or TYPE_ROTATION_VECTOR (quaternion values[0..2] and optional values[3]).
     */
    fun processRotationVectorSample(
        qxRaw: Float,
        qyRaw: Float,
        qzRaw: Float,
        qwRaw: Float?,
        timestampNs: Long,
        displayRotation: Int
    ): Pair<Float, Float>? {
        return if (processRotationVectorSampleInto(qxRaw, qyRaw, qzRaw, qwRaw, timestampNs, displayRotation, pairScratch)) {
            pairScratch[0] to pairScratch[1]
        } else {
            null
        }
    }

    fun processRotationVectorSampleInto(
        qxRaw: Float,
        qyRaw: Float,
        qzRaw: Float,
        qwRaw: Float?,
        timestampNs: Long,
        displayRotation: Int,
        outDelta: FloatArray
    ): Boolean {
        if (!qxRaw.isFinite() || !qyRaw.isFinite() || !qzRaw.isFinite()) return false
        val wComputed = qwRaw?.takeIf { it.isFinite() }
            ?: sqrt(max(0f, 1f - (qxRaw * qxRaw + qyRaw * qyRaw + qzRaw * qzRaw)))
        val norm = sqrt(qxRaw * qxRaw + qyRaw * qyRaw + qzRaw * qzRaw + wComputed * wComputed)
        if (norm < 1e-4f) return false

        var qx = qxRaw / norm
        var qy = qyRaw / norm
        var qz = qzRaw / norm
        var qw = wComputed / norm

        if (!hasPrevQuat) {
            prevQx = qx
            prevQy = qy
            prevQz = qz
            prevQw = qw
            prevQuatTimestampNs = timestampNs
            hasPrevQuat = true
            // Anchor gyro timestamp simultaneously so the very next sample produces an immediate delta
            lastTimestampNs = timestampNs
            return false
        }

        val deltaNs = timestampNs - prevQuatTimestampNs
        if (deltaNs <= 0L) return false
        val dt = deltaNs * 1e-9f

        // Shortest-arc hemisphere alignment
        val dot = prevQx * qx + prevQy * qy + prevQz * qz + prevQw * qw
        if (dot < 0f) {
            qx = -qx
            qy = -qy
            qz = -qz
            qw = -qw
        }

        // Incremental rotation in device local frame: q_delta = q_prev^-1 * q_curr
        val dw = prevQw * qw + prevQx * qx + prevQy * qy + prevQz * qz
        val dx = prevQw * qx - prevQx * qw - prevQy * qz + prevQz * qy
        val dy = prevQw * qy + prevQx * qz - prevQy * qw - prevQz * qx
        val dz = prevQw * qz - prevQx * qy + prevQy * qx - prevQz * qw

        prevQx = qx
        prevQy = qy
        prevQz = qz
        prevQw = qw
        prevQuatTimestampNs = timestampNs

        if (dt > MAX_VALID_DT_SEC) {
            lastTimestampNs = timestampNs
            smoothYawRate = 0f
            smoothPitchRate = 0f
            return false
        }

        val sinHalf = sqrt(dx * dx + dy * dy + dz * dz)
        val wx: Float
        val wy: Float
        val wz: Float
        if (sinHalf > 1e-6f) {
            val angle = 2f * atan2(sinHalf, dw.coerceAtLeast(0f))
            val rateScale = (angle / sinHalf) / dt
            wx = dx * rateScale
            wy = dy * rateScale
            wz = dz * rateScale
        } else {
            wx = 0f
            wy = 0f
            wz = 0f
        }

        return processGyroSampleInto(wx, wy, wz, timestampNs, displayRotation, outDelta)
    }

    private fun applySoftDeadzone(rate: Float, deadzone: Float): Float {
        val mag = abs(rate)
        if (mag <= deadzone) return 0f
        return if (rate > 0f) mag - deadzone else -(mag - deadzone)
    }

    private companion object {
        const val RAD_TO_DEG = 57.29578f
        const val DEADZONE_RAD = 0.008f
        const val SMOOTH_TAU_SEC = 0.011f
        const val MAX_VALID_DT_SEC = 0.15f
        const val MAX_RATE_RAD = 12f
        const val STILL_RATE_THRESHOLD_RAD = 0.045f
        const val STILL_JERK_THRESHOLD_RAD = 0.025f
        const val STILL_CALIBRATION_DELAY_SEC = 0.35f
        const val BIAS_TAU_SEC = 0.85f
        const val MAX_BIAS_RAD = 0.08f
    }
}
