package com.soltini.app.audio

import android.content.Context
import android.media.AudioManager
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.Locale

/**
 * Offline wake-word listener ("Myra", "Hey Myra", ...) built on Vosk.
 *
 * Used while Myra is in standby: the Gemini connection is closed and only this
 * lightweight on-device recognizer owns the microphone. Detection is deliberately
 * strict: a restricted phrase grammar, word-level confidence checks and a short
 * cooldown after entering standby, so TV/background speech rarely wakes her.
 *
 * Only one mic consumer may run at a time: callers must [stop] this engine before
 * starting the Gemini [AudioRecorder] again.
 */
class WakeWordEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val onWake: () -> Unit
) {
    companion object {
        private const val TAG = "WakeWordEngine"
        private const val SAMPLE_RATE = 16000.0f
        private const val START_COOLDOWN_MS = 800L
        private const val MIN_NAME_CONF = 0.70
        private const val MIN_SINGLE_WORD_CONF = 0.75
        private const val MIN_PHRASE_CONF = 0.65

        /** Spellings Vosk may produce for the name. All are in the bundled vocabulary. */
        private val NAME_VARIANTS = listOf("myra", "maira", "meera", "mayra")
        private val PREFIXES = listOf(
            "", "hey ", "hi ", "hello ", "ok ", "okay ", "wake up ", "sun ", "suno "
        )

        private fun buildPhrases(): List<String> {
            val out = LinkedHashSet<String>()
            for (name in NAME_VARIANTS) {
                for (prefix in PREFIXES) out.add("$prefix$name")
                out.add("$name $name")
            }
            return out.toList()
        }

        private val PHRASES = buildPhrases()
    }

    private val appContext = context.applicationContext
    private var speechService: SpeechService? = null
    private var waitModelJob: Job? = null
    private var restartJob: Job? = null
    @Volatile private var wanted = false
    @Volatile private var startedAt = 0L

    val isListening: Boolean get() = speechService != null

    /** Begin (or resume) wake-word listening. Safe to call repeatedly. */
    fun start() {
        wanted = true
        startInternal()
    }

    /** Stop listening and release the microphone. Safe to call repeatedly. */
    fun stop() {
        wanted = false
        waitModelJob?.cancel(); waitModelJob = null
        restartJob?.cancel(); restartJob = null
        releaseService()
    }

    private fun releaseService() {
        val svc = speechService ?: return
        speechService = null
        try { svc.stop() } catch (e: Exception) { Log.w(TAG, "stop failed: ${e.message}") }
        try { svc.shutdown() } catch (e: Exception) { Log.w(TAG, "shutdown failed: ${e.message}") }
    }

    private fun startInternal() {
        if (!wanted || speechService != null) return

        val model = VoskModelManager.model
        if (model == null) {
            // Model still unpacking (first run) - wait for it, then start.
            VoskModelManager.init(appContext, scope)
            if (waitModelJob?.isActive == true) return
            waitModelJob = scope.launch(Dispatchers.Main) {
                VoskModelManager.isModelReady.first { it }
                if (wanted && speechService == null) startInternal()
            }
            return
        }

        try {
            val grammar = JSONArray().apply {
                PHRASES.forEach { put(it) }
                put("[unk]")
            }.toString()
            val recognizer = Recognizer(model, SAMPLE_RATE, grammar).apply { setWords(true) }
            val service = SpeechService(recognizer, SAMPLE_RATE)
            startedAt = System.currentTimeMillis()
            service.startListening(listener)
            speechService = service
            Log.i(TAG, "Wake-word listening active (${PHRASES.size} phrases)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake-word listening: ${e.message}", e)
            releaseService()
            scheduleRestart(3000)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!wanted) return
        restartJob?.cancel()
        restartJob = scope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (wanted && speechService == null) startInternal()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            // Partials are unstable; only final word-level results can wake Myra.
        }

        override fun onResult(hypothesis: String?) = handleHypothesis(hypothesis)
        override fun onFinalResult(hypothesis: String?) = handleHypothesis(hypothesis)

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Vosk error: ${exception?.message}")
            releaseService()
            scheduleRestart(1500)
        }

        override fun onTimeout() {
            releaseService()
            scheduleRestart(200)
        }
    }

    private fun handleHypothesis(hypothesisJson: String?) {
        if (hypothesisJson.isNullOrBlank() || !wanted) return
        if (isPhoneCallActive()) return
        if (System.currentTimeMillis() - startedAt < START_COOLDOWN_MS) return

        try {
            val json = JSONObject(hypothesisJson)
            val text = json.optString("text", "").trim().lowercase(Locale.ROOT)
            if (text.isEmpty() || text == "[unk]") return

            val words = json.optJSONArray("result")
            if (words == null || words.length() == 0) return  // never wake without confidences

            var totalConf = 0.0
            val wordList = mutableListOf<String>()
            var nameConf = 0.0
            for (i in 0 until words.length()) {
                val obj = words.getJSONObject(i)
                val w = obj.optString("word", "").trim().lowercase(Locale.ROOT)
                if (w.isEmpty() || w == "[unk]") continue
                val conf = obj.optDouble("conf", 0.0)
                totalConf += conf
                wordList.add(w)
                if (w in NAME_VARIANTS) nameConf = maxOf(nameConf, conf)
            }
            if (wordList.isEmpty()) return

            val phrase = wordList.joinToString(" ")
            if (phrase !in PHRASES && wordList.none { it in NAME_VARIANTS }) return

            if (nameConf < MIN_NAME_CONF) {
                Log.w(TAG, "Rejected '$phrase': name confidence $nameConf too low")
                return
            }
            val avgConf = totalConf / wordList.size
            val needed = if (wordList.size == 1) MIN_SINGLE_WORD_CONF else MIN_PHRASE_CONF
            if (avgConf < needed) {
                Log.w(TAG, "Rejected '$phrase': avg confidence $avgConf < $needed")
                return
            }

            Log.i(TAG, "Wake word confirmed: '$phrase' (conf=$avgConf)")
            onWake()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking hypothesis: ${e.message}")
        }
    }

    private fun isPhoneCallActive(): Boolean {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        if (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_RINGTONE) return true
        return try {
            val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm != null && tm.callState != TelephonyManager.CALL_STATE_IDLE
        } catch (_: SecurityException) { false } catch (_: Exception) { false }
    }
}
