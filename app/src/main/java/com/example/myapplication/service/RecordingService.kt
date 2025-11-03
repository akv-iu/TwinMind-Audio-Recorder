package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R

/**
 * Foreground service that shows live recording status on lock screen
 * Displays timer, status, and control actions
 */
class RecordingService : Service() {
    
    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording_service_channel"
        private const val CHANNEL_NAME = "Recording Service"
        
        // Actions for notification buttons
        const val ACTION_PAUSE_RESUME = "action_pause_resume"
        const val ACTION_STOP = "action_stop"
        
        // Service commands
        const val COMMAND_START_RECORDING = "start_recording"
        const val COMMAND_PAUSE_RECORDING = "pause_recording"
        const val COMMAND_RESUME_RECORDING = "resume_recording"
        const val COMMAND_STOP_RECORDING = "stop_recording"
        const val COMMAND_UPDATE_TIMER = "update_timer"
    }
    
    // Service binding
    private val binder = RecordingServiceBinder()
    
    // Recording state
    enum class RecordingStatus {
        STOPPED, RECORDING, PAUSED, PAUSED_FOR_CALL
    }
    
    private var currentStatus = RecordingStatus.STOPPED
    private var recordingTimer = "00:00"
    private var startTime = 0L
    private var pausedTime = 0L
    
    // Timer handler for live updates
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    
    // Callback interface for communication with ViewModel
    interface RecordingServiceCallback {
        fun onPauseResumeRequested()
        fun onStopRequested()
    }
    
    private var callback: RecordingServiceCallback? = null
    
    inner class RecordingServiceBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RecordingService created")
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "RecordingService bound")
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "=== RecordingService onStartCommand ===")
        Log.d(TAG, "Action: ${intent?.action}")
        Log.d(TAG, "Available actions: $ACTION_PAUSE_RESUME, $ACTION_STOP")
        Log.d(TAG, "Current service status: $currentStatus")
        
        when (intent?.action) {
            ACTION_PAUSE_RESUME -> {
                Log.d(TAG, "=== PAUSE/RESUME ACTION TRIGGERED ===")
                Log.d(TAG, "Current status: $currentStatus")
                Log.d(TAG, "Callback available: ${callback != null}")
                
                // Only notify ViewModel - let it handle status changes and update us back
                if (callback != null) {
                    Log.d(TAG, "Calling ViewModel callback...")
                    callback?.onPauseResumeRequested()
                    Log.d(TAG, "ViewModel callback called - waiting for status sync")
                } else {
                    Log.e(TAG, "No callback available - ViewModel not connected!")
                }
                
                // Don't update our status here - let ViewModel sync it back to us
                // This prevents the one-step-behind issue
            }
            ACTION_STOP -> {
                Log.d(TAG, "=== STOP ACTION TRIGGERED ===")
                Log.d(TAG, "Callback available: ${callback != null}")
                
                // Only notify ViewModel - let it handle stopping and clean up
                if (callback != null) {
                    Log.d(TAG, "Calling ViewModel stop callback...")
                    callback?.onStopRequested()
                    Log.d(TAG, "ViewModel stop callback called")
                } else {
                    Log.e(TAG, "No callback available - ViewModel not connected!")
                    // If no callback, stop service directly as fallback
                    stopRecordingService()
                }
                
                // Don't stop service here if callback worked - let ViewModel handle it
            }
            COMMAND_START_RECORDING -> {
                Log.d(TAG, "Starting recording service via command")
                startRecordingService()
            }
            COMMAND_PAUSE_RECORDING -> {
                updateRecordingStatus(RecordingStatus.PAUSED)
            }
            COMMAND_RESUME_RECORDING -> {
                updateRecordingStatus(RecordingStatus.RECORDING)
            }
            COMMAND_STOP_RECORDING -> {
                stopRecordingService()
            }
            COMMAND_UPDATE_TIMER -> {
                val timer = intent.getStringExtra("timer") ?: "00:00"
                updateTimer(timer)
            }
            null -> {
                Log.w(TAG, "RecordingService started with null action")
            }
            else -> {
                Log.w(TAG, "RecordingService started with unknown action: ${intent.action}")
            }
        }
        
        return START_STICKY // Restart service if killed
    }
    
    /**
     * Set callback for communication with ViewModel
     */
    fun setCallback(callback: RecordingServiceCallback) {
        this.callback = callback
        Log.d(TAG, "=== SERVICE CALLBACK SET ===")
        Log.d(TAG, "Callback object: $callback")
        Log.d(TAG, "Service can now communicate with ViewModel")
    }
    
    /**
     * Sync service state with ViewModel state
     */
    fun syncWithViewModelState(isRecording: Boolean, isPaused: Boolean, currentTimer: String) {
        Log.d(TAG, "=== SYNCING WITH VIEWMODEL ===")
        Log.d(TAG, "Previous status: $currentStatus")
        Log.d(TAG, "New state - Recording: $isRecording, Paused: $isPaused, Timer: $currentTimer")
        
        val newStatus = when {
            isRecording -> RecordingStatus.RECORDING
            isPaused -> RecordingStatus.PAUSED
            else -> RecordingStatus.STOPPED
        }
        
        currentStatus = newStatus
        recordingTimer = currentTimer
        
        Log.d(TAG, "Updated status to: $currentStatus")
        Log.d(TAG, "Updated timer to: $recordingTimer")
        
        updateNotification()
        
        // Start/stop timer updates based on recording state
        if (isRecording) {
            startTimerUpdates()
        } else {
            stopTimerUpdates()
        }
        
        Log.d(TAG, "Sync completed and notification updated")
    }
    
    /**
     * Start recording service with foreground notification
     */
    private fun startRecordingService() {
        Log.d(TAG, "Starting recording service")
        try {
            currentStatus = RecordingStatus.RECORDING
            startTime = System.currentTimeMillis()
            pausedTime = 0L
            
            val notification = createNotification()
            Log.d(TAG, "Created notification, starting foreground")
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Started foreground service successfully")
            
            startTimerUpdates()
            Log.d(TAG, "Timer updates started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording service", e)
        }
    }
    
    /**
     * Update recording status and notification
     */
    fun updateRecordingStatus(status: RecordingStatus, callRelated: Boolean = false) {
        Log.d(TAG, "Updating recording status to: $status")
        val oldStatus = currentStatus
        currentStatus = if (callRelated && status == RecordingStatus.PAUSED) {
            RecordingStatus.PAUSED_FOR_CALL
        } else {
            status
        }
        
        // Handle timer based on status changes
        when (currentStatus) {
            RecordingStatus.RECORDING -> {
                if (oldStatus == RecordingStatus.PAUSED || oldStatus == RecordingStatus.PAUSED_FOR_CALL) {
                    // Resuming - adjust start time to account for paused duration
                    startTime = System.currentTimeMillis() - pausedTime
                } else {
                    // New recording
                    startTime = System.currentTimeMillis()
                    pausedTime = 0L
                }
                startTimerUpdates()
            }
            RecordingStatus.PAUSED, RecordingStatus.PAUSED_FOR_CALL -> {
                // Save elapsed time when pausing
                pausedTime = System.currentTimeMillis() - startTime
                stopTimerUpdates()
            }
            RecordingStatus.STOPPED -> {
                stopTimerUpdates()
            }
        }
        
        updateNotification()
    }
    
    /**
     * Update timer display from external source (ViewModel)
     */
    fun updateTimer(timer: String) {
        if (recordingTimer != timer) {
            recordingTimer = timer
            updateNotification()
            Log.d(TAG, "Timer updated to: $timer")
        }
    }

    /**
     * Update timer display (internal method)
     */
    private fun updateTimerInternal(timer: String) {
        updateTimer(timer)
    }
    
    /**
     * Stop recording service
     */
    private fun stopRecordingService() {
        Log.d(TAG, "Stopping recording service")
        currentStatus = RecordingStatus.STOPPED
        recordingTimer = "00:00"
        stopTimerUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /**
     * Start timer updates every second (backup timer for notification)
     */
    private fun startTimerUpdates() {
        stopTimerUpdates() // Ensure no duplicate timers
        
        timerRunnable = object : Runnable {
            override fun run() {
                if (currentStatus == RecordingStatus.RECORDING) {
                    // Only update notification, don't calculate our own timer
                    // Timer should come from ViewModel for accuracy
                    updateNotification()
                    timerHandler.postDelayed(this, 1000)
                } else {
                    Log.d(TAG, "Timer stopped - status: $currentStatus")
                }
            }
        }
        timerHandler.post(timerRunnable!!)
        Log.d(TAG, "Timer updates started for status: $currentStatus")
    }
    
    /**
     * Stop timer updates
     */
    private fun stopTimerUpdates() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
    }
    
    /**
     * Create notification channel for Android 8+ (stopwatch-style)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel for Android 8+")
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH // HIGH importance like stopwatch for lock screen
            ).apply {
                description = "Live audio recording timer with pause/stop controls"
                setShowBadge(true)
                setSound(null, null) // Silent updates like stopwatch
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false) // No vibration like stopwatch
                enableLights(false) // No lights like stopwatch
                setBypassDnd(false) // Don't bypass Do Not Disturb
                canBubble() // Allow bubbles if supported
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "High-importance notification channel created for lock screen visibility")
        } else {
            Log.d(TAG, "Android version < 8, no channel creation needed")
        }
    }
    
    /**
     * Create notification with live recording status (stopwatch-style)
     */
    private fun createNotification(): Notification {
        // Main app intent
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else 
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Pause/Resume action intent
        val pauseResumeIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_PAUSE_RESUME
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 1, pauseResumeIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else 
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Stop action intent
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else 
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Determine status text and actions
        val (statusText, pauseResumeText, pauseResumeIcon) = when (currentStatus) {
            RecordingStatus.RECORDING -> Triple("Recording", "Pause", android.R.drawable.ic_media_pause)
            RecordingStatus.PAUSED -> Triple("Paused", "Resume", android.R.drawable.ic_media_play)
            RecordingStatus.PAUSED_FOR_CALL -> Triple("Paused - Call", "Resume", android.R.drawable.ic_media_play)
            RecordingStatus.STOPPED -> Triple("Stopped", "Start", android.R.drawable.ic_media_play)
        }
        
        // Create stopwatch-style notification like Android's built-in timer
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Microphone icon
            .setContentTitle("Audio Recording") // Simple title like stopwatch
            .setContentText(recordingTimer) // Timer as main content (like stopwatch)
            .setSubText(statusText) // Status as subtitle
            .setContentIntent(mainPendingIntent)
            .setOngoing(true) // Persistent like stopwatch
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH) // Use stopwatch category
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for lock screen
            .setShowWhen(false) // Don't show system time, we have our own timer
            .setUsesChronometer(false) // We handle our own timing
            .setLocalOnly(true) // Don't sync to wear devices
            
        // Add actions (like stopwatch pause/stop buttons)
        notificationBuilder.addAction(pauseResumeIcon, pauseResumeText, pauseResumePendingIntent)
        notificationBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
        
        // Use MediaStyle for better action button display (like stopwatch)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1) // Show both buttons in compact view
            )
        }
        
        return notificationBuilder.build()
    }
    
    /**
     * Update existing notification
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "RecordingService destroyed")
        stopTimerUpdates()
        callback = null
    }
    
    /**
     * Public methods for external control
     */
    fun startRecording() {
        startRecordingService()
    }
    
    fun pauseRecording(isCallRelated: Boolean = false) {
        updateRecordingStatus(RecordingStatus.PAUSED, isCallRelated)
    }
    
    fun resumeRecording() {
        updateRecordingStatus(RecordingStatus.RECORDING)
    }
    
    fun stopRecording() {
        stopRecordingService()
    }
    
    fun isRecording(): Boolean = currentStatus == RecordingStatus.RECORDING
    fun isPaused(): Boolean = currentStatus == RecordingStatus.PAUSED || currentStatus == RecordingStatus.PAUSED_FOR_CALL
    fun isStopped(): Boolean = currentStatus == RecordingStatus.STOPPED

    // Test method to manually show notification for debugging
    fun testNotification() {
        Log.d(TAG, "Testing notification display manually")
        val testNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("TEST: Audio Recording")
            .setContentText("00:30")
            .setSubText("Testing")
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(999, testNotification)
        Log.d(TAG, "Test stopwatch-style notification posted with ID 999")
    }

    // Force start recording service for testing
    fun forceStartRecordingService() {
        Log.d(TAG, "Force starting recording service for testing")
        startRecordingService()
    }
}