package com.soltini.app.orchestrator

/**
 * AppDestination
 *
 * Defines the execution destination for web, search, video, and music intents.
 *
 * NEW DEFAULTS (v2):
 *   - Web search / website / link  -> CHROME_APP        (real Chrome, default browser as fallback)
 *   - Songs / videos / YouTube     -> YOUTUBE_APP       (real YouTube app)
 *   - "Spotify ..."                -> handled by SpotifyAutomator (see MyraCommandParser.parseSpotify)
 *   - MYRA Internal Browser        -> ONLY when the user explicitly asks for it
 *                                     ("internal browser mein", "myra browser par", ...).
 */
enum class AppDestination {
    INTERNAL_BROWSER,
    CHROME_APP,
    YOUTUBE_APP
}

/**
 * DestinationResolver
 *
 * Routing priority (highest first):
 *   1. Explicit "internal browser" request  -> INTERNAL_BROWSER
 *   2. Explicit "chrome" request            -> CHROME_APP
 *   3. Media intent (song / video / YouTube)-> YOUTUBE_APP
 *   4. Web intent (search / site / link)    -> CHROME_APP
 */
object DestinationResolver {

    /** Destination for song / video / YouTube commands. Default = YouTube app. */
    fun resolveMedia(input: String): AppDestination {
        val lower = input.lowercase().trim()
        if (isExplicitInternalBrowser(lower)) return AppDestination.INTERNAL_BROWSER
        if (isExplicitChrome(lower)) return AppDestination.CHROME_APP
        return AppDestination.YOUTUBE_APP
    }

    /** Destination for web search / website / link commands. Default = Chrome. */
    fun resolveWeb(input: String): AppDestination {
        val lower = input.lowercase().trim()
        if (isExplicitInternalBrowser(lower)) return AppDestination.INTERNAL_BROWSER
        return AppDestination.CHROME_APP
    }

    /**
     * Generic resolver (kept for backwards compatibility).
     * Media-looking text -> [resolveMedia]; everything else -> [resolveWeb].
     */
    fun resolve(input: String): AppDestination {
        val lower = input.lowercase().trim()
        val looksLikeMedia = lower.contains("youtube") || lower.contains("song") ||
                lower.contains("gaana") || lower.contains("gaane") || lower.contains("video") ||
                lower.contains("music") || lower.contains("bajao") || lower.contains("sunao")
        return if (looksLikeMedia) resolveMedia(input) else resolveWeb(input)
    }

    /** True ONLY if the user explicitly asked for MYRA's built-in browser. */
    fun isExplicitInternalBrowser(lower: String): Boolean {
        return lower.contains("internal browser") ||
                lower.contains("myra browser") ||
                lower.contains("myra ke browser") ||
                lower.contains("in-app browser") ||
                lower.contains("inapp browser") ||
                lower.contains("andar wale browser") ||
                lower.contains("andar ke browser") ||
                lower.contains("apne browser") ||
                lower.contains("internal browser") ||
                lower.contains("webview")
    }

    /** True if the user explicitly asked for the external YouTube application. */
    fun isExplicitYouTubeApp(lower: String): Boolean {
        if (!lower.contains("youtube")) return false
        return lower.contains("youtube app") ||
                lower.contains("youtube application") ||
                lower.contains("youtube apk") ||
                lower.contains("youtube wale app") ||
                lower.contains("youtube ki app") ||
                lower.contains("youtube ke app") ||
                lower.contains("app par") || lower.contains("app pe") ||
                lower.contains("app me") || lower.contains("app mein") ||
                lower.contains("app kholo")
    }

    /**
     * Tool-call paths (Gemini Live tools, plugins, agent executor) use this to decide whether a
     * song should play in MYRA's Internal Browser. That now happens ONLY on an explicit request
     * (destination argument or a phrase in the query). Everything else plays in the YouTube app.
     */
    fun wantsInternalBrowser(rawQuery: String, destinationArg: String = ""): Boolean {
        val d = destinationArg.lowercase().trim()
        if (d == "internal" || d == "internal_browser" || d == "browser" || d == "myra_browser") return true
        return isExplicitInternalBrowser(rawQuery.lowercase().trim())
    }

    /**
     * Kept so older call sites still compile. Now simply the inverse of [wantsInternalBrowser]:
     * the YouTube app is the default for every song/video unless the Internal Browser is explicitly requested.
     */
    fun wantsYouTubeApp(rawQuery: String, destinationArg: String = ""): Boolean =
        !wantsInternalBrowser(rawQuery, destinationArg)

    /** True if the user explicitly asked for Chrome. */
    fun isExplicitChrome(lower: String): Boolean = lower.contains("chrome")

    /** True if the user explicitly named Spotify. */
    fun isExplicitSpotify(lower: String): Boolean = lower.contains("spotify") || lower.contains("spotifai") || lower.contains("spotifi")
}
