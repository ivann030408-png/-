package com.example

import com.example.data.UserProfile
import com.example.engine.ChickenSkin
import com.example.engine.PropSpatialGrid
import com.example.engine.PropType
import com.example.engine.WeaponSkin
import com.example.engine.WorldProp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PerformanceAndProfileUnitTest {

    @Test
    fun `PropSpatialGrid matches brute-force overlap across 360 props with zero duplicate visits`() {
        val grid = PropSpatialGrid()
        val props = ArrayList<WorldProp>(360)
        val types = listOf(
            PropType.CRATE,
            PropType.BARREL,
            PropType.WALL_ARCH_GATE,
            PropType.GARAGE_HANGAR,
            PropType.WATCHTOWER,
            PropType.LOOP_THE_LOOP
        )

        var id = 0
        for (ix in -10..9) {
            for (iz in -9..8) {
                val type = types[abs(ix * 7 + iz * 13) % types.size]
                val prop = WorldProp(
                    id = "p_${id++}",
                    type = type,
                    x = ix * 11.5f,
                    y = 0f,
                    z = iz * 12.25f,
                    yaw = ((ix + iz) * 30f) % 360f,
                    destroyed = (id % 17 == 0)
                )
                props.add(prop)
            }
        }

        grid.rebuild(props)
        assertEquals(props.size, grid.indexedCount)

        // Test both narrow (single-cell) and wide (multi-cell) queries across the arena
        val queries = listOf(
            Triple(0f, 0f, 1.0f),
            Triple(0f, 0f, 24.0f),
            Triple(-95f, 62f, 8.5f),
            Triple(140f, -145f, 18.0f),
            Triple(-155f, -155f, 4.0f),
            Triple(48f, 36f, 0.5f)
        )

        for ((qx, qz, qr) in queries) {
            val visitedList = ArrayList<String>()
            val visitedSet = HashSet<String>()
            grid.forEachCandidate(qx, qz, qr, props) { prop ->
                assertFalse("Destroyed prop should never be returned: ${prop.id}", prop.destroyed)
                visitedList.add(prop.id)
                assertTrue("Duplicate prop visit detected for ${prop.id}", visitedSet.add(prop.id))
            }

            // Every non-destroyed prop whose bounding box intersects the query circle MUST be visited
            for (prop in props) {
                if (prop.destroyed) continue
                val reach = prop.boundRadius + qr
                val dx = abs(prop.x - qx)
                val dz = abs(prop.z - qz)
                if (dx <= reach && dz <= reach) {
                    assertTrue(
                        "Spatial grid missed overlapping prop ${prop.id} at (${prop.x},${prop.z}) for query ($qx,$qz,r=$qr)",
                        prop.id in visitedSet
                    )
                }
            }
            assertEquals(visitedSet.size, visitedList.size)
        }

        // Test dynamic move + rebuild + clear
        val moving = props.first { !it.destroyed }
        moving.x = 120f
        moving.z = 110f
        grid.markInvalid()
        assertEquals(-1, grid.indexedCount)
        grid.rebuild(props)
        assertEquals(props.size, grid.indexedCount)

        var foundMoved = false
        grid.forEachCandidate(120f, 110f, 2.0f, props) { prop ->
            if (prop.id == moving.id) foundMoved = true
        }
        assertTrue("Moved prop must be found at new coordinates after rebuild", foundMoved)

        // Empty list must be safe
        props.clear()
        grid.rebuild(props)
        assertEquals(0, grid.indexedCount)
        var visitedEmpty = 0
        grid.forEachCandidate(0f, 0f, 50f, props) { visitedEmpty++ }
        assertEquals(0, visitedEmpty)
    }

    @Test
    fun `UserProfile defaults and skin parsers handle valid and unknown values safely`() {
        val defaultProfile = UserProfile()
        assertEquals("CLASSIC", defaultProfile.selectedSkin)
        assertEquals("CLASSIC", defaultProfile.weaponSkin)
        assertEquals(0L, defaultProfile.totalKills)
        assertEquals(0L, defaultProfile.totalWins)
        assertEquals(0L, defaultProfile.totalMatches)
        assertEquals(0L, defaultProfile.bestStreak)
        assertEquals(0L, defaultProfile.coins)

        // All ChickenSkin & WeaponSkin entries round-trip cleanly
        for (skin in ChickenSkin.entries) {
            assertEquals(skin, ChickenSkin.fromNameSafe(skin.name))
        }
        for (wSkin in WeaponSkin.entries) {
            assertEquals(wSkin, WeaponSkin.fromNameSafe(wSkin.name))
        }

        // Unknown / malformed values safely fall back to CLASSIC
        assertEquals(ChickenSkin.CLASSIC, ChickenSkin.fromNameSafe(null))
        assertEquals(ChickenSkin.CLASSIC, ChickenSkin.fromNameSafe(""))
        assertEquals(ChickenSkin.CLASSIC, ChickenSkin.fromNameSafe("INVALID_SKIN"))
        assertEquals(WeaponSkin.CLASSIC, WeaponSkin.fromNameSafe(null))
        assertEquals(WeaponSkin.CLASSIC, WeaponSkin.fromNameSafe("UNKNOWN_WEAPON_SKIN"))
    }

    @Test
    fun `player health regenerates 1 HP every 1_3 seconds capped at 100 and never heals while dead`() {
        val engine = com.example.engine.GameEngine(
            soundManager = com.example.engine.SoundManager(),
            isOnlineMode = true,
            localUserId = "p_local"
        )
        engine.spawnProtectionTimer = 0f
        engine.playerHp = 95
        engine.playerAlive = true

        // 1.25s elapsed -> not yet 1.3s, still 95 HP
        repeat(25) { engine.step(0.05f) }
        assertEquals(95, engine.playerHp)

        // +0.10s (total 1.35s) -> +1 HP => 96 HP
        repeat(2) { engine.step(0.05f) }
        assertEquals(96, engine.playerHp)

        // Another ~10s -> caps strictly at 100 HP
        repeat(200) { engine.step(0.05f) }
        assertEquals(com.example.engine.GameEngine.PLAYER_MAX_HP, engine.playerHp)

        // Dead player (0 HP, playerAlive = false) must NOT regenerate
        engine.playerHp = 0
        engine.playerAlive = false
        repeat(100) { engine.step(0.05f) }
        assertEquals(0, engine.playerHp)
        assertFalse(engine.playerAlive)
    }

    @Test
    fun `floating damage popups respect simultaneous cap and expire quickly during rapid fire`() {
        val engine = com.example.engine.GameEngine(
            soundManager = com.example.engine.SoundManager(),
            isOnlineMode = true,
            localUserId = "p_local"
        )
        // Burst 20 hits rapidly -> must cap strictly at MAX_DAMAGE_POPUPS (7)
        repeat(20) { i ->
            engine.spawnDamagePopup(0f, 2f, 5f, 25 + i, com.example.engine.DamageKind.NORMAL)
        }
        assertEquals(com.example.engine.GameEngine.MAX_DAMAGE_POPUPS, engine.damagePopups3D.size)

        // After 0.65s of simulation, all normal popups (0.54s lifetime) must have expired
        repeat(13) { engine.step(0.05f) }
        assertTrue("Expected rapid-fire damage popups to clear quickly", engine.damagePopups3D.isEmpty())
    }

    @Test
    fun `per-player host privileges enforce personal godMode and canBuild override in real time`() {
        val engine = com.example.engine.GameEngine(
            soundManager = com.example.engine.SoundManager(),
            isOnlineMode = true,
            openBuildInitially = true,
            localUserId = "guest_1"
        )
        engine.spawnProtectionTimer = 0f
        // Room initially allows guest building and has global godMode off
        engine.applyOnlineRoomRules(
            rules = com.example.data.OnlineRoomRules(
                allowGuestPropSpawn = true,
                allowGuestVehicleSpawn = true,
                godMode = false
            ),
            isHost = false
        )
        assertTrue(engine.latestHud.canBuildProps)
        assertTrue(engine.latestHud.canSpawnVehicles)
        assertFalse(engine.latestHud.godMode)

        // Host grants personal God Mode and revokes building for guest_1
        engine.playerHp = 45
        engine.applyRemotePlayers(
            listOf(
                com.example.data.RoomPlayer(
                    userId = "guest_1",
                    displayName = "Guest",
                    hp = 10,
                    godMode = true,
                    canBuild = false
                ),
                com.example.data.RoomPlayer(
                    userId = "host_1",
                    displayName = "Host",
                    hp = 100,
                    godMode = true,
                    canBuild = true
                )
            )
        )
        assertTrue(engine.personalGodMode)
        assertTrue(engine.latestHud.godMode)
        assertEquals(100, engine.playerHp) // restored & protected from incoming serverHp = 10
        assertFalse(engine.latestHud.canBuildProps)
        assertFalse(engine.latestHud.canSpawnVehicles)
        assertFalse(engine.buildModeOpen) // build dock auto-closed on revoke
        assertTrue(engine.remotePlayers["host_1"]?.godMode == true)

        // Room disables guest spawning globally, but host explicitly grants canBuild = true to guest_1
        engine.applyOnlineRoomRules(
            rules = com.example.data.OnlineRoomRules(
                allowGuestPropSpawn = false,
                allowGuestVehicleSpawn = false,
                godMode = false
            ),
            isHost = false
        )
        engine.applyRemotePlayers(
            listOf(
                com.example.data.RoomPlayer(
                    userId = "guest_1",
                    displayName = "Guest",
                    hp = 100,
                    godMode = false,
                    canBuild = true
                )
            )
        )
        assertFalse(engine.personalGodMode)
        assertFalse(engine.latestHud.godMode)
        assertTrue(engine.latestHud.canBuildProps)
        assertTrue(engine.latestHud.canSpawnVehicles)
    }

    @Test
    fun `QuickChatPreset catalog has unique presets within Firestore bounds and matches text`() {
        val presets = com.example.ui.QuickChatPreset.entries
        assertTrue("Expected at least 6 quick chat presets", presets.size >= 6)
        assertEquals("Preset IDs must be unique", presets.size, presets.map { it.id }.toSet().size)
        for (preset in presets) {
            assertTrue("Preset ${preset.id} text must be 1..200 chars", preset.fallbackText.length in 1..200)
            assertEquals(preset, com.example.ui.QuickChatPreset.matchByText(preset.fallbackText))
        }
        assertEquals(com.example.ui.QuickChatPreset.HELP, com.example.ui.QuickChatPreset.matchByText("помогите!"))
        assertEquals(com.example.ui.QuickChatPreset.BUILD, com.example.ui.QuickChatPreset.matchByText("Давайте строить!"))
        assertEquals(com.example.ui.QuickChatPreset.FOLLOW, com.example.ui.QuickChatPreset.matchByText("За мной!"))
        assertEquals(null, com.example.ui.QuickChatPreset.matchByText("Произвольное сообщение"))
    }

    @Test
    fun `GyroAimProcessor handles all 4 rotations high-rate IMUs overhead non-inversion drift and fallback`() {
        val processor = com.example.engine.GyroAimProcessor()
        val step20ms = 20_000_000L

        // 1. All 4 display rotations produce exact +yaw (right) and +pitch (up) signs
        processor.processGyroSample(0f, 0f, 0f, 1_000_000_000L, android.view.Surface.ROTATION_0)
        val r0 = processor.processGyroSample(0.8f, -1.0f, 0f, 1_000_000_000L + step20ms, android.view.Surface.ROTATION_0)
        assertTrue("ROTATION_0 should yield +yaw and +pitch", r0 != null && r0.first > 0f && r0.second > 0f)

        processor.reset()
        processor.processGyroSample(0f, 0f, 0f, 2_000_000_000L, android.view.Surface.ROTATION_90)
        val r90 = processor.processGyroSample(-1.0f, -0.8f, 0f, 2_000_000_000L + step20ms, android.view.Surface.ROTATION_90)
        assertTrue("ROTATION_90 should yield +yaw and +pitch", r90 != null && r90.first > 0f && r90.second > 0f)

        processor.reset()
        processor.processGyroSample(0f, 0f, 0f, 3_000_000_000L, android.view.Surface.ROTATION_180)
        val r180 = processor.processGyroSample(-0.8f, 1.0f, 0f, 3_000_000_000L + step20ms, android.view.Surface.ROTATION_180)
        assertTrue("ROTATION_180 should yield +yaw and +pitch", r180 != null && r180.first > 0f && r180.second > 0f)

        processor.reset()
        processor.processGyroSample(0f, 0f, 0f, 4_000_000_000L, android.view.Surface.ROTATION_270)
        val r270 = processor.processGyroSample(1.0f, 0.8f, 0f, 4_000_000_000L + step20ms, android.view.Surface.ROTATION_270)
        assertTrue("ROTATION_270 should yield +yaw and +pitch", r270 != null && r270.first > 0f && r270.second > 0f)

        // 2. High-rate / FIFO-batched IMU (2500 Hz, dt = 0.4ms) MUST NOT drop motion or reset smoothing
        processor.reset()
        var ts = 5_000_000_000L
        processor.processGyroSample(0f, 0f, 0f, ts, android.view.Surface.ROTATION_90)
        var highRateYawTotal = 0f
        repeat(250) {
            ts += 400_000L // 0.4ms -> total 0.1s at -1.0 rad/s (~5.7 deg)
            val d = processor.processGyroSample(-1.0f, 0f, 0f, ts, android.view.Surface.ROTATION_90)
            if (d != null) highRateYawTotal += d.first
        }
        assertTrue(
            "High-rate 2500 Hz IMU should integrate smoothly without dropping samples (got $highRateYawTotal deg)",
            highRateYawTotal in 3.5f..6.5f
        )

        // 3. Reclined / overhead posture (screen facing slightly down: gz < 0) MUST NOT invert yaw!
        processor.reset()
        processor.onGravitySample(4.9f, 0f, -8.5f, isRawAccelerometer = false)
        processor.processGyroSample(0f, 0f, 0f, 6_000_000_000L, android.view.Surface.ROTATION_90)
        val overheadRight = processor.processGyroSample(-1.0f, 0f, 0f, 6_000_000_000L + step20ms, android.view.Surface.ROTATION_90)
        assertTrue("Overhead posture must keep turning right positive", overheadRight != null && overheadRight.first > 0f)

        // 4. Raw accelerometer haptic recoil spike (~18 m/s²) must be ignored (keep upright gravity intact)
        processor.reset()
        processor.onGravitySample(9.81f, 0f, 0f, isRawAccelerometer = true)
        processor.onGravitySample(0f, 0f, 18.5f, isRawAccelerometer = true) // haptic shock
        processor.processGyroSample(0f, 0f, 0f, 7_000_000_000L, android.view.Surface.ROTATION_90)
        val postShock = processor.processGyroSample(-1.0f, 0f, 0f, 7_000_000_000L + step20ms, android.view.Surface.ROTATION_90)
        assertTrue("Upright yaw should remain unaffected after haptic shock", postShock != null && postShock.first > 0f)

        // 5. Stationary thermal zero-rate bias (0.03 rad/s) calibrates away cleanly without endless drift
        processor.reset()
        var driftTs = 8_000_000_000L
        processor.processGyroSample(0.03f, 0.025f, 0f, driftTs, android.view.Surface.ROTATION_90)
        var lateDriftCount = 0
        repeat(200) { i ->
            driftTs += 20_000_000L // 4 seconds total of sitting still
            val d = processor.processGyroSample(0.03f, 0.025f, 0f, driftTs, android.view.Surface.ROTATION_90)
            if (i >= 150 && d != null) lateDriftCount++
        }
        assertEquals("Stationary bias should converge to zero drift", 0, lateDriftCount)

        // 6. Rotation vector quaternion-delta fallback produces matching +yaw turning right in ROTATION_90
        processor.reset()
        val halfAngle = -0.05f // -0.10 rad around +X_dev = turning right in ROTATION_90
        val q0 = processor.processRotationVectorSample(0f, 0f, 0f, 1f, 9_000_000_000L, android.view.Surface.ROTATION_90)
        assertEquals(null, q0)
        val q1 = processor.processRotationVectorSample(
            kotlin.math.sin(halfAngle),
            0f,
            0f,
            kotlin.math.cos(halfAngle),
            9_000_000_000L + step20ms,
            android.view.Surface.ROTATION_90
        )
        assertTrue("Rotation vector fallback should yield positive yaw turning right", q1 != null && q1.first > 0f)

        // 7. NaN and spike samples never poison processor state
        assertEquals(null, processor.processGyroSample(Float.NaN, 0f, 0f, 9_000_000_000L + step20ms * 2, android.view.Surface.ROTATION_90))
        assertEquals(null, processor.processGyroSample(25f, 0f, 0f, 9_000_000_000L + step20ms * 3, android.view.Surface.ROTATION_90))
        val recovered = processor.processGyroSample(-1.0f, 0f, 0f, 9_000_000_000L + step20ms * 4, android.view.Surface.ROTATION_90)
        assertTrue("Processor must recover cleanly after NaN/spike samples", recovered != null && recovered.first.isFinite() && recovered.first > 0f)
    }

    @Test
    fun `GameInputState resetMotionControls clears queued gyro and look deltas and ignores NaN`() {
        val input = com.example.engine.GameInputState()
        input.addGyroDelta(15f, -7f)
        input.addGyroDelta(Float.NaN, Float.POSITIVE_INFINITY)
        input.addLookDelta(4f, -3f)
        input.resetMotionControls()

        val (gy, gp) = input.consumeGyroDelta()
        val (lx, ly) = input.consumeLookDelta()
        assertEquals(0f, gy, 0.0001f)
        assertEquals(0f, gp, 0.0001f)
        assertEquals(0f, lx, 0.0001f)
        assertEquals(0f, ly, 0.0001f)
    }
}


