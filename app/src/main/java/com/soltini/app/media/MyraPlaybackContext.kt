package com.soltini.app.media

import com.soltini.app.orchestrator.AppDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlaybackStateInfo
 *
 * Remembers current playback destination, media title, URL, and playing state
 * for context-aware voice commands (e.g. "Pause karo", "Resume karo", "Next song").
 */
data class PlaybackStateInfo(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "Myra Internal Browser",
    val url: String = "",
    val destination: AppDestination = AppDestination.INTERNAL_BROWSER,
    val timestamp: Long = System.currentTimeMillis()
)

object MyraPlaybackContext {
    private val _state = MutableStateFlow(PlaybackStateInfo())
    val state: StateFlow<PlaybackStateInfo> = _state.asStateFlow()

    fun updatePlayback(
        isPlaying: Boolean,
        title: String? = null,
        artist: String? = null,
        url: String? = null,
        destination: AppDestination = AppDestination.INTERNAL_BROWSER
    ) {
        val curr = _state.value
        _state.value = curr.copy(
            isPlaying = isPlaying,
            title = if (!title.isNullOrBlank()) title else curr.title,
            artist = if (!artist.isNullOrBlank()) artist else curr.artist,
            url = if (!url.isNullOrBlank()) url else curr.url,
            destination = destination,
            timestamp = System.currentTimeMillis()
        )
    }

    fun isPlaying(): Boolean = _state.value.isPlaying
    fun getCurrentTitle(): String = _state.value.title
    fun getCurrentDestination(): AppDestination = _state.value.destination
    fun getSnapshot(): PlaybackStateInfo = _state.value
}
