package com.soltini.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.soltini.app.settings.AppSettings
import com.soltini.app.ui.theme.BlueGlassTokens
import com.soltini.app.ui.theme.ClassicTokens
import com.soltini.app.ui.theme.MyraThemeMode
import com.soltini.app.ui.theme.ThemeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemePreferenceTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `default is classic so existing users are not switched`() {
        assertEquals("classic", AppSettings(ctx).appThemeMode)
        ThemeController.init(ctx)
        assertEquals(MyraThemeMode.CLASSIC, ThemeController.mode.value)
    }

    @Test fun `selection persists across a fresh AppSettings and re-init`() {
        ThemeController.set(ctx, MyraThemeMode.BLUE_GLASS)
        assertEquals("blue_glass", AppSettings(ctx).appThemeMode)
        ThemeController.set(ctx, MyraThemeMode.CLASSIC) // simulate leaving state, then restart:
        AppSettings(ctx).appThemeMode = "blue_glass"
        ThemeController.init(ctx)
        assertEquals(MyraThemeMode.BLUE_GLASS, ThemeController.mode.value)
    }

    @Test fun `switching theme does not touch other settings`() {
        val s = AppSettings(ctx)
        s.floatingOrbStyle = AppSettings.ORB_STYLE_PARTICLE
        ThemeController.set(ctx, MyraThemeMode.BLUE_GLASS)
        assertEquals(AppSettings.ORB_STYLE_PARTICLE, AppSettings(ctx).floatingOrbStyle)
    }

    @Test fun `unknown or null id falls back to classic`() {
        assertEquals(MyraThemeMode.CLASSIC, MyraThemeMode.fromId(null))
        assertEquals(MyraThemeMode.CLASSIC, MyraThemeMode.fromId("garbage"))
        assertEquals(MyraThemeMode.BLUE_GLASS, MyraThemeMode.fromId("blue_glass"))
    }

    @Test fun `token sets differ`() {
        assertNotEquals(ClassicTokens.accent, BlueGlassTokens.accent)
        assertEquals(MyraThemeMode.CLASSIC, ClassicTokens.mode)
    }
}
