package com.example.myapplication.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages audio focus for recording, handling interruptions from other apps
 * 
 * Audio Focus Scenarios:
 * - Music apps start playing → Pause recording
 * - Phone calls → Pause recording (handled separately by phone state listener)
 * - Navigation apps with voice guidance → Pause recording
 * - Notification sounds → Brief pause/duck (we'll pause to be safe)
 * - Other recording apps → Pause recording
 */
class AudioFocusManager(
    private val context: Context,
    private val onFocusLost: () -> Unit,
    private val onFocusGained: () -> Unit
) {
    
    companion object {
        private const val TAG = "AudioFocusManager"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    
    // Audio focus change listener
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Audio focus changed: ${getFocusChangeString(focusChange)}")
        
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss - other app has taken audio focus
                Log.d(TAG, "Audio focus lost permanently - pausing recording")
                hasFocus = false
                onFocusLost()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss - pause and wait for focus to return
                Log.d(TAG, "Audio focus lost temporarily - pausing recording")
                hasFocus = false
                onFocusLost()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Could duck (lower volume) but we'll pause for recording safety
                Log.d(TAG, "Audio focus lost (can duck) - pausing recording for safety")
                hasFocus = false
                onFocusLost()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regained - resume if we had it before
                if (!hasFocus) {
                    Log.d(TAG, "Audio focus regained - resuming recording")
                    hasFocus = true
                    onFocusGained()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                // Temporary gain - treat same as permanent gain
                if (!hasFocus) {
                    Log.d(TAG, "Audio focus gained transiently - resuming recording")
                    hasFocus = true
                    onFocusGained()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> {
                // Exclusive gain - resume recording
                if (!hasFocus) {
                    Log.d(TAG, "Audio focus gained exclusively - resuming recording")
                    hasFocus = true
                    onFocusGained()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                // Can duck but we want full focus for recording
                if (!hasFocus) {
                    Log.d(TAG, "Audio focus gained (may duck) - resuming recording")
                    hasFocus = true
                    onFocusGained()
                }
            }
        }
    }
    
    /**
     * Request audio focus for recording
     * @return true if focus was granted, false otherwise
     */
    fun requestAudioFocus(): Boolean {
        Log.d(TAG, "Requesting audio focus for recording")
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android O+ approach with AudioFocusRequest
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
                
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false) // Don't wait for focus
                .setWillPauseWhenDucked(true) // We pause when others want to duck
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            
            Log.d(TAG, "Audio focus request result: ${getRequestResultString(result)}")
            hasFocus
            
        } else {
            // Legacy approach for older Android versions
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d(TAG, "Audio focus request result (legacy): ${getRequestResultString(result)}")
            hasFocus
        }
    }
    
    /**
     * Abandon audio focus when recording stops
     */
    fun abandonAudioFocus() {
        Log.d(TAG, "Abandoning audio focus")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        
        hasFocus = false
        Log.d(TAG, "Audio focus abandoned")
    }
    
    /**
     * Check if we currently have audio focus
     */
    fun hasAudioFocus(): Boolean = hasFocus
    
    /**
     * Convert focus change constant to readable string for logging
     */
    private fun getFocusChangeString(focusChange: Int): String {
        return when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
            AudioManager.AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "AUDIOFOCUS_LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
            else -> "UNKNOWN ($focusChange)"
        }
    }
    
    /**
     * Convert request result constant to readable string for logging
     */
    private fun getRequestResultString(result: Int): String {
        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "AUDIOFOCUS_REQUEST_GRANTED"
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "AUDIOFOCUS_REQUEST_FAILED"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "AUDIOFOCUS_REQUEST_DELAYED"
            else -> "UNKNOWN ($result)"
        }
    }
}