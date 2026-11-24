package com.example.engine

import kotlin.math.sqrt

data class Vec3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    fun copyOf(): Vec3 = Vec3(x, y, z)
    fun distanceTo(other: Vec3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun horizontalDistanceTo(ox: Float, oz: Float): Float {
        val dx = x - ox
        val dz = z - oz
        return sqrt(dx * dx + dz * dz)
    }
}

enum class WeaponSkin(
    val label: String,
    val suffix: String
) {
    CLASSIC("Классика", "classic"),
    TACTICAL("Тактический", "tactical");

    fun next(): WeaponSkin = if (this == CLASSIC) TACTICAL else CLASSIC

    companion object {
        fun fromNameSafe(raw: String?): WeaponSkin =
            entries.firstOrNull { it.name == raw } ?: CLASSIC
    }
}

enum class WeaponType(
    val displayName: String,
    val damage: Int,
    val fireIntervalSec: Float,
    val maxAmmo: Int,
    val reloadSec: Float,
    val recoilKick: Float,
    val automatic: Boolean,
    val projectileSpeed: Float,
    val explosive: Boolean,
    val glbBaseName: String
) {
    PISTOL(
        displayName = "Пистолет",
        damage = 28,
        fireIntervalSec = 0.26f,
        maxAmmo = 12,
        reloadSec = 1.15f,
        recoilKick = 0.14f,
        automatic = false,
        projectileSpeed = 98f,
        explosive = false,
        glbBaseName = "weapon_pistol"
    ),
    RIFLE(
        displayName = "Автомат",
        damage = 19,
        fireIntervalSec = 0.10f,
        maxAmmo = 30,
        reloadSec = 1.60f,
        recoilKick = 0.09f,
        automatic = true,
        projectileSpeed = 118f,
        explosive = false,
        glbBaseName = "weapon_rifle"
    ),
    RPG(
        displayName = "РПГ-7",
        damage = 95,
        fireIntervalSec = 1.05f,
        maxAmmo = 4,
        reloadSec = 2.0f,
        recoilKick = 0.35f,
        automatic = false,
        projectileSpeed = 38f,
        explosive = true,
        glbBaseName = "weapon_rpg"
    );

    fun glbFile(skin: WeaponSkin): String = "${glbBaseName}_${skin.suffix}.glb"

    fun next(): WeaponType {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

enum class BuildCategory(val label: String) {
    STUNTS_TRAPS("Трюки"),
    BLOCKS("Стройка"),
    FOOD_DRINKS("Еда"),
    NPCS("НПС"),
    VEHICLES("Авто")
}

enum class PropType(
    val label: String,
    val halfW: Float,
    val height: Float,
    val halfD: Float,
    val maxHp: Int,
    val destructible: Boolean,
    val explosive: Boolean,
    val movableByBlast: Boolean,
    val firestoreType: String,
    val glbFile: String? = null,
    val modelScale: Float = 1.0f,
    val buildTab: BuildCategory = BuildCategory.BLOCKS,
    val isHealPickup: Boolean = false,
    val isDrink: Boolean = false,
    val isTrap: Boolean = false,
    val isWaterBarrel: Boolean = false,
    val isWalkableSurface: Boolean = false,
    val isPassThrough: Boolean = false,
    val hasInteractiveDoor: Boolean = false,
    val doorActionLabel: String = ""
) {
    // --- Core & Modular Build Objects (updated to clean new .glb models) ---
    CRATE(
        label = "Ящик",
        halfW = 0.78f, height = 1.52f, halfD = 0.78f,
        maxHp = 50, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "wooden_crate.glb", modelScale = 1.42f,
        buildTab = BuildCategory.BLOCKS
    ),
    WALL(
        label = "Бетон. стена",
        halfW = 2.26f, height = 3.45f, halfD = 0.42f,
        maxHp = 350, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "wall_block_concrete.glb", modelScale = 1.12f,
        buildTab = BuildCategory.BLOCKS
    ),
    WALL_BRICK(
        label = "Кирпич. стена",
        halfW = 2.30f, height = 3.45f, halfD = 0.36f,
        maxHp = 260, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "wall_section_brick.glb", modelScale = 1.12f,
        buildTab = BuildCategory.BLOCKS
    ),
    WALL_WOOD(
        label = "Дерев. стена",
        halfW = 2.24f, height = 3.35f, halfD = 0.22f,
        maxHp = 140, destructible = true, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "wall_section_wood.glb", modelScale = 1.12f,
        buildTab = BuildCategory.BLOCKS
    ),
    HOUSE_SMALL(
        label = "Дом",
        halfW = 3.65f, height = 6.50f, halfD = 4.05f,
        maxHp = 1200, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "house_small.glb", modelScale = 1.15f,
        buildTab = BuildCategory.BLOCKS,
        hasInteractiveDoor = true, doorActionLabel = "дверь дома"
    ),
    GARAGE_HANGAR(
        label = "Ангар",
        halfW = 5.00f, height = 6.60f, halfD = 4.95f,
        maxHp = 1500, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "garage_hangar.glb", modelScale = 1.18f,
        buildTab = BuildCategory.BLOCKS,
        hasInteractiveDoor = true, doorActionLabel = "ворота ангара"
    ),
    WATCHTOWER(
        label = "Вышка",
        halfW = 3.40f, height = 7.50f, halfD = 3.35f,
        maxHp = 1200, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "watchtower_building.glb", modelScale = 1.15f,
        buildTab = BuildCategory.BLOCKS,
        hasInteractiveDoor = true, doorActionLabel = "дверь вышки"
    ),
    WALL_ARCH_GATE(
        label = "Ворота-арка",
        halfW = 4.90f, height = 5.90f, halfD = 0.72f,
        maxHp = 1200, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "wall_arch_gate.glb", modelScale = 1.20f,
        buildTab = BuildCategory.BLOCKS,
        hasInteractiveDoor = true, doorActionLabel = "ворота"
    ),
    RAMP(
        label = "Трамплин",
        halfW = 2.25f, height = 1.95f, halfD = 2.85f,
        maxHp = 400, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = "prop_ramp.glb", modelScale = 1.42f,
        buildTab = BuildCategory.STUNTS_TRAPS, isWalkableSurface = true
    ),
    TOWER(
        label = "Башня",
        halfW = 1.8f, height = 3.5f, halfD = 1.8f,
        maxHp = 450, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = null, modelScale = 1.0f,
        buildTab = BuildCategory.BLOCKS, isWalkableSurface = true
    ),
    BRIDGE(
        label = "Мост",
        halfW = 2.3f, height = 0.42f, halfD = 3.5f,
        maxHp = 400, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = null, modelScale = 1.0f,
        buildTab = BuildCategory.BLOCKS, isWalkableSurface = true
    ),
    TARGET(
        label = "Мишень",
        halfW = 0.62f, height = 1.92f, halfD = 0.46f,
        maxHp = 45, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "shooting_target.glb", modelScale = 1.12f,
        buildTab = BuildCategory.BLOCKS
    ),
    BARREL(
        label = "Взрыв. бочка",
        halfW = 0.52f, height = 1.42f, halfD = 0.52f,
        maxHp = 28, destructible = true, explosive = true, movableByBlast = true,
        firestoreType = "BARREL", glbFile = "explosive_barrel.glb", modelScale = 1.48f,
        buildTab = BuildCategory.BLOCKS
    ),
    CAR(
        label = "Машина",
        halfW = 1.35f, height = 1.55f, halfD = 2.45f,
        maxHp = 300, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CAR", glbFile = null, modelScale = 1.0f,
        buildTab = BuildCategory.VEHICLES
    ),

    // --- Tab 1: Stunts & Traps (Трюки и ловушки) ---
    LOOP_THE_LOOP(
        label = "Петля 360°",
        halfW = 4.0f, height = 14.5f, halfD = 6.5f,
        maxHp = 1000, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = "loop_the_loop.glb", modelScale = 1.65f,
        buildTab = BuildCategory.STUNTS_TRAPS, isPassThrough = true
    ),
    DOUBLE_RAMP(
        label = "Двойной трамплин",
        halfW = 2.65f, height = 3.25f, halfD = 4.05f,
        maxHp = 500, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = "double_ramp.glb", modelScale = 1.45f,
        buildTab = BuildCategory.STUNTS_TRAPS, isWalkableSurface = true
    ),
    FIRE_RING(
        label = "Огненное кольцо",
        halfW = 3.4f, height = 6.4f, halfD = 1.0f,
        maxHp = 600, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "fire_ring.glb", modelScale = 1.85f,
        buildTab = BuildCategory.STUNTS_TRAPS, isPassThrough = true
    ),
    JUMP_PAD(
        label = "Батут",
        halfW = 1.55f, height = 0.55f, halfD = 1.55f,
        maxHp = 400, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = "jump_pad.glb", modelScale = 1.45f,
        buildTab = BuildCategory.STUNTS_TRAPS, isPassThrough = true
    ),
    BOOST_PAD(
        label = "Турбо-панель",
        halfW = 1.85f, height = 0.25f, halfD = 1.85f,
        maxHp = 400, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = "boost_pad.glb", modelScale = 2.40f,
        buildTab = BuildCategory.STUNTS_TRAPS, isPassThrough = true
    ),
    CRUSHER(
        label = "Пресс",
        halfW = 2.45f, height = 5.1f, halfD = 1.15f,
        maxHp = 800, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "crusher.glb", modelScale = 1.42f,
        buildTab = BuildCategory.STUNTS_TRAPS, isTrap = true, isPassThrough = true
    ),
    PENDULUM_HAMMER(
        label = "Маятник",
        halfW = 3.2f, height = 6.4f, halfD = 1.5f,
        maxHp = 800, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "pendulum_hammer.glb", modelScale = 1.48f,
        buildTab = BuildCategory.STUNTS_TRAPS, isTrap = true, isPassThrough = true
    ),
    SPINNER_TRAP(
        label = "Вертушка",
        halfW = 3.2f, height = 1.6f, halfD = 3.2f,
        maxHp = 700, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "spinner_trap.glb", modelScale = 1.42f,
        buildTab = BuildCategory.STUNTS_TRAPS, isTrap = true, isPassThrough = true
    ),
    FINISH_ARCH(
        label = "Финиш",
        halfW = 5.4f, height = 6.8f, halfD = 1.1f,
        maxHp = 900, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "finish_arch.glb", modelScale = 1.90f,
        buildTab = BuildCategory.STUNTS_TRAPS, isPassThrough = true
    ),
    BOWLING_PIN(
        label = "Кегли",
        halfW = 1.15f, height = 1.10f, halfD = 1.05f,
        maxHp = 45, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "bowling_pins.glb", modelScale = 2.65f,
        buildTab = BuildCategory.STUNTS_TRAPS
    ),

    // --- Tab 2: Modular Roads, Construction & Destructible Props ---
    ROAD(
        label = "Дорога",
        halfW = 5.0f, height = 0.24f, halfD = 5.0f,
        maxHp = 900, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = "road_straight.glb", modelScale = 1.25f,
        buildTab = BuildCategory.BLOCKS, isWalkableSurface = true, isPassThrough = true
    ),
    ROAD_TURN(
        label = "Поворот 90°",
        halfW = 5.0f, height = 0.24f, halfD = 5.0f,
        maxHp = 900, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = "road_turn_90.glb", modelScale = 1.25f,
        buildTab = BuildCategory.BLOCKS, isWalkableSurface = true, isPassThrough = true
    ),
    ROAD_ROUNDABOUT(
        label = "Кольцо",
        halfW = 10.0f, height = 0.24f, halfD = 10.0f,
        maxHp = 1200, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = "road_roundabout.glb", modelScale = 1.25f,
        buildTab = BuildCategory.BLOCKS, isWalkableSurface = true, isPassThrough = true
    ),
    ROAD_RAMP(
        label = "Дорога-рамп",
        halfW = 5.0f, height = 1.85f, halfD = 5.0f,
        maxHp = 900, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = "road_cross_or_ramp.glb", modelScale = 1.25f,
        buildTab = BuildCategory.STUNTS_TRAPS, isWalkableSurface = true
    ),
    FRAGILE_BRICK_WALL(
        label = "Хруп. стена",
        halfW = 2.10f, height = 1.90f, halfD = 0.34f,
        maxHp = 65, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "fragile_brick_wall.glb", modelScale = 2.60f,
        buildTab = BuildCategory.BLOCKS
    ),
    BARRIER(
        label = "Мешки с песком",
        halfW = 1.70f, height = 0.95f, halfD = 0.46f,
        maxHp = 220, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "sandbags.glb", modelScale = 1.35f,
        buildTab = BuildCategory.BLOCKS
    ),
    REINFORCED_FENCE(
        label = "Металл. стена",
        halfW = 2.28f, height = 3.30f, halfD = 0.28f,
        maxHp = 350, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "wall_section_metal.glb", modelScale = 1.10f,
        buildTab = BuildCategory.BLOCKS
    ),
    WOODEN_FENCE(
        label = "Забор",
        halfW = 1.25f, height = 1.75f, halfD = 0.22f,
        maxHp = 60, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "wooden_fence.glb", modelScale = 1.25f,
        buildTab = BuildCategory.BLOCKS
    ),
    BOOM_BARRIER(
        label = "Шлагбаум",
        halfW = 1.65f, height = 1.55f, halfD = 0.38f,
        maxHp = 75, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "boom_barrier.glb", modelScale = 1.25f,
        buildTab = BuildCategory.BLOCKS
    ),
    METAL_GATE(
        label = "Решётка",
        halfW = 1.58f, height = 2.70f, halfD = 0.24f,
        maxHp = 280, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "metal_gate.glb", modelScale = 1.25f,
        buildTab = BuildCategory.BLOCKS
    ),
    TIRE_STACK(
        label = "Шины",
        halfW = 0.76f, height = 1.72f, halfD = 0.76f,
        maxHp = 90, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "tire_stack.glb", modelScale = 1.38f,
        buildTab = BuildCategory.BLOCKS
    ),
    GAS_BARREL(
        label = "Газ. баллон",
        halfW = 0.48f, height = 1.48f, halfD = 0.48f,
        maxHp = 24, destructible = true, explosive = true, movableByBlast = true,
        firestoreType = "BARREL", glbFile = "gas_cylinder.glb", modelScale = 1.45f,
        buildTab = BuildCategory.BLOCKS
    ),
    WATER_BARREL(
        label = "Бочка воды",
        halfW = 0.52f, height = 1.48f, halfD = 0.52f,
        maxHp = 26, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "water_barrel.glb", modelScale = 1.48f,
        buildTab = BuildCategory.BLOCKS, isWaterBarrel = true
    ),
    OIL_DRUM(
        label = "Бочка масла",
        halfW = 0.52f, height = 1.42f, halfD = 0.52f,
        maxHp = 28, destructible = true, explosive = true, movableByBlast = true,
        firestoreType = "BARREL", glbFile = "oil_drum.glb", modelScale = 1.45f,
        buildTab = BuildCategory.BLOCKS
    ),
    TOXIC_DRUM(
        label = "Токсин",
        halfW = 0.52f, height = 1.42f, halfD = 0.52f,
        maxHp = 28, destructible = true, explosive = true, movableByBlast = true,
        firestoreType = "BARREL", glbFile = "toxic_drum.glb", modelScale = 1.45f,
        buildTab = BuildCategory.BLOCKS
    ),
    FUEL_CANISTER(
        label = "Канистра",
        halfW = 0.40f, height = 0.85f, halfD = 0.30f,
        maxHp = 20, destructible = true, explosive = true, movableByBlast = true,
        firestoreType = "BARREL", glbFile = "fuel_canister.glb", modelScale = 1.55f,
        buildTab = BuildCategory.BLOCKS
    ),
    STREET_LAMP(
        label = "Фонарь",
        halfW = 0.38f, height = 5.40f, halfD = 0.38f,
        maxHp = 300, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "street_lamp.glb", modelScale = 1.25f,
        buildTab = BuildCategory.BLOCKS
    ),
    BILLBOARD_SIGN(
        label = "Билборд",
        halfW = 1.68f, height = 4.00f, halfD = 0.48f,
        maxHp = 350, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "billboard_sign.glb", modelScale = 1.30f,
        buildTab = BuildCategory.BLOCKS
    ),
    DUMPSTER(
        label = "Контейнер",
        halfW = 1.16f, height = 1.52f, halfD = 0.78f,
        maxHp = 95, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "dumpster.glb", modelScale = 1.20f,
        buildTab = BuildCategory.BLOCKS
    ),
    POWER_GENERATOR(
        label = "Генератор",
        halfW = 0.80f, height = 1.12f, halfD = 0.52f,
        maxHp = 55, destructible = true, explosive = true, movableByBlast = true,
        firestoreType = "BARREL", glbFile = "power_generator.glb", modelScale = 1.35f,
        buildTab = BuildCategory.BLOCKS
    ),
    RADAR_DISH(
        label = "Радар",
        halfW = 1.00f, height = 2.95f, halfD = 0.90f,
        maxHp = 350, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "radar_dish.glb", modelScale = 1.35f,
        buildTab = BuildCategory.BLOCKS
    ),
    RADIO_MAST(
        label = "Радиомачта",
        halfW = 0.82f, height = 6.30f, halfD = 0.82f,
        maxHp = 400, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "radio_mast.glb", modelScale = 1.40f,
        buildTab = BuildCategory.BLOCKS
    ),
    SOLAR_PANEL(
        label = "Солн. панель",
        halfW = 1.16f, height = 2.25f, halfD = 0.45f,
        maxHp = 65, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "solar_panel.glb", modelScale = 1.25f,
        buildTab = BuildCategory.BLOCKS
    ),
    AMMO_CRATE(
        label = "Боеприпасы",
        halfW = 0.65f, height = 0.58f, halfD = 0.38f,
        maxHp = 45, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "ammo_crate.glb", modelScale = 1.40f,
        buildTab = BuildCategory.BLOCKS
    ),
    FIRE_BRAZIER(
        label = "Жаровня",
        halfW = 0.52f, height = 1.35f, halfD = 0.52f,
        maxHp = 180, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "WALL", glbFile = "fire_brazier.glb", modelScale = 1.30f,
        buildTab = BuildCategory.BLOCKS
    ),
    FIRE_HYDRANT(
        label = "Гидрант",
        halfW = 0.36f, height = 1.15f, halfD = 0.36f,
        maxHp = 38, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "fire_hydrant.glb", modelScale = 1.35f,
        buildTab = BuildCategory.BLOCKS, isWaterBarrel = true
    ),
    PARK_BENCH(
        label = "Лавочка",
        halfW = 1.00f, height = 1.05f, halfD = 0.38f,
        maxHp = 50, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "park_bench.glb", modelScale = 1.20f,
        buildTab = BuildCategory.BLOCKS
    ),
    PLANK_BRIDGE(
        label = "Мостик",
        halfW = 1.15f, height = 0.45f, halfD = 1.90f,
        maxHp = 180, destructible = false, explosive = false, movableByBlast = false,
        firestoreType = "RAMP", glbFile = "plank_bridge.glb", modelScale = 1.55f,
        buildTab = BuildCategory.BLOCKS, isWalkableSurface = true
    ),
    SAFE_BOX(
        label = "Сейф",
        halfW = 0.65f, height = 1.15f, halfD = 0.62f,
        maxHp = 120, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "safe_box.glb", modelScale = 1.35f,
        buildTab = BuildCategory.BLOCKS
    ),
    MILITARY_CASE(
        label = "Воен. кейс",
        halfW = 0.72f, height = 0.52f, halfD = 0.45f,
        maxHp = 55, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "military_case.glb", modelScale = 1.35f,
        buildTab = BuildCategory.BLOCKS
    ),
    WORKBENCH(
        label = "Верстак",
        halfW = 1.10f, height = 1.15f, halfD = 0.55f,
        maxHp = 65, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "workbench_table.glb", modelScale = 1.20f,
        buildTab = BuildCategory.BLOCKS
    ),
    WOODEN_BARREL(
        label = "Дерев. бочка",
        halfW = 0.52f, height = 1.35f, halfD = 0.52f,
        maxHp = 35, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "wooden_barrel.glb", modelScale = 1.40f,
        buildTab = BuildCategory.BLOCKS
    ),
    WOODEN_PALLET(
        label = "Паллета",
        halfW = 0.85f, height = 0.25f, halfD = 0.85f,
        maxHp = 35, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "wooden_pallet.glb", modelScale = 1.30f,
        buildTab = BuildCategory.BLOCKS, isWalkableSurface = true
    ),
    ROAD_SIGN(
        label = "Дор. знак",
        halfW = 0.35f, height = 2.65f, halfD = 0.35f,
        maxHp = 35, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "road_sign.glb", modelScale = 1.30f,
        buildTab = BuildCategory.BLOCKS
    ),
    BOLLARD(
        label = "Столбик",
        halfW = 0.28f, height = 1.10f, halfD = 0.28f,
        maxHp = 50, destructible = true, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "bollard.glb", modelScale = 1.30f,
        buildTab = BuildCategory.BLOCKS
    ),

    // --- Tab 3: Food & Drinks / Heal Pickups (Еда и напитки) ---
    GAZYAVA_LEMON(
        label = "Газява Лимон",
        halfW = 0.45f, height = 1.25f, halfD = 0.45f,
        maxHp = 20, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "gazyava_lemon.glb", modelScale = 1.35f,
        buildTab = BuildCategory.FOOD_DRINKS, isHealPickup = true, isDrink = true, isPassThrough = true
    ),
    GAZYAVA_CLASSIC(
        label = "Газява Классик",
        halfW = 0.45f, height = 1.25f, halfD = 0.45f,
        maxHp = 20, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "gazyava_classic.glb", modelScale = 1.35f,
        buildTab = BuildCategory.FOOD_DRINKS, isHealPickup = true, isDrink = true, isPassThrough = true
    ),
    GAZYAVA_CHICKEN(
        label = "Газява Курица",
        halfW = 0.45f, height = 1.25f, halfD = 0.45f,
        maxHp = 20, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "gazyava_chicken.glb", modelScale = 1.35f,
        buildTab = BuildCategory.FOOD_DRINKS, isHealPickup = true, isDrink = true, isPassThrough = true
    ),
    GAZYAVA_TARHUN(
        label = "Газява Тархун",
        halfW = 0.45f, height = 1.25f, halfD = 0.45f,
        maxHp = 20, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "gazyava_tarhun.glb", modelScale = 1.35f,
        buildTab = BuildCategory.FOOD_DRINKS, isHealPickup = true, isDrink = true, isPassThrough = true
    ),
    HRUMKI_CHICKEN(
        label = "Хрумки Курица",
        halfW = 0.55f, height = 1.25f, halfD = 0.35f,
        maxHp = 20, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "hrumki_chicken.glb", modelScale = 1.30f,
        buildTab = BuildCategory.FOOD_DRINKS, isHealPickup = true, isDrink = false, isPassThrough = true
    ),
    HRUMKI_CHEESE(
        label = "Хрумки Сыр",
        halfW = 0.55f, height = 1.25f, halfD = 0.35f,
        maxHp = 20, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "hrumki_cheese.glb", modelScale = 1.30f,
        buildTab = BuildCategory.FOOD_DRINKS, isHealPickup = true, isDrink = false, isPassThrough = true
    ),
    HRUMKI_SPICY(
        label = "Хрумки Острые",
        halfW = 0.55f, height = 1.25f, halfD = 0.35f,
        maxHp = 20, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "hrumki_spicy.glb", modelScale = 1.30f,
        buildTab = BuildCategory.FOOD_DRINKS, isHealPickup = true, isDrink = false, isPassThrough = true
    ),
    HRUMKI_GREENS(
        label = "Хрумки Зелень",
        halfW = 0.55f, height = 1.25f, halfD = 0.35f,
        maxHp = 20, destructible = false, explosive = false, movableByBlast = true,
        firestoreType = "CRATE", glbFile = "hrumki_greens.glb", modelScale = 1.30f,
        buildTab = BuildCategory.FOOD_DRINKS, isHealPickup = true, isDrink = false, isPassThrough = true
    );

    companion object {
        val stuntsAndTraps = listOf(
            LOOP_THE_LOOP, DOUBLE_RAMP, RAMP, ROAD_RAMP, FIRE_RING, JUMP_PAD, BOOST_PAD,
            CRUSHER, PENDULUM_HAMMER, SPINNER_TRAP, FINISH_ARCH, BOWLING_PIN
        )
        val buildableBlocks = listOf(
            ROAD, ROAD_TURN, ROAD_ROUNDABOUT,
            HOUSE_SMALL, GARAGE_HANGAR, WATCHTOWER, WALL_ARCH_GATE,
            WALL, WALL_BRICK, REINFORCED_FENCE, WALL_WOOD, FRAGILE_BRICK_WALL,
            BARRIER, WOODEN_FENCE, BOOM_BARRIER, METAL_GATE,
            TIRE_STACK, TARGET, CRATE, BARREL, OIL_DRUM, TOXIC_DRUM, GAS_BARREL, WATER_BARREL,
            STREET_LAMP, BILLBOARD_SIGN, DUMPSTER, POWER_GENERATOR, RADAR_DISH, RADIO_MAST,
            SOLAR_PANEL, AMMO_CRATE, PARK_BENCH, PLANK_BRIDGE, TOWER, BRIDGE
        )
        val foodAndDrinks = listOf(
            GAZYAVA_LEMON, GAZYAVA_CLASSIC, GAZYAVA_CHICKEN, GAZYAVA_TARHUN,
            HRUMKI_CHICKEN, HRUMKI_CHEESE, HRUMKI_SPICY, HRUMKI_GREENS
        )

        fun propsForTab(tab: BuildCategory): List<PropType> = when (tab) {
            BuildCategory.STUNTS_TRAPS -> stuntsAndTraps
            BuildCategory.BLOCKS -> buildableBlocks
            BuildCategory.FOOD_DRINKS -> foodAndDrinks
            else -> emptyList()
        }

        fun fromNameSafe(raw: String): PropType =
            entries.firstOrNull { it.name == raw } ?: CRATE
    }
}

enum class VehicleType(
    val label: String,
    val maxSpeed: Float,
    val turboSpeed: Float,
    val accel: Float,
    val turnRate: Float,
    val halfW: Float,
    val halfL: Float,
    val jumpBoost: Float,
    val defaultR: Float,
    val defaultG: Float,
    val defaultB: Float,
    val glbFile: String? = null,
    val modelScale: Float = 1.10f,
    val hasSirens: Boolean = false,
    // Seated driver coordinates in vehicle GLB local space (+Z = forward, +X = driver left, +Y = up)
    val driverSeatX: Float = 0.28f,
    val driverSeatY: Float = 0.44f,
    val driverSeatZ: Float = -0.15f,
    val driverScale: Float = 0.64f,
    val driverRotatesWithTurret: Boolean = false
) {
    SPORTS_CAR(
        label = "Седан",
        maxSpeed = 38f,
        turboSpeed = 52f,
        accel = 44f,
        turnRate = 118f,
        halfW = 1.15f,
        halfL = 2.65f,
        jumpBoost = 1.15f,
        defaultR = 0.92f,
        defaultG = 0.16f,
        defaultB = 0.18f,
        glbFile = "car_sedan.glb",
        modelScale = 1.12f,
        driverSeatX = 0.26f,
        driverSeatY = 0.42f,
        driverSeatZ = -0.18f,
        driverScale = 0.62f
    ),
    MONSTER_TRUCK(
        label = "Монстр-трак",
        maxSpeed = 30f,
        turboSpeed = 43f,
        accel = 36f,
        turnRate = 98f,
        halfW = 1.56f,
        halfL = 2.68f,
        jumpBoost = 1.36f,
        defaultR = 0.20f,
        defaultG = 0.84f,
        defaultB = 0.28f,
        glbFile = "car_monster.glb",
        modelScale = 1.12f,
        driverSeatX = 0.30f,
        driverSeatY = 1.40f,
        driverSeatZ = 0.12f,
        driverScale = 0.72f
    ),
    PICKUP_4X4(
        label = "Пикап 4x4",
        maxSpeed = 29f,
        turboSpeed = 41f,
        accel = 34f,
        turnRate = 105f,
        halfW = 1.24f,
        halfL = 2.75f,
        jumpBoost = 1.12f,
        defaultR = 0.98f,
        defaultG = 0.74f,
        defaultB = 0.12f,
        glbFile = "car_pickup.glb",
        modelScale = 1.12f,
        driverSeatX = 0.28f,
        driverSeatY = 0.76f,
        driverSeatZ = 0.30f,
        driverScale = 0.70f
    ),
    POLICE_SUV(
        label = "Полиция",
        maxSpeed = 34f,
        turboSpeed = 47f,
        accel = 40f,
        turnRate = 112f,
        halfW = 1.18f,
        halfL = 2.70f,
        jumpBoost = 1.14f,
        defaultR = 0.14f,
        defaultG = 0.18f,
        defaultB = 0.28f,
        glbFile = "car_police.glb",
        modelScale = 1.14f,
        hasSirens = true,
        driverSeatX = 0.26f,
        driverSeatY = 0.44f,
        driverSeatZ = -0.18f,
        driverScale = 0.64f
    ),
    BATTLE_TANK(
        label = "Танк",
        maxSpeed = 22f,
        turboSpeed = 32f,
        accel = 26f,
        turnRate = 82f,
        halfW = 1.65f,
        halfL = 2.85f,
        jumpBoost = 0.92f,
        defaultR = 0.32f,
        defaultG = 0.42f,
        defaultB = 0.24f,
        glbFile = null,
        modelScale = 1.0f,
        driverSeatX = -0.36f,
        driverSeatY = 1.40f,
        driverSeatZ = -0.15f,
        driverScale = 0.70f,
        driverRotatesWithTurret = true
    ),
    DUNE_BUGGY(
        label = "Багги",
        maxSpeed = 36f,
        turboSpeed = 49f,
        accel = 48f,
        turnRate = 132f,
        halfW = 1.24f,
        halfL = 2.15f,
        jumpBoost = 1.44f,
        defaultR = 0.98f,
        defaultG = 0.46f,
        defaultB = 0.10f,
        glbFile = null,
        modelScale = 1.0f,
        driverSeatX = 0.0f,
        driverSeatY = 0.56f,
        driverSeatZ = -0.08f,
        driverScale = 0.62f
    ),
    FIRE_TRUCK(
        label = "Пожарная",
        maxSpeed = 26f,
        turboSpeed = 36f,
        accel = 28f,
        turnRate = 86f,
        halfW = 1.38f,
        halfL = 3.45f,
        jumpBoost = 0.96f,
        defaultR = 0.90f,
        defaultG = 0.12f,
        defaultB = 0.14f,
        glbFile = "car_firetruck.glb",
        modelScale = 1.10f,
        hasSirens = true,
        driverSeatX = 0.34f,
        driverSeatY = 1.22f,
        driverSeatZ = 1.92f,
        driverScale = 0.72f
    ),
    ARMORED_SWAT(
        label = "Внедорожник SUV",
        maxSpeed = 28f,
        turboSpeed = 39f,
        accel = 32f,
        turnRate = 92f,
        halfW = 1.26f,
        halfL = 2.80f,
        jumpBoost = 1.02f,
        defaultR = 0.14f,
        defaultG = 0.24f,
        defaultB = 0.44f,
        glbFile = "car_suv.glb",
        modelScale = 1.14f,
        driverSeatX = 0.30f,
        driverSeatY = 0.84f,
        driverSeatZ = 0.08f,
        driverScale = 0.72f
    ),
    CYBER_SUPERCAR(
        label = "Гиперкар",
        maxSpeed = 44f,
        turboSpeed = 58f,
        accel = 54f,
        turnRate = 124f,
        halfW = 1.22f,
        halfL = 2.72f,
        jumpBoost = 1.22f,
        defaultR = 0.46f,
        defaultG = 0.18f,
        defaultB = 0.88f,
        glbFile = "car_hypercar.glb",
        modelScale = 1.14f,
        driverSeatX = 0.24f,
        driverSeatY = 0.36f,
        driverSeatZ = -0.14f,
        driverScale = 0.54f
    )
}

enum class GameMap(
    val label: String,
    val subtitle: String,
    val skyHorizonR: Float,
    val skyHorizonG: Float,
    val skyHorizonB: Float,
    val skyZenithR: Float,
    val skyZenithG: Float,
    val skyZenithB: Float,
    val fogR: Float,
    val fogG: Float,
    val fogB: Float,
    val wallR: Float,
    val wallG: Float,
    val wallB: Float,
    val railR: Float,
    val railG: Float,
    val railB: Float,
    val treeR: Float,
    val treeG: Float,
    val treeB: Float
) {
    MAIN_ARENA(
        label = "Главная Арена",
        subtitle = "Кольцо, дома, ангар, вышка, трамплины и интерактивные ворота",
        skyHorizonR = 0.94f, skyHorizonG = 0.90f, skyHorizonB = 0.82f,
        skyZenithR = 0.12f, skyZenithG = 0.46f, skyZenithB = 0.94f,
        fogR = 0.78f, fogG = 0.90f, fogB = 0.96f,
        wallR = 0.52f, wallG = 0.56f, wallB = 0.62f,
        railR = 0.96f, railG = 0.56f, railB = 0.14f,
        treeR = 0.18f, treeG = 0.58f, treeB = 0.24f
    ),
    TURBO_SPEEDWAY(
        label = "Турбо-Трек",
        subtitle = "Скоростная трасса с поворотами, петлёй 360°, огненными кольцами и трамплинами",
        skyHorizonR = 0.98f, skyHorizonG = 0.76f, skyHorizonB = 0.58f,
        skyZenithR = 0.14f, skyZenithG = 0.34f, skyZenithB = 0.84f,
        fogR = 0.92f, fogG = 0.80f, fogB = 0.72f,
        wallR = 0.40f, wallG = 0.44f, wallB = 0.54f,
        railR = 0.98f, railG = 0.26f, railB = 0.20f,
        treeR = 0.24f, treeG = 0.62f, treeB = 0.26f
    ),
    TACTICAL_COMPOUND(
        label = "Штурм-База",
        subtitle = "Плотная застройка для перестрелок: укрытия, вышки, ангары и разрушаемые стены",
        skyHorizonR = 0.86f, skyHorizonG = 0.88f, skyHorizonB = 0.92f,
        skyZenithR = 0.22f, skyZenithG = 0.38f, skyZenithB = 0.62f,
        fogR = 0.78f, fogG = 0.82f, fogB = 0.86f,
        wallR = 0.44f, wallG = 0.46f, wallB = 0.48f,
        railR = 0.92f, railG = 0.72f, railB = 0.16f,
        treeR = 0.26f, treeG = 0.46f, treeB = 0.24f
    );

    companion object {
        fun fromNameSafe(raw: String?): GameMap =
            entries.firstOrNull { it.name == raw } ?: MAIN_ARENA
    }
}

enum class ChickenSkin(
    val label: String,
    val featherR: Float,
    val featherG: Float,
    val featherB: Float,
    val vestR: Float,
    val vestG: Float,
    val vestB: Float,
    val visorR: Float,
    val visorG: Float,
    val visorB: Float,
    val combR: Float,
    val combG: Float,
    val combB: Float,
    val helmetR: Float,
    val helmetG: Float,
    val helmetB: Float
) {
    CLASSIC(
        label = "Классик",
        featherR = 0.98f, featherG = 0.97f, featherB = 0.94f,
        vestR = 0.98f, vestG = 0.62f, vestB = 0.12f,
        visorR = 0.16f, visorG = 0.64f, visorB = 0.88f,
        combR = 0.96f, combG = 0.12f, combB = 0.14f,
        helmetR = 0.22f, helmetG = 0.26f, helmetB = 0.28f
    ),
    SPEC_OPS(
        label = "Спецназ",
        featherR = 0.84f, featherG = 0.86f, featherB = 0.88f,
        vestR = 0.18f, vestG = 0.48f, vestB = 0.32f,
        visorR = 0.14f, visorG = 0.92f, visorB = 0.68f,
        combR = 0.92f, combG = 0.18f, combB = 0.16f,
        helmetR = 0.16f, helmetG = 0.24f, helmetB = 0.18f
    ),
    GOLDEN_KING(
        label = "Золотой",
        featherR = 1.00f, featherG = 0.84f, featherB = 0.22f,
        vestR = 0.88f, vestG = 0.16f, vestB = 0.20f,
        visorR = 1.00f, visorG = 0.92f, visorB = 0.40f,
        combR = 0.98f, combG = 0.10f, combB = 0.18f,
        helmetR = 0.84f, helmetG = 0.64f, helmetB = 0.10f
    ),
    CYBER_NEON(
        label = "Кибер-Неон",
        featherR = 0.26f, featherG = 0.34f, featherB = 0.56f,
        vestR = 0.10f, vestG = 0.86f, vestB = 0.98f,
        visorR = 0.98f, visorG = 0.24f, visorB = 0.88f,
        combR = 0.16f, combG = 0.95f, combB = 0.98f,
        helmetR = 0.14f, helmetG = 0.16f, helmetB = 0.32f
    ),
    SHADOW_NINJA(
        label = "Ниндзя",
        featherR = 0.20f, featherG = 0.22f, featherB = 0.28f,
        vestR = 0.86f, vestG = 0.14f, vestB = 0.22f,
        visorR = 1.00f, visorG = 0.28f, visorB = 0.20f,
        combR = 0.94f, combG = 0.12f, combB = 0.16f,
        helmetR = 0.10f, helmetG = 0.11f, helmetB = 0.15f
    ),
    ARCTIC_FROST(
        label = "Арктик",
        featherR = 0.92f, featherG = 0.98f, featherB = 1.00f,
        vestR = 0.18f, vestG = 0.68f, vestB = 0.94f,
        visorR = 0.32f, visorG = 0.92f, visorB = 1.00f,
        combR = 0.22f, combG = 0.72f, combB = 0.98f,
        helmetR = 0.76f, helmetG = 0.88f, helmetB = 0.96f
    );

    companion object {
        fun fromNameSafe(raw: String?): ChickenSkin =
            entries.firstOrNull { it.name == raw } ?: CLASSIC
    }
}

enum class CameraMode {
    FIRST_PERSON,
    THIRD_PERSON,
    FREE_CAM
}

class WorldProp(
    val id: String,
    val type: PropType,
    var x: Float,
    var y: Float,
    var z: Float,
    var yaw: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var hp: Int = type.maxHp,
    var destroyed: Boolean = false,
    val userPlaced: Boolean = false,
    var remoteId: String? = null,
    // Dynamic animation & physics properties for traps, targets, pins, pads & pickups
    var tiltAngle: Float = 0f,
    var tiltVel: Float = 0f,
    var spinAngle: Float = 0f,
    var bounceTimer: Float = 0f,
    var trapPhaseOffset: Float = 0f,
    var trapCooldown: Float = 0f,
    var targetDownTimer: Float = 0f,
    // Interactive door / gate animation & pass-through state (house_small, garage_hangar, watchtower, wall_arch_gate)
    var doorOpen: Boolean = false,
    var doorAnimTime: Float = 0f,
    var doorTouched: Boolean = false
) {
    val cosYaw: Float = kotlin.math.cos(yaw * kotlin.math.PI.toFloat() / 180f)
    val sinYaw: Float = kotlin.math.sin(yaw * kotlin.math.PI.toFloat() / 180f)
    val boundRadius: Float = sqrt(type.halfW * type.halfW + type.halfD * type.halfD) + 0.25f
}

class VehicleEntity(
    val id: String,
    val type: VehicleType = VehicleType.PICKUP_4X4,
    var x: Float,
    var y: Float,
    var z: Float,
    var yaw: Float,
    var speed: Float = 0f,
    var vy: Float = 0f,
    var wheelSpin: Float = 0f,
    var steerAngle: Float = 0f,
    var grounded: Boolean = true,
    val colorR: Float = type.defaultR,
    val colorG: Float = type.defaultG,
    val colorB: Float = type.defaultB,
    var nitroTimer: Float = 0f,
    var turretYaw: Float = 0f,
    var remoteId: String? = null,
    // Suspension, body dynamics & drift physics
    var pitchAngle: Float = 0f,
    var rollAngle: Float = 0f,
    var suspensionOffset: Float = 0f,
    var suspensionVel: Float = 0f,
    var lateralVel: Float = 0f,
    var cannonFlashTimer: Float = 0f,
    var collisionSoundCooldown: Float = 0f,
    var currentRpm: Float = 0.16f,
    var currentGear: Int = 1,
    // 360-degree Loop-the-Loop stunt ride state
    var loopPropId: String? = null,
    var loopProgress: Float = 0f,
    var loopSpeed: Float = 0f,
    var loopDirection: Float = 1f
)

enum class PigBurgerState {
    IDLE,
    WALK,
    PANIC_JUMP,
    FLEE_RUN,
    HAPPY_HOP
}

/**
 * Living, interactive NPC Хряк-Бургер (`pig_burger_animated.glb`).
 * Wanders, panics on shots/horns/collisions, loves being petted/fed, and drops tasty food loot on hit!
 */
class PigBurgerNpc(
    val id: String,
    val name: String,
    var x: Float,
    var y: Float,
    var z: Float,
    var yaw: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var state: PigBurgerState = PigBurgerState.WALK,
    var stateTimer: Float = 2.5f,
    var animTime: Float = 0f,
    var grounded: Boolean = true,
    var hitFlash: Float = 0f,
    var happyTimer: Float = 0f,
    var oinkCooldown: Float = 0f,
    var lootCooldown: Float = 0f,
    var knockbackTimer: Float = 0f,
    val userPlaced: Boolean = false
)

data class LobbyBotSlot(
    val id: String,
    val name: String,
    val weapon: WeaponType,
    val accentR: Float,
    val accentG: Float,
    val accentB: Float
) {
    companion object {
        private val presets = listOf(
            Triple("Командир Хряк", WeaponType.RIFLE, Triple(0.20f, 0.72f, 0.35f)),
            Triple("Ракетчик Наполеон", WeaponType.RPG, Triple(0.92f, 0.25f, 0.22f)),
            Triple("Снайпер Пятачок", WeaponType.PISTOL, Triple(0.22f, 0.58f, 0.92f)),
            Triple("Штурмовик Куро", WeaponType.RIFLE, Triple(0.85f, 0.45f, 0.15f)),
            Triple("Берсерк Бургер", WeaponType.RIFLE, Triple(0.68f, 0.28f, 0.85f)),
            Triple("Танкист Перьевой", WeaponType.RPG, Triple(0.95f, 0.72f, 0.15f)),
            Triple("Разведчик Хрумка", WeaponType.PISTOL, Triple(0.18f, 0.82f, 0.78f)),
            Triple("Капитан Газява", WeaponType.RIFLE, Triple(0.92f, 0.35f, 0.65f))
        )

        fun defaultRoster(count: Int = 5): List<LobbyBotSlot> =
            (0 until count.coerceIn(0, presets.size)).map { index ->
                createSlot(index)
            }

        fun createSlot(index: Int): LobbyBotSlot {
            val (name, weapon, color) = presets[index % presets.size]
            val suffix = if (index >= presets.size) " #${index + 1}" else ""
            return LobbyBotSlot(
                id = "bot_slot_$index",
                name = "$name$suffix",
                weapon = weapon,
                accentR = color.first,
                accentG = color.second,
                accentB = color.third
            )
        }
    }
}

class BotChicken(
    val id: String,
    val name: String,
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var yaw: Float = 0f,
    var hp: Int = 100,
    var alive: Boolean = true,
    var respawnTimer: Float = 0f,
    var shootCooldown: Float = 1.2f,
    var strafeDir: Float = 1f,
    var strafeTimer: Float = 1.5f,
    var walkCycle: Float = 0f,
    var hitFlash: Float = 0f,
    var muzzleFlash: Float = 0f,
    val weapon: WeaponType = WeaponType.RIFLE,
    val accentR: Float = 0.2f,
    val accentG: Float = 0.65f,
    val accentB: Float = 0.3f
)

class RemoteChicken(
    val userId: String,
    var displayName: String,
    var targetX: Float,
    var targetY: Float,
    var targetZ: Float,
    var targetYaw: Float,
    var x: Float = targetX,
    var y: Float = targetY,
    var z: Float = targetZ,
    var yaw: Float = targetYaw,
    var hp: Int = 100,
    var kills: Int = 0,
    var weapon: WeaponType = WeaponType.RIFLE,
    var drivingVehicle: Boolean = false,
    var shooting: Boolean = false,
    var muzzleFlashTimer: Float = 0f,
    var walkCycle: Float = 0f,
    var hitFlash: Float = 0f,
    var ramCooldown: Float = 0f,
    var godMode: Boolean = false
)

class Projectile(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    val damage: Int,
    val fromPlayer: Boolean,
    val explosive: Boolean,
    var life: Float = 3.0f
)

class ExplosionEffect(
    val x: Float,
    val y: Float,
    val z: Float,
    var age: Float = 0f,
    val maxAge: Float = 0.70f,
    val maxRadius: Float = 6.5f
)

class Particle3D(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var size: Float,
    var age: Float,
    val maxAge: Float,
    val r: Float,
    val g: Float,
    val b: Float
)

data class MinimapBlip(
    val relX: Float,
    val relZ: Float,
    val kind: BlipKind
)

enum class BlipKind {
    BOT,
    ONLINE_PLAYER,
    VEHICLE,
    BARREL,
    PIG_BURGER,
    PICKUP
}

enum class DamageKind {
    NORMAL,
    CRITICAL,
    EXPLOSIVE,
    VEHICLE_RAM,
    PROP
}

class DamagePopup3D(
    val id: Long,
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    val amount: Int,
    val kind: DamageKind,
    var age: Float = 0f,
    val maxAge: Float = 0.95f,
    var screenX: Float = -1f,
    var screenY: Float = -1f,
    var screenScale: Float = 1f,
    var screenVisible: Boolean = false
)

data class ProjectedDamageText(
    val id: Long,
    val screenX: Float,
    val screenY: Float,
    val amount: Int,
    val kind: DamageKind,
    val progress: Float,
    val scale: Float
)

data class KillFeedEntry(
    val id: Long,
    val message: String
)

data class HudSnapshot(
    val hp: Int = 100,
    val alive: Boolean = true,
    val peacefulMode: Boolean = false,
    val spawnProtectionSec: Float = 3.0f,
    val kills: Int = 0,
    val streak: Int = 0,
    val killStreakBanner: String = "",
    val weapon: WeaponType = WeaponType.RIFLE,
    val weaponSkin: WeaponSkin = WeaponSkin.CLASSIC,
    val ammo: Int = 30,
    val maxAmmo: Int = 30,
    val reloading: Boolean = false,
    val reloadProgress: Float = 0f,
    val weaponSpread: Float = 0f,
    val cameraMode: CameraMode = CameraMode.FIRST_PERSON,
    val freeCamActive: Boolean = false,
    val vehicleCamOrbitYaw: Float = 0f,
    val aiming: Boolean = false,
    val inVehicle: Boolean = false,
    val nearVehicle: Boolean = false,
    val activeVehicleName: String = "",
    val nearVehicleName: String = "",
    val vehicleSpeedKmh: Int = 0,
    val vehicleNitroFrac: Float = 0f,
    val vehicleRpm: Float = 0f,
    val vehicleGear: Int = 1,
    val nearPigBurger: Boolean = false,
    val nearPigBurgerName: String = "",
    val nearDoorProp: Boolean = false,
    val nearDoorLabel: String = "",
    val nearDoorIsOpen: Boolean = false,
    val pigBurgerCount: Int = 0,
    val raceActive: Boolean = false,
    val raceLapCount: Int = 0,
    val raceCurrentLapSec: Float = 0f,
    val raceBestLapSec: Float = 0f,
    val raceBannerText: String = "",
    val buildModeOpen: Boolean = false,
    val buildCategory: BuildCategory = BuildCategory.STUNTS_TRAPS,
    val selectedBuildProp: PropType = PropType.LOOP_THE_LOOP,
    val selectedVehicleType: VehicleType = VehicleType.POLICE_SUV,
    val buildGridSnap: Boolean = true,
    val ghostPlacementValid: Boolean = true,
    val ghostYawOffset: Float = 0f,
    val userPlacedCount: Int = 0,
    val hitMarkerAlpha: Float = 0f,
    val critHitFlash: Boolean = false,
    val damageVignetteAlpha: Float = 0f,
    val botsAlive: Int = 5,
    val onlinePlayersCount: Int = 0,
    val isOnlineMode: Boolean = false,
    val isOnlineHost: Boolean = false,
    val onlineGameModeLabel: String = "",
    val canBuildProps: Boolean = true,
    val canSpawnVehicles: Boolean = true,
    val godMode: Boolean = false,
    val infiniteAmmo: Boolean = false,
    val activeMapLabel: String = GameMap.MAIN_ARENA.label,
    val fps: Int = 120,
    val playerYawDeg: Float = 0f,
    val minimapBlips: List<MinimapBlip> = emptyList(),
    val killFeed: List<KillFeedEntry> = emptyList(),
    val damagePopups: List<ProjectedDamageText> = emptyList()
)

class GameInputState {
    @Volatile var moveX: Float = 0f
    @Volatile var moveY: Float = 0f
    @Volatile var firing: Boolean = false
    @Volatile var jumpQueued: Boolean = false
    @Volatile var gasPressed: Boolean = false
    @Volatile var brakePressed: Boolean = false
    @Volatile var steerLeft: Boolean = false
    @Volatile var steerRight: Boolean = false
    @Volatile var turboQueued: Boolean = false
    @Volatile var flyUp: Boolean = false
    @Volatile var flyDown: Boolean = false

    private val lock = Any()
    private var pendingLookYaw: Float = 0f
    private var pendingLookPitch: Float = 0f
    private var pendingGyroYawDeg: Float = 0f
    private var pendingGyroPitchDeg: Float = 0f

    fun resetMotionControls() {
        moveX = 0f
        moveY = 0f
        firing = false
        jumpQueued = false
        gasPressed = false
        brakePressed = false
        steerLeft = false
        steerRight = false
        turboQueued = false
        flyUp = false
        flyDown = false
        synchronized(lock) {
            pendingLookYaw = 0f
            pendingLookPitch = 0f
            pendingGyroYawDeg = 0f
            pendingGyroPitchDeg = 0f
        }
    }

    fun addLookDelta(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) return
        synchronized(lock) {
            pendingLookYaw += dx
            pendingLookPitch += dy
        }
    }

    fun consumeLookDelta(): Pair<Float, Float> {
        synchronized(lock) {
            val out = Pair(pendingLookYaw, pendingLookPitch)
            pendingLookYaw = 0f
            pendingLookPitch = 0f
            return out
        }
    }

    fun addGyroDelta(dYawDeg: Float, dPitchDeg: Float) {
        if (!dYawDeg.isFinite() || !dPitchDeg.isFinite()) return
        synchronized(lock) {
            pendingGyroYawDeg += dYawDeg
            pendingGyroPitchDeg += dPitchDeg
        }
    }

    fun consumeGyroDelta(): Pair<Float, Float> {
        synchronized(lock) {
            val out = Pair(pendingGyroYawDeg, pendingGyroPitchDeg)
            pendingGyroYawDeg = 0f
            pendingGyroPitchDeg = 0f
            return out
        }
    }
}
