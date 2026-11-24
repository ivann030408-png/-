package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ChickenGunColorScheme = darkColorScheme(
    primary = BeakAmber,
    onPrimary = TacticalNavy,
    primaryContainer = ExplosiveOrange,
    onPrimaryContainer = FeatherWhite,
    secondary = ArenaSkyCyan,
    onSecondary = TacticalNavy,
    secondaryContainer = SteelSurface,
    onSecondaryContainer = FeatherWhite,
    tertiary = NitroEmerald,
    onTertiary = TacticalNavy,
    background = TacticalNavy,
    onBackground = FeatherWhite,
    surface = BunkerSlate,
    onSurface = FeatherWhite,
    surfaceVariant = SteelSurface,
    onSurfaceVariant = MutedSteel,
    error = CombCrimson,
    onError = Color.White,
    outline = Color(0xFF475569)
)

private val ChickenGunShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun ChickenGunTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ChickenGunColorScheme,
        typography = Typography,
        shapes = ChickenGunShapes,
        content = content
    )
}
