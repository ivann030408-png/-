package com.example.data

/**
 * Identifiers for every customizable on-screen HUD control in landscape gameplay.
 */
enum class HudControlId(
    val label: String,
    val baseWidthDp: Float,
    val baseHeightDp: Float,
    val isCircle: Boolean
) {
    JOYSTICK("Джойстик", 126f, 126f, true),
    FIRE("Огонь", 82f, 82f, true),
    JUMP("Прыжок", 60f, 60f, true),
    AIM("Прицел", 54f, 54f, true),
    SWITCH_WEAPON("Смена оружия", 54f, 54f, true),
    BUILD("Стройка", 96f, 48f, false),
    CAMERA("Камера 1P/3P", 80f, 48f, false),
    WEAPON_BAR("Панель оружия", 244f, 44f, false);

    companion object {
        fun fromNameSafe(raw: String): HudControlId? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * Normalized center position (xFrac, yFrac in 0.05..0.95 of safe HUD area),
 * per-button scale multiplier, and opacity.
 */
data class HudControlSlot(
    val xFrac: Float,
    val yFrac: Float,
    val scale: Float = 1.0f,
    val alpha: Float = 0.90f
) {
    fun sanitized(): HudControlSlot = copy(
        xFrac = xFrac.coerceIn(0.05f, 0.95f),
        yFrac = yFrac.coerceIn(0.06f, 0.94f),
        scale = scale.coerceIn(0.70f, 1.45f),
        alpha = alpha.coerceIn(0.35f, 1.00f)
    )
}

/**
 * Full customizable HUD layout persisted as a compact string in SharedPreferences.
 */
data class CustomHudLayout(
    val customized: Boolean = false,
    val slots: Map<HudControlId, HudControlSlot> = defaultSlots(leftHanded = false)
) {
    fun slotFor(id: HudControlId, leftHanded: Boolean): HudControlSlot {
        if (!customized) {
            return defaultSlots(leftHanded)[id] ?: HudControlSlot(0.5f, 0.5f)
        }
        return slots[id] ?: defaultSlots(leftHanded)[id] ?: HudControlSlot(0.5f, 0.5f)
    }

    fun withUpdatedSlot(id: HudControlId, slot: HudControlSlot, leftHanded: Boolean): CustomHudLayout {
        val base = if (customized) slots else defaultSlots(leftHanded)
        return CustomHudLayout(
            customized = true,
            slots = base + (id to slot.sanitized())
        )
    }

    fun serialize(): String {
        if (!customized) return ""
        return HudControlId.entries.joinToString(";") { id ->
            val s = (slots[id] ?: defaultSlots(false)[id]!!).sanitized()
            "${id.name}:${"%.4f".format(java.util.Locale.US, s.xFrac)},${"%.4f".format(java.util.Locale.US, s.yFrac)},${"%.3f".format(java.util.Locale.US, s.scale)},${"%.3f".format(java.util.Locale.US, s.alpha)}"
        }
    }

    companion object {
        fun defaultSlots(leftHanded: Boolean): Map<HudControlId, HudControlSlot> {
            val base = mapOf(
                HudControlId.JOYSTICK to HudControlSlot(0.13f, 0.76f, 1.0f, 0.90f),
                HudControlId.FIRE to HudControlSlot(0.89f, 0.77f, 1.0f, 0.92f),
                HudControlId.JUMP to HudControlSlot(0.76f, 0.81f, 1.0f, 0.90f),
                HudControlId.AIM to HudControlSlot(0.78f, 0.57f, 1.0f, 0.90f),
                HudControlId.SWITCH_WEAPON to HudControlSlot(0.89f, 0.54f, 1.0f, 0.90f),
                HudControlId.CAMERA to HudControlSlot(0.80f, 0.10f, 1.0f, 0.92f),
                HudControlId.BUILD to HudControlSlot(0.80f, 0.25f, 1.0f, 0.92f),
                HudControlId.WEAPON_BAR to HudControlSlot(0.47f, 0.90f, 1.0f, 0.92f)
            )
            if (!leftHanded) return base
            return base.mapValues { (id, slot) ->
                when (id) {
                    HudControlId.JOYSTICK,
                    HudControlId.FIRE,
                    HudControlId.JUMP,
                    HudControlId.AIM,
                    HudControlId.SWITCH_WEAPON -> slot.copy(xFrac = (1f - slot.xFrac).coerceIn(0.06f, 0.94f))
                    else -> slot
                }
            }
        }

        fun deserialize(raw: String?): CustomHudLayout {
            if (raw.isNullOrBlank()) return CustomHudLayout(customized = false)
            val parsed = mutableMapOf<HudControlId, HudControlSlot>()
            raw.split(";").forEach { entry ->
                val parts = entry.split(":")
                if (parts.size != 2) return@forEach
                val id = HudControlId.fromNameSafe(parts[0]) ?: return@forEach
                val nums = parts[1].split(",")
                if (nums.size < 4) return@forEach
                val x = nums[0].toFloatOrNull() ?: return@forEach
                val y = nums[1].toFloatOrNull() ?: return@forEach
                val sc = nums[2].toFloatOrNull() ?: 1.0f
                val al = nums[3].toFloatOrNull() ?: 0.90f
                parsed[id] = HudControlSlot(x, y, sc, al).sanitized()
            }
            return if (parsed.isEmpty()) {
                CustomHudLayout(customized = false)
            } else {
                CustomHudLayout(
                    customized = true,
                    slots = defaultSlots(false) + parsed
                )
            }
        }
    }
}
