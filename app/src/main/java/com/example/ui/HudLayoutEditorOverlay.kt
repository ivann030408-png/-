package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomHudLayout
import com.example.data.GameSettings
import com.example.data.HudControlId
import com.example.data.HudControlSlot
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.BunkerSlate
import com.example.ui.theme.CombCrimson
import com.example.ui.theme.ExplosiveOrange
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.SteelSurface
import com.example.ui.theme.TacticalNavy
import kotlin.math.roundToInt

/**
 * Interactive Visual HUD Layout Editor ("Кастомная настройка кнопок").
 * Players freely drag any control button anywhere on the landscape screen,
 * tap to select it, tune individual Size and Opacity sliders, reset to defaults, or save.
 */
@Composable
fun HudLayoutEditorOverlay(
    settings: GameSettings,
    onSaveLayout: (CustomHudLayout) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    var draftLayout by remember(settings.customHudLayout, settings.leftHandedLayout) {
        mutableStateOf(
            if (settings.customHudLayout.customized) settings.customHudLayout
            else CustomHudLayout(
                customized = false,
                slots = CustomHudLayout.defaultSlots(settings.leftHandedLayout)
            )
        )
    }
    var selectedId by remember { mutableStateOf(HudControlId.FIRE) }

    val selectedSlot = draftLayout.slotFor(selectedId, settings.leftHandedLayout)
    val toolbarAtBottom = selectedSlot.yFrac < 0.34f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalNavy.copy(alpha = 0.90f))
            .testTag("hud_layout_editor_root")
    ) {
        // Subtle alignment grid + center crosshair reference
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = 12
            val rows = 6
            val gridColor = Color.White.copy(alpha = 0.08f)
            for (c in 1 until cols) {
                val x = size.width * c / cols
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            for (r in 1 until rows) {
                val y = size.height * r / rows
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            // Center crosshair marker
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            drawLine(ArenaSkyCyan.copy(alpha = 0.35f), Offset(cx - 18f, cy), Offset(cx + 18f, cy), strokeWidth = 2f)
            drawLine(ArenaSkyCyan.copy(alpha = 0.35f), Offset(cx, cy - 18f), Offset(cx, cy + 18f), strokeWidth = 2f)
        }

        // Safe-area canvas matching the real in-game HUD coordinate space!
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            val density = LocalDensity.current
            val areaWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
            val areaHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
            val globalScale = settings.buttonScale.coerceIn(0.80f, 1.35f)

            // Render every draggable HUD control in its exact normalized position
            HudControlId.entries.forEach { controlId ->
                val slot = draftLayout.slotFor(controlId, settings.leftHandedLayout)
                val effectiveScale = (slot.scale * globalScale).coerceIn(0.65f, 1.65f)
                val wDp = (controlId.baseWidthDp * effectiveScale).coerceAtLeast(48f).dp
                val hDp = (controlId.baseHeightDp * effectiveScale).coerceAtLeast(44f).dp
                val wPx = with(density) { wDp.toPx() }
                val hPx = with(density) { hDp.toPx() }
                val leftPx = (slot.xFrac * areaWidthPx - wPx * 0.5f).coerceIn(0f, (areaWidthPx - wPx).coerceAtLeast(0f))
                val topPx = (slot.yFrac * areaHeightPx - hPx * 0.5f).coerceIn(0f, (areaHeightPx - hPx).coerceAtLeast(0f))
                val isSelected = selectedId == controlId

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .size(width = wDp, height = hDp)
                        .graphicsLayer { alpha = slot.alpha }
                        .pointerInput(controlId, areaWidthPx, areaHeightPx) {
                            detectTapGestures(
                                onTap = { selectedId = controlId }
                            )
                        }
                        .pointerInput(controlId, areaWidthPx, areaHeightPx) {
                            detectDragGestures(
                                onDragStart = { selectedId = controlId },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val cur = draftLayout.slotFor(controlId, settings.leftHandedLayout)
                                    val nextX = (cur.xFrac + dragAmount.x / areaWidthPx).coerceIn(0.05f, 0.95f)
                                    val nextY = (cur.yFrac + dragAmount.y / areaHeightPx).coerceIn(0.06f, 0.94f)
                                    draftLayout = draftLayout.withUpdatedSlot(
                                        id = controlId,
                                        slot = cur.copy(xFrac = nextX, yFrac = nextY),
                                        leftHanded = settings.leftHandedLayout
                                    )
                                }
                            )
                        }
                        .testTag("editor_control_${controlId.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    EditorControlPreview(
                        controlId = controlId,
                        selected = isSelected,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Floating compact inspector bar (auto-moves to bottom when editing top buttons)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BunkerSlate.copy(alpha = 0.94f),
                border = BorderStroke(1.5.dp, BeakAmber.copy(alpha = 0.8f)),
                modifier = Modifier
                    .align(if (toolbarAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenWith,
                            contentDescription = null,
                            tint = BeakAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "КАСТОМНАЯ НАСТРОЙКА: ${selectedId.label.uppercase()}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = FeatherWhite
                            )
                            Text(
                                text = "Перетаскивайте кнопки пальцем • Нажмите для выбора",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedSteel,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        OutlinedButton(
                            onClick = {
                                draftLayout = CustomHudLayout(
                                    customized = false,
                                    slots = CustomHudLayout.defaultSlots(settings.leftHandedLayout)
                                )
                            },
                            border = BorderStroke(1.dp, CombCrimson.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("hud_editor_reset_button")
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = CombCrimson, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Сброс", style = MaterialTheme.typography.labelMedium, color = FeatherWhite)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            border = BorderStroke(1.dp, MutedSteel.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("hud_editor_cancel_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = FeatherWhite, modifier = Modifier.size(16.dp))
                        }

                        Button(
                            onClick = {
                                onSaveLayout(draftLayout)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NitroEmerald,
                                contentColor = TacticalNavy
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("hud_editor_save_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("СОХРАНИТЬ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    // Individual Size & Opacity sliders for the selected control
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(210.dp)
                        ) {
                            Text(
                                text = "Размер ${(selectedSlot.scale * 100f).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = BeakAmber,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(74.dp)
                            )
                            Slider(
                                value = selectedSlot.scale,
                                onValueChange = { newScale ->
                                    draftLayout = draftLayout.withUpdatedSlot(
                                        id = selectedId,
                                        slot = selectedSlot.copy(scale = newScale),
                                        leftHanded = settings.leftHandedLayout
                                    )
                                },
                                valueRange = 0.70f..1.45f,
                                colors = SliderDefaults.colors(
                                    thumbColor = BeakAmber,
                                    activeTrackColor = BeakAmber,
                                    inactiveTrackColor = SteelSurface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .testTag("hud_editor_size_slider")
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(210.dp)
                        ) {
                            Text(
                                text = "Вид. ${(selectedSlot.alpha * 100f).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = ArenaSkyCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(66.dp)
                            )
                            Slider(
                                value = selectedSlot.alpha,
                                onValueChange = { newAlpha ->
                                    draftLayout = draftLayout.withUpdatedSlot(
                                        id = selectedId,
                                        slot = selectedSlot.copy(alpha = newAlpha),
                                        leftHanded = settings.leftHandedLayout
                                    )
                                },
                                valueRange = 0.35f..1.00f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ArenaSkyCyan,
                                    activeTrackColor = ArenaSkyCyan,
                                    inactiveTrackColor = SteelSurface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .testTag("hud_editor_alpha_slider")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorControlPreview(
    controlId: HudControlId,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = when (controlId) {
        HudControlId.JOYSTICK -> ArenaSkyCyan
        HudControlId.FIRE -> ExplosiveOrange
        HudControlId.JUMP -> NitroEmerald
        HudControlId.AIM -> ArenaSkyCyan
        HudControlId.SWITCH_WEAPON -> BeakAmber
        HudControlId.BUILD -> BeakAmber
        HudControlId.CAMERA -> ArenaSkyCyan
        HudControlId.WEAPON_BAR -> BeakAmber
    }
    val shape = if (controlId.isCircle) CircleShape else RoundedCornerShape(12.dp)
    val borderColor = if (selected) BeakAmber else accent.copy(alpha = 0.78f)
    val borderWidth = if (selected) 2.6.dp else 1.5.dp

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.34f) else BunkerSlate.copy(alpha = 0.80f))
            .border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        when (controlId) {
            HudControlId.JOYSTICK -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ArenaSkyCyan.copy(alpha = 0.4f))
                            .border(1.5.dp, ArenaSkyCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.OpenWith, contentDescription = null, tint = FeatherWhite, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("ДЖОЙСТИК", style = MaterialTheme.typography.labelSmall, color = FeatherWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            HudControlId.FIRE -> EditorCircleLabel(Icons.Filled.GpsFixed, "ОГОНЬ", accent)
            HudControlId.JUMP -> EditorCircleLabel(Icons.Filled.ArrowUpward, "ПРЫЖОК", accent)
            HudControlId.AIM -> EditorCircleLabel(Icons.Filled.CenterFocusStrong, "ПРИЦЕЛ", accent)
            HudControlId.SWITCH_WEAPON -> EditorCircleLabel(Icons.Filled.SwapHoriz, "ОРУЖ.", accent)
            HudControlId.BUILD -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Construction, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text("СТРОЙКА", style = MaterialTheme.typography.labelSmall, color = FeatherWhite, fontWeight = FontWeight.Bold)
            }
            HudControlId.CAMERA -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text("1P/3P", style = MaterialTheme.typography.labelSmall, color = FeatherWhite, fontWeight = FontWeight.Bold)
            }
            HudControlId.WEAPON_BAR -> Text(
                text = "Пистолет • Автомат • РПГ",
                style = MaterialTheme.typography.labelSmall,
                color = FeatherWhite,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EditorCircleLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = FeatherWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}
