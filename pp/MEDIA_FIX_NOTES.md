# Background music fix — changes in this build

## Root causes found
1. **Voice/Gemini `youtube_play` opened the YouTube *app*** (via `YouTubeAutomator.playSong` -> VIEW intent).
   The YouTube app pauses when minimized / screen locks (no Premium). Only the text-command path used the
   Internal Browser + foreground media service. -> All 3 call sites now default to the Internal Browser
   (`playMediaOnYouTube`); the YouTube app is used only if explicitly requested
   (`DestinationResolver.wantsYouTubeApp`).
2. **Play/Pause double-toggle**: `pauseMediaDirect/resumeMediaDirect` did `video.pause()/play()` and then ALSO
   clicked the player button, toggling it back. Now it's `if video -> play/pause else -> click button`.
3. **Infinite service loop on Stop**: service ACTION_STOP -> stopMediaDirect -> onMediaStopped -> startService(ACTION_STOP) -> ...
   State/stop are now delivered directly to the running service instance (no intent round-trip).
4. **`startService()` from background** for pause/resume/stop updates throws on Android 8+ (silently swallowed) -> notification/service state went stale. Fixed (see 3).
5. **Audio focus**: transient loss (call, notification, assistant) paused music but it never resumed. Now
   LOSS_TRANSIENT -> pause + auto-resume on GAIN; CAN_DUCK is ignored; LOSS -> pause, no auto-resume.
   Also the foreground service is now started *before* requesting focus (Android 15/16 rejects focus requests from
   background apps without a mediaPlayback FGS).
6. **Page-visibility spoof injected too late** (only `onPageFinished`). Now injected in `onPageStarted`,
   `onPageCommitVisible`, `onPageFinished`, and it also swallows `visibilitychange/pagehide/blur/freeze` events.
7. **No WakeLock/WifiLock** -> audio could stop/stutter after screen off. Service now holds both (6h safety timeout).
8. `ensureBackgroundPlayback()` was never effective and forced "playing" state; `MainActivity.onStop()` now calls
   `keepWebViewAlive()` when music is playing.
9. `Notification.Builder.style = ...` (no public getter -> likely compile error) replaced with `setStyle(...)`.
10. `START_STICKY` -> `START_NOT_STICKY` (a restarted service without a WebView is just a ghost notification).

## Files changed
media/MyraMediaPlaybackService.kt, media/MyraMediaSessionManager.kt, browser/BrowserController.kt,
services/BackgroundVoiceService.kt, agent/AgentToolExecutor.kt, plugins/BuiltinPluginInitializer.kt,
orchestrator/DestinationResolver.kt, MainActivity.kt

## NOT verified
No Android SDK / internet here, so I could NOT compile or run this. Build in Android Studio and send me any errors / logcat.

## Device settings that still matter (esp. Xiaomi/POCO/Redmi/Oppo/Vivo)
Battery -> "No restrictions" for this app, Autostart ON, lock the app in Recents, notifications allowed (Android 13+).

## Security warnings
- `.env` contains a GEMINI_API_KEY and it is inside the zip. Rotate that key and never share/commit it.
- `debug.keystore` is committed on purpose (CI), but do not use it for release builds.
