package com.example.engine

import kotlin.math.floor

/**
 * Zero-GC 2D uniform spatial hash grid for fast O(1) broad-phase prop queries
 * (surface height, ceiling height, horizontal & vehicle collisions, bullet/LOS hit tests, doors).
 *
 * Covers the full arena [-156f, +156f] on X and Z with 12m cells (26 x 26 = 676 cells) using flat
 * primitive arrays and stamp-based deduplication so multi-cell props are tested at most once per query.
 */
class PropSpatialGrid {
    companion object {
        private const val MIN_COORD = -156f
        private const val CELL_SIZE = 12f
        private const val INV_CELL_SIZE = 1f / CELL_SIZE
        @PublishedApi
        internal const val GRID_DIM = 26
        private const val TOTAL_CELLS = GRID_DIM * GRID_DIM
    }

    @PublishedApi
    internal val cellHead = IntArray(TOTAL_CELLS) { -1 }

    @PublishedApi
    internal var entryPropIdx = IntArray(1024)

    @PublishedApi
    internal var entryNext = IntArray(1024)

    @PublishedApi
    internal var visitStamp = IntArray(512)

    @PublishedApi
    internal var currentStamp = 0

    private var entryCount = 0

    var indexedCount: Int = -1
        private set

    fun markInvalid() {
        indexedCount = -1
    }

    private fun ensureVisitCapacity(propCount: Int) {
        if (propCount <= visitStamp.size) return
        visitStamp = IntArray(maxOf(propCount + 128, visitStamp.size * 2))
        currentStamp = 0
    }

    private fun ensureEntryCapacity(needed: Int) {
        if (needed <= entryPropIdx.size) return
        val nextCap = maxOf(needed + 256, entryPropIdx.size * 2)
        entryPropIdx = entryPropIdx.copyOf(nextCap)
        entryNext = entryNext.copyOf(nextCap)
    }

    @PublishedApi
    internal fun coordToCell(v: Float): Int {
        val c = floor((v - MIN_COORD) * INV_CELL_SIZE).toInt()
        return when {
            c < 0 -> 0
            c >= GRID_DIM -> GRID_DIM - 1
            else -> c
        }
    }

    fun rebuild(props: List<WorldProp>) {
        cellHead.fill(-1)
        entryCount = 0
        val count = props.size
        ensureVisitCapacity(count)

        for (i in 0 until count) {
            val prop = props[i]
            if (prop.destroyed) continue
            val r = prop.boundRadius + 0.6f
            val minCx = coordToCell(prop.x - r)
            val maxCx = coordToCell(prop.x + r)
            val minCz = coordToCell(prop.z - r)
            val maxCz = coordToCell(prop.z + r)

            for (cz in minCz..maxCz) {
                val rowOffset = cz * GRID_DIM
                for (cx in minCx..maxCx) {
                    val cell = rowOffset + cx
                    val slot = entryCount++
                    if (slot >= entryPropIdx.size) {
                        ensureEntryCapacity(slot + 1)
                    }
                    entryPropIdx[slot] = i
                    entryNext[slot] = cellHead[cell]
                    cellHead[cell] = slot
                }
            }
        }
        indexedCount = count
    }

    @PublishedApi
    internal fun nextStamp(): Int {
        val s = currentStamp + 1
        if (s == Int.MAX_VALUE) {
            visitStamp.fill(0)
            currentStamp = 1
            return 1
        }
        currentStamp = s
        return s
    }

    /**
     * Visits every non-destroyed prop whose bounding cell overlaps [x-radius..x+radius, z-radius..z+radius].
     * Each prop is visited at most once per call. Zero heap allocations.
     */
    inline fun forEachCandidate(
        x: Float,
        z: Float,
        radius: Float,
        props: List<WorldProp>,
        action: (WorldProp) -> Unit
    ) {
        val propSize = props.size
        if (propSize == 0) return
        val minCx = coordToCell(x - radius)
        val maxCx = coordToCell(x + radius)
        val minCz = coordToCell(z - radius)
        val maxCz = coordToCell(z + radius)

        if (minCx == maxCx && minCz == maxCz) {
            // Fast single-cell path: no deduplication stamp needed!
            var node = cellHead[minCz * GRID_DIM + minCx]
            while (node >= 0) {
                val idx = entryPropIdx[node]
                if (idx < propSize) {
                    val prop = props[idx]
                    if (!prop.destroyed) {
                        action(prop)
                    }
                }
                node = entryNext[node]
            }
            return
        }

        val stamp = nextStamp()
        val stamps = visitStamp
        for (cz in minCz..maxCz) {
            val rowOffset = cz * GRID_DIM
            for (cx in minCx..maxCx) {
                var node = cellHead[rowOffset + cx]
                while (node >= 0) {
                    val idx = entryPropIdx[node]
                    if (idx < propSize && stamps[idx] != stamp) {
                        stamps[idx] = stamp
                        val prop = props[idx]
                        if (!prop.destroyed) {
                            action(prop)
                        }
                    }
                    node = entryNext[node]
                }
            }
        }
    }
}
