package com.soltini.app.voiceprint

import com.soltini.app.voiceprint.dsp.LogMelFbank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class LogMelFbankTest {

    @Test
    fun testOutputShape() {
        val sampleRate = 16000
        val numMelBins = 80
        val fbank = LogMelFbank(sampleRate = sampleRate, numMelBins = numMelBins)

        // 1 second of audio (16,000 samples)
        val pcm = ShortArray(16000) { i ->
            (sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 10000.0).toInt().toShort()
        }

        val features = fbank.extractFeatures(pcm)
        // (16000 - 400) / 160 + 1 = 98 + 1 = 99 frames
        val expectedFrames = (16000 - 400) / 160 + 1
        assertEquals(expectedFrames, features.size)
        assertEquals(numMelBins, features[0].size)
    }

    @Test
    fun testDeterminism() {
        val fbank = LogMelFbank()
        val pcm = ShortArray(8000) { i ->
            (sin(2.0 * Math.PI * 1000.0 * i / 16000) * 15000.0).toInt().toShort()
        }

        val run1 = fbank.extractFeatures(pcm)
        val run2 = fbank.extractFeatures(pcm)

        assertEquals(run1.size, run2.size)
        for (f in run1.indices) {
            for (m in 0 until 80) {
                assertEquals(run1[f][m], run2[f][m], 1e-6f)
            }
        }
    }

    @Test
    fun testNextPowerOfTwo() {
        assertEquals(512, LogMelFbank.nextPowerOfTwo(400))
        assertEquals(256, LogMelFbank.nextPowerOfTwo(256))
        assertEquals(1024, LogMelFbank.nextPowerOfTwo(513))
    }
}
