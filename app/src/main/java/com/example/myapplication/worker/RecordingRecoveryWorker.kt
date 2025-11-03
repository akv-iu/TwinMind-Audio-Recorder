package com.example.myapplication.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.myapplication.audio.AudioMerger
import com.example.myapplication.session.SessionPersistenceManager
import com.example.myapplication.database.entity.RecoveryTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for handling recording recovery tasks after process death
 */
class RecordingRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        private const val TAG = "RecordingRecoveryWorker"
        const val WORK_NAME = "recording_recovery_work"
        
        // Input data keys
        const val KEY_TASK_ID = "task_id"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_TASK_TYPE = "task_type"
        
        /**
         * Enqueue recovery work for immediate execution
         */
        fun enqueueRecoveryWork(context: Context, taskId: String, sessionId: String, taskType: String) {
            val inputData = Data.Builder()
                .putString(KEY_TASK_ID, taskId)
                .putString(KEY_SESSION_ID, sessionId)
                .putString(KEY_TASK_TYPE, taskType)
                .build()
            
            val recoveryRequest = OneTimeWorkRequestBuilder<RecordingRecoveryWorker>()
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(false)
                        .setRequiresStorageNotLow(true) // Need storage for file operations
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15000L, // 15 seconds minimum backoff
                    TimeUnit.MILLISECONDS
                )
                .addTag(WORK_NAME)
                .addTag(sessionId)
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_${taskId}",
                ExistingWorkPolicy.REPLACE,
                recoveryRequest
            )
            
            Log.d(TAG, "Enqueued recovery work for task $taskId (session $sessionId, type $taskType)")
        }
        
        /**
         * Check for and enqueue any pending recovery tasks on app startup
         */
        suspend fun enqueuePendingRecoveryTasks(context: Context) {
            val persistenceManager = SessionPersistenceManager(context)
            val pendingTasks = persistenceManager.getPendingRecoveryTasks()
            
            Log.d(TAG, "Found ${pendingTasks.size} pending recovery tasks")
            
            for (task in pendingTasks) {
                enqueueRecoveryWork(
                    context = context,
                    taskId = task.taskId,
                    sessionId = task.sessionId,
                    taskType = task.taskType
                )
            }
        }
    }
    
    private val persistenceManager = SessionPersistenceManager(applicationContext)
    private val audioMerger = AudioMerger()
    
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val taskType = inputData.getString(KEY_TASK_TYPE) ?: return Result.failure()
        
        Log.d(TAG, "Starting recovery work for task $taskId (session $sessionId, type $taskType)")
        
        return try {
            // Update task status to in progress
            persistenceManager.updateTaskStatus(taskId, SessionPersistenceManager.TASK_STATUS_IN_PROGRESS)
            
            val result = when (taskType) {
                SessionPersistenceManager.TASK_TYPE_FINALIZE_CHUNK -> finalizeIncompleteChunk(sessionId)
                SessionPersistenceManager.TASK_TYPE_MERGE_CHUNKS -> mergeSessionChunks(sessionId)
                SessionPersistenceManager.TASK_TYPE_CLEANUP -> cleanupSessionFiles(sessionId)
                else -> {
                    Log.e(TAG, "Unknown task type: $taskType")
                    false
                }
            }
            
            if (result) {
                persistenceManager.updateTaskStatus(taskId, SessionPersistenceManager.TASK_STATUS_COMPLETED)
                Log.d(TAG, "Recovery task $taskId completed successfully")
                Result.success()
            } else {
                persistenceManager.updateTaskStatus(
                    taskId, 
                    SessionPersistenceManager.TASK_STATUS_FAILED,
                    "Task execution failed"
                )
                Log.e(TAG, "Recovery task $taskId failed")
                Result.failure()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Recovery task $taskId failed with exception", e)
            persistenceManager.updateTaskStatus(
                taskId, 
                SessionPersistenceManager.TASK_STATUS_FAILED,
                e.message ?: "Unknown error"
            )
            Result.failure()
        }
    }
    
    /**
     * Finalize an incomplete chunk that was interrupted by process death
     */
    private suspend fun finalizeIncompleteChunk(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val incompleteChunk = persistenceManager.getIncompleteChunk(sessionId)
                if (incompleteChunk != null) {
                    Log.d(TAG, "Finalizing incomplete chunk: ${incompleteChunk.filePath}")
                    
                    val file = File(incompleteChunk.filePath)
                    if (file.exists() && file.length() > 0) {
                        // Calculate approximate duration based on file size and audio format
                        // This is an approximation since we don't know the exact recording time
                        val approximateDuration = estimateAudioDuration(file)
                        
                        persistenceManager.finalizeChunk(incompleteChunk.chunkId, approximateDuration)
                        Log.d(TAG, "Successfully finalized incomplete chunk ${incompleteChunk.chunkId}")
                        return@withContext true
                    } else {
                        Log.w(TAG, "Incomplete chunk file does not exist or is empty: ${incompleteChunk.filePath}")
                        // Mark as finalized with 0 duration since file is unusable
                        persistenceManager.finalizeChunk(incompleteChunk.chunkId, 0L)
                        return@withContext true
                    }
                } else {
                    Log.d(TAG, "No incomplete chunks found for session $sessionId")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing incomplete chunk for session $sessionId", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Merge all completed chunks into a final recording file
     */
    private suspend fun mergeSessionChunks(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val session = persistenceManager.getLastActiveSession()
                if (session?.sessionId != sessionId) {
                    Log.e(TAG, "Session not found: $sessionId")
                    return@withContext false
                }
                
                val chunks = persistenceManager.getSessionChunks(sessionId)
                    .filter { it.isComplete && File(it.filePath).exists() }
                    .sortedBy { it.chunkIndex }
                
                if (chunks.isEmpty()) {
                    Log.w(TAG, "No complete chunks found for session $sessionId")
                    return@withContext true
                }
                
                Log.d(TAG, "Merging ${chunks.size} chunks for session $sessionId")
                
                val outputFile = File(session.outputDirectory, "${session.baseFileName}.m4a")
                val inputFiles = chunks.map { File(it.filePath) }
                
                val success = audioMerger.mergeAudioFiles(inputFiles, outputFile)
                
                if (success) {
                    // Mark chunks as merged
                    persistenceManager.completeSession(sessionId)
                    
                    // Clean up individual chunk files
                    inputFiles.forEach { file ->
                        try {
                            if (file.delete()) {
                                Log.d(TAG, "Deleted chunk file: ${file.path}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not delete chunk file: ${file.path}", e)
                        }
                    }
                    
                    Log.d(TAG, "Successfully merged chunks into: ${outputFile.path}")
                    return@withContext true
                } else {
                    Log.e(TAG, "Failed to merge chunks for session $sessionId")
                    return@withContext false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error merging chunks for session $sessionId", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Clean up session files and database entries
     */
    private suspend fun cleanupSessionFiles(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val chunks = persistenceManager.getSessionChunks(sessionId)
                
                // Delete chunk files
                var deletedCount = 0
                for (chunk in chunks) {
                    val file = File(chunk.filePath)
                    if (file.exists()) {
                        if (file.delete()) {
                            deletedCount++
                        } else {
                            Log.w(TAG, "Could not delete chunk file: ${chunk.filePath}")
                        }
                    }
                }
                
                // Delete database entries
                persistenceManager.deleteSession(sessionId)
                
                Log.d(TAG, "Cleaned up session $sessionId: deleted $deletedCount files")
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up session $sessionId", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Estimate audio duration based on file size
     * This is an approximation for AAC files at typical bitrates
     */
    private fun estimateAudioDuration(file: File): Long {
        val fileSizeBytes = file.length()
        // Assume ~128 kbps AAC encoding (16 KB/sec)
        val bytesPerSecond = 16 * 1024
        return (fileSizeBytes / bytesPerSecond) * 1000L // Convert to milliseconds
    }
}