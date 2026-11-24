package com.example.renderer

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Complete, fast, zero-dependency glTF 2.0 Binary (.glb) parser and OpenGL ES 2.0 GPU uploader.
 *
 * Features:
 * - Reads interleaved/tightly-packed POSITION, NORMAL, TEXCOORD_0 & indices (UNSIGNED_BYTE/SHORT/INT)
 * - Reads PBR materials (baseColorFactor, baseColorTexture, emissiveFactor, metallic/roughness, alphaMode)
 * - Decodes embedded PNG/JPG textures from GLB binary chunk directly into GL_TEXTURE_2D with mipmaps
 * - Supports full node hierarchies (both 4x4 `matrix` and TRS `translation`/`rotation`/`scale`)
 * - Evaluates glTF keyframe animations (`STEP` / `LINEAR`, quaternion SLERP, smooth looping)
 * - Supports runtime procedural node overrides (wheel spin/steer, sirens, pendulum/crusher/spinner traps,
 *   hidden nodes like RPG rocket, and world-space anchor capture like `weapon_slot` / `muzzle`)
 */
enum class GlbAlphaMode { OPAQUE, MASK, BLEND }

class GlbMaterialData(
    val baseR: Float,
    val baseG: Float,
    val baseB: Float,
    val baseA: Float,
    val emissiveR: Float,
    val emissiveG: Float,
    val emissiveB: Float,
    val specStrength: Float,
    val textureImageIndex: Int,
    val doubleSided: Boolean,
    val alphaBlend: Boolean,
    val alphaMode: GlbAlphaMode = if (alphaBlend) GlbAlphaMode.BLEND else GlbAlphaMode.OPAQUE,
    val alphaCutoff: Float = 0.5f
)

class GlbPrimitiveCpu(
    val interleavedVertices: FloatArray, // [px,py,pz, nx,ny,nz, u,v] = 8 floats per vertex
    val indices: ShortArray?,
    val vertexCount: Int,
    val materialIndex: Int
)

class GlbMeshData(
    val primitives: List<GlbPrimitiveCpu>
)

class GlbNodeData(
    val index: Int,
    val name: String,
    val meshIndex: Int,
    val children: IntArray,
    val hasMatrix: Boolean,
    val localMatrix: FloatArray, // 16 floats if hasMatrix
    val baseTranslation: FloatArray, // 3 floats
    val baseRotation: FloatArray, // 4 floats (x,y,z,w)
    val baseScale: FloatArray // 3 floats
)

enum class GlbAnimPath { TRANSLATION, ROTATION, SCALE }

class GlbAnimChannel(
    val nodeIndex: Int,
    val path: GlbAnimPath,
    val times: FloatArray,
    val values: FloatArray, // 3 floats per key for T/S, 4 floats per key for R
    val step: Boolean
)

class GlbAnimationData(
    val name: String,
    val duration: Float,
    val channels: List<GlbAnimChannel>
)

class GlbImageBytes(
    val bytes: ByteArray
)

/** CPU-parsed representation of a .glb asset (thread-safe to parse off-GL or test under Robolectric). */
class GlbModelCpu(
    val assetPath: String,
    val nodes: List<GlbNodeData>,
    val rootNodes: IntArray,
    val meshes: List<GlbMeshData>,
    val materials: List<GlbMaterialData>,
    val images: List<GlbImageBytes>,
    val animations: Map<String, GlbAnimationData>,
    val nodeNameToIndex: Map<String, Int>,
    val minY: Float,
    val maxY: Float,
    val halfExtentX: Float,
    val halfExtentZ: Float
)

/** GPU-uploaded primitive with cached VBO + optional IBO. */
class GlbGpuPrimitive(
    val vboId: Int,
    val iboId: Int,
    val indexCount: Int,
    val vertexCount: Int,
    val fallbackVerts: FloatBuffer?,
    val fallbackIndices: ShortBuffer?,
    val materialIndex: Int
)

/** GPU-uploaded model cached per GL context. */
class GlbGpuModel(
    val cpu: GlbModelCpu,
    val gpuMeshes: List<List<GlbGpuPrimitive>>,
    val textureIds: IntArray
) {
    fun releaseGl() {
        val bufs = ArrayList<Int>()
        for (meshPrims in gpuMeshes) {
            for (p in meshPrims) {
                if (p.vboId != 0) bufs.add(p.vboId)
                if (p.iboId != 0) bufs.add(p.iboId)
            }
        }
        if (bufs.isNotEmpty()) {
            val arr = bufs.toIntArray()
            GLES20.glDeleteBuffers(arr.size, arr, 0)
        }
        val validTex = textureIds.filter { it != 0 }.toIntArray()
        if (validTex.isNotEmpty()) {
            GLES20.glDeleteTextures(validTex.size, validTex, 0)
        }
    }
}

/**
 * Reusable zero-allocation evaluation workspace for animating & drawing a GlbGpuModel.
 */
class GlbNodeOverrides {
    val hiddenNodes = HashSet<String>(4)
    // Euler degrees (rx, ry, rz) post-multiplied in local node space
    val extraRotDeg = HashMap<String, FloatArray>(8)
    // Local translation delta (tx, ty, tz)
    val extraTrans = HashMap<String, FloatArray>(4)
    // Emissive override RGB (r, g, b) for flashing sirens, glowing fire, etc.
    val emissiveBoost = HashMap<String, FloatArray>(4)
    // Optional world-matrix capture slots by node name (e.g., "weapon_slot", "muzzle")
    val captureWorldMatrix = HashMap<String, FloatArray>(4)

    fun clear() {
        hiddenNodes.clear()
        extraRotDeg.clear()
        extraTrans.clear()
        emissiveBoost.clear()
        captureWorldMatrix.clear()
    }
}

object GlbModelLoader {
    private const val TAG = "GlbModelLoader"
    private const val GLB_MAGIC = 0x46546C67 // "glTF"
    private const val CHUNK_JSON = 0x4E4F534A // "JSON"
    private const val CHUNK_BIN = 0x004E4942  // "BIN\0"

    fun parseFromAssets(assets: AssetManager, assetPath: String): GlbModelCpu? {
        return try {
            val bytes = assets.open(assetPath).use { it.readBytes() }
            parseBytes(bytes, assetPath)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read GLB asset: $assetPath (${t.message})")
            null
        }
    }

    fun parseBytes(bytes: ByteArray, assetPath: String): GlbModelCpu? {
        if (bytes.size < 20) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.getInt(0)
        val version = buf.getInt(4)
        if (magic != GLB_MAGIC || version != 2) {
            Log.w(TAG, "Invalid GLB header in $assetPath (magic=$magic, ver=$version)")
            return null
        }

        var offset = 12
        var jsonStr: String? = null
        var binOffset = -1
        var binLength = 0

        while (offset + 8 <= bytes.size) {
            val chunkLen = buf.getInt(offset)
            val chunkType = buf.getInt(offset + 4)
            val dataStart = offset + 8
            if (chunkLen <= 0 || dataStart + chunkLen > bytes.size) break
            when (chunkType) {
                CHUNK_JSON -> jsonStr = String(bytes, dataStart, chunkLen, Charsets.UTF_8)
                CHUNK_BIN -> {
                    binOffset = dataStart
                    binLength = chunkLen
                }
            }
            offset = dataStart + chunkLen
        }

        val rootJsonStr = jsonStr ?: return null
        val json = JSONObject(rootJsonStr)
        val bufferViews = json.optJSONArray("bufferViews") ?: JSONArray()
        val accessors = json.optJSONArray("accessors") ?: JSONArray()

        // 1. Embedded images
        val imagesList = ArrayList<GlbImageBytes>()
        val imagesArr = json.optJSONArray("images")
        if (imagesArr != null && binOffset >= 0) {
            for (i in 0 until imagesArr.length()) {
                val imgObj = imagesArr.getJSONObject(i)
                val bvIndex = imgObj.optInt("bufferView", -1)
                if (bvIndex >= 0 && bvIndex < bufferViews.length()) {
                    val bv = bufferViews.getJSONObject(bvIndex)
                    val bvOff = bv.optInt("byteOffset", 0)
                    val bvLen = bv.optInt("byteLength", 0)
                    val start = binOffset + bvOff
                    if (start >= 0 && start + bvLen <= bytes.size && bvLen > 0) {
                        imagesList.add(GlbImageBytes(bytes.copyOfRange(start, start + bvLen)))
                    } else {
                        imagesList.add(GlbImageBytes(ByteArray(0)))
                    }
                } else {
                    imagesList.add(GlbImageBytes(ByteArray(0)))
                }
            }
        }

        // 2. Texture -> Image index map
        val texToImage = IntArray(json.optJSONArray("textures")?.length() ?: 0) { -1 }
        val texturesArr = json.optJSONArray("textures")
        if (texturesArr != null) {
            for (i in 0 until texturesArr.length()) {
                texToImage[i] = texturesArr.getJSONObject(i).optInt("source", -1)
            }
        }

        // 3. Materials
        val materialsList = ArrayList<GlbMaterialData>()
        val matsArr = json.optJSONArray("materials")
        if (matsArr != null) {
            for (i in 0 until matsArr.length()) {
                val m = matsArr.getJSONObject(i)
                val pbr = m.optJSONObject("pbrMetallicRoughness")
                val bcf = pbr?.optJSONArray("baseColorFactor")
                val br = bcf?.optDouble(0, 1.0)?.toFloat() ?: 1f
                val bg = bcf?.optDouble(1, 1.0)?.toFloat() ?: 1f
                val bb = bcf?.optDouble(2, 1.0)?.toFloat() ?: 1f
                val ba = bcf?.optDouble(3, 1.0)?.toFloat() ?: 1f

                val texIdx = pbr?.optJSONObject("baseColorTexture")?.optInt("index", -1) ?: -1
                val imgIdx = if (texIdx in texToImage.indices) texToImage[texIdx] else -1

                val roughness = pbr?.optDouble("roughnessFactor", 0.65)?.toFloat()?.coerceIn(0.05f, 1f) ?: 0.65f
                val metallic = pbr?.optDouble("metallicFactor", 0.1)?.toFloat()?.coerceIn(0f, 1f) ?: 0.1f
                val specStrength = ((1f - roughness) * 0.55f + metallic * 0.35f).coerceIn(0.04f, 0.90f)

                val ef = m.optJSONArray("emissiveFactor")
                val er = ef?.optDouble(0, 0.0)?.toFloat() ?: 0f
                val eg = ef?.optDouble(1, 0.0)?.toFloat() ?: 0f
                val eb = ef?.optDouble(2, 0.0)?.toFloat() ?: 0f

                val doubleSided = m.optBoolean("doubleSided", false)
                val alphaModeStr = m.optString("alphaMode", "OPAQUE")
                val parsedAlphaMode = when (alphaModeStr) {
                    "MASK" -> GlbAlphaMode.MASK
                    "BLEND" -> GlbAlphaMode.BLEND
                    else -> GlbAlphaMode.OPAQUE
                }
                val alphaCutoff = m.optDouble("alphaCutoff", 0.5).toFloat().coerceIn(0.01f, 0.99f)
                val alphaBlend = parsedAlphaMode == GlbAlphaMode.BLEND

                materialsList.add(
                    GlbMaterialData(
                        baseR = br, baseG = bg, baseB = bb, baseA = ba,
                        emissiveR = er, emissiveG = eg, emissiveB = eb,
                        specStrength = specStrength,
                        textureImageIndex = imgIdx,
                        doubleSided = doubleSided,
                        alphaBlend = alphaBlend,
                        alphaMode = parsedAlphaMode,
                        alphaCutoff = alphaCutoff
                    )
                )
            }
        }
        if (materialsList.isEmpty()) {
            materialsList.add(
                GlbMaterialData(
                    baseR = 1f, baseG = 1f, baseB = 1f, baseA = 1f,
                    emissiveR = 0f, emissiveG = 0f, emissiveB = 0f,
                    specStrength = 0.25f,
                    textureImageIndex = -1,
                    doubleSided = false,
                    alphaBlend = false,
                    alphaMode = GlbAlphaMode.OPAQUE,
                    alphaCutoff = 0.5f
                )
            )
        }

        // Helper to read float accessor
        fun readFloatAccessor(accIndex: Int, components: Int): FloatArray? {
            if (accIndex < 0 || accIndex >= accessors.length() || binOffset < 0) return null
            val acc = accessors.getJSONObject(accIndex)
            val bvIndex = acc.optInt("bufferView", -1)
            if (bvIndex < 0 || bvIndex >= bufferViews.length()) return null
            val bv = bufferViews.getJSONObject(bvIndex)
            val count = acc.optInt("count", 0)
            val compType = acc.optInt("componentType", 5126)
            val bvOffset = bv.optInt("byteOffset", 0)
            val accOffset = acc.optInt("byteOffset", 0)
            val elemSize = when (compType) {
                5126 -> 4 // FLOAT
                5123, 5122 -> 2 // UNSIGNED_SHORT / SHORT
                5121, 5120 -> 1 // UNSIGNED_BYTE / BYTE
                else -> 4
            }
            val stride = bv.optInt("byteStride", 0).let { if (it > 0) it else components * elemSize }
            val normalized = acc.optBoolean("normalized", false)
            val out = FloatArray(count * components)
            val baseStart = binOffset + bvOffset + accOffset
            for (i in 0 until count) {
                val p = baseStart + i * stride
                if (p + components * elemSize > bytes.size) break
                for (c in 0 until components) {
                    val cp = p + c * elemSize
                    out[i * components + c] = when (compType) {
                        5126 -> buf.getFloat(cp)
                        5123 -> {
                            val v = (buf.getShort(cp).toInt() and 0xFFFF).toFloat()
                            if (normalized) v / 65535f else v
                        }
                        5122 -> {
                            val v = buf.getShort(cp).toFloat()
                            if (normalized) (v / 32767f).coerceAtLeast(-1f) else v
                        }
                        5121 -> {
                            val v = (bytes[cp].toInt() and 0xFF).toFloat()
                            if (normalized) v / 255f else v
                        }
                        5120 -> {
                            val v = bytes[cp].toFloat()
                            if (normalized) (v / 127f).coerceAtLeast(-1f) else v
                        }
                        else -> 0f
                    }
                }
            }
            return out
        }

        fun readIndexAccessor(accIndex: Int): IntArray? {
            if (accIndex < 0 || accIndex >= accessors.length() || binOffset < 0) return null
            val acc = accessors.getJSONObject(accIndex)
            val bvIndex = acc.optInt("bufferView", -1)
            if (bvIndex < 0 || bvIndex >= bufferViews.length()) return null
            val bv = bufferViews.getJSONObject(bvIndex)
            val count = acc.optInt("count", 0)
            val compType = acc.optInt("componentType", 5123)
            val bvOffset = bv.optInt("byteOffset", 0)
            val accOffset = acc.optInt("byteOffset", 0)
            val elemSize = when (compType) {
                5121 -> 1
                5123 -> 2
                5125 -> 4
                else -> 2
            }
            val stride = bv.optInt("byteStride", 0).let { if (it > 0) it else elemSize }
            val out = IntArray(count)
            val baseStart = binOffset + bvOffset + accOffset
            for (i in 0 until count) {
                val p = baseStart + i * stride
                if (p + elemSize > bytes.size) break
                out[i] = when (compType) {
                    5121 -> bytes[p].toInt() and 0xFF
                    5123 -> buf.getShort(p).toInt() and 0xFFFF
                    5125 -> buf.getInt(p)
                    else -> 0
                }
            }
            return out
        }

        // 4. Meshes & bounding box
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxAbsX = 0f
        var maxAbsZ = 0f

        val meshesList = ArrayList<GlbMeshData>()
        val meshesArr = json.optJSONArray("meshes")
        if (meshesArr != null) {
            for (mi in 0 until meshesArr.length()) {
                val meshObj = meshesArr.getJSONObject(mi)
                val primsArr = meshObj.optJSONArray("primitives") ?: JSONArray()
                val primList = ArrayList<GlbPrimitiveCpu>(primsArr.length())
                for (pi in 0 until primsArr.length()) {
                    val primObj = primsArr.getJSONObject(pi)
                    val mode = primObj.optInt("mode", 4)
                    if (mode != 4) continue // Only GL_TRIANGLES
                    val attrs = primObj.optJSONObject("attributes") ?: continue
                    val posIdx = attrs.optInt("POSITION", -1)
                    val pos = readFloatAccessor(posIdx, 3) ?: continue
                    val vCount = pos.size / 3
                    if (vCount <= 0) continue

                    val normIdx = attrs.optInt("NORMAL", -1)
                    val norm = readFloatAccessor(normIdx, 3)
                    val uvIdx = attrs.optInt("TEXCOORD_0", -1)
                    val uv = readFloatAccessor(uvIdx, 2)
                    val idxArr = if (primObj.has("indices")) readIndexAccessor(primObj.getInt("indices")) else null
                    val matIdx = primObj.optInt("material", 0).coerceIn(0, materialsList.size - 1)

                    for (v in 0 until vCount) {
                        val px = pos[v * 3]
                        val py = pos[v * 3 + 1]
                        val pz = pos[v * 3 + 2]
                        if (py < minY) minY = py
                        if (py > maxY) maxY = py
                        if (abs(px) > maxAbsX) maxAbsX = abs(px)
                        if (abs(pz) > maxAbsZ) maxAbsZ = abs(pz)
                    }

                    // If indices fit in unsigned short (all user GLBs have maxVerts <= 4848!), use compact indexed VBO!
                    val canUseShortIndices = idxArr != null && vCount <= 65535
                    if (canUseShortIndices && idxArr != null) {
                        val interleaved = FloatArray(vCount * 8)
                        for (v in 0 until vCount) {
                            val d = v * 8
                            interleaved[d] = pos[v * 3]
                            interleaved[d + 1] = pos[v * 3 + 1]
                            interleaved[d + 2] = pos[v * 3 + 2]
                            if (norm != null && v * 3 + 2 < norm.size) {
                                interleaved[d + 3] = norm[v * 3]
                                interleaved[d + 4] = norm[v * 3 + 1]
                                interleaved[d + 5] = norm[v * 3 + 2]
                            } else {
                                interleaved[d + 3] = 0f
                                interleaved[d + 4] = 1f
                                interleaved[d + 5] = 0f
                            }
                            if (uv != null && v * 2 + 1 < uv.size) {
                                interleaved[d + 6] = uv[v * 2]
                                interleaved[d + 7] = uv[v * 2 + 1]
                            } else {
                                interleaved[d + 6] = 0f
                                interleaved[d + 7] = 0f
                            }
                        }
                        if (norm == null) {
                            // Compute smooth vertex normals from triangles when NORMAL attribute is omitted
                            var ti = 0
                            while (ti + 2 < idxArr.size) {
                                val i0 = idxArr[ti]
                                val i1 = idxArr[ti + 1]
                                val i2 = idxArr[ti + 2]
                                if (i0 in 0 until vCount && i1 in 0 until vCount && i2 in 0 until vCount) {
                                    val ax = pos[i0 * 3]; val ay = pos[i0 * 3 + 1]; val az = pos[i0 * 3 + 2]
                                    val ux = pos[i1 * 3] - ax; val uy = pos[i1 * 3 + 1] - ay; val uz = pos[i1 * 3 + 2] - az
                                    val vx = pos[i2 * 3] - ax; val vy = pos[i2 * 3 + 1] - ay; val vz = pos[i2 * 3 + 2] - az
                                    val nx = uy * vz - uz * vy
                                    val ny = uz * vx - ux * vz
                                    val nz = ux * vy - uy * vx
                                    val len = sqrt(nx * nx + ny * ny + nz * nz)
                                    if (len > 1e-6f) {
                                        val fnx = nx / len; val fny = ny / len; val fnz = nz / len
                                        interleaved[i0 * 8 + 3] = fnx; interleaved[i0 * 8 + 4] = fny; interleaved[i0 * 8 + 5] = fnz
                                        interleaved[i1 * 8 + 3] = fnx; interleaved[i1 * 8 + 4] = fny; interleaved[i1 * 8 + 5] = fnz
                                        interleaved[i2 * 8 + 3] = fnx; interleaved[i2 * 8 + 4] = fny; interleaved[i2 * 8 + 5] = fnz
                                    }
                                }
                                ti += 3
                            }
                        }
                        val shortIdx = ShortArray(idxArr.size) { idxArr[it].toShort() }
                        primList.add(GlbPrimitiveCpu(interleaved, shortIdx, vCount, matIdx))
                    } else {
                        // De-index into flat triangle array (handles both non-indexed primitives and >65k verts)
                        val count = idxArr?.size ?: vCount
                        val interleaved = FloatArray(count * 8)
                        for (k in 0 until count) {
                            val v = idxArr?.get(k) ?: k
                            if (v * 3 + 2 >= pos.size) continue
                            val d = k * 8
                            interleaved[d] = pos[v * 3]
                            interleaved[d + 1] = pos[v * 3 + 1]
                            interleaved[d + 2] = pos[v * 3 + 2]
                            if (norm != null && v * 3 + 2 < norm.size) {
                                interleaved[d + 3] = norm[v * 3]
                                interleaved[d + 4] = norm[v * 3 + 1]
                                interleaved[d + 5] = norm[v * 3 + 2]
                            } else {
                                interleaved[d + 3] = 0f
                                interleaved[d + 4] = 1f
                                interleaved[d + 5] = 0f
                            }
                            if (uv != null && v * 2 + 1 < uv.size) {
                                interleaved[d + 6] = uv[v * 2]
                                interleaved[d + 7] = uv[v * 2 + 1]
                            }
                        }
                        if (norm == null) {
                            var k = 0
                            while (k + 2 < count) {
                                val d0 = k * 8; val d1 = (k + 1) * 8; val d2 = (k + 2) * 8
                                val ux = interleaved[d1] - interleaved[d0]
                                val uy = interleaved[d1 + 1] - interleaved[d0 + 1]
                                val uz = interleaved[d1 + 2] - interleaved[d0 + 2]
                                val vx = interleaved[d2] - interleaved[d0]
                                val vy = interleaved[d2 + 1] - interleaved[d0 + 1]
                                val vz = interleaved[d2 + 2] - interleaved[d0 + 2]
                                val nx = uy * vz - uz * vy
                                val ny = uz * vx - ux * vz
                                val nz = ux * vy - uy * vx
                                val len = sqrt(nx * nx + ny * ny + nz * nz)
                                if (len > 1e-6f) {
                                    val fnx = nx / len; val fny = ny / len; val fnz = nz / len
                                    interleaved[d0 + 3] = fnx; interleaved[d0 + 4] = fny; interleaved[d0 + 5] = fnz
                                    interleaved[d1 + 3] = fnx; interleaved[d1 + 4] = fny; interleaved[d1 + 5] = fnz
                                    interleaved[d2 + 3] = fnx; interleaved[d2 + 4] = fny; interleaved[d2 + 5] = fnz
                                }
                                k += 3
                            }
                        }
                        primList.add(GlbPrimitiveCpu(interleaved, null, count, matIdx))
                    }
                }
                meshesList.add(GlbMeshData(primList))
            }
        }

        // 5. Node hierarchy
        val nodesList = ArrayList<GlbNodeData>()
        val nodeNameMap = HashMap<String, Int>()
        val hasParent = HashSet<Int>()
        val nodesArr = json.optJSONArray("nodes")
        if (nodesArr != null) {
            for (ni in 0 until nodesArr.length()) {
                val n = nodesArr.getJSONObject(ni)
                val name = n.optString("name", "")
                if (name.isNotEmpty()) {
                    nodeNameMap[name] = ni
                }
                val meshIdx = n.optInt("mesh", -1)
                val chArr = n.optJSONArray("children")
                val children = if (chArr != null) {
                    IntArray(chArr.length()) { ci ->
                        val c = chArr.getInt(ci)
                        hasParent.add(c)
                        c
                    }
                } else IntArray(0)

                val matArr = n.optJSONArray("matrix")
                val hasMat = matArr != null && matArr.length() == 16
                val localMat = FloatArray(16)
                if (hasMat && matArr != null) {
                    for (k in 0 until 16) localMat[k] = matArr.getDouble(k).toFloat()
                } else {
                    Matrix.setIdentityM(localMat, 0)
                }

                val tArr = n.optJSONArray("translation")
                val trans = floatArrayOf(
                    tArr?.optDouble(0, 0.0)?.toFloat() ?: 0f,
                    tArr?.optDouble(1, 0.0)?.toFloat() ?: 0f,
                    tArr?.optDouble(2, 0.0)?.toFloat() ?: 0f
                )
                val rArr = n.optJSONArray("rotation")
                val rot = floatArrayOf(
                    rArr?.optDouble(0, 0.0)?.toFloat() ?: 0f,
                    rArr?.optDouble(1, 0.0)?.toFloat() ?: 0f,
                    rArr?.optDouble(2, 0.0)?.toFloat() ?: 0f,
                    rArr?.optDouble(3, 1.0)?.toFloat() ?: 1f
                )
                val sArr = n.optJSONArray("scale")
                val scl = floatArrayOf(
                    sArr?.optDouble(0, 1.0)?.toFloat() ?: 1f,
                    sArr?.optDouble(1, 1.0)?.toFloat() ?: 1f,
                    sArr?.optDouble(2, 1.0)?.toFloat() ?: 1f
                )

                nodesList.add(
                    GlbNodeData(
                        index = ni,
                        name = name,
                        meshIndex = meshIdx,
                        children = children,
                        hasMatrix = hasMat,
                        localMatrix = localMat,
                        baseTranslation = trans,
                        baseRotation = rot,
                        baseScale = scl
                    )
                )
            }
        }

        // Determine root nodes from active scene or parentless nodes
        val scenesArr = json.optJSONArray("scenes")
        val defaultScene = json.optInt("scene", 0)
        val rootNodes: IntArray = if (scenesArr != null && defaultScene in 0 until scenesArr.length()) {
            val scNodes = scenesArr.getJSONObject(defaultScene).optJSONArray("nodes")
            if (scNodes != null) IntArray(scNodes.length()) { scNodes.getInt(it) }
            else nodesList.indices.filter { it !in hasParent }.toIntArray()
        } else {
            nodesList.indices.filter { it !in hasParent }.toIntArray()
        }

        // 6. Keyframe animations
        val animMap = HashMap<String, GlbAnimationData>()
        val animsArr = json.optJSONArray("animations")
        if (animsArr != null) {
            for (ai in 0 until animsArr.length()) {
                val aObj = animsArr.getJSONObject(ai)
                val aName = aObj.optString("name", "anim_$ai")
                val samplers = aObj.optJSONArray("samplers") ?: continue
                val channels = aObj.optJSONArray("channels") ?: continue
                val parsedChannels = ArrayList<GlbAnimChannel>(channels.length())
                var maxTime = 0.001f

                for (ci in 0 until channels.length()) {
                    val chObj = channels.getJSONObject(ci)
                    val target = chObj.optJSONObject("target") ?: continue
                    val targetNode = target.optInt("node", -1)
                    if (targetNode !in nodesList.indices) continue
                    val path = when (target.optString("path")) {
                        "translation" -> GlbAnimPath.TRANSLATION
                        "rotation" -> GlbAnimPath.ROTATION
                        "scale" -> GlbAnimPath.SCALE
                        else -> continue
                    }
                    val samplerIdx = chObj.optInt("sampler", -1)
                    if (samplerIdx !in 0 until samplers.length()) continue
                    val sampler = samplers.getJSONObject(samplerIdx)
                    val times = readFloatAccessor(sampler.optInt("input", -1), 1) ?: continue
                    val compCount = if (path == GlbAnimPath.ROTATION) 4 else 3
                    val values = readFloatAccessor(sampler.optInt("output", -1), compCount) ?: continue
                    val step = sampler.optString("interpolation", "LINEAR") == "STEP"
                    if (times.isNotEmpty() && times.last() > maxTime) {
                        maxTime = times.last()
                    }
                    parsedChannels.add(GlbAnimChannel(targetNode, path, times, values, step))
                }
                animMap[aName] = GlbAnimationData(aName, maxTime, parsedChannels)
            }
        }

        if (minY.isInfinite()) minY = 0f
        if (maxY.isInfinite()) maxY = 1f

        return GlbModelCpu(
            assetPath = assetPath,
            nodes = nodesList,
            rootNodes = rootNodes,
            meshes = meshesList,
            materials = materialsList,
            images = imagesList,
            animations = animMap,
            nodeNameToIndex = nodeNameMap,
            minY = minY,
            maxY = maxY,
            halfExtentX = maxAbsX,
            halfExtentZ = maxAbsZ
        )
    }

    /** Uploads CPU model to OpenGL ES 2.0 VBOs, IBOs & Textures. Must be called on the GL thread. */
    fun uploadToGpu(cpu: GlbModelCpu): GlbGpuModel {
        // Upload embedded textures
        val texIds = IntArray(cpu.images.size)
        for (i in cpu.images.indices) {
            val raw = cpu.images[i].bytes
            if (raw.isEmpty()) continue
            val bmp = try {
                val opts = BitmapFactory.Options().apply { inScaled = false }
                BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
            } catch (_: Throwable) {
                null
            } ?: continue

            val idArr = IntArray(1)
            GLES20.glGenTextures(1, idArr, 0)
            val texId = idArr[0]
            if (texId != 0) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                val isPot = (bmp.width and (bmp.width - 1)) == 0 && (bmp.height and (bmp.height - 1)) == 0
                if (isPot) {
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
                    GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
                } else {
                    // OpenGL ES 2.0 strictly requires CLAMP_TO_EDGE on NPOT textures to avoid black rendering
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                texIds[i] = texId
            }
            bmp.recycle()
        }

        // Upload meshes to VBO + IBO
        val gpuMeshes = ArrayList<List<GlbGpuPrimitive>>(cpu.meshes.size)
        for (mesh in cpu.meshes) {
            val prims = ArrayList<GlbGpuPrimitive>(mesh.primitives.size)
            for (p in mesh.primitives) {
                val vBuf = ByteBuffer.allocateDirect(p.interleavedVertices.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(p.interleavedVertices)
                        position(0)
                    }
                val vboArr = IntArray(1)
                GLES20.glGenBuffers(1, vboArr, 0)
                val vboId = vboArr[0]
                if (vboId != 0) {
                    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
                    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, p.interleavedVertices.size * 4, vBuf, GLES20.GL_STATIC_DRAW)
                    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
                }

                var iboId = 0
                var fallbackIdx: ShortBuffer? = null
                val idxArr = p.indices
                if (idxArr != null) {
                    val iBuf = ByteBuffer.allocateDirect(idxArr.size * 2)
                        .order(ByteOrder.nativeOrder())
                        .asShortBuffer()
                        .apply {
                            put(idxArr)
                            position(0)
                        }
                    val iboIds = IntArray(1)
                    GLES20.glGenBuffers(1, iboIds, 0)
                    if (iboIds[0] != 0) {
                        iboId = iboIds[0]
                        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId)
                        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idxArr.size * 2, iBuf, GLES20.GL_STATIC_DRAW)
                        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
                    } else {
                        fallbackIdx = iBuf
                    }
                }

                prims.add(
                    GlbGpuPrimitive(
                        vboId = vboId,
                        iboId = iboId,
                        indexCount = idxArr?.size ?: 0,
                        vertexCount = p.vertexCount,
                        fallbackVerts = if (vboId == 0) vBuf else null,
                        fallbackIndices = fallbackIdx,
                        materialIndex = p.materialIndex
                    )
                )
            }
            gpuMeshes.add(prims)
        }

        return GlbGpuModel(cpu, gpuMeshes, texIds)
    }

    /** Evaluates TRS matrix from translation, quaternion (x,y,z,w), and scale into `outMat` (16 floats). */
    fun composeTrsMatrix(
        outMat: FloatArray,
        tx: Float, ty: Float, tz: Float,
        qx: Float, qy: Float, qz: Float, qw: Float,
        sx: Float, sy: Float, sz: Float
    ) {
        val xx = qx * qx
        val yy = qy * qy
        val zz = qz * qz
        val xy = qx * qy
        val xz = qx * qz
        val yz = qy * qz
        val wx = qw * qx
        val wy = qw * qy
        val wz = qw * qz

        outMat[0] = (1f - 2f * (yy + zz)) * sx
        outMat[1] = (2f * (xy + wz)) * sx
        outMat[2] = (2f * (xz - wy)) * sx
        outMat[3] = 0f

        outMat[4] = (2f * (xy - wz)) * sy
        outMat[5] = (1f - 2f * (xx + zz)) * sy
        outMat[6] = (2f * (yz + wx)) * sy
        outMat[7] = 0f

        outMat[8] = (2f * (xz + wy)) * sz
        outMat[9] = (2f * (yz - wx)) * sz
        outMat[10] = (1f - 2f * (xx + yy)) * sz
        outMat[11] = 0f

        outMat[12] = tx
        outMat[13] = ty
        outMat[14] = tz
        outMat[15] = 1f
    }

    fun sampleChannelVec3(ch: GlbAnimChannel, t: Float, out: FloatArray) {
        val times = ch.times
        val vals = ch.values
        val n = times.size
        if (n == 0) return
        if (n == 1 || t <= times[0]) {
            out[0] = vals[0]; out[1] = vals[1]; out[2] = vals[2]
            return
        }
        if (t >= times[n - 1]) {
            val base = (n - 1) * 3
            out[0] = vals[base]; out[1] = vals[base + 1]; out[2] = vals[base + 2]
            return
        }
        var lo = 0
        var hi = n - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t) lo = mid else hi = mid
        }
        val b0 = lo * 3
        if (ch.step) {
            out[0] = vals[b0]; out[1] = vals[b0 + 1]; out[2] = vals[b0 + 2]
            return
        }
        val b1 = hi * 3
        val span = (times[hi] - times[lo]).coerceAtLeast(0.00001f)
        val u = ((t - times[lo]) / span).coerceIn(0f, 1f)
        out[0] = vals[b0] + (vals[b1] - vals[b0]) * u
        out[1] = vals[b0 + 1] + (vals[b1 + 1] - vals[b0 + 1]) * u
        out[2] = vals[b0 + 2] + (vals[b1 + 2] - vals[b0 + 2]) * u
    }

    fun sampleChannelQuat(ch: GlbAnimChannel, t: Float, out: FloatArray) {
        val times = ch.times
        val vals = ch.values
        val n = times.size
        if (n == 0) return
        if (n == 1 || t <= times[0]) {
            out[0] = vals[0]; out[1] = vals[1]; out[2] = vals[2]; out[3] = vals[3]
            return
        }
        if (t >= times[n - 1]) {
            val b = (n - 1) * 4
            out[0] = vals[b]; out[1] = vals[b + 1]; out[2] = vals[b + 2]; out[3] = vals[b + 3]
            return
        }
        var lo = 0
        var hi = n - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t) lo = mid else hi = mid
        }
        val b0 = lo * 4
        if (ch.step) {
            out[0] = vals[b0]; out[1] = vals[b0 + 1]; out[2] = vals[b0 + 2]; out[3] = vals[b0 + 3]
            return
        }
        val b1 = hi * 4
        val span = (times[hi] - times[lo]).coerceAtLeast(0.00001f)
        val u = ((t - times[lo]) / span).coerceIn(0f, 1f)

        val ax = vals[b0]; val ay = vals[b0 + 1]; val az = vals[b0 + 2]; val aw = vals[b0 + 3]
        var bx = vals[b1]; var by = vals[b1 + 1]; var bz = vals[b1 + 2]; var bw = vals[b1 + 3]

        var dot = ax * bx + ay * by + az * bz + aw * bw
        if (dot < 0f) {
            dot = -dot
            bx = -bx; by = -by; bz = -bz; bw = -bw
        }
        val s0: Float
        val s1: Float
        if (dot > 0.9995f) {
            s0 = 1f - u
            s1 = u
        } else {
            val theta = acos(dot.coerceIn(-1f, 1f))
            val sinTheta = sin(theta).coerceAtLeast(0.0001f)
            s0 = sin((1f - u) * theta) / sinTheta
            s1 = sin(u * theta) / sinTheta
        }
        var rx = ax * s0 + bx * s1
        var ry = ay * s0 + by * s1
        var rz = az * s0 + bz * s1
        var rw = aw * s0 + bw * s1
        val len = sqrt(rx * rx + ry * ry + rz * rz + rw * rw).coerceAtLeast(0.0001f)
        out[0] = rx / len
        out[1] = ry / len
        out[2] = rz / len
        out[3] = rw / len
    }
}
