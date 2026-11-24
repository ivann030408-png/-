package com.example.renderer

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.engine.BuildCategory
import com.example.engine.ChickenSkin
import com.example.engine.GameEngine
import com.example.engine.GameMap
import com.example.engine.PigBurgerNpc
import com.example.engine.PigBurgerState
import com.example.engine.PropType
import com.example.engine.VehicleEntity
import com.example.engine.VehicleType
import com.example.engine.WeaponSkin
import com.example.engine.WeaponType
import com.example.engine.WorldProp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Full glTF 2.0 (.glb) 3D Entity Renderer with automatic LVP LOD, zero per-frame heap allocations,
 * procedural node overrides (wheels, sirens, traps, springs, RPG rocket visibility, weapon/muzzle anchors),
 * and safe procedural fallbacks while assets stream in.
 */
class WorldEntityRenderer(
    private val host: ChickenGlRenderer
) {
    // Pre-allocated scratch buffers & static tables (zero GC churn per frame!)
    private val fpProjMatrix = FloatArray(16)
    private val savedVpMatrix = FloatArray(16)
    private val glbRootMatrix = FloatArray(16)
    private val weaponSlotMatrix = FloatArray(16)
    private val muzzleWorldMatrix = FloatArray(16)
    private val nodeOverrides = GlbNodeOverrides()
    private val rotBufA = FloatArray(3)
    private val rotBufB = FloatArray(3)
    private val rotBufC = FloatArray(3)
    private val transBufA = FloatArray(3)
    private val transBufB = FloatArray(3)
    private val emBufA = FloatArray(3)
    private val emBufB = FloatArray(3)
    private val legSides = floatArrayOf(-1f, 1f)
    private val tankRoadWheelZ = floatArrayOf(-1.9f, -0.95f, 0f, 0.95f, 1.9f)
    private val monsterLightX = floatArrayOf(-0.62f, -0.22f, 0.22f, 0.62f)
    private val wheelXSigns = floatArrayOf(-1f, 1f, -1f, 1f)
    private val wheelZSigns = floatArrayOf(-1f, -1f, 1f, 1f)
    private val wheelIsFront = booleanArrayOf(true, true, false, false)

    private val mapEnvironmentRenderer = MapEnvironmentRenderer(host)

    fun drawArenaEnvironment(
        camX: Float,
        camZ: Float,
        camFwdX: Float,
        camFwdZ: Float,
        renderDist: Float = 215f,
        worldTime: Float = 0f,
        lowEnd: Boolean = false,
        map: GameMap = GameMap.MAIN_ARENA
    ) {
        mapEnvironmentRenderer.draw(
            camX = camX,
            camZ = camZ,
            camFwdX = camFwdX,
            camFwdZ = camFwdZ,
            renderDist = renderDist,
            worldTime = worldTime,
            lowEnd = lowEnd,
            map = map
        )
    }

    fun propNeedsContactShadow(type: PropType): Boolean = when (type) {
        PropType.WALL,
        PropType.WALL_BRICK,
        PropType.WALL_WOOD,
        PropType.REINFORCED_FENCE,
        PropType.HOUSE_SMALL,
        PropType.GARAGE_HANGAR,
        PropType.WATCHTOWER,
        PropType.WALL_ARCH_GATE,
        PropType.BRIDGE,
        PropType.ROAD,
        PropType.ROAD_TURN,
        PropType.ROAD_ROUNDABOUT,
        PropType.ROAD_RAMP,
        PropType.BOOST_PAD,
        PropType.FINISH_ARCH,
        PropType.LOOP_THE_LOOP,
        PropType.FIRE_RING -> false
        else -> true
    }

    fun drawWorldProp(
        prop: WorldProp,
        shadowY: Float = 0.03f,
        highDetail: Boolean = true,
        worldTime: Float = 0f,
        drawShadow: Boolean = true
    ) {
        if (drawShadow && highDetail && propNeedsContactShadow(prop.type)) {
            val heightAbove = (prop.y - (shadowY - 0.03f)).coerceAtLeast(0f)
            val shadowScale = (1f - (heightAbove * 0.06f).coerceIn(0f, 0.55f))
            host.drawShadowDisc(
                prop.x, shadowY, prop.z,
                prop.type.halfW * 2.25f * shadowScale,
                prop.type.halfD * 2.25f * shadowScale,
                0.34f * shadowScale,
                yawDeg = prop.yaw
            )
        }

        // Fast .glb path for all custom 3D prop models
        val glbFile = prop.type.glbFile
        if (glbFile != null) {
            val gpuModel = host.glbManager.getOrLoadGpu(glbFile)
            if (gpuModel != null) {
                val bobY = if (prop.type.isHealPickup) {
                    0.18f + sin(worldTime * 3.4f + prop.trapPhaseOffset) * 0.14f
                } else 0f
                val extraYaw = when {
                    prop.type.isHealPickup -> (worldTime * 72f + prop.trapPhaseOffset * 57.3f) % 360f
                    prop.type == PropType.BOWLING_PIN -> prop.spinAngle
                    else -> 0f
                }

                Matrix.setIdentityM(glbRootMatrix, 0)
                Matrix.translateM(glbRootMatrix, 0, prop.x, prop.y + bobY, prop.z)
                Matrix.rotateM(glbRootMatrix, 0, -prop.yaw + 180f + extraYaw, 0f, 1f, 0f)
                if (prop.type == PropType.BOWLING_PIN && abs(prop.tiltAngle) > 0.2f) {
                    Matrix.rotateM(glbRootMatrix, 0, prop.tiltAngle, 1f, 0f, 0f)
                }
                val sc = prop.type.modelScale
                Matrix.scaleM(glbRootMatrix, 0, sc, sc, sc)

                nodeOverrides.clear()
                var useOverrides = false
                when (prop.type) {
                    PropType.PENDULUM_HAMMER -> {
                        val swingDeg = sin(worldTime * 2.15f + prop.trapPhaseOffset) * 58f
                        rotBufA[0] = 0f; rotBufA[1] = 0f; rotBufA[2] = swingDeg
                        nodeOverrides.extraRotDeg["hammer_pivot"] = rotBufA
                        useOverrides = true
                    }
                    PropType.CRUSHER -> {
                        val cycle = (worldTime * 1.45f + prop.trapPhaseOffset) % 2.4f
                        val headDrop = when {
                            cycle < 0.35f -> cycle / 0.35f
                            cycle < 0.65f -> 1f
                            else -> max(0f, 1f - (cycle - 0.65f) / 1.1f)
                        }
                        transBufA[0] = 0f; transBufA[1] = -headDrop * 2.15f; transBufA[2] = 0f
                        nodeOverrides.extraTrans["crusher_head"] = transBufA
                        useOverrides = true
                    }
                    PropType.SPINNER_TRAP -> {
                        val spinDeg = (worldTime * 152f + prop.trapPhaseOffset * 57.3f) % 360f
                        rotBufA[0] = 0f; rotBufA[1] = spinDeg; rotBufA[2] = 0f
                        nodeOverrides.extraRotDeg["spinner_arm"] = rotBufA
                        useOverrides = true
                    }
                    PropType.JUMP_PAD -> {
                        if (prop.bounceTimer > 0f) {
                            val bounceFrac = (prop.bounceTimer / 0.36f).coerceIn(0f, 1f)
                            val lift = sin(bounceFrac * PI.toFloat()) * 0.42f
                            transBufA[0] = 0f; transBufA[1] = lift; transBufA[2] = 0f
                            transBufB[0] = 0f; transBufB[1] = lift * 0.55f; transBufB[2] = 0f
                            nodeOverrides.extraTrans["pad_top"] = transBufA
                            nodeOverrides.extraTrans["spring"] = transBufB
                            useOverrides = true
                        }
                    }
                    PropType.TARGET -> {
                        if (prop.targetDownTimer > 0f) {
                            rotBufA[0] = 82f; rotBufA[1] = 0f; rotBufA[2] = 0f
                            nodeOverrides.extraRotDeg["target_board"] = rotBufA
                            useOverrides = true
                        }
                    }
                    PropType.FIRE_RING -> {
                        val pulse = 0.72f + 0.28f * sin(worldTime * 9.5f + prop.trapPhaseOffset)
                        emBufA[0] = 1.0f * pulse; emBufA[1] = 0.48f * pulse; emBufA[2] = 0.08f
                        nodeOverrides.emissiveBoost["fire_meshes"] = emBufA
                        useOverrides = true
                    }
                    else -> Unit
                }

                val doorAnimName = if (prop.type.hasInteractiveDoor && prop.doorTouched) {
                    if (prop.doorOpen) "open" else "close"
                } else null
                host.glbManager.drawModel(
                    renderer = host,
                    model = gpuModel,
                    rootWorldMatrix = glbRootMatrix,
                    animName = doorAnimName,
                    animTime = if (doorAnimName != null) prop.doorAnimTime else 0f,
                    animLoop = false,
                    overrides = if (useOverrides) nodeOverrides else null
                )
                return
            }
        }

        when (prop.type) {
            PropType.CRATE -> {
                val h = prop.type.height
                val w = prop.type.halfW * 2f
                host.drawBox(prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, w, h, w, 0.76f, 0.53f, 0.26f, 1f, spec = 0.10f)
                // Top & bottom wood frame bands
                host.drawBox(prop.x, prop.y + 0.13f, prop.z, prop.yaw, w + 0.07f, 0.24f, w + 0.07f, 0.48f, 0.30f, 0.14f, 1f, spec = 0.08f)
                host.drawBox(prop.x, prop.y + h - 0.13f, prop.z, prop.yaw, w + 0.07f, 0.24f, w + 0.07f, 0.48f, 0.30f, 0.14f, 1f, spec = 0.08f)
                if (highDetail) {
                    // Cross-braces & dark steel corner brackets
                    host.drawBox(prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, w + 0.06f, 0.20f, w + 0.06f, 0.52f, 0.33f, 0.15f, 1f, spec = 0.08f)
                    host.drawBox(prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, 0.22f, h + 0.04f, w + 0.08f, 0.44f, 0.27f, 0.12f, 1f, spec = 0.08f)
                    host.drawBox(prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, w + 0.08f, h + 0.04f, 0.22f, 0.44f, 0.27f, 0.12f, 1f, spec = 0.08f)
                    // Steel diagonal reinforcing bar on front/back faces
                    Matrix.setIdentityM(host.modelMatrix, 0)
                    Matrix.translateM(host.modelMatrix, 0, prop.x, prop.y + h * 0.5f, prop.z)
                    Matrix.rotateM(host.modelMatrix, 0, -prop.yaw, 0f, 1f, 0f)
                    Matrix.rotateM(host.modelMatrix, 0, 45f, 0f, 0f, 1f)
                    Matrix.scaleM(host.modelMatrix, 0, 0.18f, h * 1.18f, w + 0.07f)
                    host.drawCurrentModel(host.cubeMesh, 0.26f, 0.28f, 0.32f, 1f, 0.55f)
                }
            }
            PropType.WALL -> {
                val w = prop.type.halfW * 2f
                val h = prop.type.height
                val d = prop.type.halfD * 2f
                // Concrete Jersey-style tactical wall + beveled base & steel cap
                host.drawBox(prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, w, h, d, 0.70f, 0.74f, 0.79f, 1f, spec = 0.14f)
                host.drawBox(prop.x, prop.y + h + 0.09f, prop.z, prop.yaw, w + 0.16f, 0.24f, d + 0.18f, 0.36f, 0.41f, 0.48f, 1f, spec = 0.24f)
                if (highDetail) {
                    host.drawBox(prop.x, prop.y + 0.15f, prop.z, prop.yaw, w + 0.14f, 0.30f, d + 0.18f, 0.30f, 0.34f, 0.40f, 1f, spec = 0.16f)
                    // Alternating yellow & dark-charcoal hazard warning stripes along lower face!
                    val stripes = 5
                    val segW = w / stripes
                    val startOff = -w * 0.5f + segW * 0.5f
                    for (s in 0 until stripes) {
                        val off = startOff + s * segW
                        val sx = prop.x + prop.cosYaw * off
                        val sz = prop.z + prop.sinYaw * off
                        val isYellow = (s and 1) == 0
                        host.drawBox(
                            sx, prop.y + 0.48f, sz, prop.yaw,
                            segW * 0.96f, 0.32f, d + 0.08f,
                            if (isYellow) 0.98f else 0.16f,
                            if (isYellow) 0.74f else 0.18f,
                            if (isYellow) 0.10f else 0.22f,
                            1f, spec = 0.28f
                        )
                    }
                }
            }
            PropType.RAMP -> {
                val w = prop.type.halfW * 2f
                val h = prop.type.height
                val d = prop.type.halfD * 2f
                host.drawMesh(host.rampMesh, prop.x, prop.y, prop.z, prop.yaw, w, h, d, 0.96f, 0.56f, 0.10f, 1f, spec = 0.35f)
                if (highDetail) {
                    val slopeAngleDeg = kotlin.math.atan2(h, d) * 180f / PI.toFloat()
                    // Bold white-and-dark racing chevron stripes across the ramp launch surface + side guard rails
                    for (k in 0..2) {
                        val frac = 0.26f + k * 0.24f
                        val localZ = prop.type.halfD - frac * d
                        val localY = frac * h + 0.05f
                        val wx = prop.x - prop.sinYaw * localZ
                        val wz = prop.z + prop.cosYaw * localZ
                        Matrix.setIdentityM(host.modelMatrix, 0)
                        Matrix.translateM(host.modelMatrix, 0, wx, prop.y + localY, wz)
                        Matrix.rotateM(host.modelMatrix, 0, -prop.yaw, 0f, 1f, 0f)
                        Matrix.rotateM(host.modelMatrix, 0, slopeAngleDeg, 1f, 0f, 0f)
                        Matrix.scaleM(host.modelMatrix, 0, w * 0.88f, 0.06f, 0.52f)
                        val bright = (k and 1) == 0
                        host.drawCurrentModel(
                            host.cubeMesh,
                            if (bright) 0.97f else 0.16f,
                            if (bright) 0.97f else 0.20f,
                            if (bright) 0.99f else 0.26f,
                            1f, 0.45f
                        )
                    }
                    // Top launch lip + glowing cyan boost strip
                    val topX = prop.x + prop.sinYaw * (prop.type.halfD - 0.22f)
                    val topZ = prop.z - prop.cosYaw * (prop.type.halfD - 0.22f)
                    host.drawBox(topX, prop.y + h + 0.03f, topZ, prop.yaw, w * 0.98f, 0.14f, 0.44f, 0.16f, 0.20f, 0.26f, 1f, spec = 0.65f)
                    host.setUnlit(true)
                    host.drawBox(topX, prop.y + h + 0.11f, topZ, prop.yaw, w * 0.82f, 0.07f, 0.18f, 0.18f, 0.95f, 0.92f, 1f)
                    host.setUnlit(false)
                }
            }
            PropType.TOWER -> {
                val w = prop.type.halfW * 2f
                val h = prop.type.height
                val d = prop.type.halfD * 2f
                // Corrugated steel shipping-container / guard-tower block with vertical ribs & observation deck rail
                host.drawBox(prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, w, h, d, 0.42f, 0.52f, 0.62f, 1f, spec = 0.24f)
                host.drawBox(prop.x, prop.y + h - 0.15f, prop.z, prop.yaw, w + 0.24f, 0.32f, d + 0.24f, 0.24f, 0.30f, 0.38f, 1f, spec = 0.38f)
                if (highDetail) {
                    // Corrugated vertical steel container ribs & roof parapet
                    for (ri in -1..1) {
                        val off = ri * (w * 0.30f)
                        val rx = prop.x + prop.cosYaw * off
                        val rz = prop.z + prop.sinYaw * off
                        host.drawBox(rx, prop.y + h * 0.5f, rz, prop.yaw, 0.24f, h - 0.35f, d + 0.12f, 0.32f, 0.40f, 0.50f, 1f, spec = 0.32f)
                    }
                    host.drawBox(prop.x, prop.y + h * 0.52f, prop.z, prop.yaw, w + 0.10f, 0.36f, d + 0.10f, 0.18f, 0.78f, 0.96f, 1f, spec = 0.55f)
                    host.drawBox(prop.x, prop.y + 0.18f, prop.z, prop.yaw, w + 0.18f, 0.36f, d + 0.18f, 0.24f, 0.28f, 0.34f, 1f, spec = 0.30f)
                }
            }
            PropType.BRIDGE -> {
                val w = prop.type.halfW * 2f
                val h = prop.type.height
                val d = prop.type.halfD * 2f
                host.drawBox(prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, w, h, d, 0.42f, 0.47f, 0.55f, 1f, spec = 0.38f)
                if (highDetail) {
                    val rx = prop.cosYaw * (prop.type.halfW - 0.12f)
                    val rz = prop.sinYaw * (prop.type.halfW - 0.12f)
                    host.drawBox(prop.x - rx, prop.y + h + 0.25f, prop.z - rz, prop.yaw, 0.22f, 0.50f, d, 0.95f, 0.68f, 0.12f, 1f, spec = 0.45f)
                    host.drawBox(prop.x + rx, prop.y + h + 0.25f, prop.z + rz, prop.yaw, 0.22f, 0.50f, d, 0.95f, 0.68f, 0.12f, 1f, spec = 0.45f)
                }
            }
            PropType.TARGET -> {
                host.drawBox(prop.x, prop.y + 0.45f, prop.z, prop.yaw, 0.22f, 0.95f, 0.22f, 0.48f, 0.32f, 0.16f, 1f, spec = 0.08f)
                if (highDetail) {
                    host.drawBox(prop.x, prop.y + 0.08f, prop.z, prop.yaw, 1.35f, 0.16f, 0.65f, 0.42f, 0.28f, 0.14f, 1f, spec = 0.08f)
                }
                host.drawBox(prop.x, prop.y + 1.38f, prop.z, prop.yaw, 1.65f, 1.45f, 0.16f, 0.96f, 0.96f, 0.98f, 1f, spec = 0.20f)
                host.drawBox(prop.x, prop.y + 1.38f, prop.z, prop.yaw, 1.12f, 0.98f, 0.20f, 0.94f, 0.18f, 0.16f, 1f, spec = 0.25f)
                host.drawBox(prop.x, prop.y + 1.38f, prop.z, prop.yaw, 0.55f, 0.48f, 0.24f, 0.98f, 0.85f, 0.16f, 1f, spec = 0.35f)
            }
            PropType.BARREL -> {
                val h = prop.type.height
                val dia = prop.type.halfW * 2f
                host.drawMesh(host.cylinderMesh, prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, dia, h, dia, 0.92f, 0.14f, 0.12f, 1f, spec = 0.55f)
                host.setUnlit(true)
                host.drawMesh(host.cylinderMesh, prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, dia + 0.06f, 0.22f, dia + 0.06f, 1.0f, 0.86f, 0.14f, 1f)
                host.setUnlit(false)
                if (highDetail) {
                    host.drawMesh(host.cylinderMesh, prop.x, prop.y + h - 0.06f, prop.z, prop.yaw, dia + 0.08f, 0.10f, dia + 0.08f, 0.20f, 0.22f, 0.26f, 1f, spec = 0.72f)
                    host.drawMesh(host.cylinderMesh, prop.x, prop.y + 0.07f, prop.z, prop.yaw, dia + 0.08f, 0.10f, dia + 0.08f, 0.20f, 0.22f, 0.26f, 1f, spec = 0.72f)
                    host.drawMesh(host.cylinderMesh, prop.x, prop.y + h * 0.28f, prop.z, prop.yaw, dia + 0.06f, 0.06f, dia + 0.06f, 0.70f, 0.12f, 0.10f, 1f, spec = 0.65f)
                    host.drawMesh(host.cylinderMesh, prop.x, prop.y + h * 0.72f, prop.z, prop.yaw, dia + 0.06f, 0.06f, dia + 0.06f, 0.70f, 0.12f, 0.10f, 1f, spec = 0.65f)
                    // Metallic top fill bung cap
                    host.drawMesh(host.cylinderMesh, prop.x + 0.18f, prop.y + h + 0.04f, prop.z, prop.yaw, 0.16f, 0.08f, 0.16f, 0.76f, 0.80f, 0.86f, 1f, spec = 0.90f)
                }
            }
            PropType.CAR -> Unit
            else -> {
                // Safe procedural fallback while a .glb prop streams in
                val w = prop.type.halfW * 2f
                val h = prop.type.height
                val d = prop.type.halfD * 2f
                val r = when {
                    prop.type.isHealPickup -> 0.25f
                    prop.type.isWaterBarrel -> 0.16f
                    prop.type.isTrap -> 0.92f
                    prop.type == PropType.ROAD -> 0.24f
                    else -> 0.85f
                }
                val g = when {
                    prop.type.isHealPickup -> 0.92f
                    prop.type.isWaterBarrel -> 0.62f
                    prop.type.isTrap -> 0.32f
                    prop.type == PropType.ROAD -> 0.26f
                    else -> 0.56f
                }
                val b = when {
                    prop.type.isHealPickup -> 0.36f
                    prop.type.isWaterBarrel -> 0.96f
                    prop.type.isTrap -> 0.18f
                    prop.type == PropType.ROAD -> 0.30f
                    else -> 0.22f
                }
                if (prop.type.isWaterBarrel || prop.type == PropType.GAS_BARREL || prop.type == PropType.TIRE_STACK || prop.type == PropType.BOWLING_PIN) {
                    host.drawMesh(host.cylinderMesh, prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, w, h, d, r, g, b, 1f, spec = 0.45f)
                } else {
                    host.drawBox(prop.x, prop.y + h * 0.5f, prop.z, prop.yaw, w, h, d, r, g, b, 1f, spec = 0.30f)
                }
            }
        }
    }

    fun drawPlacementGhost(engine: GameEngine) {
        val valid = engine.ghostPlacementValid
        val alpha = 0.46f
        val gR = if (valid) 0.16f else 0.98f
        val gG = if (valid) 0.96f else 0.22f
        val gB = if (valid) 0.76f else 0.24f
        val x = engine.ghostX
        val y = engine.ghostY
        val z = engine.ghostZ
        val yaw = engine.ghostYaw

        // Ground placement ring indicator
        host.setUnlit(true)
        if (engine.buildCategory == BuildCategory.NPCS) {
            host.drawBox(x, y + 0.05f, z, yaw, 2.0f, 0.06f, 2.0f, gR, gG, gB, 0.30f)
            host.setUnlit(false)
            val pigGpu = host.glbManager.getOrLoadGpu("pig_burger_animated.glb")
            if (pigGpu != null) {
                Matrix.setIdentityM(glbRootMatrix, 0)
                Matrix.translateM(glbRootMatrix, 0, x, y, z)
                Matrix.rotateM(glbRootMatrix, 0, -yaw + 180f, 0f, 1f, 0f)
                Matrix.scaleM(glbRootMatrix, 0, 1.28f, 1.28f, 1.28f)
                host.glbManager.drawModel(
                    renderer = host,
                    model = pigGpu,
                    rootWorldMatrix = glbRootMatrix,
                    animName = "Idle",
                    animTime = engine.worldTime,
                    tintR = gR,
                    tintG = gG,
                    tintB = gB,
                    alphaOverride = alpha
                )
            } else {
                host.drawMesh(host.sphereMesh, x, y + 0.85f, z, yaw, 1.3f, 1.3f, 1.3f, gR, gG, gB, alpha)
            }
            return
        }

        if (engine.buildCategory == BuildCategory.VEHICLES) {
            val vt = engine.selectedVehicleType
            val bodyW = vt.halfW * 1.92f
            val bodyL = vt.halfL * 1.92f
            host.drawBox(x, y + 0.05f, z, yaw, bodyW + 0.35f, 0.06f, bodyL + 0.35f, gR, gG, gB, 0.28f)
            host.setUnlit(false)

            val carGlb = vt.glbFile?.let { host.glbManager.getOrLoadGpu(it) }
            if (carGlb != null) {
                Matrix.setIdentityM(glbRootMatrix, 0)
                Matrix.translateM(glbRootMatrix, 0, x, y, z)
                Matrix.rotateM(glbRootMatrix, 0, -yaw + 180f, 0f, 1f, 0f)
                val sc = vt.modelScale
                Matrix.scaleM(glbRootMatrix, 0, sc, sc, sc)
                host.glbManager.drawModel(
                    renderer = host,
                    model = carGlb,
                    rootWorldMatrix = glbRootMatrix,
                    tintR = gR,
                    tintG = gG,
                    tintB = gB,
                    alphaOverride = alpha
                )
                return
            }

            fun ghostPart(
                mesh: GlMesh,
                lx: Float, ly: Float, lz: Float,
                sx: Float, sy: Float, sz: Float,
                rotX: Float = 0f,
                rotZ: Float = 0f
            ) {
                Matrix.setIdentityM(host.modelMatrix, 0)
                Matrix.translateM(host.modelMatrix, 0, x, y, z)
                Matrix.rotateM(host.modelMatrix, 0, -yaw, 0f, 1f, 0f)
                Matrix.translateM(host.modelMatrix, 0, lx, ly, lz)
                if (rotX != 0f) Matrix.rotateM(host.modelMatrix, 0, rotX, 1f, 0f, 0f)
                if (rotZ != 0f) Matrix.rotateM(host.modelMatrix, 0, rotZ, 0f, 0f, 1f)
                Matrix.scaleM(host.modelMatrix, 0, sx, sy, sz)
                host.drawCurrentModel(mesh, gR, gG, gB, alpha, 0.35f)
            }
            val bodyH = when (vt) {
                VehicleType.MONSTER_TRUCK -> 0.80f
                VehicleType.FIRE_TRUCK, VehicleType.ARMORED_SWAT -> 1.35f
                VehicleType.BATTLE_TANK -> 0.88f
                else -> 0.56f
            }
            val bodyY = when (vt) {
                VehicleType.MONSTER_TRUCK -> 1.35f
                VehicleType.FIRE_TRUCK, VehicleType.ARMORED_SWAT -> 1.15f
                VehicleType.BATTLE_TANK -> 0.96f
                else -> 0.64f
            }
            ghostPart(host.cubeMesh, 0f, bodyY, 0f, bodyW, bodyH, bodyL)
            when (vt) {
                VehicleType.BATTLE_TANK -> {
                    ghostPart(host.cylinderMesh, 0f, 1.72f, -0.10f, 2.0f, 0.70f, 2.2f)
                    ghostPart(host.cylinderMesh, 0f, 1.76f, -2.20f, 0.26f, 3.1f, 0.26f, rotX = 90f)
                }
                VehicleType.FIRE_TRUCK -> {
                    ghostPart(host.cubeMesh, 0f, 2.05f, 0.60f, 0.95f, 0.30f, 4.4f)
                }
                VehicleType.SPORTS_CAR, VehicleType.CYBER_SUPERCAR -> {
                    ghostPart(host.cubeMesh, 0f, 1.06f, 0.18f, bodyW * 0.78f, 0.46f, bodyL * 0.46f)
                    ghostPart(host.cubeMesh, 0f, 1.38f, bodyL * 0.44f, bodyW * 1.04f, 0.12f, 0.42f)
                }
                else -> {
                    ghostPart(host.cubeMesh, 0f, bodyY + bodyH * 0.62f, -0.05f, bodyW * 0.84f, 0.64f, bodyL * 0.46f)
                }
            }
            val wDia = if (vt == VehicleType.MONSTER_TRUCK) 1.32f else 0.82f
            val wTrack = vt.halfW * 0.94f
            val wZ = vt.halfL * 0.60f
            for (i in 0 until 4) {
                ghostPart(
                    host.cylinderMesh,
                    wheelXSigns[i] * wTrack,
                    wDia * 0.5f,
                    wheelZSigns[i] * wZ,
                    wDia,
                    0.46f,
                    wDia,
                    rotZ = 90f
                )
            }
            return
        }

        val type = engine.selectedBuildProp
        host.drawBox(x, y + 0.04f, z, yaw, type.halfW * 2.15f, 0.05f, type.halfD * 2.15f, gR, gG, gB, 0.26f)
        host.setUnlit(false)
        val propGlb = type.glbFile?.let { host.glbManager.getOrLoadGpu(it) }
        if (propGlb != null) {
            Matrix.setIdentityM(glbRootMatrix, 0)
            Matrix.translateM(glbRootMatrix, 0, x, y, z)
            Matrix.rotateM(glbRootMatrix, 0, -yaw + 180f, 0f, 1f, 0f)
            val sc = type.modelScale
            Matrix.scaleM(glbRootMatrix, 0, sc, sc, sc)
            host.glbManager.drawModel(
                renderer = host,
                model = propGlb,
                rootWorldMatrix = glbRootMatrix,
                tintR = gR,
                tintG = gG,
                tintB = gB,
                alphaOverride = alpha
            )
            return
        }
        when (type) {
            PropType.RAMP, PropType.DOUBLE_RAMP -> host.drawMesh(host.rampMesh, x, y, z, yaw, type.halfW * 2f, type.height, type.halfD * 2f, gR, gG, gB, alpha)
            PropType.BARREL, PropType.GAS_BARREL, PropType.WATER_BARREL, PropType.TIRE_STACK -> host.drawMesh(host.cylinderMesh, x, y + type.height * 0.5f, z, yaw, type.halfW * 2f, type.height, type.halfW * 2f, gR, gG, gB, alpha)
            else -> host.drawBox(x, y + type.height * 0.5f, z, yaw, type.halfW * 2f, type.height, type.halfD * 2f, gR, gG, gB, alpha)
        }
    }

    /**
     * Upgraded Sculpted 3D Combat Chicken with tactical helmet + comb cutout, neck ruff,
     * armored vest ammo pouches, layered tail feathers, and automatic LVP LOD!
     */
    fun drawCombatChicken(
        x: Float, y: Float, z: Float, yaw: Float,
        walkCycle: Float,
        weapon: WeaponType?,
        muzzleFlash: Boolean,
        hitFlash: Float,
        accentR: Float, accentG: Float, accentB: Float,
        seated: Boolean,
        seatedVehicle: VehicleEntity? = null,
        shieldActive: Boolean = false,
        shadowY: Float = 0.03f,
        highDetail: Boolean = true,
        skin: ChickenSkin? = null,
        worldTime: Float = 0f,
        weaponSkin: WeaponSkin = WeaponSkin.CLASSIC,
        grounded: Boolean = true,
        moveSpeed: Float = 0f,
        rpgRocketLoaded: Boolean = true,
        weaponRecoil: Float = 0f,
        reloadProgress: Float = 0f,
        onMuzzleWorld: ((Float, Float, Float) -> Unit)? = null
    ) {
        if (!seated) {
            val heightAbove = (y - (shadowY - 0.03f)).coerceAtLeast(0f)
            val shadowFade = (1f - (heightAbove * 0.08f).coerceIn(0f, 0.65f))
            host.drawShadowDisc(x, shadowY, z, 1.35f * shadowFade, 1.35f * shadowFade, 0.44f * shadowFade)
        }

        // Primary .glb character path: khryak_napoleon_animated.glb + weapon attached to weapon_slot!
        val charGpu = host.glbManager.getOrLoadGpu("khryak_napoleon_animated.glb", syncIfMissing = true)
        if (charGpu != null) {
            val animName = when {
                seated -> "Idle"
                !grounded -> "Jump"
                moveSpeed > 7.5f -> "Run"
                moveSpeed > 0.7f -> "Walk"
                weapon != null -> "HoldGun"
                else -> "Idle"
            }
            val animTime = if (animName == "Walk" || animName == "Run") walkCycle * 0.30f else worldTime

            Matrix.setIdentityM(glbRootMatrix, 0)
            if (seated && seatedVehicle != null) {
                val vt = seatedVehicle.type
                // Inherit exact vehicle world matrix (suspension + pitch + roll + 360° loop tilt)
                Matrix.translateM(glbRootMatrix, 0, seatedVehicle.x, seatedVehicle.y + seatedVehicle.suspensionOffset, seatedVehicle.z)
                Matrix.rotateM(glbRootMatrix, 0, -seatedVehicle.yaw + 180f, 0f, 1f, 0f)
                if (seatedVehicle.pitchAngle != 0f) Matrix.rotateM(glbRootMatrix, 0, -seatedVehicle.pitchAngle, 1f, 0f, 0f)
                if (seatedVehicle.rollAngle != 0f) Matrix.rotateM(glbRootMatrix, 0, -seatedVehicle.rollAngle, 0f, 0f, 1f)
                if (vt.driverRotatesWithTurret && seatedVehicle.turretYaw != 0f) {
                    // Rotate tank commander cupola seat with the turret around turret center (GLB z = +0.10)
                    Matrix.translateM(glbRootMatrix, 0, 0f, 0f, 0.10f)
                    Matrix.rotateM(glbRootMatrix, 0, -seatedVehicle.turretYaw, 0f, 1f, 0f)
                    Matrix.translateM(glbRootMatrix, 0, 0f, 0f, -0.10f)
                }
                Matrix.translateM(glbRootMatrix, 0, vt.driverSeatX, vt.driverSeatY, vt.driverSeatZ)
                Matrix.scaleM(glbRootMatrix, 0, vt.driverScale, vt.driverScale, vt.driverScale)
            } else {
                val charScale = if (seated) 0.66f else 1.26f
                // Lift by +0.088 * charScale because khryak_napoleon_animated.glb feet sit at minY = -0.088
                val footLift = if (seated) 0f else 0.088f * charScale
                Matrix.translateM(glbRootMatrix, 0, x, y + footLift, z)
                Matrix.rotateM(glbRootMatrix, 0, -yaw + 180f, 0f, 1f, 0f)
                Matrix.scaleM(glbRootMatrix, 0, charScale, charScale, charScale)
            }

            // Tint slightly on damage flash or subtle skin tint
            val skinTintR = if (skin != null && skin != ChickenSkin.CLASSIC) 0.78f + skin.featherR * 0.24f else 1f
            val skinTintG = if (skin != null && skin != ChickenSkin.CLASSIC) 0.78f + skin.featherG * 0.24f else 1f
            val skinTintB = if (skin != null && skin != ChickenSkin.CLASSIC) 0.78f + skin.featherB * 0.24f else 1f
            val tintR = if (hitFlash > 0.05f) 1.0f else skinTintR
            val tintG = if (hitFlash > 0.05f) (skinTintG * (1f - hitFlash * 0.65f)) else skinTintG
            val tintB = if (hitFlash > 0.05f) (skinTintB * (1f - hitFlash * 0.65f)) else skinTintB

            Matrix.setIdentityM(weaponSlotMatrix, 0)
            nodeOverrides.clear()
            if (weapon != null) {
                nodeOverrides.captureWorldMatrix["weapon_slot"] = weaponSlotMatrix
            }
            if (seated) {
                // Tuck/hide lower legs inside the cabin so feet never poke through car floor or wheels
                nodeOverrides.hiddenNodes.add("leg_L")
                nodeOverrides.hiddenNodes.add("leg_R")
                // Reach arms forward toward the steering wheel / controls
                rotBufC[0] = -42f; rotBufC[1] = 0f; rotBufC[2] = 0f
                nodeOverrides.extraRotDeg["arm_L"] = rotBufC
                nodeOverrides.extraRotDeg["arm_R"] = rotBufC
            }

            // Layer HoldGun pose onto arm_R during Walk/Run/Jump so the gun stays aimed forward!
            val overlayHoldArm = weapon != null && !seated && animName != "HoldGun"
            host.glbManager.drawModel(
                renderer = host,
                model = charGpu,
                rootWorldMatrix = glbRootMatrix,
                animName = animName,
                animTime = animTime,
                tintR = tintR,
                tintG = tintG,
                tintB = tintB,
                overrides = if (weapon != null || seated) nodeOverrides else null,
                overlayAnimName = if (overlayHoldArm) "HoldGun" else null,
                overlayNodeName = if (overlayHoldArm) "arm_R" else null
            )

            // Draw attached .glb weapon directly at the animated weapon_slot anchor!
            if (weapon != null) {
                val weaponGpu = host.glbManager.getOrLoadGpu(weapon.glbFile(weaponSkin), syncIfMissing = true)
                if (weaponGpu != null) {
                    val wScale = when (weapon) {
                        WeaponType.PISTOL -> 1.38f
                        WeaponType.RIFLE -> 1.02f
                        WeaponType.RPG -> 0.88f
                    }
                    val reloadArc = if (reloadProgress > 0.01f) sin(reloadProgress * PI.toFloat()) else 0f
                    // Fine-tune grip anchor in weapon_slot space (+Z forward, +Y up) with recoil & reload dip
                    Matrix.translateM(
                        weaponSlotMatrix, 0,
                        0.01f,
                        0.02f - reloadArc * 0.08f,
                        0.05f - weaponRecoil * 0.14f
                    )
                    if (weaponRecoil > 0.005f) {
                        Matrix.rotateM(weaponSlotMatrix, 0, -weaponRecoil * 24f, 1f, 0f, 0f)
                    }
                    if (reloadArc > 0.01f) {
                        Matrix.rotateM(weaponSlotMatrix, 0, reloadArc * 28f, 0f, 0f, 1f)
                    }
                    Matrix.scaleM(weaponSlotMatrix, 0, wScale, wScale, wScale)

                    Matrix.setIdentityM(muzzleWorldMatrix, 0)
                    nodeOverrides.clear()
                    if (weapon == WeaponType.RPG && !rpgRocketLoaded) {
                        nodeOverrides.hiddenNodes.add("rocket")
                    }
                    if (weaponRecoil > 0.01f && weapon != WeaponType.RPG) {
                        transBufA[0] = 0f; transBufA[1] = 0f; transBufA[2] = -weaponRecoil * 0.16f
                        nodeOverrides.extraTrans["slide"] = transBufA
                    }
                    if (reloadArc > 0.02f && weapon != WeaponType.RPG) {
                        transBufB[0] = 0f; transBufB[1] = -reloadArc * 0.18f; transBufB[2] = 0f
                        nodeOverrides.extraTrans["magazine"] = transBufB
                    }
                    nodeOverrides.captureWorldMatrix["muzzle"] = muzzleWorldMatrix

                    host.glbManager.drawModel(
                        renderer = host,
                        model = weaponGpu,
                        rootWorldMatrix = weaponSlotMatrix,
                        overrides = nodeOverrides
                    )

                    val mx = muzzleWorldMatrix[12]
                    val my = muzzleWorldMatrix[13]
                    val mz = muzzleWorldMatrix[14]
                    val hasMuzzle = (mx != 0f || my != 0f || mz != 0f)
                    if (hasMuzzle) {
                        onMuzzleWorld?.invoke(mx, my, mz)
                        if (muzzleFlash) {
                            host.setUnlit(true)
                            host.drawMesh(host.sphereMesh, mx, my, mz, 0f, 0.30f, 0.30f, 0.30f, 1f, 0.90f, 0.28f, 1f)
                            host.setUnlit(false)
                        }
                    }
                }
            }

            // Spawn protection golden bubble
            if (shieldActive) {
                host.setUnlit(true)
                host.setDepthMask(false)
                host.drawMesh(host.sphereMesh, x, y + 1.15f, z, 0f, 1.32f, 1.42f, 1.32f, 1.0f, 0.84f, 0.22f, 0.22f)
                host.setDepthMask(true)
                host.setUnlit(false)
            }
            return
        }

        val baseR = skin?.featherR ?: 0.98f
        val baseG = skin?.featherG ?: 0.97f
        val baseB = skin?.featherB ?: 0.95f
        val whiteR = if (hitFlash > 0.05f) (baseR + (1f - baseR) * hitFlash).coerceIn(0f, 1f) else baseR
        val whiteG = if (hitFlash > 0.05f) baseG * (1f - hitFlash * 0.68f) else baseG
        val whiteB = if (hitFlash > 0.05f) baseB * (1f - hitFlash * 0.68f) else baseB

        val vestR = skin?.vestR ?: accentR
        val vestG = skin?.vestG ?: accentG
        val vestB = skin?.vestB ?: accentB
        val helmetR = skin?.helmetR ?: 0.20f
        val helmetG = skin?.helmetG ?: 0.24f
        val helmetB = skin?.helmetB ?: 0.26f
        val combR = skin?.combR ?: 0.96f
        val combG = skin?.combG ?: 0.12f
        val combB = skin?.combB ?: 0.14f
        val visorR = skin?.visorR ?: 0.16f
        val visorG = skin?.visorG ?: 0.64f
        val visorB = skin?.visorB ?: 0.88f

        val stride = if (seated) 0f else sin(walkCycle) * 0.24f
        val bob = if (seated) 0f else abs(sin(walkCycle)) * 0.08f
        val wingFlap = if (seated) 0f else sin(walkCycle) * 18f

        fun localPart(
            mesh: GlMesh,
            lx: Float, ly: Float, lz: Float,
            sx: Float, sy: Float, sz: Float,
            r: Float, g: Float, b: Float,
            pitchExtra: Float = 0f,
            rollExtra: Float = 0f,
            yawExtra: Float = 0f,
            spec: Float = 0.14f,
            alpha: Float = 1f
        ) {
            Matrix.setIdentityM(host.modelMatrix, 0)
            if (seated && seatedVehicle != null) {
                val vt = seatedVehicle.type
                Matrix.translateM(host.modelMatrix, 0, seatedVehicle.x, seatedVehicle.y + seatedVehicle.suspensionOffset, seatedVehicle.z)
                Matrix.rotateM(host.modelMatrix, 0, -seatedVehicle.yaw, 0f, 1f, 0f)
                if (seatedVehicle.pitchAngle != 0f) Matrix.rotateM(host.modelMatrix, 0, seatedVehicle.pitchAngle, 1f, 0f, 0f)
                if (seatedVehicle.rollAngle != 0f) Matrix.rotateM(host.modelMatrix, 0, seatedVehicle.rollAngle, 0f, 0f, 1f)
                if (vt.driverRotatesWithTurret && seatedVehicle.turretYaw != 0f) {
                    Matrix.translateM(host.modelMatrix, 0, 0f, 0f, -0.10f)
                    Matrix.rotateM(host.modelMatrix, 0, -seatedVehicle.turretYaw, 0f, 1f, 0f)
                    Matrix.translateM(host.modelMatrix, 0, 0f, 0f, 0.10f)
                }
                // Convert GLB (+X left, +Z forward) seat coords to procedural engine (-X left, -Z forward)
                Matrix.translateM(host.modelMatrix, 0, -vt.driverSeatX, vt.driverSeatY - 0.12f, -vt.driverSeatZ)
                Matrix.scaleM(host.modelMatrix, 0, vt.driverScale, vt.driverScale, vt.driverScale)
            } else {
                Matrix.translateM(host.modelMatrix, 0, x, y + bob, z)
                Matrix.rotateM(host.modelMatrix, 0, -yaw, 0f, 1f, 0f)
            }
            Matrix.translateM(host.modelMatrix, 0, lx, ly, lz)
            if (yawExtra != 0f) Matrix.rotateM(host.modelMatrix, 0, yawExtra, 0f, 1f, 0f)
            if (pitchExtra != 0f) Matrix.rotateM(host.modelMatrix, 0, pitchExtra, 1f, 0f, 0f)
            if (rollExtra != 0f) Matrix.rotateM(host.modelMatrix, 0, rollExtra, 0f, 0f, 1f)
            Matrix.scaleM(host.modelMatrix, 0, sx, sy, sz)
            host.drawCurrentModel(mesh, r, g, b, alpha, spec)
        }

        // 1. Articulated Orange Legs & 3-Toed Chicken Feet
        if (!seated) {
            for (i in legSides.indices) {
                val side = legSides[i]
                val legZ = if (side < 0f) stride else -stride
                val sx = side * 0.22f
                if (highDetail) {
                    localPart(host.sphereMesh, sx, 0.48f, legZ * 0.4f, 0.16f, 0.14f, 0.16f, whiteR, whiteG, whiteB)
                }
                localPart(host.cylinderMesh, sx, 0.25f, legZ, 0.10f, 0.44f, 0.10f, 0.98f, 0.56f, 0.05f, spec = 0.25f)
                localPart(host.cubeMesh, sx, 0.05f, legZ - 0.12f, 0.12f, 0.06f, 0.28f, 0.98f, 0.50f, 0.03f, spec = 0.25f)
                if (highDetail) {
                    localPart(host.cubeMesh, sx - 0.08f, 0.05f, legZ - 0.10f, 0.07f, 0.06f, 0.24f, 0.98f, 0.50f, 0.03f, yawExtra = 22f, spec = 0.25f)
                    localPart(host.cubeMesh, sx + 0.08f, 0.05f, legZ - 0.10f, 0.07f, 0.06f, 0.24f, 0.98f, 0.50f, 0.03f, yawExtra = -22f, spec = 0.25f)
                }
            }
        }

        // 2. Sculpted Plump Feathered Torso + Chest Puff + Neck Ruff + Layered 3-Plume Tail
        localPart(host.sphereMesh, 0f, 0.88f, 0.02f, 0.50f, 0.46f, 0.56f, whiteR, whiteG, whiteB, spec = 0.12f)
        if (highDetail) {
            localPart(host.sphereMesh, 0f, 0.96f, -0.26f, 0.36f, 0.32f, 0.32f, whiteR, whiteG, whiteB, spec = 0.12f)
            // Feathered neck ruff collar
            localPart(host.cylinderMesh, 0f, 1.24f, -0.08f, 0.66f, 0.15f, 0.66f, whiteR * 0.97f, whiteG * 0.97f, whiteB * 0.97f, spec = 0.10f)
        }
        // Fanned 3-feather tail (+Z back)
        localPart(host.coneMesh, 0f, 1.08f, 0.56f, 0.26f, 0.52f, 0.20f, whiteR * 0.96f, whiteG * 0.96f, whiteB * 0.96f, pitchExtra = 36f)
        if (highDetail) {
            localPart(host.coneMesh, -0.16f, 1.04f, 0.52f, 0.20f, 0.44f, 0.18f, whiteR * 0.93f, whiteG * 0.93f, whiteB * 0.93f, pitchExtra = 42f, rollExtra = 18f)
            localPart(host.coneMesh, 0.16f, 1.04f, 0.52f, 0.20f, 0.44f, 0.18f, whiteR * 0.93f, whiteG * 0.93f, whiteB * 0.93f, pitchExtra = 42f, rollExtra = -18f)
        }

        // Tactical Combat Vest, Chest Armor Pouches, Ammo Belt & Mini Backpack
        localPart(host.cylinderMesh, 0f, 0.84f, 0.02f, 0.96f, 0.38f, 1.02f, vestR, vestG, vestB, spec = 0.24f)
        if (highDetail) {
            localPart(host.cubeMesh, 0f, 0.72f, 0.02f, 1.02f, 0.12f, 1.06f, 0.18f, 0.20f, 0.24f, spec = 0.45f)
            // Twin armored chest mag pouches on front (-Z)
            localPart(host.cubeMesh, -0.20f, 0.84f, -0.50f, 0.17f, 0.20f, 0.10f, 0.20f, 0.23f, 0.27f, spec = 0.35f)
            localPart(host.cubeMesh, 0.20f, 0.84f, -0.50f, 0.17f, 0.20f, 0.10f, 0.20f, 0.23f, 0.27f, spec = 0.35f)
            // Tactical backpack on back (+Z)
            localPart(host.cubeMesh, 0f, 0.94f, 0.50f, 0.48f, 0.46f, 0.24f, 0.22f, 0.26f, 0.22f, spec = 0.18f)
            localPart(host.cubeMesh, 0f, 1.06f, 0.54f, 0.36f, 0.14f, 0.22f, vestR, vestG, vestB, spec = 0.28f)
        }

        // 3. Sculpted Rounded Head + Tactical Helmet Shell + 3-Lobe Comb + Cone Beak + Wattle
        localPart(host.sphereMesh, 0f, 1.54f, -0.12f, 0.40f, 0.38f, 0.40f, whiteR, whiteG, whiteB, spec = 0.14f)
        // Tactical helmet shells flanking the central comb slot (keeps comb iconic & visible!)
        if (highDetail) {
            localPart(host.sphereMesh, -0.16f, 1.72f, -0.06f, 0.25f, 0.20f, 0.36f, helmetR, helmetG, helmetB, rollExtra = 14f, spec = 0.45f)
            localPart(host.sphereMesh, 0.16f, 1.72f, -0.06f, 0.25f, 0.20f, 0.36f, helmetR, helmetG, helmetB, rollExtra = -14f, spec = 0.45f)
        }
        // 3-lobe rooster comb proudly rising through helmet crest
        localPart(host.sphereMesh, 0f, 1.96f, -0.22f, 0.10f, 0.17f, 0.13f, combR, combG, combB, spec = 0.32f)
        localPart(host.sphereMesh, 0f, 2.00f, -0.06f, 0.10f, 0.20f, 0.14f, combR * 0.96f, combG * 0.96f, combB * 0.96f, spec = 0.32f)
        if (highDetail) {
            localPart(host.sphereMesh, 0f, 1.92f, 0.09f, 0.09f, 0.15f, 0.12f, combR * 0.92f, combG * 0.92f, combB * 0.92f, spec = 0.32f)
        }

        // Sculpted 2-part orange cone beak pointing forward (-Z)
        localPart(host.coneMesh, 0f, 1.51f, -0.58f, 0.26f, 0.38f, 0.20f, 1.0f, 0.60f, 0.04f, pitchExtra = -86f, spec = 0.45f)
        if (highDetail) {
            localPart(host.coneMesh, 0f, 1.41f, -0.52f, 0.20f, 0.26f, 0.14f, 0.94f, 0.48f, 0.03f, pitchExtra = -102f, spec = 0.40f)
            localPart(host.sphereMesh, -0.07f, 1.30f, -0.44f, 0.07f, 0.11f, 0.07f, combR, combG, combB, spec = 0.30f)
            localPart(host.sphereMesh, 0.07f, 1.30f, -0.44f, 0.07f, 0.11f, 0.07f, combR, combG, combB, spec = 0.30f)
            localPart(host.sphereMesh, -0.17f, 1.60f, -0.44f, 0.09f, 0.09f, 0.06f, 0.99f, 0.99f, 1.0f, spec = 0.8f)
            localPart(host.sphereMesh, 0.17f, 1.60f, -0.44f, 0.09f, 0.09f, 0.06f, 0.99f, 0.99f, 1.0f, spec = 0.8f)
            localPart(host.cubeMesh, -0.17f, 1.61f, -0.50f, 0.24f, 0.11f, 0.05f, visorR, visorG, visorB, spec = 1.0f)
            localPart(host.cubeMesh, 0.17f, 1.61f, -0.50f, 0.24f, 0.11f, 0.05f, visorR, visorG, visorB, spec = 1.0f)
        }
        // Glossy tinted tactical visor & team headband
        localPart(host.cubeMesh, 0f, 1.61f, -0.48f, 0.68f, 0.15f, 0.07f, 0.08f, 0.12f, 0.18f, spec = 1.0f)
        localPart(host.cylinderMesh, 0f, 1.72f, -0.12f, 0.80f, 0.11f, 0.80f, vestR, vestG, vestB, spec = 0.25f)

        // 4. Sculpted Feathered Wings (animated flap)
        localPart(host.sphereMesh, -0.56f, 0.92f, 0.02f, 0.14f, 0.30f, 0.36f, whiteR * 0.95f, whiteG * 0.95f, whiteB * 0.95f, pitchExtra = stride * 32f, rollExtra = 14f + wingFlap)
        localPart(host.sphereMesh, 0.56f, 0.94f, -0.12f, 0.14f, 0.26f, 0.34f, whiteR * 0.95f, whiteG * 0.95f, whiteB * 0.95f, pitchExtra = -18f, rollExtra = -14f)

        // 5. Detailed 3D Weapon held in Right Wing
        if (weapon != null) {
            when (weapon) {
                WeaponType.PISTOL -> {
                    localPart(host.cubeMesh, 0.58f, 0.98f, -0.58f, 0.11f, 0.16f, 0.50f, 0.20f, 0.24f, 0.30f, spec = 0.75f)
                    localPart(host.cylinderMesh, 0.58f, 1.01f, -0.88f, 0.08f, 0.22f, 0.08f, 0.14f, 0.16f, 0.20f, pitchExtra = 90f, spec = 0.85f)
                    if (highDetail) {
                        localPart(host.cubeMesh, 0.58f, 0.83f, -0.40f, 0.09f, 0.22f, 0.14f, 0.12f, 0.14f, 0.16f, pitchExtra = 12f, spec = 0.3f)
                    }
                }
                WeaponType.RIFLE -> {
                    localPart(host.cubeMesh, 0.58f, 0.96f, -0.76f, 0.13f, 0.17f, 0.88f, 0.18f, 0.21f, 0.26f, spec = 0.65f)
                    localPart(host.cylinderMesh, 0.58f, 0.98f, -1.32f, 0.07f, 0.42f, 0.07f, 0.12f, 0.14f, 0.18f, pitchExtra = 90f, spec = 0.85f)
                    if (highDetail) {
                        localPart(host.cubeMesh, 0.58f, 0.94f, -0.28f, 0.12f, 0.17f, 0.34f, 0.58f, 0.35f, 0.15f, spec = 0.20f)
                        localPart(host.cubeMesh, 0.58f, 0.75f, -0.76f, 0.09f, 0.30f, 0.14f, 0.14f, 0.15f, 0.18f, pitchExtra = -12f, spec = 0.55f)
                        localPart(host.cubeMesh, 0.58f, 1.10f, -0.64f, 0.08f, 0.09f, 0.22f, 0.12f, 0.14f, 0.18f, spec = 0.75f)
                    }
                }
                WeaponType.RPG -> {
                    localPart(host.cylinderMesh, 0.60f, 1.06f, -0.66f, 0.22f, 1.48f, 0.22f, 0.26f, 0.38f, 0.22f, pitchExtra = 90f, spec = 0.45f)
                    if (highDetail) {
                        localPart(host.cylinderMesh, 0.60f, 1.06f, -0.55f, 0.26f, 0.55f, 0.26f, 0.56f, 0.34f, 0.15f, pitchExtra = 90f, spec = 0.20f)
                    }
                    localPart(host.coneMesh, 0.60f, 1.06f, -1.52f, 0.34f, 0.48f, 0.34f, 0.88f, 0.22f, 0.14f, pitchExtra = -90f, spec = 0.55f)
                }
            }
            if (muzzleFlash) {
                host.setUnlit(true)
                localPart(host.sphereMesh, 0.58f, 1.00f, -1.56f, 0.28f, 0.28f, 0.28f, 1f, 0.90f, 0.28f)
                host.setUnlit(false)
            }
        }

        // 6. Translucent Golden Shield Bubble during Spawn Protection
        if (shieldActive) {
            host.setUnlit(true)
            host.setDepthMask(false)
            localPart(host.sphereMesh, 0f, 1.15f, 0f, 1.25f, 1.35f, 1.25f, 1.0f, 0.84f, 0.22f, alpha = 0.22f)
            host.setDepthMask(true)
            host.setUnlit(false)
        }
    }

    /**
     * Renders living NPC Хряк-Бургер (`pig_burger_animated.glb`) with live Idle/Walk/Run/Jump animations,
     * contact shadow, happy petting tint, and safe procedural fallback.
     */
    fun drawPigBurger(
        pig: PigBurgerNpc,
        shadowY: Float = 0.03f,
        highDetail: Boolean = true
    ) {
        if (highDetail) {
            val heightAbove = (pig.y - (shadowY - 0.03f)).coerceAtLeast(0f)
            val shadowFade = (1f - (heightAbove * 0.08f).coerceIn(0f, 0.65f))
            host.drawShadowDisc(pig.x, shadowY, pig.z, 1.48f * shadowFade, 1.48f * shadowFade, 0.42f * shadowFade)
        }

        val pigGpu = host.glbManager.getOrLoadGpu("pig_burger_animated.glb")
        if (pigGpu != null) {
            val animName = when {
                !pig.grounded || pig.state == PigBurgerState.PANIC_JUMP || pig.state == PigBurgerState.HAPPY_HOP -> "Jump"
                pig.state == PigBurgerState.FLEE_RUN -> "Run"
                pig.state == PigBurgerState.WALK -> "Walk"
                else -> "Idle"
            }
            Matrix.setIdentityM(glbRootMatrix, 0)
            Matrix.translateM(glbRootMatrix, 0, pig.x, pig.y, pig.z)
            Matrix.rotateM(glbRootMatrix, 0, -pig.yaw + 180f, 0f, 1f, 0f)
            Matrix.scaleM(glbRootMatrix, 0, 1.28f, 1.28f, 1.28f)

            val tintR = when {
                pig.hitFlash > 0.05f -> 1.0f
                pig.happyTimer > 0f -> 1.08f
                else -> 1.0f
            }
            val tintG = when {
                pig.hitFlash > 0.05f -> 1f - pig.hitFlash * 0.65f
                pig.happyTimer > 0f -> 0.92f
                else -> 1.0f
            }
            val tintB = when {
                pig.hitFlash > 0.05f -> 1f - pig.hitFlash * 0.65f
                pig.happyTimer > 0f -> 1.04f
                else -> 1.0f
            }

            host.glbManager.drawModel(
                renderer = host,
                model = pigGpu,
                rootWorldMatrix = glbRootMatrix,
                animName = animName,
                animTime = pig.animTime,
                tintR = tintR,
                tintG = tintG,
                tintB = tintB
            )
            return
        }

        // Cute pink piggy-burger procedural fallback while streaming
        host.drawMesh(host.sphereMesh, pig.x, pig.y + 0.72f, pig.z, pig.yaw, 0.85f, 0.65f, 0.85f, 0.98f, 0.66f, 0.72f, 1f, spec = 0.25f)
        host.drawMesh(host.cylinderMesh, pig.x, pig.y + 1.15f, pig.z, pig.yaw, 1.15f, 0.30f, 1.15f, 0.92f, 0.66f, 0.24f, 1f, spec = 0.25f)
    }

    /**
     * Renders all 9 drivable vehicles:
     * - 7 custom .glb car models with live wheel spin, front steering, flashing sirens & 360° loop pitch!
     * - Procedural Battle Tank & Dune Buggy + automatic fallback if any .glb is still loading.
     */
    fun drawVehicle(
        v: VehicleEntity,
        worldTime: Float,
        shadowY: Float = 0.03f,
        highDetail: Boolean = true,
        drawShadow: Boolean = true
    ) {
        if (drawShadow && v.loopPropId == null) {
            val heightAbove = (v.y - (shadowY - 0.03f)).coerceAtLeast(0f)
            val shadowFade = (1f - (heightAbove * 0.06f).coerceIn(0f, 0.6f))
            host.drawShadowDisc(
                v.x, shadowY, v.z,
                v.type.halfW * 2.55f * shadowFade,
                v.type.halfL * 2.35f * shadowFade,
                0.46f * shadowFade,
                yawDeg = v.yaw
            )
        }

        val suspY = v.suspensionOffset
        val pitch = v.pitchAngle
        val roll = v.rollAngle

        // Primary .glb vehicle path for all 7 car_*.glb models
        val carGlbFile = v.type.glbFile
        if (carGlbFile != null) {
            val carGpu = host.glbManager.getOrLoadGpu(carGlbFile, syncIfMissing = v.id.startsWith("car_user_"))
            if (carGpu != null) {
                Matrix.setIdentityM(glbRootMatrix, 0)
                Matrix.translateM(glbRootMatrix, 0, v.x, v.y + suspY, v.z)
                Matrix.rotateM(glbRootMatrix, 0, -v.yaw + 180f, 0f, 1f, 0f)
                // Because .glb faces +Z (rotated 180 deg from engine -Z), negate pitch & roll for accurate squat/loop/lean
                if (pitch != 0f) Matrix.rotateM(glbRootMatrix, 0, -pitch, 1f, 0f, 0f)
                if (roll != 0f) Matrix.rotateM(glbRootMatrix, 0, -roll, 0f, 0f, 1f)
                val sc = v.type.modelScale
                Matrix.scaleM(glbRootMatrix, 0, sc, sc, sc)

                nodeOverrides.clear()
                // Front wheel steering (wheel_FL, wheel_FR)
                rotBufA[0] = 0f; rotBufA[1] = -v.steerAngle; rotBufA[2] = 0f
                nodeOverrides.extraRotDeg["wheel_FL"] = rotBufA
                nodeOverrides.extraRotDeg["wheel_FR"] = rotBufA
                // All 4 wheel spin nodes (wheel_*_spin)
                rotBufB[0] = v.wheelSpin; rotBufB[1] = 0f; rotBufB[2] = 0f
                nodeOverrides.extraRotDeg["wheel_FL_spin"] = rotBufB
                nodeOverrides.extraRotDeg["wheel_FR_spin"] = rotBufB
                nodeOverrides.extraRotDeg["wheel_RL_spin"] = rotBufB
                nodeOverrides.extraRotDeg["wheel_RR_spin"] = rotBufB

                // Flashing emergency sirens on police cruiser & firetruck
                if (v.type.hasSirens) {
                    val leftOn = ((worldTime * 7.2f).toInt() and 1) == 0
                    if (v.type == VehicleType.POLICE_SUV) {
                        emBufA[0] = if (leftOn) 1.3f else 0.1f; emBufA[1] = 0.08f; emBufA[2] = if (leftOn) 0.1f else 0.2f
                        emBufB[0] = 0.08f; emBufB[1] = if (!leftOn) 0.55f else 0.1f; emBufB[2] = if (!leftOn) 1.35f else 0.2f
                    } else {
                        emBufA[0] = if (leftOn) 1.3f else 0.2f; emBufA[1] = 0.12f; emBufA[2] = 0.08f
                        emBufB[0] = if (!leftOn) 1.2f else 0.2f; emBufB[1] = if (!leftOn) 0.85f else 0.15f; emBufB[2] = 0.12f
                    }
                    nodeOverrides.emissiveBoost["siren_L"] = emBufA
                    nodeOverrides.emissiveBoost["siren_R"] = emBufB
                }

                host.glbManager.drawModel(
                    renderer = host,
                    model = carGpu,
                    rootWorldMatrix = glbRootMatrix,
                    overrides = nodeOverrides
                )

                // Twin glowing nitro flames out the rear exhaust when turbo is active!
                if (v.nitroTimer > 0.05f) {
                    val fLen = 1.05f + abs(sin(worldTime * 36f)) * 0.60f
                    val rearZ = v.type.halfL + 0.15f + fLen * 0.35f
                    host.setUnlit(true)
                    for (side in legSides) {
                        Matrix.setIdentityM(host.modelMatrix, 0)
                        Matrix.translateM(host.modelMatrix, 0, v.x, v.y + suspY, v.z)
                        Matrix.rotateM(host.modelMatrix, 0, -v.yaw, 0f, 1f, 0f)
                        if (pitch != 0f) Matrix.rotateM(host.modelMatrix, 0, pitch, 1f, 0f, 0f)
                        if (roll != 0f) Matrix.rotateM(host.modelMatrix, 0, roll, 0f, 0f, 1f)
                        Matrix.translateM(host.modelMatrix, 0, side * 0.55f, 0.45f, rearZ)
                        Matrix.rotateM(host.modelMatrix, 0, 90f, 1f, 0f, 0f)
                        Matrix.scaleM(host.modelMatrix, 0, 0.26f, fLen, 0.26f)
                        host.drawCurrentModel(host.coneMesh, 0.22f, 0.88f, 1.0f, 1f, 0f)
                    }
                    host.setUnlit(false)
                }
                return
            }
        }

        // Chassis parts inherit live suspension bounce + acceleration squat/dive pitch + cornering roll!
        fun carPart(
            mesh: GlMesh,
            lx: Float, ly: Float, lz: Float,
            sx: Float, sy: Float, sz: Float,
            r: Float, g: Float, b: Float,
            rotY: Float = 0f,
            rotX: Float = 0f,
            rotZ: Float = 0f,
            spec: Float = 0.65f,
            grounded: Boolean = false,
            alpha: Float = 1f
        ) {
            Matrix.setIdentityM(host.modelMatrix, 0)
            Matrix.translateM(host.modelMatrix, 0, v.x, v.y + (if (grounded) 0f else suspY), v.z)
            Matrix.rotateM(host.modelMatrix, 0, -v.yaw, 0f, 1f, 0f)
            if (!grounded) {
                if (pitch != 0f) Matrix.rotateM(host.modelMatrix, 0, pitch, 1f, 0f, 0f)
                if (roll != 0f) Matrix.rotateM(host.modelMatrix, 0, roll, 0f, 0f, 1f)
            }
            Matrix.translateM(host.modelMatrix, 0, lx, ly, lz)
            if (rotY != 0f) Matrix.rotateM(host.modelMatrix, 0, rotY, 0f, 1f, 0f)
            if (rotX != 0f) Matrix.rotateM(host.modelMatrix, 0, rotX, 1f, 0f, 0f)
            if (rotZ != 0f) Matrix.rotateM(host.modelMatrix, 0, rotZ, 0f, 0f, 1f)
            Matrix.scaleM(host.modelMatrix, 0, sx, sy, sz)
            host.drawCurrentModel(mesh, r, g, b, alpha, spec)
        }

        fun drawWheelSet(trackX: Float, wheelZ: Float, wheelDia: Float, wheelWidth: Float, wheelY: Float) {
            val spin = -v.wheelSpin
            for (i in 0 until 4) {
                val wx = wheelXSigns[i] * trackX
                val wz = wheelZSigns[i] * wheelZ
                val steer = if (wheelIsFront[i]) -v.steerAngle else 0f
                carPart(host.cylinderMesh, wx, wheelY, wz, wheelDia, wheelWidth, wheelDia, 0.13f, 0.14f, 0.16f, rotY = steer, rotX = spin, rotZ = 90f, spec = 0.22f, grounded = true)
                if (highDetail) {
                    carPart(host.cylinderMesh, wx + (if (wx < 0) -0.05f else 0.05f), wheelY, wz, wheelDia * 0.54f, wheelWidth * 0.92f, wheelDia * 0.54f, 0.84f, 0.88f, 0.92f, rotY = steer, rotX = spin, rotZ = 90f, spec = 0.95f, grounded = true)
                }
            }
        }

        when (v.type) {
            VehicleType.SPORTS_CAR -> {
                // Sleek low aerodynamic race coupe with racing stripe, hood scoop, mirrors, exhausts & GT spoiler
                carPart(host.cubeMesh, 0f, 0.56f, 0f, 2.34f, 0.54f, 4.75f, v.colorR, v.colorG, v.colorB, spec = 0.85f)
                carPart(host.cubeMesh, 0f, 0.84f, -0.2f, 0.48f, 0.04f, 4.2f, 0.97f, 0.97f, 0.99f, spec = 0.80f)
                carPart(host.cubeMesh, 0f, 1.08f, 0.22f, 1.88f, 0.52f, 2.05f, v.colorR * 0.88f, v.colorG * 0.88f, v.colorB * 0.88f, spec = 0.85f)
                carPart(host.cubeMesh, 0f, 1.06f, -0.78f, 1.72f, 0.46f, 0.14f, 0.24f, 0.68f, 0.94f, rotX = -32f, spec = 1.0f)
                carPart(host.cubeMesh, 0f, 1.06f, 1.24f, 1.72f, 0.42f, 0.12f, 0.20f, 0.52f, 0.78f, rotX = 28f, spec = 1.0f)
                // GT Wing Spoiler
                carPart(host.cubeMesh, 0f, 1.38f, 2.12f, 2.38f, 0.10f, 0.44f, 0.16f, 0.18f, 0.22f, rotX = 8f, spec = 0.85f)
                if (highDetail) {
                    // Front carbon splitter, recessed grille, flared wheel arches, spoiler struts, aero mirrors, hood scoop & exhausts
                    carPart(host.cubeMesh, 0f, 0.30f, -2.42f, 2.40f, 0.14f, 0.42f, 0.14f, 0.15f, 0.18f, spec = 0.55f)
                    carPart(host.cubeMesh, 0f, 0.48f, -2.40f, 1.24f, 0.20f, 0.10f, 0.12f, 0.14f, 0.18f, spec = 0.75f)
                    carPart(host.cubeMesh, 0f, 0.34f, 2.40f, 2.32f, 0.18f, 0.18f, 0.14f, 0.16f, 0.20f, spec = 0.55f)
                    carPart(host.cubeMesh, -1.20f, 0.64f, -1.48f, 0.18f, 0.36f, 1.02f, v.colorR * 0.84f, v.colorG * 0.84f, v.colorB * 0.84f, spec = 0.85f)
                    carPart(host.cubeMesh, 1.20f, 0.64f, -1.48f, 0.18f, 0.36f, 1.02f, v.colorR * 0.84f, v.colorG * 0.84f, v.colorB * 0.84f, spec = 0.85f)
                    carPart(host.cubeMesh, -1.20f, 0.64f, 1.48f, 0.18f, 0.36f, 1.02f, v.colorR * 0.84f, v.colorG * 0.84f, v.colorB * 0.84f, spec = 0.85f)
                    carPart(host.cubeMesh, 1.20f, 0.64f, 1.48f, 0.18f, 0.36f, 1.02f, v.colorR * 0.84f, v.colorG * 0.84f, v.colorB * 0.84f, spec = 0.85f)
                    carPart(host.cubeMesh, -0.78f, 1.08f, 2.05f, 0.12f, 0.58f, 0.24f, 0.16f, 0.17f, 0.20f, spec = 0.65f)
                    carPart(host.cubeMesh, 0.78f, 1.08f, 2.05f, 0.12f, 0.58f, 0.24f, 0.16f, 0.17f, 0.20f, spec = 0.65f)
                    carPart(host.cubeMesh, 0f, 0.88f, -1.48f, 0.58f, 0.12f, 0.54f, 0.14f, 0.16f, 0.20f, spec = 0.75f)
                    carPart(host.cubeMesh, -1.24f, 0.94f, -0.62f, 0.26f, 0.12f, 0.18f, v.colorR, v.colorG, v.colorB, spec = 0.85f)
                    carPart(host.cubeMesh, 1.24f, 0.94f, -0.62f, 0.26f, 0.12f, 0.18f, v.colorR, v.colorG, v.colorB, spec = 0.85f)
                    carPart(host.cylinderMesh, -0.58f, 0.36f, 2.46f, 0.18f, 0.34f, 0.18f, 0.82f, 0.86f, 0.90f, rotX = 90f, spec = 1.0f)
                    carPart(host.cylinderMesh, 0.58f, 0.36f, 2.46f, 0.18f, 0.34f, 0.18f, 0.82f, 0.86f, 0.90f, rotX = 90f, spec = 1.0f)
                }
                host.setUnlit(true)
                carPart(host.cubeMesh, -0.80f, 0.62f, -2.39f, 0.48f, 0.16f, 0.08f, 1f, 0.97f, 0.76f)
                carPart(host.cubeMesh, 0.80f, 0.62f, -2.39f, 0.48f, 0.16f, 0.08f, 1f, 0.97f, 0.76f)
                carPart(host.cubeMesh, -0.78f, 0.64f, 2.39f, 0.54f, 0.16f, 0.08f, 1f, 0.14f, 0.12f)
                carPart(host.cubeMesh, 0.78f, 0.64f, 2.39f, 0.54f, 0.16f, 0.08f, 1f, 0.14f, 0.12f)
                if (v.nitroTimer > 0.05f) {
                    val fLen = 0.95f + abs(sin(worldTime * 34f)) * 0.55f
                    carPart(host.coneMesh, -0.58f, 0.36f, 2.66f + fLen * 0.4f, 0.24f, fLen, 0.24f, 0.24f, 0.82f, 1.0f, rotX = 90f)
                    carPart(host.coneMesh, 0.58f, 0.36f, 2.66f + fLen * 0.4f, 0.24f, fLen, 0.24f, 0.24f, 0.82f, 1.0f, rotX = 90f)
                }
                host.setUnlit(false)
                drawWheelSet(trackX = 1.24f, wheelZ = 1.48f, wheelDia = 0.78f, wheelWidth = 0.44f, wheelY = 0.39f)
            }

            VehicleType.MONSTER_TRUCK -> {
                // High-lifted chassis + red shocks + twin chrome exhaust stacks + roll-cage + monster wheels
                carPart(host.cubeMesh, 0f, 0.72f, 0f, 1.55f, 0.32f, 3.30f, 0.22f, 0.24f, 0.28f, spec = 0.55f, grounded = true)
                if (highDetail) {
                    carPart(host.cylinderMesh, -0.95f, 0.95f, -1.45f, 0.16f, 0.72f, 0.16f, 0.95f, 0.22f, 0.14f, spec = 0.70f, grounded = true)
                    carPart(host.cylinderMesh, 0.95f, 0.95f, -1.45f, 0.16f, 0.72f, 0.16f, 0.95f, 0.22f, 0.14f, spec = 0.70f, grounded = true)
                    carPart(host.cylinderMesh, -0.95f, 0.95f, 1.45f, 0.16f, 0.72f, 0.16f, 0.95f, 0.22f, 0.14f, spec = 0.70f, grounded = true)
                    carPart(host.cylinderMesh, 0.95f, 0.95f, 1.45f, 0.16f, 0.72f, 0.16f, 0.95f, 0.22f, 0.14f, spec = 0.70f, grounded = true)
                }
                carPart(host.cubeMesh, 0f, 1.38f, 0f, 2.48f, 0.78f, 4.85f, v.colorR, v.colorG, v.colorB, spec = 0.75f)
                carPart(host.cubeMesh, 0f, 2.08f, -0.15f, 2.15f, 0.70f, 2.05f, v.colorR * 0.9f, v.colorG * 0.9f, v.colorB * 0.9f, spec = 0.75f)
                carPart(host.cubeMesh, 0f, 2.06f, -1.20f, 1.88f, 0.54f, 0.12f, 0.28f, 0.74f, 0.94f, spec = 1.0f)
                carPart(host.cubeMesh, 0f, 1.35f, -2.54f, 2.30f, 0.55f, 0.22f, 0.18f, 0.20f, 0.24f, spec = 0.80f)
                if (highDetail) {
                    // Front radiator grille, rollbar, angled rear roll-cage struts & twin chrome exhaust stacks!
                    carPart(host.cubeMesh, 0f, 1.46f, -2.44f, 1.24f, 0.34f, 0.12f, 0.78f, 0.82f, 0.88f, spec = 0.95f)
                    carPart(host.cubeMesh, 0f, 2.52f, 0.65f, 1.90f, 0.14f, 0.14f, 0.16f, 0.18f, 0.22f, spec = 0.80f)
                    carPart(host.cylinderMesh, -0.94f, 2.16f, 1.40f, 0.14f, 1.32f, 0.14f, 0.18f, 0.20f, 0.24f, rotX = 36f, spec = 0.75f)
                    carPart(host.cylinderMesh, 0.94f, 2.16f, 1.40f, 0.14f, 1.32f, 0.14f, 0.18f, 0.20f, 0.24f, rotX = 36f, spec = 0.75f)
                    carPart(host.cylinderMesh, -1.04f, 2.24f, 0.98f, 0.16f, 1.28f, 0.16f, 0.84f, 0.88f, 0.92f, spec = 1.0f)
                    carPart(host.cylinderMesh, 1.04f, 2.24f, 0.98f, 0.16f, 1.28f, 0.16f, 0.84f, 0.88f, 0.92f, spec = 1.0f)
                }
                host.setUnlit(true)
                if (highDetail) {
                    for (i in monsterLightX.indices) {
                        carPart(host.sphereMesh, monsterLightX[i], 2.66f, 0.65f, 0.14f, 0.14f, 0.14f, 1f, 0.96f, 0.72f)
                    }
                }
                carPart(host.cubeMesh, -0.82f, 1.48f, -2.45f, 0.44f, 0.24f, 0.10f, 1f, 0.96f, 0.72f)
                carPart(host.cubeMesh, 0.82f, 1.48f, -2.45f, 0.44f, 0.24f, 0.10f, 1f, 0.96f, 0.72f)
                carPart(host.cubeMesh, -0.82f, 1.48f, 2.45f, 0.44f, 0.24f, 0.10f, 1f, 0.16f, 0.12f)
                carPart(host.cubeMesh, 0.82f, 1.48f, 2.45f, 0.44f, 0.24f, 0.10f, 1f, 0.16f, 0.12f)
                host.setUnlit(false)
                drawWheelSet(trackX = 1.48f, wheelZ = 1.54f, wheelDia = 1.36f, wheelWidth = 0.74f, wheelY = 0.68f)
            }

            VehicleType.PICKUP_4X4 -> {
                // Rugged off-road yellow pickup with cargo bed, spare wheel, rear rollbar & fog lights
                carPart(host.cubeMesh, 0f, 0.76f, 0f, 2.38f, 0.76f, 4.78f, v.colorR, v.colorG, v.colorB, spec = 0.70f)
                carPart(host.cubeMesh, -1.08f, 1.24f, 1.32f, 0.18f, 0.34f, 1.98f, v.colorR * 0.92f, v.colorG * 0.92f, v.colorB * 0.92f, spec = 0.65f)
                carPart(host.cubeMesh, 1.08f, 1.24f, 1.32f, 0.18f, 0.34f, 1.98f, v.colorR * 0.92f, v.colorG * 0.92f, v.colorB * 0.92f, spec = 0.65f)
                carPart(host.cubeMesh, 0f, 1.24f, 2.28f, 2.26f, 0.34f, 0.18f, v.colorR * 0.92f, v.colorG * 0.92f, v.colorB * 0.92f, spec = 0.65f)
                if (highDetail) {
                    // Chrome radiator grille, flared wheel-arch fenders, spare wheel & off-road bed rollbar
                    carPart(host.cubeMesh, 0f, 0.82f, -2.41f, 1.20f, 0.30f, 0.10f, 0.80f, 0.84f, 0.88f, spec = 0.95f)
                    carPart(host.cubeMesh, -1.22f, 0.76f, -1.46f, 0.18f, 0.42f, 1.18f, 0.14f, 0.16f, 0.20f, spec = 0.45f)
                    carPart(host.cubeMesh, 1.22f, 0.76f, -1.46f, 0.18f, 0.42f, 1.18f, 0.14f, 0.16f, 0.20f, spec = 0.45f)
                    carPart(host.cubeMesh, -1.22f, 0.76f, 1.46f, 0.18f, 0.42f, 1.18f, 0.14f, 0.16f, 0.20f, spec = 0.45f)
                    carPart(host.cubeMesh, 1.22f, 0.76f, 1.46f, 0.18f, 0.42f, 1.18f, 0.14f, 0.16f, 0.20f, spec = 0.45f)
                    carPart(host.cylinderMesh, 0f, 1.26f, 1.45f, 0.90f, 0.30f, 0.90f, 0.15f, 0.16f, 0.18f, spec = 0.25f)
                    carPart(host.cylinderMesh, -0.92f, 1.66f, 0.82f, 0.12f, 0.88f, 0.12f, 0.18f, 0.20f, 0.24f, spec = 0.75f)
                    carPart(host.cylinderMesh, 0.92f, 1.66f, 0.82f, 0.12f, 0.88f, 0.12f, 0.18f, 0.20f, 0.24f, spec = 0.75f)
                    carPart(host.cubeMesh, 0f, 2.08f, 0.82f, 1.96f, 0.12f, 0.12f, 0.18f, 0.20f, 0.24f, spec = 0.75f)
                }
                carPart(host.cubeMesh, 0f, 1.88f, -0.25f, 2.10f, 0.16f, 1.80f, v.colorR * 0.9f, v.colorG * 0.9f, v.colorB * 0.9f, spec = 0.70f)
                carPart(host.cubeMesh, -0.94f, 1.48f, -1.05f, 0.14f, 0.78f, 0.14f, 0.18f, 0.20f, 0.24f, spec = 0.55f)
                carPart(host.cubeMesh, 0.94f, 1.48f, -1.05f, 0.14f, 0.78f, 0.14f, 0.18f, 0.20f, 0.24f, spec = 0.55f)
                carPart(host.cubeMesh, -0.94f, 1.48f, 0.55f, 0.14f, 0.78f, 0.14f, 0.18f, 0.20f, 0.24f, spec = 0.55f)
                carPart(host.cubeMesh, 0.94f, 1.48f, 0.55f, 0.14f, 0.78f, 0.14f, 0.18f, 0.20f, 0.24f, spec = 0.55f)
                carPart(host.cubeMesh, 0f, 1.48f, -1.08f, 1.78f, 0.64f, 0.10f, 0.34f, 0.72f, 0.94f, spec = 1.0f)
                carPart(host.cubeMesh, 0f, 0.54f, -2.44f, 2.46f, 0.34f, 0.34f, 0.18f, 0.20f, 0.24f, spec = 0.65f)
                carPart(host.cubeMesh, 0f, 0.54f, 2.44f, 2.46f, 0.34f, 0.34f, 0.18f, 0.20f, 0.24f, spec = 0.65f)
                host.setUnlit(true)
                carPart(host.cubeMesh, -0.80f, 0.84f, -2.41f, 0.42f, 0.24f, 0.10f, 1f, 0.96f, 0.72f)
                carPart(host.cubeMesh, 0.80f, 0.84f, -2.41f, 0.42f, 0.24f, 0.10f, 1f, 0.96f, 0.72f)
                if (highDetail) {
                    // Amber off-road fog lamps on bumper
                    carPart(host.sphereMesh, -0.44f, 0.66f, -2.62f, 0.16f, 0.16f, 0.10f, 1f, 0.74f, 0.18f)
                    carPart(host.sphereMesh, 0.44f, 0.66f, -2.62f, 0.16f, 0.16f, 0.10f, 1f, 0.74f, 0.18f)
                }
                carPart(host.cubeMesh, -0.80f, 0.84f, 2.41f, 0.44f, 0.22f, 0.10f, 0.98f, 0.15f, 0.12f)
                carPart(host.cubeMesh, 0.80f, 0.84f, 2.41f, 0.44f, 0.22f, 0.10f, 0.98f, 0.15f, 0.12f)
                host.setUnlit(false)
                drawWheelSet(trackX = 1.28f, wheelZ = 1.46f, wheelDia = 0.96f, wheelWidth = 0.48f, wheelY = 0.48f)
            }

            VehicleType.POLICE_SUV -> {
                // Tactical Interceptor SUV with flashing Red/Blue siren bar, spotlight & whip antenna
                carPart(host.cubeMesh, 0f, 0.74f, 0f, 2.38f, 0.74f, 4.80f, 0.12f, 0.14f, 0.20f, spec = 0.85f)
                carPart(host.cubeMesh, 0f, 0.76f, 0.15f, 2.42f, 0.62f, 2.15f, 0.96f, 0.97f, 0.99f, spec = 0.80f)
                carPart(host.cubeMesh, 0f, 1.44f, 0.28f, 2.16f, 0.74f, 2.75f, 0.14f, 0.16f, 0.22f, spec = 0.85f)
                carPart(host.cubeMesh, 0f, 1.83f, 0.28f, 2.12f, 0.10f, 2.65f, 0.96f, 0.97f, 0.99f, spec = 0.85f)
                carPart(host.cubeMesh, 0f, 1.46f, -1.12f, 1.86f, 0.56f, 0.12f, 0.18f, 0.42f, 0.65f, spec = 1.0f)
                carPart(host.cubeMesh, 0f, 1.46f, 0.30f, 2.20f, 0.46f, 2.25f, 0.14f, 0.26f, 0.40f, spec = 1.0f)
                carPart(host.cubeMesh, 0f, 0.68f, -2.52f, 2.22f, 0.56f, 0.26f, 0.18f, 0.20f, 0.24f, spec = 0.80f)
                if (highDetail) {
                    // Flared black wheel-arch fenders, chrome radiator grille, push-bar uprights, side mirrors, spotlight & whip antenna
                    carPart(host.cubeMesh, 0f, 0.80f, -2.44f, 1.18f, 0.32f, 0.10f, 0.74f, 0.78f, 0.84f, spec = 0.95f)
                    carPart(host.cubeMesh, -0.52f, 0.82f, -2.62f, 0.14f, 0.68f, 0.16f, 0.16f, 0.18f, 0.22f, spec = 0.85f)
                    carPart(host.cubeMesh, 0.52f, 0.82f, -2.62f, 0.14f, 0.68f, 0.16f, 0.16f, 0.18f, 0.22f, spec = 0.85f)
                    carPart(host.cubeMesh, 0f, 0.44f, 2.46f, 2.42f, 0.26f, 0.22f, 0.16f, 0.18f, 0.22f, spec = 0.65f)
                    carPart(host.cubeMesh, -1.24f, 0.74f, -1.48f, 0.18f, 0.40f, 1.16f, 0.10f, 0.12f, 0.16f, spec = 0.50f)
                    carPart(host.cubeMesh, 1.24f, 0.74f, -1.48f, 0.18f, 0.40f, 1.16f, 0.10f, 0.12f, 0.16f, spec = 0.50f)
                    carPart(host.cubeMesh, -1.24f, 0.74f, 1.48f, 0.18f, 0.40f, 1.16f, 0.10f, 0.12f, 0.16f, spec = 0.50f)
                    carPart(host.cubeMesh, 1.24f, 0.74f, 1.48f, 0.18f, 0.40f, 1.16f, 0.10f, 0.12f, 0.16f, spec = 0.50f)
                    carPart(host.cubeMesh, -1.26f, 1.22f, -0.92f, 0.24f, 0.14f, 0.18f, 0.12f, 0.14f, 0.20f, spec = 0.80f)
                    carPart(host.cubeMesh, 1.26f, 1.22f, -0.92f, 0.24f, 0.14f, 0.18f, 0.12f, 0.14f, 0.20f, spec = 0.80f)
                    carPart(host.cylinderMesh, -1.24f, 1.28f, -1.04f, 0.20f, 0.22f, 0.20f, 0.16f, 0.18f, 0.22f, rotX = 90f, spec = 0.9f)
                    carPart(host.cylinderMesh, 0.82f, 2.38f, 1.44f, 0.04f, 1.15f, 0.04f, 0.22f, 0.24f, 0.28f, spec = 0.7f)
                }
                val redBright = ((worldTime * 6.5f).toInt() and 1) == 0
                host.setUnlit(true)
                carPart(host.cubeMesh, -0.42f, 1.98f, -0.25f, 0.72f, 0.20f, 0.30f, if (redBright) 1.0f else 0.42f, 0.08f, 0.10f)
                carPart(host.cubeMesh, 0.42f, 1.98f, -0.25f, 0.72f, 0.20f, 0.30f, 0.10f, if (!redBright) 0.58f else 0.20f, if (!redBright) 1.0f else 0.45f)
                if (highDetail) {
                    host.setDepthMask(false)
                    if (redBright) {
                        carPart(host.sphereMesh, -0.44f, 2.02f, -0.25f, 0.55f, 0.38f, 0.46f, 1.0f, 0.15f, 0.15f, alpha = 0.22f)
                    } else {
                        carPart(host.sphereMesh, 0.44f, 2.02f, -0.25f, 0.55f, 0.38f, 0.46f, 0.15f, 0.55f, 1.0f, alpha = 0.22f)
                    }
                    host.setDepthMask(true)
                }
                carPart(host.cubeMesh, -0.80f, 0.82f, -2.42f, 0.42f, 0.22f, 0.10f, 1f, 0.97f, 0.82f)
                carPart(host.cubeMesh, 0.80f, 0.82f, -2.42f, 0.42f, 0.22f, 0.10f, 1f, 0.97f, 0.82f)
                carPart(host.cubeMesh, -0.80f, 0.82f, 2.42f, 0.46f, 0.22f, 0.10f, 0.98f, 0.15f, 0.12f)
                carPart(host.cubeMesh, 0.80f, 0.82f, 2.42f, 0.46f, 0.22f, 0.10f, 0.98f, 0.15f, 0.12f)
                host.setUnlit(false)
                drawWheelSet(trackX = 1.28f, wheelZ = 1.48f, wheelDia = 0.94f, wheelWidth = 0.46f, wheelY = 0.47f)
            }

            VehicleType.BATTLE_TANK -> {
                // Heavy armored Battle Tank with reactive side skirts, rear fuel drums, turret MG & cannon flash!
                carPart(host.cubeMesh, -1.32f, 0.56f, 0f, 0.74f, 0.96f, 5.20f, 0.16f, 0.18f, 0.17f, spec = 0.35f, grounded = true)
                carPart(host.cubeMesh, 1.32f, 0.56f, 0f, 0.74f, 0.96f, 5.20f, 0.16f, 0.18f, 0.17f, spec = 0.35f, grounded = true)
                val step = if (highDetail) 1 else 2
                var wi = 0
                while (wi < tankRoadWheelZ.size) {
                    val tz = tankRoadWheelZ[wi]
                    val spin = -v.wheelSpin
                    carPart(host.cylinderMesh, -1.42f, 0.44f, tz, 0.76f, 0.64f, 0.76f, 0.28f, 0.32f, 0.26f, rotX = spin, rotZ = 90f, spec = 0.45f, grounded = true)
                    carPart(host.cylinderMesh, 1.42f, 0.44f, tz, 0.76f, 0.64f, 0.76f, 0.28f, 0.32f, 0.26f, rotX = spin, rotZ = 90f, spec = 0.45f, grounded = true)
                    wi += step
                }
                // Sloped Main Armored Hull + Reactive Side Skirts + Rear Fuel Drums
                carPart(host.cubeMesh, 0f, 0.98f, 0f, 2.38f, 0.86f, 4.90f, v.colorR, v.colorG, v.colorB, spec = 0.40f)
                carPart(host.cubeMesh, 0f, 1.06f, -2.42f, 2.24f, 0.56f, 0.72f, v.colorR * 0.92f, v.colorG * 0.92f, v.colorB * 0.92f, rotX = 28f, spec = 0.40f)
                if (highDetail) {
                    carPart(host.cubeMesh, -1.58f, 0.86f, 0f, 0.16f, 0.52f, 4.88f, v.colorR * 0.84f, v.colorG * 0.84f, v.colorB * 0.84f, spec = 0.45f)
                    carPart(host.cubeMesh, 1.58f, 0.86f, 0f, 0.16f, 0.52f, 4.88f, v.colorR * 0.84f, v.colorG * 0.84f, v.colorB * 0.84f, spec = 0.45f)
                    carPart(host.cylinderMesh, -0.64f, 1.18f, 2.54f, 0.48f, 0.96f, 0.48f, 0.24f, 0.30f, 0.20f, rotZ = 90f, spec = 0.45f)
                    carPart(host.cylinderMesh, 0.64f, 1.18f, 2.54f, 0.48f, 0.96f, 0.48f, 0.24f, 0.30f, 0.20f, rotZ = 90f, spec = 0.45f)
                }
                // Rotating Tank Turret + Cupola + Commander MG + Long Cannon Barrel
                val tYaw = -v.turretYaw
                val tRad = v.turretYaw * PI.toFloat() / 180f
                val sinT = sin(tRad)
                val cosT = cos(tRad)
                carPart(host.cylinderMesh, 0f, 1.72f, -0.10f, 2.15f, 0.72f, 2.35f, v.colorR * 1.08f, v.colorG * 1.08f, v.colorB * 1.08f, rotY = tYaw, spec = 0.45f)
                carPart(host.cylinderMesh, 0.36f, 2.18f, 0.15f, 0.72f, 0.24f, 0.72f, 0.22f, 0.28f, 0.18f, rotY = tYaw, spec = 0.50f)
                if (highDetail) {
                    val mgX = 0.36f + sinT * 0.75f
                    val mgZ = 0.15f - cosT * 0.75f
                    carPart(host.cylinderMesh, mgX, 2.34f, mgZ, 0.09f, 1.05f, 0.09f, 0.16f, 0.18f, 0.20f, rotY = tYaw, rotX = 90f, spec = 0.85f)
                }
                val barrelX = sinT * 2.15f
                val barrelZ = -0.10f - cosT * 2.15f
                carPart(host.cylinderMesh, barrelX, 1.76f, barrelZ, 0.24f, 3.20f, 0.24f, 0.22f, 0.28f, 0.18f, rotY = tYaw, rotX = 90f, spec = 0.70f)
                val muzzleX = sinT * 3.72f
                val muzzleZ = -0.10f - cosT * 3.72f
                carPart(host.cylinderMesh, muzzleX, 1.76f, muzzleZ, 0.36f, 0.42f, 0.36f, 0.16f, 0.18f, 0.16f, rotY = tYaw, rotX = 90f, spec = 0.75f)
                host.setUnlit(true)
                carPart(host.sphereMesh, -0.82f, 1.24f, -2.32f, 0.20f, 0.20f, 0.20f, 1f, 0.95f, 0.70f)
                if (v.cannonFlashTimer > 0f) {
                    val fx = sinT * 4.35f
                    val fz = -0.10f - cosT * 4.35f
                    val fSc = 0.65f + (v.cannonFlashTimer / 0.18f) * 0.85f
                    carPart(host.sphereMesh, fx, 1.76f, fz, fSc, fSc, fSc, 1.0f, 0.84f, 0.22f)
                }
                host.setUnlit(false)
            }

            VehicleType.DUNE_BUGGY -> {
                // Agile off-road desert dune buggy with open tubular roll-cage, rear V6 engine block & roof LED bar
                carPart(host.cubeMesh, 0f, 0.56f, 0.12f, 1.88f, 0.42f, 3.85f, v.colorR, v.colorG, v.colorB, spec = 0.75f)
                carPart(host.cubeMesh, 0f, 0.66f, -1.62f, 1.44f, 0.28f, 0.92f, v.colorR * 0.92f, v.colorG * 0.92f, v.colorB * 0.92f, rotX = 14f, spec = 0.75f)
                // Tubular black steel roll-cage frame
                carPart(host.cylinderMesh, -0.78f, 1.22f, -0.55f, 0.12f, 1.20f, 0.12f, 0.16f, 0.18f, 0.22f, rotX = -26f, spec = 0.70f)
                carPart(host.cylinderMesh, 0.78f, 1.22f, -0.55f, 0.12f, 1.20f, 0.12f, 0.16f, 0.18f, 0.22f, rotX = -26f, spec = 0.70f)
                carPart(host.cylinderMesh, -0.78f, 1.28f, 0.92f, 0.12f, 1.25f, 0.12f, 0.16f, 0.18f, 0.22f, rotX = 24f, spec = 0.70f)
                carPart(host.cylinderMesh, 0.78f, 1.28f, 0.92f, 0.12f, 1.25f, 0.12f, 0.16f, 0.18f, 0.22f, rotX = 24f, spec = 0.70f)
                carPart(host.cubeMesh, 0f, 1.72f, 0.16f, 1.64f, 0.12f, 1.15f, 0.18f, 0.20f, 0.24f, spec = 0.75f)
                if (highDetail) {
                    // Exposed rear engine cylinders & rear wing
                    carPart(host.cubeMesh, 0f, 0.96f, 1.56f, 1.12f, 0.50f, 0.85f, 0.36f, 0.40f, 0.46f, spec = 0.90f)
                    carPart(host.cylinderMesh, -0.34f, 1.22f, 1.85f, 0.14f, 0.65f, 0.14f, 0.84f, 0.88f, 0.92f, rotX = 48f, spec = 1.0f)
                    carPart(host.cylinderMesh, 0.34f, 1.22f, 1.85f, 0.14f, 0.65f, 0.14f, 0.84f, 0.88f, 0.92f, rotX = 48f, spec = 1.0f)
                    carPart(host.cubeMesh, 0f, 1.48f, 1.88f, 1.98f, 0.10f, 0.34f, v.colorR, v.colorG, v.colorB, rotX = 10f, spec = 0.80f)
                }
                host.setUnlit(true)
                carPart(host.cubeMesh, 0f, 1.80f, -0.34f, 1.22f, 0.12f, 0.10f, 1f, 0.96f, 0.74f)
                carPart(host.sphereMesh, -0.62f, 0.72f, -2.02f, 0.22f, 0.22f, 0.16f, 1f, 0.95f, 0.72f)
                carPart(host.sphereMesh, 0.62f, 0.72f, -2.02f, 0.22f, 0.22f, 0.16f, 1f, 0.95f, 0.72f)
                if (v.nitroTimer > 0.05f) {
                    val fLen = 0.88f + abs(sin(worldTime * 34f)) * 0.48f
                    carPart(host.coneMesh, -0.34f, 1.02f, 2.24f + fLen * 0.35f, 0.22f, fLen, 0.22f, 1.0f, 0.65f, 0.16f, rotX = 90f)
                    carPart(host.coneMesh, 0.34f, 1.02f, 2.24f + fLen * 0.35f, 0.22f, fLen, 0.22f, 1.0f, 0.65f, 0.16f, rotX = 90f)
                }
                host.setUnlit(false)
                drawWheelSet(trackX = 1.22f, wheelZ = 1.44f, wheelDia = 0.96f, wheelWidth = 0.50f, wheelY = 0.48f)
            }

            VehicleType.FIRE_TRUCK -> {
                // Heavy Crimson Fire Engine with white stripe, chrome grille, flashing roof beacons & extendable top ladder!
                carPart(host.cubeMesh, 0f, 1.18f, -1.78f, 2.66f, 1.48f, 2.20f, v.colorR, v.colorG, v.colorB, spec = 0.78f)
                carPart(host.cubeMesh, 0f, 1.16f, 0.98f, 2.60f, 1.38f, 3.65f, v.colorR * 0.94f, v.colorG * 0.94f, v.colorB * 0.94f, spec = 0.72f)
                // White rescue stripe along sides
                carPart(host.cubeMesh, 0f, 0.98f, 0.0f, 2.70f, 0.24f, 5.75f, 0.96f, 0.97f, 0.99f, spec = 0.65f)
                // Wide windshield + metallic equipment roller doors + chrome front bumper
                carPart(host.cubeMesh, 0f, 1.44f, -2.90f, 2.28f, 0.66f, 0.10f, 0.24f, 0.64f, 0.90f, spec = 1.0f)
                carPart(host.cubeMesh, 0f, 0.56f, -2.98f, 2.72f, 0.34f, 0.28f, 0.86f, 0.88f, 0.92f, spec = 0.95f)
                if (highDetail) {
                    carPart(host.cubeMesh, -1.33f, 1.12f, 0.95f, 0.08f, 0.88f, 2.85f, 0.74f, 0.78f, 0.82f, spec = 0.85f)
                    carPart(host.cubeMesh, 1.33f, 1.12f, 0.95f, 0.08f, 0.88f, 2.85f, 0.74f, 0.78f, 0.82f, spec = 0.85f)
                }
                // Iconic silver extendable ladder assembly mounted on roof
                carPart(host.cubeMesh, -0.34f, 2.08f, 0.75f, 0.14f, 0.24f, 4.70f, 0.88f, 0.90f, 0.94f, rotX = -6f, spec = 0.95f)
                carPart(host.cubeMesh, 0.34f, 2.08f, 0.75f, 0.14f, 0.24f, 4.70f, 0.88f, 0.90f, 0.94f, rotX = -6f, spec = 0.95f)
                carPart(host.cubeMesh, 0f, 1.98f, 0.75f, 0.68f, 0.10f, 4.40f, 0.74f, 0.78f, 0.84f, rotX = -6f, spec = 0.80f)
                val sirenPulse = ((worldTime * 7.2f).toInt() and 1) == 0
                host.setUnlit(true)
                carPart(host.cubeMesh, -0.58f, 2.04f, -2.35f, 0.54f, 0.20f, 0.28f, if (sirenPulse) 1.0f else 0.45f, 0.10f, 0.10f)
                carPart(host.cubeMesh, 0.58f, 2.04f, -2.35f, 0.54f, 0.20f, 0.28f, if (!sirenPulse) 1.0f else 0.45f, if (!sirenPulse) 0.92f else 0.35f, 0.20f)
                carPart(host.cubeMesh, -0.92f, 0.84f, -2.92f, 0.44f, 0.22f, 0.10f, 1f, 0.96f, 0.74f)
                carPart(host.cubeMesh, 0.92f, 0.84f, -2.92f, 0.44f, 0.22f, 0.10f, 1f, 0.96f, 0.74f)
                carPart(host.cubeMesh, -0.92f, 0.84f, 2.84f, 0.48f, 0.24f, 0.10f, 1f, 0.14f, 0.12f)
                carPart(host.cubeMesh, 0.92f, 0.84f, 2.84f, 0.48f, 0.24f, 0.10f, 1f, 0.14f, 0.12f)
                host.setUnlit(false)
                drawWheelSet(trackX = 1.40f, wheelZ = 1.92f, wheelDia = 1.04f, wheelWidth = 0.52f, wheelY = 0.52f)
            }

            VehicleType.ARMORED_SWAT -> {
                // Heavy Tactical Armored SWAT Van with angled armor nose, steel battering ram, armored slits & roof hatch
                carPart(host.cubeMesh, 0f, 1.12f, 0.18f, 2.58f, 1.38f, 4.95f, v.colorR, v.colorG, v.colorB, spec = 0.55f)
                carPart(host.cubeMesh, 0f, 0.96f, -2.42f, 2.38f, 0.82f, 1.15f, v.colorR * 0.92f, v.colorG * 0.92f, v.colorB * 0.92f, rotX = 18f, spec = 0.55f)
                // Heavy steel bullbar battering ram
                carPart(host.cubeMesh, 0f, 0.68f, -3.02f, 2.48f, 0.64f, 0.34f, 0.14f, 0.16f, 0.20f, spec = 0.82f)
                // Narrow armored glass viewport + roof turret hatch
                carPart(host.cubeMesh, 0f, 1.46f, -2.30f, 1.98f, 0.34f, 0.14f, 0.22f, 0.58f, 0.86f, spec = 1.0f)
                carPart(host.cylinderMesh, 0f, 1.96f, 0.35f, 1.18f, 0.28f, 1.18f, 0.16f, 0.18f, 0.24f, spec = 0.65f)
                if (highDetail) {
                    // Side armor plate ribs & armored gunports
                    carPart(host.cubeMesh, -1.32f, 1.14f, 0.20f, 0.10f, 0.62f, 4.10f, 0.10f, 0.14f, 0.24f, spec = 0.65f)
                    carPart(host.cubeMesh, 1.32f, 1.14f, 0.20f, 0.10f, 0.62f, 4.10f, 0.10f, 0.14f, 0.24f, spec = 0.65f)
                    carPart(host.cubeMesh, 0f, 1.92f, -1.25f, 2.15f, 0.18f, 0.26f, 0.14f, 0.16f, 0.20f, spec = 0.75f)
                }
                val swatBlue = ((worldTime * 8.0f).toInt() and 1) == 0
                host.setUnlit(true)
                carPart(host.cubeMesh, -0.55f, 2.04f, -1.25f, 0.64f, 0.18f, 0.22f, 0.12f, if (swatBlue) 0.65f else 0.25f, if (swatBlue) 1.0f else 0.50f)
                carPart(host.cubeMesh, 0.55f, 2.04f, -1.25f, 0.64f, 0.18f, 0.22f, if (!swatBlue) 1.0f else 0.38f, 0.12f, 0.16f)
                carPart(host.cubeMesh, -0.84f, 0.92f, -2.96f, 0.38f, 0.20f, 0.10f, 1f, 0.97f, 0.80f)
                carPart(host.cubeMesh, 0.84f, 0.92f, -2.96f, 0.38f, 0.20f, 0.10f, 1f, 0.97f, 0.80f)
                host.setUnlit(false)
                drawWheelSet(trackX = 1.38f, wheelZ = 1.72f, wheelDia = 1.10f, wheelWidth = 0.56f, wheelY = 0.55f)
            }

            VehicleType.CYBER_SUPERCAR -> {
                // Futuristic Low-Slung Neon Hypercar with wedge aero nose, glowing cyan trims & twin jet thrusters!
                carPart(host.cubeMesh, 0f, 0.50f, 0f, 2.52f, 0.44f, 4.92f, v.colorR, v.colorG, v.colorB, spec = 0.95f)
                carPart(host.cubeMesh, 0f, 0.44f, -2.20f, 2.38f, 0.24f, 0.85f, v.colorR * 0.85f, v.colorG * 0.85f, v.colorB * 0.85f, rotX = 14f, spec = 0.95f)
                // Sleek glass canopy bubble
                carPart(host.sphereMesh, 0f, 0.94f, 0.12f, 0.92f, 0.36f, 1.38f, 0.14f, 0.74f, 0.96f, spec = 1.0f)
                // Raised carbon hypercar active wing
                carPart(host.cubeMesh, 0f, 1.24f, 2.24f, 2.58f, 0.08f, 0.48f, 0.12f, 0.14f, 0.20f, rotX = 10f, spec = 0.90f)
                if (highDetail) {
                    // Wide aero wheel-arch blisters, rear diffuser, wing struts & twin jet nozzles
                    carPart(host.cubeMesh, -1.28f, 0.62f, -1.56f, 0.18f, 0.30f, 1.06f, v.colorR * 0.82f, v.colorG * 0.82f, v.colorB * 0.82f, spec = 0.95f)
                    carPart(host.cubeMesh, 1.28f, 0.62f, -1.56f, 0.18f, 0.30f, 1.06f, v.colorR * 0.82f, v.colorG * 0.82f, v.colorB * 0.82f, spec = 0.95f)
                    carPart(host.cubeMesh, -1.28f, 0.62f, 1.56f, 0.18f, 0.30f, 1.06f, v.colorR * 0.82f, v.colorG * 0.82f, v.colorB * 0.82f, spec = 0.95f)
                    carPart(host.cubeMesh, 1.28f, 0.62f, 1.56f, 0.18f, 0.30f, 1.06f, v.colorR * 0.82f, v.colorG * 0.82f, v.colorB * 0.82f, spec = 0.95f)
                    carPart(host.cubeMesh, 0f, 0.32f, 2.46f, 2.40f, 0.20f, 0.22f, 0.12f, 0.14f, 0.20f, spec = 0.75f)
                    carPart(host.cubeMesh, -0.96f, 0.96f, 2.18f, 0.10f, 0.54f, 0.28f, 0.14f, 0.16f, 0.22f, spec = 0.80f)
                    carPart(host.cubeMesh, 0.96f, 0.96f, 2.18f, 0.10f, 0.54f, 0.28f, 0.14f, 0.16f, 0.22f, spec = 0.80f)
                    carPart(host.cylinderMesh, -0.56f, 0.48f, 2.54f, 0.24f, 0.32f, 0.24f, 0.22f, 0.26f, 0.34f, rotX = 90f, spec = 1.0f)
                    carPart(host.cylinderMesh, 0.56f, 0.48f, 2.54f, 0.24f, 0.32f, 0.24f, 0.22f, 0.26f, 0.34f, rotX = 90f, spec = 1.0f)
                }
                host.setUnlit(true)
                // Glowing neon side skirts, full-width front DRL strip & rear cyber lightbar
                carPart(host.cubeMesh, -1.28f, 0.32f, 0f, 0.08f, 0.10f, 4.30f, 0.16f, 0.96f, 1.0f)
                carPart(host.cubeMesh, 1.28f, 0.32f, 0f, 0.08f, 0.10f, 4.30f, 0.16f, 0.96f, 1.0f)
                carPart(host.cubeMesh, 0f, 0.54f, -2.54f, 2.12f, 0.10f, 0.08f, 0.20f, 0.96f, 1.0f)
                carPart(host.cubeMesh, 0f, 0.62f, 2.48f, 2.24f, 0.12f, 0.08f, 1.0f, 0.18f, 0.68f)
                if (v.nitroTimer > 0.05f) {
                    val fLen = 1.25f + abs(sin(worldTime * 38f)) * 0.65f
                    carPart(host.coneMesh, -0.56f, 0.48f, 2.74f + fLen * 0.4f, 0.28f, fLen, 0.28f, 0.20f, 0.92f, 1.0f, rotX = 90f)
                    carPart(host.coneMesh, 0.56f, 0.48f, 2.74f + fLen * 0.4f, 0.28f, fLen, 0.28f, 0.96f, 0.24f, 0.92f, rotX = 90f)
                }
                host.setUnlit(false)
                drawWheelSet(trackX = 1.32f, wheelZ = 1.56f, wheelDia = 0.80f, wheelWidth = 0.48f, wheelY = 0.40f)
            }
        }
    }

    /**
     * Crisp 3D First-Person Weapon Viewmodel with smooth ADS blend, reload tilt animation,
     * laser rail, rifle foregrip & RPG rear venturi exhaust flare (zero per-frame heap allocations).
     */
    fun drawFirstPersonWeaponViewmodel(engine: GameEngine, surfaceAspect: Float) {
        Matrix.perspectiveM(fpProjMatrix, 0, 56f, surfaceAspect, 0.04f, 14f)
        System.arraycopy(host.vpMatrix, 0, savedVpMatrix, 0, 16)
        System.arraycopy(fpProjMatrix, 0, host.vpMatrix, 0, 16)
        // Reset uCamPos to (0,0,0) in view space so weapon never receives world-distance fog
        host.setCameraPosUniform(0f, 0f, 0f)

        val aimBlend = engine.aimBlend
        val bobAmp = 1f - aimBlend * 0.72f
        val bobX = if (engine.playerGrounded) cos(engine.playerWalkCycle) * 0.018f * bobAmp else 0f
        val bobY = if (engine.playerGrounded) sin(engine.playerWalkCycle * 2f) * 0.015f * bobAmp else 0f

        val reloadFrac = if (engine.reloadTimer > 0f && engine.currentWeapon.reloadSec > 0.01f) {
            (1f - engine.reloadTimer / engine.currentWeapon.reloadSec).coerceIn(0f, 1f)
        } else 0f
        val reloadArc = sin(reloadFrac * PI.toFloat())
        val reloadDip = -0.17f * reloadArc
        val reloadRoll = -28f * reloadArc

        val baseX = 0.31f * (1f - aimBlend * 0.86f) + bobX
        val baseY = -0.28f + aimBlend * 0.085f + bobY + reloadDip + engine.landingCameraDip * 0.25f
        val baseZ = -0.66f + engine.weaponRecoil * 0.30f + aimBlend * 0.10f

        // Primary .glb first-person weapon viewmodel with classic/tactical skins, slide blowback, magazine drop & rocket hide!
        val fpWeaponGpu = host.glbManager.getOrLoadGpu(
            engine.currentWeapon.glbFile(engine.settings.weaponSkin),
            syncIfMissing = true
        )
        if (fpWeaponGpu != null) {
            Matrix.setIdentityM(glbRootMatrix, 0)
            val weaponScale = when (engine.currentWeapon) {
                WeaponType.PISTOL -> 1.45f
                WeaponType.RIFLE -> 0.88f
                WeaponType.RPG -> 0.62f
            }
            val xAdj = when (engine.currentWeapon) {
                WeaponType.PISTOL -> -0.02f * (1f - aimBlend)
                WeaponType.RIFLE -> 0.01f * (1f - aimBlend)
                WeaponType.RPG -> 0.04f * (1f - aimBlend)
            }
            val yAdj = when (engine.currentWeapon) {
                WeaponType.PISTOL -> -0.04f
                WeaponType.RIFLE -> -0.06f
                WeaponType.RPG -> -0.05f
            }
            val zAdj = when (engine.currentWeapon) {
                WeaponType.PISTOL -> 0.12f
                WeaponType.RIFLE -> -0.06f
                WeaponType.RPG -> -0.04f
            }
            Matrix.translateM(glbRootMatrix, 0, baseX + xAdj, baseY + yAdj, baseZ + zAdj)
            Matrix.rotateM(glbRootMatrix, 0, engine.weaponRecoil * 22f, 1f, 0f, 0f)
            if (reloadRoll != 0f) Matrix.rotateM(glbRootMatrix, 0, reloadRoll, 0f, 0f, 1f)
            // Slight inward cant when hip-firing, straight center when aiming down sights
            val hipYaw = -3.5f * (1f - aimBlend)
            Matrix.rotateM(glbRootMatrix, 0, 180f + hipYaw, 0f, 1f, 0f)
            Matrix.scaleM(glbRootMatrix, 0, weaponScale, weaponScale, weaponScale)

            Matrix.setIdentityM(muzzleWorldMatrix, 0)
            nodeOverrides.clear()
            if (engine.currentWeapon == WeaponType.RPG && !engine.rpgRocketLoaded) {
                nodeOverrides.hiddenNodes.add("rocket")
            }
            if (engine.weaponRecoil > 0.01f && engine.currentWeapon != WeaponType.RPG) {
                transBufA[0] = 0f; transBufA[1] = 0f; transBufA[2] = -engine.weaponRecoil * 0.18f
                nodeOverrides.extraTrans["slide"] = transBufA
            }
            if (reloadArc > 0.02f && engine.currentWeapon != WeaponType.RPG) {
                transBufB[0] = 0f; transBufB[1] = -reloadArc * 0.22f; transBufB[2] = 0f
                nodeOverrides.extraTrans["magazine"] = transBufB
            }
            nodeOverrides.captureWorldMatrix["muzzle"] = muzzleWorldMatrix

            host.glbManager.drawModel(
                renderer = host,
                model = fpWeaponGpu,
                rootWorldMatrix = glbRootMatrix,
                overrides = nodeOverrides
            )

            if (engine.muzzleFlashTimer > 0f) {
                val fx = if (muzzleWorldMatrix[14] != 0f) muzzleWorldMatrix[12] else baseX
                val fy = if (muzzleWorldMatrix[14] != 0f) muzzleWorldMatrix[13] else baseY + 0.04f
                val fz = if (muzzleWorldMatrix[14] != 0f) muzzleWorldMatrix[14] else baseZ - 0.65f
                host.setUnlit(true)
                host.drawMesh(host.sphereMesh, fx, fy, fz, 0f, 0.16f, 0.16f, 0.16f, 1f, 0.90f, 0.28f, 1f)
                host.setUnlit(false)
            }

            System.arraycopy(savedVpMatrix, 0, host.vpMatrix, 0, 16)
            return
        }

        fun fpPart(
            mesh: GlMesh,
            lx: Float, ly: Float, lz: Float,
            sx: Float, sy: Float, sz: Float,
            r: Float, g: Float, b: Float,
            rotX: Float = 0f,
            rotZ: Float = 0f,
            spec: Float = 0.70f
        ) {
            Matrix.setIdentityM(host.modelMatrix, 0)
            Matrix.translateM(host.modelMatrix, 0, baseX + lx, baseY + ly, baseZ + lz)
            Matrix.rotateM(host.modelMatrix, 0, engine.weaponRecoil * 24f, 1f, 0f, 0f)
            if (reloadRoll != 0f) Matrix.rotateM(host.modelMatrix, 0, reloadRoll, 0f, 0f, 1f)
            if (rotX != 0f) Matrix.rotateM(host.modelMatrix, 0, rotX, 1f, 0f, 0f)
            if (rotZ != 0f) Matrix.rotateM(host.modelMatrix, 0, rotZ, 0f, 0f, 1f)
            Matrix.scaleM(host.modelMatrix, 0, sx, sy, sz)
            host.drawCurrentModel(mesh, r, g, b, 1f, spec)
        }

        // Sculpted feathered chicken wing holding the weapon (tinted with player's chosen skin!)
        val playerSkin = engine.settings.selectedSkin
        fpPart(
            host.sphereMesh,
            0.11f, -0.14f, 0.22f,
            0.10f, 0.12f, 0.22f,
            playerSkin.featherR, playerSkin.featherG, playerSkin.featherB,
            spec = 0.12f
        )

        when (engine.currentWeapon) {
            WeaponType.PISTOL -> {
                fpPart(host.cubeMesh, 0f, 0.02f, -0.08f, 0.10f, 0.12f, 0.44f, 0.20f, 0.24f, 0.30f, spec = 0.80f)
                fpPart(host.cubeMesh, 0f, 0.09f, -0.10f, 0.09f, 0.05f, 0.40f, 0.62f, 0.66f, 0.72f, spec = 0.94f)
                fpPart(host.cylinderMesh, 0f, 0.04f, -0.35f, 0.06f, 0.16f, 0.06f, 0.14f, 0.16f, 0.20f, rotX = 90f, spec = 0.90f)
                fpPart(host.cubeMesh, 0f, -0.10f, 0.08f, 0.08f, 0.18f, 0.12f, 0.14f, 0.15f, 0.18f, spec = 0.30f)
                // Underbarrel tactical laser module
                fpPart(host.cubeMesh, 0f, -0.045f, -0.22f, 0.055f, 0.045f, 0.14f, 0.14f, 0.16f, 0.20f, spec = 0.75f)
                host.setUnlit(true)
                fpPart(host.cubeMesh, 0f, 0.125f, -0.26f, 0.024f, 0.024f, 0.024f, 0.16f, 1.0f, 0.48f)
                fpPart(host.sphereMesh, 0f, -0.045f, -0.30f, 0.022f, 0.022f, 0.022f, 1.0f, 0.18f, 0.16f)
                host.setUnlit(false)
            }
            WeaponType.RIFLE -> {
                fpPart(host.cubeMesh, 0f, 0.02f, -0.14f, 0.11f, 0.15f, 0.76f, 0.18f, 0.21f, 0.26f, spec = 0.72f)
                // Top Picatinny rail + angled foregrip
                fpPart(host.cubeMesh, 0f, 0.10f, -0.26f, 0.06f, 0.025f, 0.48f, 0.32f, 0.36f, 0.42f, spec = 0.85f)
                fpPart(host.cubeMesh, 0f, -0.08f, -0.38f, 0.055f, 0.11f, 0.07f, 0.14f, 0.16f, 0.20f, rotX = -22f, spec = 0.45f)
                fpPart(host.cylinderMesh, 0f, 0.03f, -0.66f, 0.05f, 0.38f, 0.05f, 0.28f, 0.32f, 0.38f, rotX = 90f, spec = 0.90f)
                fpPart(host.cubeMesh, 0f, 0.0f, 0.26f, 0.10f, 0.14f, 0.26f, 0.58f, 0.35f, 0.16f, spec = 0.22f)
                fpPart(host.cubeMesh, 0f, -0.14f, -0.20f, 0.08f, 0.26f, 0.12f, 0.14f, 0.15f, 0.18f, rotX = -10f, spec = 0.55f)
                // Holographic optic housing + glowing emerald reticle dot
                fpPart(host.cubeMesh, 0f, 0.14f, -0.06f, 0.085f, 0.08f, 0.20f, 0.12f, 0.14f, 0.18f, spec = 0.85f)
                host.setUnlit(true)
                fpPart(host.cubeMesh, 0f, 0.14f, -0.15f, 0.022f, 0.022f, 0.02f, 0.14f, 1.0f, 0.55f)
                host.setUnlit(false)
            }
            WeaponType.RPG -> {
                fpPart(host.cylinderMesh, 0.02f, 0.04f, -0.18f, 0.18f, 1.05f, 0.18f, 0.26f, 0.38f, 0.22f, rotX = 90f, spec = 0.45f)
                fpPart(host.cylinderMesh, 0.02f, 0.04f, -0.10f, 0.21f, 0.38f, 0.21f, 0.58f, 0.36f, 0.16f, rotX = 90f, spec = 0.20f)
                // Rear venturi exhaust cone & optical side sight
                fpPart(host.coneMesh, 0.02f, 0.04f, 0.42f, 0.25f, 0.24f, 0.25f, 0.18f, 0.20f, 0.22f, rotX = -90f, spec = 0.65f)
                fpPart(host.cylinderMesh, -0.10f, 0.14f, -0.12f, 0.055f, 0.24f, 0.055f, 0.14f, 0.16f, 0.20f, rotX = 90f, spec = 0.85f)
                fpPart(host.coneMesh, 0.02f, 0.04f, -0.82f, 0.28f, 0.38f, 0.28f, 0.88f, 0.22f, 0.14f, rotX = -90f, spec = 0.60f)
                fpPart(host.cubeMesh, 0.02f, -0.12f, -0.05f, 0.08f, 0.18f, 0.10f, 0.45f, 0.28f, 0.12f, spec = 0.25f)
            }
        }

        if (engine.muzzleFlashTimer > 0f) {
            host.setUnlit(true)
            fpPart(host.sphereMesh, 0f, 0.04f, -0.90f, 0.22f, 0.22f, 0.22f, 1f, 0.90f, 0.28f)
            host.setUnlit(false)
        }

        System.arraycopy(savedVpMatrix, 0, host.vpMatrix, 0, 16)
    }
}
