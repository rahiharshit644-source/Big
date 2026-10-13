package com.soltini.app.voiceprint.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * VoiceProfileDao
 *
 * Data Access Object for Room database operations on enrolled voiceprints.
 */
@Dao
interface VoiceProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: VoiceProfileEntity): Long

    @Update
    suspend fun update(profile: VoiceProfileEntity)

    @Delete
    suspend fun delete(profile: VoiceProfileEntity)

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM voice_profiles")
    suspend fun deleteAll()

    @Query("SELECT * FROM voice_profiles ORDER BY createdAt DESC LIMIT 1")
    fun getActiveProfileFlow(): Flow<VoiceProfileEntity?>

    @Query("SELECT * FROM voice_profiles ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveProfile(): VoiceProfileEntity?

    @Query("SELECT * FROM voice_profiles ORDER BY createdAt DESC")
    fun getAllProfilesFlow(): Flow<List<VoiceProfileEntity>>

    @Query("SELECT * FROM voice_profiles ORDER BY createdAt DESC")
    suspend fun getAllProfiles(): List<VoiceProfileEntity>

    @Query("SELECT COUNT(*) FROM voice_profiles")
    fun getProfileCountFlow(): Flow<Int>
}
