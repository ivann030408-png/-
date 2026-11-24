package com.example.renderer

import android.opengl.GLES20
import com.example.engine.GameMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tracks currently bound VBO & vertex attribute layout on the GL thread so repeated
 * draws of identical meshes (e.g. cubeMesh/sphereMesh) skip redundant glBindBuffer & pointer calls.
 */
object GlBufferState {
    var boundVbo: Int = -1
    var boundPosAttr: Int = -1
    var boundExtraAttr: Int = -1
    var boundGlbVbo: Int = -1
    var boundGlbIbo: Int = -1
    var glbAttribsEnabled: Boolean = false

    fun reset() {
        boundVbo = -1
        boundPosAttr = -1
        boundExtraAttr = -1
        boundGlbVbo = -1
        boundGlbIbo = -1
        glbAttribsEnabled = false
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }
}

/**
 * GPU-backed VBO interleaved 3D mesh: [posX, posY, posZ, normX, normY, normZ] = 6 floats (24 bytes) per vertex.
 * Falls back cleanly to client-side buffer if VBO allocation fails.
 */
class GlMesh(vertices: FloatArray) {
    val vertexCount: Int = vertices.size / 6
    private val vboId: Int
    private val fallbackBuffer: FloatBuffer?

    init {
        val nativeBuf = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        if (ids[0] != 0) {
            vboId = ids[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, nativeBuf, GLES20.GL_STATIC_DRAW)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            fallbackBuffer = null
        } else {
            vboId = 0
            fallbackBuffer = nativeBuf
        }
    }

    fun draw(posHandle: Int, normHandle: Int) {
        if (vboId != 0) {
            if (GlBufferState.boundVbo != vboId ||
                GlBufferState.boundPosAttr != posHandle ||
                GlBufferState.boundExtraAttr != normHandle
            ) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
                GLES20.glEnableVertexAttribArray(posHandle)
                GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 24, 0)
                GLES20.glEnableVertexAttribArray(normHandle)
                GLES20.glVertexAttribPointer(normHandle, 3, GLES20.GL_FLOAT, false, 24, 12)
                GlBufferState.boundVbo = vboId
                GlBufferState.boundPosAttr = posHandle
                GlBufferState.boundExtraAttr = normHandle
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        } else {
            val buf = fallbackBuffer ?: return
            GlBufferState.boundVbo = 0
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            buf.position(0)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 24, buf)
            GLES20.glEnableVertexAttribArray(posHandle)
            buf.position(3)
            GLES20.glVertexAttribPointer(normHandle, 3, GLES20.GL_FLOAT, false, 24, buf)
            GLES20.glEnableVertexAttribArray(normHandle)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        }
    }
}

/**
 * GPU-backed VBO colored-vertex mesh for ground tiles, sky dome, and distant mountains: [posX, posY, posZ, r, g, b] = 6 floats.
 */
class GlColorMesh(vertices: FloatArray) {
    val vertexCount: Int = vertices.size / 6
    private val vboId: Int
    private val fallbackBuffer: FloatBuffer?

    init {
        val nativeBuf = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        if (ids[0] != 0) {
            vboId = ids[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, nativeBuf, GLES20.GL_STATIC_DRAW)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            fallbackBuffer = null
        } else {
            vboId = 0
            fallbackBuffer = nativeBuf
        }
    }

    fun draw(posHandle: Int, colorHandle: Int) {
        if (vboId != 0) {
            if (GlBufferState.boundVbo != vboId ||
                GlBufferState.boundPosAttr != posHandle ||
                GlBufferState.boundExtraAttr != colorHandle
            ) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
                GLES20.glEnableVertexAttribArray(posHandle)
                GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 24, 0)
                GLES20.glEnableVertexAttribArray(colorHandle)
                GLES20.glVertexAttribPointer(colorHandle, 3, GLES20.GL_FLOAT, false, 24, 12)
                GlBufferState.boundVbo = vboId
                GlBufferState.boundPosAttr = posHandle
                GlBufferState.boundExtraAttr = colorHandle
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        } else {
            val buf = fallbackBuffer ?: return
            GlBufferState.boundVbo = 0
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            buf.position(0)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 24, buf)
            GLES20.glEnableVertexAttribArray(posHandle)
            buf.position(3)
            GLES20.glVertexAttribPointer(colorHandle, 3, GLES20.GL_FLOAT, false, 24, buf)
            GLES20.glEnableVertexAttribArray(colorHandle)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        }
    }

    fun release() {
        if (vboId != 0) {
            val ids = intArrayOf(vboId)
            GLES20.glDeleteBuffers(1, ids, 0)
        }
    }
}

object MeshFactory {

    /** Unit cube centered at (0,0,0) with side length 1 (-0.5..+0.5). */
    fun createUnitCube(): GlMesh {
        val data = ArrayList<Float>(36 * 6)
        fun addQuad(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            nx: Float, ny: Float, nz: Float
        ) {
            fun v(x: Float, y: Float, z: Float) {
                data.add(x); data.add(y); data.add(z)
                data.add(nx); data.add(ny); data.add(nz)
            }
            v(x0, y0, z0); v(x1, y1, z1); v(x2, y2, z2)
            v(x0, y0, z0); v(x2, y2, z2); v(x3, y3, z3)
        }

        val s = 0.5f
        addQuad(-s, -s, s, s, -s, s, s, s, s, -s, s, s, 0f, 0f, 1f)
        addQuad(s, -s, -s, -s, -s, -s, -s, s, -s, s, s, -s, 0f, 0f, -1f)
        addQuad(-s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s, -1f, 0f, 0f)
        addQuad(s, -s, s, s, -s, -s, s, s, -s, s, s, s, 1f, 0f, 0f)
        addQuad(-s, s, s, s, s, s, s, s, -s, -s, s, -s, 0f, 1f, 0f)
        addQuad(-s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s, 0f, -1f, 0f)

        return GlMesh(data.toFloatArray())
    }

    /**
     * Unit stunt ramp wedge: X in [-0.5..0.5], Y in [0..1], Z in [-0.5..0.5].
     * Low edge at Z = +0.5 (Y = 0), high edge at Z = -0.5 (Y = 1).
     */
    fun createRampWedge(): GlMesh {
        val data = ArrayList<Float>(24 * 6)
        fun addTri(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            nx: Float, ny: Float, nz: Float
        ) {
            data.add(x0); data.add(y0); data.add(z0); data.add(nx); data.add(ny); data.add(nz)
            data.add(x1); data.add(y1); data.add(z1); data.add(nx); data.add(ny); data.add(nz)
            data.add(x2); data.add(y2); data.add(z2); data.add(nx); data.add(ny); data.add(nz)
        }

        val w = 0.5f
        val d = 0.5f
        val h = 1.0f

        val ny = 0.7071f
        val nz = 0.7071f
        addTri(-w, 0f, d, w, 0f, d, w, h, -d, 0f, ny, nz)
        addTri(-w, 0f, d, w, h, -d, -w, h, -d, 0f, ny, nz)

        addTri(w, 0f, -d, -w, 0f, -d, -w, h, -d, 0f, 0f, -1f)
        addTri(w, 0f, -d, -w, h, -d, w, h, -d, 0f, 0f, -1f)

        addTri(-w, 0f, -d, -w, 0f, d, -w, h, -d, -1f, 0f, 0f)
        addTri(w, 0f, d, w, 0f, -d, w, h, -d, 1f, 0f, 0f)

        addTri(-w, 0f, -d, w, 0f, -d, w, 0f, d, 0f, -1f, 0f)
        addTri(-w, 0f, -d, w, 0f, d, -w, 0f, d, 0f, -1f, 0f)

        return GlMesh(data.toFloatArray())
    }

    /** Unit vertical cylinder centered on Y in [-0.5..+0.5], radius 0.5. */
    fun createCylinder(segments: Int = 18): GlMesh {
        val data = ArrayList<Float>(segments * 12 * 6)
        val r = 0.5f
        val h = 0.5f
        fun push(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
            data.add(x); data.add(y); data.add(z)
            data.add(nx); data.add(ny); data.add(nz)
        }
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * 2f * PI.toFloat()
            val a1 = ((i + 1).toFloat() / segments) * 2f * PI.toFloat()
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            // Outward CCW winding so front-facing barrel/wheel/pole walls are never back-face culled!
            push(c0 * r, -h, s0 * r, c0, 0f, s0)
            push(c1 * r, h, s1 * r, c1, 0f, s1)
            push(c1 * r, -h, s1 * r, c1, 0f, s1)
            push(c0 * r, -h, s0 * r, c0, 0f, s0)
            push(c0 * r, h, s0 * r, c0, 0f, s0)
            push(c1 * r, h, s1 * r, c1, 0f, s1)

            push(0f, h, 0f, 0f, 1f, 0f)
            push(c1 * r, h, s1 * r, 0f, 1f, 0f)
            push(c0 * r, h, s0 * r, 0f, 1f, 0f)

            push(0f, -h, 0f, 0f, -1f, 0f)
            push(c0 * r, -h, s0 * r, 0f, -1f, 0f)
            push(c1 * r, -h, s1 * r, 0f, -1f, 0f)
        }
        return GlMesh(data.toFloatArray())
    }

    /**
     * Unit cone centered on Y in [-0.5..+0.5], base radius 0.5 at Y=-0.5, apex at (0, +0.5, 0).
     * Ideal for sculpted chicken beaks, rocket warheads, pine trees, and comb crests!
     */
    fun createCone(segments: Int = 14): GlMesh {
        val data = ArrayList<Float>(segments * 6 * 6)
        val r = 0.5f
        val baseY = -0.5f
        val topY = 0.5f
        val slopeNormY = 0.4472f
        val slopeNormR = 0.8944f
        fun push(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
            data.add(x); data.add(y); data.add(z)
            data.add(nx); data.add(ny); data.add(nz)
        }
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * 2f * PI.toFloat()
            val a1 = ((i + 1).toFloat() / segments) * 2f * PI.toFloat()
            val am = (a0 + a1) * 0.5f
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            val cm = cos(am); val sm = sin(am)
            // Side face (outward CCW winding)
            push(c0 * r, baseY, s0 * r, c0 * slopeNormR, slopeNormY, s0 * slopeNormR)
            push(0f, topY, 0f, cm * slopeNormR, slopeNormY, sm * slopeNormR)
            push(c1 * r, baseY, s1 * r, c1 * slopeNormR, slopeNormY, s1 * slopeNormR)
            // Bottom cap
            push(0f, baseY, 0f, 0f, -1f, 0f)
            push(c0 * r, baseY, s0 * r, 0f, -1f, 0f)
            push(c1 * r, baseY, s1 * r, 0f, -1f, 0f)
        }
        return GlMesh(data.toFloatArray())
    }

    /** Flat horizontal disc on XZ plane (Y = 0, radius = 0.5) for crisp contact shadows & halos. */
    fun createFlatDisc(segments: Int = 18): GlMesh {
        val data = ArrayList<Float>(segments * 3 * 6)
        val r = 0.5f
        fun push(x: Float, z: Float) {
            data.add(x); data.add(0f); data.add(z)
            data.add(0f); data.add(1f); data.add(0f)
        }
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * 2f * PI.toFloat()
            val a1 = ((i + 1).toFloat() / segments) * 2f * PI.toFloat()
            push(0f, 0f)
            push(cos(a1) * r, sin(a1) * r)
            push(cos(a0) * r, sin(a0) * r)
        }
        return GlMesh(data.toFloatArray())
    }

    /** Smooth low-poly UV sphere of radius 1 (used for sculpted chickens, trees, explosions, sun). */
    fun createSphere(stacks: Int = 12, slices: Int = 16): GlMesh {
        val data = ArrayList<Float>(stacks * slices * 6 * 6)
        fun push(x: Float, y: Float, z: Float) {
            val len = sqrt(x * x + y * y + z * z).coerceAtLeast(0.0001f)
            data.add(x); data.add(y); data.add(z)
            data.add(x / len); data.add(y / len); data.add(z / len)
        }
        for (i in 0 until stacks) {
            val phi0 = -PI.toFloat() / 2f + (i.toFloat() / stacks) * PI.toFloat()
            val phi1 = -PI.toFloat() / 2f + ((i + 1).toFloat() / stacks) * PI.toFloat()
            for (j in 0 until slices) {
                val th0 = (j.toFloat() / slices) * 2f * PI.toFloat()
                val th1 = ((j + 1).toFloat() / slices) * 2f * PI.toFloat()

                val x00 = cos(phi0) * cos(th0); val y00 = sin(phi0); val z00 = cos(phi0) * sin(th0)
                val x10 = cos(phi0) * cos(th1); val y10 = sin(phi0); val z10 = cos(phi0) * sin(th1)
                val x11 = cos(phi1) * cos(th1); val y11 = sin(phi1); val z11 = cos(phi1) * sin(th1)
                val x01 = cos(phi1) * cos(th0); val y01 = sin(phi1); val z01 = cos(phi1) * sin(th0)

                // Outward CCW winding for solid, properly shaded spheres
                push(x00, y00, z00); push(x11, y11, z11); push(x10, y10, z10)
                push(x00, y00, z00); push(x01, y01, z01); push(x11, y11, z11)
            }
        }
        return GlMesh(data.toFloatArray())
    }

    /**
     * Unified MAIN_ARENA open-world ground mesh (304m x 304m) tailored to the modular GLB roads,
     * central roundabout plaza, northern fortified base pad, eastern residential/garage district,
     * and southern/western stunt & demolition tarmac.
     */
    fun createArenaGround(
        map: GameMap = GameMap.MAIN_ARENA,
        halfExtent: Float = 152f,
        tileSize: Float = 4.0f
    ): GlColorMesh {
        val count = ((halfExtent * 2f) / tileSize).toInt()
        val data = ArrayList<Float>(count * count * 6 * 6)
        fun push(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float) {
            data.add(x); data.add(y); data.add(z)
            data.add(r); data.add(g); data.add(b)
        }
        for (ix in 0 until count) {
            for (iz in 0 until count) {
                val x0 = -halfExtent + ix * tileSize
                val z0 = -halfExtent + iz * tileSize
                val x1 = x0 + tileSize
                val z1 = z0 + tileSize
                val cx = (x0 + x1) * 0.5f
                val cz = (z0 + z1) * 0.5f
                val checker = ((ix + iz) and 1) == 0
                val microVar = (((ix * 37 + iz * 17) and 7) - 3) * 0.008f
                val organicPatch = (sin(cx * 0.07f + cos(cz * 0.06f)) * cos(cz * 0.07f)) * 0.042f

                val (baseR, baseG, baseB) = when (map) {
                    GameMap.MAIN_ARENA -> {
                        val distCenter = sqrt(cx * cx + cz * cz)
                        val onInnerPlaza = distCenter < 13.5f
                        val onCrossRoad = (abs(cx) < 6.0f && abs(cz) <= 56f) || (abs(cz) < 6.0f && abs(cx) <= 56f)
                        val onRingRoad = (abs(abs(cx) - 50f) < 6.0f && abs(cz) <= 56f) || (abs(abs(cz) - 50f) < 6.0f && abs(cx) <= 56f)
                        val onSidewalk = !onInnerPlaza && !onCrossRoad && !onRingRoad && (
                            (abs(cx) < 8.6f && abs(cz) <= 58f) ||
                                (abs(cz) < 8.6f && abs(cx) <= 58f) ||
                                (abs(abs(cx) - 50f) < 8.4f && abs(cz) <= 58f) ||
                                (abs(abs(cz) - 50f) < 8.4f && abs(cx) <= 58f)
                            )
                        val inNorthCompound = cx in -34f..34f && cz in -92f..-58f
                        val inEastDistrict = cx in 10f..42f && cz in -42f..42f
                        val inWestStuntPad = cx in -92f..-58f && cz in -48f..48f
                        val inSouthRange = cx in -42f..42f && cz in 58f..92f
                        val inStartGrid = abs(cx) < 6f && cz in 26f..34f

                        when {
                            inStartGrid -> if (checker) Triple(0.94f, 0.96f, 0.98f) else Triple(0.16f, 0.18f, 0.22f)
                            onInnerPlaza || onCrossRoad || onRingRoad ->
                                if (checker) Triple(0.28f, 0.30f, 0.35f) else Triple(0.25f, 0.27f, 0.32f)
                            onSidewalk ->
                                if (checker) Triple(0.74f, 0.73f, 0.69f) else Triple(0.68f, 0.67f, 0.63f)
                            inNorthCompound ->
                                if (checker) Triple(0.54f, 0.56f, 0.58f) else Triple(0.49f, 0.51f, 0.54f)
                            inWestStuntPad ->
                                if (checker) Triple(0.32f, 0.35f, 0.42f) else Triple(0.28f, 0.31f, 0.38f)
                            inSouthRange ->
                                if (checker) Triple(0.58f, 0.53f, 0.46f) else Triple(0.53f, 0.48f, 0.42f)
                            inEastDistrict ->
                                if (checker) Triple(0.32f + organicPatch * 0.5f, 0.65f + organicPatch, 0.28f)
                                else Triple(0.27f + organicPatch * 0.4f, 0.59f + organicPatch, 0.24f)
                            else ->
                                if (checker) Triple(0.29f + organicPatch * 0.5f, 0.62f + organicPatch, 0.27f)
                                else Triple(0.24f + organicPatch * 0.4f, 0.56f + organicPatch, 0.23f)
                        }
                    }
                    GameMap.TURBO_SPEEDWAY -> {
                        val distCenter = sqrt(cx * cx + cz * cz)
                        val onInnerPlaza = distCenter < 13.5f
                        val onCrossRoad = (abs(cx) < 6.0f && abs(cz) <= 66f) || (abs(cz) < 6.0f && abs(cx) <= 66f)
                        val onOvalTrack = (abs(abs(cx) - 60f) < 6.5f && abs(cz) <= 66f) || (abs(abs(cz) - 60f) < 6.5f && abs(cx) <= 66f)
                        val onRedWhiteCurb = !onOvalTrack && (
                            (abs(abs(cx) - 60f) < 9.0f && abs(cz) <= 68f) ||
                                (abs(abs(cz) - 60f) < 9.0f && abs(cx) <= 68f)
                            )
                        val inStartGrid = abs(cx) < 6f && cz in 26f..34f
                        val inPitApron = (cx in 68f..88f && cz in -34f..34f) || (cx in -88f..-68f && cz in -34f..34f)
                        val inStuntLane = abs(cx - 34f) < 5.5f && cz in -40f..44f

                        when {
                            inStartGrid -> if (checker) Triple(0.96f, 0.97f, 0.99f) else Triple(0.14f, 0.15f, 0.19f)
                            onInnerPlaza || onCrossRoad || onOvalTrack ->
                                if (checker) Triple(0.22f, 0.24f, 0.29f) else Triple(0.19f, 0.21f, 0.26f)
                            onRedWhiteCurb ->
                                if (checker) Triple(0.92f, 0.20f, 0.18f) else Triple(0.95f, 0.95f, 0.96f)
                            inPitApron || inStuntLane ->
                                if (checker) Triple(0.42f, 0.45f, 0.50f) else Triple(0.37f, 0.40f, 0.46f)
                            else ->
                                if (checker) Triple(0.34f + organicPatch * 0.4f, 0.64f + organicPatch, 0.26f)
                                else Triple(0.28f + organicPatch * 0.4f, 0.57f + organicPatch, 0.22f)
                        }
                    }
                    GameMap.TACTICAL_COMPOUND -> {
                        val distCenter = sqrt(cx * cx + cz * cz)
                        val onInnerPlaza = distCenter < 13.5f
                        val onCrossRoad = (abs(cx) < 6.0f && abs(cz) <= 58f) || (abs(cz) < 6.0f && abs(cx) <= 58f)
                        val inInnerCompound = abs(cx) <= 56f && abs(cz) <= 56f
                        val inOuterTarmac = abs(cx) <= 82f && abs(cz) <= 82f
                        val inNorthSouthGate = abs(cx) < 6.0f && (abs(cz + 56f) < 4.5f || abs(cz - 56f) < 4.5f)

                        when {
                            inNorthSouthGate -> if (checker) Triple(0.84f, 0.72f, 0.24f) else Triple(0.24f, 0.26f, 0.29f)
                            onInnerPlaza || onCrossRoad ->
                                if (checker) Triple(0.30f, 0.32f, 0.34f) else Triple(0.26f, 0.28f, 0.30f)
                            inInnerCompound ->
                                if (checker) Triple(0.50f, 0.51f, 0.49f) else Triple(0.45f, 0.46f, 0.44f)
                            inOuterTarmac ->
                                if (checker) Triple(0.56f, 0.52f, 0.44f) else Triple(0.51f, 0.47f, 0.40f)
                            else ->
                                if (checker) Triple(0.32f + organicPatch * 0.4f, 0.46f + organicPatch * 0.7f, 0.27f)
                                else Triple(0.28f + organicPatch * 0.3f, 0.41f + organicPatch * 0.6f, 0.23f)
                        }
                    }
                }

                val r = (baseR + microVar).coerceIn(0f, 1f)
                val g = (baseG + microVar).coerceIn(0f, 1f)
                val b = (baseB + microVar * 0.6f).coerceIn(0f, 1f)

                push(x0, 0f, z0, r, g, b)
                push(x0, 0f, z1, r, g, b)
                push(x1, 0f, z1, r, g, b)
                push(x0, 0f, z0, r, g, b)
                push(x1, 0f, z1, r, g, b)
                push(x1, 0f, z0, r, g, b)
            }
        }
        return GlColorMesh(data.toFloatArray())
    }

    /** Radiant atmospheric gradient sky dome styled for the arena. */
    fun createSkyDome(map: GameMap = GameMap.MAIN_ARENA, radius: Float = 380f, stacks: Int = 10, slices: Int = 24): GlColorMesh {
        val data = ArrayList<Float>(stacks * slices * 6 * 6)
        val midR = (map.skyHorizonR * 0.45f + map.skyZenithR * 0.55f)
        val midG = (map.skyHorizonG * 0.45f + map.skyZenithG * 0.55f)
        val midB = (map.skyHorizonB * 0.45f + map.skyZenithB * 0.55f)
        fun colorForHeight(normY: Float): Triple<Float, Float, Float> {
            val t = normY.coerceIn(0f, 1f)
            return if (t < 0.24f) {
                val u = t / 0.24f
                Triple(
                    map.skyHorizonR * (1f - u) + midR * u,
                    map.skyHorizonG * (1f - u) + midG * u,
                    map.skyHorizonB * (1f - u) + midB * u
                )
            } else {
                val u = (t - 0.24f) / 0.76f
                Triple(
                    midR * (1f - u) + map.skyZenithR * u,
                    midG * (1f - u) + map.skyZenithG * u,
                    midB * (1f - u) + map.skyZenithB * u
                )
            }
        }
        fun push(x: Float, y: Float, z: Float, ny: Float) {
            val (r, g, b) = colorForHeight(ny)
            data.add(x); data.add(y); data.add(z)
            data.add(r); data.add(g); data.add(b)
        }
        for (i in 0 until stacks) {
            val phi0 = -0.12f + (i.toFloat() / stacks) * (PI.toFloat() * 0.58f)
            val phi1 = -0.12f + ((i + 1).toFloat() / stacks) * (PI.toFloat() * 0.58f)
            for (j in 0 until slices) {
                val th0 = (j.toFloat() / slices) * 2f * PI.toFloat()
                val th1 = ((j + 1).toFloat() / slices) * 2f * PI.toFloat()

                val y0 = sin(phi0); val r0 = cos(phi0)
                val y1 = sin(phi1); val r1 = cos(phi1)

                val x00 = r0 * cos(th0) * radius; val z00 = r0 * sin(th0) * radius
                val x10 = r0 * cos(th1) * radius; val z10 = r0 * sin(th1) * radius
                val x11 = r1 * cos(th1) * radius; val z11 = r1 * sin(th1) * radius
                val x01 = r1 * cos(th0) * radius; val z01 = r1 * sin(th0) * radius

                push(x00, y0 * radius, z00, y0)
                push(x11, y1 * radius, z11, y1)
                push(x10, y0 * radius, z10, y0)
                push(x00, y0 * radius, z00, y0)
                push(x01, y1 * radius, z01, y1)
                push(x11, y1 * radius, z11, y1)
            }
        }
        return GlColorMesh(data.toFloatArray())
    }

    /** Stylized low-poly mountain / rolling hill silhouette ring outside the 300m arena perimeter. */
    fun createMountainRing(map: GameMap = GameMap.MAIN_ARENA, radius: Float = 260f, peaks: Int = 36): GlColorMesh {
        val data = ArrayList<Float>(peaks * 6 * 6)
        val (baseR, baseG, baseB) = when (map) {
            GameMap.MAIN_ARENA -> Triple(0.28f, 0.48f, 0.38f)
            GameMap.TURBO_SPEEDWAY -> Triple(0.48f, 0.34f, 0.30f)
            GameMap.TACTICAL_COMPOUND -> Triple(0.32f, 0.36f, 0.40f)
        }
        val (peakR, peakG, peakB) = when (map) {
            GameMap.MAIN_ARENA -> Triple(0.66f, 0.78f, 0.84f)
            GameMap.TURBO_SPEEDWAY -> Triple(0.92f, 0.68f, 0.52f)
            GameMap.TACTICAL_COMPOUND -> Triple(0.64f, 0.68f, 0.74f)
        }
        val heightScale = 0.85f
        fun push(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float) {
            data.add(x); data.add(y); data.add(z)
            data.add(r); data.add(g); data.add(b)
        }
        for (i in 0 until peaks) {
            val a0 = (i.toFloat() / peaks) * 2f * PI.toFloat()
            val a1 = ((i + 1).toFloat() / peaks) * 2f * PI.toFloat()
            val am = (a0 + a1) * 0.5f
            val peakH = (38f + ((i * 19 + 7) % 5) * 11f) * heightScale
            val x0 = cos(a0) * radius; val z0 = sin(a0) * radius
            val x1 = cos(a1) * radius; val z1 = sin(a1) * radius
            val xm = cos(am) * (radius - 10f); val zm = sin(am) * (radius - 10f)

            push(x0, -3f, z0, baseR, baseG, baseB)
            push(xm, peakH, zm, peakR, peakG, peakB)
            push(x1, -3f, z1, baseR, baseG, baseB)
        }
        return GlColorMesh(data.toFloatArray())
    }
}

