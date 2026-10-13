package com.soltini.app.voiceprint.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * EmbeddingCrypto
 *
 * Hardware-backed AES-256 GCM encryption for VoicePrint biometric embeddings.
 * Encrypts all enrollment vectors and centroid into a secure, authenticated binary blob
 * stored in the Room database.
 *
 * Security:
 * - Keys reside in AndroidKeyStore (Secure Element / TEE).
 * - Authenticated cipher: AES/GCM/NoPadding with 128-bit authentication tag.
 * - Nonce/IV is randomized per encryption and packed with ciphertext.
 * - Plaintext embeddings are never leaked to logs or unencrypted files.
 */
object EmbeddingCrypto {

    private const val TAG = "EmbeddingCrypto"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "MyraVoiceprintMasterKey_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(spec)
                keyGenerator.generateKey()
                Log.i(TAG, "Initialized hardware-backed AES-256 key for VoicePrint.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing KeyStore key: ${e.message}", e)
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("KeyStore key entry not found for $KEY_ALIAS")
        return entry.secretKey
    }

    /**
     * Encrypts plaintext bytes using AES-GCM and returns packed byte array:
     * [1 byte IV length] + [IV bytes] + [Ciphertext + Tag bytes].
     */
    fun encryptBlob(plaintext: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)

            val packed = ByteArray(1 + iv.size + ciphertext.size)
            packed[0] = iv.size.toByte()
            System.arraycopy(iv, 0, packed, 1, iv.size)
            System.arraycopy(ciphertext, 0, packed, 1 + iv.size, ciphertext.size)
            packed
        } catch (e: Exception) {
            Log.e(TAG, "Voiceprint blob encryption error: ${e.message}", e)
            null
        }
    }

    /**
     * Decrypts a packed AES-GCM byte array back to plaintext.
     */
    fun decryptBlob(packed: ByteArray): ByteArray? {
        return try {
            if (packed.isEmpty()) return null
            val ivLength = packed[0].toInt() and 0xFF
            if (packed.size <= 1 + ivLength) return null

            val iv = ByteArray(ivLength)
            System.arraycopy(packed, 1, iv, 0, ivLength)

            val ciphertextLength = packed.size - 1 - ivLength
            val ciphertext = ByteArray(ciphertextLength)
            System.arraycopy(packed, 1 + ivLength, ciphertext, 0, ciphertextLength)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "Voiceprint blob decryption error: ${e.message}", e)
            null
        }
    }

    data class VoiceprintData(
        val centroid: FloatArray,
        val enrollmentEmbeddings: List<FloatArray>
    )

    /**
     * Serializes centroid and enrollment vectors, encrypts them, and returns packed ciphertext ByteArray.
     */
    fun encryptVoiceprint(centroid: FloatArray, enrollmentEmbeddings: List<FloatArray>): ByteArray? {
        val dim = centroid.size
        val count = enrollmentEmbeddings.size
        // Payload: [dim: Int] + [count: Int] + [centroid: dim * 4 bytes] + [enrollments: count * dim * 4 bytes]
        val totalFloats = dim + count * dim
        val buffer = ByteBuffer.allocate(8 + totalFloats * 4)
        buffer.putInt(dim)
        buffer.putInt(count)
        for (f in centroid) buffer.putFloat(f)
        for (vec in enrollmentEmbeddings) {
            for (f in vec) buffer.putFloat(f)
        }
        val plaintext = buffer.array()
        return encryptBlob(plaintext)
    }

    /**
     * Decrypts packed ciphertext and reconstructs [VoiceprintData].
     */
    fun decryptVoiceprint(encryptedBlob: ByteArray): VoiceprintData? {
        val plaintext = decryptBlob(encryptedBlob) ?: return null
        return try {
            val buffer = ByteBuffer.wrap(plaintext)
            val dim = buffer.int
            val count = buffer.int
            if (dim <= 0 || count < 0) return null

            val centroid = FloatArray(dim)
            for (i in 0 until dim) centroid[i] = buffer.float

            val enrollments = ArrayList<FloatArray>(count)
            for (c in 0 until count) {
                val vec = FloatArray(dim)
                for (i in 0 until dim) vec[i] = buffer.float
                enrollments.add(vec)
            }
            VoiceprintData(centroid, enrollments)
        } catch (e: Exception) {
            Log.e(TAG, "Failed deserializing decrypted voiceprint: ${e.message}", e)
            null
        }
    }
}
