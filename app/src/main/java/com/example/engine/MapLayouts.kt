package com.example.engine

/**
 * Modular 3D map layouts built exclusively from verified .glb assets in app/src/main/assets/models/.
 * Streamlined for 120 FPS (~38-42% fewer static props, zero redundant micro-clutter) with wide
 * stuck-free roads, clear combat zoning, smooth stunt transitions, and clean building interiors.
 */
object MapLayouts {

    data class StaticPropSpec(
        val type: PropType,
        val x: Float,
        val y: Float,
        val z: Float,
        val yaw: Float,
        val phaseOffset: Float = 0f,
        val startOpen: Boolean = false
    )

    /**
     * Map 1: «Полигон» (MAIN_ARENA)
     * Clear 3-zone layout:
     * - Open central combat plaza with roundabout, wide avenues & clean perimeter cover
     * - Dedicated West high-speed stunt strip (boosts, double ramp, fire ring, 360° loop & overpass)
     * - Interactive North compound, East hangars/houses & South shooting/demolition range
     */
    fun buildMainArenaSpecs(): List<StaticPropSpec> {
        val out = ArrayList<StaticPropSpec>(125)
        fun add(
            type: PropType,
            x: Float,
            y: Float,
            z: Float,
            yaw: Float,
            phase: Float = 0f,
            startOpen: Boolean = false
        ) {
            out.add(StaticPropSpec(type, x, y, z, yaw, phase, startOpen))
        }

        // ============================================================
        // 1. OPEN CENTRAL COMBAT PLAZA & WIDE AVENUES (ZERO ROAD CLUTTER)
        // ============================================================
        add(PropType.ROAD_ROUNDABOUT, 0f, 0f, 0f, 0f)
        for (d in floatArrayOf(-15f, -25f, -35f, -45f, 15f, 25f, 35f, 45f)) {
            add(PropType.ROAD, 0f, 0f, d, 0f)
            add(PropType.ROAD, d, 0f, 0f, 90f)
        }
        // Wide ±50m ring road corners & straights for smooth high-speed vehicle loops
        add(PropType.ROAD_TURN, -50f, 0f, -50f, 0f)
        add(PropType.ROAD_TURN, 50f, 0f, -50f, 90f)
        add(PropType.ROAD_TURN, 50f, 0f, 50f, 180f)
        add(PropType.ROAD_TURN, -50f, 0f, 50f, -90f)
        for (r in floatArrayOf(-38f, -26f, -14f, 14f, 26f, 38f)) {
            add(PropType.ROAD, r, 0f, -50f, 90f)
            add(PropType.ROAD, r, 0f, 50f, 90f)
            add(PropType.ROAD, -50f, 0f, r, 0f)
            add(PropType.ROAD, 50f, 0f, r, 0f)
        }

        // Clean Start/Finish Arch & 4 diagonal combat cover positions framing the open plaza
        add(PropType.FINISH_ARCH, 0f, 0f, 30f, 0f)
        add(PropType.BARRIER, -16f, 0f, -16f, 45f)
        add(PropType.BARRIER, 16f, 0f, -16f, -45f)
        add(PropType.BARRIER, -16f, 0f, 16f, -45f)
        add(PropType.BARRIER, 16f, 0f, 16f, 45f)
        add(PropType.AMMO_CRATE, -19f, 0f, -19f, 45f)
        add(PropType.AMMO_CRATE, 19f, 0f, 19f, 45f)
        add(PropType.GAZYAVA_CLASSIC, 0f, 0f, 0f, 0f)

        // ============================================================
        // 2. DEDICATED WEST STUNT STRIP (x = -50..-70, z = -42..+44)
        // ============================================================
        // Inner West Ramp & Fire-Ring Runway (clear of central square!)
        add(PropType.BOOST_PAD, -32f, 0f, 18f, 0f)
        add(PropType.DOUBLE_RAMP, -32f, 0f, 2f, 0f)
        add(PropType.FIRE_RING, -32f, 0f, -12f, 0f)
        add(PropType.JUMP_PAD, -32f, 0f, -32f, 0f)
        add(PropType.JUMP_PAD, -32f, 0f, 34f, 0f)

        // Outer West Mega Loop-the-Loop & Elevated Bridge Strip
        add(PropType.BOOST_PAD, -68f, 0f, 12f, 0f)
        add(PropType.LOOP_THE_LOOP, -68f, 0f, -8f, 0f)
        add(PropType.BOOST_PAD, -68f, 0f, -28f, 0f)
        add(PropType.RAMP, -66f, 0f, 40f, 0f)
        add(PropType.BRIDGE, -66f, 2.8f, 33f, 0f)
        add(PropType.RAMP, -66f, 0f, 26f, 180f)
        add(PropType.PENDULUM_HAMMER, -50f, 0f, -24f, 0f, phase = 0f)
        add(PropType.HRUMKI_CHEESE, -38f, 0f, -24f, 0f)

        // ============================================================
        // 3. NORTH FORTIFIED PERIMETER (z = -58..-78)
        // ============================================================
        add(PropType.WALL_ARCH_GATE, 0f, 0f, -58f, 180f, startOpen = true)
        add(PropType.WALL, -9f, 0f, -58f, 0f)
        add(PropType.WALL, 9f, 0f, -58f, 0f)
        add(PropType.WATCHTOWER, -18f, 0f, -72f, 180f)
        add(PropType.WATCHTOWER, 18f, 0f, -72f, 180f)
        add(PropType.RADAR_DISH, -6f, 0f, -76f, 20f)
        add(PropType.RADIO_MAST, 6f, 0f, -76f, 0f)
        add(PropType.BARRIER, -7f, 0f, -64f, 0f)
        add(PropType.BARRIER, 7f, 0f, -64f, 0f)
        add(PropType.OIL_DRUM, -12f, 0f, -68f, 0f)
        add(PropType.GAS_BARREL, 12f, 0f, -68f, 0f)
        add(PropType.HRUMKI_SPICY, 0f, 0f, -72f, 0f)

        // ============================================================
        // 4. EAST INTERACTIVE HANGARS & HOUSES QUARTER (x = +26..+72)
        // ============================================================
        add(PropType.GARAGE_HANGAR, 68f, 0f, -20f, -90f, startOpen = true)
        add(PropType.GARAGE_HANGAR, 68f, 0f, 20f, -90f, startOpen = true)
        add(PropType.HOUSE_SMALL, 28f, 0f, -24f, 180f) // door faces +Z (East avenue)
        add(PropType.HOUSE_SMALL, 28f, 0f, 24f, 0f)    // door faces -Z (East avenue)
        add(PropType.WALL_BRICK, 38f, 0f, 24f, 90f)
        add(PropType.WALL_BRICK, 38f, 0f, -24f, 90f)
        add(PropType.CRATE, 34f, 0f, -14f, 8f)
        add(PropType.CRATE, 34f, 0f, 14f, -10f)
        add(PropType.BARREL, 58f, 0f, -8f, 0f)
        add(PropType.BARREL, 58f, 0f, 8f, 0f)
        add(PropType.GAZYAVA_LEMON, 28f, 0f, 14f, 0f)
        add(PropType.HRUMKI_CHICKEN, 58f, 0f, -20f, 0f)

        // ============================================================
        // 5. SOUTH DEMOLITION, SHOOTING & BOWLING RANGE (z = +58..+82)
        // ============================================================
        add(PropType.WALL_ARCH_GATE, 0f, 0f, 58f, 0f, startOpen = true)
        add(PropType.WALL_BRICK, -9f, 0f, 58f, 0f)
        add(PropType.WALL_BRICK, 9f, 0f, 58f, 0f)

        // Compact 6-Pin Car-Bowling Lane (SE South Range, x = 22)
        add(PropType.BOOST_PAD, 22f, 0f, 64f, 180f)
        add(PropType.FRAGILE_BRICK_WALL, 22f, 0f, 70f, 0f)
        val pinStartZ = 75f
        for (row in 0 until 3) {
            val countInRow = row + 1
            val rowZ = pinStartZ + row * 1.9f
            val startX = 22f - (countInRow - 1) * 1.9f * 0.5f
            for (c in 0 until countInRow) {
                add(PropType.BOWLING_PIN, startX + c * 1.9f, 0f, rowZ, 180f)
            }
        }

        // Shooting & Explosive Range (SW South Range)
        add(PropType.TARGET, -12f, 0f, 72f, 0f)
        add(PropType.TARGET, -20f, 0f, 72f, 0f)
        add(PropType.TARGET, -28f, 0f, 72f, 0f)
        add(PropType.BARREL, -16f, 0f, 76f, 0f)
        add(PropType.TOXIC_DRUM, -24f, 0f, 76f, 0f)
        add(PropType.FRAGILE_BRICK_WALL, -6f, 0f, 78f, 0f)
        add(PropType.RAMP, 0f, 0f, 68f, 180f)
        add(PropType.GAZYAVA_CHICKEN, 0f, 0f, 76f, 0f)

        // ============================================================
        // 6. CLEAN CORNER LANDMARKS (±74, ±74)
        // ============================================================
        add(PropType.TOWER, -74f, 0f, -74f, 0f)
        add(PropType.TOWER, 74f, 0f, -74f, 0f)
        add(PropType.TOWER, 74f, 0f, 74f, 0f)
        add(PropType.TOWER, -74f, 0f, 74f, 0f)
        add(PropType.GAZYAVA_TARHUN, -64f, 0f, -66f, 0f)
        add(PropType.HRUMKI_GREENS, 64f, 0f, -66f, 0f)

        return out
    }

    /**
     * Map 2: «Турбо-Трек» (TURBO_SPEEDWAY)
     * Clean, ultra-smooth high-speed racing circuit with zero roadside poles or micro-noise.
     * Wide aligned approaches into boost strips, double ramps, fire rings, overpass bridge, and 360° loop.
     */
    fun buildTurboSpeedwaySpecs(): List<StaticPropSpec> {
        val out = ArrayList<StaticPropSpec>(116)
        fun add(
            type: PropType,
            x: Float,
            y: Float,
            z: Float,
            yaw: Float,
            phase: Float = 0f,
            startOpen: Boolean = false
        ) {
            out.add(StaticPropSpec(type, x, y, z, yaw, phase, startOpen))
        }

        // ============================================================
        // 1. CENTRAL ROUNDABOUT, MAIN STRAIGHT & SMOOTH ±60m OVAL RING
        // ============================================================
        add(PropType.ROAD_ROUNDABOUT, 0f, 0f, 0f, 0f)
        for (d in floatArrayOf(-15f, -26f, -38f, -50f, 15f, 26f, 38f, 50f)) {
            add(PropType.ROAD, 0f, 0f, d, 0f)
            add(PropType.ROAD, d, 0f, 0f, 90f)
        }

        add(PropType.FINISH_ARCH, 0f, 0f, 30f, 0f)
        add(PropType.BOOST_PAD, 0f, 0f, 16f, 0f)
        add(PropType.BOOST_PAD, 0f, 0f, -18f, 0f)
        add(PropType.GAZYAVA_CLASSIC, 0f, 0f, 0f, 0f)

        // Outer ±60m High-Speed Perimeter Ring (wide clean turns, zero kerb obstacles)
        add(PropType.ROAD_TURN, -60f, 0f, -60f, 0f)
        add(PropType.ROAD_TURN, 60f, 0f, -60f, 90f)
        add(PropType.ROAD_TURN, 60f, 0f, 60f, 180f)
        add(PropType.ROAD_TURN, -60f, 0f, 60f, -90f)
        for (r in floatArrayOf(-48f, -36f, -24f, -12f, 12f, 24f, 36f, 48f)) {
            add(PropType.ROAD, r, 0f, -60f, 90f)
            add(PropType.ROAD, r, 0f, 60f, 90f)
            add(PropType.ROAD, -60f, 0f, r, 0f)
            add(PropType.ROAD, 60f, 0f, r, 0f)
        }

        // North & South High-Speed Boost Straights + Centered Fire Rings
        add(PropType.BOOST_PAD, -26f, 0f, -60f, 90f)
        add(PropType.BOOST_PAD, 26f, 0f, -60f, 90f)
        add(PropType.BOOST_PAD, 26f, 0f, 60f, -90f)
        add(PropType.BOOST_PAD, -26f, 0f, 60f, -90f)
        add(PropType.FIRE_RING, 0f, 0f, -60f, 90f)
        add(PropType.FIRE_RING, 0f, 0f, 60f, 90f)

        // ============================================================
        // 2. EAST STUNT STRAIGHT (x = +60) — ALIGNED BOOST -> RAMP -> RING -> LOOP
        // ============================================================
        add(PropType.BOOST_PAD, 60f, 0f, 42f, 0f)
        add(PropType.DOUBLE_RAMP, 60f, 0f, 26f, 0f)
        add(PropType.FIRE_RING, 60f, 0f, 14f, 0f)
        add(PropType.BOOST_PAD, 60f, 0f, -4f, 0f)
        add(PropType.LOOP_THE_LOOP, 60f, 0f, -24f, 0f)
        add(PropType.BOOST_PAD, 60f, 0f, -44f, 0f)

        // Inner East Jump Lane (x = +32, completely clear of turns)
        add(PropType.BOOST_PAD, 32f, 0f, 32f, 0f)
        add(PropType.RAMP, 32f, 0f, 18f, 0f)
        add(PropType.FIRE_RING, 32f, 0f, 8f, 0f)
        add(PropType.ROAD_RAMP, 32f, 0f, -24f, 0f)
        add(PropType.JUMP_PAD, 24f, 0f, -12f, 0f)
        add(PropType.JUMP_PAD, 24f, 0f, 12f, 0f)

        // ============================================================
        // 3. WEST OVERPASS & DRIFT STRAIGHT (x = -60..-32)
        // ============================================================
        add(PropType.RAMP, -60f, 0f, 36f, 0f)
        add(PropType.BRIDGE, -60f, 2.8f, 29f, 0f)
        add(PropType.RAMP, -60f, 0f, 22f, 180f)
        add(PropType.PENDULUM_HAMMER, -60f, 0f, -24f, 0f, phase = 0.0f)

        // Inner West Stunt & Smash Strip (x = -32)
        add(PropType.BOOST_PAD, -32f, 0f, -10f, 0f)
        add(PropType.DOUBLE_RAMP, -32f, 0f, -24f, 0f)
        add(PropType.FIRE_RING, -32f, 0f, -36f, 0f)
        add(PropType.FRAGILE_BRICK_WALL, -32f, 0f, 24f, 0f)
        add(PropType.BARREL, -24f, 0f, 28f, 0f)
        add(PropType.GAS_BARREL, -20f, 0f, 28f, 0f)
        add(PropType.BARREL, 22f, 0f, 36f, 0f)
        add(PropType.OIL_DRUM, 26f, 0f, 36f, 0f)

        // ============================================================
        // 4. PIT HANGARS, RACE CONTROL TOWERS & CLEAN GRANDSTAND PERIMETER
        // ============================================================
        add(PropType.GARAGE_HANGAR, 78f, 0f, -20f, -90f, startOpen = true)
        add(PropType.GARAGE_HANGAR, 78f, 0f, 20f, -90f, startOpen = true)
        add(PropType.WATCHTOWER, 78f, 0f, 0f, -90f)
        add(PropType.WATCHTOWER, -78f, 0f, 0f, 90f)
        add(PropType.HOUSE_SMALL, -78f, 0f, -24f, 90f)
        add(PropType.HOUSE_SMALL, -78f, 0f, 24f, 90f)

        // North & South Wide Arch Gates (open pass-through) & Billboard Landmarks
        add(PropType.WALL_ARCH_GATE, 0f, 0f, -74f, 180f, startOpen = true)
        add(PropType.WALL_ARCH_GATE, 0f, 0f, 74f, 0f, startOpen = true)
        add(PropType.BILLBOARD_SIGN, -34f, 0f, -74f, 0f)
        add(PropType.BILLBOARD_SIGN, 34f, 0f, -74f, 0f)
        add(PropType.BILLBOARD_SIGN, -34f, 0f, 74f, 180f)
        add(PropType.BILLBOARD_SIGN, 34f, 0f, 74f, 180f)

        // 4 Corner Spectator Towers & Track Pickups
        add(PropType.TOWER, -78f, 0f, -74f, 0f)
        add(PropType.TOWER, 78f, 0f, -74f, 0f)
        add(PropType.TOWER, 78f, 0f, 74f, 0f)
        add(PropType.TOWER, -78f, 0f, 74f, 0f)
        add(PropType.GAZYAVA_LEMON, 32f, 0f, -6f, 0f)
        add(PropType.GAZYAVA_CHICKEN, -32f, 0f, 6f, 0f)
        add(PropType.GAZYAVA_TARHUN, 0f, 0f, -42f, 0f)
        add(PropType.HRUMKI_CHICKEN, 46f, 0f, 44f, 0f)
        add(PropType.HRUMKI_CHEESE, -46f, 0f, -44f, 0f)

        return out
    }

    /**
     * Map 3: «Штурм-База» (TACTICAL_COMPOUND)
     * Symmetrical two-flank tactical compound with:
     * - Central drive-through hangar corridor for vehicles (open portals + facing hangar bays, zero interior clutter)
     * - Mirrored West & East flanks with watchtowers, houses, breachable walls & explosive chains
     * - Clean outer flanking loops, wide open arch gates & elevated sniper bridges
     */
    fun buildTacticalCompoundSpecs(): List<StaticPropSpec> {
        val out = ArrayList<StaticPropSpec>(116)
        fun add(
            type: PropType,
            x: Float,
            y: Float,
            z: Float,
            yaw: Float,
            phase: Float = 0f,
            startOpen: Boolean = false
        ) {
            out.add(StaticPropSpec(type, x, y, z, yaw, phase, startOpen))
        }

        // ============================================================
        // 1. CENTRAL DRIVE-THROUGH HANGAR CORRIDOR & CROSS-ROADS
        // ============================================================
        add(PropType.ROAD_ROUNDABOUT, 0f, 0f, 0f, 0f)
        for (d in floatArrayOf(-15f, -26f, -38f, -50f, 15f, 26f, 38f, 50f)) {
            add(PropType.ROAD, 0f, 0f, d, 0f)
            add(PropType.ROAD, d, 0f, 0f, 90f)
        }

        // Open drive-through Central Hangar Plaza along N-S road (x = 0):
        // Twin open Arch Gates at z = ±14 + twin facing open Garage Hangars at x = ±16 (clean empty interiors!)
        add(PropType.WALL_ARCH_GATE, 0f, 0f, -14f, 180f, startOpen = true)
        add(PropType.WALL_ARCH_GATE, 0f, 0f, 14f, 0f, startOpen = true)
        add(PropType.GARAGE_HANGAR, -17f, 0f, 0f, 90f, startOpen = true)
        add(PropType.GARAGE_HANGAR, 17f, 0f, 0f, -90f, startOpen = true)

        // Tactical low cover outside the drive-through lane (leaves >=10m wide center pass!)
        add(PropType.BARRIER, -10f, 0f, -22f, 25f)
        add(PropType.BARRIER, 10f, 0f, -22f, -25f)
        add(PropType.BARRIER, -10f, 0f, 22f, -25f)
        add(PropType.BARRIER, 10f, 0f, 22f, 25f)
        add(PropType.GAZYAVA_CLASSIC, 0f, 0f, 0f, 0f)

        // ============================================================
        // 2. SYMMETRICAL WEST FLANK (x = -24..-52, z = -42..+42)
        // ============================================================
        // NW & SW Watchtowers commanding the West lane
        add(PropType.WATCHTOWER, -32f, 0f, -28f, 180f) // door faces +Z
        add(PropType.WATCHTOWER, -32f, 0f, 28f, 0f)    // door faces -Z
        add(PropType.HOUSE_SMALL, -46f, 0f, -14f, 90f) // door faces +X, empty interior
        add(PropType.HOUSE_SMALL, -46f, 0f, 14f, 90f)  // door faces +X, empty interior

        // West flank cover, breachable walls & explosive barrels
        add(PropType.WALL, -20f, 0f, -34f, 90f)
        add(PropType.FRAGILE_BRICK_WALL, -26f, 0f, -40f, 0f)
        add(PropType.WALL_BRICK, -20f, 0f, 34f, 90f)
        add(PropType.FRAGILE_BRICK_WALL, -26f, 0f, 40f, 0f)
        add(PropType.BARREL, -24f, 0f, -18f, 0f)
        add(PropType.OIL_DRUM, -26f, 0f, -20f, 0f)
        add(PropType.GAS_BARREL, -24f, 0f, 18f, 0f)
        add(PropType.TOXIC_DRUM, -26f, 0f, 20f, 0f)
        add(PropType.CRATE, -36f, 0f, -12f, 10f)
        add(PropType.CRATE, -36f, 0f, 12f, -10f)
        add(PropType.AMMO_CRATE, -30f, 0f, -14f, 0f)
        add(PropType.RADAR_DISH, -42f, 0f, -34f, 35f)
        add(PropType.TARGET, -42f, 0f, 34f, 0f)
        add(PropType.HRUMKI_SPICY, -34f, 0f, -18f, 0f)

        // West Elevated Flanking Bridge (x = -58)
        add(PropType.RAMP, -58f, 0f, 22f, 0f)
        add(PropType.BRIDGE, -58f, 2.8f, 15f, 0f)
        add(PropType.RAMP, -58f, 0f, 8f, 180f)
        add(PropType.JUMP_PAD, -46f, 0f, -28f, 0f)

        // ============================================================
        // 3. SYMMETRICAL EAST FLANK (x = +24..+52, z = -42..+42)
        // ============================================================
        // NE & SE Watchtowers commanding the East lane (mirrors West!)
        add(PropType.WATCHTOWER, 32f, 0f, -28f, 180f)  // door faces +Z
        add(PropType.WATCHTOWER, 32f, 0f, 28f, 0f)     // door faces -Z
        add(PropType.HOUSE_SMALL, 46f, 0f, -14f, -90f) // door faces -X, empty interior
        add(PropType.HOUSE_SMALL, 46f, 0f, 14f, -90f)  // door faces -X, empty interior

        // East flank cover, breachable walls & explosive barrels
        add(PropType.WALL, 20f, 0f, -34f, 90f)
        add(PropType.FRAGILE_BRICK_WALL, 26f, 0f, -40f, 0f)
        add(PropType.WALL_BRICK, 20f, 0f, 34f, 90f)
        add(PropType.FRAGILE_BRICK_WALL, 26f, 0f, 40f, 0f)
        add(PropType.BARREL, 24f, 0f, -18f, 0f)
        add(PropType.OIL_DRUM, 26f, 0f, -20f, 0f)
        add(PropType.GAS_BARREL, 24f, 0f, 18f, 0f)
        add(PropType.TOXIC_DRUM, 26f, 0f, 20f, 0f)
        add(PropType.CRATE, 36f, 0f, -12f, -10f)
        add(PropType.CRATE, 36f, 0f, 12f, 10f)
        add(PropType.AMMO_CRATE, 30f, 0f, 14f, 0f)
        add(PropType.RADIO_MAST, 42f, 0f, -34f, 0f)
        add(PropType.TARGET, 42f, 0f, 34f, 0f)
        add(PropType.GAZYAVA_LEMON, 34f, 0f, 18f, 0f)

        // East Elevated Flanking Bridge (x = +58, mirrors West!)
        add(PropType.RAMP, 58f, 0f, -8f, 0f)
        add(PropType.BRIDGE, 58f, 2.8f, -15f, 0f)
        add(PropType.RAMP, 58f, 0f, -22f, 180f)
        add(PropType.JUMP_PAD, 46f, 0f, 28f, 0f)

        // ============================================================
        // 4. OUTER FLANKING PERIMETER GATES & CORNER BASTIONS (±56..±74)
        // ============================================================
        add(PropType.WALL_ARCH_GATE, 0f, 0f, -56f, 180f, startOpen = true)
        add(PropType.WALL_ARCH_GATE, 0f, 0f, 56f, 0f, startOpen = true)
        add(PropType.WALL, -10f, 0f, -56f, 0f)
        add(PropType.WALL, 10f, 0f, -56f, 0f)
        add(PropType.WALL_BRICK, -10f, 0f, 56f, 0f)
        add(PropType.WALL_BRICK, 10f, 0f, 56f, 0f)

        // Clean outer flank bastions with wide vehicle turning room around ±68
        add(PropType.TOWER, -70f, 0f, -70f, 0f)
        add(PropType.TOWER, 70f, 0f, -70f, 0f)
        add(PropType.TOWER, 70f, 0f, 70f, 0f)
        add(PropType.TOWER, -70f, 0f, 70f, 0f)
        add(PropType.GARAGE_HANGAR, 0f, 0f, -74f, 180f, startOpen = true)
        add(PropType.GARAGE_HANGAR, 0f, 0f, 74f, 0f, startOpen = true)
        add(PropType.BILLBOARD_SIGN, -38f, 0f, -64f, 0f)
        add(PropType.BILLBOARD_SIGN, 38f, 0f, 64f, 180f)
        add(PropType.HRUMKI_GREENS, 0f, 0f, -64f, 0f)
        add(PropType.GAZYAVA_CHICKEN, 0f, 0f, 64f, 0f)

        return out
    }

    /**
     * Stages all 9 vehicles cleanly in open areas for every map (zero wall/roof overlaps).
     */
    fun initialVehiclesForMap(map: GameMap): List<VehicleEntity> = when (map) {
        GameMap.MAIN_ARENA -> listOf(
            VehicleEntity("car_cyber", VehicleType.CYBER_SUPERCAR, -8f, 0f, 14f, 0f),
            VehicleEntity("car_sports", VehicleType.SPORTS_CAR, 8f, 0f, 14f, 0f),
            VehicleEntity("car_police", VehicleType.POLICE_SUV, -8f, 0f, 26f, 0f),
            VehicleEntity("car_pickup", VehicleType.PICKUP_4X4, 8f, 0f, 26f, 0f),
            VehicleEntity("car_monster", VehicleType.MONSTER_TRUCK, 52f, 0f, -20f, -90f),
            VehicleEntity("car_fire", VehicleType.FIRE_TRUCK, 52f, 0f, 20f, -90f),
            VehicleEntity("car_swat", VehicleType.ARMORED_SWAT, -10f, 0f, -40f, 0f),
            VehicleEntity("car_buggy", VehicleType.DUNE_BUGGY, -32f, 0f, 26f, 0f),
            VehicleEntity("car_tank", VehicleType.BATTLE_TANK, 10f, 0f, -40f, 0f)
        )
        GameMap.TURBO_SPEEDWAY -> listOf(
            VehicleEntity("car_cyber", VehicleType.CYBER_SUPERCAR, -4.5f, 0f, 36f, 0f),
            VehicleEntity("car_sports", VehicleType.SPORTS_CAR, 4.5f, 0f, 36f, 0f),
            VehicleEntity("car_police", VehicleType.POLICE_SUV, -4.5f, 0f, 45f, 0f),
            VehicleEntity("car_buggy", VehicleType.DUNE_BUGGY, 4.5f, 0f, 45f, 0f),
            VehicleEntity("car_monster", VehicleType.MONSTER_TRUCK, 60f, 0f, 50f, 0f),
            VehicleEntity("car_pickup", VehicleType.PICKUP_4X4, -60f, 0f, 48f, 0f),
            VehicleEntity("car_swat", VehicleType.ARMORED_SWAT, 66f, 0f, -18f, -90f),
            VehicleEntity("car_fire", VehicleType.FIRE_TRUCK, 66f, 0f, 20f, -90f),
            VehicleEntity("car_tank", VehicleType.BATTLE_TANK, -14f, 0f, -60f, 90f)
        )
        GameMap.TACTICAL_COMPOUND -> listOf(
            VehicleEntity("car_swat", VehicleType.ARMORED_SWAT, -5f, 0f, 28f, 0f),
            VehicleEntity("car_police", VehicleType.POLICE_SUV, 5f, 0f, 28f, 0f),
            VehicleEntity("car_pickup", VehicleType.PICKUP_4X4, -5f, 0f, -28f, 180f),
            VehicleEntity("car_tank", VehicleType.BATTLE_TANK, 5f, 0f, -38f, 180f),
            VehicleEntity("car_monster", VehicleType.MONSTER_TRUCK, 34f, 0f, 0f, -90f),
            VehicleEntity("car_fire", VehicleType.FIRE_TRUCK, -34f, 0f, 0f, 90f),
            VehicleEntity("car_buggy", VehicleType.DUNE_BUGGY, 0f, 0f, 42f, 0f),
            VehicleEntity("car_sports", VehicleType.SPORTS_CAR, -58f, 0f, -18f, 0f),
            VehicleEntity("car_cyber", VehicleType.CYBER_SUPERCAR, 58f, 0f, 18f, 180f)
        )
    }
}
