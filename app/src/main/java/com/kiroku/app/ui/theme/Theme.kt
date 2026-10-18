package com.kiroku.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Brand palette (as provided)
val Sky300 = Color(0xFF7DD3FC)
val Sky500 = Color(0xFF0EA5E9)
val Gray800 = Color(0xFF1F2937)
val Slate700 = Color(0xFF334155)
val White = Color(0xFFFFFFFF)

// Supporting tints from the same Tailwind-style families
private val Sky100 = Color(0xFFE0F2FE)
private val Sky900 = Color(0xFF0C4A6E)
private val Sky950 = Color(0xFF082F49)
private val Gray900 = Color(0xFF111827)
private val Slate50 = Color(0xFFF8FAFC)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate300 = Color(0xFFCBD5E1)
private val Slate400 = Color(0xFF94A3B8)
private val Slate500 = Color(0xFF64748B)
private val Slate600 = Color(0xFF475569)
private val Red400 = Color(0xFFF87171)
private val Red600 = Color(0xFFDC2626)

private val DarkColors = darkColorScheme(
    primary = Sky300,
    onPrimary = Sky950,
    primaryContainer = Sky900,
    onPrimaryContainer = Sky100,
    secondary = Sky500,
    onSecondary = White,
    secondaryContainer = Sky900,
    onSecondaryContainer = Sky300,
    tertiary = Sky300,
    onTertiary = Sky950,
    background = Gray800,
    onBackground = White,
    surface = Gray800,
    onSurface = White,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate300,
    surfaceTint = Sky300,
    surfaceContainerLowest = Gray900,
    surfaceContainerLow = Color(0xFF243142),
    surfaceContainer = Color(0xFF283548),
    surfaceContainerHigh = Slate700,
    surfaceContainerHighest = Slate600,
    outline = Slate500,
    outlineVariant = Slate700,
    error = Red400,
    onError = Color(0xFF450A0A),
    inverseSurface = Slate100,
    inverseOnSurface = Gray800,
    inversePrimary = Sky500,
)

private val LightColors = lightColorScheme(
    primary = Sky500,
    onPrimary = White,
    primaryContainer = Sky100,
    onPrimaryContainer = Sky900,
    secondary = Slate700,
    onSecondary = White,
    secondaryContainer = Sky100,
    onSecondaryContainer = Sky900,
    tertiary = Sky500,
    onTertiary = White,
    background = White,
    onBackground = Gray800,
    surface = White,
    onSurface = Gray800,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    surfaceTint = Sky500,
    surfaceContainerLowest = White,
    surfaceContainerLow = Slate50,
    surfaceContainer = Slate50,
    surfaceContainerHigh = Slate100,
    surfaceContainerHighest = Slate200,
    outline = Slate400,
    outlineVariant = Slate200,
    error = Red600,
    onError = White,
    inverseSurface = Gray800,
    inverseOnSurface = White,
    inversePrimary = Sky300,
)

private val base = Typography()
private val KirokuTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val KirokuShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun KirokuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KirokuTypography,
        shapes = KirokuShapes,
        content = content,
    )
}
