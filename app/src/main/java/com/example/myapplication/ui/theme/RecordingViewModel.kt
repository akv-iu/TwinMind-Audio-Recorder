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
import com.example.myapplication.audio.AudioDeviceManager
import com.example.myapplication.storage.StorageManager

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    sealed class Status {
        object Stopped : Status()
        object Recording : Status()
        object Paused : Status()
    }

    init {
        try {
            android.util.Log.i("RecordingViewModel", "==========================================")
            android.util.Log.i("RecordingViewModel", "STARTING RECORDINGVIEWMODEL INITIALIZATION")
            android.util.Log.i("RecordingViewModel", "==========================================")
            
            // Simple storage path test first
            val app = getApplication<Application>()
            val externalDir = app.getExternalFilesDir(null)
            android.util.Log.w("TwinMindApp", "STORAGE PATH TEST:")
            android.util.Log.w("TwinMindApp", "External files dir: ${externalDir?.absolutePath}")
            android.util.Log.w("TwinMindApp", "External dir exists: ${externalDir?.exists()}")
            android.util.Log.w("TwinMindApp", "External dir readable: ${externalDir?.canRead()}")
            android.util.Log.w("TwinMindApp", "External dir writable: ${externalDir?.canWrite()}")
            
            setupPhoneStateListener()
            
            // Test storage immediately during initialization
            android.util.Log.w("RecordingViewModel", "TESTING STORAGE DURING INIT...")
            testStorageChecker()
            
            updateStorageStatus()
            android.util.Log.i("RecordingViewModel", "==========================================")
            android.util.Log.i("RecordingViewModel", "VIEWMODEL INITIALIZATION COMPLETED!")
            android.util.Log.i("RecordingViewModel", "==========================================")
        } catch (e: Exception) {
            android.util.Log.e("RecordingViewModel", "ERROR DURING VIEWMODEL INITIALIZATION", e)
            // Don't let initialization crash the app
        }
    }

    private val _status = mutableStateOf<Status>(Status.Stopped)
    val status: State<Status> = _status

    private val _timerText = mutableStateOf("00:00")
    val timerText: State<String> = _timerText
    
    private val _storageStatus = mutableStateOf("Storage: Checking...")
    val storageStatus: State<String> = _storageStatus

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
    
    // Audio device management
    private var audioDeviceManager: AudioDeviceManager? = null
    private var currentAudioDevice = AudioDeviceManager.AudioDeviceType.DEVICE_MIC
    private var currentDeviceName = "Device Microphone"
    
    // Storage management
    private val storageManager = StorageManager(app)
    
    // Silence detection state
    private var isShowingSilenceWarning = false

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
        // Check storage before starting
        if (!storageManager.hasEnoughStorageToStart()) {
            val storageInfo = storageManager.getStorageInfo()
            android.util.Log.w("RecordingViewModel", "Insufficient storage to start recording: ${storageInfo.availableMB}MB available")
            notificationHelper.showInsufficientStorageNotification(storageInfo.availableMB)
            return
        }
        
        val file = File(
            getApplication<Application>().getExternalFilesDir(null),
            "meeting_${timestamp()}.m4a"
        ).also { it.parentFile?.mkdirs() }

        currentFile = file
        
        // Set up silence detection callback before starting
        recorder.setSilenceDetectionCallback { silenceDurationSeconds ->
            handleSilenceDetected(silenceDurationSeconds)
        }
        
        // Get optimal audio source for current device
        val audioSource = recorder.getOptimalAudioSource(currentAudioDevice)
        recorder.start(file, audioSource)

        _status.value = Status.Recording
        startTimer()
        
        // Start monitoring audio device changes
        startAudioDeviceMonitoring()
        
        // Start monitoring storage during recording
        startStorageMonitoring()
        
        android.util.Log.d("RecordingViewModel", "Recording started with ${storageManager.getStorageDescription()}")
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
        
        // Hide silence warning if showing
        if (isShowingSilenceWarning) {
            isShowingSilenceWarning = false
            notificationHelper.hideSilenceWarningNotification()
        }

        currentFile = null
        startTimeMs = 0L
        pausedTimeMs = 0L
        
        // Stop monitoring audio device changes
        stopAudioDeviceMonitoring()
        
        // Stop storage monitoring
        stopStorageMonitoring()
    }

    /**
     * Start monitoring audio device changes
     */
    private fun startAudioDeviceMonitoring() {
        try {
            android.util.Log.d("RecordingViewModel", "Starting audio device monitoring")
            
            if (audioDeviceManager == null) {
                audioDeviceManager = AudioDeviceManager(getApplication()) { deviceType, deviceName ->
                    handleAudioDeviceChange(deviceType, deviceName)
                }
            }
            audioDeviceManager?.startMonitoring()
            
            // Get initial device state
            val (initialDevice, initialName) = audioDeviceManager?.getCurrentDevice() 
                ?: Pair(AudioDeviceManager.AudioDeviceType.DEVICE_MIC, "Device Microphone")
            
            currentAudioDevice = initialDevice
            currentDeviceName = initialName
            
            android.util.Log.d("RecordingViewModel", "Initial audio device: $initialName ($initialDevice)")
        } catch (e: Exception) {
            android.util.Log.e("RecordingViewModel", "Error starting audio device monitoring", e)
            // Use default device if monitoring fails
            currentAudioDevice = AudioDeviceManager.AudioDeviceType.DEVICE_MIC
            currentDeviceName = "Device Microphone"
        }
    }
    
    /**
     * Manual method to test audio device detection (for debugging)
     */
    fun testAudioDeviceDetection() {
        android.util.Log.d("RecordingViewModel", "=== MANUAL AUDIO DEVICE TEST ===")
        audioDeviceManager?.forceDeviceDetection()
    }
    
    /**
     * Manual method to test storage checker (for debugging)
     */
    fun testStorageChecker() {
        android.util.Log.w("RecordingViewModel", "============ STORAGE TEST START ============")
        try {
            val storageInfo = storageManager.getStorageInfo()
            val hasEnough = storageManager.hasEnoughStorageToStart()
            val status = storageManager.getStorageStatus()
            val description = storageManager.getStorageDescription()
            val estimatedTime = storageManager.getEstimatedRecordingTimeMinutes()
            
            android.util.Log.i("RecordingViewModel", "STORAGE RESULTS:")
            android.util.Log.i("RecordingViewModel", "  Total: ${storageInfo.totalMB}MB")
            android.util.Log.i("RecordingViewModel", "  Available: ${storageInfo.availableMB}MB")
            android.util.Log.i("RecordingViewModel", "  Used: ${storageInfo.usedMB}MB")
            android.util.Log.i("RecordingViewModel", "  Usage: ${String.format("%.1f", storageInfo.usagePercent)}%")
            android.util.Log.i("RecordingViewModel", "  Has enough to start: $hasEnough")
            android.util.Log.i("RecordingViewModel", "  Status: $status")
            android.util.Log.i("RecordingViewModel", "  Description: $description")
            android.util.Log.i("RecordingViewModel", "  Estimated time: ${estimatedTime} minutes")
            android.util.Log.i("RecordingViewModel", "  Current UI status: ${_storageStatus.value}")
            
        } catch (e: Exception) {
            android.util.Log.e("RecordingViewModel", "ERROR IN STORAGE TEST", e)
        }
        android.util.Log.w("RecordingViewModel", "============ STORAGE TEST END ============")
    }
    
    /**
     * Start monitoring storage during recording
     */
    private fun startStorageMonitoring() {
        // Update initial storage status
        updateStorageStatus()
        
        storageManager.startStorageMonitoring { availableMB, isCritical ->
            handleLowStorage(availableMB, isCritical)
            updateStorageStatus()
        }
    }
    
    /**
     * Stop storage monitoring
     */
    private fun stopStorageMonitoring() {
        storageManager.stopStorageMonitoring()
        notificationHelper.hideStorageNotifications()
    }
    
    /**
     * Handle low storage during recording
     */
    private fun handleLowStorage(availableMB: Long, isCritical: Boolean) {
        android.util.Log.w("RecordingViewModel", 
            "Low storage detected: ${availableMB}MB available, critical: $isCritical")
        
        if (isCritical && _status.value is Status.Recording) {
            // Critical storage - stop recording immediately
            android.util.Log.e("RecordingViewModel", "Stopping recording due to critical low storage")
            
            // Show critical storage notification
            notificationHelper.showLowStorageNotification(availableMB, isCritical = true)
            
            // Stop recording gracefully
            stopRecording()
            
        } else if (!isCritical) {
            // Warning level - just notify user
            notificationHelper.showLowStorageNotification(availableMB, isCritical = false)
        }
    }
    
    /**
     * Get current storage information for UI display
     */
    fun getStorageDescription(): String {
        return storageManager.getStorageDescription()
    }
    
    /**
     * Get estimated recording time based on available storage
     */
    fun getEstimatedRecordingTimeMinutes(): Long {
        return storageManager.getEstimatedRecordingTimeMinutes()
    }
    
    /**
     * Update storage status for UI display
     */
    private fun updateStorageStatus() {
        try {
            android.util.Log.d("RecordingViewModel", "Updating storage status")
            val description = storageManager.getStorageDescription()
            val estimatedMinutes = storageManager.getEstimatedRecordingTimeMinutes()
            _storageStatus.value = "$description (~${estimatedMinutes}min)"
            android.util.Log.d("RecordingViewModel", "Storage status updated: ${_storageStatus.value}")
        } catch (e: Exception) {
            android.util.Log.e("RecordingViewModel", "Error updating storage status", e)
            _storageStatus.value = "Storage: Error checking"
        }
    }
    
    /**
     * Stop monitoring audio device changes
     */
    private fun stopAudioDeviceMonitoring() {
        audioDeviceManager?.stopMonitoring()
    }
    
    /**
     * Handle audio device changes during recording
     */
    private fun handleAudioDeviceChange(newDeviceType: AudioDeviceManager.AudioDeviceType, newDeviceName: String) {
        val isRecording = _status.value is Status.Recording
        
        android.util.Log.d("RecordingViewModel", 
            "Audio device changed: $currentDeviceName ($currentAudioDevice) -> $newDeviceName ($newDeviceType), isRecording=$isRecording")
        
        // Update current device info
        val oldDeviceType = currentAudioDevice
        val oldDeviceName = currentDeviceName
        currentAudioDevice = newDeviceType
        currentDeviceName = newDeviceName
        
        // Show notification about device change
        notificationHelper.showAudioSourceChangeNotification(newDeviceName, isRecording)
        
        // If currently recording, attempt to switch audio source
        if (isRecording) {
            android.util.Log.d("RecordingViewModel", "Currently recording - attempting audio source switch")
            switchRecordingAudioSource(newDeviceType)
        } else {
            android.util.Log.d("RecordingViewModel", "Not currently recording - just updating device info")
        }
    }
    
    /**
     * Switch audio source during active recording
     */
    private fun switchRecordingAudioSource(deviceType: AudioDeviceManager.AudioDeviceType) {
        try {
            val newAudioSource = recorder.getOptimalAudioSource(deviceType)
            val currentSource = recorder.getCurrentAudioSource()
            
            android.util.Log.d("RecordingViewModel", 
                "Switching audio source: currentSource=$currentSource, newAudioSource=$newAudioSource, deviceType=$deviceType")
            
            if (newAudioSource != currentSource) {
                android.util.Log.d("RecordingViewModel", "Audio sources differ - attempting switch")
                
                // Note: MediaRecorder requires restart to change source
                // This is a limitation - we can't seamlessly switch without brief interruption
                val success = recorder.switchAudioSource(newAudioSource)
                
                android.util.Log.d("RecordingViewModel", "Audio source switch result: $success")
                
                if (success) {
                    // Successfully switched - continue recording with new source
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(100) // Brief delay for stabilization
                        // Auto-hide notification after a few seconds
                        kotlinx.coroutines.delay(3000)
                        notificationHelper.hideAudioSourceChangeNotification()
                    }
                } else {
                    android.util.Log.w("RecordingViewModel", "Failed to switch audio source")
                }
            } else {
                android.util.Log.d("RecordingViewModel", "Audio sources are the same - no switch needed")
            }
        } catch (e: Exception) {
            android.util.Log.e("RecordingViewModel", "Error switching audio source", e)
            // If switching fails, continue with current source
        }
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
        try {
            android.util.Log.d("RecordingViewModel", "Setting up phone state listener")
            val app = getApplication<Application>()
            
            // Check if we have the required permission
            if (ActivityCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.d("RecordingViewModel", "READ_PHONE_STATE permission not granted, skipping phone state listener setup")
                return
            }

            telephonyManager = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ - use TelephonyCallback
                setupModernCallCallback()
            } else {
                // Android 11 and below - use PhoneStateListener
                setupLegacyPhoneStateListener()
            }
            
            android.util.Log.d("RecordingViewModel", "Phone state listener setup completed successfully")
            
        } catch (e: Exception) {
            android.util.Log.w("RecordingViewModel", "Failed to setup phone state listener - continuing without call detection", e)
            // Silently fail if phone state detection setup fails - app should still work
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
        notificationHelper.hideAudioSourceChangeNotification()
        notificationHelper.hideStorageNotifications()
        
        // Clean up audio device monitoring
        stopAudioDeviceMonitoring()
        
        // Clean up storage monitoring
        stopStorageMonitoring()
        
        // Hide silence warning notifications
        notificationHelper.hideSilenceWarningNotification()
    }
    
    /**
     * Handle silence detection callback from recorder
     */
    private fun handleSilenceDetected(silenceDurationSeconds: Int) {
        android.util.Log.w("RecordingViewModel", 
            "Silence detected for ${silenceDurationSeconds} seconds during recording")
        
        // Only show notification if not already showing and currently recording
        if (!isShowingSilenceWarning && _status.value is Status.Recording) {
            isShowingSilenceWarning = true
            notificationHelper.showSilenceWarningNotification(silenceDurationSeconds)
            
            // Auto-hide notification after 10 seconds to avoid spam
            viewModelScope.launch {
                kotlinx.coroutines.delay(10000)
                if (isShowingSilenceWarning) {
                    isShowingSilenceWarning = false
                    notificationHelper.hideSilenceWarningNotification()
                }
            }
        }
    }
    
    /**
     * Manually check microphone and reset silence counter
     * Useful for testing or when user wants to check if mic is working
     */
    fun checkMicrophone() {
        android.util.Log.d("RecordingViewModel", "Manual microphone check requested")
        
        // Reset silence counter in recorder
        recorder.resetSilenceCounter()
        
        // Hide any existing silence warning
        if (isShowingSilenceWarning) {
            isShowingSilenceWarning = false
            notificationHelper.hideSilenceWarningNotification()
        }
        
        // Log current silence status for debugging
        val currentSilenceDuration = recorder.getCurrentSilenceDuration()
        val isCurrentlySilent = recorder.isCurrentlySilent()
        
        android.util.Log.d("RecordingViewModel", 
            "Microphone check - Silence duration: ${currentSilenceDuration}s, Currently silent: $isCurrentlySilent")
    }
    
    /**
     * Get current silence detection status for UI display
     */
    fun getSilenceStatus(): String {
        if (!recorder.isRecording()) return "Not recording"
        
        val silenceDuration = recorder.getCurrentSilenceDuration()
        return if (silenceDuration > 0) {
            "Silent for ${silenceDuration}s"
        } else {
            "Audio detected"
        }
    }
}

data class RecordingItem(
    val file: File,
    val name: String,
    val date: Date,
    val durationSec: Long
)