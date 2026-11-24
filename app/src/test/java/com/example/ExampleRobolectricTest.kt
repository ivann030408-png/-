package com.example

import android.content.Context
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import com.example.data.CustomHudLayout
import com.example.data.GameSettings
import com.example.data.GameSettingsStore
import com.example.data.GraphicsQuality
import com.example.data.HudControlId
import com.example.data.HudControlSlot
import com.example.data.OnlineGameMode
import com.example.data.OnlineRoomRules
import com.example.engine.BuildCategory
import com.example.engine.CameraMode
import com.example.engine.ChickenSkin
import com.example.engine.DamageKind
import com.example.engine.GameEngine
import com.example.engine.GameMap
import com.example.engine.GyroAimProcessor
import com.example.engine.HapticController
import com.example.engine.HapticFeedbackCatalog
import com.example.engine.LobbyBotSlot
import com.example.engine.PropType
import com.example.engine.CollisionAcousticStyle
import com.example.engine.SoundManager
import com.example.engine.PigBurgerState
import com.example.engine.TireAcousticStyle
import com.example.engine.VehicleAudioCatalog
import com.example.engine.VehicleType
import com.example.engine.WeaponSkin
import com.example.engine.WeaponType
import com.example.engine.WorldProp
import com.example.renderer.GlbAlphaMode
import com.example.renderer.GlbModelLoader
import com.example.renderer.GlbModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Петушиные Бои", appName)
  }

  @Test
  fun `settings store persists fps options gyro invert custom hud layout skin and map`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = GameSettingsStore(context)

    val customLayout = CustomHudLayout().withUpdatedSlot(
      id = HudControlId.FIRE,
      slot = HudControlSlot(xFrac = 0.82f, yFrac = 0.68f, scale = 1.25f, alpha = 0.72f),
      leftHanded = false
    )

    store.update {
      it.withPreset(GraphicsQuality.LOW).copy(
        targetFps = 90,
        fovDegrees = 140f,
        lookSensitivity = 0.05f,
        gyroEnabled = true,
        gyroAimOnly = true,
        gyroSensitivity = 4.0f,
        gyroInvertX = true,
        gyroInvertY = true,
        customHudLayout = customLayout,
        selectedSkin = ChickenSkin.CYBER_NEON,
        selectedMap = GameMap.MAIN_ARENA,
        leftHandedLayout = true,
        showDamageNumbers = false,
        masterVolume = 1.5f
      )
    }

    val reloaded = GameSettingsStore(context).settings.value
    assertEquals(GraphicsQuality.LOW, reloaded.graphicsQuality)
    assertTrue(reloaded.lowEndMode)
    assertFalse(reloaded.showDamageNumbers)
    assertEquals(115f, reloaded.renderDistance, 0.01f)
    assertEquals(90, reloaded.targetFps)
    assertFalse(reloaded.shadowsEnabled)
    assertEquals(105f, reloaded.fovDegrees, 0.01f)
    assertEquals(0.30f, reloaded.lookSensitivity, 0.01f)
    assertTrue(reloaded.gyroEnabled)
    assertTrue(reloaded.gyroAimOnly)
    assertTrue(reloaded.gyroInvertX)
    assertTrue(reloaded.gyroInvertY)
    assertEquals(2.50f, reloaded.gyroSensitivity, 0.01f)
    assertTrue(reloaded.customHudLayout.customized)
    val savedFire = reloaded.customHudLayout.slotFor(HudControlId.FIRE, leftHanded = false)
    assertEquals(0.82f, savedFire.xFrac, 0.01f)
    assertEquals(0.68f, savedFire.yFrac, 0.01f)
    assertEquals(1.25f, savedFire.scale, 0.01f)
    assertEquals(0.72f, savedFire.alpha, 0.01f)
    assertEquals(ChickenSkin.CYBER_NEON, reloaded.selectedSkin)
    assertEquals(GameMap.MAIN_ARENA, reloaded.selectedMap)
    assertTrue(reloaded.leftHandedLayout)
    assertEquals(1.0f, reloaded.masterVolume, 0.01f)

    // Verify all 5 FPS tiers normalize cleanly
    assertEquals(30, GameSettings.normalizeFps(30))
    assertEquals(45, GameSettings.normalizeFps(45))
    assertEquals(60, GameSettings.normalizeFps(60))
    assertEquals(90, GameSettings.normalizeFps(90))
    assertEquals(120, GameSettings.normalizeFps(120))

    store.resetToDefaults()
    val defaults = GameSettingsStore(context).settings.value
    assertEquals(GraphicsQuality.HIGH, defaults.graphicsQuality)
    assertFalse(defaults.lowEndMode)
    assertTrue(defaults.showDamageNumbers)
    assertFalse(defaults.gyroEnabled)
    assertFalse(defaults.gyroInvertX)
    assertFalse(defaults.gyroInvertY)
    assertFalse(defaults.customHudLayout.customized)
    assertEquals(ChickenSkin.CLASSIC, defaults.selectedSkin)
    assertEquals(GameMap.MAIN_ARENA, defaults.selectedMap)
    assertEquals(72f, defaults.fovDegrees, 0.01f)
    assertFalse(defaults.leftHandedLayout)
  }

  @Test
  fun `gyro aim processor fixes pitch sign handles all rotations and gravity tilt`() {
    val processor = GyroAimProcessor()
    val stepNs = 20_000_000L // 20 ms

    // Anchor timestamp
    assertNull(processor.processGyroSample(0f, 0f, 0f, 1_000_000_000L, Surface.ROTATION_90))

    // In ROTATION_90: turning phone RIGHT gives wx < 0 -> +dYaw; tilting sightline UP gives wy < 0 -> +dPitch
    val r90 = processor.processGyroSample(-1.0f, -0.8f, 0f, 1_000_000_000L + stepNs, Surface.ROTATION_90)
    assertNotNull(r90)
    assertTrue("Expected positive yaw turning right in ROTATION_90", r90!!.first > 0f)
    assertTrue("Expected positive pitch tilting up in ROTATION_90", r90.second > 0f)

    // In ROTATION_270 (reverse landscape): signs flip cleanly
    processor.reset()
    processor.processGyroSample(0f, 0f, 0f, 2_000_000_000L, Surface.ROTATION_270)
    val r270 = processor.processGyroSample(1.0f, 0.8f, 0f, 2_000_000_000L + stepNs, Surface.ROTATION_270)
    assertNotNull(r270)
    assertTrue("Expected positive yaw turning right in ROTATION_270", r270!!.first > 0f)
    assertTrue("Expected positive pitch tilting up in ROTATION_270", r270.second > 0f)

    // Gravity-projected yaw when phone is lying flat on lap (gravity along +Z): turning right (wz < 0) -> +dYaw
    processor.reset()
    processor.onGravitySample(0f, 0f, 9.81f, isRawAccelerometer = false)
    processor.processGyroSample(0f, 0f, 0f, 3_000_000_000L, Surface.ROTATION_90)
    val flatTurnRight = processor.processGyroSample(0f, 0f, -1.2f, 3_000_000_000L + stepNs, Surface.ROTATION_90)
    assertNotNull(flatTurnRight)
    assertTrue("Expected positive yaw when turning flat phone right", flatTurnRight!!.first > 0f)

    // Sensor spike (> 12 rad/s) is rejected cleanly
    assertNull(processor.processGyroSample(25f, 0f, 0f, 3_000_000_000L + stepNs * 2, Surface.ROTATION_90))
  }

  @Test
  fun `floating damage popups spawn rise and respect setting toggle`() {
    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(showDamageNumbers = true),
      initialBotRoster = emptyList()
    )

    engine.spawnDamagePopup(10f, 2.0f, -8f, 42, DamageKind.CRITICAL)
    assertEquals(1, engine.damagePopups3D.size)
    val popup = engine.damagePopups3D.first()
    assertEquals(42, popup.amount)
    assertEquals(DamageKind.CRITICAL, popup.kind)
    val startY = popup.y

    repeat(5) { engine.step(0.04f) }
    assertTrue(popup.y > startY)
    repeat(30) { engine.step(0.04f) }
    assertTrue(engine.damagePopups3D.isEmpty())

    engine.updateSettings(GameSettings(showDamageNumbers = false))
    engine.spawnDamagePopup(0f, 2f, 0f, 25, DamageKind.NORMAL)
    assertTrue(engine.damagePopups3D.isEmpty())

    soundManager.release()
  }

  @Test
  fun `build menu stays closed on start supports 9 vehicles select-then-place and gyro aim`() {
    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(
        gyroEnabled = true,
        gyroAimOnly = false,
        gyroSensitivity = 1.0f,
        selectedMap = GameMap.MAIN_ARENA,
        selectedSkin = ChickenSkin.GOLDEN_KING
      ),
      peacefulMode = true,
      openBuildInitially = false,
      initialBotRoster = emptyList()
    )

    assertFalse(engine.buildModeOpen)
    assertEquals(9, VehicleType.entries.size)
    assertEquals(3, GameMap.entries.size)
    assertEquals(6, ChickenSkin.entries.size)
    assertEquals(GameMap.MAIN_ARENA, engine.activeMap)

    // Gyroscope delta updates camera yaw & pitch cleanly
    val startYaw = engine.playerYaw
    val startPitch = engine.playerPitch
    engine.input.addGyroDelta(12f, 5f)
    engine.step(0.03f)
    assertEquals(startYaw + 12f, engine.playerYaw, 0.05f)
    assertEquals(startPitch + 5f, engine.playerPitch, 0.05f)

    // Toggle build menu, select prop (without spawning yet), then confirm placement
    engine.toggleBuildMenu()
    assertTrue(engine.buildModeOpen)
    val beforeProps = engine.props.size
    engine.selectBuildProp(PropType.TOWER)
    assertEquals(beforeProps, engine.props.size)
    engine.placeCurrentGhost()
    assertEquals(beforeProps + 1, engine.props.size)

    // Select one of the new vehicles & confirm placement
    val beforeVehicles = engine.vehicles.size
    engine.selectBuildCategory(BuildCategory.VEHICLES)
    engine.selectBuildVehicle(VehicleType.CYBER_SUPERCAR)
    assertEquals(beforeVehicles, engine.vehicles.size)
    engine.placeCurrentGhost()
    assertEquals(beforeVehicles + 1, engine.vehicles.size)
    assertEquals(VehicleType.CYBER_SUPERCAR, engine.vehicles.last().type)

    // Toggle grid snap works cleanly
    val prevSnap = engine.buildGridSnap
    engine.toggleBuildGridSnap()
    assertEquals(!prevSnap, engine.buildGridSnap)

    soundManager.release()
  }

  @Test
  fun `unified main arena replaces old maps with rich architecture interactive structures and all 9 vehicles`() {
    val soundManager = SoundManager()

    assertEquals(
      listOf(GameMap.MAIN_ARENA, GameMap.TURBO_SPEEDWAY, GameMap.TACTICAL_COMPOUND),
      GameMap.entries
    )
    assertEquals(GameMap.MAIN_ARENA, GameMap.fromNameSafe("CHALO_HOUSE"))
    assertEquals(GameMap.MAIN_ARENA, GameMap.fromNameSafe("SANDBOX_POLYGON"))
    assertEquals(GameMap.TURBO_SPEEDWAY, GameMap.fromNameSafe("TURBO_SPEEDWAY"))
    assertEquals(GameMap.TACTICAL_COMPOUND, GameMap.fromNameSafe("TACTICAL_COMPOUND"))

    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )

    // Unified map has modular road network, interactive buildings/gates, stunts, destructibles & all 9 vehicles
    assertTrue(engine.props.any { it.type == PropType.ROAD_ROUNDABOUT })
    assertTrue(engine.props.any { it.type == PropType.ROAD })
    assertTrue(engine.props.any { it.type == PropType.ROAD_TURN })
    assertTrue(engine.props.any { it.type == PropType.ROAD_RAMP })
    assertTrue(engine.props.any { it.type == PropType.HOUSE_SMALL })
    assertTrue(engine.props.any { it.type == PropType.GARAGE_HANGAR })
    assertTrue(engine.props.any { it.type == PropType.WATCHTOWER })
    assertTrue(engine.props.count { it.type == PropType.WALL_ARCH_GATE } >= 2)
    assertEquals(VehicleType.entries.toSet(), engine.vehicles.map { it.type }.toSet())

    // All 4 quadrants and outer zones are populated (zero empty zones)
    assertTrue("NW quadrant populated", engine.props.any { it.x < -50f && it.z < -50f })
    assertTrue("NE quadrant populated", engine.props.any { it.x > 50f && it.z < -50f })
    assertTrue("SE quadrant populated", engine.props.any { it.x > 50f && it.z > 50f })
    assertTrue("SW quadrant populated", engine.props.any { it.x < -50f && it.z > 50f })

    soundManager.release()
  }

  @Test
  fun `menu slideshow backgrounds contain attached and generated drawables`() {
    val backgrounds = com.example.ui.MENU_BACKGROUNDS
    assertTrue("Expected at least 5 slideshow backgrounds", backgrounds.size >= 5)
    assertTrue(backgrounds.contains(R.drawable.img_bg_blue_chicken_1795199604978))
    assertTrue(backgrounds.contains(R.drawable.lobby_hero_poster))
    assertTrue(backgrounds.contains(R.drawable.img_bg_chalohouse_sunset_1795199606926))
    assertTrue(backgrounds.contains(R.drawable.img_bg_stunt_arena_1795199607105))
    assertTrue(backgrounds.contains(R.drawable.img_menu_arena_1795119038541))
    assertEquals(backgrounds.size, backgrounds.distinct().size)
    assertTrue(backgrounds.all { it != 0 })
  }

  @Test
  fun `all 9 vehicles have distinct acoustic profiles and realistic rpm speed and tire pitch curves`() {
    val profiles = VehicleType.entries.map { VehicleAudioCatalog.profileFor(it) }
    assertEquals(9, profiles.size)

    // Every vehicle must have a unique acoustic signature (idle/span Hz, gear count, tire & collision style)
    val signatures = profiles.map { p ->
      "${p.idleHz.toInt()}:${p.rpmHzSpan.toInt()}:${p.gearCount}:${p.tireStyle}:${p.collisionStyle}:${(p.electricWhineWeight * 100).toInt()}"
    }.toSet()
    assertEquals("Expected all 9 vehicles to have distinct audio signatures", 9, signatures.size)

    // Verify all collision & tire acoustic classes are exercised across the fleet
    assertEquals(TireAcousticStyle.entries.toSet(), profiles.map { it.tireStyle }.toSet())
    assertEquals(CollisionAcousticStyle.entries.toSet(), profiles.map { it.collisionStyle }.toSet())

    // Verify RPM & pitch rise with speed, throttle, and nitro, and shift gears smoothly
    for (type in VehicleType.entries) {
      val idle = VehicleAudioCatalog.computeMetrics(
        type = type,
        speed = 0f,
        maxSpeed = type.maxSpeed,
        nitroActive = false,
        gasPressed = false,
        brakePressed = false,
        lateralSpeed = 0f,
        grounded = true
      )
      val cruising = VehicleAudioCatalog.computeMetrics(
        type = type,
        speed = type.maxSpeed * 0.55f,
        maxSpeed = type.maxSpeed,
        nitroActive = false,
        gasPressed = true,
        brakePressed = false,
        lateralSpeed = 0.5f,
        grounded = true
      )
      val nitroTop = VehicleAudioCatalog.computeMetrics(
        type = type,
        speed = type.maxSpeed * 1.15f,
        maxSpeed = type.maxSpeed,
        nitroActive = true,
        gasPressed = true,
        brakePressed = false,
        lateralSpeed = 0.2f,
        grounded = true
      )
      assertEquals(1, idle.gear)
      assertTrue("${type.name} cruising pitch should exceed idle", cruising.enginePitchHz > idle.enginePitchHz)
      assertTrue("${type.name} nitro pitch should exceed cruising", nitroTop.enginePitchHz > cruising.enginePitchHz)
      assertTrue("${type.name} should shift up past gear 1 at high speed", nitroTop.gear >= 2)
      assertTrue("${type.name} cruising tire roll gain should be > 0", cruising.tireRollGain > 0f)
      assertTrue("${type.name} tire roll pitch should rise with speed", nitroTop.tireRollFreqHz > cruising.tireRollFreqHz)

      // Hard lateral drift triggers tire screech while grounded
      val drifting = VehicleAudioCatalog.computeMetrics(
        type = type,
        speed = type.maxSpeed * 0.65f,
        maxSpeed = type.maxSpeed,
        nitroActive = false,
        gasPressed = true,
        brakePressed = false,
        lateralSpeed = 6.5f,
        grounded = true
      )
      assertTrue("${type.name} should emit tire screech while drifting", drifting.tireScreechGain > 0.1f)

      // Airborne vehicle silences both tire roll and tire screech immediately
      val airborne = VehicleAudioCatalog.computeMetrics(
        type = type,
        speed = type.maxSpeed * 0.7f,
        maxSpeed = type.maxSpeed,
        nitroActive = false,
        gasPressed = true,
        brakePressed = false,
        lateralSpeed = 6.5f,
        grounded = false
      )
      assertEquals(0f, airborne.tireRollGain, 0.0001f)
      assertEquals(0f, airborne.tireScreechGain, 0.0001f)
    }
  }

  @Test
  fun `mute flag is always sanitized to false and vehicle collisions transfer momentum with cooldown`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = GameSettingsStore(context)
    store.update { it.copy(muted = true, masterVolume = 0.8f) }
    val reloaded = GameSettingsStore(context).settings.value
    assertFalse("Legacy muted flag must be sanitized to false", reloaded.muted)
    assertEquals(0.8f, reloaded.masterVolume, 0.01f)

    // Verify vehicle-to-vehicle collision transfers velocity into a parked vehicle and sets cooldown
    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )
    assertTrue("Main arena should spawn vehicles", engine.vehicles.size >= 2)
    val striker = engine.vehicles[0]
    val parked = engine.vehicles[1]
    striker.x = 0f
    striker.z = 0f
    striker.yaw = 0f // facing -Z
    striker.speed = 22f
    striker.collisionSoundCooldown = 0f

    parked.x = 0f
    parked.z = -((striker.type.halfW + parked.type.halfW) * 1.15f - 0.2f)
    parked.yaw = 0f
    parked.speed = 0f
    parked.collisionSoundCooldown = 0f

    engine.step(0.03f)
    assertTrue("Parked vehicle should gain forward momentum when rammed", kotlin.math.abs(parked.speed) > 1.0f)
    assertTrue("Collision sound cooldown should activate to prevent spam", striker.collisionSoundCooldown > 0f)

    soundManager.release()
  }

  @Test
  fun `glb parser loads all 45 custom models with animations and named rigging nodes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val assets = context.assets

    // Every preloaded .glb asset in app/src/main/assets/models/ must parse cleanly with geometry
    assertTrue("Expected at least 40 preloaded GLB models", GlbModelManager.CORE_PRELOAD_MODELS.size >= 40)
    for (fileName in GlbModelManager.CORE_PRELOAD_MODELS) {
      val path = "models/$fileName"
      val parsed = GlbModelLoader.parseFromAssets(assets, path)
      assertNotNull("Failed to parse GLB asset: $path", parsed)
      assertTrue("GLB asset $path should have at least 1 mesh", parsed!!.meshes.isNotEmpty())
      assertTrue("GLB asset $path should have at least 1 node", parsed.nodes.isNotEmpty())
    }

    // Hero character: khryak_napoleon_animated.glb
    val hero = GlbModelLoader.parseFromAssets(assets, "models/khryak_napoleon_animated.glb")!!
    assertTrue(hero.animations.keys.containsAll(listOf("Idle", "Walk", "Run", "Jump", "HoldGun")))
    assertTrue("Hero must contain weapon_slot node", hero.nodeNameToIndex.containsKey("weapon_slot"))

    // Living NPC: pig_burger_animated.glb
    val pig = GlbModelLoader.parseFromAssets(assets, "models/pig_burger_animated.glb")!!
    assertTrue(pig.animations.keys.containsAll(listOf("Idle", "Walk", "Run", "Jump")))

    // Tactical RPG: muzzle + hideable rocket node
    val rpg = GlbModelLoader.parseFromAssets(assets, "models/weapon_rpg_tactical.glb")!!
    assertTrue("RPG must contain muzzle node", rpg.nodeNameToIndex.containsKey("muzzle"))
    assertTrue("RPG must contain rocket node", rpg.nodeNameToIndex.containsKey("rocket"))

    // Police car: steering, wheel spin, and dual flashing sirens
    val police = GlbModelLoader.parseFromAssets(assets, "models/car_police.glb")!!
    assertTrue(police.nodeNameToIndex.containsKey("wheel_FL"))
    assertTrue(police.nodeNameToIndex.containsKey("wheel_FR"))
    assertTrue(police.nodeNameToIndex.containsKey("wheel_FL_spin"))
    assertTrue(police.nodeNameToIndex.containsKey("siren_L"))
    assertTrue(police.nodeNameToIndex.containsKey("siren_R"))

    // Track & stunt props
    for (stuntPath in listOf(
      "models/loop_the_loop.glb",
      "models/pendulum_hammer.glb",
      "models/crusher.glb",
      "models/spinner_trap.glb",
      "models/finish_arch.glb"
    )) {
      val stunt = GlbModelLoader.parseFromAssets(assets, stuntPath)
      assertNotNull("Missing stunt GLB $stuntPath", stunt)
      assertTrue("$stuntPath should have meshes", stunt!!.meshes.isNotEmpty())
    }
  }

  @Test
  fun `settings store persists weapon skin and lobby music volume`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = GameSettingsStore(context)

    store.update {
      it.copy(
        weaponSkin = WeaponSkin.TACTICAL,
        musicVolume = 0.45f
      )
    }
    val reloaded = GameSettingsStore(context).settings.value
    assertEquals(WeaponSkin.TACTICAL, reloaded.weaponSkin)
    assertEquals(0.45f, reloaded.musicVolume, 0.01f)

    store.resetToDefaults()
    val defaults = GameSettingsStore(context).settings.value
    assertEquals(WeaponSkin.CLASSIC, defaults.weaponSkin)
    assertEquals(0.72f, defaults.musicVolume, 0.01f)
  }

  @Test
  fun `racing track obstacle course pig-burger npcs and 5-tab sandbox work end to end`() {
    val soundManager = SoundManager()

    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )
    assertTrue("MAIN_ARENA should start uncluttered with 1..3 default Pig-Burger NPCs", engine.pigBurgers.size in 1..3)
    assertTrue("MAIN_ARENA should have FINISH_ARCH", engine.props.any { it.type == PropType.FINISH_ARCH })
    assertTrue("MAIN_ARENA should have LOOP_THE_LOOP", engine.props.any { it.type == PropType.LOOP_THE_LOOP })
    assertTrue("MAIN_ARENA should have BOOST_PAD", engine.props.any { it.type == PropType.BOOST_PAD })
    assertTrue("MAIN_ARENA should have JUMP_PAD", engine.props.any { it.type == PropType.JUMP_PAD })
    assertTrue("MAIN_ARENA should have >= 10 BOWLING_PIN", engine.props.count { it.type == PropType.BOWLING_PIN } >= 10)
    assertTrue("MAIN_ARENA should have PENDULUM_HAMMER", engine.props.any { it.type == PropType.PENDULUM_HAMMER })
    assertTrue("MAIN_ARENA should have CRUSHER", engine.props.any { it.type == PropType.CRUSHER })
    assertTrue("MAIN_ARENA should have SPINNER_TRAP", engine.props.any { it.type == PropType.SPINNER_TRAP })

    // Petting a nearby Pig-Burger triggers HAPPY_HOP and heals the player
    val targetPig = engine.pigBurgers.first()
    engine.playerX = targetPig.x + 1.5f
    engine.playerZ = targetPig.z
    engine.playerHp = 60
    engine.petNearestPigBurger()
    assertEquals(PigBurgerState.HAPPY_HOP, targetPig.state)
    assertTrue("Petting Pig-Burger should heal player", engine.playerHp > 60)

    // Hitting a Pig-Burger launches it and drops a heal pickup (hrumki_* / gazyava_*)
    val propsBeforeHit = engine.props.size
    targetPig.lootCooldown = 0f
    engine.hitPigBurger(targetPig, impulseX = 6f, impulseZ = -8f, upVy = 10f, dropLoot = true)
    assertEquals(PigBurgerState.FLEE_RUN, targetPig.state)
    assertTrue("Pig-Burger should be launched upward", targetPig.vy >= 9f)
    assertEquals(propsBeforeHit + 1, engine.props.size)
    assertTrue("Dropped loot must be a heal pickup", engine.props.last().type.isHealPickup)

    // 5-tab sandbox build menu: NPCS, STUNTS_TRAPS, FOOD_DRINKS
    assertEquals(5, BuildCategory.entries.size)
    engine.toggleBuildMenu()

    val pigsBeforeSpawn = engine.pigBurgers.size
    engine.selectBuildCategory(BuildCategory.NPCS)
    engine.placeCurrentGhost()
    assertEquals(pigsBeforeSpawn + 1, engine.pigBurgers.size)

    val propsBeforeTrap = engine.props.size
    engine.selectBuildCategory(BuildCategory.STUNTS_TRAPS)
    engine.selectBuildProp(PropType.FIRE_RING)
    engine.placeCurrentGhost()
    assertEquals(propsBeforeTrap + 1, engine.props.size)
    assertEquals(PropType.FIRE_RING, engine.props.last().type)

    val propsBeforeFood = engine.props.size
    engine.selectBuildCategory(BuildCategory.FOOD_DRINKS)
    engine.selectBuildProp(PropType.GAZYAVA_LEMON)
    engine.placeCurrentGhost()
    assertEquals(propsBeforeFood + 1, engine.props.size)
    assertEquals(PropType.GAZYAVA_LEMON, engine.props.last().type)

    soundManager.release()
  }

  @Test
  fun `glb loader parses alpha modes cutoffs doubleSided and non-indexed car and weapon geometry`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val assets = context.assets

    // Every single car_*.glb in assets/models/ must parse with opaque solid body + non-empty vertices (indexed or non-indexed)
    val modelFiles = assets.list("models")?.toList().orEmpty()
    val carGlbFiles = modelFiles.filter { it.startsWith("car_") && it.endsWith(".glb") }.sorted()
    assertTrue("Expected at least 7 car_*.glb models", carGlbFiles.size >= 7)

    // Verify every car_*.glb file is registered in VehicleType.entries!
    val registeredCarGlbs = VehicleType.entries.mapNotNull { it.glbFile }.toSet()
    for (carFile in carGlbFiles) {
      assertTrue("Car GLB $carFile must be registered in VehicleType", carFile in registeredCarGlbs)
      val parsed = GlbModelLoader.parseFromAssets(assets, "models/$carFile")
      assertNotNull("Failed to parse $carFile", parsed)
      val totalVerts = parsed!!.meshes.sumOf { m -> m.primitives.sumOf { it.vertexCount } }
      assertTrue("$carFile must have vertices", totalVerts > 0)
      assertTrue("$carFile must have at least one OPAQUE material", parsed.materials.any { it.alphaMode == GlbAlphaMode.OPAQUE })
    }

    // Non-indexed weapon & hero models must report vertexCount > 0 and valid normals
    for (criticalPath in listOf(
      "models/weapon_pistol_classic.glb",
      "models/weapon_pistol_tactical.glb",
      "models/weapon_rifle_classic.glb",
      "models/weapon_rifle_tactical.glb",
      "models/weapon_rpg_classic.glb",
      "models/weapon_rpg_tactical.glb",
      "models/khryak_napoleon_animated.glb"
    )) {
      val parsed = GlbModelLoader.parseFromAssets(assets, criticalPath)
      assertNotNull("Failed to parse $criticalPath", parsed)
      val allPrims = parsed!!.meshes.flatMap { it.primitives }
      assertTrue("$criticalPath should have primitives", allPrims.isNotEmpty())
      assertTrue("$criticalPath primitives must all have vertexCount > 0", allPrims.all { it.vertexCount > 0 })
    }

    // Verify zero duplicate/garbage (-1 or (1)) or deleted legacy GLBs remain in assets/models/
    val badGlbs = modelFiles.filter { it.contains("-1.") || it.contains("(1)") || it.startsWith("prop_explosive") || it.startsWith("prop_breakable") || it == "reinforced_fence.glb" }
    assertTrue("Found stale duplicate or legacy GLB files: $badGlbs", badGlbs.isEmpty())

    // Verify every PropType.glbFile exists in assets/models/
    for (pType in PropType.entries) {
      val file = pType.glbFile ?: continue
      assertTrue("PropType.${pType.name} references missing GLB $file", file in modelFiles)
    }

    // Verify all 4 interactive door/gate GLB structures parse with real open & close animations
    for (doorFile in listOf("house_small.glb", "garage_hangar.glb", "watchtower_building.glb", "wall_arch_gate.glb")) {
      val parsed = GlbModelLoader.parseFromAssets(assets, "models/$doorFile")
      assertNotNull("Failed to parse interactive door GLB $doorFile", parsed)
      assertTrue("$doorFile must have 'open' animation", parsed!!.animations.containsKey("open"))
      assertTrue("$doorFile must have 'close' animation", parsed.animations.containsKey("close"))
    }
  }

  @Test
  fun `every loaded car type can be spawned into engine and driven cleanly`() {
    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )

    // Spawn ALL 9 vehicle types (all 7 car_*.glb + Tank + Buggy) via public spawnVehicle()
    val spawnedTypes = mutableSetOf<VehicleType>()
    for ((idx, vType) in VehicleType.entries.withIndex()) {
      engine.playerX = -60f + idx * 10f
      engine.playerZ = -20f
      engine.playerYaw = 0f
      val car = engine.spawnVehicle(vType)
      assertNotNull("Expected spawnVehicle(${vType.name}) to succeed", car)
      assertEquals(vType, car!!.type)
      assertTrue(car.id.startsWith("car_user_"))
      spawnedTypes.add(car.type)
    }
    assertEquals(VehicleType.entries.toSet(), spawnedTypes)
    assertTrue("All 9 user-spawned vehicles should coexist under MAX_VEHICLES", engine.vehicles.count { it.id.startsWith("car_user_") } == 9)

    // Enter and drive one of the newly spawned GLB cars to confirm steering, wheel spin & speed work
    val userFiretruck = engine.vehicles.last { it.type == VehicleType.FIRE_TRUCK }
    engine.playerX = userFiretruck.x
    engine.playerZ = userFiretruck.z
    engine.toggleVehicle()
    assertEquals(userFiretruck, engine.activeVehicle)
    engine.input.gasPressed = true
    engine.input.steerRight = true
    repeat(15) { engine.step(0.03f) }
    assertTrue("Spawned vehicle should accelerate", userFiretruck.speed > 5f)
    assertTrue("Spawned vehicle wheels should spin", kotlin.math.abs(userFiretruck.wheelSpin) > 1f)
    assertTrue("Spawned vehicle steering should respond", userFiretruck.steerAngle > 5f)

    soundManager.release()
  }

  @Test
  fun `free camera flies smoothly in 3d and rotates without moving player`() {
    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )
    engine.step(0.03f)
    val startPlayerX = engine.playerX
    val startPlayerZ = engine.playerZ
    val prevMode = engine.cameraMode

    // Enter Free Camera
    engine.toggleFreeCamera()
    assertEquals(CameraMode.FREE_CAM, engine.cameraMode)
    assertTrue(engine.latestHud.freeCamActive)

    val startCamX = engine.freeCamX
    val startCamY = engine.freeCamY
    val startCamZ = engine.freeCamZ
    val startCamYaw = engine.freeCamYaw

    // Look right, fly forward + right + up
    engine.input.addLookDelta(80f, -20f)
    engine.input.moveX = 0.7f
    engine.input.moveY = 1.0f
    engine.input.flyUp = true
    repeat(12) { engine.step(0.03f) }

    assertTrue("Free camera yaw should rotate on look drag", engine.freeCamYaw != startCamYaw)
    assertTrue("Free camera should move upward when flyUp is held", engine.freeCamY > startCamY + 2f)
    val movedHoriz = kotlin.math.hypot((engine.freeCamX - startCamX).toDouble(), (engine.freeCamZ - startCamZ).toDouble())
    assertTrue("Free camera should fly smoothly horizontally", movedHoriz > 2.0)
    assertEquals("Player X should remain stationary during Free Cam", startPlayerX, engine.playerX, 0.25f)
    assertEquals("Player Z should remain stationary during Free Cam", startPlayerZ, engine.playerZ, 0.25f)

    // Exit Free Camera restores previous camera mode
    engine.toggleFreeCamera()
    assertEquals(prevMode, engine.cameraMode)
    assertFalse(engine.latestHud.freeCamActive)

    soundManager.release()
  }

  @Test
  fun `vehicle free orbit camera rotates 360 degrees and preserves angle while driving without snap-back`() {
    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )
    val car = engine.vehicles.first()
    engine.playerX = car.x
    engine.playerZ = car.z
    engine.toggleVehicle()
    assertEquals(car, engine.activeVehicle)
    assertEquals(0f, engine.vehicleCamOrbitYaw, 0.01f)

    // Swipe horizontally and vertically to orbit around the car
    engine.input.addLookDelta(300f, 60f)
    engine.step(0.03f)
    val chosenOrbitYaw = engine.vehicleCamOrbitYaw
    val chosenOrbitPitch = engine.vehicleCamOrbitPitch
    assertTrue("Vehicle orbit yaw should change significantly after swipe", kotlin.math.abs(chosenOrbitYaw) > 40f)
    assertTrue("Vehicle orbit pitch should adjust vertically", chosenOrbitPitch != 16f)

    // Drive forward and turn for 2 full seconds: chosen orbit angle MUST be preserved (zero forced snap-back!)
    engine.input.gasPressed = true
    engine.input.steerLeft = true
    repeat(60) { engine.step(0.033f) }
    assertEquals("Vehicle orbit yaw must NOT auto-snap back while driving", chosenOrbitYaw, engine.vehicleCamOrbitYaw, 0.01f)
    assertEquals("Vehicle orbit pitch must NOT auto-snap back while driving", chosenOrbitPitch, engine.vehicleCamOrbitPitch, 0.01f)

    // Manual recenter pill resets orbit cleanly on demand
    engine.resetVehicleCameraOrbit()
    assertEquals(0f, engine.vehicleCamOrbitYaw, 0.01f)
    assertEquals(16f, engine.vehicleCamOrbitPitch, 0.01f)

    soundManager.release()
  }

  @Test
  fun `pig burgers props and vehicles support unlimited spawning without artificial caps`() {
    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )

    assertEquals(Int.MAX_VALUE, GameEngine.MAX_PIG_BURGERS)
    assertEquals(Int.MAX_VALUE, GameEngine.MAX_PROPS)
    assertEquals(Int.MAX_VALUE, GameEngine.MAX_VEHICLES)
    assertTrue("Default starting Pig-Burgers should be sparse (<= 3)", engine.pigBurgers.size in 1..3)

    // Spawn 75 Pig-Burgers (> old 64 cap) cleanly without capping or crashing
    val initialCount = engine.pigBurgers.size
    engine.toggleBuildMenu()
    engine.selectBuildCategory(BuildCategory.NPCS)
    repeat(75) { idx ->
      engine.playerX = -60f + (idx % 10) * 6f
      engine.playerZ = -45f + (idx / 10) * 6f
      engine.step(1f / 120f)
      engine.placeCurrentGhost()
    }
    assertEquals(initialCount + 75, engine.pigBurgers.size)

    // Spawn 30 vehicles (> old 24 cap) cleanly without evicting previous ones
    val initialVehicles = engine.vehicles.size
    repeat(30) { idx ->
      engine.playerX = -50f + (idx % 6) * 8f
      engine.playerZ = 10f + (idx / 6) * 8f
      assertNotNull(engine.spawnVehicle(VehicleType.entries[idx % VehicleType.entries.size]))
    }
    assertEquals(initialVehicles + 30, engine.vehicles.size)

    soundManager.release()
  }

  @Test
  fun `empty arches ramps and bridges allow smooth traversal and jumps without invisible walls`() {
    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )

    // 1. Driving cleanly through FINISH_ARCH (yaw = 0 drives along -Z) without hitting an invisible wall
    val finishArch = engine.props.first { it.type == PropType.FINISH_ARCH }
    val car = engine.vehicles.first()
    car.x = finishArch.x
    car.y = 0f
    car.z = finishArch.z + 8f
    car.yaw = 0f // -Z toward arch
    car.speed = 24f
    engine.playerX = car.x
    engine.playerZ = car.z
    engine.toggleVehicle()
    engine.input.gasPressed = true
    repeat(35) { engine.step(0.03f) }
    assertTrue(
      "Car must drive straight through FINISH_ARCH (expected z < ${finishArch.z - 3f}, got ${car.z})",
      car.z < finishArch.z - 3f
    )
    engine.input.gasPressed = false

    // 2. Driving up West stunt runway DOUBLE_RAMP (-26, -10) and jumping cleanly through FIRE_RING (-26, -22)
    val ringCar = engine.activeVehicle!!
    ringCar.x = -26f
    ringCar.y = 0f
    ringCar.vy = 0f
    ringCar.z = 6f
    ringCar.yaw = 0f // drives toward -Z: BOOST_PAD(+4) -> DOUBLE_RAMP(-10) -> FIRE_RING(-22)
    ringCar.speed = 28f
    engine.input.gasPressed = true
    var maxYReached = 0f
    repeat(48) {
      engine.step(0.03f)
      if (ringCar.y > maxYReached) maxYReached = ringCar.y
    }
    assertTrue("Car should climb DOUBLE_RAMP and get airborne (maxY=$maxYReached)", maxYReached > 1.8f)
    assertTrue(
      "Car must pass cleanly through FIRE_RING without stopping (z=${ringCar.z})",
      ringCar.z < -24f
    )
    engine.input.gasPressed = false
    engine.toggleVehicle() // exit car

    // 3. Jumping on foot underneath a BRIDGE must NOT snap the player upward onto the bridge deck
    val bridge = engine.props.first { it.type == PropType.BRIDGE }
    engine.playerX = bridge.x
    engine.playerY = 0f
    engine.playerZ = bridge.z
    engine.playerVy = 0f
    engine.input.jumpQueued = true
    repeat(20) { engine.step(0.03f) }
    assertTrue(
      "Jumping underneath a bridge must stay below bridge deck (y=${engine.playerY}, deck=${bridge.y + bridge.type.height})",
      engine.playerY < bridge.y + bridge.type.height - 0.5f
    )

    soundManager.release()
  }

  @Test
  fun `interactive doors and arch gates open close animate and allow pass-through when opened`() {
    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )

    // 1. North WALL_ARCH_GATE at (0, -58) starts closed and blocks driving through
    val northGate = engine.props.first { it.type == PropType.WALL_ARCH_GATE && it.z < 0f }
    assertFalse("Gate should start closed", northGate.doorOpen)
    val car = engine.vehicles.first()
    car.x = northGate.x
    car.y = 0f
    car.z = northGate.z + 5.0f
    car.yaw = 0f // drive North (-Z) toward gate
    car.speed = 18f
    engine.playerX = car.x
    engine.playerZ = car.z
    engine.toggleVehicle()

    engine.input.gasPressed = true
    repeat(25) { engine.step(0.03f) }
    assertTrue("Closed gate must stop vehicle from crossing z=${northGate.z} (got ${car.z})", car.z > northGate.z)

    // 2. Open the gate via toggleNearestDoor() -> HUD reflects open state, animation advances, and car drives straight through!
    assertTrue("HUD should detect nearby interactive gate", engine.latestHud.nearDoorProp)
    assertTrue("toggleNearestDoor() should open gate", engine.toggleNearestDoor())
    assertTrue("Gate should now be open", northGate.doorOpen)
    assertTrue("Gate doorTouched should be set for GLB animation", northGate.doorTouched)
    assertTrue("HUD nearDoorIsOpen should be true", engine.latestHud.nearDoorIsOpen)

    car.z = northGate.z + 5.0f
    car.speed = 20f
    repeat(30) { engine.step(0.03f) }
    assertTrue("Gate animation time should advance", northGate.doorAnimTime > 0.3f)
    assertTrue("Vehicle must drive cleanly through open arch gate (got z=${car.z})", car.z < northGate.z - 2.5f)

    // 3. Closing a nearby house door on foot updates state cleanly
    engine.input.gasPressed = false
    engine.toggleVehicle()
    val house = engine.props.first { it.type == PropType.HOUSE_SMALL }
    val doorX = house.x + house.sinYaw * (house.type.halfD + 1.5f)
    val doorZ = house.z - house.cosYaw * (house.type.halfD + 1.5f)
    engine.playerX = doorX
    engine.playerZ = doorZ
    engine.step(0.03f)
    assertTrue("toggleNearestDoor() should open nearby house door", engine.toggleNearestDoor())
    assertTrue("House door should be open", house.doorOpen)
    assertTrue("toggleNearestDoor() should close house door", engine.toggleNearestDoor())
    assertFalse("House door should be closed again", house.doorOpen)

    soundManager.release()
  }

  @Test
  fun `all maps fill outer zones and every vehicle defines coherent seated driver fit`() {
    val soundManager = SoundManager()

    for (map in GameMap.entries) {
      val engine = GameEngine(
        soundManager = soundManager,
        initialSettings = GameSettings(selectedMap = map),
        peacefulMode = true,
        initialBotRoster = emptyList()
      )
      assertTrue("${map.name} should have rich prop layout (>= 100 props)", engine.props.size >= 100)
    }

    for (vType in VehicleType.entries) {
      assertTrue("${vType.name} driverScale out of range: ${vType.driverScale}", vType.driverScale in 0.45f..0.85f)
      assertTrue("${vType.name} driverSeatY out of range: ${vType.driverSeatY}", vType.driverSeatY in 0.15f..2.20f)
      assertTrue("${vType.name} halfW should be positive", vType.halfW in 0.9f..2.5f)
      assertTrue("${vType.name} halfL should be positive", vType.halfL in 1.5f..5.0f)
    }

    soundManager.release()
  }

  @Test
  fun `gun recoil tank cannon and vehicle collisions trigger distinct haptic feedback respecting settings`() {
    val recordedWeapons = mutableListOf<WeaponType>()
    var tankCannonCount = 0
    val recordedCollisions = mutableListOf<Triple<Float, VehicleType, Boolean>>()
    val recordedRams = mutableListOf<Pair<Float, VehicleType>>()
    val recordedExplosions = mutableListOf<Float>()

    val spyHaptics = object : HapticController {
      override var enabled: Boolean = true
      override var paused: Boolean = false
      override fun onWeaponRecoil(weapon: WeaponType) {
        if (enabled && !paused) recordedWeapons.add(weapon)
      }
      override fun onTankCannonRecoil() {
        if (enabled && !paused) tankCannonCount++
      }
      override fun onVehicleCollision(impactSpeed: Float, vehicleType: VehicleType, isLanding: Boolean) {
        if (enabled && !paused) recordedCollisions.add(Triple(impactSpeed, vehicleType, isLanding))
      }
      override fun onVehicleRam(impactSpeed: Float, vehicleType: VehicleType) {
        if (enabled && !paused) recordedRams.add(impactSpeed to vehicleType)
      }
      override fun onExplosionShockwave(distance: Float) {
        if (enabled && !paused) recordedExplosions.add(distance)
      }
      override fun cancel() = Unit
    }

    // Verify catalog intensity scaling: RPG > Pistol/Rifle, heavy crash > light bump, Tank > Buggy
    val rifleProfile = HapticFeedbackCatalog.weaponRecoilProfile(WeaponType.RIFLE)
    val rpgProfile = HapticFeedbackCatalog.weaponRecoilProfile(WeaponType.RPG)
    val tankProfile = HapticFeedbackCatalog.tankCannonProfile()
    assertTrue("RPG recoil peak should exceed Rifle", rpgProfile.peakAmplitude > rifleProfile.peakAmplitude)
    assertTrue("RPG recoil should use multi-stage waveform", !rpgProfile.isOneShot)
    assertTrue("Tank cannon should be strongest recoil", tankProfile.peakAmplitude >= rpgProfile.peakAmplitude)

    val lightCrash = HapticFeedbackCatalog.vehicleCollisionProfile(7f, VehicleType.DUNE_BUGGY, isLanding = false)
    val heavyCrash = HapticFeedbackCatalog.vehicleCollisionProfile(26f, VehicleType.BATTLE_TANK, isLanding = false)
    assertTrue("Heavy tank crash should exceed light buggy bump", heavyCrash.peakAmplitude > lightCrash.peakAmplitude)
    assertTrue("Heavy crash should produce multi-stage crunch waveform", !heavyCrash.isOneShot)

    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA, hapticsEnabled = true),
      peacefulMode = true,
      initialBotRoster = emptyList(),
      hapticController = spyHaptics
    )
    assertTrue("Haptics should start enabled from GameSettings", spyHaptics.enabled)

    // 1. Firing Rifle, Pistol, and RPG triggers distinct recoil haptics
    engine.switchWeapon(WeaponType.RIFLE)
    engine.input.firing = true
    engine.step(0.03f)
    engine.input.firing = false
    engine.fireCooldown = 0f

    engine.switchWeapon(WeaponType.PISTOL)
    engine.input.firing = true
    engine.step(0.03f)
    engine.input.firing = false
    engine.fireCooldown = 0f

    engine.switchWeapon(WeaponType.RPG)
    engine.input.firing = true
    engine.step(0.03f)
    engine.input.firing = false

    assertEquals(listOf(WeaponType.RIFLE, WeaponType.PISTOL, WeaponType.RPG), recordedWeapons)

    // 2. Disabling haptics in settings suppresses recoil vibration
    engine.updateSettings(engine.settings.copy(hapticsEnabled = false))
    assertFalse("Haptics controller must sync disabled state from settings", spyHaptics.enabled)
    engine.fireCooldown = 0f
    engine.switchWeapon(WeaponType.RIFLE)
    engine.input.firing = true
    engine.step(0.03f)
    engine.input.firing = false
    assertEquals("No recoil haptic should fire while disabled", 3, recordedWeapons.size)

    // Re-enable haptics for vehicle combat & collisions
    engine.updateSettings(engine.settings.copy(hapticsEnabled = true))

    // 3. Tank cannon recoil haptic
    engine.playerX = -35f
    engine.playerZ = -15f
    val tank = engine.spawnVehicle(VehicleType.BATTLE_TANK)!!
    engine.playerX = -55f
    engine.playerZ = -15f
    val targetCar = engine.spawnVehicle(VehicleType.SPORTS_CAR)!!

    engine.playerX = tank.x
    engine.playerZ = tank.z
    engine.toggleVehicle()
    engine.fireCooldown = 0f
    engine.input.turboQueued = true
    engine.step(0.03f)
    assertEquals("Firing tank cannon should trigger tank recoil haptic", 1, tankCannonCount)

    // 4. Vehicle-to-vehicle collision triggers collision haptic scaled by relative impact speed
    tank.x = -20f; tank.y = 0f; tank.z = -10f; tank.yaw = 0f; tank.speed = 20f
    targetCar.x = -20f; targetCar.y = 0f; targetCar.z = -12.5f; targetCar.yaw = 0f; targetCar.speed = 0f
    engine.step(0.03f)
    assertTrue("Vehicle-to-vehicle crash should trigger collision haptic", recordedCollisions.any { !it.third && it.first > 10f })

    // 5. Hard landing triggers landing collision haptic
    tank.x = -40f; tank.z = -30f; tank.speed = 0f; tank.y = 0.25f; tank.vy = -14f; tank.grounded = false
    tank.collisionSoundCooldown = 0f
    engine.step(0.03f)
    assertTrue("Hard vehicle landing should trigger landing haptic", recordedCollisions.any { it.third && it.first >= 10f })

    // 6. Ramming a crate triggers ram haptic
    val crate = WorldProp(id = "haptic_crate", type = PropType.CRATE, x = -40f, y = 0f, z = -34f, yaw = 0f)
    engine.props.add(crate)
    tank.x = -40f; tank.y = 0f; tank.z = -32.2f; tank.yaw = 0f; tank.speed = 16f
    engine.step(0.03f)
    assertTrue("Ramming prop with driven vehicle should trigger ram haptic", recordedRams.isNotEmpty())

    soundManager.release()
  }

  @Test
  fun `turbo speedway and tactical compound load dense layouts all 9 vehicles and 120 fps defaults`() {
    assertEquals(120, GameSettings().targetFps)

    val soundManager = SoundManager()

    // 1. TURBO_SPEEDWAY: racing & stunt circuit
    val speedway = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.TURBO_SPEEDWAY),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )
    assertEquals(GameMap.TURBO_SPEEDWAY, speedway.activeMap)
    assertEquals(VehicleType.entries.toSet(), speedway.vehicles.map { it.type }.toSet())
    assertTrue(speedway.props.any { it.type == PropType.FINISH_ARCH })
    assertTrue(speedway.props.any { it.type == PropType.LOOP_THE_LOOP })
    assertTrue(speedway.props.count { it.type == PropType.BOOST_PAD } >= 4)
    assertTrue("NW populated", speedway.props.any { it.x < -40f && it.z < -40f })
    assertTrue("SE populated", speedway.props.any { it.x > 40f && it.z > 40f })
    repeat(30) { speedway.step(1f / 120f) }

    // 2. TACTICAL_COMPOUND: dense cover & destruction fort
    val compound = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.TACTICAL_COMPOUND),
      peacefulMode = true,
      initialBotRoster = emptyList()
    )
    assertEquals(GameMap.TACTICAL_COMPOUND, compound.activeMap)
    assertEquals(VehicleType.entries.toSet(), compound.vehicles.map { it.type }.toSet())
    assertTrue(compound.props.count { it.type == PropType.WATCHTOWER } >= 2)
    assertTrue(compound.props.count { it.type.explosive } >= 8)
    assertTrue(compound.props.count { it.type == PropType.WALL_ARCH_GATE } >= 2)
    assertTrue("SW populated", compound.props.any { it.x < -30f && it.z > 30f })
    assertTrue("NE populated", compound.props.any { it.x > 30f && it.z < -30f })
    repeat(30) { compound.step(1f / 120f) }

    soundManager.release()
  }

  @Test
  fun `online mode is strictly humans only and applies live host room rules and map switching`() {
    val soundManager = SoundManager()
    val engine = GameEngine(
      soundManager = soundManager,
      initialSettings = GameSettings(selectedMap = GameMap.MAIN_ARENA),
      isOnlineMode = true,
      peacefulMode = false,
      localUserId = "guest_1",
      initialBotRoster = LobbyBotSlot.defaultRoster(5)
    )

    // Strictly zero bots and zero Pig-Burger NPCs in online rooms
    assertEquals(0, engine.bots.size)
    assertEquals(0, engine.pigBurgers.size)
    assertTrue(engine.latestHud.isOnlineMode)

    // Selecting NPCS build tab in online mode must be ignored and never spawn Pig-Burgers
    engine.toggleBuildMenu()
    engine.selectBuildCategory(BuildCategory.NPCS)
    assertTrue(engine.buildCategory != BuildCategory.NPCS)
    engine.placeCurrentGhost()
    assertEquals(0, engine.pigBurgers.size)

    // Apply guest-restricted room rules + live map switch to TURBO_SPEEDWAY
    val restrictedRules = OnlineRoomRules(
      mapId = GameMap.TURBO_SPEEDWAY.name,
      gameMode = OnlineGameMode.RACING,
      allowGuestPropSpawn = false,
      allowGuestVehicleSpawn = false,
      friendlyFire = false,
      infiniteAmmo = true,
      godMode = true
    )
    engine.applyOnlineRoomRules(restrictedRules, isHost = false)
    assertEquals(GameMap.TURBO_SPEEDWAY, engine.activeMap)
    assertEquals(0, engine.bots.size)
    assertEquals(0, engine.pigBurgers.size)
    assertFalse(engine.latestHud.canBuildProps)
    assertFalse(engine.latestHud.canSpawnVehicles)
    assertTrue(engine.latestHud.godMode)
    assertTrue(engine.latestHud.infiniteAmmo)

    // Guest cannot spawn props or vehicles when disallowed
    val propsBefore = engine.props.size
    val vehiclesBefore = engine.vehicles.size
    engine.placeCurrentGhost()
    assertNull(engine.spawnVehicle(VehicleType.POLICE_SUV))
    assertEquals(propsBefore, engine.props.size)
    assertEquals(vehiclesBefore, engine.vehicles.size)

    // Infinite ammo keeps magazine full while firing
    val ammoBefore = engine.latestHud.ammo
    engine.input.firing = true
    engine.step(0.03f)
    engine.input.firing = false
    assertEquals(ammoBefore, engine.latestHud.ammo)

    // Host can always build & spawn vehicles even when guest spawning is off, and switch map to TACTICAL_COMPOUND
    engine.applyOnlineRoomRules(restrictedRules.copy(mapId = GameMap.TACTICAL_COMPOUND.name), isHost = true)
    assertEquals(GameMap.TACTICAL_COMPOUND, engine.activeMap)
    assertTrue(engine.latestHud.isOnlineHost)
    assertTrue(engine.latestHud.canBuildProps)
    assertTrue(engine.latestHud.canSpawnVehicles)
    assertNotNull(engine.spawnVehicle(VehicleType.POLICE_SUV))

    soundManager.release()
  }
}
