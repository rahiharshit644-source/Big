# Phase 4 (cumulative: includes Phase 2 + 3 files, so this zip alone is enough)
Unzip over project root. NOT compiled/tested here (no Gradle in sandbox). Run first:
  ./gradlew :app:testDebugUnitTest :app:assembleDebug

## New screens (real data only)
- WeatherScreen (route "weather"): location permission flow (coarse), or typed city -> existing PublicApiExecutor.fetchWeather (wttr.in).
  Honest states: loading / permission denied / no location / API error. No hidden default city, no sample numbers. Air quality is NOT shown (source doesn't provide it).
  weather/WeatherParser.kt is unit-tested (WeatherParserTest, Robolectric).
- MemoryScreen (route "memory"): reads real Memory2Database. Tabs: Saved memories (persistent) vs Chat history. Search, type filters,
  detail, edit, delete with confirmation. SENSITIVE memories are masked until tapped. Deleting chat-history rows does not touch the permanent archive.
- Home (Blue Glass) gets Weather + Memory shortcut cards.

## Existing screens now follow the theme (colour tokens only, layout/logic untouched)
Smart Home, Add Device, Voice ID/enrollment, Voice profiles, Permissions, Settings (Control Center menu + all sections), Storage/RAG,
Orb settings, Public APIs Hub, Conversation memory card.

## NOT done (be aware)
- These existing screens were NOT rebuilt to match the reference layouts (e.g. Smart Home "Quick Controls" row, Permissions toggle list,
  Notifications app list, API & Persona rows). They keep their current structure and features, recoloured via tokens.
- Weather and Memory screens are reachable from the Blue Glass Home shortcuts only (Classic unchanged).
- Floating overlay orbs, WebGL orb colours and browser chrome redesign still pending.
- Nothing has been run on a device.
