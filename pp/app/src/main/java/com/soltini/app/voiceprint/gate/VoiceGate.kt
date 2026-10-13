package com.soltini.app.voiceprint.gate

import android.util.Log
import com.soltini.app.BuildConfig
import com.soltini.app.voiceprint.audio.PcmRingBuffer
import com.soltini.app.voiceprint.domain.VoiceprintVerifier
import com.soltini.app.voiceprint.dsp.EnergyVad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * VoiceGateMode
 *
 * Operational security mode for VoicePrint audio gating:
 * - OFF: Audio streams directly without verification. Zero latency.
 * - OWNER_ONLY: Audio is blocked until the speaker is verified as the owner.
 *   Uses 400ms pre-roll and 1.2s speech-end hangover.
 * - OWNER_FOR_SENSITIVE: All speakers can chat normally, but sensitive function calls
 *   (locks, relays, personal data) require owner voice verification within 10 seconds.
 */
enum class VoiceGateMode {
    OFF,
    OWNER_ONLY,
    OWNER_FOR_SENSITIVE;

    companion object {
        fun fromString(value: String?): VoiceGateMode {
            return when (value?.uppercase()) {
                "OFF" -> OFF
                "OWNER_ONLY" -> OWNER_ONLY
                "OWNER_FOR_SENSITIVE" -> OWNER_FOR_SENSITIVE
                else -> OWNER_FOR_SENSITIVE // Default
            }
        }
    }
}

/**
 * VoiceGate
 *
 * Sits between microphone capture (AudioRecorder) and the Gemini Live sender.
 * Controls audio passthrough based on speaker identity and operational mode.
 */
class VoiceGate(
    val verifier: VoiceprintVerifier,
    private val forwardToConsumer: (ByteArray) -> Unit,
    private val vad: EnergyVad = EnergyVad()
) {
    companion object {
        private const val TAG = "VoiceGate"
        const val PRE_ROLL_MS = 400
        const val HANGOVER_MS = 1200L
    }

    var mode: VoiceGateMode = VoiceGateMode.OWNER_FOR_SENSITIVE
    var isModelSpeaking: Boolean = false

    private val preRollBuffer = PcmRingBuffer(capacitySamples = 32000)

    // Non-blocking channel for passing audio chunks to the background verification worker
    // DROPS oldest frames if inference falls behind so audio capture thread NEVER blocks.
    private val inferenceChannel = Channel<ShortArray>(
        capacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val gateScope = CoroutineScope(Dispatchers.Default + Job())
    private var workerJob: Job? = null

    private val _isGateOpen = MutableStateFlow(false)
    val isGateOpen: StateFlow<Boolean> = _isGateOpen.asStateFlow()

    private var lastSpeechTimestamp: Long = 0L
    private var speechOnsetTimestampNanos: Long = 0L

    init {
        startInferenceWorker()
    }

    private fun startInferenceWorker() {
        workerJob?.cancel()
        workerJob = gateScope.launch {
            for (chunk in inferenceChannel) {
                if (!isActive) break
                verifier.processLiveSamples(chunk)
            }
        }
    }

    /**
     * Intercepts incoming mic audio chunk (16kHz 16-bit mono PCM).
     * Guaranteed to be non-blocking.
     */
    fun onAudioChunkCaptured(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return

        // FAIL-OPEN: without a speaker model + enrolled profile nobody can ever be verified,
        // so OWNER_ONLY would drop 100% of the audio and the assistant would never hear anything.
        if (mode != VoiceGateMode.OFF && !verifier.isReady) {
            _isGateOpen.value = true
            forwardToConsumer(pcmBytes)
            return
        }

        when (mode) {
            VoiceGateMode.OFF -> {
                _isGateOpen.value = true
                forwardToConsumer(pcmBytes)
            }

            VoiceGateMode.OWNER_FOR_SENSITIVE -> {
                // Pass all audio through so everyone can chat
                _isGateOpen.value = true
                forwardToConsumer(pcmBytes)

                // Dispatch to verifier in background for sensitive-action tracking
                val shorts = bytesToShorts(pcmBytes)
                inferenceChannel.trySend(shorts)
            }

            VoiceGateMode.OWNER_ONLY -> {
                handleOwnerOnlyMode(pcmBytes)
            }
        }
    }

    private fun handleOwnerOnlyMode(pcmBytes: ByteArray) {
        val now = System.currentTimeMillis()
        val shorts = bytesToShorts(pcmBytes)

        // 1. Always record into the 400ms pre-roll ring buffer
        preRollBuffer.write(shorts)

        // 2. Measure speech energy
        val isSpeech = isEnergySpeech(shorts)
        if (isSpeech) {
            if (speechOnsetTimestampNanos == 0L) {
                speechOnsetTimestampNanos = System.nanoTime()
            }
            lastSpeechTimestamp = now
        }

        // 3. Prevent model audio echo from opening the gate
        // Barge-in is only allowed if verifier actually confirms the owner
        val isVerifiedOwner = verifier.state.value.state == VoiceprintVerifier.VerificationStateEnum.OWNER

        // Queue audio to verifier non-blockingly
        inferenceChannel.trySend(shorts)

        val gateCurrentlyOpen = _isGateOpen.value

        if (!gateCurrentlyOpen) {
            // Check if verifier just authorized the owner
            if (isVerifiedOwner) {
                val onsetLatencyMs = if (speechOnsetTimestampNanos > 0L) {
                    (System.nanoTime() - speechOnsetTimestampNanos) / 1_000_000
                } else 0L

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "VoiceGate OPENED for Owner (latency: ${onsetLatencyMs}ms)")
                }

                _isGateOpen.value = true
                // Flush the 400ms pre-roll buffer so speech onset is preserved
                val preRoll = preRollBuffer.getPreRollBytes(PRE_ROLL_MS)
                if (preRoll.isNotEmpty()) {
                    forwardToConsumer(preRoll)
                }
                forwardToConsumer(pcmBytes)
            } else {
                // Gate closed: send nothing
            }
        } else {
            // Gate is already open
            val timeSinceSpeech = now - lastSpeechTimestamp

            if (isVerifiedOwner || timeSinceSpeech <= HANGOVER_MS) {
                forwardToConsumer(pcmBytes)
            } else {
                // Hangover expired and speech ended -> close gate
                _isGateOpen.value = false
                speechOnsetTimestampNanos = 0L
                Log.d(TAG, "VoiceGate CLOSED after hangover expiry")
            }
        }
    }

    private fun isEnergySpeech(shorts: ShortArray): Boolean {
        var sum = 0.0
        for (s in shorts) {
            val f = s.toFloat() / 32768.0f
            sum += f * f
        }
        val rms = kotlin.math.sqrt(sum / shorts.size)
        return rms > 0.015f
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val count = bytes.size / 2
        val shorts = ShortArray(count)
        var b = 0
        for (i in 0 until count) {
            val b0 = bytes[b].toInt() and 0xFF
            val b1 = bytes[b + 1].toInt()
            shorts[i] = ((b1 shl 8) or b0).toShort()
            b += 2
        }
        return shorts
    }

    fun release() {
        workerJob?.cancel()
        inferenceChannel.close()
    }
}
