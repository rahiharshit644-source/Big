# Myra – changes in this build

## 1. NouTube features in the Myra browser (YouTube)
- `browser/YouTubeEnhancer.kt` – strips ad data from YouTube API responses (fetch/XHR/initial data), removes ad items from feeds, hides Shorts, auto-skips leftover ads.
- Toggles: `YouTubeEnhancer.blockAds`, `YouTubeEnhancer.hideShorts`.

## 2. Background play fix
- `MyraWebView` (in `BrowserController.kt`) always reports a visible window, so the page never pauses when the app is backgrounded.
- `MyraMediaBridge`: page `<video>/<audio>` play/pause is reported to native, so the media foreground service + notification start when YOU play something (not only when the agent does).
- Auto-resume if the site pauses itself in the background, and auto-dismiss of "Continue watching?" prompts.
- `MainActivity` tells the browser when the app goes to background/foreground.

## 3. Status orb on the Voice screen
- `ui/components/CurlNoiseOrb.kt` – `MyraStatusOrb` = native state halo + WebGL Curl Noise Orb (+ native fallback orb if WebGL fails).
- `ui/components/MyraOrbState.kt` – OFF (grey) / CONNECTING (violet) / LISTENING (cyan-blue) / SPEAKING (pink) / MUTED (amber) / PAUSED (slate).
- `assets/orb/curl_noise_orb.html` – orb now takes `orbSetState(name)`, uses phone-safe shader precision and reports WebGL failure.
- `ui/VoiceScreen.kt` – coloured status chip + title ("Myra is OFF / Listening / Speaking …") driven by the same state.

## 4. Permanent conversation memory (saved to local files)
- `memory2/ConversationArchive.kt` – every message (voice + typed, yours + Myra's) is saved to
  `<app files>/conversations/YYYY-MM-DD.jsonl` (for recall) and one readable `Myra_Conversations.txt`.
  Hooked into `Memory2Database.saveConversationEntry()`, so no path can miss a message. "Clear chat" does NOT delete it.
- `GeminiLiveManager` now enables `inputAudioTranscription` + `outputAudioTranscription` (before this, spoken words were never transcribed, so voice chats were not saved).
- `BackgroundVoiceService` turns those transcripts into saved USER / MYRA entries (partial replies kept when you interrupt).
- On every connect, the last ~40 messages are injected into Myra's system instruction; new tool `recall_conversation`
  (query / days_ago / date / export) lets her search ALL older conversations ("kal kya baat hui thi?").
- Fixed: `getAllConversationEntries(limit)` returned the OLDEST rows; it now returns the newest `limit` rows.
- Settings -> Storage: "Conversation Memory" card (count, folder path, Export to Downloads/Myra, Delete archive).

---

## Phase 1 — Voice, wake word & standby (ported from the Jarvis project)

- **Wake word**: in standby an offline Vosk listener waits for "Myra" / "Hey Myra" (+ variants). Strict grammar, word-level confidence checks, call-aware. Gemini stays disconnected (0 data) until she wakes. (`audio/WakeWordEngine.kt`, `audio/VoskModelManager.kt`)
- **Standby**: `sleep()` now means standby (mic owned by the wake listener); `wakeUp()` fires on wake word or orb tap. Wake chime, standby chime, brief vibration, short spoken greeting after a voice wake. (`audio/MyraSoundEffects.kt`)
- **Idle → standby**: the existing "Auto-Sleep Idle Timeout" setting is now actually enforced (it was saved but never read).
- **Standby by voice**: `sleep_agent` tool now understands "go to background", "standby", "take a break", "so jao", "background me jao".
- **Headset router**: routes audio/mic to Bluetooth or wired headsets, otherwise to the loudspeaker (`audio/HeadsetAudioRouter.kt`). Skipped while Screen Recording Mode is on.
- **Silent Execution** (Settings): no spoken narration while action tools run; read/lookup tools (screen, notifications, weather...) stay audible.
- Settings: new "Wake Word" and "Silent Execution" toggles.
- Build: `vosk-android` + `jna`, `VIBRATE`/`BLUETOOTH*` permissions, ProGuard keep rules.
- **Required once**: copy the Vosk model folder `model-en-us` from the Jarvis project into `app/src/main/assets/` (see PHASE1_README.md).


---

## Phase 2 — Universal phone control, apps & calling

- **Universal app opener** (`agent/AppManager.kt`): opens any app by fuzzy name / alias ("insta", "gpay") / package / website. **Not installed -> opens Google Play and taps Install.** Also `install_app`, `uninstall_app` (asks first; system apps protected), `list_apps`, `open_app_settings`.
- **Whole-phone file control** (`agent/DeviceFileManager.kt`, tool `device_files`): list, search, create folder/file, read, write, append, edit, move, copy, rename, open, latest photos, delete. Delete needs a spoken yes and goes to a **trash** (`restore` undoes it); edits keep a backup. Needs "All files access" (Myra opens the page when missing). Existing SAF-based `manage_files` is untouched.
- **Read any photo** (`read_photo`): path, name, "latest" or "last screenshot" -> Gemini vision describes it / reads its text.
- **WhatsApp to ANY number, saved or not** (`agent/WhatsAppDirect.kt`): message, voice call, video call via the official deep link (WhatsApp Business supported), name -> number resolution through contacts, **draft / confirm / cancel**, and **delete my last message** (for me / for everyone, asks first). Old UI-search route kept as fallback.
- **SMS and phone calls to any number**: `send_sms` previously failed on numbers that were not contacts; fixed. `make_phone_call` already accepted numbers.
- **Contact nicknames** (`contact_alias`): "mummy" = "Mom"; applied to calls, SMS and WhatsApp.
- **Settings control** (`agent/PhoneSettingsController.kt`): `open_settings` (wifi, bluetooth, hotspot, battery, storage...), `set_brightness`, `open_urls` (several sites, one Chrome tab each, with the Jarvis `DomainNormalizer`).
- Incoming-call announce / answer / reject by voice was already in Myra and is unchanged.
- Accessibility service gained `clickExactText`, `findNodesByViewId`, `clickNode`, `longPressNode`, `longPressAt`.
- Settings: `defaultCountryCode` (default 91) is added to 10-digit numbers for WhatsApp.
- Manifest: MANAGE_EXTERNAL_STORAGE, READ_MEDIA_IMAGES, REQUEST_DELETE_PACKAGES, WRITE_SETTINGS (+ legacy storage). FileProvider now covers shared storage.

---

## Phase 3 — Phone agent: look, act, verify, recover

`trigger_automation` already ran asynchronously, but the operator behind it was a plain 10-step "call tools in a loop" with no screen feedback. It is replaced by `agent/AgentTaskEngine.kt` (the old loop stays as `executeOperatorGoalLegacy`).

- **Plan first**: goal restated, scope (single / all / until_found), up to 8 steps, success criteria.
- **Looks before every step** (`ScreenObserver`): numbered element list with coordinates + visible text, and a **screenshot** whenever the UI tree is empty/poor or the agent is stuck.
- **Verifies every action**: screen fingerprint before/after; the model is told `screen_changed`, `NO_VISIBLE_EFFECT`, `END_OF_CONTENT`, and whether typed text really appeared.
- **Stuck / loop recovery ladder**: dismiss pop-up -> screenshot -> Back -> different approach -> clean failure report.
- **Bulk tasks ("sab", "all")**: `record_item` prevents double handling; cannot finish until the end of the list is proven.
- **Independent final check** (screenshot + screen) before it claims success.
- **Safety gate** (`RiskGate`, Settings toggle, default ON): payments/orders/deletes ask first; passwords, PINs, CVV, card numbers are never typed - the user does them.
- **Ask / answer by voice**: `ask_user` pauses the task, the live voice model asks Boss and returns the answer with `task_reply`.
- **Voice control**: `task_status`, `pause_task`, `resume_task`, `cancel_task`.
- **Engine tools** beyond clicking: `find_on_screen` (scroll until found), `wait_for_text`, `wait_seconds`, `dismiss_popup`, `look_at_screen`, `note`, plus every phone/file/WhatsApp/SMS/Spotify/settings tool from Phases 1-2.
- **OTP reader** (opt-in, Settings, default OFF): `read_otp` returns a code from notifications in the last 2 minutes.
- **Spotify**: `spotify_play` uses Spotify's play-from-search entry point and verifies that audio really started.
- Phone-lock check, wake lock for the duration, 30 steps / 8 min (bulk: 60 steps / 15 min).
- Settings: "Agent: Ask Before Risky Steps", "OTP Reader (opt-in)".

---

## Phase 3 addendum — floating task HUD

- **Orb progress ring** (`overlay/TaskProgressArc.kt`): while a phone task runs a ring fills around the orb (cyan = working, full amber = waiting for you). Works with both orb styles.
- **Tap the orb** (when Myra is awake) to open / close the **task HUD** (`overlay/AgentHudView.kt`): goal, progress bar, step n of N, what it is doing now, the plan, Pause / Resume / Cancel, and Yes / No when the agent asks something. With no task it shows an idle card / the last task. Tapping the orb while she sleeps still wakes her.
- The HUD never blocks the agent: it is touchable while paused, waiting or finished and for 6 s after you open it, then turns click-through while the agent works. It is hidden from accessibility so the agent can never click its own HUD.

## Phase 4 — Memory (kept, extended)

Finding: Myra's memory (5-tier Memory 2.0, fact conflict resolver, user corrections, Mem0, RAG, encrypted backup/restore, permanent conversation archive, and a full Settings > Memory screen with search / filter / add / edit / delete) is more capable than the Jarvis memory, which is a simple store. Replacing it would have lost features, so it is **not replaced**. What Jarvis had that Myra did not was added on top:

- **Favourites learned from use** (`learning/HabitTracker.kt`): favourite songs (Spotify), apps, contacts (calls / SMS / WhatsApp) and YouTube topics. At 3 / 5 / 10 / 20 / 50 / 100 uses a normal memory is written or updated ("Boss often plays ... (about N times)"). You can see, edit or delete them in Settings > Memory; an item you edited/pinned is never overwritten.
- **Routines**: "usually opens YouTube around 22:00" when one 3-hour window holds >= 60% of >= 5 uses over >= 3 days (stored as a ROUTINE memory).
- **Birthdays / anniversaries** (`learning/UpcomingDates.kt`): tools `remember_date`, `upcoming_dates`, `forget_date`; also mirrored as normal memories. If something falls on today's date, her wake-up greeting wishes them.
- **`my_favorites` tool**: "meri favourite song kaun si hai", "sabse zyada kise call karta hoon".
- Settings toggle **Learn My Habits** (default ON; counting stays on the phone).
