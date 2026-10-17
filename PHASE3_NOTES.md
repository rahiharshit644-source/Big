# Phase 3 (cumulative: includes all Phase 2 files, so this zip alone is enough)
Unzip over the project root. NOT compiled/tested here (no Gradle in sandbox). First run:
  ./gradlew :app:testDebugUnitTest :app:assembleDebug

## Done
- Bottom nav is theme-aware. Classic = original 6 items, unchanged. Blue Glass = Home, Chat, Browser | MIC | Devices, Settings.
  Mic button is real: no permission -> permissions screen; live -> toggleMute(); otherwise connect(). Shows muted state.
  APIs Hub + Logs stay reachable via Home shortcut cards (Blue Glass) and Settings.
- New ChatScreen (route "chat"): real MainViewModel.sendUserMessage + live transcripts, copy, retry-last, clear (on-screen only),
  offline/error banner with reconnect, mic + send. No fake typing indicator.
- Home (VoiceScreen): header logo, hero mic and halo follow theme; Blue Glass adds shortcut cards (Browser, Smart Home, APIs Hub, Logs).
  Orb state colours per theme (MyraOrbState.colorFor); Classic colours unchanged.
- GlassComponents: buttons, circle buttons, chips, search/inputs, switch borders, shadows now read theme tokens.
- Browser: accents (shadows, sliders, gradients) follow theme. Engine untouched.
- Tests added: ThemeTokensTest.

## Not done / known limits
- Browser chrome redesign (tabs/history UI, quick-launch grid, voice prompts) NOT done; browser wallpaper is the user's own setting and stays.
- The WebGL orb (assets/orb/curl_noise_orb.html) keeps its own colours; only the native halo/chip recolours. Overlay orbs untouched.
- Text colours in GlassComponents still use the old charcoal/grey (readable on both themes).
- Chat shows only the current session; persistent history goes in the Memory screen (Phase 4).
- Classic has no nav entry for Chat (kept identical to before).
