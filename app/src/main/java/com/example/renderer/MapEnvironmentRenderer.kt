package com.example.renderer

import com.example.engine.GameEngine
import com.example.engine.GameMap
import kotlin.math.sin

/**
 * Clean outer-perimeter & distant horizon scenery renderer for the unified MAIN_ARENA map.
 * All playable buildings, roads, gates, walls, lamps, and interactive structures are authentic .glb models!
 */
class MapEnvironmentRenderer(
    private val host: ChickenGlRenderer
) {
    // Distant skyline silhouettes strictly outside the playable perimeter (6 iconic blocks for high FPS)
    private val horizonTowers = arrayOf(
        floatArrayOf(-118f, -118f, 18f, 34f, 18f, 0.42f, 0.48f, 0.58f),
        floatArrayOf(118f, -118f, 20f, 40f, 18f, 0.38f, 0.45f, 0.56f),
        floatArrayOf(-124f, 10f, 18f, 30f, 18f, 0.46f, 0.50f, 0.56f),
        floatArrayOf(124f, 10f, 18f, 34f, 18f, 0.40f, 0.48f, 0.56f),
        floatArrayOf(-56f, 124f, 18f, 28f, 16f, 0.46f, 0.48f, 0.54f),
        floatArrayOf(58f, -124f, 18f, 36f, 16f, 0.44f, 0.48f, 0.56f)
    )

    // Streamlined outer greenbelt trees (strictly outside playable roads, stunts & combat lanes)
    private val arenaTrees = arrayOf(
        floatArrayOf(-104f, -88f, 1.45f), floatArrayOf(104f, -88f, 1.45f),
        floatArrayOf(-104f, 88f, 1.40f), floatArrayOf(104f, 88f, 1.45f),
        floatArrayOf(-88f, -104f, 1.35f), floatArrayOf(88f, -104f, 1.40f),
        floatArrayOf(-88f, 104f, 1.35f), floatArrayOf(88f, 104f, 1.35f),
        floatArrayOf(-110f, 0f, 1.45f), floatArrayOf(110f, 0f, 1.45f),
        floatArrayOf(0f, -110f, 1.45f), floatArrayOf(0f, 110f, 1.45f),
        floatArrayOf(-56f, -98f, 1.30f), floatArrayOf(56f, 98f, 1.30f)
    )

    // Perimeter stadium floodlight pylons at corners
    private val cornerBeacons = arrayOf(
        floatArrayOf(-136f, -136f), floatArrayOf(136f, -136f),
        floatArrayOf(-136f, 136f), floatArrayOf(136f, 136f)
    )

    fun draw(
        camX: Float,
        camZ: Float,
        camFwdX: Float,
        camFwdZ: Float,
        renderDist: Float,
        worldTime: Float,
        lowEnd: Boolean,
        map: GameMap
    ) {
        val h = GameEngine.ARENA_HALF_SIZE
        val cullDist = renderDist.coerceIn(90f, 320f)
        val cullDistSq = cullDist * cullDist
        val beaconPulse = sin(worldTime * 3.6f) > 0.1f

        // 1. Clean arena perimeter boundary wall with orange safety rail
        drawPerimeterBoundary(h, map)

        // 2. Distant horizon skyline blocks + blinking aviation beacons
        for (i in horizonTowers.indices) {
            val b = horizonTowers[i]
            if (!isVisible(b[0], b[1], camX, camZ, camFwdX, camFwdZ, cullDistSq, 45f)) continue
            val w = b[2]; val bh = b[3]; val d = b[4]
            host.drawBox(b[0], bh * 0.5f, b[1], 0f, w, bh, d, b[5], b[6], b[7], 1f, spec = 0.14f)
            if (!lowEnd) {
                host.setUnlit(true)
                host.drawBox(b[0], bh * 0.72f, b[1], 0f, w + 0.2f, 1.4f, d + 0.2f, 0.58f, 0.84f, 0.98f, 1f)
                host.drawBox(b[0], bh * 0.42f, b[1], 0f, w + 0.2f, 1.4f, d + 0.2f, 0.58f, 0.84f, 0.98f, 1f)
                if (beaconPulse) {
                    host.drawMesh(host.sphereMesh, b[0], bh + 1.2f, b[1], 0f, 1.4f, 1.4f, 1.4f, 1.0f, 0.24f, 0.18f, 1f)
                }
                host.setUnlit(false)
            }
        }

        // 3. Corner stadium floodlight towers
        if (!lowEnd) {
            for (i in cornerBeacons.indices) {
                val p = cornerBeacons[i]
                if (!isVisible(p[0], p[1], camX, camZ, camFwdX, camFwdZ, cullDistSq, 24f)) continue
                host.drawBox(p[0], 11f, p[1], 0f, 1.8f, 22f, 1.8f, 0.34f, 0.38f, 0.46f, 1f, spec = 0.35f)
                host.setUnlit(true)
                host.drawBox(p[0], 22.4f, p[1], 0f, 6.2f, 2.0f, 2.0f, 1.0f, 0.96f, 0.82f, 1f)
                host.setUnlit(false)
            }
        }

        // 4. Stylized trees framing outer greenbelt zones (all outside playable perimeter)
        for (i in arenaTrees.indices) {
            val t = arenaTrees[i]
            if (!isVisible(t[0], t[1], camX, camZ, camFwdX, camFwdZ, cullDistSq, 14f)) continue
            drawTree(t[0], t[1], t[2], map.treeR, map.treeG, map.treeB, lowEnd)
        }
    }

    private fun drawPerimeterBoundary(h: Float, map: GameMap) {
        val wallH = 5.4f
        val thick = 2.0f
        val wR = map.wallR
        val wG = map.wallG
        val wB = map.wallB
        val rR = map.railR
        val rG = map.railG
        val rB = map.railB

        host.drawBox(0f, wallH * 0.5f, -h, 0f, h * 2f, wallH, thick, wR, wG, wB, 1f, spec = 0.08f)
        host.drawBox(0f, wallH * 0.5f, h, 0f, h * 2f, wallH, thick, wR, wG, wB, 1f, spec = 0.08f)
        host.drawBox(-h, wallH * 0.5f, 0f, 0f, thick, wallH, h * 2f, wR * 0.94f, wG * 0.94f, wB * 0.94f, 1f, spec = 0.08f)
        host.drawBox(h, wallH * 0.5f, 0f, 0f, thick, wallH, h * 2f, wR * 0.94f, wG * 0.94f, wB * 0.94f, 1f, spec = 0.08f)

        host.drawBox(0f, wallH + 0.30f, -h, 0f, h * 2f, 0.55f, thick + 0.25f, rR, rG, rB, 1f, spec = 0.30f)
        host.drawBox(0f, wallH + 0.30f, h, 0f, h * 2f, 0.55f, thick + 0.25f, rR, rG, rB, 1f, spec = 0.30f)
        host.drawBox(-h, wallH + 0.30f, 0f, 0f, thick + 0.25f, 0.55f, h * 2f, rR, rG, rB, 1f, spec = 0.30f)
        host.drawBox(h, wallH + 0.30f, 0f, 0f, thick + 0.25f, 0.55f, h * 2f, rR, rG, rB, 1f, spec = 0.30f)
    }

    private fun drawTree(x: Float, z: Float, scale: Float, r: Float, g: Float, b: Float, lowEnd: Boolean) {
        host.drawMesh(host.cylinderMesh, x, 2.2f * scale, z, 0f, 0.95f * scale, 4.4f * scale, 0.95f * scale, 0.44f, 0.29f, 0.16f, 1f, spec = 0.06f)
        host.drawMesh(host.sphereMesh, x, 5.2f * scale, z, 0f, 4.4f * scale, 3.8f * scale, 4.4f * scale, r, g, b, 1f, spec = 0.08f)
        if (!lowEnd) {
            host.drawMesh(host.sphereMesh, x + 0.9f * scale, 6.4f * scale, z - 0.6f * scale, 0f, 2.9f * scale, 2.6f * scale, 2.9f * scale, r * 1.12f, g * 1.10f, b * 1.05f, 1f, spec = 0.08f)
        }
    }

    private fun isVisible(
        x: Float, z: Float,
        camX: Float, camZ: Float,
        camFwdX: Float, camFwdZ: Float,
        cullDistSq: Float,
        radius: Float
    ): Boolean {
        val dx = x - camX
        val dz = z - camZ
        val dSq = dx * dx + dz * dz
        if (dSq > cullDistSq + radius * radius * 2f) return false
        if (dSq < radius * radius * 1.6f) return true
        return (dx * camFwdX + dz * camFwdZ) > -radius
    }
}
