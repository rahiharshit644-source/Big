package com.soltini.app.homeautomation.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.soltini.app.voiceprint.data.VoiceProfileDao
import com.soltini.app.voiceprint.data.VoiceProfileEntity
import kotlinx.coroutines.launch

/**
 * DeviceConverters
 *
 * Room TypeConverters to seamlessly persist DeviceType enums as Strings.
 */
class DeviceConverters {
    @TypeConverter
    fun fromDeviceType(value: DeviceType?): String {
        return value?.name ?: DeviceType.CUSTOM.name
    }

    @TypeConverter
    fun toDeviceType(value: String?): DeviceType {
        return try {
            if (value.isNullOrBlank()) DeviceType.CUSTOM else DeviceType.valueOf(value)
        } catch (e: Exception) {
            DeviceType.CUSTOM
        }
    }
}

/**
 * HomeDatabase
 *
 * Room Database singleton hosting the unified home automation registry and encrypted voiceprints.
 *
 * Connections:
 * - Provides DeviceDao to DeviceController and ViewModels.
 * - Provides VoiceProfileDao to VoiceprintRepository and on-device speaker verification.
 * - Thread-safe double-checked locking singleton ensures a single database instance
 *   across BackgroundVoiceService, UI activities, and background workers.
 */
@Database(
    entities = [DeviceEntity::class, VoiceProfileEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DeviceConverters::class)
abstract class HomeDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao
    abstract fun voiceProfileDao(): VoiceProfileDao

    companion object {
        private const val DB_NAME = "soltini_home_devices.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sensitive flag to existing devices table
                db.execSQL("ALTER TABLE devices ADD COLUMN sensitive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE devices SET sensitive = 1 WHERE deviceType = 'LOCK' OR deviceType = 'RELAY'")
                // Create voice_profiles table for encrypted speaker embeddings
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS voice_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        displayName TEXT NOT NULL,
                        embeddings BLOB NOT NULL,
                        embeddingDim INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        modelVersion TEXT NOT NULL,
                        calibratedThreshold REAL NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: HomeDatabase? = null

        fun getInstance(context: Context): HomeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HomeDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            seedDefaultDevices(getInstance(context).deviceDao())
                        }
                    }
                })
                .build()
                .also { instance ->
                    INSTANCE = instance
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        seedDefaultDevices(instance.deviceDao())
                    }
                }
            }
        }

        private suspend fun seedDefaultDevices(dao: DeviceDao) {
            try {
                if (dao.getAllList().isEmpty()) {
                    dao.insert(
                        DeviceEntity(
                            deviceName = "Living Room Light",
                            roomName = "Living Room",
                            deviceType = DeviceType.LIGHT,
                            gpioPin = 2,
                            esp32ClientId = "esp32-livingroom"
                        )
                    )
                    dao.insert(
                        DeviceEntity(
                            deviceName = "Ceiling Fan",
                            roomName = "Living Room",
                            deviceType = DeviceType.FAN,
                            gpioPin = 4,
                            esp32ClientId = "esp32-livingroom"
                        )
                    )
                    dao.insert(
                        DeviceEntity(
                            deviceName = "Kitchen Relay",
                            roomName = "Kitchen",
                            deviceType = DeviceType.RELAY,
                            gpioPin = 16,
                            esp32ClientId = "esp32-kitchen"
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore seed error
            }
        }
    }
}
