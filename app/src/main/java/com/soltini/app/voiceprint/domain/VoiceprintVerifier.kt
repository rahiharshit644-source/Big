package com.soltini.app.voiceprint.domain

import android.util.Log
import com.soltini.app.voiceprint.data.VoiceprintRepository
import com.soltini.app.voiceprint.dsp.EnergyVad
import com.soltini.app.voiceprint.ml.SpeakerEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * VoiceprintVerifier
 *
 * Real-time sliding window speaker verification engine with EMA score smoothing
 * and hysteresis state transitions (OWNER, NOT_OWNER, UNKNOWN) to prevent flickering.
 */
class VoiceprintVerifier(
    private val embedder: SpeakerEmbedder,
    private val repository: VoiceprintRepository,
    private val vad: EnergyVad = EnergyVad(),
    private val livenessChecker: LivenessChecker = NoOpLivenessChecker()
) {
    companion object {
        private const val TAG = "VoiceprintVerifier"
        const val WINDOW_SAMPLES = 32000 // 2.0s at 16kHz
        const val HOP_SAMPLES = 8000     // 0.5s hop
        const val HYSTERESIS_MARGIN = 0.08f
        const val EMA_ALPHA = 0.65f
    }

    enum class VerificationStateEnum {
        UNKNOWN,
        OWNER,
        NOT_OWNER
    }

    data class VerificationState(
        val score: Float = 0f,
        val rawScore: Float = 0f,
        val state: VerificationStateEnum = VerificationStateEnum.UNKNOWN,
        val lastVerifiedAtMillis: Long = 0L,
        val effectiveThreshold: Float = 0.72f
    )

    private val _state = MutableStateFlow(VerificationState())
    val state: StateFlow<VerificationState> = _state.asStateFlow()

    // Sliding audio window buffer
    private val windowBuffer = ShortArray(WINDOW_SAMPLES)
    private var bufferSamplesCount = 0

    // Cached centroid and threshold
    private var cachedCentroid: FloatArray? = null
    private var calibratedThreshold: Float = 0.72f
    var sensitivityOffset: Float = 0.0f // [-0.15f .. +0.15f]

    private val processingMutex = Mutex()

    /**
     * True only when an ONNX model is loaded AND an owner profile is enrolled.
     * When false, nothing can be verified, so gates/guards must fail open instead of
     * silently blocking the microphone / every command.
     */
    val isReady: Boolean
        get() = embedder.isAvailable && cachedCentroid != null

    /**
     * Loads or refreshes the enrolled owner profile from database.
     */
    suspend fun reloadProfile(): Boolean = withContext(Dispatchers.IO) {
        processingMutex.withLock {
            val entity = repository.getActiveProfile()
            if (entity != null) {
                cachedCentroid = repository.getActiveCentroid()
                calibratedThreshold = entity.calibratedThreshold
                val eff = (calibratedThreshold + sensitivityOffset).coerceIn(0.50f, 0.95f)
                _state.value = _state.value.copy(effectiveThreshold = eff)
                Log.i(TAG, "Loaded owner profile: '${entity.displayName}', threshold: $calibratedThreshold (effective: $eff)")
                true
            } else {
                cachedCentroid = null
                _state.value = VerificationState()
                false
            }
        }
    }

    /**
     * Checks if the owner was verified within the last [withinMs] milliseconds.
     */
    fun isOwnerVerifiedWithin(withinMs: Long): Boolean {
        val current = _state.value
        if (current.state != VerificationStateEnum.OWNER) return false
        val elapsed = System.currentTimeMillis() - current.lastVerifiedAtMillis
        return elapsed <= withinMs
    }

    /**
     * Appends live audio samples, extracts speech, and triggers inference when sliding window fills.
     */
    suspend fun processLiveSamples(pcmChunk: ShortArray) = withContext(Dispatchers.Default) {
        val centroid = cachedCentroid ?: return@withContext
        if (pcmChunk.isEmpty()) return@withContext

        // Run light VAD filter on chunk
        val speechChunk = vad.filterSpeech(pcmChunk)
        if (speechChunk.isEmpty()) return@withContext

        var samplesToProcess: ShortArray? = null

        processingMutex.withLock {
            var srcOffset = 0
            var remaining = speechChunk.size
            while (remaining > 0) {
                val availableSpace = WINDOW_SAMPLES - bufferSamplesCount
                val toCopy = kotlin.math.min(remaining, availableSpace)
                System.arraycopy(speechChunk, srcOffset, windowBuffer, bufferSamplesCount, toCopy)
                bufferSamplesCount += toCopy
                srcOffset += toCopy
                remaining -= toCopy

                if (bufferSamplesCount >= WINDOW_SAMPLES) {
                    samplesToProcess = windowBuffer.clone()
                    // Shift buffer left by HOP_SAMPLES
                    val keepCount = WINDOW_SAMPLES - HOP_SAMPLES
                    System.arraycopy(windowBuffer, HOP_SAMPLES, windowBuffer, 0, keepCount)
                    bufferSamplesCount = keepCount
                    break
                }
            }
        }

        samplesToProcess?.let { window ->
            verifyWindow(window, centroid)
        }
    }

    /**
     * Executes feature extraction and classification on a complete 2.0s window.
     */
    private suspend fun verifyWindow(window: ShortArray, centroid: FloatArray) {
        try {
            // Anti-spoofing check
            val isLive = livenessChecker.checkLiveness(window)
            if (!isLive) {
                Log.w(TAG, "Liveness check failed on audio window")
                return
            }

            val embedding = embedder.embed(window)
            val rawSim = cosineSimilarity(embedding, centroid)

            val currentState = _state.value
            val prevEma = currentState.score
            val smoothedScore = if (prevEma == 0f) rawSim else (EMA_ALPHA * rawSim + (1f - EMA_ALPHA) * prevEma)

            val acceptThreshold = (calibratedThreshold + sensitivityOffset).coerceIn(0.50f, 0.95f)
            val rejectThreshold = acceptThreshold - HYSTERESIS_MARGIN

            val newStateEnum = when (currentState.state) {
                VerificationStateEnum.OWNER -> {
                    if (smoothedScore >= rejectThreshold) VerificationStateEnum.OWNER else VerificationStateEnum.NOT_OWNER
                }
                VerificationStateEnum.NOT_OWNER, VerificationStateEnum.UNKNOWN -> {
                    if (smoothedScore >= acceptThreshold) VerificationStateEnum.OWNER
                    else if (smoothedScore < rejectThreshold) VerificationStateEnum.NOT_OWNER
                    else VerificationStateEnum.UNKNOWN
                }
            }

            val now = System.currentTimeMillis()
            val lastVerifiedTime = if (newStateEnum == VerificationStateEnum.OWNER) now else currentState.lastVerifiedAtMillis

            _state.value = VerificationState(
                score = smoothedScore,
                rawScore = rawSim,
                state = newStateEnum,
                lastVerifiedAtMillis = lastVerifiedTime,
                effectiveThreshold = acceptThreshold
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in verifyWindow: ${e.message}")
        }
    }

    /**
     * Explicit verification of a single standalone audio clip (e.g. for Test Panel).
     */
    suspend fun verifyUtterance(pcm16k: ShortArray): VerificationState = withContext(Dispatchers.IO) {
        val centroid = cachedCentroid ?: run {
            reloadProfile()
            cachedCentroid
        } ?: return@withContext VerificationState()

        val speechPcm = vad.filterSpeech(pcm16k)
        val audio = if (speechPcm.isNotEmpty()) speechPcm else pcm16k

        val embedding = embedder.embed(audio)
        val score = cosineSimilarity(embedding, centroid)

        val acceptThreshold = (calibratedThreshold + sensitivityOffset).coerceIn(0.50f, 0.95f)
        val isOwner = score >= acceptThreshold

        val newState = VerificationState(
            score = score,
            rawScore = score,
            state = if (isOwner) VerificationStateEnum.OWNER else VerificationStateEnum.NOT_OWNER,
            lastVerifiedAtMillis = if (isOwner) System.currentTimeMillis() else 0L,
            effectiveThreshold = acceptThreshold
        )
        _state.value = newState
        newState
    }

    fun resetState() {
        bufferSamplesCount = 0
        _state.value = VerificationState(
            effectiveThreshold = (calibratedThreshold + sensitivityOffset).coerceIn(0.50f, 0.95f)
        )
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-9) (dot / denom).toFloat() else 0f
    }
}
