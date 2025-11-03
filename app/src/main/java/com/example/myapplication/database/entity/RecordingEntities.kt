package com.example.myapplication.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

/**
 * Recording session entity for persisting recording state across process death
 */
@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val startTime: Long, // System.currentTimeMillis()
    val status: String, // "RECORDING", "PAUSED", "STOPPED"
    val pausedTime: Long = 0L, // Total paused duration in milliseconds
    val lastChunkIndex: Int = 0,
    val outputDirectory: String,
    val baseFileName: String, // Base name without chunk index
    val audioSource: Int, // MediaRecorder.AudioSource value
    val pauseReason: String? = null, // "CALL", "AUDIO_FOCUS", "USER", null
    val lastActivityTime: Long = System.currentTimeMillis(),
    val isActive: Boolean = true // false when recording is completed/abandoned
)

/**
 * Individual audio chunk entity for tracking recorded chunks
 */
@Entity(
    tableName = "audio_chunks",
    foreignKeys = [androidx.room.ForeignKey(
        entity = RecordingSessionEntity::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = androidx.room.ForeignKey.CASCADE
    )],
    indices = [androidx.room.Index(value = ["sessionId"])]
)
data class AudioChunkEntity(
    @PrimaryKey
    val chunkId: String,
    val sessionId: String,
    val chunkIndex: Int,
    val filePath: String,
    val startTime: Long, // When this chunk started recording
    val endTime: Long? = null, // When this chunk finished (null if interrupted)
    val duration: Long = 0L, // Duration in milliseconds
    val fileSize: Long = 0L, // File size in bytes
    val isComplete: Boolean = false, // true when chunk finished normally
    val needsMerging: Boolean = true // false when merged into final file
)

/**
 * Recovery task entity for tracking background recovery operations
 */
@Entity(tableName = "recovery_tasks")
data class RecoveryTaskEntity(
    @PrimaryKey
    val taskId: String,
    val sessionId: String,
    val taskType: String, // "FINALIZE_CHUNK", "MERGE_CHUNKS", "CLEANUP"
    val status: String, // "PENDING", "IN_PROGRESS", "COMPLETED", "FAILED"
    val createdTime: Long = System.currentTimeMillis(),
    val completedTime: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3
)