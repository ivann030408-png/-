package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.DamageKind
import com.example.engine.GameEngine
import com.example.engine.ProjectedDamageText
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.ExplosiveOrange
import com.example.ui.theme.FeatherWhite

/**
 * Compact, background-free world-projected floating damage numbers rendered over 3D targets.
 * Uses small crisp outlined text with subtle shadow so crosshair & viewport stay completely clear.
 */
@Composable
fun FloatingDamageOverlay(
    popups: List<ProjectedDamageText>,
    modifier: Modifier = Modifier
) {
    if (popups.isEmpty()) return

    val density = LocalDensity.current
    val visiblePopups = if (popups.size > GameEngine.MAX_DAMAGE_POPUPS) {
        popups.takeLast(GameEngine.MAX_DAMAGE_POPUPS)
    } else {
        popups
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("floating_damage_overlay")
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val strokeWidthPx = with(density) { 1.6.dp.toPx() }

        for (popup in visiblePopups) {
            key(popup.id) {
                val t = popup.progress.coerceIn(0f, 1f)
                // Gentle compact pop-in (0..0.14) and quick clean fade-out (0.52..1.0)
                val popScale = when {
                    t < 0.14f -> 0.84f + (t / 0.14f) * 0.22f
                    t < 0.30f -> 1.06f - ((t - 0.14f) / 0.16f) * 0.08f
                    else -> 0.98f - (t - 0.30f) * 0.08f
                }
                val kindScale = when (popup.kind) {
                    DamageKind.CRITICAL -> 1.08f
                    DamageKind.EXPLOSIVE, DamageKind.VEHICLE_RAM -> 1.04f
                    DamageKind.NORMAL -> 1.0f
                    DamageKind.PROP -> 0.92f
                }
                val distScale = popup.scale.coerceIn(0.75f, 1.08f)
                val finalScale = (popScale * kindScale * distScale).coerceIn(0.72f, 1.15f)
                val alpha = when {
                    t < 0.08f -> (t / 0.08f)
                    t > 0.52f -> (1f - (t - 0.52f) / 0.48f).coerceIn(0f, 1f)
                    else -> 1f
                }

                val extraRisePx = with(density) { (t * 14f).dp.toPx() }
                val targetX = popup.screenX * widthPx
                val targetY = popup.screenY * heightPx - extraRisePx

                val textColor = when (popup.kind) {
                    DamageKind.CRITICAL -> BeakAmber
                    DamageKind.EXPLOSIVE -> ExplosiveOrange
                    DamageKind.VEHICLE_RAM -> ArenaSkyCyan
                    DamageKind.PROP -> Color(0xFFFDE68A)
                    DamageKind.NORMAL -> FeatherWhite
                }
                val fontSize = when (popup.kind) {
                    DamageKind.CRITICAL -> 12.sp
                    DamageKind.PROP -> 10.sp
                    else -> 11.sp
                }
                val labelText = when (popup.kind) {
                    DamageKind.CRITICAL -> "-${popup.amount}!"
                    else -> "-${popup.amount}"
                }

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = targetX - (size.width * 0.5f)
                            translationY = targetY - (size.height * 0.5f)
                            scaleX = finalScale
                            scaleY = finalScale
                            this.alpha = alpha
                        }
                ) {
                    // Thin dark outline pass for crisp contrast over bright sky & dark walls (no background box)
                    Text(
                        text = labelText,
                        maxLines = 1,
                        style = TextStyle(
                            color = Color.Black.copy(alpha = 0.82f),
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round)
                        )
                    )
                    // Clean compact foreground damage number with subtle shadow
                    Text(
                        text = labelText,
                        maxLines = 1,
                        style = TextStyle(
                            color = textColor,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.65f),
                                offset = Offset(0.8f, 1.0f),
                                blurRadius = 2.0f
                            )
                        )
                    )
                }
            }
        }
    }
}
