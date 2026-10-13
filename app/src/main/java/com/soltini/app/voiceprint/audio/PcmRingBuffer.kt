package com.soltini.app.voiceprint.audio

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

/**
 * PcmRingBuffer
 *
 * Thread-safe ring buffer for 16kHz mono 16-bit PCM audio.
 * Allows continuous streaming writes from AudioRecord while preserving a configurable
 * pre-roll window (default 400ms = 6400 samples) to ensure audio onset is not lost
 * when the voice gate opens.
 */
class PcmRingBuffer(
    val capacitySamples: Int = 32000 // 2.0 seconds at 16kHz
) {
    private val buffer = ShortArray(capacitySamples)
    private var writePos = 0
    private var totalWrittenSamples = 0L
    private val lock = ReentrantLock()

    /**
     * Writes 16-bit PCM Short samples into the ring buffer.
     */
    fun write(samples: ShortArray, offset: Int = 0, length: Int = samples.size) {
        if (length <= 0) return
        lock.withLock {
            var srcPos = offset
            var remaining = length
            while (remaining > 0) {
                val chunk = min(remaining, capacitySamples - writePos)
                System.arraycopy(samples, srcPos, buffer, writePos, chunk)
                writePos = (writePos + chunk) % capacitySamples
                srcPos += chunk
                remaining -= chunk
            }
            totalWrittenSamples += length
        }
    }

    /**
     * Writes raw little-endian PCM ByteArray into the ring buffer.
     */
    fun writeBytes(pcmBytes: ByteArray, offset: Int = 0, length: Int = pcmBytes.size) {
        val sampleCount = length / 2
        if (sampleCount <= 0) return
        val shorts = ShortArray(sampleCount)
        var b = offset
        for (i in 0 until sampleCount) {
            val b0 = pcmBytes[b].toInt() and 0xFF
            val b1 = pcmBytes[b + 1].toInt()
            shorts[i] = ((b1 shl 8) or b0).toShort()
            b += 2
        }
        write(shorts)
    }

    /**
     * Retrieves the most recent audio samples corresponding to [durationMs].
     * For 400ms at 16kHz, this returns 6400 Short samples.
     */
    fun getPreRollShorts(durationMs: Int = 400, sampleRate: Int = 16000): ShortArray {
        val requestedSamples = min((sampleRate * durationMs) / 1000, capacitySamples)
        lock.withLock {
            val available = min(totalWrittenSamples, capacitySamples.toLong()).toInt()
            val count = min(requestedSamples, available)
            val result = ShortArray(count)
            if (count == 0) return result

            val startPos = (writePos - count + capacitySamples) % capacitySamples
            for (i in 0 until count) {
                val idx = (startPos + i) % capacitySamples
                result[i] = buffer[idx]
            }
            return result
        }
    }

    /**
     * Retrieves pre-roll as raw 16-bit little-endian ByteArray.
     */
    fun getPreRollBytes(durationMs: Int = 400, sampleRate: Int = 16000): ByteArray {
        val shorts = getPreRollShorts(durationMs, sampleRate)
        val bytes = ByteArray(shorts.size * 2)
        var b = 0
        for (sample in shorts) {
            val s = sample.toInt()
            bytes[b++] = (s and 0xFF).toByte()
            bytes[b++] = ((s shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Clears buffer contents.
     */
    fun clear() {
        lock.withLock {
            buffer.fill(0)
            writePos = 0
            totalWrittenSamples = 0L
        }
    }

    /**
     * Number of samples recorded so far.
     */
    fun totalSamples(): Long = lock.withLock { totalWrittenSamples }
}
