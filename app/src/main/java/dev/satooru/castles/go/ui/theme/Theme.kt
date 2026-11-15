package dev.satooru.castles.go.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Ally,
    onSecondary = OnPrimary,
    secondaryContainer = AllyMuted,
    onSecondaryContainer = Primary,
    tertiary = Ally,
    error = Enemy,
    background = Color.White,
    onBackground = Primary,
    surface = Color.White,
    onSurface = Primary,
    surfaceVariant = PrimaryMuted,
    onSurfaceVariant = MutedForeground,
    surfaceContainerHighest = PrimaryMuted,
    outline = Border,
    outlineVariant = Border,
)

// base = 10dp．sm = 0.6，md = 0.8，lg = 1，xl = 1.4 倍．
private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
)

/** 白地・黒基調の単一テーマ．ダイナミックカラーは使わない． */
@Composable
fun CastleGoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
