package com.soltini.app.voiceprint.domain

import com.soltini.app.voiceprint.data.VoiceprintRepository
import com.soltini.app.voiceprint.dsp.EnergyVad
import com.soltini.app.voiceprint.ml.SpeakerEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * EnrollmentManager
 *
 * Coordinates 5-phrase VoicePrint enrollment with DSP quality gating,
 * speech duration validation via EnergyVad (minimum 3.0s active speech),
 * centroid computation, and leave-one-out threshold calibration.
 */
class EnrollmentManager(
    private val embedder: SpeakerEmbedder,
    private val repository: VoiceprintRepository,
    private val vad: EnergyVad = EnergyVad()
) {
    companion object {
        const val REQUIRED_PHRASE_COUNT = 5
        const val MIN_SPEECH_DURATION_MS = 3000L // 3.0s of detected speech required

        val ENROLLMENT_PHRASES = listOf(
            "Hey Myra, what's on my schedule today?",
            "Myra, turn on the lights in the living room",
            "Sun, aaj ka mausam kaisa hai aur baahar kya chal raha hai?",
            "Myra, check my unread messages and notifications",
            "Mera voice print create karo aur securely verify karo"
        )

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
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

        fun l2Normalize(vector: FloatArray): FloatArray {
            var sumSquares = 0.0
            for (v in vector) sumSquares += (v * v)
            val norm = sqrt(sumSquares).toFloat()
            if (norm < 1e-9f) return vector
            val out = FloatArray(vector.size)
            for (i in vector.indices) out[i] = vector[i] / norm
            return out
        }

        /**
         * Calibrates verification threshold from enrollment embeddings using leave-one-out cross validation.
         */
        fun calibrateThreshold(embeddings: List<FloatArray>): Float {
            if (embeddings.size < 2) return 0.72f

            val n = embeddings.size
            val scores = FloatArray(n)

            for (i in 0 until n) {
                // Compute centroid of the other n - 1 vectors
                val dim = embeddings[0].size
                val tempCentroid = FloatArray(dim)
                for (j in 0 until n) {
                    if (j == i) continue
                    for (d in 0 until dim) {
                        tempCentroid[d] += embeddings[j][d]
                    }
                }
                val normalizedLeaveOneOut = l2Normalize(tempCentroid)
                scores[i] = cosineSimilarity(embeddings[i], normalizedLeaveOneOut)
            }

            var sum = 0f
            for (s in scores) sum += s
            val mean = sum / n

            var sumSqDiff = 0f
            for (s in scores) {
                val diff = s - mean
                sumSqDiff += diff * diff
            }
            val stdDev = sqrt(sumSqDiff / n)

            // Subtract margin: clamp between 0.62 and 0.85
            val margin = max(0.06f, 2.5f * stdDev)
            val rawThreshold = mean - margin
            return rawThreshold.coerceIn(0.62f, 0.85f)
        }
    }

    sealed class StepResult {
        object Idle : StepResult()
        object Recording : StepResult()
        object Checking : StepResult()
        data class Accepted(val stepIndex: Int, val speechDurationMs: Long) : StepResult()
        data class Rejected(val stepIndex: Int, val reason: String) : StepResult()
        data class Completed(val calibratedThreshold: Float, val profileId: Long) : StepResult()
    }

    private val collectedEmbeddings = mutableListOf<FloatArray>()
    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _stepStatus = MutableStateFlow<StepResult>(StepResult.Idle)
    val stepStatus: StateFlow<StepResult> = _stepStatus.asStateFlow()

    fun reset() {
        collectedEmbeddings.clear()
        _currentStep.value = 0
        _stepStatus.value = StepResult.Idle
    }

    /**
     * Evaluates audio quality and extracts embedding for the current enrollment step.
     */
    suspend fun processAudioSample(pcm16k: ShortArray): StepResult = withContext(Dispatchers.IO) {
        val step = _currentStep.value
        _stepStatus.value = StepResult.Checking

        if (pcm16k.isEmpty()) {
            val res = StepResult.Rejected(step, "No audio data received. Check microphone.")
            _stepStatus.value = res
            return@withContext res
        }

        // 1. RMS Energy Check (too quiet?)
        var sumSquares = 0.0
        var clippedCount = 0
        for (sample in pcm16k) {
            val s = sample.toFloat() / 32768.0f
            sumSquares += (s * s)
            if (abs(sample.toInt()) >= 32700) {
                clippedCount++
            }
        }
        val rms = sqrt(sumSquares / pcm16k.size).toFloat()
        if (rms < 0.012f) {
            val res = StepResult.Rejected(step, "Too quiet (RMS: ${(rms * 100).toInt()}%). Please speak louder and closer to the mic.")
            _stepStatus.value = res
            return@withContext res
        }

        // 2. Clipping Check
        val clipRatio = clippedCount.toFloat() / pcm16k.size
        if (clipRatio > 0.015f) {
            val res = StepResult.Rejected(step, "Audio clipping detected. Please hold the phone a bit further away.")
            _stepStatus.value = res
            return@withContext res
        }

        // 3. VAD Speech Duration Check
        val vadResult = vad.analyze(pcm16k)
        if (vadResult.speechDurationMs < MIN_SPEECH_DURATION_MS) {
            val res = StepResult.Rejected(
                step,
                "Speech too short (${String.format("%.1f", vadResult.speechDurationMs / 1000.0)}s). Needs at least 3.0s of active speech. Speak the entire phrase."
            )
            _stepStatus.value = res
            return@withContext res
        }

        // 4. Extract Speaker Embedding from trimmed speech PCM
        val embedding: FloatArray
        try {
            embedding = embedder.embed(vadResult.speechPcm)
        } catch (e: Exception) {
            val res = StepResult.Rejected(step, "Feature extraction failed: ${e.message}")
            _stepStatus.value = res
            return@withContext res
        }

        collectedEmbeddings.add(embedding)
        val acceptedResult = StepResult.Accepted(step, vadResult.speechDurationMs)
        _stepStatus.value = acceptedResult

        val nextStep = step + 1
        _currentStep.value = nextStep

        // 5. Finalize if all phrases are complete
        if (nextStep >= REQUIRED_PHRASE_COUNT) {
            return@withContext finalizeEnrollment()
        }

        return@withContext acceptedResult
    }

    private suspend fun finalizeEnrollment(): StepResult = withContext(Dispatchers.IO) {
        val dim = collectedEmbeddings[0].size
        val centroidAccum = FloatArray(dim)
        for (vec in collectedEmbeddings) {
            for (d in 0 until dim) {
                centroidAccum[d] += vec[d]
            }
        }
        val centroid = l2Normalize(centroidAccum)
        val calibratedThreshold = calibrateThreshold(collectedEmbeddings)

        val profileId = repository.saveProfile(
            displayName = "Owner",
            centroid = centroid,
            enrollmentEmbeddings = collectedEmbeddings,
            calibratedThreshold = calibratedThreshold,
            modelVersion = "1.0"
        )

        val completed = StepResult.Completed(calibratedThreshold, profileId)
        _stepStatus.value = completed
        return@withContext completed
    }
}
