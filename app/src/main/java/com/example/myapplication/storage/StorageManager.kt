package com.example.myapplication.storage

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * Manages storage monitoring and checks for audio recording
 */
class StorageManager(private val context: Context) {
    
    companion object {
        private const val TAG = "StorageManager"
        
        // Storage thresholds in bytes
        private const val MIN_STORAGE_TO_START_MB = 50L // 50MB minimum to start recording
        private const val LOW_STORAGE_WARNING_MB = 100L // 100MB warning threshold
        private const val CRITICAL_STORAGE_MB = 25L // 25MB critical - stop recording
        
        private const val MB_TO_BYTES = 1024 * 1024L
        private const val MONITORING_INTERVAL_MS = 10000L // Check every 10 seconds
    }
    
    private var monitoringJob: Job? = null
    private var onLowStorageCallback: ((availableMB: Long, isCritical: Boolean) -> Unit)? = null
    
    data class StorageInfo(
        val totalBytes: Long,
        val availableBytes: Long,
        val usedBytes: Long
    ) {
        val availableMB: Long get() = availableBytes / MB_TO_BYTES
        val totalMB: Long get() = totalBytes / MB_TO_BYTES
        val usedMB: Long get() = usedBytes / MB_TO_BYTES
        val usagePercent: Float get() = (usedBytes.toFloat() / totalBytes.toFloat()) * 100f
    }
    
    /**
     * Get current storage information for the app's external files directory
     */
    fun getStorageInfo(): StorageInfo {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null) ?: context.filesDir
            val stat = StatFs(externalFilesDir.path)
            
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            
            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes
            
            StorageInfo(totalBytes, availableBytes, usedBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage info", e)
            // Return safe defaults if storage check fails
            StorageInfo(
                totalBytes = 1024L * MB_TO_BYTES, // 1GB default
                availableBytes = 100L * MB_TO_BYTES, // 100MB default
                usedBytes = 924L * MB_TO_BYTES
            )
        }
    }
    
    /**
     * Check if there's enough storage to start recording
     */
    fun hasEnoughStorageToStart(): Boolean {
        try {
            val storageInfo = getStorageInfo()
            val hasEnough = storageInfo.availableMB >= MIN_STORAGE_TO_START_MB
            
            Log.d(TAG, "=== STORAGE CHECK ===")
            Log.d(TAG, "Available: ${storageInfo.availableMB}MB")
            Log.d(TAG, "Required minimum: ${MIN_STORAGE_TO_START_MB}MB") 
            Log.d(TAG, "Sufficient storage: $hasEnough")
            Log.d(TAG, "Storage path: ${context.getExternalFilesDir(null)?.absolutePath}")
            
            return hasEnough
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage", e)
            return false
        }
    }
    
    /**
     * Get storage status for user display
     */
    fun getStorageStatus(): StorageStatus {
        val storageInfo = getStorageInfo()
        
        return when {
            storageInfo.availableMB < CRITICAL_STORAGE_MB -> {
                StorageStatus.CRITICAL
            }
            storageInfo.availableMB < LOW_STORAGE_WARNING_MB -> {
                StorageStatus.LOW
            }
            storageInfo.availableMB < MIN_STORAGE_TO_START_MB * 2 -> {
                StorageStatus.WARNING
            }
            else -> {
                StorageStatus.SUFFICIENT
            }
        }
    }
    
    /**
     * Start monitoring storage during recording
     */
    fun startStorageMonitoring(onLowStorage: (availableMB: Long, isCritical: Boolean) -> Unit) {
        stopStorageMonitoring()
        
        onLowStorageCallback = onLowStorage
        
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val storageInfo = getStorageInfo()
                    
                    Log.d(TAG, "Storage monitor: ${storageInfo.availableMB}MB available")
                    
                    when {
                        storageInfo.availableMB <= CRITICAL_STORAGE_MB -> {
                            Log.w(TAG, "CRITICAL storage: ${storageInfo.availableMB}MB remaining")
                            withContext(Dispatchers.Main) {
                                onLowStorageCallback?.invoke(storageInfo.availableMB, true)
                            }
                        }
                        storageInfo.availableMB <= LOW_STORAGE_WARNING_MB -> {
                            Log.w(TAG, "LOW storage warning: ${storageInfo.availableMB}MB remaining")
                            withContext(Dispatchers.Main) {
                                onLowStorageCallback?.invoke(storageInfo.availableMB, false)
                            }
                        }
                    }
                    
                    delay(MONITORING_INTERVAL_MS)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring storage", e)
                    delay(MONITORING_INTERVAL_MS)
                }
            }
        }
        
        Log.d(TAG, "Storage monitoring started")
    }
    
    /**
     * Stop storage monitoring
     */
    fun stopStorageMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        onLowStorageCallback = null
        Log.d(TAG, "Storage monitoring stopped")
    }
    
    /**
     * Get human-readable storage description
     */
    fun getStorageDescription(): String {
        try {
            val storageInfo = getStorageInfo()
            val description = "${storageInfo.availableMB}MB available of ${storageInfo.totalMB}MB total (${String.format("%.1f", storageInfo.usagePercent)}% used)"
            Log.d(TAG, "Storage description: $description")
            return description
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage description", e)
            return "Storage: Error"
        }
    }
    
    /**
     * Calculate estimated recording time based on available storage
     * Assumes ~1MB per minute for M4A recording (approximate)
     */
    fun getEstimatedRecordingTimeMinutes(): Long {
        val storageInfo = getStorageInfo()
        val usableStorage = (storageInfo.availableMB - CRITICAL_STORAGE_MB).coerceAtLeast(0)
        return usableStorage // Rough estimate: 1MB per minute
    }
    
    enum class StorageStatus {
        SUFFICIENT,   // > 100MB
        WARNING,      // 50-100MB 
        LOW,          // 25-50MB
        CRITICAL      // < 25MB
    }
}