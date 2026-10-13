package com.soltini.app.voiceprint.data

import android.content.Context
import com.soltini.app.homeautomation.data.HomeDatabase
import com.soltini.app.voiceprint.security.EmbeddingCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * VoiceprintRepository
 *
 * Repository layer connecting Room persistence, hardware crypto, and domain logic.
 */
class VoiceprintRepository(
    private val voiceProfileDao: VoiceProfileDao
) {
    companion object {
        @Volatile
        private var INSTANCE: VoiceprintRepository? = null

        fun getInstance(context: Context): VoiceprintRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceprintRepository(
                    HomeDatabase.getInstance(context).voiceProfileDao()
                ).also { INSTANCE = it }
            }
        }
    }

    val activeProfileFlow: Flow<VoiceProfileEntity?> = voiceProfileDao.getActiveProfileFlow()

    val isProfileEnrolledFlow: Flow<Boolean> = voiceProfileDao.getActiveProfileFlow().map { it != null }

    suspend fun getActiveProfile(): VoiceProfileEntity? = withContext(Dispatchers.IO) {
        voiceProfileDao.getActiveProfile()
    }

    /**
     * Decrypts and returns the stored centroid vector for verification.
     */
    suspend fun getActiveCentroid(): FloatArray? = withContext(Dispatchers.IO) {
        val entity = voiceProfileDao.getActiveProfile() ?: return@withContext null
        val data = EmbeddingCrypto.decryptVoiceprint(entity.embeddings)
        data?.centroid
    }

    /**
     * Saves an enrolled voiceprint with hardware AES-GCM encryption.
     */
    suspend fun saveProfile(
        displayName: String,
        centroid: FloatArray,
        enrollmentEmbeddings: List<FloatArray>,
        calibratedThreshold: Float,
        modelVersion: String = "1.0"
    ): Long = withContext(Dispatchers.IO) {
        val encryptedBlob = EmbeddingCrypto.encryptVoiceprint(centroid, enrollmentEmbeddings)
            ?: throw IllegalStateException("Failed to encrypt voiceprint embedding with hardware Keystore")

        // Wipe existing profile first to enforce single-owner voiceprint
        voiceProfileDao.deleteAll()

        val entity = VoiceProfileEntity(
            displayName = displayName,
            embeddings = encryptedBlob,
            embeddingDim = centroid.size,
            createdAt = System.currentTimeMillis(),
            modelVersion = modelVersion,
            calibratedThreshold = calibratedThreshold
        )
        voiceProfileDao.insert(entity)
    }

    /**
     * Deletes enrolled voiceprint and purges ciphertext.
     */
    suspend fun deleteProfile(): Unit = withContext(Dispatchers.IO) {
        voiceProfileDao.deleteAll()
    }
}
