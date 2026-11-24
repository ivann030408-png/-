package com.example

import com.example.data.GameRoom
import com.example.data.GameSettings
import com.example.data.GraphicsQuality
import com.example.data.OnlineGameMode
import com.example.data.RoomConfig
import com.example.engine.GameEngine
import com.example.engine.GameMap
import com.example.engine.MapLayouts
import com.example.engine.PropType
import com.example.engine.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapsAndOnlineUnitTest {

    @Test
    fun `all three maps have dense layouts all 9 vehicles and valid glb model references`() {
        assertEquals(
            listOf(GameMap.MAIN_ARENA, GameMap.TURBO_SPEEDWAY, GameMap.TACTICAL_COMPOUND),
            GameMap.entries
        )

        val modelsDir = File("src/main/assets/models").let {
            if (it.exists()) it else File("app/src/main/assets/models")
        }
        assertTrue("Expected assets/models directory to exist", modelsDir.exists() && modelsDir.isDirectory)
        val modelFiles = modelsDir.list()?.toSet().orEmpty()

        // Verify all PropType GLBs exist
        for (propType in PropType.entries) {
            val file = propType.glbFile ?: continue
            assertTrue("Missing GLB for ${propType.name}: $file", file in modelFiles)
        }

        // Verify streamlined MAIN_ARENA ("Полигон") layout (~40% fewer static objects for 120 FPS)
        val mainArena = MapLayouts.buildMainArenaSpecs()
        assertTrue("MAIN_ARENA streamlined count should be in 85..135 (got ${mainArena.size})", mainArena.size in 85..135)
        assertTrue(mainArena.any { it.type == PropType.ROAD_ROUNDABOUT })
        assertTrue(mainArena.any { it.type == PropType.FINISH_ARCH })
        assertTrue(mainArena.any { it.type == PropType.LOOP_THE_LOOP })
        assertTrue(mainArena.any { it.type == PropType.FIRE_RING })
        assertTrue(mainArena.count { it.type == PropType.WALL_ARCH_GATE && it.startOpen } >= 2)

        // Verify streamlined TURBO_SPEEDWAY ("Турбо-Трек") layout
        val speedway = MapLayouts.buildTurboSpeedwaySpecs()
        assertTrue("TURBO_SPEEDWAY streamlined count should be in 85..135 (got ${speedway.size})", speedway.size in 85..135)
        assertTrue(speedway.any { it.type == PropType.FINISH_ARCH })
        assertTrue(speedway.any { it.type == PropType.LOOP_THE_LOOP })
        assertTrue(speedway.count { it.type == PropType.BOOST_PAD } >= 4)
        assertTrue(speedway.any { it.x < -40f && it.z < -40f })
        assertTrue(speedway.any { it.x > 40f && it.z < -40f })
        assertTrue(speedway.any { it.x > 40f && it.z > 40f })
        assertTrue(speedway.any { it.x < -40f && it.z > 40f })

        // Verify streamlined TACTICAL_COMPOUND ("Штурм-База") layout
        val compound = MapLayouts.buildTacticalCompoundSpecs()
        assertTrue("TACTICAL_COMPOUND streamlined count should be in 85..135 (got ${compound.size})", compound.size in 85..135)
        assertTrue(compound.count { it.type == PropType.WATCHTOWER } >= 2)
        assertTrue(compound.count { it.type.explosive } >= 6)
        assertTrue(compound.count { it.type == PropType.WALL_ARCH_GATE && it.startOpen } >= 2)
        assertTrue(compound.any { it.x < -30f && it.z < -30f })
        assertTrue(compound.any { it.x > 30f && it.z < -30f })
        assertTrue(compound.any { it.x > 30f && it.z > 30f })
        assertTrue(compound.any { it.x < -30f && it.z > 30f })

        // Verify all 3 maps stage all 9 distinct vehicles cleanly
        for (map in GameMap.entries) {
            val vehicles = MapLayouts.initialVehiclesForMap(map)
            assertEquals("Map ${map.name} must have all 9 vehicle types", VehicleType.entries.toSet(), vehicles.map { it.type }.toSet())
            assertEquals("Map ${map.name} vehicle IDs must be unique", vehicles.size, vehicles.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `120 fps defaults and unlimited spawn caps are configured`() {
        val defaults = GameSettings()
        assertEquals(120, defaults.targetFps)
        assertEquals(120, defaults.withPreset(GraphicsQuality.HIGH).targetFps)
        assertEquals(120, defaults.withPreset(GraphicsQuality.ULTRA).targetFps)
        assertEquals(90, defaults.withPreset(GraphicsQuality.MEDIUM).targetFps)

        assertEquals(Int.MAX_VALUE, GameEngine.MAX_PROPS)
        assertEquals(Int.MAX_VALUE, GameEngine.MAX_VEHICLES)
        assertEquals(Int.MAX_VALUE, GameEngine.MAX_PIG_BURGERS)
    }

    @Test
    fun `online room model maps extended settings and mode rules accurately`() {
        val cfg = RoomConfig(
            name = "Turbo Cup",
            mapId = GameMap.TURBO_SPEEDWAY.name,
            gameMode = OnlineGameMode.RACING,
            maxPlayers = 8,
            privateRoom = true,
            password = "pass",
            pinned = true,
            joinLocked = false,
            allowGuestPropSpawn = false,
            allowGuestVehicleSpawn = true,
            friendlyFire = false,
            infiniteAmmo = true,
            godMode = true
        )
        val room = GameRoom(
            id = "r1",
            roomCode = "TURB01",
            name = cfg.name,
            hostId = "host_uid",
            status = "open",
            maxPlayers = cfg.maxPlayers.toLong(),
            mapId = cfg.mapId,
            gameMode = cfg.gameMode.name,
            privateRoom = cfg.privateRoom,
            password = cfg.password,
            pinned = cfg.pinned,
            joinLocked = cfg.joinLocked,
            allowGuestPropSpawn = cfg.allowGuestPropSpawn,
            allowGuestVehicleSpawn = cfg.allowGuestVehicleSpawn,
            friendlyFire = cfg.friendlyFire,
            infiniteAmmo = cfg.infiniteAmmo,
            godMode = cfg.godMode,
            memberUids = listOf("host_uid")
        )
        assertEquals(OnlineGameMode.RACING, room.resolvedGameMode())
        val rules = room.toRoomRules()
        assertEquals("TURBO_SPEEDWAY", rules.mapId)
        assertEquals(OnlineGameMode.RACING, rules.gameMode)
        assertFalse(rules.allowGuestPropSpawn)
        assertTrue(rules.allowGuestVehicleSpawn)
        assertFalse(rules.friendlyFire)
        assertTrue(rules.infiniteAmmo)
        assertTrue(rules.godMode)
    }
}
