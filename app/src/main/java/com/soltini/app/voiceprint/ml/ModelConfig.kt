package com.soltini.app.voiceprint.ml

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * ModelConfig
 *
 * Configuration for on-device ONNX speaker embedding model and audio feature pipeline.
 * Reads from `assets/voiceprint/voiceprint_model.json` or fallback defaults.
 */
data class ModelConfig(
    val sampleRate: Int = 16000,
    val numMelBins: Int = 80,
    val frameLengthMs: Int = 25,
    val frameShiftMs: Int = 10,
    val preEmphasis: Float = 0.97f,
    val applyCmn: Boolean = true,
    val embeddingDim: Int = 0, // 0 = auto-detect from ONNX output tensor
    val minSpeechMs: Int = 1500,
    val inputLayout: String = "BTF", // "BTF" (Batch, Time, Features) or "BFT" (Batch, Features, Time)
    val inputName: String = "",      // Empty = auto-detect from session
    val outputName: String = ""      // Empty = auto-detect from session
) {
    companion object {
        const val DEFAULT_ASSET_PATH = "voiceprint/voiceprint_model.json"
        const val DEFAULT_MODEL_ASSET = "voiceprint/speaker_embedder.onnx"

        fun fromJson(jsonStr: String): ModelConfig {
            return try {
                val json = JSONObject(jsonStr)
                ModelConfig(
                    sampleRate = json.optInt("sampleRate", 16000),
                    numMelBins = json.optInt("numMelBins", 80),
                    frameLengthMs = json.optInt("frameLengthMs", 25),
                    frameShiftMs = json.optInt("frameShiftMs", 10),
                    preEmphasis = json.optDouble("preEmphasis", 0.97).toFloat(),
                    applyCmn = json.optBoolean("applyCmn", true),
                    embeddingDim = json.optInt("embeddingDim", 0),
                    minSpeechMs = json.optInt("minSpeechMs", 1500),
                    inputLayout = json.optString("inputLayout", "BTF"),
                    inputName = json.optString("inputName", ""),
                    outputName = json.optString("outputName", "")
                )
            } catch (e: Exception) {
                ModelConfig()
            }
        }

        fun loadFromAssets(context: Context, assetPath: String = DEFAULT_ASSET_PATH): ModelConfig {
            return try {
                context.assets.open(assetPath).use { stream: InputStream ->
                    val text = stream.bufferedReader().use { it.readText() }
                    fromJson(text)
                }
            } catch (e: Exception) {
                ModelConfig()
            }
        }
    }
}
