package com.soltini.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Presentation tokens for the active theme. Screens/components read these through [LocalMyraTokens]
 * instead of importing raw colors, so one component can serve both themes.
 *
 * CLASSIC tokens are mapped 1:1 onto the pre-existing colors/values, so Classic stays visually identical.
 */
@Immutable
data class MyraTokens(
    val mode: MyraThemeMode,
    val backgroundBrush: Brush,
    val accent: Color,
    val accentSecondary: Color,
    val accentGradient: Brush,
    val accentHorizontalGradient: Brush,
    val textPrimary: Color,
    val textSecondary: Color,
    val cardColor: Color,
    val cardBorder: BorderStroke?,
    val shadowColor: Color,
    val cardCornerRadius: Dp,
    val subtleSurface: Color
)

val ClassicTokens = MyraTokens(
    mode = MyraThemeMode.CLASSIC,
    backgroundBrush = SoftBackgroundBrush,
    accent = PrimaryPurple,
    accentSecondary = PrimaryPink,
    accentGradient = PrimaryGradientBrush,
    accentHorizontalGradient = PrimaryHorizontalBrush,
    textPrimary = TextCharcoal,
    textSecondary = TextMidGray,
    cardColor = GlassCardWhite,
    cardBorder = null,
    shadowColor = PrimaryPurple,
    cardCornerRadius = 24.dp,
    subtleSurface = GlassSurfaceSubtle
)

// Blue Glass palette (starting values from the redesign brief)
val BluePrimary = Color(0xFF087BFF)
val BlueElectricCyan = Color(0xFF39D9FF)
val BlueDeep = Color(0xFF0753D6)
val BlueIceBackground = Color(0xFFEAF5FF)
val BluePaleCyanSurface = Color(0xFFD8F1FF)
val BlueTextPrimary = Color(0xFF10264B)
val BlueTextSecondary = Color(0xFF617A9D)

val BlueGlassBackgroundBrush: Brush = Brush.verticalGradient(
    colors = listOf(BlueIceBackground, BluePaleCyanSurface, BlueIceBackground)
)

val BlueGlassTokens = MyraTokens(
    mode = MyraThemeMode.BLUE_GLASS,
    backgroundBrush = BlueGlassBackgroundBrush,
    accent = BluePrimary,
    accentSecondary = BlueElectricCyan,
    accentGradient = Brush.linearGradient(listOf(BluePrimary, BlueElectricCyan)),
    accentHorizontalGradient = Brush.horizontalGradient(listOf(BluePrimary, BlueElectricCyan)),
    textPrimary = BlueTextPrimary,
    textSecondary = BlueTextSecondary,
    cardColor = Color(0xCCFFFFFF),                     // ~80% white: frosted, lets the blue show through
    cardBorder = BorderStroke(1.dp, Color(0xB3FFFFFF)), // subtle white edge
    shadowColor = BluePrimary,
    cardCornerRadius = 26.dp,
    subtleSurface = Color(0x99FFFFFF)
)

fun tokensFor(mode: MyraThemeMode): MyraTokens = when (mode) {
    MyraThemeMode.CLASSIC -> ClassicTokens
    MyraThemeMode.BLUE_GLASS -> BlueGlassTokens
}

/** Defaults to Classic so any composable rendered outside GeminiVoiceTheme (previews, tests) looks as before. */
val LocalMyraTokens = compositionLocalOf { ClassicTokens }
