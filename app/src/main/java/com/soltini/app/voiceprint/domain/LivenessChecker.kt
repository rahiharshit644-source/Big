package com.soltini.app.voiceprint.domain

/**
 * LivenessChecker
 *
 * Interface for anti-spoofing and audio replay detection.
 * Designed to be pluggable for future machine learning anti-spoofing models.
 */
interface LivenessChecker {
    /**
     * Inspects 16kHz PCM audio for signs of synthetic vocoding, speaker replay, or spoofing.
     *
     * @param pcm16k ShortArray containing 16kHz mono audio.
     * @return true if the utterance is determined to be from a live human speaker.
     */
    suspend fun checkLiveness(pcm16k: ShortArray): Boolean
}

/**
 * NoOpLivenessChecker
 *
 * Default implementation for v1 (passes all samples).
 */
class NoOpLivenessChecker : LivenessChecker {
    override suspend fun checkLiveness(pcm16k: ShortArray): Boolean = true
}
