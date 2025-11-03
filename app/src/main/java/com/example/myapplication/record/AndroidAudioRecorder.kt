package com.example.myapplication.record

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.myapplication.audio.AudioDeviceManager
import com.example.myapplication.audio.SilenceDetector
import java.io.File
import java.io.FileOutputStream


class AndroidAudioRecorder(
    private val context: Context
) : AudioRecorder {

    companion object {
        private const val TAG = "AndroidAudioRecorder"
    }

    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var currentAudioSource: Int = MediaRecorder.AudioSource.MIC
    private var fileOutputStream: FileOutputStream? = null
    
    // Silence detection
    private var silenceDetector: SilenceDetector? = null
    private var onSilenceDetected: ((Int) -> Unit)? = null

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    override fun start(outputFile: File) {
        start(outputFile, MediaRecorder.AudioSource.MIC)
    }
    
    fun start(outputFile: File, audioSource: Int) {
        currentOutputFile = outputFile
        currentAudioSource = audioSource
        
        // Create FileOutputStream and keep reference to close it properly
        fileOutputStream = FileOutputStream(outputFile)
        
        createRecorder().apply {
            setAudioSource(audioSource)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(fileOutputStream!!.fd)

            prepare()
            start()

            recorder = this
        }
        
        // Start silence detection when recording starts
        startSilenceDetection()
        
        Log.d(TAG, "Recording started with audio source: ${getAudioSourceName(audioSource)}")
    }

    override fun stop() {
        // Stop silence detection when recording stops
        stopSilenceDetection()
        
        recorder?.apply {
            try { 
                stop()
                Log.d(TAG, "MediaRecorder stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MediaRecorder", e)
            }
            release()
            Log.d(TAG, "MediaRecorder released")
        }
        recorder = null
        
        // Properly close FileOutputStream to finalize the file
        fileOutputStream?.apply {
            try {
                flush()
                close()
                Log.d(TAG, "FileOutputStream closed and flushed")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing FileOutputStream", e)
            }
        }
        fileOutputStream = null
        
        currentOutputFile?.let { file ->
            Log.d(TAG, "Recording stopped - File: ${file.absolutePath}, Size: ${file.length()} bytes")
        }
        currentOutputFile = null
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder?.pause()
            // Pause silence detection when recording is paused
            stopSilenceDetection()
            Log.d(TAG, "Recording paused")
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder?.resume()
            // Resume silence detection when recording resumes
            startSilenceDetection()
            Log.d(TAG, "Recording resumed")
        }
    }
    
    /**
     * Switch audio source during recording (requires restart of MediaRecorder)
     * This is a limitation of MediaRecorder - cannot change source without restarting
     */
    fun switchAudioSource(newAudioSource: Int): Boolean {
        val outputFile = currentOutputFile ?: return false
        
        return try {
            val wasRecording = recorder != null
            
            if (wasRecording) {
                // Save current position and pause
                val wasPaused = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                if (wasPaused) {
                    pause()
                }
                
                // Stop current recorder
                stop()
                
                // Start with new audio source
                start(outputFile, newAudioSource)
                
                Log.d(TAG, "Audio source switched to: ${getAudioSourceName(newAudioSource)}")
                true
            } else {
                // Just update the audio source for next recording
                currentAudioSource = newAudioSource
                Log.d(TAG, "Audio source updated to: ${getAudioSourceName(newAudioSource)}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching audio source", e)
            false
        }
    }
    
    /**
     * Get optimal audio source for device type
     */
    fun getOptimalAudioSource(deviceType: AudioDeviceManager.AudioDeviceType): Int {
        return when (deviceType) {
            AudioDeviceManager.AudioDeviceType.BLUETOOTH_HEADSET -> {
                // Use unprocessed source for Bluetooth if available, otherwise default
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    MediaRecorder.AudioSource.UNPROCESSED
                } else {
                    MediaRecorder.AudioSource.MIC
                }
            }
            AudioDeviceManager.AudioDeviceType.WIRED_HEADSET -> {
                MediaRecorder.AudioSource.MIC
            }
            AudioDeviceManager.AudioDeviceType.USB_HEADSET -> {
                MediaRecorder.AudioSource.MIC
            }
            AudioDeviceManager.AudioDeviceType.DEVICE_MIC -> {
                MediaRecorder.AudioSource.MIC
            }
        }
    }
    
    private fun getAudioSourceName(audioSource: Int): String {
        return when (audioSource) {
            MediaRecorder.AudioSource.MIC -> "MIC"
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
            MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
            else -> "UNKNOWN($audioSource)"
        }
    }
    
    fun getCurrentAudioSource(): Int = currentAudioSource
    
    fun isRecording(): Boolean = recorder != null
    
    /**
     * Set callback for silence detection events
     */
    fun setSilenceDetectionCallback(callback: (Int) -> Unit) {
        onSilenceDetected = callback
    }
    
    /**
     * Start monitoring for silence during recording
     */
    private fun startSilenceDetection() {
        try {
            Log.d(TAG, "Starting silence detection")
            
            silenceDetector = SilenceDetector { silenceDurationSeconds ->
                Log.w(TAG, "Silence detected for ${silenceDurationSeconds} seconds")
                onSilenceDetected?.invoke(silenceDurationSeconds)
            }
            
            silenceDetector?.startMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting silence detection", e)
            // Don't let silence detection failure prevent recording
        }
    }
    
    /**
     * Stop silence monitoring
     */
    private fun stopSilenceDetection() {
        try {
            Log.d(TAG, "Stopping silence detection")
            silenceDetector?.stopMonitoring()
            silenceDetector = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping silence detection", e)
        }
    }
    
    /**
     * Get current silence duration in seconds
     */
    fun getCurrentSilenceDuration(): Int {
        return silenceDetector?.getCurrentSilenceDuration() ?: 0
    }
    
    /**
     * Check if currently detecting silence
     */
    fun isCurrentlySilent(): Boolean {
        return silenceDetector?.isCurrentlySilent() ?: false
    }
    
    /**
     * Reset silence counter (useful when user manually checks microphone)
     */
    fun resetSilenceCounter() {
        silenceDetector?.resetSilenceCounter()
    }
}