package com.soltini.app.voiceprint.dsp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * EnergyVad
 *
 * Adaptive energy Voice Activity Detector with hangover logic.
 * Detects speech start, speech end, active speech duration, and filters out silence/noise segments.
 */
class EnergyVad(
    val sampleRate: Int = 16000,
    val frameSizeMs: Int = 20,
    val minEnergyFloor: Float = 0.010f,
    val snrThresholdMultiplier: Float = 2.2f,
    val onsetHangoverFrames: Int = 3,
    val offsetHangoverFrames: Int = 18 // ~360ms hangover to prevent clipping word ends
) {
    val frameSamples: Int = (sampleRate * frameSizeMs) / 1000

    data class VadResult(
        val hasSpeech: Boolean,
        val speechDurationMs: Long,
        val totalDurationMs: Long,
        val speechSampleCount: Int,
        val speechPcm: ShortArray
    )

    /**
     * Checks if a single frame contains speech based on adaptive threshold.
     */
    fun isFrameSpeech(frame: ShortArray, noiseFloor: Float): Boolean {
        if (frame.isEmpty()) return false
        var sumSquares = 0.0
        for (sample in frame) {
            val s = sample.toFloat() / 32768.0f
            sumSquares += (s * s)
        }
        val rms = sqrt(sumSquares / frame.size).toFloat()
        val threshold = max(minEnergyFloor, noiseFloor * snrThresholdMultiplier)
        return rms >= threshold
    }

    /**
     * Analyzes an entire audio clip, extracts speech segments, and returns [VadResult].
     */
    fun analyze(pcm16k: ShortArray): VadResult {
        if (pcm16k.isEmpty()) {
            return VadResult(
                hasSpeech = false,
                speechDurationMs = 0L,
                totalDurationMs = 0L,
                speechSampleCount = 0,
                speechPcm = ShortArray(0)
            )
        }

        val totalFrames = pcm16k.size / frameSamples
        if (totalFrames == 0) {
            return VadResult(
                hasSpeech = false,
                speechDurationMs = 0L,
                totalDurationMs = (pcm16k.size * 1000L) / sampleRate,
                speechSampleCount = 0,
                speechPcm = ShortArray(0)
            )
        }

        // 1. Calculate RMS per frame and estimate initial noise floor from quietest frames
        val frameRms = FloatArray(totalFrames)
        for (f in 0 until totalFrames) {
            var sum = 0.0
            val offset = f * frameSamples
            for (i in 0 until frameSamples) {
                val s = pcm16k[offset + i].toFloat() / 32768.0f
                sum += (s * s)
            }
            frameRms[f] = sqrt(sum / frameSamples).toFloat()
        }

        // Noise floor: median of lowest 25% energy frames
        val sortedRms = frameRms.clone().apply { sort() }
        val noiseFloorIndex = max(0, totalFrames / 4)
        val noiseFloor = max(0.003f, sortedRms[noiseFloorIndex])
        val energyThreshold = max(minEnergyFloor, noiseFloor * snrThresholdMultiplier)

        // 2. Classify frames with onset and offset hangover
        val isSpeechFrame = BooleanArray(totalFrames)
        var speechOnsetCount = 0
        var hangoverLeft = 0
        var isCurrentlySpeaking = false

        for (f in 0 until totalFrames) {
            val rms = frameRms[f]
            if (rms >= energyThreshold) {
                speechOnsetCount++
                if (speechOnsetCount >= onsetHangoverFrames) {
                    isCurrentlySpeaking = true
                    hangoverLeft = offsetHangoverFrames
                }
            } else {
                speechOnsetCount = 0
                if (hangoverLeft > 0) {
                    hangoverLeft--
                } else {
                    isCurrentlySpeaking = false
                }
            }

            if (isCurrentlySpeaking) {
                isSpeechFrame[f] = true
                // Retroactively mark the onset frames
                val startOnset = max(0, f - onsetHangoverFrames)
                for (k in startOnset until f) {
                    isSpeechFrame[k] = true
                }
            }
        }

        // 3. Count speech frames and accumulate speech PCM
        var speechFrameCount = 0
        for (f in 0 until totalFrames) {
            if (isSpeechFrame[f]) speechFrameCount++
        }

        val speechSamples = speechFrameCount * frameSamples
        val speechPcm = ShortArray(speechSamples)
        var writePos = 0

        for (f in 0 until totalFrames) {
            if (isSpeechFrame[f]) {
                val offset = f * frameSamples
                System.arraycopy(pcm16k, offset, speechPcm, writePos, frameSamples)
                writePos += frameSamples
            }
        }

        val speechDurationMs = (speechSamples * 1000L) / sampleRate
        val totalDurationMs = (pcm16k.size * 1000L) / sampleRate

        return VadResult(
            hasSpeech = speechFrameCount >= onsetHangoverFrames,
            speechDurationMs = speechDurationMs,
            totalDurationMs = totalDurationMs,
            speechSampleCount = speechSamples,
            speechPcm = speechPcm
        )
    }

    /**
     * Filters out non-speech audio, returning trimmed speech samples.
     * If no speech is detected, returns an empty array.
     */
    fun filterSpeech(pcm16k: ShortArray): ShortArray = analyze(pcm16k).speechPcm
}
