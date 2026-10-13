package com.soltini.app.voiceprint.ml

/**
 * SpeakerEmbedder
 *
 * Model-agnostic interface for extracting L2-normalized speaker voiceprint embeddings
 * from 16kHz mono audio.
 */
interface SpeakerEmbedder {
    /**
     * Embedding dimension (e.g. 192, 256, 512).
     */
    val embeddingDim: Int

    /**
     * Whether the underlying ONNX model weights and session are ready.
     */
    val isAvailable: Boolean

    /**
     * Human-readable status of the model (e.g. "Ready", "Model missing", "Initialized").
     */
    val statusDescription: String

    /**
     * Extracts an L2-normalized embedding vector from 16kHz mono PCM16 audio.
     *
     * @param pcm16k ShortArray of 16kHz audio samples.
     * @return FloatArray of length [embeddingDim] with Euclidean norm = 1.0.
     * @throws IllegalStateException if model is not available or input is invalid.
     */
    suspend fun embed(pcm16k: ShortArray): FloatArray

    /**
     * Releases ONNX sessions and native resources.
     */
    fun close()
}
