package com.example.myapplication.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.example.myapplication.database.dao.RecordingSessionDao
import com.example.myapplication.database.dao.AudioChunkDao
import com.example.myapplication.database.dao.RecoveryTaskDao
import com.example.myapplication.database.entity.RecordingSessionEntity
import com.example.myapplication.database.entity.AudioChunkEntity
import com.example.myapplication.database.entity.RecoveryTaskEntity

@Database(
    entities = [
        RecordingSessionEntity::class,
        AudioChunkEntity::class,
        RecoveryTaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RecordingDatabase : RoomDatabase() {
    
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun recoveryTaskDao(): RecoveryTaskDao
    
    companion object {
        @Volatile
        private var INSTANCE: RecordingDatabase? = null
        
        fun getDatabase(context: Context): RecordingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecordingDatabase::class.java,
                    "recording_database"
                )
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Database callback for initialization
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Database created - can add initial setup if needed
            }
            
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Database opened - can add recurring setup if needed
            }
        }
        
        /**
         * Migration from version 1 to 2 (for future use)
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add migration logic when schema changes
                // Example: database.execSQL("ALTER TABLE recording_sessions ADD COLUMN new_field TEXT")
            }
        }
    }
}