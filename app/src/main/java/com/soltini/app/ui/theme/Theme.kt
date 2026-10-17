package com.soltini.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val SoftLightGlassColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = BadgePurpleBg,
    onPrimaryContainer = TextCharcoal,
    secondary = SecondaryMint,
    onSecondary = TextCharcoal,
    secondaryContainer = BadgeMintBg,
    onSecondaryContainer = SecondaryMintDark,
    tertiary = PrimaryPink,
    onTertiary = Color.White,
    background = BgLavender,
    onBackground = TextCharcoal,
    surface = GlassCardSolid,
    onSurface = TextCharcoal,
    surfaceVariant = GlassSurfaceSubtle,
    onSurfaceVariant = TextMidGray,
    outline = SoftDivider,
    outlineVariant = SoftDivider,
    error = RedError,
    onError = Color.White
)

private val BlueGlassColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = BluePaleCyanSurface,
    onPrimaryContainer = BlueTextPrimary,
    secondary = BlueElectricCyan,
    onSecondary = BlueTextPrimary,
    secondaryContainer = BluePaleCyanSurface,
    onSecondaryContainer = BlueDeep,
    tertiary = Color(0xFFB68AFF),
    onTertiary = Color.White,
    background = BlueIceBackground,
    onBackground = BlueTextPrimary,
    surface = Color.White,
    onSurface = BlueTextPrimary,
    surfaceVariant = BluePaleCyanSurface,
    onSurfaceVariant = BlueTextSecondary,
    outline = Color(0xFFBFD9F2),
    outlineVariant = Color(0xFFD3E6F7),
    error = RedError,
    onError = Color.White
)

val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun GeminiVoiceTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    val mode by ThemeController.mode.collectAsState()
    CompositionLocalProvider(LocalMyraTokens provides tokensFor(mode)) {
        MaterialTheme(
            colorScheme = if (mode == MyraThemeMode.BLUE_GLASS) BlueGlassColorScheme else SoftLightGlassColorScheme,
            typography = Typography,
            shapes = GlassShapes,
            content = content
        )
    }
}
