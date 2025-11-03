package com.example.myapplication.session

import android.content.Context
import android.util.Log
import com.example.myapplication.database.RecordingDatabase
import com.example.myapplication.database.entity.RecordingSessionEntity
import com.example.myapplication.database.entity.AudioChunkEntity
import com.example.myapplication.database.entity.RecoveryTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

/**
 * Manages recording session persistence for process death recovery
 */
class SessionPersistenceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SessionPersistenceManager"
        
        // Session status constants
        const val STATUS_RECORDING = "RECORDING"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_STOPPED = "STOPPED"
        
        // Pause reason constants
        const val PAUSE_REASON_USER = "USER"
        const val PAUSE_REASON_CALL = "CALL"
        const val PAUSE_REASON_AUDIO_FOCUS = "AUDIO_FOCUS"
        
        // Recovery task types
        const val TASK_TYPE_FINALIZE_CHUNK = "FINALIZE_CHUNK"
        const val TASK_TYPE_MERGE_CHUNKS = "MERGE_CHUNKS"
        const val TASK_TYPE_CLEANUP = "CLEANUP"
        
        // Recovery task status
        const val TASK_STATUS_PENDING = "PENDING"
        const val TASK_STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val TASK_STATUS_COMPLETED = "COMPLETED"
        const val TASK_STATUS_FAILED = "FAILED"
    }
    
    private val database = RecordingDatabase.getDatabase(context)
    private val sessionDao = database.recordingSessionDao()
    private val chunkDao = database.audioChunkDao()
    private val taskDao = database.recoveryTaskDao()
    
    /**
     * Create a new recording session
     */
    suspend fun createSession(
        baseFileName: String,
        outputDirectory: String,
        audioSource: Int
    ): String {
        val sessionId = UUID.randomUUID().toString()
        
        val session = RecordingSessionEntity(
            sessionId = sessionId,
            startTime = System.currentTimeMillis(),
            status = STATUS_RECORDING,
            outputDirectory = outputDirectory,
            baseFileName = baseFileName,
            audioSource = audioSource
        )
        
        withContext(Dispatchers.IO) {
            sessionDao.insertSession(session)
        }
        
        Log.d(TAG, "Created new recording session: $sessionId")
        return sessionId
    }
    
    /**
     * Update session status
     */
    suspend fun updateSessionStatus(sessionId: String, status: String, pauseReason: String? = null) {
        withContext(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis()
            sessionDao.updateSessionStatus(sessionId, status, currentTime)
            if (pauseReason != null) {
                sessionDao.updatePauseReason(sessionId, pauseReason, currentTime)
            }
        }
        Log.d(TAG, "Updated session $sessionId status to $status")
    }
    
    /**
     * Add a new audio chunk to the session
     */
    suspend fun addChunk(
        sessionId: String,
        chunkIndex: Int,
        filePath: String
    ): String {
        val chunkId = UUID.randomUUID().toString()
        
        val chunk = AudioChunkEntity(
            chunkId = chunkId,
            sessionId = sessionId,
            chunkIndex = chunkIndex,
            filePath = filePath,
            startTime = System.currentTimeMillis()
        )
        
        withContext(Dispatchers.IO) {
            chunkDao.insertChunk(chunk)
        }
        
        Log.d(TAG, "Added chunk $chunkIndex to session $sessionId")
        return chunkId
    }
    
    /**
     * Finalize a chunk when recording completes
     */
    suspend fun finalizeChunk(chunkId: String, duration: Long) {
        withContext(Dispatchers.IO) {
            val chunk = chunkDao.getChunksForSession("").find { it.chunkId == chunkId }
            if (chunk != null) {
                val file = File(chunk.filePath)
                val fileSize = if (file.exists()) file.length() else 0L
                
                chunkDao.finalizeChunk(
                    chunkId = chunkId,
                    endTime = System.currentTimeMillis(),
                    duration = duration,
                    fileSize = fileSize
                )
                
                Log.d(TAG, "Finalized chunk $chunkId")
            }
        }
    }
    
    /**
     * Get the last active session for recovery
     */
    suspend fun getLastActiveSession(): RecordingSessionEntity? {
        return withContext(Dispatchers.IO) {
            sessionDao.getLastActiveSession()
        }
    }
    
    /**
     * Get all chunks for a session
     */
    suspend fun getSessionChunks(sessionId: String): List<AudioChunkEntity> {
        return withContext(Dispatchers.IO) {
            chunkDao.getChunksForSession(sessionId)
        }
    }
    
    /**
     * Check if there's an incomplete chunk that needs recovery
     */
    suspend fun getIncompleteChunk(sessionId: String): AudioChunkEntity? {
        return withContext(Dispatchers.IO) {
            chunkDao.getIncompleteChunk(sessionId)
        }
    }
    
    /**
     * Create a recovery task
     */
    suspend fun createRecoveryTask(
        sessionId: String,
        taskType: String,
        maxRetries: Int = 3
    ): String {
        val taskId = UUID.randomUUID().toString()
        
        val task = RecoveryTaskEntity(
            taskId = taskId,
            sessionId = sessionId,
            taskType = taskType,
            status = TASK_STATUS_PENDING,
            maxRetries = maxRetries
        )
        
        withContext(Dispatchers.IO) {
            taskDao.insertTask(task)
        }
        
        Log.d(TAG, "Created recovery task $taskType for session $sessionId")
        return taskId
    }
    
    /**
     * Get pending recovery tasks
     */
    suspend fun getPendingRecoveryTasks(): List<RecoveryTaskEntity> {
        return withContext(Dispatchers.IO) {
            taskDao.getPendingTasks()
        }
    }
    
    /**
     * Update recovery task status
     */
    suspend fun updateTaskStatus(taskId: String, status: String, errorMessage: String? = null) {
        withContext(Dispatchers.IO) {
            if (status == TASK_STATUS_FAILED && errorMessage != null) {
                taskDao.markTaskFailed(taskId, status, errorMessage)
            } else {
                val completedTime = if (status in listOf(TASK_STATUS_COMPLETED, TASK_STATUS_FAILED)) {
                    System.currentTimeMillis()
                } else null
                taskDao.updateTaskStatus(taskId, status, completedTime)
            }
        }
        Log.d(TAG, "Updated task $taskId status to $status")
    }
    
    /**
     * Complete a recording session
     */
    suspend fun completeSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            sessionDao.updateSessionStatus(sessionId, STATUS_STOPPED, System.currentTimeMillis())
            sessionDao.deactivateSession(sessionId)
            chunkDao.markChunksMerged(sessionId)
        }
        Log.d(TAG, "Completed recording session $sessionId")
    }
    
    /**
     * Clean up old sessions and tasks
     */
    suspend fun cleanup(maxAgeHours: Int = 24) {
        val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000L)
        
        withContext(Dispatchers.IO) {
            sessionDao.cleanupOldSessions(cutoffTime)
            taskDao.cleanupOldTasks(cutoffTime)
        }
        
        Log.d(TAG, "Cleaned up sessions and tasks older than $maxAgeHours hours")
    }
    
    /**
     * Delete a specific session and all related data
     */
    suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            chunkDao.deleteChunksForSession(sessionId)
            taskDao.deleteTasksForSession(sessionId)
            sessionDao.deactivateSession(sessionId)
        }
        Log.d(TAG, "Deleted session $sessionId and related data")
    }
}