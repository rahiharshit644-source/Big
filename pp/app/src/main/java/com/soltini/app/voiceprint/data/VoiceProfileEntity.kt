package com.soltini.app.voiceprint.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * VoiceProfileEntity
 *
 * Room Entity representing an enrolled owner voiceprint profile.
 * Biometric embeddings (centroid + enrollment vectors) are encrypted at rest with hardware Keystore AES-GCM.
 */
@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embeddings: ByteArray, // Encrypted blob storing centroid + enrollment vectors
    val embeddingDim: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val modelVersion: String = "1.0",
    val calibratedThreshold: Float = 0.72f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceProfileEntity
        if (id != other.id) return false
        if (displayName != other.displayName) return false
        if (!embeddings.contentEquals(other.embeddings)) return false
        if (embeddingDim != other.embeddingDim) return false
        if (createdAt != other.createdAt) return false
        if (modelVersion != other.modelVersion) return false
        if (calibratedThreshold != other.calibratedThreshold) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + embeddings.contentHashCode()
        result = 31 * result + embeddingDim
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + calibratedThreshold.hashCode()
        return result
    }
}
