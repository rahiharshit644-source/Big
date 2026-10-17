package com.soltini.app

import com.soltini.app.ui.components.MyraOrbState
import com.soltini.app.ui.theme.BlueGlassTokens
import com.soltini.app.ui.theme.ClassicTokens
import com.soltini.app.ui.theme.MyraThemeMode
import com.soltini.app.ui.theme.tokensFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ThemeTokensTest {
    @Test fun `classic orb colours are unchanged`() {
        MyraOrbState.entries.forEach { assertEquals(it.color, it.colorFor(MyraThemeMode.CLASSIC)) }
    }

    @Test fun `blue glass recolours speaking and listening`() {
        assertNotEquals(MyraOrbState.SPEAKING.color, MyraOrbState.SPEAKING.colorFor(MyraThemeMode.BLUE_GLASS))
        assertNotEquals(MyraOrbState.LISTENING.color, MyraOrbState.LISTENING.colorFor(MyraThemeMode.BLUE_GLASS))
    }

    @Test fun `tokensFor maps modes`() {
        assertSame(ClassicTokens, tokensFor(MyraThemeMode.CLASSIC))
        assertSame(BlueGlassTokens, tokensFor(MyraThemeMode.BLUE_GLASS))
    }
}
