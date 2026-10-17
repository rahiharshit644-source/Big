package com.soltini.app.ui.theme

import android.content.Context
import com.soltini.app.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the active theme. Backed by AppSettings (SharedPreferences) so the choice
 * survives restarts and activity recreation; exposed as a StateFlow so the UI recomposes instantly
 * with no activity restart (unsaved input in other screens is kept).
 *
 * Only the theme id is stored/changed here: it never touches conversations, memories, keys, permissions
 * or any other setting.
 */
object ThemeController {
    private val _mode = MutableStateFlow(MyraThemeMode.CLASSIC)
    val mode: StateFlow<MyraThemeMode> = _mode.asStateFlow()

    /** Call once early (MainActivity.onCreate). Missing/unknown pref => CLASSIC (existing users unchanged). */
    fun init(context: Context) {
        _mode.value = MyraThemeMode.fromId(AppSettings(context).appThemeMode)
    }

    fun set(context: Context, newMode: MyraThemeMode) {
        AppSettings(context).appThemeMode = newMode.id
        _mode.value = newMode
    }
}
