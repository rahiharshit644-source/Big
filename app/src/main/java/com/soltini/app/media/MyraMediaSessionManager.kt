package com.soltini.app.media

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import com.soltini.app.browser.BrowserController

/**
 * MyraMediaSessionManager
 *
 * Coordinates system MediaSession, audio focus, notification controls,
 * lock-screen controls, and Bluetooth/headset integration for MYRA's Internal Browser.
 * Prevents duplicate MediaSessions and memory leaks.
 */
class MyraMediaSessionManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "MyraMediaSession"
        private const val SESSION_TAG = "MyraBrowserMediaSession"

        @Volatile
        private var instance: MyraMediaSessionManager? = null

        fun getInstance(context: Context): MyraMediaSessionManager {
            return instance ?: synchronized(this) {
                instance ?: MyraMediaSessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var mediaSession: MediaSession? = null
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    /** True when WE paused because another app took focus (so we may auto-resume on regain). */
    @Volatile
    private var pausedByFocusLoss = false

    @Volatile
    private var currentTitle: String = "Myra Media"

    @Volatile
    private var isPlaying: Boolean = false

    init {
        initMediaSession()
    }

    private fun initMediaSession() {
        if (mediaSession != null) return
        try {
            mediaSession = MediaSession(appContext, SESSION_TAG).apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        Log.d(TAG, "MediaSession.onPlay received")
                        val controller = BrowserController.getInstance(appContext)
                        controller.resumeMediaDirect()
                    }

                    override fun onPause() {
                        Log.d(TAG, "MediaSession.onPause received")
                        val controller = BrowserController.getInstance(appContext)
                        controller.pauseMediaDirect()
                    }

                    override fun onSkipToNext() {
                        Log.d(TAG, "MediaSession.onSkipToNext received")
                        val controller = BrowserController.getInstance(appContext)
                        controller.nextMediaDirect()
                    }

                    override fun onSkipToPrevious() {
                        Log.d(TAG, "MediaSession.onSkipToPrevious received")
                        val controller = BrowserController.getInstance(appContext)
                        controller.previousMediaDirect()
                    }

                    override fun onStop() {
                        Log.d(TAG, "MediaSession.onStop received")
                        val controller = BrowserController.getInstance(appContext)
                        controller.stopMediaDirect()
                    }

                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }
                        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    onPlay()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    onPause()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                                    if (isPlaying) onPause() else onPlay()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    onSkipToNext()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                    onSkipToPrevious()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_STOP -> {
                                    onStop()
                                    return true
                                }
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaSession", e)
        }
    }

    fun getSessionToken(): MediaSession.Token? = mediaSession?.sessionToken

    /**
     * Called when media starts playing in Internal Browser.
     */
    fun onMediaStarted(title: String, artist: String = "Myra Internal Browser") {
        currentTitle = title.ifBlank { "Playing Media" }
        isPlaying = true
        pausedByFocusLoss = false
        updateSessionState(true, currentTitle, artist)
        MyraPlaybackContext.updatePlayback(isPlaying = true, title = currentTitle)

        // 1) Start the foreground service FIRST. On Android 15/16 a background app's audio focus
        //    request is rejected unless the app already has a mediaPlayback foreground service.
        startOrUpdateService(currentTitle, artist, true)
        // 2) Then ask for audio focus (the service also retries once it is in the foreground).
        requestAudioFocus()
    }

    /**
     * Called when media playback is paused in Internal Browser.
     */
    fun onMediaPaused() {
        isPlaying = false
        updateSessionState(false, currentTitle)
        MyraPlaybackContext.updatePlayback(isPlaying = false)
        // Service is already running while media is active; talk to it directly (no background startService()).
        MyraMediaPlaybackService.updateState(playing = false)
    }

    /**
     * Called when media playback is resumed in Internal Browser.
     */
    fun onMediaResumed() {
        isPlaying = true
        pausedByFocusLoss = false
        updateSessionState(true, currentTitle)
        MyraPlaybackContext.updatePlayback(isPlaying = true)
        if (MyraMediaPlaybackService.isRunning()) {
            MyraMediaPlaybackService.updateState(playing = true)
        } else {
            // Service was stopped/killed meanwhile -> bring it back as a proper foreground service.
            startOrUpdateService(currentTitle, "Myra Internal Browser", true)
        }
        requestAudioFocus()
    }

    /**
     * Called when media playback is stopped or closed.
     */
    fun onMediaStopped() {
        isPlaying = false
        pausedByFocusLoss = false
        abandonAudioFocus()
        updateSessionState(false, currentTitle)
        MyraPlaybackContext.updatePlayback(isPlaying = false)
        // Direct shutdown: no ACTION_STOP intent round-trip (that used to loop forever).
        MyraMediaPlaybackService.shutdown()
    }

    /** Re-request audio focus if we are supposed to be playing but don't hold it (called by the service). */
    fun ensureAudioFocus() {
        if (isPlaying) requestAudioFocus()
    }

    private fun startOrUpdateService(title: String, artist: String, playing: Boolean) {
        if (MyraMediaPlaybackService.isRunning()) {
            MyraMediaPlaybackService.updateState(title, artist, playing)
            return
        }
        try {
            val serviceIntent = Intent(appContext, MyraMediaPlaybackService::class.java).apply {
                action = MyraMediaPlaybackService.ACTION_START
                putExtra(MyraMediaPlaybackService.EXTRA_TITLE, title)
                putExtra(MyraMediaPlaybackService.EXTRA_ARTIST, artist)
                putExtra(MyraMediaPlaybackService.EXTRA_IS_PLAYING, playing)
            }
            appContext.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start MyraMediaPlaybackService (background playback will NOT survive minimize): ${e.message}", e)
        }
    }

    private fun updateSessionState(playing: Boolean, title: String, artist: String = "Myra Internal Browser") {
        val session = mediaSession ?: return

        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                if (playing) 1.0f else 0.0f
            )

        session.setPlaybackState(stateBuilder.build())

        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .build()

        session.setMetadata(metadata)
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val controller = BrowserController.getInstance(appContext)
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss (e.g. another music app). Pause and do NOT auto-resume.
                Log.d(TAG, "Audio focus lost permanently, pausing media")
                hasAudioFocus = false
                pausedByFocusLoss = false
                controller.pauseMediaDirect()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Phone call / assistant beep / notification sound. Pause, remember, resume on GAIN.
                Log.d(TAG, "Audio focus lost transiently, pausing media")
                if (isPlaying) {
                    pausedByFocusLoss = true
                    controller.pauseMediaDirect()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Short sound (navigation prompt etc.). Keep playing; system ducks the volume itself.
                Log.d(TAG, "Audio focus: can duck, ignoring")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus regained")
                hasAudioFocus = true
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    controller.resumeMediaDirect()
                }
            }
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        try {
            val playbackAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttrs)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
                .also { audioFocusRequest = it }

            val res = audioManager.requestAudioFocus(req)
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                Log.w(TAG, "Audio focus request FAILED (app probably in background without media FGS yet)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed requesting audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {}
        audioFocusRequest = null
        hasAudioFocus = false
    }

    fun release() {
        abandonAudioFocus()
        mediaSession?.release()
        mediaSession = null
    }
}
