package com.example.myapplication.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects silence during audio recording by monitoring audio levels
 * Shows warning after 10 seconds of consecutive silence
 */
class SilenceDetector(
    private val onSilenceDetected: (silenceDurationSeconds: Int) -> Unit
) {
    
    companion object {
        private const val TAG = "SilenceDetector"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        
        // Silence detection parameters
        private const val SILENCE_THRESHOLD = 500 // Amplitude threshold below which is considered silence
        private const val SILENCE_WARNING_SECONDS = 10 // Show warning after 10 seconds of silence
        private const val CHECK_INTERVAL_MS = 1000L // Check audio level every second
    }
    
    private var audioRecord: AudioRecord? = null
    private var monitoringJob: Job? = null
    private var isMonitoring = false
    
    // Silence tracking
    private var consecutiveSilenceSeconds = 0
    private var lastWarningTime = 0L
    
    /**
     * Start monitoring audio levels for silence detection
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring, ignoring start request")
            return
        }
        
        try {
            Log.d(TAG, "Starting silence detection monitoring")
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_FACTOR
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }
            
            audioRecord?.startRecording()
            isMonitoring = true
            consecutiveSilenceSeconds = 0
            lastWarningTime = 0L
            
            // Start monitoring in background coroutine
            monitoringJob = CoroutineScope(Dispatchers.IO).launch {
                monitorAudioLevels()
            }
            
            Log.d(TAG, "Silence detection started successfully")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for audio recording", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting silence detection", e)
            cleanup()
        }
    }
    
    /**
     * Stop monitoring audio levels
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            Log.d(TAG, "Not currently monitoring, ignoring stop request")
            return
        }
        
        Log.d(TAG, "Stopping silence detection monitoring")
        cleanup()
    }
    
    /**
     * Monitor audio levels and detect silence periods
     */
    private suspend fun monitorAudioLevels() {
        val audioRecord = this.audioRecord ?: return
        val bufferSize = audioRecord.bufferSizeInFrames
        val buffer = ShortArray(bufferSize)
        
        Log.d(TAG, "Starting audio level monitoring loop")
        
        while (isMonitoring && !Thread.currentThread().isInterrupted) {
            try {
                val readResult = audioRecord.read(buffer, 0, buffer.size)
                
                if (readResult > 0) {
                    val audioLevel = calculateRMS(buffer, readResult)
                    val isSilent = audioLevel < SILENCE_THRESHOLD
                    
                    Log.v(TAG, "Audio level: $audioLevel, threshold: $SILENCE_THRESHOLD, silent: $isSilent")
                    
                    if (isSilent) {
                        consecutiveSilenceSeconds++
                        Log.d(TAG, "Silence detected: ${consecutiveSilenceSeconds}s consecutive")
                        
                        // Show warning after 10 seconds of silence
                        if (consecutiveSilenceSeconds >= SILENCE_WARNING_SECONDS) {
                            val currentTime = System.currentTimeMillis()
                            // Only show warning once per silence period (avoid spam)
                            if (currentTime - lastWarningTime > 5000) {
                                lastWarningTime = currentTime
                                Log.w(TAG, "Silence warning triggered: ${consecutiveSilenceSeconds}s of silence")
                                
                                // Call callback on main thread
                                withContext(Dispatchers.Main) {
                                    onSilenceDetected(consecutiveSilenceSeconds)
                                }
                            }
                        }
                    } else {
                        if (consecutiveSilenceSeconds > 0) {
                            Log.d(TAG, "Audio detected, resetting silence counter (was ${consecutiveSilenceSeconds}s)")
                        }
                        consecutiveSilenceSeconds = 0
                        lastWarningTime = 0L
                    }
                } else {
                    Log.w(TAG, "AudioRecord read error: $readResult")
                    delay(100) // Brief delay before retrying
                }
                
                delay(CHECK_INTERVAL_MS)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio monitoring loop", e)
                delay(1000) // Longer delay on error
            }
        }
        
        Log.d(TAG, "Audio monitoring loop ended")
    }
    
    /**
     * Calculate RMS (Root Mean Square) of audio samples to determine audio level
     */
    private fun calculateRMS(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            sum += (buffer[i] * buffer[i]).toDouble()
        }
        return sqrt(sum / length)
    }
    
    /**
     * Get current silence duration in seconds
     */
    fun getCurrentSilenceDuration(): Int {
        return consecutiveSilenceSeconds
    }
    
    /**
     * Check if currently detecting silence
     */
    fun isCurrentlySilent(): Boolean {
        return consecutiveSilenceSeconds > 0
    }
    
    /**
     * Reset silence counter (useful when user manually checks microphone)
     */
    fun resetSilenceCounter() {
        Log.d(TAG, "Manually resetting silence counter")
        consecutiveSilenceSeconds = 0
        lastWarningTime = 0L
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        isMonitoring = false
        
        monitoringJob?.cancel()
        monitoringJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
        
        consecutiveSilenceSeconds = 0
        lastWarningTime = 0L
        
        Log.d(TAG, "Silence detection cleanup completed")
    }
}