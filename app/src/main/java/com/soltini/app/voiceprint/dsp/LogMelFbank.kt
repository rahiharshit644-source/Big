package com.soltini.app.voiceprint.dsp

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * LogMelFbank
 *
 * Kaldi-compatible Log-Mel Filterbank extractor for 16kHz mono audio.
 * Computes pre-emphasis, framing, Hamming windowing, in-place radix-2 FFT,
 * triangular Mel filterbank energies, log compression, and optional Cepstral Mean Normalization (CMN).
 *
 * Implemented natively without external audio DSP libraries.
 */
class LogMelFbank(
    val sampleRate: Int = 16000,
    val numMelBins: Int = 80,
    val frameLengthMs: Int = 25,
    val frameShiftMs: Int = 10,
    val preEmphasis: Float = 0.97f,
    val lowFreq: Float = 20.0f,
    val highFreq: Float = 7600.0f,
    val applyCmn: Boolean = true
) {
    val frameLengthSamples: Int = (sampleRate * frameLengthMs) / 1000
    val frameShiftSamples: Int = (sampleRate * frameShiftMs) / 1000
    val fftSize: Int = nextPowerOfTwo(frameLengthSamples)
    private val halfFft: Int = fftSize / 2

    // Precomputed Hamming window
    private val hammingWindow: FloatArray = FloatArray(frameLengthSamples) { n ->
        (0.54 - 0.46 * cos(2.0 * Math.PI * n / (frameLengthSamples - 1))).toFloat()
    }

    // Precomputed Mel filterbank weights: filterBank[melBin][fftBin]
    private val filterBank: Array<FloatArray> = buildMelFilterBank()

    /**
     * Extracts Log-Mel Filterbank features from 16-bit PCM samples.
     *
     * @param pcm16k ShortArray containing 16kHz mono audio samples.
     * @return 2D Array of shape [numFrames, numMelBins].
     */
    fun extractFeatures(pcm16k: ShortArray): Array<FloatArray> {
        val numSamples = pcm16k.size
        if (numSamples < frameLengthSamples) {
            return Array(0) { FloatArray(numMelBins) }
        }

        val numFrames = (numSamples - frameLengthSamples) / frameShiftSamples + 1
        val features = Array(numFrames) { FloatArray(numMelBins) }

        // Buffers reused per frame to avoid GC pressure
        val fftReal = DoubleArray(fftSize)
        val fftImag = DoubleArray(fftSize)
        val powerSpectrum = FloatArray(halfFft + 1)

        for (frameIdx in 0 until numFrames) {
            val startSample = frameIdx * frameShiftSamples

            // 1. Framing, Pre-emphasis, and Windowing
            for (i in 0 until frameLengthSamples) {
                val current = pcm16k[startSample + i].toFloat()
                val prev = if (startSample + i > 0) pcm16k[startSample + i - 1].toFloat() else current
                val emphasized = current - preEmphasis * prev
                fftReal[i] = (emphasized * hammingWindow[i]).toDouble()
                fftImag[i] = 0.0
            }
            // Zero-pad to fftSize
            for (i in frameLengthSamples until fftSize) {
                fftReal[i] = 0.0
                fftImag[i] = 0.0
            }

            // 2. Radix-2 in-place FFT
            radix2Fft(fftReal, fftImag, fftSize)

            // 3. Power Spectrum |X(k)|^2
            for (k in 0..halfFft) {
                val r = fftReal[k]
                val im = fftImag[k]
                powerSpectrum[k] = (r * r + im * im).toFloat()
            }

            // 4. Mel Filterbank Application and Log Compression
            for (m in 0 until numMelBins) {
                val weights = filterBank[m]
                var energy = 0f
                for (k in 0..halfFft) {
                    val w = weights[k]
                    if (w > 0f) {
                        energy += powerSpectrum[k] * w
                    }
                }
                // Floor energy to avoid log(0)
                features[frameIdx][m] = ln(max(1e-5f, energy))
            }
        }

        // 5. Optional Cepstral Mean Normalization (CMN) across time frames
        if (applyCmn && numFrames > 0) {
            for (m in 0 until numMelBins) {
                var sum = 0f
                for (t in 0 until numFrames) {
                    sum += features[t][m]
                }
                val mean = sum / numFrames
                for (t in 0 until numFrames) {
                    features[t][m] -= mean
                }
            }
        }

        return features
    }

    /**
     * Helper to convert 2D features [frames, melBins] into a flattened 1D array.
     */
    fun extractFeaturesFlat(pcm16k: ShortArray): FloatArray {
        val matrix = extractFeatures(pcm16k)
        if (matrix.isEmpty()) return FloatArray(0)
        val numFrames = matrix.size
        val flat = FloatArray(numFrames * numMelBins)
        for (i in 0 until numFrames) {
            System.arraycopy(matrix[i], 0, flat, i * numMelBins, numMelBins)
        }
        return flat
    }

    private fun buildMelFilterBank(): Array<FloatArray> {
        val bank = Array(numMelBins) { FloatArray(halfFft + 1) }

        val lowMel = hzToMel(lowFreq)
        val highMel = hzToMel(highFreq)
        val melStep = (highMel - lowMel) / (numMelBins + 1)

        val binFreqs = DoubleArray(numMelBins + 2) { i ->
            melToHz(lowMel + i * melStep)
        }

        // Map Hz to FFT bin indices
        val fftBins = IntArray(numMelBins + 2) { i ->
            ((fftSize + 1) * binFreqs[i] / sampleRate).toInt().coerceIn(0, halfFft)
        }

        for (m in 0 until numMelBins) {
            val left = fftBins[m]
            val center = fftBins[m + 1]
            val right = fftBins[m + 2]

            if (center > left) {
                for (k in left..center) {
                    bank[m][k] = (k - left).toFloat() / (center - left)
                }
            }
            if (right > center) {
                for (k in center..right) {
                    bank[m][k] = (right - k).toFloat() / (right - center)
                }
            }
        }

        return bank
    }

    private fun hzToMel(hz: Float): Double = 1127.0 * ln(1.0 + hz / 700.0)

    private fun melToHz(mel: Double): Double = 700.0 * (exp(mel / 1127.0) - 1.0)

    companion object {
        fun nextPowerOfTwo(n: Int): Int {
            var v = n - 1
            v = v or (v shr 1)
            v = v or (v shr 2)
            v = v or (v shr 4)
            v = v or (v shr 8)
            v = v or (v shr 16)
            return v + 1
        }

        /**
         * Radix-2 in-place Cooley-Tukey FFT implementation.
         */
        fun radix2Fft(real: DoubleArray, imag: DoubleArray, n: Int) {
            // Bit-reversal permutation
            var j = 0
            for (i in 0 until n - 1) {
                if (i < j) {
                    val tr = real[i]; real[i] = real[j]; real[j] = tr
                    val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
                }
                var k = n shr 1
                while (k <= j) {
                    j -= k
                    k = k shr 1
                }
                j += k
            }

            // Butterfly computation
            var len = 2
            while (len <= n) {
                val halfLen = len shr 1
                val angle = -2.0 * Math.PI / len
                val wStepR = cos(angle)
                val wStepI = sin(angle)

                var i = 0
                while (i < n) {
                    var wR = 1.0
                    var wI = 0.0
                    for (k in 0 until halfLen) {
                        val pos = i + k
                        val match = pos + halfLen
                        val uR = real[pos]
                        val uI = imag[pos]
                        val vR = real[match] * wR - imag[match] * wI
                        val vI = real[match] * wI + imag[match] * wR

                        real[pos] = uR + vR
                        imag[pos] = uI + vI
                        real[match] = uR - vR
                        imag[match] = uI - vI

                        val nextWR = wR * wStepR - wI * wStepI
                        wI = wR * wStepI + wI * wStepR
                        wR = nextWR
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
