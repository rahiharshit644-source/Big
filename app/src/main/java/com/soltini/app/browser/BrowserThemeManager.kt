package com.soltini.app.browser

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.soltini.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BrowserTheme
 *
 * Defines soft, pastel, light glassmorphic color schemes for the Myra Internal Browser.
 */
data class BrowserTheme(
    val id: String,
    val name: String,
    val emoji: String,
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val textPrimary: Color = Color(0xFF2A2A35),
    val textSecondary: Color = Color(0xFF8A8A95),
    val gradientColors: List<Color>
)

enum class WallpaperType {
    DRAWABLE_RES,
    GRADIENT,
    SOLID
}

/**
 * BrowserWallpaper
 *
 * Represents a soft pastel background or procedural gradient for the browser.
 */
data class BrowserWallpaper(
    val id: String,
    val name: String,
    val category: String,
    val type: WallpaperType,
    val drawableRes: Int? = null,
    val gradientColors: List<Color>? = null,
    val solidColor: Color? = null,
    val previewColor: Color
)

class BrowserThemeManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("myra_browser_theme_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: BrowserThemeManager? = null

        fun getInstance(context: Context): BrowserThemeManager {
            return instance ?: synchronized(this) {
                instance ?: BrowserThemeManager(context).also { instance = it }
            }
        }

        val THEMES = listOf(
            BrowserTheme(
                id = "pastel_lavender",
                name = "Soft Lavender & Mint",
                emoji = "🌸",
                primary = Color(0xFF9B72EF),
                secondary = Color(0xFF249E6B),
                surface = Color(0xEBFFFFFF),
                surfaceVariant = Color(0xFFF5F3FA),
                border = Color(0xFFEAE6F3),
                gradientColors = listOf(Color(0xFFB993F0), Color(0xFFE8A0E0))
            ),
            BrowserTheme(
                id = "cyberpunk",
                name = "Pastel Orchid",
                emoji = "✨",
                primary = Color(0xFF8E5AE8),
                secondary = Color(0xFFD65DB1),
                surface = Color(0xEBFFFFFF),
                surfaceVariant = Color(0xFFF6F1FD),
                border = Color(0xFFE9DFF8),
                gradientColors = listOf(Color(0xFFB993F0), Color(0xFFD99BF0), Color(0xFFE8A0E0))
            ),
            BrowserTheme(
                id = "cosmic_purple",
                name = "Dreamy Lilac",
                emoji = "🔮",
                primary = Color(0xFF8B5CF6),
                secondary = Color(0xFFEC4899),
                surface = Color(0xEBFFFFFF),
                surfaceVariant = Color(0xFFF3EEFC),
                border = Color(0xFFE6DDF7),
                gradientColors = listOf(Color(0xFFA78BFA), Color(0xFFC084FC), Color(0xFFF472B6))
            ),
            BrowserTheme(
                id = "emerald_matrix",
                name = "Mint Breeze",
                emoji = "🌿",
                primary = Color(0xFF249E6B),
                secondary = Color(0xFF8E5AE8),
                surface = Color(0xEBFFFFFF),
                surfaceVariant = Color(0xFFEFF9F3),
                border = Color(0xFFD8F0E2),
                gradientColors = listOf(Color(0xFFA9E8C9), Color(0xFF7AD9B0), Color(0xFFB993F0))
            ),
            BrowserTheme(
                id = "sunset_glow",
                name = "Peach Blossom",
                emoji = "🍑",
                primary = Color(0xFFD65DB1),
                secondary = Color(0xFFD9822B),
                surface = Color(0xEBFFFFFF),
                surfaceVariant = Color(0xFFFDF3F8),
                border = Color(0xFFF7E2EE),
                gradientColors = listOf(Color(0xFFE8A0E0), Color(0xFFF6B092), Color(0xFFB993F0))
            ),
            BrowserTheme(
                id = "ocean_azure",
                name = "Sky Iridescence",
                emoji = "☁️",
                primary = Color(0xFF5B8DEF),
                secondary = Color(0xFF9B72EF),
                surface = Color(0xEBFFFFFF),
                surfaceVariant = Color(0xFFF0F6FE),
                border = Color(0xFFDCE9FA),
                gradientColors = listOf(Color(0xFF8EC5FC), Color(0xFFE0C3FC), Color(0xFFE8A0E0))
            )
        )

        val WALLPAPERS = listOf(
            BrowserWallpaper(
                id = "grad_lavender_mint",
                name = "Lavender to Mint Air",
                category = "Pastel Gradients",
                type = WallpaperType.GRADIENT,
                gradientColors = listOf(Color(0xFFF3EEFC), Color(0xFFF7F4FD), Color(0xFFEAF6EE)),
                previewColor = Color(0xFFB993F0)
            ),
            BrowserWallpaper(
                id = "grad_aurora",
                name = "Iridescent Pearl",
                category = "Pastel Gradients",
                type = WallpaperType.GRADIENT,
                gradientColors = listOf(Color(0xFFFCEBF9), Color(0xFFF3EEFC), Color(0xFFE8F3FE)),
                previewColor = Color(0xFFE8A0E0)
            ),
            BrowserWallpaper(
                id = "grad_deep_galaxy",
                name = "Soft Mint Meadow",
                category = "Pastel Gradients",
                type = WallpaperType.GRADIENT,
                gradientColors = listOf(Color(0xFFEAF6EE), Color(0xFFF4FAF6), Color(0xFFF3EEFC)),
                previewColor = Color(0xFFA9E8C9)
            ),
            BrowserWallpaper(
                id = "grad_sunset_sky",
                name = "Warm Rose & Lilac",
                category = "Pastel Gradients",
                type = WallpaperType.GRADIENT,
                gradientColors = listOf(Color(0xFFFFF5F8), Color(0xFFF7EEFC), Color(0xFFEAF6EE)),
                previewColor = Color(0xFFF472B6)
            ),
            BrowserWallpaper(
                id = "wp_cyberpunk",
                name = "Soft Cyber Art",
                category = "Art",
                type = WallpaperType.DRAWABLE_RES,
                drawableRes = R.drawable.img_wp_cyberpunk,
                previewColor = Color(0xFFB993F0)
            ),
            BrowserWallpaper(
                id = "wp_cosmic",
                name = "Cosmic Silk",
                category = "Art",
                type = WallpaperType.DRAWABLE_RES,
                drawableRes = R.drawable.img_wp_cosmic,
                previewColor = Color(0xFFA855F7)
            ),
            BrowserWallpaper(
                id = "solid_oled",
                name = "Pure Soft Lavender",
                category = "Solid",
                type = WallpaperType.SOLID,
                solidColor = Color(0xFFF3EEFC),
                previewColor = Color(0xFFF3EEFC)
            )
        )
    }

    private val _currentTheme = MutableStateFlow(getSavedTheme())
    val currentTheme: StateFlow<BrowserTheme> = _currentTheme.asStateFlow()

    private val _currentWallpaper = MutableStateFlow(getSavedWallpaper())
    val currentWallpaper: StateFlow<BrowserWallpaper> = _currentWallpaper.asStateFlow()

    private val _wallpaperDim = MutableStateFlow(prefs.getFloat("wallpaper_dim", 0.15f))
    val wallpaperDim: StateFlow<Float> = _wallpaperDim.asStateFlow()

    private val _adBlockerCount = MutableStateFlow(prefs.getInt("ads_blocked_count", 28))
    val adBlockerCount: StateFlow<Int> = _adBlockerCount.asStateFlow()

    private fun getSavedTheme(): BrowserTheme {
        val savedId = prefs.getString("selected_theme_id", "pastel_lavender")
        return THEMES.find { it.id == savedId } ?: THEMES.first()
    }

    private fun getSavedWallpaper(): BrowserWallpaper {
        val savedId = prefs.getString("selected_wallpaper_id", "grad_lavender_mint")
        return WALLPAPERS.find { it.id == savedId } ?: WALLPAPERS.first()
    }

    fun setTheme(theme: BrowserTheme) {
        _currentTheme.value = theme
        prefs.edit().putString("selected_theme_id", theme.id).apply()
    }

    fun setWallpaper(wallpaper: BrowserWallpaper) {
        _currentWallpaper.value = wallpaper
        prefs.edit().putString("selected_wallpaper_id", wallpaper.id).apply()
    }

    fun setWallpaperDim(dim: Float) {
        _wallpaperDim.value = dim.coerceIn(0.05f, 0.75f)
        prefs.edit().putFloat("wallpaper_dim", _wallpaperDim.value).apply()
    }

    fun incrementBlockedAds() {
        val next = _adBlockerCount.value + 1
        _adBlockerCount.value = next
        prefs.edit().putInt("ads_blocked_count", next).apply()
    }
}
