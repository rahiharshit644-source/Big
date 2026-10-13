package com.soltini.app.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.soltini.app.MainActivity
import com.soltini.app.browser.BrowserController

/**
 * MyraMediaPlaybackService
 *
 * Foreground service that keeps the process (and the WebView playing audio) alive while
 * MYRA is minimized or the screen is off, and shows notification / lock-screen controls.
 *
 * FIX NOTES (vs. previous version):
 *  - State updates / stop are now delivered through the running instance (see [updateState] /
 *    [shutdown]) instead of startService() intents. The old approach (a) threw
 *    IllegalStateException when the app was in the background and (b) caused an endless
 *    ACTION_STOP -> stopMediaDirect -> onMediaStopped -> ACTION_STOP loop.
 *  - startForeground() is ALWAYS called first for every intent that arrives through
 *    startForegroundService(), otherwise Android kills the app after 5 s.
 *  - Holds a PARTIAL_WAKE_LOCK + Wi-Fi lock so audio does not stutter/stop after the screen turns off.
 *  - START_NOT_STICKY: a restarted service without a WebView would just be a ghost notification.
 *  - Notification.Builder.style property replaced with setStyle() (no public getter).
 */
class MyraMediaPlaybackService : Service() {

    companion object {
        private const val TAG = "MyraMediaService"
        const val CHANNEL_ID = "myra_media_playback_channel"
        const val NOTIFICATION_ID = 2048

        const val ACTION_START = "com.soltini.app.media.ACTION_START"
        const val ACTION_UPDATE_STATE = "com.soltini.app.media.ACTION_UPDATE_STATE"
        const val ACTION_PLAY = "com.soltini.app.media.ACTION_PLAY"
        const val ACTION_PAUSE = "com.soltini.app.media.ACTION_PAUSE"
        const val ACTION_NEXT = "com.soltini.app.media.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.soltini.app.media.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.soltini.app.media.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        private const val WAKELOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L // safety net: 6 hours

        @Volatile
        private var instance: MyraMediaPlaybackService? = null

        fun isRunning(): Boolean = instance != null

        /** Update notification/state on the running service. Returns false if service is not running. */
        fun updateState(title: String? = null, artist: String? = null, playing: Boolean? = null): Boolean {
            val s = instance ?: return false
            s.mainHandler.post { s.applyState(title, artist, playing) }
            return true
        }

        /** Stop the running service (no intent round-trip, so no restart loops). */
        fun shutdown(): Boolean {
            val s = instance ?: return false
            s.mainHandler.post { s.stopServiceInternal() }
            return true
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentTitle: String = "Media Playing"
    private var currentArtist: String = "Myra Internal Browser"
    private var isPlaying: Boolean = true

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        // Must happen first: we may have been started with startForegroundService().
        // Refresh extras before building so the very first notification is already correct.
        if (action == ACTION_START) {
            currentTitle = intent?.getStringExtra(EXTRA_TITLE) ?: currentTitle
            currentArtist = intent?.getStringExtra(EXTRA_ARTIST) ?: currentArtist
            isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true
        } else if (action == ACTION_UPDATE_STATE) {
            isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying) ?: isPlaying
        }
        if (!startForegroundWithNotification()) {
            // Could not become a foreground service (e.g. background start not allowed).
            // Don't leave a half-alive service around.
            releaseLocks()
            stopSelf()
            return START_NOT_STICKY
        }
        acquireLocks()

        when (action) {
            ACTION_START -> {
                // Make sure the WebView keeps running now that the UI may go away.
                BrowserController.getInstance(this).keepWebViewAlive()
                MyraMediaSessionManager.getInstance(this).ensureAudioFocus()
            }
            ACTION_UPDATE_STATE -> updateNotification()
            ACTION_PLAY -> BrowserController.getInstance(this).resumeMediaDirect()
            ACTION_PAUSE -> BrowserController.getInstance(this).pauseMediaDirect()
            ACTION_NEXT -> BrowserController.getInstance(this).nextMediaDirect()
            ACTION_PREVIOUS -> BrowserController.getInstance(this).previousMediaDirect()
            ACTION_STOP -> {
                // stopMediaDirect() -> onMediaStopped() -> MyraMediaPlaybackService.shutdown()
                BrowserController.getInstance(this).stopMediaDirect()
                return START_NOT_STICKY
            }
        }

        return START_NOT_STICKY
    }

    private fun applyState(title: String?, artist: String?, playing: Boolean?) {
        if (!title.isNullOrBlank()) currentTitle = title
        if (!artist.isNullOrBlank()) currentArtist = artist
        if (playing != null) isPlaying = playing
        updateNotification()
    }

    private fun startForegroundWithNotification(): Boolean {
        val notification = buildMediaNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            false
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildMediaNotification())
    }

    private fun acquireLocks() {
        try {
            if (wakeLock?.isHeld != true) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Myra:MediaPlayback").apply {
                    setReferenceCounted(false)
                    acquire(WAKELOCK_TIMEOUT_MS)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock failed: ${e.message}")
        }
        try {
            if (wifiLock?.isHeld != true) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
                wifiLock = wm.createWifiLock(mode, "Myra:MediaPlayback").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiLock failed: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}
        wakeLock = null
        wifiLock = null
    }

    private fun buildMediaNotification(): Notification {
        val sessionManager = MyraMediaSessionManager.getInstance(this)
        val sessionToken = sessionManager.getSessionToken()

        // Content intent: Opens MainActivity to Browser screen
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("screen", "browser")
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPendingIntent = PendingIntent.getActivity(this, 101, contentIntent, piFlags)

        fun serviceAction(requestCode: Int, act: String): PendingIntent =
            PendingIntent.getForegroundService(
                this, requestCode,
                Intent(this, MyraMediaPlaybackService::class.java).apply { action = act },
                piFlags
            )

        val prevPendingIntent = serviceAction(102, ACTION_PREVIOUS)
        val playPausePendingIntent = serviceAction(103, if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
        val nextPendingIntent = serviceAction(104, ACTION_NEXT)
        val stopPendingIntent = serviceAction(105, ACTION_STOP)

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        builder.addAction(
            Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent).build()
        )
        builder.addAction(
            Notification.Action.Builder(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            ).build()
        )
        builder.addAction(
            Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", nextPendingIntent).build()
        )
        builder.addAction(
            Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent).build()
        )

        val mediaStyle = Notification.MediaStyle()
        if (sessionToken != null) {
            mediaStyle.setMediaSession(sessionToken)
        }
        mediaStyle.setShowActionsInCompactView(0, 1, 2)
        builder.setStyle(mediaStyle)

        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Myra Media Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls background media playback in Myra Internal Browser"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun stopServiceInternal() {
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        releaseLocks()
        super.onDestroy()
    }
}
