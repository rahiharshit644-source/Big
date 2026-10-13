package com.soltini.app.agent

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONObject

/**
 * SpotifyAutomator - "Spotify pe <gaana> chalao".
 * Uses Spotify's own play-from-search entry point first (starts playback directly), then falls back to the
 * in-app search page + Play button. Success is verified by checking that audio is really playing.
 */
class SpotifyAutomator(private val context: Context) {

    private val pkg = "com.spotify.music"
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun ok(vararg p: Any) = JSONObject().apply { var i = 0; while (i < p.size - 1) { put(p[i].toString(), p[i + 1]); i += 2 } }
    private fun err(m: String) = JSONObject().apply { put("status", "error"); put("error", m) }

    private fun installed() = try { context.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }

    private fun waitMusic(ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (audio.isMusicActive) return true
            try { Thread.sleep(500) } catch (_: InterruptedException) { return false }
        }
        return audio.isMusicActive
    }

    fun play(query: String): JSONObject {
        val q = query.trim()
        if (q.isBlank()) return err("query is required (song, artist, album or playlist)")
        if (!installed()) {
            return AppManager(context).installApp("spotify").apply {
                put("note", "Spotify was not installed. After it is installed and you are logged in, ask again.")
            }
        }

        // 1) direct play-from-search
        try {
            val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage(pkg)
                putExtra(SearchManager.QUERY, q)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
            if (waitMusic(9_000)) return ok("status", "playing", "query", q, "app", "Spotify")
        } catch (_: Exception) { /* fall through */ }

        // 2) in-app search page + Play
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(q)}")).apply {
                    setPackage(pkg); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            return err("Could not open Spotify search: ${e.message}")
        }
        val svc = SoltiniAccessibilityService.getInstance()
            ?: return ok("status", "search_opened", "message", "Spotify search for '$q' is open. Accessibility is off, so tap the result yourself.")
        try { Thread.sleep(3_000) } catch (_: InterruptedException) {}
        svc.clickExactText(listOf("Play", "Shuffle play"))
        return if (waitMusic(7_000)) ok("status", "playing", "query", q, "app", "Spotify")
        else ok("status", "search_opened", "message", "Spotify search is open but I could not confirm playback. The agent can tap the right result.")
    }
}
