package com.soltini.app.orchestrator

/**
 * AppDestination
 *
 * Defines the execution destination for web, search, video, and music intents.
 * Internal browser is the DEFAULT environment.
 * External apps (Chrome or YouTube App) are strictly EXPLICIT overrides only.
 */
enum class AppDestination {
    INTERNAL_BROWSER,
    CHROME_APP,
    YOUTUBE_APP
}

/**
 * DestinationResolver
 *
 * Resolves whether an intent should be routed to MYRA's Internal Browser (default)
 * or to an explicit external app requested by the user.
 *
 * Routing Priority:
 *   1. Explicit external-app command (Chrome App / YouTube App) -> Highest Priority
 *   2. Explicit website/service command (e.g. YouTube web in internal browser)
 *   3. Media / search intent -> Internal Browser
 *   4. Default -> MYRA Internal Browser
 */
object DestinationResolver {

    /**
     * Resolves the target destination based on natural language user input.
     *
     * Examples:
     * - "Chrome par search karo..." -> CHROME_APP
     * - "Chrome mein ye website kholo" -> CHROME_APP
     * - "Chrome par YouTube search karo" -> CHROME_APP
     * - "YouTube app par Arijit Singh ka song bajao" -> YOUTUBE_APP
     * - "YouTube application mein ye video chalao" -> YOUTUBE_APP
     * - "YouTube par Arijit Singh ka song chalao" -> INTERNAL_BROWSER (default)
     * - "YouTube par ye video dekho" -> INTERNAL_BROWSER (default)
     * - "Google par search karo..." -> INTERNAL_BROWSER (default)
     * - "Search karo Python tutorial" -> INTERNAL_BROWSER (default)
     * - "Song chalao" / "Video chalao" / "Song bajao" -> INTERNAL_BROWSER (default)
     * - "Website kholo" / "Is link ko kholo" -> INTERNAL_BROWSER (default)
     */
    fun resolve(input: String): AppDestination {
        val lower = input.lowercase().trim()

        // 1. Explicit YouTube App check (must explicitly specify "app", "application", or "apk")
        if (isExplicitYouTubeApp(lower)) {
            return AppDestination.YOUTUBE_APP
        }

        // 2. Explicit Chrome check (user explicitly mentions Chrome app/platform)
        if (isExplicitChrome(lower)) {
            return AppDestination.CHROME_APP
        }

        // 3. Otherwise DEFAULT to MYRA Internal Browser
        return AppDestination.INTERNAL_BROWSER
    }

    /**
     * True ONLY if user explicitly requested the external YouTube Application.
     * Phrases like "YouTube par gaana chalao" or "YouTube par video dekho" do NOT trigger this.
     */
    fun isExplicitYouTubeApp(lower: String): Boolean {
        // Must contain "youtube" AND explicit app indicators
        val hasYouTube = lower.contains("youtube")
        if (!hasYouTube) return false

        return lower.contains("youtube app") ||
                lower.contains("youtube application") ||
                lower.contains("youtube apk") ||
                lower.contains("youtube wale app") ||
                lower.contains("youtube ki app") ||
                lower.contains("youtube ke app") ||
                (lower.contains("app par") && lower.contains("youtube")) ||
                (lower.contains("app pe") && lower.contains("youtube")) ||
                (lower.contains("app me") && lower.contains("youtube")) ||
                (lower.contains("app mein") && lower.contains("youtube")) ||
                (lower.contains("app kholo") && lower.contains("youtube"))
    }

    /**
     * Used by tool-call paths (Gemini Live tools, plugins, agent executor) that receive a bare
     * song name. Returns true ONLY when the caller explicitly wants the external YouTube app
     * (either via a destination argument or an explicit phrase in the query). Everything else
     * plays in MYRA's Internal Browser, which supports background/lock-screen playback.
     * The external YouTube app stops playing when minimized (unless the user has Premium).
     */
    fun wantsYouTubeApp(rawQuery: String, destinationArg: String = ""): Boolean {
        val d = destinationArg.lowercase().trim()
        if (d == "app" || d.contains("youtube_app") || d.contains("youtube app") || d == "external") return true
        return isExplicitYouTubeApp(rawQuery.lowercase().trim())
    }

    /**
     * True ONLY if user explicitly asked for Chrome.
     * e.g. "Chrome par search karo", "Chrome app kholo", "Chrome mein open karo"
     */
    fun isExplicitChrome(lower: String): Boolean {
        return lower.contains("chrome")
    }
}
