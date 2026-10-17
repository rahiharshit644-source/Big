# Phase 2 — Dual-theme foundation (Classic MYRA + Blue Glass)
Unzip over the project root (paths match). NOT compiled or tested here (no Gradle/network in the sandbox) — run `./gradlew :app:testDebugUnitTest :app:assembleDebug` first.
New: ui/theme/{MyraThemeMode,MyraTokens,ThemeController}.kt, ui/AppearanceSettingsSection.kt, test ThemePreferenceTest.kt
Changed: AppSettings (appThemeMode, default "classic"), Theme.kt (provides tokens + Blue Glass scheme), GlassCard (tokens), MainActivity (ThemeController.init), SettingsScreen (Appearance route + menu card), 7 screens: background -> token brush.
Not yet themed (Phase 3+): bottom nav, buttons/badges/chips, orbs, browser theme, new screens.
