// File: ui/theme/RecordingViewModel.kt
package com.example.myapplication.ui.theme

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.playback.AndroidAudioPlayer
import com.example.myapplication.record.AndroidAudioRecorder
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    sealed class Status {
        object Stopped : Status()
        object Recording : Status()
        object Paused : Status()
    }

    init {
        setupPhoneStateListener()
    }

    private val _status = mutableStateOf<Status>(Status.Stopped)
    val status: State<Status> = _status

    private val _timerText = mutableStateOf("00:00")
    val timerText: State<String> = _timerText

    private val _recordings = mutableStateOf<List<RecordingItem>>(emptyList())
    val recordings: State<List<RecordingItem>> = _recordings

    private val _currentlyPlayingItem = mutableStateOf<RecordingItem?>(null)
    val currentlyPlayingItem: State<RecordingItem?> = _currentlyPlayingItem

    // SINGLE PLAYER INSTANCE — shared everywhere
    private val audioPlayer = AndroidAudioPlayer(app)
    
    // Expose player for completion listener access
    val player: AndroidAudioPlayer get() = audioPlayer

    private val recorder = AndroidAudioRecorder(app)

    private var currentFile: File? = null
    private var timerJob: Job? = null
    private var startTimeMs = 0L
    private var pausedTimeMs = 0L
    
    // Phone call detection
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: Any? = null
    private var callbackExecutor: Executor? = null
    private var isPausedForCall = false
    
    // Notification helper
    private val notificationHelper = NotificationHelper(app)

    fun toggleRecord() {
        when (_status.value) {
            is Status.Recording -> stopRecording()
            is Status.Stopped -> startRecording()
            is Status.Paused -> resumeRecording()
        }
    }
    
    fun togglePause() {
        when (_status.value) {
            is Status.Recording -> pauseRecording()
            is Status.Paused -> resumeRecording()
            is Status.Stopped -> { /* No action when stopped */ }
        }
    }

    private fun startRecording() {
        val file = File(
            getApplication<Application>().getExternalFilesDir(null),
            "meeting_${timestamp()}.m4a"
        ).also { it.parentFile?.mkdirs() }

        currentFile = file
        recorder.start(file)

        _status.value = Status.Recording
        startTimer()
    }

    fun stopRecording() {
        timerJob?.cancel()
        
        // Stop recorder regardless of current state (recording or paused)
        try { recorder.stop() } catch (_: Exception) {}
        
        // Save the recording file if it has data
        currentFile?.takeIf { it.exists() && it.length() > 0 }?.let { file ->
            val duration = elapsedSeconds()
            val item = RecordingItem(
                file = file,
                name = file.nameWithoutExtension,
                date = Date(),
                durationSec = duration
            )
            _recordings.value = _recordings.value + item
        }

        _status.value = Status.Stopped
        _timerText.value = "00:00"

        // Hide any call pause notifications
        if (isPausedForCall) {
            isPausedForCall = false
            notificationHelper.hideRecordingPausedNotification()
        }

        currentFile = null
        startTimeMs = 0L
        pausedTimeMs = 0L
    }

    private fun pauseRecording() {
        if (_status.value !is Status.Recording) return
        
        timerJob?.cancel()
        
        // Use MediaRecorder pause instead of stop (Android N+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { recorder.pause() } catch (_: Exception) {}
        }
        
        // Save how much time has elapsed when we paused
        pausedTimeMs = System.currentTimeMillis() - startTimeMs
        _status.value = Status.Paused
        
        // Don't save file yet - just pause the recording
    }
    
    private fun resumeRecording() {
        if (_status.value !is Status.Paused) return
        
        // Hide call pause notification only if user manually resumes (not during active call)
        if (isPausedForCall) {
            // Check if there's still an active call
            val callState = telephonyManager?.callState ?: TelephonyManager.CALL_STATE_IDLE
            if (callState == TelephonyManager.CALL_STATE_IDLE) {
                // No active call, safe to clear the flag and notification
                isPausedForCall = false
                notificationHelper.hideRecordingPausedNotification()
            }
        }
        
        try {
            // Use MediaRecorder resume instead of starting new file (Android N+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recorder.resume()
            } else {
                // For older Android versions, we'd need to handle differently
                // but for now, just resume normally
            }
            
            _status.value = Status.Recording
            
            // Continue the timer from where we left off
            startTimeMs = System.currentTimeMillis() - pausedTimeMs
            startTimer()
        } catch (e: Exception) {
            _status.value = Status.Stopped
            _timerText.value = "00:00"
        }
    }

    fun play(item: RecordingItem) {
        // Stop any currently playing audio first
        audioPlayer.stop()
        
        // Set the currently playing item
        _currentlyPlayingItem.value = item
        
        // Start playing the new item
        audioPlayer.playFile(item.file)
        
        // Set up completion listener to clear the playing state when done
        audioPlayer.setOnCompletionListener { 
            _currentlyPlayingItem.value = null
        }
    }

    fun stopPlayback() {
        audioPlayer.stop()
        _currentlyPlayingItem.value = null
    }

    private fun startTimer() {
        if (startTimeMs == 0L) {
            startTimeMs = System.currentTimeMillis()
        }
        timerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive && _status.value is Status.Recording) {
                val secs = elapsedSeconds()
                _timerText.value = String.format("%02d:%02d", secs / 60, secs % 60)
                delay(500)
            }
        }
    }

    private fun elapsedSeconds(): Long =
        ((System.currentTimeMillis() - startTimeMs) / 1000).coerceAtLeast(0)

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        
    private fun setupPhoneStateListener() {
        val app = getApplication<Application>()
        
        // Check if we have the required permission
        if (ActivityCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            telephonyManager = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ - use TelephonyCallback
                setupModernCallCallback()
            } else {
                // Android 11 and below - use PhoneStateListener
                setupLegacyPhoneStateListener()
            }
            
        } catch (e: Exception) {
            // Silently fail if phone state detection setup fails
        }
    }
    
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun setupModernCallCallback() {
        callbackExecutor = Executor { command ->
            android.os.Handler(android.os.Looper.getMainLooper()).post(command)
        }
        
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallStateChange(state)
            }
        }
        
        try {
            telephonyManager?.registerTelephonyCallback(callbackExecutor!!, callback)
            telephonyCallback = callback
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    @Suppress("DEPRECATION")
    private fun setupLegacyPhoneStateListener() {
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleCallStateChange(state)
            }
        }
        
        try {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended - resume recording if it was paused for call
                if (isPausedForCall && _status.value is Status.Paused) {
                    isPausedForCall = false
                    notificationHelper.hideRecordingPausedNotification()
                    // Use existing pause functionality to resume
                    togglePause()
                }
            }
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call starting - pause recording if currently recording
                if (_status.value is Status.Recording) {
                    isPausedForCall = true
                    // Use existing pause functionality 
                    togglePause()
                    notificationHelper.showRecordingPausedNotification()
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Clean up phone state listeners
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
                telephonyManager?.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            } else if (phoneStateListener != null) {
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        } catch (e: Exception) {
            // Silently fail cleanup
        }
        
        // Hide any remaining notifications
        notificationHelper.hideRecordingPausedNotification()
    }
}

data class RecordingItem(
    val file: File,
    val name: String,
    val date: Date,
    val durationSec: Long
)