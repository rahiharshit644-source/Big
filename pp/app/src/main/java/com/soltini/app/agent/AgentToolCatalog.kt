package com.soltini.app.agent

import org.json.JSONArray
import org.json.JSONObject

/** Tools the task engine may call: engine-control tools + every real phone action. */
object AgentToolCatalog {

    /** Names handled inside the engine itself (not by AgentToolExecutor). */
    val CONTROL_TOOLS = setOf(
        "finish_task", "ask_user", "note", "wait_for_text", "wait_seconds",
        "find_on_screen", "look_at_screen", "dismiss_popup", "record_item"
    )

    /** Tools that should visibly change the screen when they work. */
    val UI_TOOLS = setOf(
        "smart_click", "click_element", "tap_percent", "tap_screen", "swipe_direction", "swipe_screen",
        "scroll_down", "scroll_up", "press_back", "press_home", "press_recents", "open_app", "press_enter",
        "toggle_checkbox", "select_dropdown", "open_settings", "open_notifications", "open_quick_settings",
        "youtube_open_video", "youtube_search", "maps_navigate", "open_url", "open_urls", "install_app"
    )

    val SCROLL_TOOLS = setOf("scroll_down", "scroll_up", "swipe_direction", "swipe_screen")

    val TYPE_TOOLS = setOf("smart_type", "type_text")

    private fun p(name: String, type: String, desc: String) = Triple(name, type, desc)

    private fun d(name: String, desc: String, vararg props: Triple<String, String, String>, required: List<String> = emptyList()): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("description", desc)
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    for ((n, t, ds) in props) put(n, JSONObject().apply { put("type", t); put("description", ds) })
                })
                if (required.isNotEmpty()) put("required", JSONArray(required))
            })
        }

    fun build(): JSONArray = JSONArray().apply {
        // ── engine control ──
        put(d("finish_task", "Call when the goal is fully done (or impossible). summary = one short friendly sentence for the user in Hinglish.",
            p("summary", "STRING", "What was done / why it failed"), p("success", "BOOLEAN", "True only if the goal is really achieved"),
            required = listOf("summary", "success")))
        put(d("ask_user", "Ask the user a question or hand over a step (missing detail, a choice, a payment/delete confirmation, a password/PIN/OTP they must enter themselves). The task pauses until they answer.",
            p("question", "STRING", "Short clear question"), required = listOf("question")))
        put(d("note", "Remember a fact for later steps (an address, a price, a code, a name).",
            p("key", "STRING", "Short name"), p("value", "STRING", "The value"), required = listOf("key", "value")))
        put(d("wait_for_text", "Wait until some text appears on screen (loading, page change). Returns found or timed out.",
            p("text", "STRING", "Text to wait for"), p("timeout_seconds", "INTEGER", "Max seconds (default 10)"), required = listOf("text")))
        put(d("wait_seconds", "Pause briefly (1-10 s) for something to load.", p("seconds", "INTEGER", "Seconds"), required = listOf("seconds")))
        put(d("find_on_screen", "Scroll down (then up) until the text is visible, stopping at the end of the list. Use before clicking something that is not on screen.",
            p("text", "STRING", "What to find"), p("max_scrolls", "INTEGER", "Default 8"), required = listOf("text")))
        put(d("look_at_screen", "Get a real screenshot for the next step. Use when the element list is empty, confusing, or a click keeps failing.",
            p("question", "STRING", "What you want to know from the picture")))
        put(d("dismiss_popup", "Close an obstructing pop-up, ad, rating prompt or tip (Not now, Skip, Later, Close, Got it)."))
        put(d("record_item", "Bulk tasks: mark one item as processed so it is never handled twice. Returns whether it was already done.",
            p("item", "STRING", "Unique name/description of the item"), required = listOf("item")))

        // ── navigation & screen ──
        put(d("open_app", "Open any app by name (installs from Google Play if missing).", p("app_name", "STRING", "App name"), required = listOf("app_name")))
        put(d("install_app", "Install an app from Google Play.", p("app_name", "STRING", "App name"), required = listOf("app_name")))
        put(d("press_back", "Android Back."))
        put(d("press_home", "Android Home."))
        put(d("press_recents", "Recent apps."))
        put(d("smart_click", "Tap a button/tab/link/item by its visible text or label (auto-scrolls to find it).", p("target", "STRING", "Visible text or label"), required = listOf("target")))
        put(d("smart_type", "Type into an input field found by its label/hint (or the focused field). Set press_enter to submit searches.",
            p("text", "STRING", "Text to type"), p("field", "STRING", "Field label or hint"), p("clear_first", "BOOLEAN", "Clear old text first"),
            p("press_enter", "BOOLEAN", "Press Enter/Search after typing"), required = listOf("text")))
        put(d("press_enter", "Press Enter / Search on the keyboard."))
        put(d("hide_keyboard", "Close the on-screen keyboard."))
        put(d("swipe_direction", "Swipe/scroll: UP, DOWN, LEFT or RIGHT.", p("direction", "STRING", "UP, DOWN, LEFT or RIGHT"), p("distance_percent", "NUMBER", "0.1-0.9"), required = listOf("direction")))
        put(d("scroll_down", "Scroll the page down."))
        put(d("scroll_up", "Scroll the page up."))
        put(d("tap_percent", "Tap at a fraction of the screen (0.0-1.0).", p("percent_x", "NUMBER", "X"), p("percent_y", "NUMBER", "Y"), required = listOf("percent_x", "percent_y")))
        put(d("tap_screen", "Tap exact pixel coordinates (use the @(x,y) values from the screen list).", p("x", "NUMBER", "X"), p("y", "NUMBER", "Y"), required = listOf("x", "y")))
        put(d("toggle_checkbox", "Turn a checkbox/switch on or off by its label.", p("target", "STRING", "Label"), p("state", "BOOLEAN", "Desired state"), required = listOf("target")))
        put(d("select_dropdown", "Pick an option in a dropdown.", p("dropdown", "STRING", "Dropdown label"), p("option", "STRING", "Option text"), required = listOf("option")))
        put(d("scan_form", "List all fields of the form on screen."))
        put(d("fill_single_field", "Fill one form field by label.", p("field", "STRING", "Label"), p("value", "STRING", "Value"), required = listOf("field", "value")))
        put(d("open_notifications", "Pull down the notification shade."))
        put(d("open_quick_settings", "Open quick settings."))
        put(d("take_screenshot", "Take a screenshot."))
        put(d("read_screen", "Describe the screen with vision (use when the list is not enough)."))

        // ── phone, files, comms ──
        put(d("open_settings", "Open a Settings page (wifi, bluetooth, display, battery, storage, apps ...).", p("page", "STRING", "Page"), required = listOf("page")))
        put(d("set_brightness", "Screen brightness.", p("percent", "INTEGER", "1-100"), p("change", "STRING", "up/down")))
        put(d("set_volume", "Media volume percent.", p("percent", "INTEGER", "0-100"), required = listOf("percent")))
        put(d("open_url", "Open a website.", p("url", "STRING", "URL"), required = listOf("url")))
        put(d("search_google", "Search Google in Chrome.", p("query", "STRING", "Query"), required = listOf("query")))
        put(d("copy_to_clipboard", "Copy text to the clipboard.", p("text", "STRING", "Text"), required = listOf("text")))
        put(d("get_clipboard", "Read the clipboard."))
        put(d("device_files", "Files/folders on the phone: action = list, search, create_folder, create_file, read, write, append, edit, delete (needs confirmed=true after the user said yes), restore, move, copy, rename, open, latest_photos.",
            p("action", "STRING", "Action"), p("path", "STRING", "Path or name"), p("destination", "STRING", "Destination"), p("new_name", "STRING", "New name"),
            p("content", "STRING", "Text content"), p("find", "STRING", "Find"), p("replace", "STRING", "Replace"), p("query", "STRING", "Search words"),
            p("type", "STRING", "image/video/audio/document"), p("confirmed", "BOOLEAN", "User confirmed delete"), required = listOf("action")))
        put(d("read_photo", "Look at any photo (path, name, 'latest', 'last screenshot') and describe / read it.", p("path", "STRING", "Photo"), p("question", "STRING", "Question")))
        put(d("whatsapp_send_message", "WhatsApp message to a contact or ANY number.", p("contact_name", "STRING", "Contact"), p("phone_number", "STRING", "Number"), p("message", "STRING", "Message"), required = listOf("message")))
        put(d("whatsapp_call", "WhatsApp voice/video call to a contact or any number.", p("contact_name", "STRING", "Contact"), p("phone_number", "STRING", "Number"), p("is_video", "BOOLEAN", "Video?"), required = listOf("is_video")))
        put(d("send_sms", "SMS to a contact or any number.", p("recipient", "STRING", "Name or number"), p("message", "STRING", "Text"), required = listOf("recipient", "message")))
        put(d("make_phone_call", "Phone call to a contact or any number.", p("contact_or_number", "STRING", "Name or number"), required = listOf("contact_or_number")))
        put(d("send_email", "Compose an email.", p("to", "STRING", "Recipient"), p("subject", "STRING", "Subject"), p("body", "STRING", "Body"), required = listOf("to")))
        put(d("maps_navigate", "Navigate in Google Maps.", p("destination", "STRING", "Place"), p("mode", "STRING", "driving/walking/transit"), required = listOf("destination")))
        put(d("youtube_search", "Search YouTube.", p("query", "STRING", "Query"), required = listOf("query")))
        put(d("youtube_open_video", "Open/play a YouTube video.", p("video_id", "STRING", "Title or id"), required = listOf("video_id")))
        put(d("spotify_play", "Play a song/artist/playlist on Spotify and verify playback.", p("query", "STRING", "What to play"), required = listOf("query")))
        put(d("media_play_pause", "Play/pause current media."))
        put(d("set_alarm", "Set an alarm.", p("hour", "INTEGER", "0-23"), p("minute", "INTEGER", "0-59"), p("message", "STRING", "Label"), required = listOf("hour", "minute")))
        put(d("set_timer", "Start a timer.", p("seconds", "INTEGER", "Seconds"), required = listOf("seconds")))
        put(d("read_otp", "Read the newest one-time code from notifications (only if the user enabled the OTP reader). Then use smart_type to enter it."))
    }
}
