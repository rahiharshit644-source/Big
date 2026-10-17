package com.soltini.app.ui.theme

/**
 * The two complete looks MYRA can wear. Both run on exactly the same screens and business logic;
 * only presentation tokens differ (see [MyraTokens]).
 *
 * [id] is what gets persisted in AppSettings. Never rename an id after release.
 */
enum class MyraThemeMode(val id: String, val displayName: String, val description: String) {
    CLASSIC("classic", "Classic MYRA", "Original MYRA appearance"),
    BLUE_GLASS("blue_glass", "Blue Glass", "Futuristic blue glassmorphism");

    companion object {
        /** Unknown / missing / corrupted values fall back to CLASSIC so existing users are never switched. */
        fun fromId(id: String?): MyraThemeMode = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}
