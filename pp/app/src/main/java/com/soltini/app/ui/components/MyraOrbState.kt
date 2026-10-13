package com.soltini.app.ui.components

import androidx.compose.ui.graphics.Color

/**
 * What Myra is doing right now, as shown by the main-screen orb, its halo and the status chip.
 *
 *  OFF         grey, almost still   -> Myra is not connected / not running
 *  CONNECTING  violet pulse         -> starting up / reconnecting
 *  LISTENING   cyan-blue, breathing -> Myra hears you (reacts to mic level)
 *  SPEAKING    pink-violet, fast    -> Myra is talking (reacts to speaker level)
 *  MUTED       amber                -> microphone muted
 *  PAUSED      slate, frozen        -> session paused (banking / privacy mode)
 */
enum class MyraOrbState(val js: String, val chipLabel: String, val color: Color) {
    OFF("off", "Myra OFF", Color(0xFF8E94A3)),
    CONNECTING("connecting", "Connecting…", Color(0xFF8B5CF6)),
    LISTENING("listening", "Listening", Color(0xFF16A9DC)),
    SPEAKING("speaking", "Speaking", Color(0xFFE0489E)),
    MUTED("muted", "Muted", Color(0xFFE59524)),
    PAUSED("paused", "Paused", Color(0xFF7C8AAE))
}
