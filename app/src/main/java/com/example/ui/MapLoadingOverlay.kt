package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.GameMap
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.BunkerSlate
import com.example.ui.theme.ExplosiveOrange
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.SteelSurface
import com.example.ui.theme.TacticalNavy
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class MapLoadingTheme(
    val displayTitle: String,
    val backdropDrawable: Int,
    val accentColor: Color,
    val secondaryAccent: Color,
    val badgeIcon: ImageVector
)

private fun themeForMap(map: GameMap): MapLoadingTheme = when (map) {
    GameMap.MAIN_ARENA -> MapLoadingTheme(
        displayTitle = "Полигон",
        backdropDrawable = R.drawable.img_menu_arena_1795119038541,
        accentColor = ArenaSkyCyan,
        secondaryAccent = NitroEmerald,
        badgeIcon = Icons.Filled.SportsEsports
    )
    GameMap.TURBO_SPEEDWAY -> MapLoadingTheme(
        displayTitle = "Турбо-Трек",
        backdropDrawable = R.drawable.img_bg_stunt_arena_1795199607105,
        accentColor = ExplosiveOrange,
        secondaryAccent = BeakAmber,
        badgeIcon = Icons.Filled.DirectionsCar
    )
    GameMap.TACTICAL_COMPOUND -> MapLoadingTheme(
        displayTitle = "Штурм-База",
        backdropDrawable = R.drawable.img_bg_chalohouse_sunset_1795199606926,
        accentColor = BeakAmber,
        secondaryAccent = ArenaSkyCyan,
        badgeIcon = Icons.Filled.Shield
    )
}

private val LOADING_TIPS: List<String> = listOf(
    "На резких поворотах слегка отпускай газ и входи в занос — гиперкар и пикап сохраняют скорость в дрифте.",
    "Жми ТУРБО перед трамплинами и петлёй 360°, чтобы перелетать арену и проноситься сквозь огненные кольца.",
    "Подойди к Хряку-Бургеру и погладь его — он подпрыгнет от радости и запустит праздничный фейерверк!",
    "В онлайн-комнате хост может сменить карту, режим и правила матча в любой момент через меню паузы.",
    "Открой панель СТРОЙКА, чтобы ставить трамплины, укрытия, стены и взрывные бочки прямо посреди боя.",
    "Двери домов, ангара и арки на карте интерактивные — открывай и закрывай их для тактических засад.",
    "Используй свободную камеру для обзора арены, а выбранные скины персонажа и оружия сохраняются в облаке."
)

/**
 * Fullscreen themed Map Loading Screen drawn over the OpenGL viewport during initial match launch
 * and live map switches. Displays real 0..100% shader/mesh/.glb progress, rotating gameplay tips
 * every 2 seconds, and performs a smooth 300ms fade-out once the first ready frame is rendered.
 */
@Composable
fun MapLoadingOverlay(
    map: GameMap,
    isLoading: Boolean,
    progress: Float,
    stageLabel: String,
    isOnline: Boolean,
    peacefulMode: Boolean,
    roomCode: String? = null,
    modifier: Modifier = Modifier
) {
    // Snap immediately to alpha = 1f when loading starts (prevents any black flash),
    // and fade out smoothly over 300ms once loading finishes and the first frame is ready.
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = if (isLoading) snap() else tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "map_loading_fade"
    )

    if (!isLoading && overlayAlpha <= 0.005f) return

    val mapTheme = remember(map) { themeForMap(map) }
    val targetFraction = if (isLoading) progress.coerceIn(0f, 1f) else 1f
    val animatedProgress by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "map_loading_progress"
    )
    val percentInt = (animatedProgress * 100f).roundToInt().coerceIn(0, 100)

    // Rotate helpful tips every 2 seconds while loading is visible
    var tipIndex by remember(map) { mutableIntStateOf(map.ordinal % LOADING_TIPS.size) }
    LaunchedEffect(isLoading, map) {
        if (!isLoading) return@LaunchedEffect
        while (true) {
            delay(2000L)
            tipIndex = (tipIndex + 1) % LOADING_TIPS.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "loading_ambient")
    val backdropScale by infiniteTransition.animateFloat(
        initialValue = 1.02f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backdrop_zoom"
    )
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress_shimmer"
    )

    val swallowTouchModifier = if (isLoading) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {}
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .then(swallowTouchModifier)
            .background(TacticalNavy)
            .testTag("map_loading_overlay")
    ) {
        // 1. Stylized map artwork backdrop with gentle atmospheric breathing zoom
        Image(
            painter = painterResource(id = mapTheme.backdropDrawable),
            contentDescription = stringResource(R.string.cd_loading_backdrop),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = backdropScale
                    scaleY = backdropScale
                }
        )

        // 2. Layered cinematic gradient scrim & map-tinted radial glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to TacticalNavy.copy(alpha = 0.78f),
                            0.42f to TacticalNavy.copy(alpha = 0.56f),
                            0.74f to TacticalNavy.copy(alpha = 0.86f),
                            1.00f to TacticalNavy.copy(alpha = 0.96f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            mapTheme.accentColor.copy(alpha = 0.24f),
                            Color.Transparent
                        ),
                        center = Offset(260f, 180f),
                        radius = 850f
                    )
                )
        )

        // 3. Safe-area responsive content layout (compact & landscape friendly)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 18.dp)
                .widthIn(max = 820.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: Mode Pill + Loading Map Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (modeText, modeIcon, modeTint) = when {
                    isOnline -> Triple(
                        if (!roomCode.isNullOrBlank()) "${stringResource(R.string.loading_mode_online)} • $roomCode"
                        else stringResource(R.string.loading_mode_online),
                        Icons.Filled.Public,
                        ArenaSkyCyan
                    )
                    peacefulMode -> Triple(
                        stringResource(R.string.loading_mode_peaceful),
                        Icons.Filled.Build,
                        NitroEmerald
                    )
                    else -> Triple(
                        stringResource(R.string.loading_mode_solo),
                        Icons.Filled.Flag,
                        BeakAmber
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = BunkerSlate.copy(alpha = 0.86f),
                    border = BorderStroke(1.dp, modeTint.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = null,
                            tint = modeTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = modeText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = FeatherWhite,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = mapTheme.accentColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, mapTheme.accentColor.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = mapTheme.badgeIcon,
                            contentDescription = null,
                            tint = mapTheme.accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(R.string.loading_map_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = mapTheme.accentColor,
                            letterSpacing = 1.1.sp
                        )
                    }
                }
            }

            // Center hero: Selected Map Name & Subtitle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(mapTheme.accentColor, mapTheme.secondaryAccent)
                            )
                        )
                )
                Text(
                    text = mapTheme.displayTitle.uppercase(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.72f),
                            offset = Offset(0f, 4f),
                            blurRadius = 14f
                        )
                    ),
                    color = FeatherWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("map_loading_title")
                )
                Text(
                    text = map.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.7f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f
                        )
                    ),
                    color = FeatherWhite.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom dock: Rotating Tip Card + Animated 0..100% Progress Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Rotating gameplay tip card (switches every 2 seconds)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BunkerSlate.copy(alpha = 0.84f),
                    border = BorderStroke(1.dp, SteelSurface.copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(BeakAmber.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lightbulb,
                                contentDescription = stringResource(R.string.cd_loading_tip_icon),
                                tint = BeakAmber,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.loading_tip_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BeakAmber,
                                    letterSpacing = 1.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (i in LOADING_TIPS.indices) {
                                        Box(
                                            modifier = Modifier
                                                .size(if (i == tipIndex) 6.dp else 4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (i == tipIndex) mapTheme.accentColor
                                                    else MutedSteel.copy(alpha = 0.45f)
                                                )
                                        )
                                    }
                                }
                            }
                            AnimatedContent(
                                targetState = tipIndex,
                                transitionSpec = {
                                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 3 })
                                        .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 3 })
                                },
                                label = "loading_tip_switch"
                            ) { idx ->
                                Text(
                                    text = LOADING_TIPS[idx],
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FeatherWhite.copy(alpha = 0.94f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("map_loading_tip_text")
                                )
                            }
                        }
                    }
                }

                // Progress status row + glowing animated bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = stageLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedSteel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = "$percentInt%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = mapTheme.accentColor,
                            modifier = Modifier.testTag("map_loading_progress_text")
                        )
                    }

                    // Custom glowing GPU-friendly Canvas progress bar
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .testTag("map_loading_progress_bar")
                    ) {
                        val radius = CornerRadius(size.height / 2f, size.height / 2f)
                        // Track background
                        drawRoundRect(
                            color = BunkerSlate.copy(alpha = 0.92f),
                            cornerRadius = radius
                        )
                        val fillWidth = (size.width * animatedProgress).coerceIn(0f, size.width)
                        if (fillWidth > 1f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(mapTheme.accentColor, mapTheme.secondaryAccent),
                                    startX = 0f,
                                    endX = fillWidth.coerceAtLeast(1f)
                                ),
                                size = Size(fillWidth, size.height),
                                cornerRadius = radius
                            )
                            // Moving glossy highlight shimmer across the filled portion
                            val shimmerCenter = fillWidth * shimmerPhase
                            val shimmerHalf = (size.width * 0.14f).coerceAtLeast(24f)
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.42f),
                                        Color.Transparent
                                    ),
                                    startX = shimmerCenter - shimmerHalf,
                                    endX = shimmerCenter + shimmerHalf
                                ),
                                size = Size(fillWidth, size.height),
                                cornerRadius = radius
                            )
                        }
                    }
                }
            }
        }
    }
}
