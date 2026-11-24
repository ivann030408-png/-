package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ChickenSkin
import com.example.engine.GameMap
import com.example.engine.PropType
import com.example.engine.VehicleType
import com.example.engine.WeaponSkin
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.BunkerSlate
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.SteelSurface
import com.example.ui.theme.TacticalNavy

/**
 * Custom side-profile miniature illustrations for all 9 drivable 3D vehicles
 * so players immediately recognize every car on the build/spawn icons.
 */
@Composable
fun VehiclePreviewIcon(
    type: VehicleType,
    modifier: Modifier = Modifier.size(width = 48.dp, height = 28.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val body = Color(type.defaultR, type.defaultG, type.defaultB)
        val bodyDark = Color(type.defaultR * 0.72f, type.defaultG * 0.72f, type.defaultB * 0.72f)
        val glass = Color(0xFF4FC3F7)
        val tire = Color(0xFF1A1E26)
        val rim = Color(0xFFDDE4EE)
        val darkMetal = Color(0xFF2B313C)

        fun wheel(cx: Float, cy: Float, r: Float) {
            drawCircle(tire, radius = r, center = Offset(cx, cy))
            drawCircle(rim, radius = r * 0.50f, center = Offset(cx, cy))
            drawCircle(darkMetal, radius = r * 0.20f, center = Offset(cx, cy))
        }

        when (type) {
            VehicleType.SPORTS_CAR -> {
                drawRoundRect(body, Offset(w * 0.08f, h * 0.48f), Size(w * 0.84f, h * 0.28f), CornerRadius(5f, 5f))
                val cab = Path().apply {
                    moveTo(w * 0.26f, h * 0.50f)
                    lineTo(w * 0.42f, h * 0.26f)
                    lineTo(w * 0.68f, h * 0.26f)
                    lineTo(w * 0.78f, h * 0.50f)
                    close()
                }
                drawPath(cab, bodyDark)
                drawRoundRect(glass, Offset(w * 0.42f, h * 0.30f), Size(w * 0.25f, h * 0.16f), CornerRadius(3f, 3f))
                drawLine(darkMetal, Offset(w * 0.14f, h * 0.48f), Offset(w * 0.10f, h * 0.28f), strokeWidth = 2.4f)
                drawRoundRect(darkMetal, Offset(w * 0.04f, h * 0.24f), Size(w * 0.14f, h * 0.08f), CornerRadius(2f, 2f))
                drawRoundRect(Color(0xFFFFF59D), Offset(w * 0.86f, h * 0.52f), Size(w * 0.07f, h * 0.10f), CornerRadius(2f, 2f))
                wheel(w * 0.26f, h * 0.76f, h * 0.18f)
                wheel(w * 0.74f, h * 0.76f, h * 0.18f)
            }

            VehicleType.MONSTER_TRUCK -> {
                drawLine(Color(0xFFE53935), Offset(w * 0.25f, h * 0.50f), Offset(w * 0.25f, h * 0.72f), strokeWidth = 2.6f)
                drawLine(Color(0xFFE53935), Offset(w * 0.75f, h * 0.50f), Offset(w * 0.75f, h * 0.72f), strokeWidth = 2.6f)
                drawRoundRect(body, Offset(w * 0.12f, h * 0.30f), Size(w * 0.78f, h * 0.24f), CornerRadius(4f, 4f))
                drawRoundRect(bodyDark, Offset(w * 0.36f, h * 0.12f), Size(w * 0.30f, h * 0.20f), CornerRadius(3f, 3f))
                drawRoundRect(glass, Offset(w * 0.48f, h * 0.16f), Size(w * 0.15f, h * 0.13f), CornerRadius(2f, 2f))
                drawCircle(Color(0xFFFFF59D), radius = 2.4f, center = Offset(w * 0.44f, h * 0.09f))
                drawCircle(Color(0xFFFFF59D), radius = 2.4f, center = Offset(w * 0.54f, h * 0.09f))
                wheel(w * 0.25f, h * 0.70f, h * 0.26f)
                wheel(w * 0.75f, h * 0.70f, h * 0.26f)
            }

            VehicleType.PICKUP_4X4 -> {
                drawRoundRect(body, Offset(w * 0.08f, h * 0.46f), Size(w * 0.84f, h * 0.28f), CornerRadius(4f, 4f))
                drawRoundRect(bodyDark, Offset(w * 0.44f, h * 0.22f), Size(w * 0.26f, h * 0.26f), CornerRadius(3f, 3f))
                drawRoundRect(glass, Offset(w * 0.52f, h * 0.26f), Size(w * 0.15f, h * 0.16f), CornerRadius(2f, 2f))
                drawLine(darkMetal, Offset(w * 0.40f, h * 0.46f), Offset(w * 0.40f, h * 0.22f), strokeWidth = 2.5f)
                drawLine(darkMetal, Offset(w * 0.40f, h * 0.23f), Offset(w * 0.26f, h * 0.46f), strokeWidth = 2.0f)
                drawRoundRect(Color(0xFFFFF59D), Offset(w * 0.86f, h * 0.50f), Size(w * 0.06f, h * 0.12f), CornerRadius(2f, 2f))
                wheel(w * 0.25f, h * 0.74f, h * 0.20f)
                wheel(w * 0.75f, h * 0.74f, h * 0.20f)
            }

            VehicleType.POLICE_SUV -> {
                drawRoundRect(Color(0xFF1B2230), Offset(w * 0.08f, h * 0.44f), Size(w * 0.84f, h * 0.30f), CornerRadius(4f, 4f))
                drawRect(Color(0xFFF4F6FA), Offset(w * 0.34f, h * 0.45f), Size(w * 0.30f, h * 0.27f))
                drawRoundRect(Color(0xFF1B2230), Offset(w * 0.18f, h * 0.20f), Size(w * 0.54f, h * 0.26f), CornerRadius(4f, 4f))
                drawRoundRect(glass, Offset(w * 0.24f, h * 0.24f), Size(w * 0.42f, h * 0.16f), CornerRadius(2f, 2f))
                drawRoundRect(Color(0xFFFF2A36), Offset(w * 0.38f, h * 0.12f), Size(w * 0.11f, h * 0.09f), CornerRadius(2f, 2f))
                drawRoundRect(Color(0xFF2979FF), Offset(w * 0.49f, h * 0.12f), Size(w * 0.11f, h * 0.09f), CornerRadius(2f, 2f))
                wheel(w * 0.25f, h * 0.74f, h * 0.19f)
                wheel(w * 0.75f, h * 0.74f, h * 0.19f)
            }

            VehicleType.BATTLE_TANK -> {
                drawRoundRect(tire, Offset(w * 0.08f, h * 0.60f), Size(w * 0.76f, h * 0.32f), CornerRadius(8f, 8f))
                for (i in 0..4) {
                    val rx = w * (0.17f + i * 0.145f)
                    drawCircle(Color(0xFF56634D), radius = h * 0.10f, center = Offset(rx, h * 0.76f))
                }
                drawRoundRect(body, Offset(w * 0.10f, h * 0.44f), Size(w * 0.72f, h * 0.22f), CornerRadius(4f, 4f))
                drawRoundRect(Color(0xFF6B8550), Offset(w * 0.26f, h * 0.22f), Size(w * 0.36f, h * 0.24f), CornerRadius(5f, 5f))
                drawLine(Color(0xFF455736), Offset(w * 0.58f, h * 0.32f), Offset(w * 0.96f, h * 0.30f), strokeWidth = 4.2f, cap = StrokeCap.Round)
                drawCircle(darkMetal, radius = 2.8f, center = Offset(w * 0.95f, h * 0.30f))
            }

            VehicleType.DUNE_BUGGY -> {
                drawRoundRect(body, Offset(w * 0.12f, h * 0.50f), Size(w * 0.78f, h * 0.22f), CornerRadius(5f, 5f))
                drawLine(darkMetal, Offset(w * 0.24f, h * 0.52f), Offset(w * 0.36f, h * 0.18f), strokeWidth = 2.6f)
                drawLine(darkMetal, Offset(w * 0.36f, h * 0.18f), Offset(w * 0.62f, h * 0.18f), strokeWidth = 2.6f)
                drawLine(darkMetal, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.74f, h * 0.52f), strokeWidth = 2.6f)
                drawCircle(Color(0xFFFFF59D), radius = 2.6f, center = Offset(w * 0.60f, h * 0.14f))
                drawRoundRect(Color(0xFF788598), Offset(w * 0.10f, h * 0.36f), Size(w * 0.16f, h * 0.16f), CornerRadius(3f, 3f))
                wheel(w * 0.24f, h * 0.74f, h * 0.21f)
                wheel(w * 0.78f, h * 0.74f, h * 0.20f)
            }

            VehicleType.FIRE_TRUCK -> {
                drawRoundRect(body, Offset(w * 0.06f, h * 0.32f), Size(w * 0.88f, h * 0.42f), CornerRadius(4f, 4f))
                drawRect(Color(0xFFF8FAFC), Offset(w * 0.06f, h * 0.52f), Size(w * 0.88f, h * 0.08f))
                drawRoundRect(glass, Offset(w * 0.72f, h * 0.37f), Size(w * 0.18f, h * 0.16f), CornerRadius(2f, 2f))
                drawLine(Color(0xFFE2E8F0), Offset(w * 0.10f, h * 0.26f), Offset(w * 0.76f, h * 0.14f), strokeWidth = 3.2f, cap = StrokeCap.Round)
                drawLine(Color(0xFFCBD5E1), Offset(w * 0.10f, h * 0.31f), Offset(w * 0.76f, h * 0.19f), strokeWidth = 2.0f)
                drawRoundRect(Color(0xFFFF3D00), Offset(w * 0.78f, h * 0.22f), Size(w * 0.10f, h * 0.10f), CornerRadius(2f, 2f))
                wheel(w * 0.22f, h * 0.74f, h * 0.20f)
                wheel(w * 0.78f, h * 0.74f, h * 0.20f)
            }

            VehicleType.ARMORED_SWAT -> {
                drawRoundRect(body, Offset(w * 0.08f, h * 0.28f), Size(w * 0.76f, h * 0.46f), CornerRadius(4f, 4f))
                val nose = Path().apply {
                    moveTo(w * 0.78f, h * 0.36f)
                    lineTo(w * 0.92f, h * 0.46f)
                    lineTo(w * 0.92f, h * 0.74f)
                    lineTo(w * 0.78f, h * 0.74f)
                    close()
                }
                drawPath(nose, bodyDark)
                drawRect(darkMetal, Offset(w * 0.90f, h * 0.48f), Size(w * 0.07f, h * 0.26f))
                drawRoundRect(glass, Offset(w * 0.64f, h * 0.34f), Size(w * 0.14f, h * 0.12f), CornerRadius(2f, 2f))
                drawRoundRect(Color(0xFF90CAF9), Offset(w * 0.26f, h * 0.36f), Size(w * 0.14f, h * 0.09f), CornerRadius(2f, 2f))
                drawRoundRect(Color(0xFF2979FF), Offset(w * 0.62f, h * 0.18f), Size(w * 0.12f, h * 0.10f), CornerRadius(2f, 2f))
                wheel(w * 0.25f, h * 0.74f, h * 0.21f)
                wheel(w * 0.74f, h * 0.74f, h * 0.21f)
            }

            VehicleType.CYBER_SUPERCAR -> {
                val wedge = Path().apply {
                    moveTo(w * 0.06f, h * 0.72f)
                    lineTo(w * 0.08f, h * 0.48f)
                    lineTo(w * 0.38f, h * 0.32f)
                    lineTo(w * 0.66f, h * 0.36f)
                    lineTo(w * 0.94f, h * 0.56f)
                    lineTo(w * 0.94f, h * 0.72f)
                    close()
                }
                drawPath(wedge, body)
                drawRoundRect(Color(0xFF00E5FF), Offset(w * 0.38f, h * 0.36f), Size(w * 0.26f, h * 0.14f), CornerRadius(5f, 5f))
                drawLine(Color(0xFF00E5FF), Offset(w * 0.10f, h * 0.72f), Offset(w * 0.92f, h * 0.72f), strokeWidth = 2.6f)
                drawLine(darkMetal, Offset(w * 0.14f, h * 0.48f), Offset(w * 0.08f, h * 0.24f), strokeWidth = 2.4f)
                drawRoundRect(Color(0xFFE040FB), Offset(w * 0.02f, h * 0.20f), Size(w * 0.16f, h * 0.08f), CornerRadius(2f, 2f))
                wheel(w * 0.24f, h * 0.74f, h * 0.19f)
                wheel(w * 0.76f, h * 0.74f, h * 0.19f)
            }
        }
    }
}

/**
 * Custom miniature illustrations for all 31 buildable sandbox props (stunts, traps, construction, food & drinks).
 */
@Composable
fun BlockPreviewIcon(
    type: PropType,
    modifier: Modifier = Modifier.size(width = 36.dp, height = 28.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (type) {
            PropType.CRATE -> {
                drawRoundRect(Color(0xFFC28742), Offset(w * 0.16f, h * 0.12f), Size(w * 0.68f, h * 0.76f), CornerRadius(3f, 3f))
                drawRoundRect(Color(0xFF7B4D24), Offset(w * 0.16f, h * 0.12f), Size(w * 0.68f, h * 0.76f), CornerRadius(3f, 3f), style = Stroke(2.4f))
                drawLine(Color(0xFF454C56), Offset(w * 0.20f, h * 0.18f), Offset(w * 0.80f, h * 0.82f), strokeWidth = 2.6f)
                drawLine(Color(0xFF7B4D24), Offset(w * 0.80f, h * 0.18f), Offset(w * 0.20f, h * 0.82f), strokeWidth = 2.0f)
            }
            PropType.WALL -> {
                drawRoundRect(Color(0xFFB0BAC7), Offset(w * 0.08f, h * 0.18f), Size(w * 0.84f, h * 0.68f), CornerRadius(3f, 3f))
                drawRect(Color(0xFF5A6576), Offset(w * 0.06f, h * 0.14f), Size(w * 0.88f, h * 0.12f))
                drawRect(Color(0xFFF59E0B), Offset(w * 0.08f, h * 0.62f), Size(w * 0.84f, h * 0.12f))
            }
            PropType.FRAGILE_BRICK_WALL -> {
                drawRoundRect(Color(0xFFC85A32), Offset(w * 0.08f, h * 0.16f), Size(w * 0.84f, h * 0.70f), CornerRadius(3f, 3f))
                drawLine(Color(0xFFE2E8F0), Offset(w * 0.08f, h * 0.40f), Offset(w * 0.92f, h * 0.40f), strokeWidth = 1.6f)
                drawLine(Color(0xFFE2E8F0), Offset(w * 0.08f, h * 0.64f), Offset(w * 0.92f, h * 0.64f), strokeWidth = 1.6f)
                drawLine(Color(0xFF1F2937), Offset(w * 0.46f, h * 0.18f), Offset(w * 0.56f, h * 0.52f), strokeWidth = 2.2f)
                drawLine(Color(0xFF1F2937), Offset(w * 0.56f, h * 0.52f), Offset(w * 0.44f, h * 0.82f), strokeWidth = 2.2f)
            }
            PropType.RAMP -> {
                val ramp = Path().apply {
                    moveTo(w * 0.08f, h * 0.86f)
                    lineTo(w * 0.90f, h * 0.86f)
                    lineTo(w * 0.90f, h * 0.18f)
                    close()
                }
                drawPath(ramp, Color(0xFFF59E0B))
                drawLine(Color(0xFF22D3EE), Offset(w * 0.78f, h * 0.22f), Offset(w * 0.92f, h * 0.22f), strokeWidth = 3f)
            }
            PropType.DOUBLE_RAMP -> {
                val r1 = Path().apply {
                    moveTo(w * 0.06f, h * 0.86f); lineTo(w * 0.46f, h * 0.86f); lineTo(w * 0.46f, h * 0.24f); close()
                }
                val r2 = Path().apply {
                    moveTo(w * 0.94f, h * 0.86f); lineTo(w * 0.54f, h * 0.86f); lineTo(w * 0.54f, h * 0.24f); close()
                }
                drawPath(r1, Color(0xFFF97316))
                drawPath(r2, Color(0xFFFACC15))
            }
            PropType.LOOP_THE_LOOP -> {
                drawCircle(Color(0xFFF59E0B), radius = h * 0.36f, center = Offset(w * 0.50f, h * 0.48f), style = Stroke(3.6f))
                drawLine(Color(0xFF38BDF8), Offset(w * 0.06f, h * 0.84f), Offset(w * 0.94f, h * 0.84f), strokeWidth = 3f)
            }
            PropType.FIRE_RING -> {
                drawCircle(Color(0xFFFF5722), radius = h * 0.35f, center = Offset(w * 0.50f, h * 0.48f), style = Stroke(4.2f))
                drawCircle(Color(0xFFFFD54F), radius = h * 0.26f, center = Offset(w * 0.50f, h * 0.48f), style = Stroke(2.2f))
            }
            PropType.JUMP_PAD -> {
                drawRoundRect(Color(0xFF475569), Offset(w * 0.16f, h * 0.68f), Size(w * 0.68f, h * 0.20f), CornerRadius(3f, 3f))
                drawLine(Color(0xFFE2E8F0), Offset(w * 0.32f, h * 0.68f), Offset(w * 0.68f, h * 0.40f), strokeWidth = 2.4f)
                drawLine(Color(0xFFE2E8F0), Offset(w * 0.68f, h * 0.68f), Offset(w * 0.32f, h * 0.40f), strokeWidth = 2.4f)
                drawRoundRect(Color(0xFF22C55E), Offset(w * 0.14f, h * 0.22f), Size(w * 0.72f, h * 0.20f), CornerRadius(4f, 4f))
            }
            PropType.BOOST_PAD -> {
                drawRoundRect(Color(0xFF1E293B), Offset(w * 0.08f, h * 0.28f), Size(w * 0.84f, h * 0.48f), CornerRadius(4f, 4f))
                drawLine(Color(0xFF00E5FF), Offset(w * 0.22f, h * 0.66f), Offset(w * 0.44f, h * 0.36f), strokeWidth = 3f, cap = StrokeCap.Round)
                drawLine(Color(0xFF00E5FF), Offset(w * 0.46f, h * 0.66f), Offset(w * 0.68f, h * 0.36f), strokeWidth = 3f, cap = StrokeCap.Round)
                drawLine(Color(0xFF38BDF8), Offset(w * 0.70f, h * 0.66f), Offset(w * 0.86f, h * 0.44f), strokeWidth = 3f, cap = StrokeCap.Round)
            }
            PropType.CRUSHER -> {
                drawRect(Color(0xFF64748B), Offset(w * 0.12f, h * 0.12f), Size(w * 0.14f, h * 0.76f))
                drawRect(Color(0xFF64748B), Offset(w * 0.74f, h * 0.12f), Size(w * 0.14f, h * 0.76f))
                drawRect(Color(0xFFEAB308), Offset(w * 0.12f, h * 0.10f), Size(w * 0.76f, h * 0.14f))
                drawRoundRect(Color(0xFFEF4444), Offset(w * 0.28f, h * 0.42f), Size(w * 0.44f, h * 0.34f), CornerRadius(3f, 3f))
            }
            PropType.PENDULUM_HAMMER -> {
                drawLine(Color(0xFF94A3B8), Offset(w * 0.50f, h * 0.12f), Offset(w * 0.68f, h * 0.66f), strokeWidth = 3f)
                drawCircle(Color(0xFFF59E0B), radius = 3.2f, center = Offset(w * 0.50f, h * 0.14f))
                drawRoundRect(Color(0xFFEF4444), Offset(w * 0.46f, h * 0.58f), Size(w * 0.42f, h * 0.26f), CornerRadius(4f, 4f))
            }
            PropType.SPINNER_TRAP -> {
                drawCircle(Color(0xFF64748B), radius = h * 0.18f, center = Offset(w * 0.50f, h * 0.50f))
                drawLine(Color(0xFFF97316), Offset(w * 0.10f, h * 0.50f), Offset(w * 0.90f, h * 0.50f), strokeWidth = 4.2f, cap = StrokeCap.Round)
                drawLine(Color(0xFFFACC15), Offset(w * 0.50f, h * 0.14f), Offset(w * 0.50f, h * 0.86f), strokeWidth = 3.6f, cap = StrokeCap.Round)
            }
            PropType.FINISH_ARCH -> {
                drawRect(Color(0xFFE2E8F0), Offset(w * 0.12f, h * 0.20f), Size(w * 0.14f, h * 0.68f))
                drawRect(Color(0xFFE2E8F0), Offset(w * 0.74f, h * 0.20f), Size(w * 0.14f, h * 0.68f))
                drawRect(Color(0xFF0F172A), Offset(w * 0.10f, h * 0.14f), Size(w * 0.80f, h * 0.22f))
                drawRect(Color(0xFFF8FAFC), Offset(w * 0.22f, h * 0.16f), Size(w * 0.16f, h * 0.18f))
                drawRect(Color(0xFFF8FAFC), Offset(w * 0.58f, h * 0.16f), Size(w * 0.16f, h * 0.18f))
            }
            PropType.BOWLING_PIN -> {
                drawOval(Color(0xFFF8FAFC), Offset(w * 0.30f, h * 0.36f), Size(w * 0.40f, h * 0.54f))
                drawCircle(Color(0xFFF8FAFC), radius = h * 0.15f, center = Offset(w * 0.50f, h * 0.24f))
                drawRect(Color(0xFFEF4444), Offset(w * 0.36f, h * 0.35f), Size(w * 0.28f, h * 0.10f))
            }
            PropType.ROAD -> {
                drawRoundRect(Color(0xFF334155), Offset(w * 0.06f, h * 0.22f), Size(w * 0.88f, h * 0.56f), CornerRadius(3f, 3f))
                drawLine(Color(0xFFF8FAFC), Offset(w * 0.16f, h * 0.50f), Offset(w * 0.42f, h * 0.50f), strokeWidth = 2.4f)
                drawLine(Color(0xFFF8FAFC), Offset(w * 0.58f, h * 0.50f), Offset(w * 0.84f, h * 0.50f), strokeWidth = 2.4f)
            }
            PropType.BARRIER -> {
                drawRoundRect(Color(0xFFEF4444), Offset(w * 0.08f, h * 0.32f), Size(w * 0.42f, h * 0.48f), CornerRadius(3f, 3f))
                drawRoundRect(Color(0xFFF8FAFC), Offset(w * 0.50f, h * 0.32f), Size(w * 0.42f, h * 0.48f), CornerRadius(3f, 3f))
            }
            PropType.REINFORCED_FENCE -> {
                drawRect(Color(0xFF64748B), Offset(w * 0.12f, h * 0.14f), Size(w * 0.12f, h * 0.76f))
                drawRect(Color(0xFF64748B), Offset(w * 0.76f, h * 0.14f), Size(w * 0.12f, h * 0.76f))
                drawLine(Color(0xFF94A3B8), Offset(w * 0.16f, h * 0.30f), Offset(w * 0.84f, h * 0.30f), strokeWidth = 2f)
                drawLine(Color(0xFF94A3B8), Offset(w * 0.16f, h * 0.54f), Offset(w * 0.84f, h * 0.54f), strokeWidth = 2f)
                drawLine(Color(0xFF94A3B8), Offset(w * 0.16f, h * 0.76f), Offset(w * 0.84f, h * 0.76f), strokeWidth = 2f)
            }
            PropType.TIRE_STACK -> {
                drawRoundRect(Color(0xFF1E293B), Offset(w * 0.18f, h * 0.62f), Size(w * 0.64f, h * 0.24f), CornerRadius(5f, 5f))
                drawRoundRect(Color(0xFF334155), Offset(w * 0.20f, h * 0.38f), Size(w * 0.60f, h * 0.24f), CornerRadius(5f, 5f))
                drawRoundRect(Color(0xFF1E293B), Offset(w * 0.22f, h * 0.14f), Size(w * 0.56f, h * 0.24f), CornerRadius(5f, 5f))
            }
            PropType.TOWER -> {
                drawRoundRect(Color(0xFF8A95A5), Offset(w * 0.24f, h * 0.12f), Size(w * 0.52f, h * 0.78f), CornerRadius(3f, 3f))
                drawRect(Color(0xFF3E4A5C), Offset(w * 0.18f, h * 0.08f), Size(w * 0.64f, h * 0.14f))
                drawRect(Color(0xFF38BDF8), Offset(w * 0.24f, h * 0.44f), Size(w * 0.52f, h * 0.14f))
            }
            PropType.BRIDGE -> {
                drawRoundRect(Color(0xFF6B778C), Offset(w * 0.06f, h * 0.46f), Size(w * 0.88f, h * 0.24f), CornerRadius(3f, 3f))
                drawRect(Color(0xFFF59E0B), Offset(w * 0.06f, h * 0.34f), Size(w * 0.88f, h * 0.10f))
            }
            PropType.TARGET -> {
                drawLine(Color(0xFF7B4D24), Offset(w * 0.50f, h * 0.66f), Offset(w * 0.50f, h * 0.92f), strokeWidth = 3.2f)
                drawCircle(Color(0xFFF8FAFC), radius = h * 0.34f, center = Offset(w * 0.50f, h * 0.44f))
                drawCircle(Color(0xFFEF4444), radius = h * 0.23f, center = Offset(w * 0.50f, h * 0.44f))
                drawCircle(Color(0xFFFACC15), radius = h * 0.11f, center = Offset(w * 0.50f, h * 0.44f))
            }
            PropType.BARREL, PropType.GAS_BARREL -> {
                val bodyCol = if (type == PropType.GAS_BARREL) Color(0xFFF97316) else Color(0xFFE52420)
                drawRoundRect(bodyCol, Offset(w * 0.24f, h * 0.10f), Size(w * 0.52f, h * 0.80f), CornerRadius(6f, 6f))
                drawRect(Color(0xFFFACC15), Offset(w * 0.23f, h * 0.42f), Size(w * 0.54f, h * 0.16f))
                drawRect(Color(0xFF333944), Offset(w * 0.23f, h * 0.10f), Size(w * 0.54f, h * 0.09f))
                drawRect(Color(0xFF333944), Offset(w * 0.23f, h * 0.81f), Size(w * 0.54f, h * 0.09f))
            }
            PropType.WATER_BARREL -> {
                drawRoundRect(Color(0xFF0284C7), Offset(w * 0.24f, h * 0.10f), Size(w * 0.52f, h * 0.80f), CornerRadius(6f, 6f))
                drawRect(Color(0xFF38BDF8), Offset(w * 0.23f, h * 0.42f), Size(w * 0.54f, h * 0.16f))
                drawRect(Color(0xFF0C4A6E), Offset(w * 0.23f, h * 0.10f), Size(w * 0.54f, h * 0.09f))
                drawRect(Color(0xFF0C4A6E), Offset(w * 0.23f, h * 0.81f), Size(w * 0.54f, h * 0.09f))
            }
            PropType.GAZYAVA_LEMON, PropType.GAZYAVA_CLASSIC, PropType.GAZYAVA_CHICKEN, PropType.GAZYAVA_TARHUN -> {
                val drinkCol = when (type) {
                    PropType.GAZYAVA_LEMON -> Color(0xFFFACC15)
                    PropType.GAZYAVA_CLASSIC -> Color(0xFFEF4444)
                    PropType.GAZYAVA_CHICKEN -> Color(0xFFF97316)
                    else -> Color(0xFF10B981)
                }
                drawRoundRect(drinkCol, Offset(w * 0.30f, h * 0.26f), Size(w * 0.40f, h * 0.64f), CornerRadius(5f, 5f))
                drawRect(Color(0xFFF8FAFC), Offset(w * 0.38f, h * 0.10f), Size(w * 0.24f, h * 0.16f))
                drawRect(Color(0xFF1E293B), Offset(w * 0.30f, h * 0.50f), Size(w * 0.40f, h * 0.16f))
            }
            PropType.HRUMKI_CHICKEN, PropType.HRUMKI_CHEESE, PropType.HRUMKI_SPICY, PropType.HRUMKI_GREENS -> {
                val bagCol = when (type) {
                    PropType.HRUMKI_CHICKEN -> Color(0xFFF59E0B)
                    PropType.HRUMKI_CHEESE -> Color(0xFFEAB308)
                    PropType.HRUMKI_SPICY -> Color(0xFFDC2626)
                    else -> Color(0xFF22C55E)
                }
                drawRoundRect(bagCol, Offset(w * 0.20f, h * 0.12f), Size(w * 0.60f, h * 0.76f), CornerRadius(5f, 5f))
                drawCircle(Color(0xFFFEF08A), radius = h * 0.16f, center = Offset(w * 0.50f, h * 0.52f))
                drawLine(Color(0xFFFFFFFF), Offset(w * 0.22f, h * 0.20f), Offset(w * 0.78f, h * 0.20f), strokeWidth = 2f)
            }
            PropType.CAR -> {
                drawRoundRect(Color(0xFF22C55E), Offset(w * 0.12f, h * 0.36f), Size(w * 0.76f, h * 0.42f), CornerRadius(5f, 5f))
            }
            PropType.HOUSE_SMALL -> {
                drawRect(Color(0xFFE7D8B9), Offset(w * 0.18f, h * 0.44f), Size(w * 0.64f, h * 0.44f))
                val roof = Path().apply {
                    moveTo(w * 0.10f, h * 0.46f); lineTo(w * 0.50f, h * 0.12f); lineTo(w * 0.90f, h * 0.46f); close()
                }
                drawPath(roof, Color(0xFFD6453A))
                drawRect(Color(0xFF7B4D24), Offset(w * 0.42f, h * 0.60f), Size(w * 0.16f, h * 0.28f))
            }
            PropType.GARAGE_HANGAR -> {
                drawRoundRect(Color(0xFF64748B), Offset(w * 0.10f, h * 0.24f), Size(w * 0.80f, h * 0.64f), CornerRadius(6f, 6f))
                drawRect(Color(0xFF1E293B), Offset(w * 0.24f, h * 0.42f), Size(w * 0.52f, h * 0.46f))
                drawLine(Color(0xFFF59E0B), Offset(w * 0.24f, h * 0.56f), Offset(w * 0.76f, h * 0.56f), strokeWidth = 2.0f)
                drawLine(Color(0xFFF59E0B), Offset(w * 0.24f, h * 0.70f), Offset(w * 0.76f, h * 0.70f), strokeWidth = 2.0f)
            }
            PropType.WATCHTOWER -> {
                drawRect(Color(0xFF788598), Offset(w * 0.28f, h * 0.18f), Size(w * 0.44f, h * 0.72f))
                drawRect(Color(0xFF334155), Offset(w * 0.20f, h * 0.10f), Size(w * 0.60f, h * 0.16f))
                drawRect(Color(0xFF38BDF8), Offset(w * 0.36f, h * 0.28f), Size(w * 0.28f, h * 0.14f))
                drawRect(Color(0xFF7B4D24), Offset(w * 0.42f, h * 0.68f), Size(w * 0.16f, h * 0.22f))
            }
            PropType.WALL_ARCH_GATE -> {
                drawRect(Color(0xFF8A95A5), Offset(w * 0.08f, h * 0.18f), Size(w * 0.24f, h * 0.70f))
                drawRect(Color(0xFF8A95A5), Offset(w * 0.68f, h * 0.18f), Size(w * 0.24f, h * 0.70f))
                drawRect(Color(0xFF64748B), Offset(w * 0.08f, h * 0.14f), Size(w * 0.84f, h * 0.20f))
                drawRect(Color(0xFFF59E0B), Offset(w * 0.32f, h * 0.36f), Size(w * 0.36f, h * 0.52f))
            }
            PropType.ROAD_TURN -> {
                drawRoundRect(Color(0xFF334155), Offset(w * 0.14f, h * 0.16f), Size(w * 0.72f, h * 0.72f), CornerRadius(6f, 6f))
                drawArc(Color(0xFFF8FAFC), startAngle = 180f, sweepAngle = 90f, useCenter = false, topLeft = Offset(w * 0.34f, h * 0.34f), size = Size(w * 0.50f, h * 0.50f), style = Stroke(2.4f))
            }
            PropType.ROAD_ROUNDABOUT -> {
                drawCircle(Color(0xFF334155), radius = h * 0.42f, center = Offset(w * 0.50f, h * 0.50f))
                drawCircle(Color(0xFF22C55E), radius = h * 0.18f, center = Offset(w * 0.50f, h * 0.50f))
                drawCircle(Color(0xFFF8FAFC), radius = h * 0.30f, center = Offset(w * 0.50f, h * 0.50f), style = Stroke(1.6f))
            }
            PropType.ROAD_RAMP -> {
                val p = Path().apply {
                    moveTo(w * 0.08f, h * 0.84f); lineTo(w * 0.92f, h * 0.84f); lineTo(w * 0.92f, h * 0.26f); close()
                }
                drawPath(p, Color(0xFF475569))
                drawLine(Color(0xFFFACC15), Offset(w * 0.16f, h * 0.76f), Offset(w * 0.86f, h * 0.34f), strokeWidth = 2.4f)
            }
            PropType.WALL_BRICK -> {
                drawRoundRect(Color(0xFFB4533C), Offset(w * 0.08f, h * 0.18f), Size(w * 0.84f, h * 0.68f), CornerRadius(3f, 3f))
                drawLine(Color(0xFFE2E8F0), Offset(w * 0.08f, h * 0.42f), Offset(w * 0.92f, h * 0.42f), strokeWidth = 1.6f)
                drawLine(Color(0xFFE2E8F0), Offset(w * 0.08f, h * 0.64f), Offset(w * 0.92f, h * 0.64f), strokeWidth = 1.6f)
            }
            PropType.WALL_WOOD, PropType.WOODEN_FENCE -> {
                drawRoundRect(Color(0xFFA97142), Offset(w * 0.10f, h * 0.20f), Size(w * 0.80f, h * 0.66f), CornerRadius(3f, 3f))
                drawLine(Color(0xFF6B4226), Offset(w * 0.32f, h * 0.20f), Offset(w * 0.32f, h * 0.86f), strokeWidth = 2.0f)
                drawLine(Color(0xFF6B4226), Offset(w * 0.66f, h * 0.20f), Offset(w * 0.66f, h * 0.86f), strokeWidth = 2.0f)
            }
            PropType.BOOM_BARRIER -> {
                drawRect(Color(0xFFF59E0B), Offset(w * 0.10f, h * 0.36f), Size(w * 0.20f, h * 0.52f))
                drawLine(Color(0xFFEF4444), Offset(w * 0.26f, h * 0.46f), Offset(w * 0.92f, h * 0.32f), strokeWidth = 4.0f)
            }
            PropType.METAL_GATE -> {
                drawRect(Color(0xFF475569), Offset(w * 0.12f, h * 0.14f), Size(w * 0.76f, h * 0.74f), style = Stroke(2.4f))
                drawLine(Color(0xFF94A3B8), Offset(w * 0.34f, h * 0.14f), Offset(w * 0.34f, h * 0.88f), strokeWidth = 2.0f)
                drawLine(Color(0xFF94A3B8), Offset(w * 0.66f, h * 0.14f), Offset(w * 0.66f, h * 0.88f), strokeWidth = 2.0f)
            }
            PropType.OIL_DRUM, PropType.TOXIC_DRUM, PropType.FUEL_CANISTER, PropType.WOODEN_BARREL -> {
                val c = when (type) {
                    PropType.TOXIC_DRUM -> Color(0xFF22C55E)
                    PropType.OIL_DRUM -> Color(0xFF334155)
                    PropType.FUEL_CANISTER -> Color(0xFFDC2626)
                    else -> Color(0xFFA97142)
                }
                drawRoundRect(c, Offset(w * 0.24f, h * 0.14f), Size(w * 0.52f, h * 0.74f), CornerRadius(5f, 5f))
                drawRect(Color(0xFFFACC15), Offset(w * 0.24f, h * 0.44f), Size(w * 0.52f, h * 0.14f))
            }
            PropType.STREET_LAMP -> {
                drawLine(Color(0xFF94A3B8), Offset(w * 0.44f, h * 0.88f), Offset(w * 0.44f, h * 0.16f), strokeWidth = 3.0f)
                drawLine(Color(0xFF94A3B8), Offset(w * 0.44f, h * 0.18f), Offset(w * 0.74f, h * 0.18f), strokeWidth = 2.6f)
                drawCircle(Color(0xFFFEF08A), radius = h * 0.12f, center = Offset(w * 0.74f, h * 0.24f))
            }
            PropType.BILLBOARD_SIGN -> {
                drawLine(Color(0xFF64748B), Offset(w * 0.50f, h * 0.88f), Offset(w * 0.50f, h * 0.56f), strokeWidth = 3.2f)
                drawRoundRect(Color(0xFF0EA5E9), Offset(w * 0.12f, h * 0.14f), Size(w * 0.76f, h * 0.46f), CornerRadius(4f, 4f))
                drawRect(Color(0xFFFACC15), Offset(w * 0.22f, h * 0.26f), Size(w * 0.56f, h * 0.18f))
            }
            PropType.RADAR_DISH, PropType.RADIO_MAST -> {
                drawLine(Color(0xFFCBD5E1), Offset(w * 0.50f, h * 0.88f), Offset(w * 0.50f, h * 0.20f), strokeWidth = 3.0f)
                drawCircle(Color(0xFF38BDF8), radius = h * 0.22f, center = Offset(w * 0.50f, h * 0.28f), style = Stroke(2.4f))
                drawCircle(Color(0xFFEF4444), radius = h * 0.08f, center = Offset(w * 0.50f, h * 0.20f))
            }
            PropType.SOLAR_PANEL -> {
                drawLine(Color(0xFF64748B), Offset(w * 0.50f, h * 0.86f), Offset(w * 0.50f, h * 0.52f), strokeWidth = 3.0f)
                drawRoundRect(Color(0xFF1D4ED8), Offset(w * 0.14f, h * 0.20f), Size(w * 0.72f, h * 0.36f), CornerRadius(3f, 3f))
                drawLine(Color(0xFF93C5FD), Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.56f), strokeWidth = 1.6f)
            }
            else -> {
                drawRoundRect(Color(0xFF64748B), Offset(w * 0.16f, h * 0.22f), Size(w * 0.68f, h * 0.62f), CornerRadius(4f, 4f))
                drawRect(Color(0xFFF59E0B), Offset(w * 0.22f, h * 0.44f), Size(w * 0.56f, h * 0.16f))
            }
        }
    }
}

/**
 * Miniature icon for living NPC Хряк-Бургер in the Sandbox NPC tab.
 */
@Composable
fun PigBurgerPreviewIcon(
    modifier: Modifier = Modifier.size(width = 36.dp, height = 28.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Bottom bun
        drawRoundRect(Color(0xFFD99B38), Offset(w * 0.16f, h * 0.68f), Size(w * 0.68f, h * 0.20f), CornerRadius(6f, 6f))
        // Lettuce + Pink piggy body
        drawRect(Color(0xFF22C55E), Offset(w * 0.14f, h * 0.58f), Size(w * 0.72f, h * 0.10f))
        drawOval(Color(0xFFF48FB1), Offset(w * 0.22f, h * 0.36f), Size(w * 0.56f, h * 0.34f))
        // Top burger bun with sesame seeds
        drawArc(
            color = Color(0xFFE6A93C),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.16f, h * 0.10f),
            size = Size(w * 0.68f, h * 0.46f)
        )
        // Snout + eyes
        drawOval(Color(0xFFEC407A), Offset(w * 0.40f, h * 0.48f), Size(w * 0.20f, h * 0.14f))
        drawCircle(Color(0xFF111827), radius = 2.0f, center = Offset(w * 0.36f, h * 0.46f))
        drawCircle(Color(0xFF111827), radius = 2.0f, center = Offset(w * 0.64f, h * 0.46f))
    }
}

/**
 * Miniature stylized combat chicken avatar previewing each of the 6 ChickenSkin palettes.
 */
@Composable
fun SkinPreviewAvatar(
    skin: ChickenSkin,
    modifier: Modifier = Modifier.size(36.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val feather = Color(skin.featherR, skin.featherG, skin.featherB)
        val vest = Color(skin.vestR, skin.vestG, skin.vestB)
        val visor = Color(skin.visorR, skin.visorG, skin.visorB)
        val comb = Color(skin.combR, skin.combG, skin.combB)
        val helmet = Color(skin.helmetR, skin.helmetG, skin.helmetB)

        drawOval(feather, Offset(w * 0.22f, h * 0.56f), Size(w * 0.56f, h * 0.40f))
        drawRoundRect(vest, Offset(w * 0.24f, h * 0.66f), Size(w * 0.52f, h * 0.26f), CornerRadius(6f, 6f))

        drawCircle(comb, radius = w * 0.11f, center = Offset(w * 0.44f, h * 0.15f))
        drawCircle(comb, radius = w * 0.10f, center = Offset(w * 0.56f, h * 0.18f))

        drawCircle(feather, radius = w * 0.26f, center = Offset(w * 0.50f, h * 0.42f))
        drawArc(
            color = helmet,
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = true,
            topLeft = Offset(w * 0.22f, h * 0.15f),
            size = Size(w * 0.56f, h * 0.36f)
        )
        drawRoundRect(Color(0xFF111827), Offset(w * 0.26f, h * 0.33f), Size(w * 0.48f, h * 0.14f), CornerRadius(4f, 4f))
        drawRoundRect(visor, Offset(w * 0.30f, h * 0.35f), Size(w * 0.40f, h * 0.10f), CornerRadius(3f, 3f))
        val beak = Path().apply {
            moveTo(w * 0.42f, h * 0.48f)
            lineTo(w * 0.58f, h * 0.48f)
            lineTo(w * 0.50f, h * 0.60f)
            close()
        }
        drawPath(beak, Color(0xFFFF9800))
    }
}

/**
 * Miniature scenic landmark thumbnail for each of the 6 distinct playable 3D maps.
 */
@Composable
fun MapPreviewBadge(
    map: GameMap,
    modifier: Modifier = Modifier.size(width = 44.dp, height = 28.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val zenith = Color(map.skyZenithR, map.skyZenithG, map.skyZenithB)
        val horizon = Color(map.skyHorizonR, map.skyHorizonG, map.skyHorizonB)

        // Sky backdrop
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(zenith, horizon)),
            topLeft = Offset.Zero,
            size = Size(w, h * 0.68f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        when (map) {
            GameMap.MAIN_ARENA -> {
                // Unified arena: lush field + roundabout tarmac + house + hangar + watchtower
                drawRoundRect(Color(0xFF3E9B42), Offset(0f, h * 0.56f), Size(w, h * 0.44f), CornerRadius(6f, 6f))
                drawRect(Color(0xFF334155), Offset(w * 0.06f, h * 0.66f), Size(w * 0.88f, h * 0.24f))
                drawCircle(Color(0xFFF8FAFC), radius = h * 0.12f, center = Offset(w * 0.50f, h * 0.78f), style = Stroke(1.6f))
                // Small house on left
                drawRect(Color(0xFFF2E2C4), Offset(w * 0.14f, h * 0.36f), Size(w * 0.22f, h * 0.26f))
                val roof = Path().apply {
                    moveTo(w * 0.11f, h * 0.38f); lineTo(w * 0.25f, h * 0.20f); lineTo(w * 0.39f, h * 0.38f); close()
                }
                drawPath(roof, Color(0xFFD6453A))
                // Watchtower center-right
                drawRect(Color(0xFF64748B), Offset(w * 0.46f, h * 0.24f), Size(w * 0.14f, h * 0.38f))
                drawRect(Color(0xFF38BDF8), Offset(w * 0.48f, h * 0.28f), Size(w * 0.10f, h * 0.08f))
                // Hangar on right
                drawRoundRect(Color(0xFF475569), Offset(w * 0.66f, h * 0.34f), Size(w * 0.24f, h * 0.28f), CornerRadius(4f, 4f))
                drawRect(Color(0xFFF59E0B), Offset(w * 0.71f, h * 0.44f), Size(w * 0.14f, h * 0.18f))
            }

            GameMap.TURBO_SPEEDWAY -> {
                // High-speed sunset race circuit: dark asphalt + bright stunt loop + ramps
                drawRoundRect(Color(0xFF8C6239), Offset(0f, h * 0.56f), Size(w, h * 0.44f), CornerRadius(6f, 6f))
                drawRoundRect(Color(0xFF1E293B), Offset(w * 0.06f, h * 0.62f), Size(w * 0.88f, h * 0.28f), CornerRadius(10f, 10f))
                drawLine(Color(0xFFFACC15), Offset(w * 0.14f, h * 0.76f), Offset(w * 0.86f, h * 0.76f), strokeWidth = 2f)
                // Stunt loop ring
                drawCircle(Color(0xFFF97316), radius = h * 0.22f, center = Offset(w * 0.50f, h * 0.42f), style = Stroke(3f))
                // Left & right ramps
                val rampL = Path().apply {
                    moveTo(w * 0.12f, h * 0.62f); lineTo(w * 0.30f, h * 0.40f); lineTo(w * 0.30f, h * 0.62f); close()
                }
                drawPath(rampL, Color(0xFF38BDF8))
                val rampR = Path().apply {
                    moveTo(w * 0.88f, h * 0.62f); lineTo(w * 0.70f, h * 0.40f); lineTo(w * 0.70f, h * 0.62f); close()
                }
                drawPath(rampR, Color(0xFF38BDF8))
            }

            GameMap.TACTICAL_COMPOUND -> {
                // Dense military fort: concrete courtyard + fortified walls + watchtowers + red barrels
                drawRoundRect(Color(0xFF4B5563), Offset(0f, h * 0.56f), Size(w, h * 0.44f), CornerRadius(6f, 6f))
                // Perimeter wall
                drawRect(Color(0xFF6B7280), Offset(w * 0.08f, h * 0.44f), Size(w * 0.84f, h * 0.16f))
                // Left & right guard towers
                drawRect(Color(0xFF374151), Offset(w * 0.10f, h * 0.22f), Size(w * 0.16f, h * 0.38f))
                drawRect(Color(0xFF374151), Offset(w * 0.74f, h * 0.22f), Size(w * 0.16f, h * 0.38f))
                // Central command bunker
                drawRoundRect(Color(0xFF52525B), Offset(w * 0.36f, h * 0.34f), Size(w * 0.28f, h * 0.26f), CornerRadius(3f, 3f))
                // Cover crates & explosive barrels
                drawRect(Color(0xFFD97706), Offset(w * 0.24f, h * 0.68f), Size(w * 0.14f, h * 0.14f))
                drawRect(Color(0xFFEF4444), Offset(w * 0.46f, h * 0.70f), Size(w * 0.09f, h * 0.14f))
                drawRect(Color(0xFFD97706), Offset(w * 0.62f, h * 0.68f), Size(w * 0.14f, h * 0.14f))
            }
        }
    }
}

/**
 * Compact quick bar showing the clickable 3-Map picker, Chicken Skin picker, and Weapon Skin toggle.
 */
@Composable
fun MapAndSkinQuickBar(
    selectedMap: GameMap,
    selectedSkin: ChickenSkin,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
    selectedWeaponSkin: WeaponSkin = WeaponSkin.CLASSIC,
    onToggleWeaponSkin: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onOpenPicker,
            shape = RoundedCornerShape(14.dp),
            color = GlassPanelFill,
            border = BorderStroke(1.dp, ArenaSkyCyan.copy(alpha = 0.55f)),
            modifier = Modifier
                .weight(1.05f)
                .height(48.dp)
                .testTag("open_map_picker_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MapPreviewBadge(map = selectedMap, modifier = Modifier.size(width = 34.dp, height = 22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "КАРТА (${GameMap.entries.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = ArenaSkyCyan,
                        fontSize = 9.sp
                    )
                    Text(
                        text = selectedMap.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = FeatherWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            onClick = onOpenPicker,
            shape = RoundedCornerShape(14.dp),
            color = GlassPanelFill,
            border = BorderStroke(1.dp, BeakAmber.copy(alpha = 0.45f)),
            modifier = Modifier
                .weight(1.0f)
                .height(48.dp)
                .testTag("open_skin_picker_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SkinPreviewAvatar(skin = selectedSkin, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("СКИН (6)", style = MaterialTheme.typography.labelSmall, color = BeakAmber, fontSize = 9.sp)
                    Text(
                        text = selectedSkin.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = FeatherWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (onToggleWeaponSkin != null) {
            val isTactical = selectedWeaponSkin == WeaponSkin.TACTICAL
            val accent = if (isTactical) NitroEmerald else BeakAmber
            Surface(
                onClick = onToggleWeaponSkin,
                shape = RoundedCornerShape(14.dp),
                color = GlassPanelFill,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                modifier = Modifier
                    .weight(0.90f)
                    .height(48.dp)
                    .testTag("lobby_weapon_skin_toggle")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("ОРУЖИЕ", style = MaterialTheme.typography.labelSmall, color = accent, fontSize = 9.sp)
                    Text(
                        text = selectedWeaponSkin.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = FeatherWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Landscape Modal Overlay for selecting one of 3 3D Maps and one of 6 Combat Chicken Skins.
 */
@Composable
fun MapAndSkinPickerDialog(
    selectedMap: GameMap,
    selectedSkin: ChickenSkin,
    onSelectMap: (GameMap) -> Unit,
    onSelectSkin: (ChickenSkin) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalNavy.copy(alpha = 0.84f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BunkerSlate,
            border = BorderStroke(1.6.dp, BeakAmber.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 660.dp)
                .testTag("map_skin_picker_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalNavy.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = BeakAmber, modifier = Modifier.size(20.dp))
                        Text(
                            text = "КАРТЫ И СКИНЫ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = FeatherWhite
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SteelSurface)
                            .testTag("map_skin_picker_close")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = FeatherWhite)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 3 Interactive 3D Maps
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "ВЫБОР КАРТЫ (${GameMap.entries.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = ArenaSkyCyan,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameMap.entries.forEach { map ->
                            val isSelected = map == selectedMap
                            Surface(
                                onClick = { onSelectMap(map) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) ArenaSkyCyan.copy(alpha = 0.22f) else TacticalNavy.copy(alpha = 0.65f),
                                border = BorderStroke(
                                    if (isSelected) 1.8.dp else 1.dp,
                                    if (isSelected) ArenaSkyCyan else SteelSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("select_map_${map.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MapPreviewBadge(map = map, modifier = Modifier.size(width = 52.dp, height = 34.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = map.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = FeatherWhite
                                        )
                                        Text(
                                            text = map.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedSteel,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Выбранная карта",
                                            tint = NitroEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6 Chicken Skins (2x3 grid)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Palette, contentDescription = null, tint = BeakAmber, modifier = Modifier.size(16.dp))
                            Text("СКИН БОЕВОЙ КУРИЦЫ", style = MaterialTheme.typography.labelLarge, color = BeakAmber, fontWeight = FontWeight.ExtraBold)
                        }
                        val skinRows = ChickenSkin.entries.chunked(2)
                        skinRows.forEach { rowSkins ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowSkins.forEach { skin ->
                                    val isSelected = skin == selectedSkin
                                    Surface(
                                        onClick = { onSelectSkin(skin) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) BeakAmber.copy(alpha = 0.24f) else TacticalNavy.copy(alpha = 0.65f),
                                        border = BorderStroke(if (isSelected) 1.8.dp else 1.dp, if (isSelected) BeakAmber else SteelSurface),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("select_skin_${skin.name.lowercase()}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            SkinPreviewAvatar(skin = skin, modifier = Modifier.size(38.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = skin.label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = FeatherWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isSelected) "Надет" else "Выбрать",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) NitroEmerald else MutedSteel
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

