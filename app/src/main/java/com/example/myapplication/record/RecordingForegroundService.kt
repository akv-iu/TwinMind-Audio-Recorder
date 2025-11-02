package com.example.myapplication.record

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executor
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that records audio in repeated 30-second chunks and saves them to
 * app external files directory under "recordings".
 *
 * Start with: Intent(this, RecordingForegroundService::class.java).apply { action = ACTION_START }
 * Stop with: Intent(this, RecordingForegroundService::class.java).apply { action = ACTION_STOP }
 */
class RecordingForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.myapplication.action.START_RECORDING"
        const val ACTION_STOP = "com.example.myapplication.action.STOP_RECORDING"
        const val ACTION_TEST_PAUSE = "com.example.myapplication.action.TEST_PAUSE"

        private const val NOTIF_CHANNEL_ID = "recording_service_channel"
        private const val NOTIF_CHANNEL_NAME = "Recording"
        private const val NOTIF_ID = 1001
        private const val CHUNK_MILLIS = 30_000L
        private const val TAG = "RecordingService"
    }

    private val recorder by lazy { AndroidAudioRecorder(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val isPausedForCall = AtomicBoolean(false)
    private var chunkIndex = 0
    
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: Any? = null // TelephonyCallback for Android 12+
    private var callbackExecutor: Executor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupPhoneStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> startForegroundAndRecord()
            ACTION_STOP -> stopRecordingAndStopSelf()
            ACTION_TEST_PAUSE -> {
                Log.d(TAG, "Test pause requested")
                pauseRecordingForCall()
                // Auto-resume after 5 seconds for testing
                scope.launch {
                    delay(5000)
                    if (isPausedForCall.getAndSet(false)) {
                        updateNotificationText("Recording — chunk ${chunkIndex + 1}")
                        Log.d(TAG, "Test pause completed, resumed recording")
                    }
                }
            }
            else -> {
                Log.d(TAG, "Unknown action: ${intent?.action}")
            }
        }

        // Keep service running until explicitly stopped
        return START_STICKY
    }

    private fun startForegroundAndRecord() {
        if (isRecording.getAndSet(true)) {
            Log.d(TAG, "Already recording, ignoring start request")
            return
        }

        Log.d(TAG, "Starting foreground recording service")
        val notif = buildNotification("Recording — chunk ${chunkIndex + 1}")
        startForeground(NOTIF_ID, notif)

        recordingJob = scope.launch {
            try {
                val dir = File(getExternalFilesDir(null), "recordings").apply { if (!exists()) mkdirs() }
                Log.d(TAG, "Recording directory: ${dir.absolutePath}")

                while (isActive && isRecording.get()) {
                    // Wait while paused for phone call
                    while (isPausedForCall.get() && isActive && isRecording.get()) {
                        Log.d(TAG, "Waiting while paused for phone call")
                        delay(1000L)
                    }
                    
                    // If we're no longer recording, exit the loop
                    if (!isRecording.get()) {
                        Log.d(TAG, "Recording stopped, exiting loop")
                        break
                    }
                    
                    val file = File(dir, "meeting_${timestamp()}_${chunkIndex}.m4a")
                    chunkIndex += 1
                    Log.d(TAG, "Starting chunk $chunkIndex: ${file.name}")

                    try {
                        recorder.start(file)
                        Log.d(TAG, "Recorder started for chunk $chunkIndex")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start recorder for chunk $chunkIndex", e)
                        break
                    }

                    // wait for chunk duration or until cancelled/paused
                    var waited = 0L
                    while (waited < CHUNK_MILLIS && isActive && isRecording.get() && !isPausedForCall.get()) {
                        delay(1000L)
                        waited += 1000L
                    }

                    try { 
                        recorder.stop()
                        Log.d(TAG, "Recorder stopped for chunk $chunkIndex")
                    } catch (t: Throwable) { 
                        Log.e(TAG, "Failed to stop recorder for chunk $chunkIndex", t)
                    }

                    // update notification with new chunk index (if not paused)
                    if (!isPausedForCall.get()) {
                        updateNotificationText("Recording — chunk ${chunkIndex + 1}")
                    } else {
                        Log.d(TAG, "Chunk $chunkIndex completed but paused for call")
                    }
                }
            } finally {
                Log.d(TAG, "Recording loop ended, cleaning up")
                // ensure recorder stopped
                try { recorder.stop() } catch (_: Exception) {}
                isRecording.set(false)
                isPausedForCall.set(false)
                stopForeground(false)
            }
        }
    }

    private fun stopRecordingAndStopSelf() {
        if (!isRecording.getAndSet(false)) {
            Log.d(TAG, "Already stopped, ignoring stop request")
            return
        }

        Log.d(TAG, "Stopping recording service")
        recordingJob?.cancel()
        try { 
            recorder.stop()
            Log.d(TAG, "Recorder stopped successfully")
        } catch (e: Exception) { 
            Log.e(TAG, "Failed to stop recorder", e)
        }
        stopForeground(true)
        stopSelf()
    }

    private fun buildNotification(content: String) =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("TwinMind Recorder")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, com.example.myapplication.MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                android.R.drawable.ic_media_pause, 
                "Stop Recording",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, RecordingForegroundService::class.java).apply { action = ACTION_STOP },
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun setupPhoneStateListener() {
        // Check if we have the required permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted - cannot detect phone calls")
            return
        }

        try {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            Log.d(TAG, "Setting up phone state listener, Android API: ${Build.VERSION.SDK_INT}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ - use TelephonyCallback
                setupModernCallCallback()
            } else {
                // Android 11 and below - use PhoneStateListener
                setupLegacyPhoneStateListener()
            }
            
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception setting up phone state listener", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup phone state listener", e)
        }
    }
    
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun setupModernCallCallback() {
        callbackExecutor = Executor { command ->
            Handler(Looper.getMainLooper()).post(command)
        }
        
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallStateChange(state)
            }
        }
        
        try {
            telephonyManager?.registerTelephonyCallback(callbackExecutor!!, callback)
            telephonyCallback = callback
            Log.d(TAG, "Modern TelephonyCallback registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register TelephonyCallback", e)
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
            Log.d(TAG, "Legacy PhoneStateListener registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register PhoneStateListener", e)
        }
    }
    
    private fun handleCallStateChange(state: Int) {
        Log.d(TAG, "Call state changed: $state, isRecording: ${isRecording.get()}")
        
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Call ended - checking if paused: ${isPausedForCall.get()}")
                // Call ended - resume recording if we were paused
                if (isPausedForCall.getAndSet(false)) {
                    Log.d(TAG, "Resuming recording after call")
                    updateNotificationText("Recording — chunk ${chunkIndex + 1}")
                }
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "Phone ringing - pausing recording")
                pauseRecordingForCall()
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(TAG, "Call in progress - pausing recording")
                pauseRecordingForCall()
            }
        }
    }
    
    private fun pauseRecordingForCall() {
        if (isRecording.get()) {
            Log.d(TAG, "Pausing recording for phone call")
            isPausedForCall.set(true)
            try { 
                recorder.stop()
                Log.d(TAG, "Recorder stopped for call")
            } catch (e: Exception) { 
                Log.e(TAG, "Failed to stop recorder for call", e)
            }
            updateNotificationText("Paused - Phone call")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(NOTIF_CHANNEL_ID, NOTIF_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            channel.description = "Recording in background"
            nm.createNotificationChannel(channel)
        }
    }
    
    private fun updateNotificationText(text: String) {
        try {
            Log.d(TAG, "Updating notification: $text")
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = buildNotification(text)
            nm.notify(NOTIF_ID, notification)
            Log.d(TAG, "Notification updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service being destroyed, cleaning up")
        
        // Clean up phone state listeners
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
                telephonyManager?.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
                Log.d(TAG, "TelephonyCallback unregistered")
            } else if (phoneStateListener != null) {
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
                Log.d(TAG, "PhoneStateListener unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up phone state listener", e)
        }
        
        recordingJob?.cancel()
        scope.cancel()
        try { recorder.stop() } catch (_: Exception) {}
    }
}
