package com.soltini.app.agent

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import kotlin.math.ceil

/**
 * SpotifyAutomator - "Spotify pe <gaana> chalao".
 *
 * Flow (v2 - fixes "searches but never clicks"):
 *  1. Spotify's own play-from-search entry point (starts playback directly on most devices).
 *  2. If that did not start anything: open Spotify's search page, WAIT until results are drawn,
 *     then tap the best matching result row (the old code only looked for a "Play" button, which does
 *     not exist on the search-results page, so nothing was ever clicked).
 *  3. If the tapped result was an artist / album / playlist page, press its Play / Shuffle-play button.
 *  4. Success is verified through Spotify's own MediaSession (not just "some app is playing audio"),
 *     and a track change is required when Spotify was already playing something else.
 */
class SpotifyAutomator(private val context: Context) {

    companion object {
        private const val TAG = "SpotifyAutomator"
        private const val PKG = "com.spotify.music"

        /** Labels that are tabs / chips / chrome of the Spotify UI, never a song row. */
        private val NON_RESULT = setOf(
            "all", "songs", "artists", "albums", "playlists", "podcasts & shows", "podcasts", "shows",
            "profiles", "genres", "audiobooks", "top result", "search", "home", "your library",
            "premium", "create", "cancel", "clear", "back", "navigate up", "recent searches",
            "what do you want to listen to?", "what do you want to play?"
        )

        /** Row sub-buttons that also repeat the song title in their description - must not be tapped. */
        private val BAD_SUBSTRINGS = listOf(
            "more options", "add to playlist", "add to queue", "add to liked songs", "save to",
            "start radio", "dismiss"
        )

        private val STOP_WORDS = setOf(
            "ka", "ki", "ke", "ko", "ek", "the", "a", "an", "of", "song", "songs", "gaana", "gaane",
            "gana", "music", "track", "by", "from", "wala", "wali", "wale", "mera", "meri", "mujhe"
        )

        private val PLAY_LABELS = listOf("play", "shuffle play", "play button", "play playlist", "play album", "play artist")
    }

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun ok(vararg p: Any) = JSONObject().apply { var i = 0; while (i < p.size - 1) { put(p[i].toString(), p[i + 1]); i += 2 } }
    private fun err(m: String) = JSONObject().apply { put("status", "error"); put("error", m); put("message", m) }

    private fun installed() = try { context.packageManager.getPackageInfo(PKG, 0); true } catch (_: Exception) { false }

    private fun sleep(ms: Long): Boolean = try { Thread.sleep(ms); true } catch (_: InterruptedException) { false }

    // ─── Playback verification ───────────────────────────────────────────────

    /** Spotify's active media session, or null if the list is unavailable (listener access missing). */
    private fun spotifySession(): Pair<Boolean, MediaController?> {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(context, com.soltini.app.notifications.SoltiniNotificationListener::class.java)
            val list = msm.getActiveSessions(cn)
            true to list.firstOrNull { it.packageName == PKG }
        } catch (e: SecurityException) {
            false to null   // notification-listener access not granted -> cannot inspect sessions
        } catch (e: Exception) {
            false to null
        }
    }

    private fun spotifyIsPlaying(): Boolean {
        val (sessionsAvailable, controller) = spotifySession()
        if (!sessionsAvailable) return audio.isMusicActive            // best effort fallback
        val st = controller?.playbackState?.state ?: return false
        return st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING || st == PlaybackState.STATE_CONNECTING
    }

    private fun currentTrack(): String? {
        val md = spotifySession().second?.metadata ?: return null
        return md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
    }

    /** Waits until Spotify plays (and, if it was already playing, until the track actually changed). */
    private fun waitPlaying(ms: Long, trackBefore: String?, wasPlayingBefore: Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (spotifyIsPlaying()) {
                val now = currentTrack()
                if (!wasPlayingBefore || trackBefore == null || now == null || now != trackBefore) return true
            }
            if (!sleep(500)) return false
        }
        return false
    }

    // ─── UI helpers ──────────────────────────────────────────────────────────

    private fun tokens(q: String): List<String> =
        q.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 && it !in STOP_WORDS }.distinct()

    private fun aggregateLabel(node: AccessibilityNodeInfo, depth: Int, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        if (depth >= 4) return
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            aggregateLabel(c, depth + 1, sb)
            try { c.recycle() } catch (_: Exception) {}
        }
    }

    private class Cand(val node: AccessibilityNodeInfo, val score: Int, val top: Int)

    private fun scan(node: AccessibilityNodeInfo, toks: List<String>, minTop: Int, maxHeight: Int,
                     out: MutableList<Cand>, depth: Int) {
        if (depth > 35) return
        if (node.isVisibleToUser && node.isClickable && !node.isEditable) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.top >= minTop && r.width() > 0 && r.height() in 1..maxHeight) {
                val sb = StringBuilder()
                aggregateLabel(node, 0, sb)
                val label = sb.toString().lowercase().trim()
                if (label.isNotEmpty() && label !in NON_RESULT && BAD_SUBSTRINGS.none { label.startsWith(it) }) {
                    val score = toks.count { label.contains(it) }
                    if (score > 0) out.add(Cand(AccessibilityNodeInfo.obtain(node), score, r.top))
                }
            }
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            scan(c, toks, minTop, maxHeight, out, depth + 1)
            try { c.recycle() } catch (_: Exception) {}
        }
    }

    /** Taps the best matching result row on Spotify's search page. Returns the row's label or null. */
    private fun clickBestResult(svc: SoltiniAccessibilityService, query: String): Boolean {
        val toks = tokens(query)
        if (toks.isEmpty()) return false
        val minScore = maxOf(1, ceil(toks.size / 2.0).toInt())
        val screenH = context.resources.displayMetrics.heightPixels
        val minTop = (screenH * 0.14).toInt()          // skip the search bar / header
        val maxHeight = (screenH * 0.30).toInt()       // skip big containers that hold many rows

        val cands = mutableListOf<Cand>()
        svc.withRootNodes { roots ->
            for (root in roots) {
                if (root.packageName?.toString() != PKG) continue
                scan(root, toks, minTop, maxHeight, cands, 0)
            }
        }
        val best = cands.filter { it.score >= minScore }
            .sortedWith(compareByDescending<Cand> { it.score }.thenBy { it.top })
            .firstOrNull()
        val clicked = if (best != null) svc.clickNode(AccessibilityNodeInfo.obtain(best.node)) else false
        for (c in cands) { try { c.node.recycle() } catch (_: Exception) {} }
        Log.i(TAG, "clickBestResult('$query') -> candidates=${cands.size}, clicked=$clicked")
        return clicked
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    fun play(query: String): JSONObject {
        val q = query.trim()
        if (q.isBlank()) return err("query is required (song, artist, album or playlist)")
        if (!installed()) {
            return AppManager(context).installApp("spotify").apply {
                put("note", "Spotify was not installed. After it is installed and you are logged in, ask again.")
            }
        }

        val wasPlaying = spotifyIsPlaying()
        val trackBefore = currentTrack()

        // 1) direct play-from-search
        try {
            val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage(PKG)
                putExtra(SearchManager.QUERY, q)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
            if (waitPlaying(7_000, trackBefore, wasPlaying)) return ok("status", "playing", "query", q, "app", "Spotify")
        } catch (e: Exception) {
            Log.w(TAG, "play-from-search failed: ${e.message}")
        }

        // 2) open the search page and tap the right result
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(q)}")).apply {
                    setPackage(PKG); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            return err("Could not open Spotify search: ${e.message}")
        }

        val svc = SoltiniAccessibilityService.getInstance()
            ?: return ok("status", "search_opened",
                "message", "Spotify search for '$q' is open. Accessibility is off, so tap the result yourself.")

        // wait for the results list to actually render, then tap the best row (retry for ~9s)
        sleep(1_800)
        var tapped = false
        var tries = 0
        while (!tapped && tries < 9) {
            tapped = clickBestResult(svc, q)
            if (!tapped) { tries++; if (!sleep(1_000)) break }
        }

        if (tapped) {
            if (waitPlaying(5_000, trackBefore, wasPlaying)) return ok("status", "playing", "query", q, "app", "Spotify")

            // 3) we probably landed on an artist / album / playlist page -> press its Play button
            sleep(800)
            if (svc.clickExactText(PLAY_LABELS) && waitPlaying(6_000, trackBefore, wasPlaying)) {
                return ok("status", "playing", "query", q, "app", "Spotify")
            }
        } else {
            // nothing matched the query text: last resort, press any visible Play button
            if (svc.clickExactText(PLAY_LABELS) && waitPlaying(6_000, trackBefore, wasPlaying)) {
                return ok("status", "playing", "query", q, "app", "Spotify")
            }
        }

        return ok("status", "search_opened",
            "message", "Spotify par '$q' search khul gaya, par playback confirm nahi ho paya (Free account ya login check karein).")
    }
}
