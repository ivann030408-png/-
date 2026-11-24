package com.example.renderer

import android.content.res.AssetManager
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Manages asynchronous CPU parsing and lazy GL-thread VBO/Texture caching for all 44 .glb models
 * in `app/src/main/assets/models/`.
 *
 * - Background worker pre-parses .glb binaries into CPU arrays so GL frame drops stay near zero.
 * - GL thread uploads VBO/IBO/Textures once per EGL context and reuses them every frame.
 * - If a model is still streaming or missing, returns null so the renderer gracefully draws its fallback primitive.
 */
class GlbModelManager(
    private val assets: AssetManager
) {
    private val queuedPaths = ConcurrentHashMap.newKeySet<String>()
    private val gpuCache = HashMap<String, GlbGpuModel>()

    private val loaderPool = Executors.newFixedThreadPool(
        minOf(4, maxOf(2, Runtime.getRuntime().availableProcessors() - 1))
    ) { r ->
        Thread(r, "GlbAssetParser").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }

    var whiteTextureId: Int = 0
        private set

    // Pre-allocated scratch buffers for zero-GC hierarchical node evaluation on the GL thread
    private var maxNodesAllocated = 160
    private var nodeT = FloatArray(maxNodesAllocated * 3)
    private var nodeR = FloatArray(maxNodesAllocated * 4)
    private var nodeS = FloatArray(maxNodesAllocated * 3)
    private var nodeAnimated = BooleanArray(maxNodesAllocated)
    private val scratchVec3 = FloatArray(3)
    private val scratchQuat = FloatArray(4)
    private val scratchLocal = FloatArray(16)
    private val scratchWorldStack = Array(32) { FloatArray(16) }

    // Pre-allocated zero-GC deferred queue so BLEND primitives always draw AFTER all OPAQUE/MASK primitives
    private class DeferredBlendDraw {
        var prim: GlbGpuPrimitive? = null
        val worldMat = FloatArray(16)
        var textureId = 0
        var r = 1f; var g = 1f; var b = 1f; var a = 1f
        var er = 0f; var eg = 0f; var eb = 0f
        var spec = 0.25f
        var doubleSided = false
        var unlit = false
        var alphaCutoff = 0.5f
    }
    private val deferredBlends = Array(64) { DeferredBlendDraw() }
    private var deferredBlendCount = 0

    @Volatile
    internal var gpuUploadCount: Int = 0
        private set

    fun onGlContextCreated() {
        gpuCache.clear()
        whiteTextureId = createWhite1x1Texture()
        gpuUploadCount++
        // Kick off background pre-parsing for core high-priority models immediately
        preloadBatch(CORE_PRELOAD_MODELS)
    }

    fun preloadBatch(fileNames: List<String>) {
        if (loaderPool.isShutdown) return
        for (name in fileNames) {
            if (sharedCpuCache.containsKey(name) || sharedFailedPaths.contains(name) || !queuedPaths.add(name)) continue
            val assetPath = "models/$name"
            try {
                loaderPool.execute {
                    val parsed = GlbModelLoader.parseFromAssets(assets, assetPath)
                    if (parsed != null) {
                        sharedCpuCache[name] = parsed
                    } else {
                        sharedFailedPaths.add(name)
                    }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                queuedPaths.remove(name)
                return
            }
        }
    }

    data class PreloadStats(
        val cpuDone: Int,
        val gpuDone: Int,
        val failed: Int,
        val total: Int
    ) {
        val isAllGpuReady: Boolean
            get() = total <= 0 || (gpuDone + failed) >= total
    }

    /**
     * Reports exact CPU parsing & GPU upload completion counts for a target model list.
     * Safe to call on the GL thread during staged loading.
     */
    fun getPreloadStats(targets: List<String>): PreloadStats {
        var cpuDone = 0
        var gpuDone = 0
        var failed = 0
        for (i in targets.indices) {
            val name = targets[i]
            when {
                gpuCache.containsKey(name) -> {
                    cpuDone++
                    gpuDone++
                }
                sharedFailedPaths.contains(name) -> {
                    failed++
                }
                sharedCpuCache.containsKey(name) -> {
                    cpuDone++
                }
            }
        }
        return PreloadStats(
            cpuDone = cpuDone,
            gpuDone = gpuDone,
            failed = failed,
            total = targets.size
        )
    }

    /**
     * Uploads CPU-parsed .glb models from [targets] to GPU VBO/IBO/Textures in a frame-budgeted
     * batch so the Loading Screen progress bar and animations stay smooth without blocking the GL thread.
     * Always uploads at least 1 ready model per call if available so progress strictly advances.
     */
    fun uploadReadyBatchOnGlThread(targets: List<String>, maxBudgetMs: Long = 10L): Int {
        val deadlineNanos = System.nanoTime() + maxBudgetMs * 1_000_000L
        var uploaded = 0
        for (i in targets.indices) {
            val name = targets[i]
            if (gpuCache.containsKey(name) || sharedFailedPaths.contains(name)) continue
            val cpu = sharedCpuCache[name] ?: continue
            val gpu = GlbModelLoader.uploadToGpu(cpu)
            gpuCache[name] = gpu
            gpuUploadCount++
            uploaded++
            if (System.nanoTime() >= deadlineNanos) break
        }
        return uploaded
    }

    /**
     * Fetches a GPU-ready model on the GL thread with zero String allocations on cache hit.
     * If CPU data is ready, uploads to GPU immediately and caches it.
     * If not yet parsed, schedules async parse (or sync parse for small essential models if requested).
     */
    fun getOrLoadGpu(fileName: String, syncIfMissing: Boolean = false): GlbGpuModel? {
        gpuCache[fileName]?.let { return it }
        if (sharedFailedPaths.contains(fileName)) return null

        var cpu = sharedCpuCache[fileName]
        if (cpu == null) {
            val assetPath = "models/$fileName"
            if (syncIfMissing) {
                cpu = GlbModelLoader.parseFromAssets(assets, assetPath)
                if (cpu != null) {
                    sharedCpuCache[fileName] = cpu
                } else {
                    sharedFailedPaths.add(fileName)
                    return null
                }
            } else {
                if (!loaderPool.isShutdown && queuedPaths.add(fileName)) {
                    try {
                        loaderPool.execute {
                            val parsed = GlbModelLoader.parseFromAssets(assets, assetPath)
                            if (parsed != null) {
                                sharedCpuCache[fileName] = parsed
                            } else {
                                sharedFailedPaths.add(fileName)
                            }
                        }
                    } catch (_: java.util.concurrent.RejectedExecutionException) {
                        queuedPaths.remove(fileName)
                    }
                }
                return null
            }
        }

        val gpu = GlbModelLoader.uploadToGpu(cpu)
        gpuCache[fileName] = gpu
        gpuUploadCount++
        return gpu
    }

    fun releaseGl() {
        for (model in gpuCache.values) {
            model.releaseGl()
        }
        gpuCache.clear()
        if (whiteTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(whiteTextureId), 0)
            whiteTextureId = 0
        }
    }

    fun shutdown() {
        loaderPool.shutdownNow()
    }

    private fun ensureScratchCapacity(nodeCount: Int) {
        if (nodeCount <= maxNodesAllocated) return
        maxNodesAllocated = (nodeCount + 32)
        nodeT = FloatArray(maxNodesAllocated * 3)
        nodeR = FloatArray(maxNodesAllocated * 4)
        nodeS = FloatArray(maxNodesAllocated * 3)
        nodeAnimated = BooleanArray(maxNodesAllocated)
    }

    /**
     * Evaluates node hierarchy + optional animation clip + optional single-node overlay animation
     * (e.g. HoldGun on arm_R while walking/running/jumping) + procedural overrides and renders
     * the model in 2 passes (Pass 1: OPAQUE & MASK with depth write, Pass 2: BLEND without depth write).
     */
    fun drawModel(
        renderer: ChickenGlRenderer,
        model: GlbGpuModel,
        rootWorldMatrix: FloatArray,
        animName: String? = null,
        animTime: Float = 0f,
        tintR: Float = 1f,
        tintG: Float = 1f,
        tintB: Float = 1f,
        alphaOverride: Float = 1f,
        overrides: GlbNodeOverrides? = null,
        unlit: Boolean = false,
        overlayAnimName: String? = null,
        overlayNodeName: String? = null,
        animLoop: Boolean = true
    ) {
        val cpu = model.cpu
        val nCount = cpu.nodes.size
        ensureScratchCapacity(nCount)

        val anim = if (animName != null) cpu.animations[animName] else null
        val overlayAnim = if (overlayAnimName != null && overlayNodeName != null) cpu.animations[overlayAnimName] else null
        val overlayNodeIdx = if (overlayAnim != null && overlayNodeName != null) cpu.nodeNameToIndex[overlayNodeName] ?: -1 else -1
        val hasAnyAnim = anim != null || (overlayAnim != null && overlayNodeIdx in 0 until nCount)

        if (hasAnyAnim) {
            for (i in 0 until nCount) {
                val nd = cpu.nodes[i]
                nodeT[i * 3] = nd.baseTranslation[0]
                nodeT[i * 3 + 1] = nd.baseTranslation[1]
                nodeT[i * 3 + 2] = nd.baseTranslation[2]
                nodeR[i * 4] = nd.baseRotation[0]
                nodeR[i * 4 + 1] = nd.baseRotation[1]
                nodeR[i * 4 + 2] = nd.baseRotation[2]
                nodeR[i * 4 + 3] = nd.baseRotation[3]
                nodeS[i * 3] = nd.baseScale[0]
                nodeS[i * 3 + 1] = nd.baseScale[1]
                nodeS[i * 3 + 2] = nd.baseScale[2]
                nodeAnimated[i] = false
            }
            if (anim != null) {
                val tEval = if (anim.duration > 0.001f) {
                    if (animLoop) {
                        val m = animTime % anim.duration
                        if (m < 0f) m + anim.duration else m
                    } else {
                        animTime.coerceIn(0f, anim.duration)
                    }
                } else 0f

                for (ci in anim.channels.indices) {
                    val ch = anim.channels[ci]
                    val ni = ch.nodeIndex
                    if (ni !in 0 until nCount) continue
                    nodeAnimated[ni] = true
                    applyAnimChannel(ch, ni, tEval)
                }
            }
            if (overlayAnim != null && overlayNodeIdx in 0 until nCount) {
                val tOver = if (overlayAnim.duration > 0.001f) {
                    val m = animTime % overlayAnim.duration
                    if (m < 0f) m + overlayAnim.duration else m
                } else 0f
                for (ci in overlayAnim.channels.indices) {
                    val ch = overlayAnim.channels[ci]
                    if (ch.nodeIndex == overlayNodeIdx) {
                        nodeAnimated[overlayNodeIdx] = true
                        applyAnimChannel(ch, overlayNodeIdx, tOver)
                    }
                }
            }
        }

        val forceTranslucent = alphaOverride < 0.99f
        deferredBlendCount = 0
        System.arraycopy(rootWorldMatrix, 0, scratchWorldStack[0], 0, 16)
        for (rootIdx in cpu.rootNodes) {
            traverseNode(
                renderer = renderer,
                model = model,
                nodeIdx = rootIdx,
                parentDepth = 0,
                hasAnim = hasAnyAnim,
                tintR = tintR,
                tintG = tintG,
                tintB = tintB,
                alphaOverride = alphaOverride,
                forceTranslucent = forceTranslucent,
                overrides = overrides,
                unlit = unlit
            )
        }

        // Pass 2: Draw deferred BLEND primitives (car windshields, glass, fire ring, bottles)
        // AFTER all solid/opaque geometry with depth writes disabled so glass never punches holes!
        if (deferredBlendCount > 0) {
            for (i in 0 until deferredBlendCount) {
                val d = deferredBlends[i]
                val p = d.prim ?: continue
                renderer.drawGlbPrimitive(
                    prim = p,
                    worldMat = d.worldMat,
                    textureId = d.textureId,
                    r = d.r, g = d.g, b = d.b, a = d.a,
                    emissiveR = d.er, emissiveG = d.eg, emissiveB = d.eb,
                    spec = d.spec,
                    doubleSided = d.doubleSided,
                    unlit = d.unlit,
                    alphaMode = GlbAlphaMode.BLEND,
                    alphaCutoff = d.alphaCutoff
                )
            }
            deferredBlendCount = 0
        }
    }

    private fun applyAnimChannel(ch: GlbAnimChannel, ni: Int, t: Float) {
        when (ch.path) {
            GlbAnimPath.TRANSLATION -> {
                GlbModelLoader.sampleChannelVec3(ch, t, scratchVec3)
                nodeT[ni * 3] = scratchVec3[0]
                nodeT[ni * 3 + 1] = scratchVec3[1]
                nodeT[ni * 3 + 2] = scratchVec3[2]
            }
            GlbAnimPath.ROTATION -> {
                GlbModelLoader.sampleChannelQuat(ch, t, scratchQuat)
                nodeR[ni * 4] = scratchQuat[0]
                nodeR[ni * 4 + 1] = scratchQuat[1]
                nodeR[ni * 4 + 2] = scratchQuat[2]
                nodeR[ni * 4 + 3] = scratchQuat[3]
            }
            GlbAnimPath.SCALE -> {
                GlbModelLoader.sampleChannelVec3(ch, t, scratchVec3)
                nodeS[ni * 3] = scratchVec3[0]
                nodeS[ni * 3 + 1] = scratchVec3[1]
                nodeS[ni * 3 + 2] = scratchVec3[2]
            }
        }
    }

    private fun traverseNode(
        renderer: ChickenGlRenderer,
        model: GlbGpuModel,
        nodeIdx: Int,
        parentDepth: Int,
        hasAnim: Boolean,
        tintR: Float,
        tintG: Float,
        tintB: Float,
        alphaOverride: Float,
        forceTranslucent: Boolean,
        overrides: GlbNodeOverrides?,
        unlit: Boolean
    ) {
        if (parentDepth + 1 >= scratchWorldStack.size) return
        val cpu = model.cpu
        if (nodeIdx !in cpu.nodes.indices) return
        val node = cpu.nodes[nodeIdx]

        if (overrides != null && node.name.isNotEmpty() && overrides.hiddenNodes.contains(node.name)) {
            return
        }

        // Compute local node matrix
        if (hasAnim && nodeAnimated[nodeIdx]) {
            GlbModelLoader.composeTrsMatrix(
                scratchLocal,
                nodeT[nodeIdx * 3], nodeT[nodeIdx * 3 + 1], nodeT[nodeIdx * 3 + 2],
                nodeR[nodeIdx * 4], nodeR[nodeIdx * 4 + 1], nodeR[nodeIdx * 4 + 2], nodeR[nodeIdx * 4 + 3],
                nodeS[nodeIdx * 3], nodeS[nodeIdx * 3 + 1], nodeS[nodeIdx * 3 + 2]
            )
        } else if (node.hasMatrix) {
            System.arraycopy(node.localMatrix, 0, scratchLocal, 0, 16)
        } else {
            GlbModelLoader.composeTrsMatrix(
                scratchLocal,
                node.baseTranslation[0], node.baseTranslation[1], node.baseTranslation[2],
                node.baseRotation[0], node.baseRotation[1], node.baseRotation[2], node.baseRotation[3],
                node.baseScale[0], node.baseScale[1], node.baseScale[2]
            )
        }

        // Apply optional procedural local translation / rotation overrides (wheels, traps, springs, weapon slide/mag)
        if (overrides != null && node.name.isNotEmpty()) {
            overrides.extraTrans[node.name]?.let { tr ->
                Matrix.translateM(scratchLocal, 0, tr[0], tr[1], tr[2])
            }
            overrides.extraRotDeg[node.name]?.let { rot ->
                if (rot[1] != 0f) Matrix.rotateM(scratchLocal, 0, rot[1], 0f, 1f, 0f)
                if (rot[0] != 0f) Matrix.rotateM(scratchLocal, 0, rot[0], 1f, 0f, 0f)
                if (rot[2] != 0f) Matrix.rotateM(scratchLocal, 0, rot[2], 0f, 0f, 1f)
            }
        }

        val worldMat = scratchWorldStack[parentDepth + 1]
        Matrix.multiplyMM(worldMat, 0, scratchWorldStack[parentDepth], 0, scratchLocal, 0)

        // Capture anchor world matrix if requested (e.g., "weapon_slot", "muzzle", "confetti_anchor")
        if (overrides != null && node.name.isNotEmpty()) {
            overrides.captureWorldMatrix[node.name]?.let { dst ->
                System.arraycopy(worldMat, 0, dst, 0, 16)
            }
        }

        // Render mesh primitives if attached to this node
        if (node.meshIndex in model.gpuMeshes.indices) {
            val prims = model.gpuMeshes[node.meshIndex]
            val emBoost = if (overrides != null && node.name.isNotEmpty()) overrides.emissiveBoost[node.name] else null
            for (pi in prims.indices) {
                val prim = prims[pi]
                val mat = cpu.materials[prim.materialIndex]
                val texId = if (mat.textureImageIndex in model.textureIds.indices && model.textureIds[mat.textureImageIndex] != 0) {
                    model.textureIds[mat.textureImageIndex]
                } else {
                    whiteTextureId
                }
                val er = mat.emissiveR + (emBoost?.get(0) ?: 0f)
                val eg = mat.emissiveG + (emBoost?.get(1) ?: 0f)
                val eb = mat.emissiveB + (emBoost?.get(2) ?: 0f)
                val effectiveMode = if (forceTranslucent) GlbAlphaMode.BLEND else mat.alphaMode
                val finalAlpha = if (effectiveMode == GlbAlphaMode.OPAQUE) {
                    1f
                } else {
                    (mat.baseA * alphaOverride).coerceIn(0f, 1f)
                }
                val isUnlit = unlit || (emBoost != null && (emBoost[0] + emBoost[1] + emBoost[2]) > 0.6f)

                if (effectiveMode == GlbAlphaMode.BLEND && !forceTranslucent && deferredBlendCount < deferredBlends.size) {
                    val slot = deferredBlends[deferredBlendCount++]
                    slot.prim = prim
                    System.arraycopy(worldMat, 0, slot.worldMat, 0, 16)
                    slot.textureId = texId
                    slot.r = mat.baseR * tintR
                    slot.g = mat.baseG * tintG
                    slot.b = mat.baseB * tintB
                    slot.a = finalAlpha
                    slot.er = er
                    slot.eg = eg
                    slot.eb = eb
                    slot.spec = mat.specStrength
                    slot.doubleSided = mat.doubleSided
                    slot.unlit = isUnlit
                    slot.alphaCutoff = mat.alphaCutoff
                } else {
                    renderer.drawGlbPrimitive(
                        prim = prim,
                        worldMat = worldMat,
                        textureId = texId,
                        r = mat.baseR * tintR,
                        g = mat.baseG * tintG,
                        b = mat.baseB * tintB,
                        a = finalAlpha,
                        emissiveR = er,
                        emissiveG = eg,
                        emissiveB = eb,
                        spec = mat.specStrength,
                        doubleSided = mat.doubleSided,
                        unlit = isUnlit,
                        alphaMode = effectiveMode,
                        alphaCutoff = mat.alphaCutoff
                    )
                }
            }
        }

        // Recurse into children
        for (ci in node.children.indices) {
            traverseNode(
                renderer = renderer,
                model = model,
                nodeIdx = node.children[ci],
                parentDepth = parentDepth + 1,
                hasAnim = hasAnim,
                tintR = tintR,
                tintG = tintG,
                tintB = tintB,
                alphaOverride = alphaOverride,
                forceTranslucent = forceTranslucent,
                overrides = overrides,
                unlit = unlit
            )
        }
    }

    private fun createWhite1x1Texture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        if (id != 0) {
            val pixel = ByteBuffer.allocateDirect(4).apply {
                put(0xFF.toByte()); put(0xFF.toByte()); put(0xFF.toByte()); put(0xFF.toByte())
                position(0)
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
        return id
    }

    companion object {
        // Shared immutable CPU-parsed GLB cache across matches & map switches (GPU VBOs stay per-EGL-context)
        private val sharedCpuCache = ConcurrentHashMap<String, GlbModelCpu>()
        private val sharedFailedPaths = ConcurrentHashMap.newKeySet<String>()

        val CORE_PRELOAD_MODELS = listOf(
            "khryak_napoleon_animated.glb",
            "pig_burger_animated.glb",
            "weapon_pistol_classic.glb",
            "weapon_rifle_classic.glb",
            "weapon_rpg_classic.glb",
            "weapon_pistol_tactical.glb",
            "weapon_rifle_tactical.glb",
            "weapon_rpg_tactical.glb",
            "car_police.glb",
            "car_hypercar.glb",
            "car_firetruck.glb",
            "car_pickup.glb",
            "car_monster.glb",
            "car_suv.glb",
            "car_sedan.glb",
            // New modular roads & interactive architecture
            "road_roundabout.glb",
            "road_straight.glb",
            "road_turn_90.glb",
            "road_cross_or_ramp.glb",
            "house_small.glb",
            "garage_hangar.glb",
            "watchtower_building.glb",
            "wall_arch_gate.glb",
            "wall_block_concrete.glb",
            "wall_section_brick.glb",
            "wall_section_metal.glb",
            "wall_section_wood.glb",
            // Stunts, traps & racing track
            "finish_arch.glb",
            "boost_pad.glb",
            "loop_the_loop.glb",
            "double_ramp.glb",
            "prop_ramp.glb",
            "jump_pad.glb",
            "pendulum_hammer.glb",
            "crusher.glb",
            "spinner_trap.glb",
            "fire_ring.glb",
            "bowling_pins.glb",
            // Updated destructible & city/base props
            "wooden_crate.glb",
            "explosive_barrel.glb",
            "gas_cylinder.glb",
            "oil_drum.glb",
            "toxic_drum.glb",
            "fuel_canister.glb",
            "water_barrel.glb",
            "shooting_target.glb",
            "sandbags.glb",
            "tire_stack.glb",
            "fragile_brick_wall.glb",
            "street_lamp.glb",
            "billboard_sign.glb",
            "wooden_fence.glb",
            "boom_barrier.glb",
            "metal_gate.glb",
            "dumpster.glb",
            "power_generator.glb",
            "radar_dish.glb",
            "radio_mast.glb",
            "solar_panel.glb",
            "ammo_crate.glb",
            "fire_brazier.glb",
            "fire_hydrant.glb",
            "park_bench.glb",
            "plank_bridge.glb",
            "safe_box.glb",
            "military_case.glb",
            "workbench_table.glb",
            "wooden_barrel.glb",
            "wooden_pallet.glb",
            "road_sign.glb",
            "bollard.glb",
            // Food & drinks pickups
            "gazyava_classic.glb",
            "gazyava_lemon.glb",
            "gazyava_chicken.glb",
            "gazyava_tarhun.glb",
            "hrumki_chicken.glb",
            "hrumki_cheese.glb",
            "hrumki_spicy.glb",
            "hrumki_greens.glb"
        )
    }
}
