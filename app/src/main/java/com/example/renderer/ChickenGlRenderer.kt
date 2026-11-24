package com.example.renderer

import android.content.res.AssetManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.engine.CameraMode
import com.example.engine.GameEngine
import com.example.engine.GameMap
import com.example.engine.PropType
import com.example.engine.VehicleType
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * Immutable loading progress snapshot emitted by [ChickenGlRenderer] during initial match load
 * and live map switches so the Compose Loading Screen shows real 0..100% readiness.
 */
data class SceneLoadState(
    val map: GameMap = GameMap.MAIN_ARENA,
    val progress: Float = 0f,
    val stageLabel: String = "Инициализация графики…",
    val isReady: Boolean = false,
    val generation: Int = 0
)

/**
 * Upgraded OpenGL ES 2.0 3D Renderer for Chicken Gun 3D:
 * - Full glTF 2.0 (.glb) textured PBR-lit shader pipeline with VBO/IBO/Texture caching + node animations
 * - Staged shader/terrain/texture/.glb preloading with real 0..100% progress & first-frame warm-up
 * - Inverse-transpose Normal Matrix (accurate lighting on non-uniform scaled props/vehicles)
 * - Blinn-Phong Specular Sheen + Sky/Earth Hemisphere Lighting + Fresnel Rim Glow + Distance Fog
 * - Atmospheric Sky Dome, Distant Mountain Silhouettes, Double-Halo Sun, Multi-Puff Drifting Clouds
 * - Dynamic Ground Contact Shadows & automatic fallback to procedural primitives
 */
class ChickenGlRenderer(
    private val engine: GameEngine,
    assets: AssetManager,
    private val onLoadStateChanged: ((SceneLoadState) -> Unit)? = null
) : GLSurfaceView.Renderer {

    internal val glbManager = GlbModelManager(assets)

    private var litProgram = 0
    private var litMvpLoc = 0
    private var litModelLoc = 0
    private var litNormalMatLoc = 0
    private var litColorLoc = 0
    private var litLightDirLoc = 0
    private var litHemiSkyLoc = 0
    private var litHemiGroundLoc = 0
    private var litSunColorLoc = 0
    private var litFogColorLoc = 0
    private var litFogStartLoc = 0
    private var litFogRangeLoc = 0
    private var litCamPosLoc = 0
    private var litUnlitLoc = 0
    private var litSpecLoc = 0
    private var litPosAttr = 0
    private var litNormAttr = 0

    // Textured PBR-lit shader program for .glb (glTF 2.0) meshes
    private var texLitProgram = 0
    private var texMvpLoc = 0
    private var texModelLoc = 0
    private var texNormalMatLoc = 0
    private var texColorLoc = 0
    private var texEmissiveLoc = 0
    private var texSamplerLoc = 0
    private var texLightDirLoc = 0
    private var texHemiSkyLoc = 0
    private var texHemiGroundLoc = 0
    private var texSunColorLoc = 0
    private var texFogColorLoc = 0
    private var texFogStartLoc = 0
    private var texFogRangeLoc = 0
    private var texCamPosLoc = 0
    private var texUnlitLoc = 0
    private var texSpecLoc = 0
    private var texAlphaModeLoc = 0
    private var texAlphaCutoffLoc = 0
    private var texPosAttr = 0
    private var texNormAttr = 0
    private var texUvAttr = 0

    private var colorProgram = 0
    private var colorMvpLoc = 0
    private var colorCamPosLoc = 0
    private var colorFogColorLoc = 0
    private var colorFogStartLoc = 0
    private var colorFogRangeLoc = 0
    private var colorFogEnableLoc = 0
    private var colorDetailEnableLoc = 0
    private var colorPosAttr = 0
    private var colorColAttr = 0

    internal lateinit var cubeMesh: GlMesh
    internal lateinit var rampMesh: GlMesh
    internal lateinit var cylinderMesh: GlMesh
    internal lateinit var coneMesh: GlMesh
    internal lateinit var sphereMesh: GlMesh
    internal lateinit var discMesh: GlMesh
    private lateinit var groundMesh: GlColorMesh
    private lateinit var skyDomeMesh: GlColorMesh
    private lateinit var mountainMesh: GlColorMesh
    private var loadedMap: GameMap? = null
    private var curTanHalfFovX: Float = 1.6f

    private val entityRenderer = WorldEntityRenderer(this)

    // Current frame lighting/fog state shared between litProgram and texLitProgram
    private var activeProgram = 0
    private var curLightDirX = 0.46f
    private var curLightDirY = 0.80f
    private var curLightDirZ = 0.38f
    private var curHemiSkyR = 0.56f
    private var curHemiSkyG = 0.68f
    private var curHemiSkyB = 0.84f
    private var curHemiGndR = 0.28f
    private var curHemiGndG = 0.31f
    private var curHemiGndB = 0.36f
    private var curSunR = 1.04f
    private var curSunG = 0.98f
    private var curSunB = 0.90f
    private var curFogR = 0.76f
    private var curFogG = 0.89f
    private var curFogB = 0.96f
    private var curFogStart = 70f
    private var curFogRange = 140f
    private var curCamX = 0f
    private var curCamY = 2f
    private var curCamZ = 18f
    private var texFrameUniformsSynced = false
    private var lastBoundTexId = -1
    private var lastObservedGlbUploads = -1
    private var lastCullEnabled = true
    private var lastBlendEnabled = true
    private var lastDepthMask = true
    private var lastFrontFaceCw = false

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    internal val vpMatrix = FloatArray(16)
    internal val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val invModelMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)
    private val projVecIn = FloatArray(4)
    private val projVecOut = FloatArray(4)

    private var currentUnlit = false
    private var lastUploadedUnlit = -1
    private var lastSpec = -1f
    private var lastColR = -1f
    private var lastColG = -1f
    private var lastColB = -1f
    private var lastColA = -1f

    // Cached GLB material uniforms so consecutive batched primitives skip redundant glUniform calls
    private var lastTexColR = -1f
    private var lastTexColG = -1f
    private var lastTexColB = -1f
    private var lastTexColA = -1f
    private var lastTexEmR = -1f
    private var lastTexEmG = -1f
    private var lastTexEmB = -1f
    private var lastTexUnlit = -1
    private var lastTexSpec = -1f
    private var lastTexAlphaMode = -1f
    private var lastTexAlphaCutoff = -1f

    // Zero-GC intrusive linked buckets for grouping identical .glb models (Props & Vehicles) by type
    private val propBucketHead = IntArray(PropType.entries.size) { -1 }
    private var propBucketNext = IntArray(512)
    private var propShadowYBuf = FloatArray(512)
    private var propHighDetailBuf = BooleanArray(512)

    private val vehicleBucketHead = IntArray(VehicleType.entries.size) { -1 }
    private var vehicleBucketNext = IntArray(64)
    private var vehicleShadowYBuf = FloatArray(64)
    private var vehicleHighDetailBuf = BooleanArray(64)

    // Pre-allocated callback avoiding per-frame closure allocation in drawCombatChicken
    private val muzzleWorldCallback: (Float, Float, Float) -> Unit = { mx, my, mz ->
        engine.setRendererMuzzleWorld(mx, my, mz)
    }

    private var surfaceAspect = 1.77f
    private var lastFrameNanos = 0L
    private var nextFrameDeadlineNanos = 0L

    // Staged scene preloading state machine (keeps simulation frozen until 100% + first ready frame)
    private enum class LoadPhase {
        MAP_MESHES,
        GLB_STREAMING,
        WARMUP_FIRST_FRAME,
        READY
    }

    private var loadPhase: LoadPhase = LoadPhase.MAP_MESHES
    private var loadingTargetMap: GameMap = engine.activeMap
    private var loadGeneration: Int = 0
    private var loadStartNanos: Long = 0L
    private var activePreloadTargets: List<String> = emptyList()
    private var lastEmittedProgress: Float = -1f
    private var lastEmittedReady: Boolean = false
    private var lastEmittedStage: String = ""
    private var lastEmittedGeneration: Int = -1

    private fun ensurePropBatchCapacity(size: Int) {
        if (size <= propBucketNext.size) return
        val nextCap = maxOf(size + 128, propBucketNext.size * 2)
        propBucketNext = IntArray(nextCap)
        propShadowYBuf = FloatArray(nextCap)
        propHighDetailBuf = BooleanArray(nextCap)
    }

    private fun ensureVehicleBatchCapacity(size: Int) {
        if (size <= vehicleBucketNext.size) return
        val nextCap = maxOf(size + 32, vehicleBucketNext.size * 2)
        vehicleBucketNext = IntArray(nextCap)
        vehicleShadowYBuf = FloatArray(nextCap)
        vehicleHighDetailBuf = BooleanArray(nextCap)
    }

    private val clouds = arrayOf(
        floatArrayOf(-115f, 66f, -95f, 22f, 12.5f),
        floatArrayOf(38f, 72f, -120f, 25f, 14.0f),
        floatArrayOf(-24f, 64f, 92f, 20f, 11.5f),
        floatArrayOf(112f, 69f, 54f, 24f, 13.0f),
        floatArrayOf(-130f, 71f, 36f, 23f, 12.0f),
        floatArrayOf(16f, 76f, -18f, 21f, 11.5f),
        floatArrayOf(96f, 65f, -68f, 20f, 11.0f),
        floatArrayOf(-76f, 74f, 122f, 24f, 13.5f),
        floatArrayOf(68f, 70f, 116f, 22f, 12.0f),
        floatArrayOf(-58f, 68f, -42f, 18f, 10.5f),
        floatArrayOf(132f, 75f, -8f, 21f, 11.5f),
        floatArrayOf(-10f, 78f, -138f, 26f, 14.0f)
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.62f, 0.85f, 0.97f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        litProgram = createProgram(LIT_VERTEX_SHADER, LIT_FRAGMENT_SHADER)
        litMvpLoc = GLES20.glGetUniformLocation(litProgram, "uMVP")
        litModelLoc = GLES20.glGetUniformLocation(litProgram, "uModel")
        litNormalMatLoc = GLES20.glGetUniformLocation(litProgram, "uNormalMat")
        litColorLoc = GLES20.glGetUniformLocation(litProgram, "uColor")
        litLightDirLoc = GLES20.glGetUniformLocation(litProgram, "uLightDir")
        litHemiSkyLoc = GLES20.glGetUniformLocation(litProgram, "uHemiSkyColor")
        litHemiGroundLoc = GLES20.glGetUniformLocation(litProgram, "uHemiGroundColor")
        litSunColorLoc = GLES20.glGetUniformLocation(litProgram, "uSunColor")
        litFogColorLoc = GLES20.glGetUniformLocation(litProgram, "uFogColor")
        litFogStartLoc = GLES20.glGetUniformLocation(litProgram, "uFogStart")
        litFogRangeLoc = GLES20.glGetUniformLocation(litProgram, "uFogRange")
        litCamPosLoc = GLES20.glGetUniformLocation(litProgram, "uCamPos")
        litUnlitLoc = GLES20.glGetUniformLocation(litProgram, "uUnlit")
        litSpecLoc = GLES20.glGetUniformLocation(litProgram, "uSpec")
        litPosAttr = GLES20.glGetAttribLocation(litProgram, "aPos")
        litNormAttr = GLES20.glGetAttribLocation(litProgram, "aNorm")

        texLitProgram = createProgram(TEX_LIT_VERTEX_SHADER, TEX_LIT_FRAGMENT_SHADER)
        texMvpLoc = GLES20.glGetUniformLocation(texLitProgram, "uMVP")
        texModelLoc = GLES20.glGetUniformLocation(texLitProgram, "uModel")
        texNormalMatLoc = GLES20.glGetUniformLocation(texLitProgram, "uNormalMat")
        texColorLoc = GLES20.glGetUniformLocation(texLitProgram, "uColor")
        texEmissiveLoc = GLES20.glGetUniformLocation(texLitProgram, "uEmissive")
        texSamplerLoc = GLES20.glGetUniformLocation(texLitProgram, "uTexture")
        texLightDirLoc = GLES20.glGetUniformLocation(texLitProgram, "uLightDir")
        texHemiSkyLoc = GLES20.glGetUniformLocation(texLitProgram, "uHemiSkyColor")
        texHemiGroundLoc = GLES20.glGetUniformLocation(texLitProgram, "uHemiGroundColor")
        texSunColorLoc = GLES20.glGetUniformLocation(texLitProgram, "uSunColor")
        texFogColorLoc = GLES20.glGetUniformLocation(texLitProgram, "uFogColor")
        texFogStartLoc = GLES20.glGetUniformLocation(texLitProgram, "uFogStart")
        texFogRangeLoc = GLES20.glGetUniformLocation(texLitProgram, "uFogRange")
        texCamPosLoc = GLES20.glGetUniformLocation(texLitProgram, "uCamPos")
        texUnlitLoc = GLES20.glGetUniformLocation(texLitProgram, "uUnlit")
        texSpecLoc = GLES20.glGetUniformLocation(texLitProgram, "uSpec")
        texAlphaModeLoc = GLES20.glGetUniformLocation(texLitProgram, "uAlphaMode")
        texAlphaCutoffLoc = GLES20.glGetUniformLocation(texLitProgram, "uAlphaCutoff")
        texPosAttr = GLES20.glGetAttribLocation(texLitProgram, "aPos")
        texNormAttr = GLES20.glGetAttribLocation(texLitProgram, "aNorm")
        texUvAttr = GLES20.glGetAttribLocation(texLitProgram, "aTexCoord")

        colorProgram = createProgram(COLOR_VERTEX_SHADER, COLOR_FRAGMENT_SHADER)
        colorMvpLoc = GLES20.glGetUniformLocation(colorProgram, "uMVP")
        colorCamPosLoc = GLES20.glGetUniformLocation(colorProgram, "uCamPos")
        colorFogColorLoc = GLES20.glGetUniformLocation(colorProgram, "uFogColor")
        colorFogStartLoc = GLES20.glGetUniformLocation(colorProgram, "uFogStart")
        colorFogRangeLoc = GLES20.glGetUniformLocation(colorProgram, "uFogRange")
        colorFogEnableLoc = GLES20.glGetUniformLocation(colorProgram, "uFogEnable")
        colorDetailEnableLoc = GLES20.glGetUniformLocation(colorProgram, "uDetailEnable")
        colorPosAttr = GLES20.glGetAttribLocation(colorProgram, "aPos")
        colorColAttr = GLES20.glGetAttribLocation(colorProgram, "aColor")

        // GPU VBO meshes tuned for crisp silhouettes and strong performance on low-end mobile GPUs
        val map = engine.activeMap
        loadingTargetMap = map
        loadGeneration++
        loadStartNanos = System.nanoTime()
        emitLoadState(map, 0.08f, "Компиляция шейдеров…", isReady = false)

        GLES20.glClearColor(map.fogR, map.fogG, map.fogB, 1f)
        cubeMesh = MeshFactory.createUnitCube()
        rampMesh = MeshFactory.createRampWedge()
        cylinderMesh = MeshFactory.createCylinder(16)
        coneMesh = MeshFactory.createCone(12)
        sphereMesh = MeshFactory.createSphere(10, 14)
        discMesh = MeshFactory.createFlatDisc(14)
        groundMesh = MeshFactory.createArenaGround(map, 152f, 4.0f)
        skyDomeMesh = MeshFactory.createSkyDome(map, 380f, 10, 22)
        mountainMesh = MeshFactory.createMountainRing(map, 260f, 34)
        loadedMap = map

        glbManager.onGlContextCreated()
        activePreloadTargets = collectPrioritizedGlbTargets()
        glbManager.preloadBatch(activePreloadTargets)
        loadPhase = LoadPhase.GLB_STREAMING
        emitLoadState(map, 0.24f, "Загрузка 3D-моделей карты…", isReady = false)
        lastFrameNanos = System.nanoTime()
    }

    private fun collectPrioritizedGlbTargets(): List<String> {
        val ordered = LinkedHashSet<String>(GlbModelManager.CORE_PRELOAD_MODELS.size + 16)
        // Priority 1: Player, NPCs, weapons & all 7 vehicle .glb models
        ordered.add("khryak_napoleon_animated.glb")
        ordered.add("pig_burger_animated.glb")
        ordered.add("weapon_pistol_classic.glb")
        ordered.add("weapon_rifle_classic.glb")
        ordered.add("weapon_rpg_classic.glb")
        ordered.add("weapon_pistol_tactical.glb")
        ordered.add("weapon_rifle_tactical.glb")
        ordered.add("weapon_rpg_tactical.glb")
        ordered.add("car_police.glb")
        ordered.add("car_hypercar.glb")
        ordered.add("car_firetruck.glb")
        ordered.add("car_pickup.glb")
        ordered.add("car_monster.glb")
        ordered.add("car_suv.glb")
        ordered.add("car_sedan.glb")

        // Priority 2: Every .glb model currently placed on the active map
        synchronized(engine.stateLock) {
            val props = engine.props
            for (i in props.indices) {
                val glb = props[i].type.glbFile
                if (glb != null) ordered.add(glb)
            }
        }

        // Priority 3: All remaining sandbox/core .glb models so spawning never stalls mid-match
        for (file in GlbModelManager.CORE_PRELOAD_MODELS) {
            ordered.add(file)
        }
        return ArrayList(ordered)
    }

    private fun emitLoadState(map: GameMap, progress: Float, stageLabel: String, isReady: Boolean) {
        val clamped = progress.coerceIn(0f, 1f)
        if (
            lastEmittedGeneration == loadGeneration &&
            lastEmittedReady == isReady &&
            lastEmittedStage == stageLabel &&
            abs(clamped - lastEmittedProgress) < 0.01f &&
            clamped < 0.999f
        ) {
            return
        }
        lastEmittedGeneration = loadGeneration
        lastEmittedProgress = clamped
        lastEmittedReady = isReady
        lastEmittedStage = stageLabel
        onLoadStateChanged?.invoke(
            SceneLoadState(
                map = map,
                progress = clamped,
                stageLabel = stageLabel,
                isReady = isReady,
                generation = loadGeneration
            )
        )
    }

    private fun startMapSwitchLoad(targetMap: GameMap) {
        loadingTargetMap = targetMap
        loadGeneration++
        loadStartNanos = System.nanoTime()
        loadPhase = LoadPhase.MAP_MESHES
        emitLoadState(targetMap, 0.10f, "Перестроение ландшафта карты…", isReady = false)
    }

    private fun stepStagedLoadingFrame() {
        // Keep queued look/gyro deltas cleared while physics & timer are paused during loading
        engine.input.consumeLookDelta()
        engine.input.consumeGyroDelta()

        when (loadPhase) {
            LoadPhase.MAP_MESHES -> {
                val map = loadingTargetMap
                if (loadedMap != map) {
                    groundMesh.release()
                    skyDomeMesh.release()
                    mountainMesh.release()
                    groundMesh = MeshFactory.createArenaGround(map, 152f, 4.0f)
                    skyDomeMesh = MeshFactory.createSkyDome(map, 380f, 10, 22)
                    mountainMesh = MeshFactory.createMountainRing(map, 260f, 34)
                    GLES20.glClearColor(map.fogR, map.fogG, map.fogB, 1f)
                    loadedMap = map
                }
                activePreloadTargets = collectPrioritizedGlbTargets()
                glbManager.preloadBatch(activePreloadTargets)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                loadPhase = LoadPhase.GLB_STREAMING
                emitLoadState(map, 0.28f, "Загрузка 3D-моделей карты…", isReady = false)
            }

            LoadPhase.GLB_STREAMING -> {
                val map = loadingTargetMap
                val targets = activePreloadTargets
                glbManager.uploadReadyBatchOnGlThread(targets, maxBudgetMs = 10L)
                val stats = glbManager.getPreloadStats(targets)
                val total = stats.total.coerceAtLeast(1)
                val cpuFrac = (stats.cpuDone + stats.failed).toFloat() / total.toFloat()
                val gpuFrac = (stats.gpuDone + stats.failed).toFloat() / total.toFloat()
                val rawProgress = 0.28f + 0.62f * (cpuFrac * 0.35f + gpuFrac * 0.65f)

                val elapsedMs = (System.nanoTime() - loadStartNanos) / 1_000_000L
                val minPacingMs = 280L
                val pacedProgress = if (elapsedMs < minPacingMs) {
                    val timeCap = 0.28f + 0.62f * (elapsedMs.toFloat() / minPacingMs.toFloat())
                    minOf(rawProgress, timeCap)
                } else {
                    rawProgress
                }

                val stageLabel = when {
                    pacedProgress < 0.56f -> "Загрузка 3D-моделей карты…"
                    pacedProgress < 0.84f -> "Подготовка текстур и буферов GPU…"
                    else -> "Прогрев шейдеров сцены…"
                }
                emitLoadState(map, pacedProgress, stageLabel, isReady = false)

                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

                val timedOut = elapsedMs > 10_000L
                if ((stats.isAllGpuReady && elapsedMs >= minPacingMs) || timedOut) {
                    loadPhase = LoadPhase.WARMUP_FIRST_FRAME
                    emitLoadState(map, 0.94f, "Отрисовка первого кадра…", isReady = false)
                }
            }

            LoadPhase.WARMUP_FIRST_FRAME -> {
                // Render the complete first frame of the scene (all shaders, GLBs & textures bound)
                // BEFORE resuming engine.step(dt) so gameplay starts cleanly with zero hitch or pop-in!
                renderFullScenePass()
                GLES20.glFinish()
                lastFrameNanos = System.nanoTime()
                nextFrameDeadlineNanos = 0L
                loadPhase = LoadPhase.READY
                emitLoadState(loadingTargetMap, 1.0f, "Арена готова!", isReady = true)
            }

            LoadPhase.READY -> Unit
        }
    }

    private fun renderFullScenePass() {
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GlBufferState.reset()
        activeProgram = 0
        texFrameUniformsSynced = false
        lastBoundTexId = -1
        lastCullEnabled = true
        lastBlendEnabled = true
        lastDepthMask = true
        lastFrontFaceCw = false
        lastUploadedUnlit = -1
        lastSpec = -1f
        lastColR = -1f

        synchronized(engine.stateLock) {
            renderWorldLocked()
        }
    }

    fun releaseResources() {
        glbManager.shutdown()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        surfaceAspect = if (height > 0) width.toFloat() / height.toFloat() else 1.77f
    }

    override fun onDrawFrame(gl: GL10?) {
        val frameStart = System.nanoTime()

        // Detect live map switch (e.g. Online Host switching map) and run staged preloading cleanly
        val desiredMap = engine.activeMap
        if (desiredMap != loadingTargetMap || (loadPhase == LoadPhase.READY && loadedMap != desiredMap)) {
            startMapSwitchLoad(desiredMap)
        }

        if (loadPhase != LoadPhase.READY) {
            stepStagedLoadingFrame()
            lastFrameNanos = System.nanoTime()
            return
        }

        val dt = if (lastFrameNanos == 0L) 0.016f else ((frameStart - lastFrameNanos) / 1_000_000_000.0).toFloat()
        lastFrameNanos = frameStart

        engine.step(dt)
        renderFullScenePass()

        // Precision FPS limiter supporting 30 / 45 / 60 / 90 / 120 (Max) FPS
        val targetFps = engine.settings.targetFps
        if (targetFps < 115) {
            val frameBudgetNanos = 1_000_000_000L / targetFps.coerceIn(24, 120)
            val now = System.nanoTime()
            if (nextFrameDeadlineNanos == 0L || now - nextFrameDeadlineNanos > frameBudgetNanos * 2) {
                nextFrameDeadlineNanos = frameStart + frameBudgetNanos
            } else {
                nextFrameDeadlineNanos += frameBudgetNanos
            }
            val remainingNanos = nextFrameDeadlineNanos - now
            if (remainingNanos > 1_200_000L) {
                val sleepMs = ((remainingNanos - 800_000L) / 1_000_000L).coerceIn(1L, 38L)
                try {
                    Thread.sleep(sleepMs)
                } catch (_: InterruptedException) {
                }
            }
        } else {
            nextFrameDeadlineNanos = 0L
        }
    }

    private fun isVisibleInViewCone(
        wx: Float,
        wz: Float,
        camX: Float,
        camZ: Float,
        camFwdX: Float,
        camFwdZ: Float,
        maxDistSq: Float,
        behindMargin: Float
    ): Boolean {
        val dx = wx - camX
        val dz = wz - camZ
        val distSq = dx * dx + dz * dz
        if (distSq > maxDistSq) return false
        val forwardDot = dx * camFwdX + dz * camFwdZ
        if (forwardDot < -behindMargin) return false
        // True horizontal frustum culling (skips objects outside left/right viewport edges for 120 FPS)
        if (distSq > behindMargin * behindMargin * 2.2f) {
            val rightDot = dx * (-camFwdZ) + dz * camFwdX
            val maxSide = (if (forwardDot > 0f) forwardDot else 0f) * curTanHalfFovX + behindMargin * 1.45f
            if (abs(rightDot) > maxSide) return false
        }
        return true
    }

    private fun projectDamagePopupsLocked() {
        val popups = engine.damagePopups3D
        if (popups.isEmpty()) return
        for (i in popups.indices) {
            val dp = popups[i]
            projVecIn[0] = dp.x
            projVecIn[1] = dp.y
            projVecIn[2] = dp.z
            projVecIn[3] = 1f
            Matrix.multiplyMV(projVecOut, 0, vpMatrix, 0, projVecIn, 0)
            val w = projVecOut[3]
            if (w > 0.25f) {
                val invW = 1f / w
                val sx = projVecOut[0] * invW * 0.5f + 0.5f
                val sy = 0.5f - projVecOut[1] * invW * 0.5f
                if (sx in -0.05f..1.05f && sy in -0.05f..1.05f) {
                    dp.screenX = sx
                    dp.screenY = sy
                    dp.screenScale = (16f / w.coerceIn(7f, 46f)).coerceIn(0.68f, 1.32f)
                    dp.screenVisible = true
                } else {
                    dp.screenVisible = false
                }
            } else {
                dp.screenVisible = false
            }
        }
    }

    private fun renderWorldLocked() {
        val settings = engine.settings
        val lowEnd = settings.lowEndMode
        val car = engine.activeVehicle
        val isFreeCam = engine.cameraMode == CameraMode.FREE_CAM
        val isDriving = car != null && !isFreeCam
        val isFirstPerson = !isFreeCam && !isDriving && engine.cameraMode == CameraMode.FIRST_PERSON

        val baseFov = settings.fovDegrees.coerceIn(55f, 105f)
        val adsFov = (baseFov * 0.58f).coerceIn(32f, 62f)
        val fov = when {
            isFreeCam -> (baseFov + 4f).coerceAtMost(105f)
            isDriving -> (baseFov + 3f + (kotlin.math.abs(car!!.speed) / 42f) * 11f).coerceAtMost(112f)
            engine.aimBlend > 0.01f -> baseFov + (adsFov - baseFov) * engine.aimBlend
            else -> baseFov
        }
        val renderDist = if (lowEnd) settings.renderDistance.coerceIn(90f, 185f) else settings.renderDistance.coerceIn(90f, 320f)
        val farPlane = kotlin.math.max(480f, renderDist * 1.75f)
        Matrix.perspectiveM(projMatrix, 0, fov, surfaceAspect, 0.15f, farPlane)

        val yawRad = engine.playerYaw * PI.toFloat() / 180f
        val pitchRad = engine.playerPitch * PI.toFloat() / 180f
        val fwdX = sin(yawRad) * cos(pitchRad)
        val fwdY = sin(pitchRad)
        val fwdZ = -cos(yawRad) * cos(pitchRad)

        val shakeAmt = if (settings.cameraShakeEnabled && !isFreeCam) engine.screenShake * 0.22f else 0f
        val shakeX = if (shakeAmt > 0.001f) (Random.nextFloat() - 0.5f) * shakeAmt else 0f
        val shakeY = if (shakeAmt > 0.001f) (Random.nextFloat() - 0.5f) * shakeAmt else 0f

        val camX: Float
        val camY: Float
        val camZ: Float
        val lookX: Float
        val lookY: Float
        val lookZ: Float

        val camLimit = GameEngine.ARENA_HALF_SIZE - 1.6f
        if (isFreeCam) {
            val fcYawRad = engine.freeCamYaw * PI.toFloat() / 180f
            val fcPitchRad = engine.freeCamPitch * PI.toFloat() / 180f
            val fcFwdX = sin(fcYawRad) * cos(fcPitchRad)
            val fcFwdY = sin(fcPitchRad)
            val fcFwdZ = -cos(fcYawRad) * cos(fcPitchRad)
            camX = engine.freeCamX
            camY = engine.freeCamY
            camZ = engine.freeCamZ
            lookX = camX + fcFwdX * 14f
            lookY = camY + fcFwdY * 14f
            lookZ = camZ + fcFwdZ * 14f
        } else if (isDriving) {
            // Free 360-degree vehicle orbit camera preserving user's chosen yaw & pitch without forced snap-back!
            val orbitYawRad = (car!!.yaw + engine.vehicleCamOrbitYaw) * PI.toFloat() / 180f
            val orbitPitchRad = engine.vehicleCamOrbitPitch.coerceIn(-8f, 65f) * PI.toFloat() / 180f
            val orbitDist = 7.2f + car.type.halfL * 0.60f + (kotlin.math.abs(car.speed) * 0.025f)
            val horizDist = orbitDist * cos(orbitPitchRad)
            val vertOffset = orbitDist * sin(orbitPitchRad)
            val backX = -sin(orbitYawRad)
            val backZ = cos(orbitYawRad)
            val clampedX = (car.x + backX * horizDist).coerceIn(-camLimit, camLimit)
            val clampedZ = (car.z + backZ * horizDist).coerceIn(-camLimit, camLimit)
            val minCamY = engine.getSurfaceHeightAt(clampedX, clampedZ, car.y + 4.2f) + 0.72f
            camX = clampedX + shakeX
            camY = (car.y + 1.68f + vertOffset + car.suspensionOffset * 0.45f).coerceAtLeast(minCamY) + shakeY
            camZ = clampedZ
            lookX = car.x + sin(orbitYawRad) * 2.4f
            lookY = car.y + 1.48f + car.suspensionOffset * 0.45f
            lookZ = car.z - cos(orbitYawRad) * 2.4f
        } else if (isFirstPerson) {
            val bobY = if (engine.playerGrounded) sin(engine.playerWalkCycle * 2f) * 0.04f else 0f
            camX = engine.playerX + shakeX
            camY = engine.playerY + GameEngine.PLAYER_EYE_HEIGHT + bobY + engine.landingCameraDip + shakeY
            camZ = engine.playerZ
            lookX = camX + fwdX * 12f
            lookY = camY + fwdY * 12f
            lookZ = camZ + fwdZ * 12f
        } else {
            val shoulderDist = 5.2f - engine.aimBlend * 2.3f
            val rightX = cos(yawRad)
            val rightZ = sin(yawRad)
            val rawCamX = (engine.playerX - fwdX * shoulderDist + rightX * 0.65f).coerceIn(-camLimit, camLimit)
            val rawCamZ = (engine.playerZ - fwdZ * shoulderDist + rightZ * 0.65f).coerceIn(-camLimit, camLimit)
            val surfaceFloor = engine.getSurfaceHeightAt(rawCamX, rawCamZ, engine.playerY + 2.6f) + 0.65f
            val rawCamY = (engine.playerY + 2.38f + engine.landingCameraDip * 0.6f - fwdY * 2.2f)
                .coerceAtLeast(kotlin.math.max(engine.playerY + 0.85f, surfaceFloor))
            camX = rawCamX + shakeX
            camY = rawCamY + shakeY
            camZ = rawCamZ
            lookX = engine.playerX + fwdX * 14f + rightX * 0.35f
            lookY = engine.playerY + 1.48f + fwdY * 12f
            lookZ = engine.playerZ + fwdZ * 14f + rightZ * 0.35f
        }

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // Project active 3D floating damage numbers into normalized 2D viewport coordinates!
        projectDamagePopupsLocked()

        // Horizontal camera forward unit vector + horizontal FOV tangent for fast frustum culling
        val flatFwdX = lookX - camX
        val flatFwdZ = lookZ - camZ
        val flatLen = sqrt(flatFwdX * flatFwdX + flatFwdZ * flatFwdZ).coerceAtLeast(0.001f)
        val camFwdX = flatFwdX / flatLen
        val camFwdZ = flatFwdZ / flatLen
        curTanHalfFovX = tan((fov * 0.5f * PI / 180.0).toFloat()) * surfaceAspect.coerceIn(0.6f, 2.8f) * 1.18f

        val map = engine.activeMap
        if (loadedMap != map) {
            groundMesh.release()
            skyDomeMesh.release()
            mountainMesh.release()
            groundMesh = MeshFactory.createArenaGround(map, 152f, 4.0f)
            skyDomeMesh = MeshFactory.createSkyDome(map, 380f, 10, 22)
            mountainMesh = MeshFactory.createMountainRing(map, 260f, 34)
            GLES20.glClearColor(map.fogR, map.fogG, map.fogB, 1f)
            loadedMap = map
        }
        val fogR = map.fogR
        val fogG = map.fogG
        val fogB = map.fogB
        val fogStart = renderDist * 0.44f
        val fogRange = renderDist * 0.92f
        val propCullSq = (renderDist + 16f) * (renderDist + 16f)
        val highDetailDistSq = if (lowEnd) 36f * 36f else (renderDist * 0.56f) * (renderDist * 0.56f)
        val particleCullSq = (renderDist * if (lowEnd) 0.55f else 0.78f).let { it * it }

        // 1. Draw Atmospheric Sky Dome & Distant Snow-Capped Mountains
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glUseProgram(colorProgram)
        GLES20.glUniform3f(colorCamPosLoc, camX, camY, camZ)
        GLES20.glUniform3f(colorFogColorLoc, fogR, fogG, fogB)
        GLES20.glUniform1f(colorFogStartLoc, fogStart)
        GLES20.glUniform1f(colorFogRangeLoc, fogRange)
        GLES20.glUniform1f(colorFogEnableLoc, 0f)
        GLES20.glUniform1f(colorDetailEnableLoc, 0f)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, camX, camY - 14f, camZ)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(colorMvpLoc, 1, false, mvpMatrix, 0)
        skyDomeMesh.draw(colorPosAttr, colorColAttr)

        // Distant horizon mountains (softly fog-blended)
        if (settings.skyDetailsEnabled && !lowEnd) {
            GLES20.glUniform1f(colorFogEnableLoc, 1f)
            GLES20.glUniformMatrix4fv(colorMvpLoc, 1, false, vpMatrix, 0)
            mountainMesh.draw(colorPosAttr, colorColAttr)
        }

        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        // 2. Draw Rich Multi-Zone Arena Ground with Seamless Distance Fog (micro-grain skipped in LVP mode)
        GLES20.glUseProgram(colorProgram)
        GLES20.glUniform3f(colorCamPosLoc, camX, camY, camZ)
        GLES20.glUniform3f(colorFogColorLoc, fogR, fogG, fogB)
        GLES20.glUniform1f(colorFogStartLoc, fogStart)
        GLES20.glUniform1f(colorFogRangeLoc, fogRange)
        GLES20.glUniform1f(colorFogEnableLoc, 1f)
        GLES20.glUniform1f(colorDetailEnableLoc, if (lowEnd) 0f else 1f)
        GLES20.glUniformMatrix4fv(colorMvpLoc, 1, false, vpMatrix, 0)
        groundMesh.draw(colorPosAttr, colorColAttr)

        // 3. Prepare Hemisphere + Directional Lit 3D Program for the unified MAIN_ARENA (shared with GLB shader)
        when (map) {
            GameMap.MAIN_ARENA -> {
                curLightDirX = 0.48f; curLightDirY = 0.80f; curLightDirZ = 0.36f
                curHemiSkyR = 0.60f; curHemiSkyG = 0.72f; curHemiSkyB = 0.88f
                curHemiGndR = 0.32f; curHemiGndG = 0.38f; curHemiGndB = 0.30f
                curSunR = 1.08f; curSunG = 0.99f; curSunB = 0.90f
            }
            GameMap.TURBO_SPEEDWAY -> {
                curLightDirX = 0.62f; curLightDirY = 0.66f; curLightDirZ = -0.42f
                curHemiSkyR = 0.74f; curHemiSkyG = 0.64f; curHemiSkyB = 0.78f
                curHemiGndR = 0.36f; curHemiGndG = 0.32f; curHemiGndB = 0.28f
                curSunR = 1.14f; curSunG = 0.92f; curSunB = 0.76f
            }
            GameMap.TACTICAL_COMPOUND -> {
                curLightDirX = -0.42f; curLightDirY = 0.84f; curLightDirZ = 0.34f
                curHemiSkyR = 0.56f; curHemiSkyG = 0.64f; curHemiSkyB = 0.76f
                curHemiGndR = 0.30f; curHemiGndG = 0.32f; curHemiGndB = 0.34f
                curSunR = 1.02f; curSunG = 1.02f; curSunB = 0.98f
            }
        }
        curFogR = fogR; curFogG = fogG; curFogB = fogB
        curFogStart = fogStart; curFogRange = fogRange
        curCamX = camX; curCamY = camY; curCamZ = camZ

        ensureLitProgram()
        GLES20.glUniform3f(litLightDirLoc, curLightDirX, curLightDirY, curLightDirZ)
        GLES20.glUniform3f(litHemiSkyLoc, curHemiSkyR, curHemiSkyG, curHemiSkyB)
        GLES20.glUniform3f(litHemiGroundLoc, curHemiGndR, curHemiGndG, curHemiGndB)
        GLES20.glUniform3f(litSunColorLoc, curSunR, curSunG, curSunB)
        GLES20.glUniform3f(litFogColorLoc, fogR, fogG, fogB)
        GLES20.glUniform1f(litFogStartLoc, fogStart)
        GLES20.glUniform1f(litFogRangeLoc, fogRange)
        setCameraPosUniform(camX, camY, camZ)
        setUnlit(false)

        // 4. Draw Radiant Double-Halo Sun & Volumetric Multi-Puff Drifting Clouds
        setUnlit(true)
        GLES20.glDepthMask(false)
        if (!lowEnd) {
            drawMesh(sphereMesh, 150f, 155f, -195f, 0f, 26f, 26f, 26f, 1f, 0.92f, 0.62f, 0.28f)
        }
        drawMesh(sphereMesh, 150f, 155f, -195f, 0f, 14f, 14f, 14f, 1f, 0.98f, 0.82f, 1f)
        GLES20.glDepthMask(true)

        if (settings.skyDetailsEnabled && !lowEnd) {
            val drift = (engine.worldTime * 0.85f) % 300f
            for (i in clouds.indices) {
                val c = clouds[i]
                val cx = ((c[0] + drift + 150f) % 300f) - 150f
                drawMesh(sphereMesh, cx, c[1], c[2], 0f, c[3] * 0.55f, 2.8f, c[4] * 0.55f, 0.98f, 0.99f, 1f, 0.92f)
                drawMesh(sphereMesh, cx + c[3] * 0.32f, c[1] + 1.1f, c[2] - 0.6f, 0f, c[3] * 0.40f, 2.4f, c[4] * 0.42f, 1f, 1f, 1f, 0.92f)
                drawMesh(sphereMesh, cx - c[3] * 0.30f, c[1] + 0.5f, c[2] + 0.7f, 0f, c[3] * 0.36f, 2.0f, c[4] * 0.38f, 0.95f, 0.97f, 1f, 0.90f)
            }
        }
        setUnlit(false)

        // 5. Draw Perimeter Walls, Watchtowers, Pine Trees & Street Lamps (view-cone + distance culled)
        entityRenderer.drawArenaEnvironment(
            camX = camX,
            camZ = camZ,
            camFwdX = camFwdX,
            camFwdZ = camFwdZ,
            renderDist = renderDist,
            worldTime = engine.worldTime,
            lowEnd = lowEnd,
            map = map
        )

        // 6. Cull & Bucket World Props + Vehicles by .glb model type (zero-GC batching + tiered distance culling!)
        val drawShadows = settings.shadowsEnabled && !lowEnd
        val smallPropCull = renderDist * if (lowEnd) 0.48f else 0.62f
        val smallPropCullSq = smallPropCull * smallPropCull
        val medPropCull = renderDist * 0.85f
        val medPropCullSq = medPropCull * medPropCull

        val props = engine.props
        ensurePropBatchCapacity(props.size)
        propBucketHead.fill(-1)
        var hasPropShadows = false
        for (i in props.indices) {
            val prop = props[i]
            if (prop.destroyed) continue
            val cullSq = when {
                prop.boundRadius <= 1.25f -> smallPropCullSq
                prop.boundRadius <= 3.0f -> medPropCullSq
                else -> propCullSq
            }
            if (!isVisibleInViewCone(prop.x, prop.z, camX, camZ, camFwdX, camFwdZ, cullSq, prop.boundRadius + 6f)) {
                continue
            }
            val dx = prop.x - camX
            val dz = prop.z - camZ
            val highDetail = !lowEnd && (dx * dx + dz * dz < highDetailDistSq)
            val shadowY = if (drawShadows && highDetail && entityRenderer.propNeedsContactShadow(prop.type)) {
                hasPropShadows = true
                if (prop.y > 0.1f) engine.getSurfaceHeightAt(prop.x, prop.z, prop.y + 0.1f) + 0.03f else 0.03f
            } else 0.03f
            propShadowYBuf[i] = shadowY
            propHighDetailBuf[i] = highDetail
            val bucket = prop.type.ordinal
            propBucketNext[i] = propBucketHead[bucket]
            propBucketHead[bucket] = i
        }

        val vehicles = engine.vehicles
        ensureVehicleBatchCapacity(vehicles.size)
        vehicleBucketHead.fill(-1)
        var hasVehicleShadows = false
        for (i in vehicles.indices) {
            val v = vehicles[i]
            if (v !== car && !isVisibleInViewCone(v.x, v.z, camX, camZ, camFwdX, camFwdZ, propCullSq, 6f)) {
                continue
            }
            val dx = v.x - camX
            val dz = v.z - camZ
            val highDetail = (v === car) || (!lowEnd && (dx * dx + dz * dz < highDetailDistSq))
            val shadowY = if (drawShadows && v.loopPropId == null) {
                hasVehicleShadows = true
                if (v.y <= 0.12f) 0.03f else engine.getSurfaceHeightAt(v.x, v.z, v.y + 0.25f) + 0.03f
            } else 0.03f
            vehicleShadowYBuf[i] = shadowY
            vehicleHighDetailBuf[i] = highDetail
            val bucket = v.type.ordinal
            vehicleBucketNext[i] = vehicleBucketHead[bucket]
            vehicleBucketHead[bucket] = i
        }

        // 6A. Batched Contact Shadow Pass (single litProgram + discMesh binding for all props & vehicles!)
        if (drawShadows && (hasPropShadows || hasVehicleShadows)) {
            setUnlit(true)
            setDepthMask(false)
            if (hasPropShadows) {
                for (b in propBucketHead.indices) {
                    var idx = propBucketHead[b]
                    if (idx < 0) continue
                    val pType = props[idx].type
                    if (!entityRenderer.propNeedsContactShadow(pType)) continue
                    while (idx >= 0) {
                        if (propHighDetailBuf[idx]) {
                            val prop = props[idx]
                            val sY = propShadowYBuf[idx]
                            val heightAbove = (prop.y - (sY - 0.03f)).coerceAtLeast(0f)
                            val shadowScale = (1f - (heightAbove * 0.06f).coerceIn(0f, 0.55f))
                            val alpha = 0.34f * shadowScale
                            if (alpha > 0.02f) {
                                drawMesh(
                                    discMesh, prop.x, sY, prop.z, prop.yaw,
                                    prop.type.halfW * 2.25f * shadowScale, 1f, prop.type.halfD * 2.25f * shadowScale,
                                    0.04f, 0.07f, 0.12f, alpha, spec = 0f
                                )
                            }
                        }
                        idx = propBucketNext[idx]
                    }
                }
            }
            if (hasVehicleShadows) {
                for (b in vehicleBucketHead.indices) {
                    var idx = vehicleBucketHead[b]
                    while (idx >= 0) {
                        val v = vehicles[idx]
                        if (v.loopPropId == null) {
                            val sY = vehicleShadowYBuf[idx]
                            val heightAbove = (v.y - (sY - 0.03f)).coerceAtLeast(0f)
                            val shadowFade = (1f - (heightAbove * 0.06f).coerceIn(0f, 0.6f))
                            val alpha = 0.46f * shadowFade
                            if (alpha > 0.02f) {
                                drawMesh(
                                    discMesh, v.x, sY, v.z, v.yaw,
                                    v.type.halfW * 2.55f * shadowFade, 1f, v.type.halfL * 2.35f * shadowFade,
                                    0.04f, 0.07f, 0.12f, alpha, spec = 0f
                                )
                            }
                        }
                        idx = vehicleBucketNext[idx]
                    }
                }
            }
            setDepthMask(true)
            setUnlit(false)
        }

        // 6B. Batched World Props Pass (grouped by PropType -> identical .glb VBO/IBO/texture drawn back-to-back!)
        for (b in propBucketHead.indices) {
            var idx = propBucketHead[b]
            while (idx >= 0) {
                entityRenderer.drawWorldProp(
                    prop = props[idx],
                    shadowY = propShadowYBuf[idx],
                    highDetail = propHighDetailBuf[idx],
                    worldTime = engine.worldTime,
                    drawShadow = false
                )
                idx = propBucketNext[idx]
            }
        }

        // 7. Batched Drivable Vehicles Pass (grouped by VehicleType)
        for (b in vehicleBucketHead.indices) {
            var idx = vehicleBucketHead[b]
            while (idx >= 0) {
                entityRenderer.drawVehicle(
                    v = vehicles[idx],
                    worldTime = engine.worldTime,
                    shadowY = vehicleShadowYBuf[idx],
                    highDetail = vehicleHighDetailBuf[idx],
                    drawShadow = false
                )
                idx = vehicleBucketNext[idx]
            }
        }

        // 8. Draw Local Player Animated Khryak-Napoleon (.glb) + Attached Weapon (.glb)
        val weaponSkin = settings.weaponSkin
        val playerSeated = car != null
        if (engine.playerAlive && (!isFirstPerson || playerSeated || isFreeCam)) {
            val skin = settings.selectedSkin
            val px = if (playerSeated) car!!.x else engine.playerX
            val py = if (playerSeated) car!!.y else engine.playerY
            val pz = if (playerSeated) car!!.z else engine.playerZ
            val pyaw = if (playerSeated) car!!.yaw else engine.playerYaw
            val pShadowY = if (!drawShadows || playerSeated) 0.03f else if (py <= 0.15f) 0.03f else engine.getSurfaceHeightAt(px, pz, py + 0.25f) + 0.03f
            val pMoveSpeed = sqrt(engine.playerVx * engine.playerVx + engine.playerVz * engine.playerVz)
            val reloadProg = if (engine.reloadTimer > 0f && engine.currentWeapon.reloadSec > 0.01f) {
                (1f - engine.reloadTimer / engine.currentWeapon.reloadSec).coerceIn(0f, 1f)
            } else 0f
            entityRenderer.drawCombatChicken(
                x = px, y = py, z = pz, yaw = pyaw,
                walkCycle = if (playerSeated) 0f else engine.playerWalkCycle,
                weapon = if (playerSeated) null else engine.currentWeapon,
                muzzleFlash = engine.muzzleFlashTimer > 0f,
                hitFlash = engine.damageVignetteAlpha * 0.5f,
                accentR = skin.vestR, accentG = skin.vestG, accentB = skin.vestB,
                seated = playerSeated,
                seatedVehicle = car,
                shieldActive = !playerSeated && engine.spawnProtectionTimer > 0.05f,
                shadowY = pShadowY,
                highDetail = !lowEnd,
                skin = skin,
                worldTime = engine.worldTime,
                weaponSkin = weaponSkin,
                grounded = engine.playerGrounded,
                moveSpeed = pMoveSpeed,
                rpgRocketLoaded = engine.rpgRocketLoaded,
                weaponRecoil = engine.weaponRecoil,
                reloadProgress = reloadProg,
                onMuzzleWorld = muzzleWorldCallback
            )
        }

        // 9. Draw Combat Bots (Animated Khryak-Napoleon .glb + Weapon .glb)
        val bots = engine.bots
        for (i in bots.indices) {
            val bot = bots[i]
            if (!bot.alive) continue
            if (!isVisibleInViewCone(bot.x, bot.z, camX, camZ, camFwdX, camFwdZ, propCullSq, 3.5f)) continue
            val dx = bot.x - camX
            val dz = bot.z - camZ
            val highDetail = !lowEnd && (dx * dx + dz * dz < highDetailDistSq)
            val botShadowY = if (!drawShadows || bot.y <= 0.15f) 0.03f else engine.getSurfaceHeightAt(bot.x, bot.z, bot.y + 0.25f) + 0.03f
            val botSpd = sqrt(bot.vx * bot.vx + bot.vz * bot.vz)
            entityRenderer.drawCombatChicken(
                x = bot.x, y = bot.y, z = bot.z, yaw = bot.yaw,
                walkCycle = bot.walkCycle,
                weapon = if (engine.peacefulMode) null else bot.weapon,
                muzzleFlash = bot.muzzleFlash > 0f,
                hitFlash = bot.hitFlash,
                accentR = bot.accentR, accentG = bot.accentG, accentB = bot.accentB,
                seated = false,
                shadowY = botShadowY,
                highDetail = highDetail,
                worldTime = engine.worldTime + i * 0.37f,
                weaponSkin = weaponSkin,
                grounded = bot.y <= botShadowY + 0.25f,
                moveSpeed = botSpd,
                rpgRocketLoaded = true
            )
            if (!engine.peacefulMode) {
                drawOverheadHealthBar(bot.x, bot.y + 2.48f, bot.z, bot.hp / 100f, camX, camZ)
            }
        }

        // 9b. Draw Living NPC Хряки-Бургеры (`pig_burger_animated.glb`)
        val pigs = engine.pigBurgers
        for (i in pigs.indices) {
            val pig = pigs[i]
            if (!isVisibleInViewCone(pig.x, pig.z, camX, camZ, camFwdX, camFwdZ, propCullSq, 3.5f)) continue
            val pigShadowY = if (!drawShadows || pig.y <= 0.15f) 0.03f else engine.getSurfaceHeightAt(pig.x, pig.z, pig.y + 0.25f) + 0.03f
            entityRenderer.drawPigBurger(pig, pigShadowY, !lowEnd)
        }

        // 10. Draw Online Multiplayer Players
        for (rp in engine.remotePlayers.values) {
            if (rp.hp <= 0) continue
            if (!isVisibleInViewCone(rp.x, rp.z, camX, camZ, camFwdX, camFwdZ, propCullSq, 3.5f)) continue
            val dx = rp.x - camX
            val dz = rp.z - camZ
            val highDetail = !lowEnd && (dx * dx + dz * dz < highDetailDistSq)
            val rpShadowY = if (!drawShadows || rp.y <= 0.15f) 0.03f else engine.getSurfaceHeightAt(rp.x, rp.z, rp.y + 0.25f) + 0.03f
            entityRenderer.drawCombatChicken(
                x = rp.x, y = rp.y, z = rp.z, yaw = rp.yaw,
                walkCycle = rp.walkCycle,
                weapon = rp.weapon,
                muzzleFlash = rp.shooting || rp.muzzleFlashTimer > 0f,
                hitFlash = rp.hitFlash,
                accentR = 0.16f, accentG = 0.78f, accentB = 0.98f,
                seated = rp.drivingVehicle,
                shadowY = rpShadowY,
                highDetail = highDetail,
                worldTime = engine.worldTime,
                weaponSkin = weaponSkin,
                grounded = true,
                moveSpeed = 3.5f,
                rpgRocketLoaded = true
            )
            drawOverheadHealthBar(rp.x, rp.y + 2.48f, rp.z, rp.hp / 100f, camX, camZ)
        }

        // 11. Draw Velocity-Oriented Bullets, Rockets, Explosions & 3D Particles
        setUnlit(true)
        val projectiles = engine.projectiles
        for (i in projectiles.indices) {
            val p = projectiles[i]
            val projYaw = (atan2(p.vx, -p.vz) * 180f / PI.toFloat())
            val horizSpeed = sqrt(p.vx * p.vx + p.vz * p.vz).coerceAtLeast(0.01f)
            val projPitch = (atan2(p.vy, horizSpeed) * 180f / PI.toFloat())

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, p.x, p.y, p.z)
            Matrix.rotateM(modelMatrix, 0, -projYaw, 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, projPitch, 1f, 0f, 0f)

            if (p.explosive) {
                Matrix.rotateM(modelMatrix, 0, -90f, 1f, 0f, 0f)
                Matrix.scaleM(modelMatrix, 0, 0.34f, 0.85f, 0.34f)
                drawCurrentModel(coneMesh, 0.95f, 0.35f, 0.10f, 1f, 0f)
                drawMesh(sphereMesh, p.x, p.y, p.z, 0f, 0.24f, 0.24f, 0.24f, 1f, 0.82f, 0.22f, 1f)
            } else {
                val r = 1f
                val g = if (p.fromPlayer) 0.94f else 0.24f
                val b = if (p.fromPlayer) 0.36f else 0.18f
                Matrix.scaleM(modelMatrix, 0, 0.10f, 0.10f, 0.68f)
                drawCurrentModel(cubeMesh, r, g, b, 1f, 0f)
            }
        }

        val particles = engine.particles
        for (i in particles.indices) {
            val pt = particles[i]
            if (!isVisibleInViewCone(pt.x, pt.z, camX, camZ, camFwdX, camFwdZ, particleCullSq, 2f)) continue
            val alpha = (1f - pt.age / pt.maxAge).coerceIn(0f, 1f)
            val s = pt.size * (0.65f + (1f - alpha) * 0.6f)
            drawMesh(cubeMesh, pt.x, pt.y, pt.z, engine.worldTime * 190f, s, s, s, pt.r, pt.g, pt.b, alpha)
        }

        setDepthMask(false)
        val explosions = engine.explosions
        for (i in explosions.indices) {
            val exp = explosions[i]
            val t = (exp.age / exp.maxAge).coerceIn(0f, 1f)
            val outerR = exp.maxRadius * (0.25f + t * 0.88f)
            val innerR = outerR * 0.56f
            val alpha = (1f - t) * 0.85f
            drawMesh(sphereMesh, exp.x, exp.y, exp.z, 0f, outerR, outerR, outerR, 1f, 0.40f, 0.06f, alpha)
            drawMesh(sphereMesh, exp.x, exp.y + 0.3f, exp.z, 0f, innerR, innerR, innerR, 1f, 0.94f, 0.38f, alpha)
            if (!lowEnd) {
                val shockY = engine.getSurfaceHeightAt(exp.x, exp.z, exp.y + 0.5f) + 0.08f
                drawMesh(discMesh, exp.x, shockY, exp.z, 0f, outerR * 2.4f, 1f, outerR * 2.4f, 1f, 0.72f, 0.22f, alpha * 0.45f)
            }
        }

        // 12. Draw Translucent Sandbox Placement Ghost Preview
        if (engine.buildModeOpen && !playerSeated && !isFreeCam && engine.playerAlive) {
            entityRenderer.drawPlacementGhost(engine)
        }
        ensureLitProgram()
        setDepthMask(true)
        setUnlit(false)

        // 13. Draw First-Person 3D Weapon Viewmodel (crisp foreground, zero world fog, never duplicated in 3P/FreeCam/Car!)
        if (isFirstPerson && engine.playerAlive) {
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
            entityRenderer.drawFirstPersonWeaponViewmodel(engine, surfaceAspect)
        }
        restoreStandardGlState()
    }

    internal fun setDepthMask(enabled: Boolean) {
        if (enabled != lastDepthMask) {
            GLES20.glDepthMask(enabled)
            lastDepthMask = enabled
        }
    }

    internal fun drawShadowDisc(x: Float, y: Float, z: Float, sx: Float, sz: Float, alpha: Float, yawDeg: Float = 0f) {
        if (!engine.settings.shadowsEnabled || engine.settings.lowEndMode || alpha <= 0.02f) return
        setUnlit(true)
        setDepthMask(false)
        drawMesh(discMesh, x, y, z, yawDeg, sx, 1f, sz, 0.04f, 0.07f, 0.12f, alpha, spec = 0f)
        setDepthMask(true)
        setUnlit(false)
    }

    private fun drawOverheadHealthBar(x: Float, y: Float, z: Float, hpFrac: Float, camX: Float, camZ: Float) {
        val clamped = hpFrac.coerceIn(0f, 1f)
        val angleToCam = (atan2(camX - x, camZ - z) * 180f / PI.toFloat())
        setUnlit(true)
        drawBox(x, y, z, -angleToCam, 1.25f, 0.16f, 0.05f, 0.12f, 0.12f, 0.16f, 0.85f)
        if (clamped > 0.01f) {
            val r = if (clamped > 0.45f) 0.18f else 0.95f
            val g = if (clamped > 0.45f) 0.90f else 0.22f
            val b = 0.22f
            drawBox(x, y, z, -angleToCam, 1.18f * clamped, 0.11f, 0.07f, r, g, b, 1f)
        }
        setUnlit(false)
    }

    internal fun restoreStandardGlState() {
        if (activeProgram == texLitProgram && texUvAttr >= 0) {
            GLES20.glDisableVertexAttribArray(texUvAttr)
        }
        if (!lastDepthMask) {
            GLES20.glDepthMask(true)
            lastDepthMask = true
        }
        if (!lastBlendEnabled) {
            GLES20.glEnable(GLES20.GL_BLEND)
            lastBlendEnabled = true
        }
        if (!lastCullEnabled) {
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            lastCullEnabled = true
        }
        if (lastFrontFaceCw) {
            GLES20.glFrontFace(GLES20.GL_CCW)
            lastFrontFaceCw = false
        }
        GlBufferState.reset()
    }

    internal fun ensureLitProgram() {
        if (activeProgram != litProgram) {
            restoreStandardGlState()
            GLES20.glUseProgram(litProgram)
            activeProgram = litProgram
            GlBufferState.reset()
            lastUploadedUnlit = -1
            lastSpec = -1f
            lastColR = -1f
        }
    }

    private fun ensureTexLitProgram() {
        if (activeProgram != texLitProgram) {
            GLES20.glUseProgram(texLitProgram)
            activeProgram = texLitProgram
            GlBufferState.reset()
            lastBoundTexId = -1
            lastTexColR = -1f
            lastTexEmR = -1f
            lastTexUnlit = -1
            lastTexSpec = -1f
            lastTexAlphaMode = -1f
            lastTexAlphaCutoff = -1f
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glUniform1i(texSamplerLoc, 0)
            if (!texFrameUniformsSynced) {
                texFrameUniformsSynced = true
                GLES20.glUniform3f(texLightDirLoc, curLightDirX, curLightDirY, curLightDirZ)
                GLES20.glUniform3f(texHemiSkyLoc, curHemiSkyR, curHemiSkyG, curHemiSkyB)
                GLES20.glUniform3f(texHemiGroundLoc, curHemiGndR, curHemiGndG, curHemiGndB)
                GLES20.glUniform3f(texSunColorLoc, curSunR, curSunG, curSunB)
                GLES20.glUniform3f(texFogColorLoc, curFogR, curFogG, curFogB)
                GLES20.glUniform1f(texFogStartLoc, curFogStart)
                GLES20.glUniform1f(texFogRangeLoc, curFogRange)
            }
            GLES20.glUniform3f(texCamPosLoc, curCamX, curCamY, curCamZ)
        }
    }

    internal fun drawGlbPrimitive(
        prim: GlbGpuPrimitive,
        worldMat: FloatArray,
        textureId: Int,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        emissiveR: Float,
        emissiveG: Float,
        emissiveB: Float,
        spec: Float,
        doubleSided: Boolean,
        unlit: Boolean,
        alphaMode: GlbAlphaMode = GlbAlphaMode.OPAQUE,
        alphaCutoff: Float = 0.5f
    ) {
        val isIndexed = prim.iboId != 0 && prim.indexCount > 0
        if (prim.vboId == 0 || (!isIndexed && prim.vertexCount <= 0)) return
        val curUploads = glbManager.gpuUploadCount
        if (curUploads != lastObservedGlbUploads) {
            lastObservedGlbUploads = curUploads
            lastBoundTexId = -1
            GlBufferState.reset()
        }
        ensureTexLitProgram()

        // Proper OpenGL ES 2.0 blend, depth-write, culling & winding state per material
        val isBlend = alphaMode == GlbAlphaMode.BLEND
        if (isBlend != lastBlendEnabled) {
            if (isBlend) {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            } else {
                GLES20.glDisable(GLES20.GL_BLEND)
            }
            lastBlendEnabled = isBlend
        }
        val wantDepthMask = !isBlend
        if (wantDepthMask != lastDepthMask) {
            GLES20.glDepthMask(wantDepthMask)
            lastDepthMask = wantDepthMask
        }

        val wantCull = !doubleSided
        if (wantCull != lastCullEnabled) {
            if (wantCull) GLES20.glEnable(GLES20.GL_CULL_FACE) else GLES20.glDisable(GLES20.GL_CULL_FACE)
            lastCullEnabled = wantCull
        }

        // Handle negative-determinant mirrored transforms cleanly so winding never turns inside-out
        val m0 = worldMat[0]; val m1 = worldMat[1]; val m2 = worldMat[2]
        val m4 = worldMat[4]; val m5 = worldMat[5]; val m6 = worldMat[6]
        val m8 = worldMat[8]; val m9 = worldMat[9]; val m10 = worldMat[10]
        val det3 = m0 * (m5 * m10 - m6 * m9) -
            m4 * (m1 * m10 - m2 * m9) +
            m8 * (m1 * m6 - m2 * m5)
        val wantCw = det3 < 0f
        if (wantCw != lastFrontFaceCw) {
            GLES20.glFrontFace(if (wantCw) GLES20.GL_CW else GLES20.GL_CCW)
            lastFrontFaceCw = wantCw
        }

        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, worldMat, 0)
        GLES20.glUniformMatrix4fv(texMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(texModelLoc, 1, false, worldMat, 0)

        if (!unlit) {
            // Fast-path: uniform-scaled rigid transform (95%+ of GLB props/cars/characters) needs no matrix inversion!
            val sx2 = m0 * m0 + m1 * m1 + m2 * m2
            val sy2 = m4 * m4 + m5 * m5 + m6 * m6
            val sz2 = m8 * m8 + m9 * m9 + m10 * m10
            val eps = 0.002f * sx2 + 0.0001f
            val uniformScale = abs(sx2 - sy2) <= eps && abs(sx2 - sz2) <= eps
            if (uniformScale) {
                GLES20.glUniformMatrix4fv(texNormalMatLoc, 1, false, worldMat, 0)
            } else if (Matrix.invertM(invModelMatrix, 0, worldMat, 0)) {
                Matrix.transposeM(normalMatrix, 0, invModelMatrix, 0)
                GLES20.glUniformMatrix4fv(texNormalMatLoc, 1, false, normalMatrix, 0)
            } else {
                GLES20.glUniformMatrix4fv(texNormalMatLoc, 1, false, worldMat, 0)
            }
        }

        // Cached material uniforms: consecutive batched draws of identical GLB primitives skip all 6 glUniform calls!
        if (r != lastTexColR || g != lastTexColG || b != lastTexColB || a != lastTexColA) {
            lastTexColR = r; lastTexColG = g; lastTexColB = b; lastTexColA = a
            GLES20.glUniform4f(texColorLoc, r, g, b, a)
        }
        if (emissiveR != lastTexEmR || emissiveG != lastTexEmG || emissiveB != lastTexEmB) {
            lastTexEmR = emissiveR; lastTexEmG = emissiveG; lastTexEmB = emissiveB
            GLES20.glUniform3f(texEmissiveLoc, emissiveR, emissiveG, emissiveB)
        }
        val unlitFlag = if (unlit) 1 else 0
        if (unlitFlag != lastTexUnlit) {
            lastTexUnlit = unlitFlag
            GLES20.glUniform1f(texUnlitLoc, if (unlit) 1f else 0f)
        }
        if (spec != lastTexSpec) {
            lastTexSpec = spec
            GLES20.glUniform1f(texSpecLoc, spec)
        }
        val alphaModeUniform = when (alphaMode) {
            GlbAlphaMode.OPAQUE -> 0f
            GlbAlphaMode.MASK -> 1f
            GlbAlphaMode.BLEND -> 2f
        }
        if (alphaModeUniform != lastTexAlphaMode) {
            lastTexAlphaMode = alphaModeUniform
            GLES20.glUniform1f(texAlphaModeLoc, alphaModeUniform)
        }
        if (alphaMode == GlbAlphaMode.MASK && alphaCutoff != lastTexAlphaCutoff) {
            lastTexAlphaCutoff = alphaCutoff
            GLES20.glUniform1f(texAlphaCutoffLoc, alphaCutoff)
        }

        if (textureId != lastBoundTexId) {
            lastBoundTexId = textureId
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        }

        // Cached VBO + vertex attribute pointers: skipped completely when drawing batched instances of the same GLB mesh!
        if (GlBufferState.boundGlbVbo != prim.vboId || !GlBufferState.glbAttribsEnabled) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, prim.vboId)
            val strideBytes = 8 * 4
            if (!GlBufferState.glbAttribsEnabled) {
                GLES20.glEnableVertexAttribArray(texPosAttr)
                GLES20.glEnableVertexAttribArray(texNormAttr)
                GLES20.glEnableVertexAttribArray(texUvAttr)
                GlBufferState.glbAttribsEnabled = true
            }
            GLES20.glVertexAttribPointer(texPosAttr, 3, GLES20.GL_FLOAT, false, strideBytes, 0)
            GLES20.glVertexAttribPointer(texNormAttr, 3, GLES20.GL_FLOAT, false, strideBytes, 3 * 4)
            GLES20.glVertexAttribPointer(texUvAttr, 2, GLES20.GL_FLOAT, false, strideBytes, 6 * 4)
            GlBufferState.boundGlbVbo = prim.vboId
            GlBufferState.boundVbo = -1
        }

        if (isIndexed) {
            if (GlBufferState.boundGlbIbo != prim.iboId) {
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, prim.iboId)
                GlBufferState.boundGlbIbo = prim.iboId
            }
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, prim.indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        } else {
            if (GlBufferState.boundGlbIbo != 0) {
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
                GlBufferState.boundGlbIbo = 0
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, prim.vertexCount)
        }
    }

    internal fun setUnlit(unlit: Boolean) {
        ensureLitProgram()
        currentUnlit = unlit
        val flag = if (unlit) 1 else 0
        if (lastUploadedUnlit != flag) {
            lastUploadedUnlit = flag
            GLES20.glUniform1f(litUnlitLoc, if (unlit) 1f else 0f)
        }
    }

    internal fun setCameraPosUniform(x: Float, y: Float, z: Float) {
        curCamX = x; curCamY = y; curCamZ = z
        if (activeProgram == texLitProgram) {
            GLES20.glUniform3f(texCamPosLoc, x, y, z)
        } else {
            ensureLitProgram()
            GLES20.glUniform3f(litCamPosLoc, x, y, z)
        }
    }

    internal fun drawBox(
        x: Float, y: Float, z: Float,
        yawDeg: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float,
        spec: Float = 0.18f
    ) {
        drawMesh(cubeMesh, x, y, z, yawDeg, sx, sy, sz, r, g, b, a, spec)
    }

    internal fun drawMesh(
        mesh: GlMesh,
        x: Float, y: Float, z: Float,
        yawDeg: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float,
        spec: Float = 0.18f
    ) {
        ensureLitProgram()
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (yawDeg != 0f) Matrix.rotateM(modelMatrix, 0, -yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)

        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(litMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(litModelLoc, 1, false, modelMatrix, 0)

        // Analytical inverse-transpose normal matrix for M = T * R * S: (R * S)^-T = R * S^-1
        // Zero 4x4 Matrix.invertM overhead -> huge CPU/FPS boost across hundreds of parts per frame!
        if (!currentUnlit) {
            if (sx == sy && sy == sz) {
                GLES20.glUniformMatrix4fv(litNormalMatLoc, 1, false, modelMatrix, 0)
            } else {
                val invSx2 = if (kotlin.math.abs(sx) > 0.0001f) 1f / (sx * sx) else 1f
                val invSy2 = if (kotlin.math.abs(sy) > 0.0001f) 1f / (sy * sy) else 1f
                val invSz2 = if (kotlin.math.abs(sz) > 0.0001f) 1f / (sz * sz) else 1f
                normalMatrix[0] = modelMatrix[0] * invSx2
                normalMatrix[1] = modelMatrix[1] * invSx2
                normalMatrix[2] = modelMatrix[2] * invSx2
                normalMatrix[3] = 0f
                normalMatrix[4] = modelMatrix[4] * invSy2
                normalMatrix[5] = modelMatrix[5] * invSy2
                normalMatrix[6] = modelMatrix[6] * invSy2
                normalMatrix[7] = 0f
                normalMatrix[8] = modelMatrix[8] * invSz2
                normalMatrix[9] = modelMatrix[9] * invSz2
                normalMatrix[10] = modelMatrix[10] * invSz2
                normalMatrix[11] = 0f
                normalMatrix[12] = 0f
                normalMatrix[13] = 0f
                normalMatrix[14] = 0f
                normalMatrix[15] = 1f
                GLES20.glUniformMatrix4fv(litNormalMatLoc, 1, false, normalMatrix, 0)
            }
        }
        uploadMaterialAndDraw(mesh, r, g, b, a, spec)
    }

    internal fun drawCurrentModel(
        mesh: GlMesh,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        spec: Float = 0.20f,
        skipNormalInvert: Boolean = false
    ) {
        ensureLitProgram()
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(litMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(litModelLoc, 1, false, modelMatrix, 0)
        // Skip expensive 4x4 matrix inversion when unlit or uniform-scaled (big win on low-end CPUs!)
        if (!currentUnlit) {
            if (skipNormalInvert) {
                GLES20.glUniformMatrix4fv(litNormalMatLoc, 1, false, modelMatrix, 0)
            } else if (Matrix.invertM(invModelMatrix, 0, modelMatrix, 0)) {
                Matrix.transposeM(normalMatrix, 0, invModelMatrix, 0)
                GLES20.glUniformMatrix4fv(litNormalMatLoc, 1, false, normalMatrix, 0)
            } else {
                GLES20.glUniformMatrix4fv(litNormalMatLoc, 1, false, modelMatrix, 0)
            }
        }
        uploadMaterialAndDraw(mesh, r, g, b, a, spec)
    }

    private fun uploadMaterialAndDraw(
        mesh: GlMesh,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        spec: Float
    ) {
        ensureLitProgram()
        if (r != lastColR || g != lastColG || b != lastColB || a != lastColA) {
            lastColR = r
            lastColG = g
            lastColB = b
            lastColA = a
            GLES20.glUniform4f(litColorLoc, r, g, b, a)
        }
        if (spec != lastSpec) {
            lastSpec = spec
            GLES20.glUniform1f(litSpecLoc, spec)
        }
        mesh.draw(litPosAttr, litNormAttr)
    }

    private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val LIT_VERTEX_SHADER = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            uniform mat4 uNormalMat;
            attribute vec3 aPos;
            attribute vec3 aNorm;
            varying vec3 vNormal;
            varying vec3 vWorldPos;
            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = normalize((uNormalMat * vec4(aNorm, 0.0)).xyz);
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val LIT_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLightDir;
            uniform vec3 uHemiSkyColor;
            uniform vec3 uHemiGroundColor;
            uniform vec3 uSunColor;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogRange;
            uniform vec3 uCamPos;
            uniform float uUnlit;
            uniform float uSpec;
            varying vec3 vNormal;
            varying vec3 vWorldPos;
            void main() {
                vec3 viewVec = uCamPos - vWorldPos;
                float dist = length(viewVec);
                vec3 litRgb;
                if (uUnlit > 0.5) {
                    litRgb = uColor.rgb;
                } else {
                    vec3 N = normalize(vNormal);
                    vec3 L = normalize(uLightDir);
                    vec3 V = viewVec / max(dist, 0.001);
                    vec3 H = normalize(L + V);

                    // Per-map HemisphereLight + DirectionalLight + Back-Fill + Fresnel Rim
                    float diff = max(dot(N, L), 0.0);
                    float backFill = max(dot(N, vec3(-L.x, 0.35, -L.z)), 0.0) * 0.22;
                    float hemiBlend = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);
                    vec3 ambient = mix(uHemiGroundColor, uHemiSkyColor, hemiBlend);
                    float specAmt = pow(max(dot(N, H), 0.0), 32.0) * uSpec;
                    float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0) * 0.16;

                    litRgb = uColor.rgb * (ambient + uSunColor * diff * 0.74 + uHemiSkyColor * backFill)
                           + uSunColor * (specAmt + rim * 0.9);
                }
                float fog = clamp((dist - uFogStart) / max(30.0, uFogRange), 0.0, 0.80);
                vec3 sunHaze = mix(uFogColor, uSunColor * 0.92, 0.14);
                vec3 finalRgb = mix(litRgb, sunHaze, fog * (1.0 - uUnlit * 0.78));
                gl_FragColor = vec4(finalRgb, uColor.a);
            }
        """

        private const val TEX_LIT_VERTEX_SHADER = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            uniform mat4 uNormalMat;
            attribute vec3 aPos;
            attribute vec3 aNorm;
            attribute vec2 aTexCoord;
            varying vec3 vNormal;
            varying vec3 vWorldPos;
            varying vec2 vTexCoord;
            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = normalize((uNormalMat * vec4(aNorm, 0.0)).xyz);
                vTexCoord = aTexCoord;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val TEX_LIT_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec4 uColor;
            uniform vec3 uEmissive;
            uniform vec3 uLightDir;
            uniform vec3 uHemiSkyColor;
            uniform vec3 uHemiGroundColor;
            uniform vec3 uSunColor;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogRange;
            uniform vec3 uCamPos;
            uniform float uUnlit;
            uniform float uSpec;
            uniform float uAlphaMode;
            uniform float uAlphaCutoff;
            varying vec3 vNormal;
            varying vec3 vWorldPos;
            varying vec2 vTexCoord;
            void main() {
                vec4 texSample = texture2D(uTexture, vTexCoord);
                vec4 baseCol = texSample * uColor;
                float outAlpha = 1.0;
                if (uAlphaMode > 1.5) {
                    if (baseCol.a < 0.01) discard;
                    outAlpha = baseCol.a;
                } else if (uAlphaMode > 0.5) {
                    if (baseCol.a < uAlphaCutoff) discard;
                    outAlpha = 1.0;
                } else {
                    outAlpha = 1.0;
                }
                vec3 viewVec = uCamPos - vWorldPos;
                float dist = length(viewVec);
                vec3 litRgb;
                if (uUnlit > 0.5) {
                    litRgb = baseCol.rgb + uEmissive;
                } else {
                    vec3 N = normalize(vNormal);
                    if (!gl_FrontFacing) N = -N;
                    vec3 L = normalize(uLightDir);
                    vec3 V = viewVec / max(dist, 0.001);
                    vec3 H = normalize(L + V);

                    float diff = max(dot(N, L), 0.0);
                    float backFill = max(dot(N, vec3(-L.x, 0.35, -L.z)), 0.0) * 0.25;
                    float hemiBlend = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);
                    vec3 ambient = mix(uHemiGroundColor, uHemiSkyColor, hemiBlend) * 1.12;
                    float specAmt = pow(max(dot(N, H), 0.0), 28.0) * uSpec;
                    float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0) * 0.14;

                    litRgb = baseCol.rgb * (ambient + uSunColor * diff * 0.78 + uHemiSkyColor * backFill)
                           + uSunColor * (specAmt + rim * 0.75)
                           + uEmissive;
                }
                float fog = clamp((dist - uFogStart) / max(30.0, uFogRange), 0.0, 0.76);
                vec3 sunHaze = mix(uFogColor, uSunColor * 0.92, 0.14);
                vec3 finalRgb = mix(litRgb, sunHaze, fog * (1.0 - uUnlit * 0.78));
                gl_FragColor = vec4(finalRgb, outAlpha);
            }
        """

        private const val COLOR_VERTEX_SHADER = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            attribute vec3 aColor;
            varying vec3 vColor;
            varying vec3 vWorldPos;
            void main() {
                vColor = aColor;
                vWorldPos = aPos;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val COLOR_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uCamPos;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogRange;
            uniform float uFogEnable;
            uniform float uDetailEnable;
            varying vec3 vColor;
            varying vec3 vWorldPos;
            void main() {
                vec3 col = vColor;
                if (uFogEnable > 0.5) {
                    if (uDetailEnable > 0.5) {
                        // Dual-scale organic surface texture (broad terrain patches + tactile grain, skipped in LVP mode)
                        float patchWave = sin(vWorldPos.x * 0.11 + sin(vWorldPos.z * 0.08)) * cos(vWorldPos.z * 0.11);
                        float grain = fract(sin(dot(floor(vWorldPos.xz * 2.5), vec2(12.9898, 78.233))) * 43758.5453);
                        col *= (0.95 + patchWave * 0.055 + grain * 0.075);
                    }
                    float dist = length(vWorldPos - uCamPos);
                    float fog = clamp((dist - uFogStart) / max(30.0, uFogRange), 0.0, 0.80);
                    col = mix(col, uFogColor, fog);
                }
                gl_FragColor = vec4(col, 1.0);
            }
        """
    }
}
