package com.example

import com.example.engine.SoundManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundManagerTest {

    @Test
    fun spatialPanningMatchesListenerOrientation() {
        val soundManager = SoundManager(startAudioThread = false)
        // Listener at origin facing -Z (yaw = 0 -> right is +X, left is -X)
        soundManager.updateListener(x = 0f, y = 1.6f, z = 0f, yawDeg = 0f, pitchDeg = 0f)

        val rightSound = soundManager.computeSpatialParams(15f, 1.6f, 0f, refDistance = 10f, maxDistance = 100f)
        assertTrue("Sound to listener's right should be audible", rightSound.audible)
        assertTrue("Right channel should dominate for +X source", rightSound.rightGain > rightSound.leftGain * 3f)

        val leftSound = soundManager.computeSpatialParams(-15f, 1.6f, 0f, refDistance = 10f, maxDistance = 100f)
        assertTrue("Left channel should dominate for -X source", leftSound.leftGain > leftSound.rightGain * 3f)

        val frontSound = soundManager.computeSpatialParams(0f, 1.6f, -15f, refDistance = 10f, maxDistance = 100f)
        assertEquals("Front center sound should have balanced L/R", frontSound.leftGain, frontSound.rightGain, 0.01f)

        // Rotate listener 90 deg right (yaw = 90 -> facing +X, so +Z is right and -Z is left)
        soundManager.updateListener(x = 0f, y = 1.6f, z = 0f, yawDeg = 90f, pitchDeg = 0f)
        val rotatedLeftSound = soundManager.computeSpatialParams(0f, 1.6f, -15f, refDistance = 10f, maxDistance = 100f)
        assertTrue("After turning 90 deg right, -Z source should pan to left ear", rotatedLeftSound.leftGain > rotatedLeftSound.rightGain * 3f)
    }

    @Test
    fun distanceAttenuationAndMaxDistanceCullingWork() {
        val soundManager = SoundManager(startAudioThread = false)
        soundManager.updateListener(0f, 1.6f, 0f, yawDeg = 0f, pitchDeg = 0f)

        val nearExplosion = soundManager.computeSpatialParams(0f, 1.6f, -10f, refDistance = 15f, maxDistance = 150f)
        val farExplosion = soundManager.computeSpatialParams(0f, 1.6f, -80f, refDistance = 15f, maxDistance = 150f)
        val outOfRange = soundManager.computeSpatialParams(0f, 1.6f, -160f, refDistance = 15f, maxDistance = 150f)

        assertTrue("Near sound must be louder than far sound", nearExplosion.leftGain > farExplosion.leftGain * 2f)
        assertTrue("Far sound should have stronger air-absorption low-pass filtering", farExplosion.lowPassAlpha < nearExplosion.lowPassAlpha)
        assertFalse("Sound beyond maxDistance must be culled", outOfRange.audible)
        assertEquals(0f, outOfRange.leftGain, 0.0001f)
        assertEquals(0f, outOfRange.rightGain, 0.0001f)
    }

    @Test
    fun rearHemisphereSoundAppliesWarmthAndAttenuation() {
        val soundManager = SoundManager(startAudioThread = false)
        soundManager.updateListener(0f, 1.6f, 0f, yawDeg = 0f, pitchDeg = 0f)

        val front = soundManager.computeSpatialParams(0f, 1.6f, -20f, refDistance = 10f, maxDistance = 100f)
        val behind = soundManager.computeSpatialParams(0f, 1.6f, 20f, refDistance = 10f, maxDistance = 100f)

        assertTrue("Sound behind listener should be slightly warmer (lower low-pass alpha)", behind.lowPassAlpha < front.lowPassAlpha)
        assertTrue("Sound behind listener should be slightly attenuated vs front", behind.leftGain < front.leftGain)
    }
}
