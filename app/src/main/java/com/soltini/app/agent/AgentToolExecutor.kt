package com.soltini.app.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * AgentToolExecutor
 *
 * Central dispatcher for all Gemini tool calls. Receives (toolName, args) from
 * BackgroundVoiceService.onToolCall() and routes to the appropriate Android API.
 *
 * ┌── Device Control ─────────────────────────────────────────────────────────┐
 * │  open_app(app_name)           — launch any installed app by name         │
 * │  lock_device()                — lock screen via Accessibility             │
 * │  wake_device()                — turn screen on via WakeLock              │
 * │  sleep_agent()                — handled by BackgroundVoiceService        │
 * │  press_back()                 — back button                              │
 * │  press_home()                 — home button                              │
 * │  press_recents()              — recent apps                              │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ┌── Screen Automation ───────────────────────────────────────────────────────┐
 * │  read_screen()                — returns all visible UI text              │
 * │  click_element(text)          — click the UI element with matching text  │
 * │  tap_screen(x, y)             — tap at pixel coordinates                 │
 * │  swipe_screen(x1,y1,x2,y2)   — swipe/drag gesture                       │
 * │  type_text(text)              — type into focused input field            │
 * │  clear_text()                 — clear the focused input field            │
 * │  scroll_down()                — scroll the main view down                │
 * │  scroll_up()                  — scroll the main view up                  │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ┌── YouTube Automation ──────────────────────────────────────────────────────┐
 * │  youtube_search, youtube_open_video, youtube_play_pause, youtube_seek,   │
 * │  youtube_next, youtube_previous, youtube_like, youtube_dislike,          │
 * │  youtube_subscribe, youtube_skip_ad, youtube_set_quality,                │
 * │  youtube_fullscreen, youtube_mute, youtube_captions, read_youtube_screen │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ┌── WhatsApp & Reels Automation ─────────────────────────────────────────────┐
 * │  whatsapp_send_message, whatsapp_call, next_reel, previous_reel          │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ┌── Maps & Email Automation ─────────────────────────────────────────────────┐
 * │  maps_navigate(destination, mode)  — turn-by-turn navigation (Intent-based)│
 * │  maps_search(query)                — show a place on the map              │
 * │  send_email(to, subject, body, auto_send) — compose via Gmail; auto_send  │
 * │  defaults to false so Boss reviews before sending (irreversible action)   │
 * └───────────────────────────────────────────────────────────────────────────┘
 */
class AgentToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "AgentToolExecutor"
    }

    private val youtubeAutomator = YouTubeAutomator(context)
    private val whatsappAutomator = WhatsAppAutomator(context)
    private val appManager = AppManager(context)
    private val fileManager = DeviceFileManager(context)
    private val whatsappDirect = WhatsAppDirect(context)
    private val contactAliases = ContactAliasStore(context)
    private val phoneSettings = PhoneSettingsController(context)
    private val instagramAutomator = InstagramAutomator(context)
    private val reelsAutomator = ReelsAutomator(context)
    private val mapsAutomator = MapsAutomator(context)
    private val gmailAutomator = GmailAutomator(context)
    private val deviceHardware = DeviceHardwareController(context)
    private val publicApiRegistry = com.soltini.app.apis.PublicApiRegistry.getInstance(context)
    private val publicApiExecutor = com.soltini.app.apis.PublicApiExecutor(context)
    val screenOperator = ScreenOperator(context)
    val formAutomator = FormAutomator(context)
    val screenAnalyzer = ScreenAnalyzer(context)

    /**
     * Main dispatch function. Returns a JSONObject result sent back to Gemini
     * as a toolResponse so it can verbally confirm what happened.
     */
    fun execute(toolName: String, args: JSONObject): JSONObject {
        val result = executeInternal(toolName, args)
        // Phase 4: learn favourites / routines from what actually worked (never throws)
        com.soltini.app.learning.HabitTracker.record(context, toolName, args, result)
        return result
    }

    private fun executeInternal(toolName: String, args: JSONObject): JSONObject {
        Log.i(TAG, "Executing tool: $toolName | args: $args")
        return when (toolName) {
            // ── Device Control & Web / Search ────────────────────────────
            "open_app"      -> appManager.openApp(args.optString("app_name", ""), args.optBoolean("install_if_missing", true))
            "install_app"   -> appManager.installApp(args.optString("app_name", args.optString("package", "")))
            "uninstall_app" -> appManager.uninstallApp(args.optString("app_name", ""), args.optBoolean("confirmed", false))
            "list_apps"     -> appManager.listApps(args.optString("query", ""), args.optInt("limit", 40))
            "open_app_settings" -> appManager.openAppSettings(args.optString("app_name", ""))
            "open_settings" -> phoneSettings.openSettings(args.optString("page", "settings"))
            "set_brightness" -> phoneSettings.setBrightness(
                if (args.has("percent")) args.optInt("percent") else null,
                args.optString("change", "").ifBlank { null }
            )
            "open_urls"     -> {
                val arr = args.optJSONArray("urls")
                val list = if (arr != null) List(arr.length()) { arr.optString(it) } else emptyList()
                phoneSettings.openUrls(list, args.optString("text", ""))
            }
            "device_files"  -> fileManager.execute(args)
            // ── Phase 3: agent engine control, OTP, Spotify ──
            "remember_date" -> com.soltini.app.learning.UpcomingDates.remember(
                context,
                args.optString("label", ""),
                args.optString("kind", "birthday"),
                args.optInt("month", 0),
                args.optInt("day", 0),
                if (args.has("year")) args.optInt("year") else null
            )
            "upcoming_dates" -> com.soltini.app.learning.UpcomingDates.upcoming(context, args.optInt("days", 30))
            "forget_date"   -> com.soltini.app.learning.UpcomingDates.forget(context, args.optString("label", ""), args.optString("kind", "").ifBlank { null })
            "my_favorites"  -> com.soltini.app.learning.HabitTracker.favourites(context, args.optInt("per_category", 3))
            "task_status"   -> AgentTaskRegistry.status()
            "cancel_task"   -> AgentTaskRegistry.cancel()
            "pause_task"    -> AgentTaskRegistry.pause()
            "resume_task"   -> AgentTaskRegistry.resume()
            "task_reply"    -> AgentTaskRegistry.reply(args.optString("answer", args.optString("reply", "")))
            "read_otp"      -> readOtp()
            "spotify_play"  -> SpotifyAutomator(context).play(args.optString("query", args.optString("song", "")))
            "read_photo"    -> readPhoto(args)
            "contact_alias" -> contactAlias(args)
            "whatsapp_confirm_send"   -> whatsappDirect.confirmSend()
            "whatsapp_cancel_draft"   -> whatsappDirect.cancelDraft()
            "whatsapp_delete_message" -> whatsappDirect.deleteLastMessage(
                args.optString("contact_or_number", "").ifBlank { null },
                args.optBoolean("for_everyone", true),
                args.optBoolean("confirmed", false)
            )
            "search_google", "google_search", "chrome_search" -> {
                val query = args.optString("query", args.optString("q", args.optString("search", args.optString("text", ""))))
                val forceChrome = toolName == "chrome_search" || args.optBoolean("force_chrome", false)
                searchGoogle(rawQuery = query, forceChrome = forceChrome)
            }
            "open_url", "browse_url" -> openUrl(args.optString("url", args.optString("link", "")), forceChrome = args.optBoolean("force_chrome", false))
            "browser_action" -> {
                val action = args.optString("action", "snapshot")
                val ref = args.optString("ref", "")
                val text = args.optString("text", "")
                val url = args.optString("url", "")
                val resultText = kotlinx.coroutines.runBlocking {
                    val controller = com.soltini.app.browser.BrowserController.getInstance(context)
                    controller.executeAction(action, ref, text, url)
                }
                val truncated = if (resultText.length > 6000) resultText.take(6000) + "\n...[Truncated]" else resultText
                JSONObject().apply {
                    put("status", "success")
                    put("action", action)
                    put("result", truncated)
                }
            }
            "lock_device"   -> accessibilityAction { it.lockScreen() } success "Device locked"
            "wake_device"   -> wakeDevice()
            "sleep_agent"   -> result("status", "sleep_requested") // handled in BackgroundVoiceService
            "press_back"    -> accessibilityAction { it.pressBack() } success "Back pressed"
            "press_home"    -> accessibilityAction { it.pressHome() } success "Home pressed"
            "press_recents" -> accessibilityAction { it.pressRecents() } success "Recents opened"
            "press_enter", "submit_search", "ime_enter" -> screenOperator.pressEnter()

            // ── Hardware Controls ─────────────────────────────────────────
            "toggle_torch", "set_torch", "torch_on", "torch_off" -> {
                val enable = if (toolName == "torch_off") false else if (toolName == "torch_on") true else args.optBoolean("enabled", true)
                deviceHardware.setTorch(enable)
            }
            "set_volume" -> deviceHardware.setVolume(args.optInt("percent", args.optInt("volume", 50)))
            "increase_volume", "volume_up" -> deviceHardware.adjustVolume("UP")
            "decrease_volume", "volume_down" -> deviceHardware.adjustVolume("DOWN")
            "mute_volume", "mute_phone" -> deviceHardware.setMute(args.optBoolean("mute", true))
            "set_ringer_mode" -> deviceHardware.setRingerMode(args.optString("mode", args.optString("ringer_mode", "NORMAL")))
            "take_screenshot" -> accessibilityAction { it.takeScreenshot() } success "Screenshot taken"
            "open_notifications", "show_notifications" -> accessibilityAction { it.openNotifications() } success "Notifications panel opened"
            "open_quick_settings", "show_quick_settings" -> accessibilityAction { it.openQuickSettings() } success "Quick settings opened"
            "open_power_dialog", "power_menu" -> accessibilityAction { it.openPowerDialog() } success "Power menu opened"
            "toggle_split_screen", "split_screen" -> accessibilityAction { it.toggleSplitScreen() } success "Split screen toggled"
            "media_play_pause" -> deviceHardware.dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "media_next" -> deviceHardware.dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            "media_previous" -> deviceHardware.dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "get_device_status", "check_battery", "device_info" -> deviceHardware.getDeviceStatus()
            "set_alarm" -> deviceHardware.setAlarm(args.optInt("hour", 7), args.optInt("minute", 0), args.optString("message", "Alarm"))
            "set_timer" -> deviceHardware.setTimer(args.optInt("seconds", args.optInt("duration_seconds", 300)), args.optString("message", "Timer"))
            "recall_conversation", "search_conversations", "recall_memory_chat" ->
                (com.soltini.app.memory2.ConversationArchive.get()
                    ?: com.soltini.app.memory2.ConversationArchive.init(context)).recall(args)
            "get_clipboard", "read_clipboard" -> deviceHardware.getClipboard()
            "copy_to_clipboard" -> deviceHardware.copyToClipboard(args.optString("text", ""))

            // ── Screen Automation & Semantic Operator ─────────────────────
            "read_screen", "observe_screen" -> observeScreen()
            "inspect_screen" -> screenOperator.inspectScreen()
            "smart_click", "click_element" -> screenOperator.clickElement(
                target = args.optString("target", args.optString("text", args.optString("label", ""))),
                autoScroll = args.optBoolean("auto_scroll", true)
            )
            "smart_type"    -> screenOperator.typeIntoField(
                targetField = args.optString("field", args.optString("target", "")).takeIf { it.isNotBlank() },
                textToType = args.optString("text", args.optString("query", "")),
                clearFirst = args.optBoolean("clear_first", false),
                autoScroll = args.optBoolean("auto_scroll", true),
                autoDismissKeyboard = args.optBoolean("auto_dismiss_keyboard", false),
                pressEnterAfter = args.optBoolean("press_enter", args.optBoolean("submit", false))
            )
            "fill_form", "smart_fill_form", "universal_fill_form" -> formAutomator.fillForm(
                fields = args.optJSONArray("fields") ?: JSONArray(),
                autoSubmit = args.optBoolean("auto_submit", false),
                submitButtonText = args.optString("submit_button", "").takeIf { it.isNotBlank() },
                allowRisky = args.optBoolean("allow_risky", false)
            )
            "scan_form", "scan_form_fields", "inspect_form" -> formAutomator.scanForm()
            "fill_single_field" -> {
                val field = args.optString("field", args.optString("label", ""))
                val value = args.optString("value", args.optString("text", ""))
                val fieldsArr = JSONArray().apply {
                    put(JSONObject().apply {
                        put("label", field)
                        put("value", value)
                    })
                }
                formAutomator.fillForm(fieldsArr, autoSubmit = false)
            }
            "toggle_checkbox", "smart_toggle" -> screenOperator.toggleCheckable(
                target = args.optString("target", args.optString("label", "")),
                desiredState = if (args.has("checked")) args.optBoolean("checked") else null
            )
            "select_dropdown", "smart_select_dropdown" -> screenOperator.selectDropdownOption(
                dropdownLabel = args.optString("dropdown", args.optString("label", "")),
                optionText = args.optString("option", args.optString("value", ""))
            )
            "hide_keyboard" -> screenOperator.hideKeyboard()
            "swipe_direction" -> screenOperator.swipeDirection(
                direction = args.optString("direction", "UP"),
                distancePercent = args.optDouble("distance_percent", 0.5).toFloat()
            )
            "tap_percent"   -> screenOperator.tapPercent(
                percentX = args.optDouble("percent_x", 0.5).toFloat(),
                percentY = args.optDouble("percent_y", 0.5).toFloat()
            )
            "tap_screen"    -> tapScreen(
                x = args.optDouble("x", -1.0).toFloat(),
                y = args.optDouble("y", -1.0).toFloat()
            )
            "swipe_screen"  -> swipeScreen(
                x1 = args.optDouble("x1", 0.0).toFloat(),
                y1 = args.optDouble("y1", 0.0).toFloat(),
                x2 = args.optDouble("x2", 0.0).toFloat(),
                y2 = args.optDouble("y2", 0.0).toFloat(),
                durationMs = args.optLong("duration_ms", 300)
            )
            "type_text"     -> {
                val text = args.optString("text", args.optString("query", ""))
                val field = args.optString("field", args.optString("target", "")).takeIf { it.isNotBlank() }
                val pressEnter = args.optBoolean("press_enter", args.optBoolean("submit", false))
                val smartTypeRes = screenOperator.typeIntoField(field, text, pressEnterAfter = pressEnter)
                if (smartTypeRes.optString("status") == "success") smartTypeRes else typeText(text)
            }
            "clear_text"    -> accessibilityAction { it.clearText() } success "Text cleared"
            "scroll_down"   -> accessibilityAction { it.scrollDown() } success "Scrolled down"
            "scroll_up"     -> accessibilityAction { it.scrollUp() } success "Scrolled up"

            // ── YouTube Automation ────────────────────────────────────────
            "youtube_search"      -> {
                val raw = args.optString("query", args.optString("q", ""))
                val clean = com.soltini.app.orchestrator.MyraCommandParser.cleanYouTubeQuery(raw).ifBlank { raw.trim() }
                youtubeAutomator.search(clean)
            }
            "youtube_play"        -> {
                val raw = args.optString("query", args.optString("song", args.optString("title", "")))
                val clean = com.soltini.app.orchestrator.MyraCommandParser.cleanYouTubeQuery(raw).ifBlank { raw.trim() }
                val useApp = com.soltini.app.orchestrator.DestinationResolver.wantsYouTubeApp(
                    raw, args.optString("destination", args.optString("target", ""))
                )
                if (useApp) {
                    youtubeAutomator.playSong(clean)
                } else {
                    // Default: Internal Browser (supports background / lock-screen playback).
                    try {
                        kotlinx.coroutines.runBlocking {
                            com.soltini.app.browser.BrowserController.getInstance(context).playMediaOnYouTube(clean)
                        }
                        JSONObject().apply {
                            put("status", "playing")
                            put("message", "Playing '$clean' in Myra Internal Browser (background playback enabled)")
                        }
                    } catch (e: Exception) {
                        JSONObject().apply {
                            put("status", "error")
                            put("message", e.message ?: "Internal browser playback failed")
                        }
                    }
                }
            }
            "youtube_open_video"  -> {
                val target = args.optString("video_id", "")
                    .ifBlank { args.optString("query", "") }
                    .ifBlank { args.optString("title", "") }
                    .ifBlank { args.optString("url", "") }
                youtubeAutomator.openVideo(target)
            }
            "youtube_play_pause"  -> youtubeAutomator.playPause()
            "youtube_seek"        -> youtubeAutomator.seek(args.optInt("seconds", 10))
            "youtube_next"        -> youtubeAutomator.nextVideo()
            "youtube_previous"    -> youtubeAutomator.previousVideo()
            "youtube_like"        -> youtubeAutomator.likeVideo()
            "youtube_dislike"     -> youtubeAutomator.dislikeVideo()
            "youtube_subscribe"   -> youtubeAutomator.subscribe()
            "youtube_skip_ad"     -> youtubeAutomator.skipAd()
            "youtube_set_quality" -> youtubeAutomator.setQuality(args.optString("quality", "1080p"))
            "youtube_fullscreen"  -> youtubeAutomator.toggleFullscreen()
            "youtube_captions"    -> youtubeAutomator.toggleCaptions()
            "youtube_mute"        -> youtubeAutomator.toggleMute()
            "read_youtube_screen" -> youtubeAutomator.readYouTubeScreen()

            // ── WhatsApp Automation ───────────────────────────────────────
            "whatsapp_send_message" -> whatsappDirect.sendMessage(
                args.optString("phone_number", "").ifBlank { args.optString("contact_name", "") },
                args.optString("message", ""),
                args.optBoolean("confirm_before_send", false)
            )
            "maps_navigate" -> mapsAutomator.navigate(args.optString("destination", ""), args.optString("mode", "driving"))
            "maps_search" -> mapsAutomator.searchPlace(args.optString("query", ""))
            "send_email" -> gmailAutomator.composeEmail(
                args.optString("to", ""),
                args.optString("subject", ""),
                args.optString("body", args.optString("message", "")),
                args.optBoolean("auto_send", false)
            )
            "whatsapp_call"         -> whatsappDirect.call(
                args.optString("phone_number", "").ifBlank { args.optString("contact_name", "") },
                args.optBoolean("is_video", false)
            )

            // ── Instagram Automation ──────────────────────────────────────
            "instagram_send_message" -> kotlinx.coroutines.runBlocking {
                instagramAutomator.sendMessage(
                    username = args.optString("username", args.optString("contact_name", "")),
                    message = args.optString("message", "")
                )
            }
            "instagram_open_profile" -> instagramAutomator.openProfile(args.optString("username", ""))

            // ── Autonomous Messaging / Busy Mode ───────────────────────────
            "set_busy_mode" -> {
                val enable = args.optBoolean("enable", true)
                val reason = args.optString("reason", "busy")
                val busyMgr = com.soltini.app.messaging.BusyModeManager.getInstance(context)
                if (enable) {
                    val sanitized = busyMgr.enableBusyMode(reason)
                    JSONObject().apply {
                        put("status", "success")
                        put("busy_mode_active", true)
                        put("reason", sanitized)
                        put("message", "Busy mode enabled. Auto-replying to WhatsApp, Instagram, Telegram, SMS messages contextually.")
                    }
                } else {
                    val summary = busyMgr.disableBusyMode()
                    JSONObject().apply {
                        put("status", "success")
                        put("busy_mode_active", false)
                        put("replied_count", summary.totalRepliedCount)
                        put("unique_senders", org.json.JSONArray(summary.uniqueSenders))
                        put("message", "Busy mode disabled. Replied to ${summary.totalRepliedCount} messages.")
                    }
                }
            }
            "get_busy_mode_status" -> {
                val busyMgr = com.soltini.app.messaging.BusyModeManager.getInstance(context)
                val active = busyMgr.isBusyModeActive()
                val reason = busyMgr.getBusyReason()
                val history = busyMgr.getRecentHistory(10)
                val arr = org.json.JSONArray()
                history.forEach { arr.put(it.toJsonObject()) }
                JSONObject().apply {
                    put("status", "success")
                    put("busy_mode_active", active)
                    put("reason", reason)
                    put("total_replied", history.size)
                    put("history", arr)
                }
            }

            // ── Reels Automation ──────────────────────────────────────────
            "next_reel"             -> reelsAutomator.nextReel()
            "previous_reel"         -> reelsAutomator.previousReel()

            // ── Public APIs Integration (1,756+ APIs across 51 categories) ──
            "search_public_apis" -> {
                val query = args.optString("query", "")
                val category = args.optString("category", "").ifBlank { null }
                val noAuth = args.optBoolean("require_no_auth", false)
                val limit = args.optInt("limit", 10)
                val results = publicApiRegistry.searchApis(query, category, noAuth, limit)
                val arr = org.json.JSONArray()
                results.forEach { arr.put(it.toJsonObject()) }
                JSONObject().apply {
                    put("status", "success")
                    put("query", query)
                    put("total_found", results.size)
                    put("apis", arr)
                }
            }
            "list_public_api_categories" -> {
                val cats = publicApiRegistry.getCategories()
                val arr = org.json.JSONArray()
                cats.forEach { arr.put(it) }
                JSONObject().apply {
                    put("status", "success")
                    put("total_categories", cats.size)
                    put("total_apis", publicApiRegistry.getTotalApisCount())
                    put("categories", arr)
                }
            }
            "get_apis_by_category" -> {
                val category = args.optString("category", "")
                val results = publicApiRegistry.getApisByCategory(category)
                val arr = org.json.JSONArray()
                results.forEach { arr.put(it.toJsonObject()) }
                JSONObject().apply {
                    put("status", "success")
                    put("category", category)
                    put("total_apis", results.size)
                    put("apis", arr)
                }
            }
            "recommend_api_for_task" -> {
                val task = args.optString("task", args.optString("task_description", args.optString("query", "")))
                publicApiRegistry.recommendApisForTask(task)
            }
            "execute_public_api" -> kotlinx.coroutines.runBlocking {
                val url = args.optString("url", "")
                val method = args.optString("method", "GET")
                val body = args.optString("body", "").ifBlank { null }
                publicApiExecutor.executeApi(url = url, method = method, body = body)
            }
            "fetch_crypto_price" -> kotlinx.coroutines.runBlocking {
                val coin = args.optString("coin", args.optString("crypto", "bitcoin"))
                val currency = args.optString("currency", "usd")
                publicApiExecutor.fetchCryptoPrice(coin, currency)
            }
            "fetch_weather" -> kotlinx.coroutines.runBlocking {
                val city = args.optString("city", args.optString("location", "Delhi"))
                publicApiExecutor.fetchWeather(city)
            }
            "lookup_dictionary" -> kotlinx.coroutines.runBlocking {
                val word = args.optString("word", "")
                publicApiExecutor.lookupDictionaryWord(word)
            }
            "fetch_currency_rate" -> kotlinx.coroutines.runBlocking {
                val from = args.optString("from", "USD")
                val to = args.optString("to", "INR")
                publicApiExecutor.fetchCurrencyRate(from, to)
            }
            "lookup_ip" -> kotlinx.coroutines.runBlocking {
                val ip = args.optString("ip", "").ifBlank { null }
                publicApiExecutor.lookupIp(ip)
            }
            "fetch_random_joke" -> kotlinx.coroutines.runBlocking {
                publicApiExecutor.fetchRandomJoke()
            }
            "fetch_random_advice" -> kotlinx.coroutines.runBlocking {
                publicApiExecutor.fetchRandomAdvice()
            }

            // ── Phone Call Control & SMS ───────────────────────────────────
            "get_call_info", "get_incoming_call", "who_is_calling" -> {
                val callMgr = com.soltini.app.telephony.CallNotificationManager.getInstance(context)
                val info = callMgr.getCurrentCallInfo()
                val res = info.toJsonObject()
                if (info.isIncoming) {
                    val caller = info.callerName ?: "Unknown"
                    res.put("message", "Incoming call from $caller (${info.phoneNumber ?: "Unknown number"}).")
                } else {
                    res.put("message", "No incoming call right now. Current call state is ${info.callState}.")
                }
                res
            }
            "answer_call"           -> {
                val callMgr = com.soltini.app.telephony.CallNotificationManager.getInstance(context)
                val res = callMgr.answerCallDetailed()
                result("status", if (res.success) "success" else "failed", "method", res.method, "message", res.message)
            }
            "reject_call"           -> {
                val callMgr = com.soltini.app.telephony.CallNotificationManager.getInstance(context)
                val res = callMgr.rejectCallDetailed()
                result("status", if (res.success) "success" else "failed", "method", res.method, "message", res.message)
            }
            "make_phone_call"       -> {
                val target = contactAliases.resolve(args.optString("contact_or_number", ""))
                val success = com.soltini.app.telephony.CallNotificationManager.getInstance(context).makeCall(target)
                result("status", if (success) "success" else "error")
            }
            "send_sms"              -> {
                val recipient = contactAliases.resolve(args.optString("recipient", args.optString("contact", args.optString("phone_number", ""))))
                val message = args.optString("message", args.optString("text", ""))
                val cleanMsg = com.soltini.app.orchestrator.MyraCommandParser.cleanMessageBody(message).ifBlank { message.trim() }
                val callMgr = com.soltini.app.telephony.CallNotificationManager.getInstance(context)
                val numberOnly = recipient.replace(Regex("[\\s\\-().]"), "")
                val isRawNumber = numberOnly.matches(Regex("^\\+?\\d{5,15}$"))
                val matches = if (isRawNumber) emptyList<com.soltini.app.telephony.ContactMatch>() else callMgr.searchContacts(recipient)
                if (isRawNumber) {
                    // Any number works - it does not need to be saved in contacts.
                    val sendRes = com.soltini.app.telephony.SmsSender(context).sendSms(numberOnly, cleanMsg)
                    result(
                        "status", if (sendRes.success) "success" else "failed",
                        "recipient", recipient, "phone", numberOnly,
                        "method", sendRes.method, "message", sendRes.message
                    )
                } else if (matches.isEmpty()) {
                    error("No contact found for '$recipient'. Please specify a valid contact name or phone number.")
                } else if (matches.size > 1 && !matches.any { it.name.equals(recipient, ignoreCase = true) }) {
                    val listStr = matches.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                    result("status", "clarification_needed", "message", "Multiple contacts found for '$recipient': $listStr. Which one should I message?")
                } else {
                    val targetMatch = matches.find { it.name.equals(recipient, ignoreCase = true) } ?: matches.first()
                    val smsSender = com.soltini.app.telephony.SmsSender(context)
                    val sendRes = smsSender.sendSms(targetMatch.phoneNumber, cleanMsg)
                    result(
                        "status", if (sendRes.success) "success" else "failed",
                        "recipient", targetMatch.name,
                        "phone", targetMatch.phoneNumber,
                        "method", sendRes.method,
                        "message", sendRes.message
                    )
                }
            }
            "search_contacts"       -> {
                val query = args.optString("query", args.optString("name", ""))
                val callMgr = com.soltini.app.telephony.CallNotificationManager.getInstance(context)
                val matches = callMgr.searchContacts(query)
                val arr = JSONArray()
                matches.forEach { arr.put(JSONObject().apply { put("name", it.name); put("phoneNumber", it.phoneNumber) }) }
                result("status", "success", "total", matches.size, "contacts", arr)
            }

            else -> error("Unknown tool: $toolName")
        }
    }

    // ─── Device Control ───────────────────────────────────────────────────────

    // ─── Phase 3 helpers ──────────────────────────────────────────────────────

    private fun readOtp(): JSONObject {
        val settings = com.soltini.app.settings.AppSettings(context)
        if (!settings.isOtpReaderEnabled) {
            return error("The OTP reader is switched off. Boss can enable it in Settings (Agent section).")
        }
        if (!com.soltini.app.notifications.NotificationRepository.getInstance(context).isNotificationAccessGranted()) {
            return error("Notification access is not granted, so I cannot see incoming codes.")
        }
        val otp = OtpReader.latest(context)
            ?: return result("status", "not_found", "message", "No fresh OTP in the last 2 minutes. Ask Boss to wait for the SMS, then try again.")
        return result("status", "success", "code", otp.code, "from_app", otp.app, "seconds_ago", otp.secondsAgo)
    }

    // ─── Phase 2 helpers ──────────────────────────────────────────────────────

    /** Looks at any photo on the phone (path, name, "latest", "last screenshot") and describes / reads it with Gemini vision. */
    private fun readPhoto(args: JSONObject): JSONObject {
        if (!fileManager.hasAccess()) {
            val r = fileManager.requestAccess()
            r.put("status", "needs_permission")
            return r
        }
        val spec = args.optString("path", args.optString("name", args.optString("which", "latest")))
        val question = args.optString("question", args.optString("prompt", "Describe this photo and read out any text written in it."))
        val file = fileManager.findImage(spec) ?: return error("I could not find a photo for '$spec'.")
        val uri = fileManager.prepareImageForVision(file) ?: return error("Could not load ${file.name}.")
        return try {
            val settings = com.soltini.app.settings.AppSettings(context)
            val analysis = kotlinx.coroutines.runBlocking {
                com.soltini.app.rag.RagKnowledgeEngine.getInstance(context, settings)
                    .imageVisionParser.analyzeImageWithPrompt(context, uri, question)
            }
            result("status", "success", "photo", file.name, "path", file.absolutePath, "analysis", analysis)
        } catch (e: Exception) {
            error("Could not analyse the photo: ${e.message}")
        }
    }

    private fun contactAlias(args: JSONObject): JSONObject {
        val action = args.optString("action", "list").lowercase()
        val alias = args.optString("alias", "")
        return when (action) {
            "set", "add" -> {
                val target = args.optString("contact_or_number", "")
                if (alias.isBlank() || target.isBlank()) error("alias and contact_or_number are required")
                else { contactAliases.set(alias, target); result("status", "saved", "alias", alias, "means", target) }
            }
            "remove", "delete" -> result("status", if (contactAliases.remove(alias)) "removed" else "not_found", "alias", alias)
            else -> {
                val arr = JSONArray()
                contactAliases.all().forEach { (k, v) -> arr.put(JSONObject().apply { put("alias", k); put("means", v) }) }
                result("status", "success", "aliases", arr)
            }
        }
    }

    private fun openApp(appName: String): JSONObject {
        if (appName.isBlank()) return error("app_name is required")

        val pm: PackageManager = context.packageManager
        val query = appName.lowercase().trim()
        val match = pm.getInstalledApplications(PackageManager.GET_META_DATA).firstOrNull {
            val label = pm.getApplicationLabel(it).toString().lowercase()
            label.contains(query) || query.contains(label)
        } ?: return error("No app found matching: $appName")

        val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            ?: return error("App found (${match.packageName}) but cannot be launched")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return result("status", "opened", "app", pm.getApplicationLabel(match).toString())
    }

    private fun wakeDevice(): JSONObject {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Soltini::WakeDevice"
            )
            wl.acquire(3000L)
            wl.release()
            result("status", "screen_on")
        } catch (e: Exception) {
            error(e.message ?: "Failed to wake device")
        }
    }

    // ─── Screen Automation ────────────────────────────────────────────────────

    private fun observeScreen(): JSONObject {
        val companion = com.soltini.app.companion.ScreenCompanionManager.getInstance(context)
        val snapshot = try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(2500L) {
                    companion.captureCurrentScreenWithVision()
                }
            } ?: companion.captureCurrentScreenContext()
        } catch (_: Exception) {
            companion.captureCurrentScreenContext()
        }
        return if (!snapshot.isAvailable) {
            readScreen()
        } else if (snapshot.isSecure) {
            JSONObject().apply {
                put("status", "secure_screen")
                put("message", "Screen is protected (banking or secure screen).")
            }
        } else {
            JSONObject().apply {
                put("status", "ok")
                put("app", snapshot.currentApp)
                put("screen_content", snapshot.toHumanSummary())
            }
        }
    }

    private fun readScreen(): JSONObject {
        val svc = SoltiniAccessibilityService.getInstance()
            ?: return error("Accessibility Service not active")
        val text = svc.readScreen()
        return JSONObject().apply {
            put("status", "ok")
            put("screen_content", text)
        }
    }

    private fun clickElement(text: String): JSONObject {
        if (text.isBlank()) return error("text is required")
        val svc = SoltiniAccessibilityService.getInstance()
            ?: return error("Accessibility Service not active")
        return if (svc.clickByText(text)) {
            result("status", "clicked", "element", text)
        } else {
            error("No clickable element found matching: $text")
        }
    }

    private fun tapScreen(x: Float, y: Float): JSONObject {
        if (x < 0 || y < 0) return error("x and y coordinates are required")
        val svc = SoltiniAccessibilityService.getInstance()
            ?: return error("Accessibility Service not active")
        return if (svc.tapAt(x, y)) {
            result("status", "tapped", "x", x.toString(), "y", y.toString())
        } else {
            error("Tap gesture failed — requires Android 7+ and gesture permission")
        }
    }

    private fun swipeScreen(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): JSONObject {
        val svc = SoltiniAccessibilityService.getInstance()
            ?: return error("Accessibility Service not active")
        return if (svc.swipe(x1, y1, x2, y2, durationMs)) {
            result("status", "swiped")
        } else {
            error("Swipe gesture failed")
        }
    }

    private fun typeText(text: String): JSONObject {
        if (text.isBlank()) return error("text is required")
        val svc = SoltiniAccessibilityService.getInstance()
            ?: return error("Accessibility Service not active")
        return if (svc.typeText(text)) {
            result("status", "typed", "text", text)
        } else {
            error("No editable text field found on screen. Please tap a text field first.")
        }
    }

    fun searchGoogle(rawQuery: String, forceChrome: Boolean = false): JSONObject {
        val query = com.soltini.app.orchestrator.MyraCommandParser.cleanWebSearchQuery(rawQuery).ifBlank { rawQuery.trim() }
        if (query.isBlank()) return error("query is required for google search")
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.google.com/search?q=$encoded"
            if (!forceChrome) {
                val browserController = com.soltini.app.browser.BrowserController.getInstance(context)
                browserController.openUrlInInternalBrowser(url)
                result("status", "success", "action", "google_search", "destination", "internal_browser", "query", query, "url", url)
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    setPackage("com.android.chrome")
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // If Chrome not found or default handler preferred, launch via general browser intent
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(fallbackIntent)
                }
                result("status", "success", "action", "google_search", "destination", "chrome_app", "query", query, "url", url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchGoogle failed: ${e.message}")
            error("Failed to execute google search: ${e.message}")
        }
    }

    fun openUrl(url: String, forceChrome: Boolean = false): JSONObject {
        if (url.isBlank()) return error("url is required")
        return try {
            val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            if (!forceChrome) {
                val browserController = com.soltini.app.browser.BrowserController.getInstance(context)
                browserController.openUrlInInternalBrowser(fullUrl)
                result("status", "success", "action", "open_url", "destination", "internal_browser", "url", fullUrl)
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    setPackage("com.android.chrome")
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(fallbackIntent)
                }
                result("status", "success", "action", "open_url", "destination", "chrome_app", "url", fullUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "openUrl failed: ${e.message}")
            error("Failed to open URL: ${e.message}")
        }
    }

    fun openUrlInChrome(url: String): JSONObject = openUrl(url, forceChrome = true)

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Runs a lambda against the active AccessibilityService instance.
     * Returns an error JSONObject if the service is not running.
     */
    private fun accessibilityAction(action: (SoltiniAccessibilityService) -> Boolean): AccessibilityResult {
        val svc = SoltiniAccessibilityService.getInstance()
            ?: return AccessibilityResult.NoService
        val ok = action(svc)
        return if (ok) AccessibilityResult.Success else AccessibilityResult.Failed
    }

    private sealed class AccessibilityResult {
        object NoService : AccessibilityResult()
        object Success : AccessibilityResult()
        object Failed : AccessibilityResult()
    }

    private infix fun AccessibilityResult.success(successMsg: String): JSONObject = when (this) {
        is AccessibilityResult.NoService -> error("Accessibility Service not active. Enable it in Settings → Accessibility → Soltini")
        is AccessibilityResult.Success   -> result("status", successMsg)
        is AccessibilityResult.Failed    -> error("Action failed")
    }

    private fun result(vararg pairs: Any): JSONObject = JSONObject().apply {
        var i = 0
        while (i < pairs.size - 1) {
            put(pairs[i].toString(), pairs[i + 1])
            i += 2
        }
    }

    private fun error(msg: String): JSONObject = JSONObject().apply { put("error", msg) }
}
