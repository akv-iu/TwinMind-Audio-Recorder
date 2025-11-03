package com.example.myapplication.database.dao

import androidx.room.*
import com.example.myapplication.database.entity.RecordingSessionEntity
import com.example.myapplication.database.entity.AudioChunkEntity
import com.example.myapplication.database.entity.RecoveryTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    
    @Query("SELECT * FROM recording_sessions WHERE isActive = 1 ORDER BY lastActivityTime DESC")
    suspend fun getActiveSessions(): List<RecordingSessionEntity>
    
    @Query("SELECT * FROM recording_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): RecordingSessionEntity?
    
    @Query("SELECT * FROM recording_sessions WHERE isActive = 1 ORDER BY lastActivityTime DESC LIMIT 1")
    suspend fun getLastActiveSession(): RecordingSessionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSessionEntity)
    
    @Update
    suspend fun updateSession(session: RecordingSessionEntity)
    
    @Query("UPDATE recording_sessions SET isActive = 0 WHERE sessionId = :sessionId")
    suspend fun deactivateSession(sessionId: String)
    
    @Query("UPDATE recording_sessions SET status = :status, lastActivityTime = :timestamp WHERE sessionId = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: String, timestamp: Long)
    
    @Query("UPDATE recording_sessions SET pauseReason = :reason, lastActivityTime = :timestamp WHERE sessionId = :sessionId")
    suspend fun updatePauseReason(sessionId: String, reason: String?, timestamp: Long)
    
    @Query("DELETE FROM recording_sessions WHERE isActive = 0 AND lastActivityTime < :cutoffTime")
    suspend fun cleanupOldSessions(cutoffTime: Long)
}

@Dao
interface AudioChunkDao {
    
    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    suspend fun getChunksForSession(sessionId: String): List<AudioChunkEntity>
    
    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId AND isComplete = 0 ORDER BY chunkIndex DESC LIMIT 1")
    suspend fun getIncompleteChunk(sessionId: String): AudioChunkEntity?
    
    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId AND needsMerging = 1 ORDER BY chunkIndex ASC")
    suspend fun getChunksNeedingMerge(sessionId: String): List<AudioChunkEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: AudioChunkEntity)
    
    @Update
    suspend fun updateChunk(chunk: AudioChunkEntity)
    
    @Query("UPDATE audio_chunks SET isComplete = 1, endTime = :endTime, duration = :duration, fileSize = :fileSize WHERE chunkId = :chunkId")
    suspend fun finalizeChunk(chunkId: String, endTime: Long, duration: Long, fileSize: Long)
    
    @Query("UPDATE audio_chunks SET needsMerging = 0 WHERE sessionId = :sessionId")
    suspend fun markChunksMerged(sessionId: String)
    
    @Query("DELETE FROM audio_chunks WHERE sessionId = :sessionId")
    suspend fun deleteChunksForSession(sessionId: String)
}

@Dao
interface RecoveryTaskDao {
    
    @Query("SELECT * FROM recovery_tasks WHERE status IN ('PENDING', 'IN_PROGRESS') ORDER BY createdTime ASC")
    suspend fun getPendingTasks(): List<RecoveryTaskEntity>
    
    @Query("SELECT * FROM recovery_tasks WHERE sessionId = :sessionId ORDER BY createdTime ASC")
    suspend fun getTasksForSession(sessionId: String): List<RecoveryTaskEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: RecoveryTaskEntity)
    
    @Update
    suspend fun updateTask(task: RecoveryTaskEntity)
    
    @Query("UPDATE recovery_tasks SET status = :status, completedTime = :completedTime WHERE taskId = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: String, completedTime: Long?)
    
    @Query("UPDATE recovery_tasks SET status = :status, errorMessage = :errorMessage, retryCount = retryCount + 1 WHERE taskId = :taskId")
    suspend fun markTaskFailed(taskId: String, status: String, errorMessage: String)
    
    @Query("DELETE FROM recovery_tasks WHERE status IN ('COMPLETED', 'FAILED') AND completedTime < :cutoffTime")
    suspend fun cleanupOldTasks(cutoffTime: Long)
    
    @Query("DELETE FROM recovery_tasks WHERE sessionId = :sessionId")
    suspend fun deleteTasksForSession(sessionId: String)
}