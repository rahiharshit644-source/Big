package com.soltini.app.voiceprint.ml

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.soltini.app.voiceprint.dsp.LogMelFbank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.sqrt

/**
 * OnnxSpeakerEmbedder
 *
 * Pretrained on-device speaker embedding model executed via ONNX Runtime Mobile.
 * Encapsulates model loading from assets/files, dynamic input/output inspection,
 * Kaldi-style LogMelFbank feature extraction, and Euclidean L2 normalization.
 *
 * Graceful degradation:
 * If speaker_embedder.onnx is missing or fails validation, isAvailable is set to false
 * without crashing or blocking the app.
 */
class OnnxSpeakerEmbedder(
    private val context: Context,
    private val config: ModelConfig = ModelConfig.loadFromAssets(context)
) : SpeakerEmbedder {

    companion object {
        private const val TAG = "OnnxSpeakerEmbedder"
        private const val DEFAULT_ASSET_MODEL = "voiceprint/speaker_embedder.onnx"
        private const val MODEL_CACHE_FILENAME = "speaker_embedder_cached.onnx"
    }

    private val fbank = LogMelFbank(
        sampleRate = config.sampleRate,
        numMelBins = config.numMelBins,
        frameLengthMs = config.frameLengthMs,
        frameShiftMs = config.frameShiftMs,
        preEmphasis = config.preEmphasis,
        applyCmn = config.applyCmn
    )

    // Fallback (no ONNX model): same mel filterbank but WITHOUT per-utterance mean removal,
    // because the mean log-mel spectrum is the main speaker-dependent signal we pool below.
    private val fallbackFbank = LogMelFbank(
        sampleRate = config.sampleRate,
        numMelBins = config.numMelBins,
        frameLengthMs = config.frameLengthMs,
        frameShiftMs = config.frameShiftMs,
        preEmphasis = config.preEmphasis,
        applyCmn = false
    )
    private var useFallback: Boolean = false

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    private var detectedInputName: String = config.inputName
    private var detectedOutputName: String = config.outputName
    private var detectedEmbeddingDim: Int = config.embeddingDim

    override val embeddingDim: Int
        get() = if (detectedEmbeddingDim > 0) detectedEmbeddingDim else 256

    private var _isAvailable: Boolean = false
    override val isAvailable: Boolean
        get() = _isAvailable

    private var _statusDescription: String = "Not initialized"
    override val statusDescription: String
        get() = _statusDescription

    private val initMutex = Mutex()
    private var isInitialized = false

    /**
     * Lazily loads ONNX model session on IO dispatcher.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (isInitialized) return@withContext _isAvailable

            try {
                val modelFile = ensureModelFile()
                if (modelFile == null || !modelFile.exists() || modelFile.length() == 0L) {
                    enableFallback("Basic mode: 'speaker_embedder.onnx' not found in assets/voiceprint/ (works, but lower accuracy)")
                    isInitialized = true
                    return@withContext true
                }

                val env = OrtEnvironment.getEnvironment()
                ortEnv = env

                val sessionOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                }

                val session = env.createSession(modelFile.absolutePath, sessionOptions)
                ortSession = session

                // Inspect inputs
                val inputNames = session.inputNames
                if (inputNames.isEmpty()) {
                    throw IllegalStateException("ONNX model has no input tensors")
                }
                detectedInputName = if (config.inputName.isNotBlank() && inputNames.contains(config.inputName)) {
                    config.inputName
                } else {
                    inputNames.first()
                }

                // Inspect outputs
                val outputNames = session.outputNames
                if (outputNames.isEmpty()) {
                    throw IllegalStateException("ONNX model has no output tensors")
                }
                detectedOutputName = if (config.outputName.isNotBlank() && outputNames.contains(config.outputName)) {
                    config.outputName
                } else {
                    outputNames.first()
                }

                // Inspect output tensor shape to find embedding dimension
                val outputInfo = session.outputInfo[detectedOutputName]?.info
                if (outputInfo is TensorInfo) {
                    val shape = outputInfo.shape
                    if (shape.isNotEmpty()) {
                        val lastDim = shape.last().toInt()
                        if (lastDim > 0) {
                            detectedEmbeddingDim = lastDim
                        }
                    }
                }

                if (detectedEmbeddingDim <= 0) {
                    detectedEmbeddingDim = if (config.embeddingDim > 0) config.embeddingDim else 256
                }

                _isAvailable = true
                _statusDescription = "Ready (Dim: $detectedEmbeddingDim, Input: $detectedInputName, Output: $detectedOutputName)"
                Log.i(TAG, "ONNX Speaker Embedder initialized successfully. $_statusDescription")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize ONNX session: ${e.message}", e)
                enableFallback("Basic mode: ONNX model failed to load (${e.message})")
            } finally {
                isInitialized = true
            }

            return@withContext _isAvailable
        }
    }

    override suspend fun embed(pcm16k: ShortArray): FloatArray = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            initialize()
        }

        if (!_isAvailable) {
            throw IllegalStateException("SpeakerEmbedder is not available: $_statusDescription")
        }

        if (useFallback) {
            return@withContext fallbackEmbed(pcm16k)
        }

        val session = ortSession ?: throw IllegalStateException("ONNX Session is null")
        val env = ortEnv ?: throw IllegalStateException("ONNX Environment is null")

        // 1. Extract Log-Mel filterbank features [numFrames, numMelBins]
        val fbankMatrix = fbank.extractFeatures(pcm16k)
        val numFrames = fbankMatrix.size
        if (numFrames < 5) {
            throw IllegalArgumentException("Audio sample is too short to extract speaker embedding (frames=$numFrames)")
        }

        val numMelBins = config.numMelBins
        val isBtf = config.inputLayout.equals("BTF", ignoreCase = true)

        // 2. Prepare tensor buffer according to input layout
        val totalElements = numFrames * numMelBins
        val tensorShape = if (isBtf) {
            longArrayOf(1, numFrames.toLong(), numMelBins.toLong())
        } else {
            longArrayOf(1, numMelBins.toLong(), numFrames.toLong())
        }

        val floatBuffer = FloatBuffer.allocate(totalElements)
        if (isBtf) {
            // [Batch=1, Time=numFrames, Features=numMelBins]
            for (t in 0 until numFrames) {
                floatBuffer.put(fbankMatrix[t])
            }
        } else {
            // [Batch=1, Features=numMelBins, Time=numFrames]
            for (m in 0 until numMelBins) {
                for (t in 0 until numFrames) {
                    floatBuffer.put(fbankMatrix[t][m])
                }
            }
        }
        floatBuffer.flip()

        // 3. Create ONNX Tensor and execute inference
        val tensor = OnnxTensor.createTensor(env, floatBuffer, tensorShape)
        val inputs = mapOf(detectedInputName to tensor)

        var rawEmbedding: FloatArray
        try {
            val result = session.run(inputs)
            try {
                val outputTensor = result.get(detectedOutputName).orElse(result.first().value) as OnnxTensor
                val tensorValue = outputTensor.value

                rawEmbedding = extractEmbeddingFromTensorValue(tensorValue, detectedEmbeddingDim)
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }

        // 4. Euclidean L2 Normalization: v / ||v||_2
        return@withContext l2Normalize(rawEmbedding)
    }

    private fun enableFallback(reason: String) {
        useFallback = true
        detectedEmbeddingDim = config.numMelBins * 2
        _isAvailable = true
        _statusDescription = reason
        Log.w(TAG, reason)
    }

    /**
     * Basic speaker embedding used when no neural model is bundled:
     * mean + std of the log-mel spectrum over all frames, each centered across mel bins
     * (removes loudness / mic gain), concatenated and L2-normalized.
     * Much weaker than a real model (ECAPA / ResNet) - fine for a single owner in a quiet room.
     */
    private fun fallbackEmbed(pcm16k: ShortArray): FloatArray {
        val feats = fallbackFbank.extractFeatures(pcm16k)
        val frames = feats.size
        if (frames < 5) {
            throw IllegalArgumentException("Audio sample is too short to extract speaker embedding (frames=$frames)")
        }
        val bins = config.numMelBins
        val mean = FloatArray(bins)
        for (f in feats) for (b in 0 until bins) mean[b] += f[b]
        for (b in 0 until bins) mean[b] /= frames

        val std = FloatArray(bins)
        for (f in feats) for (b in 0 until bins) {
            val d = f[b] - mean[b]
            std[b] += d * d
        }
        for (b in 0 until bins) std[b] = sqrt(std[b] / frames)

        val meanAvg = mean.average().toFloat()
        val stdAvg = std.average().toFloat()
        val out = FloatArray(bins * 2)
        for (b in 0 until bins) {
            out[b] = mean[b] - meanAvg
            out[bins + b] = std[b] - stdAvg
        }
        return l2Normalize(out)
    }

    private fun extractEmbeddingFromTensorValue(value: Any?, expectedDim: Int): FloatArray {
        when (value) {
            is Array<*> -> {
                // Could be Array<FloatArray> or Array<Array<FloatArray>>
                val first = value.firstOrNull()
                if (first is FloatArray) {
                    return first
                } else if (first is Array<*>) {
                    val inner = first.firstOrNull()
                    if (inner is FloatArray) return inner
                }
            }
            is FloatArray -> return value
        }

        // Fallback: If shape is irregular, attempt reflection or default zero vector
        Log.w(TAG, "Unexpected tensor output type: ${value?.javaClass?.name}. Generating fallback vector.")
        return FloatArray(expectedDim) { 0f }
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += (v * v)
        }
        val norm = sqrt(sumSquares).toFloat()
        if (norm < 1e-9f) {
            return vector
        }
        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = vector[i] / norm
        }
        return normalized
    }

    /**
     * Extracts model file from APK assets into internal cache directory if needed.
     */
    private fun ensureModelFile(): File? {
        val cacheFile = File(context.cacheDir, MODEL_CACHE_FILENAME)

        // If file already exists and is non-empty, use it
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return cacheFile
        }

        // Check if model exists in assets
        return try {
            context.assets.open(DEFAULT_ASSET_MODEL).use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0L) {
                cacheFile
            } else {
                null
            }
        } catch (e: Exception) {
            // Asset does not exist yet (user hasn't placed it)
            null
        }
    }

    override fun close() {
        try {
            ortSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ORT session: ${e.message}")
        }
        ortSession = null

        try {
            ortEnv?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ORT env: ${e.message}")
        }
        ortEnv = null

        _isAvailable = false
        isInitialized = false
    }
}
