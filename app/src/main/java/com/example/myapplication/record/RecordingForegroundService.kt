package com.example.myapplication.record

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
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

        private const val NOTIF_CHANNEL_ID = "recording_service_channel"
        private const val NOTIF_CHANNEL_NAME = "Recording"
        private const val NOTIF_ID = 1001
        private const val CHUNK_MILLIS = 30_000L
    }

    private val recorder by lazy { AndroidAudioRecorder(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val isPausedForCall = AtomicBoolean(false)
    private var chunkIndex = 0
    
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupPhoneStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundAndRecord()
            ACTION_STOP -> stopRecordingAndStopSelf()
            else -> {
                // no-op
            }
        }

        // Keep service running until explicitly stopped
        return START_STICKY
    }

    private fun startForegroundAndRecord() {
        if (isRecording.getAndSet(true)) return

        val notif = buildNotification("Recording — chunk ${chunkIndex + 1}")
        startForeground(NOTIF_ID, notif)

        recordingJob = scope.launch {
            try {
                val dir = File(getExternalFilesDir(null), "recordings").apply { if (!exists()) mkdirs() }

                while (isActive && isRecording.get()) {
                    // Wait while paused for phone call
                    while (isPausedForCall.get() && isActive && isRecording.get()) {
                        delay(1000L)
                    }
                    
                    // If we're no longer recording, exit the loop
                    if (!isRecording.get()) break
                    
                    val file = File(dir, "meeting_${timestamp()}_${chunkIndex}.m4a")
                    chunkIndex += 1

                    try {
                        recorder.start(file)
                    } catch (e: Exception) {
                        // if start fails, break the loop and stop service
                        e.printStackTrace()
                        break
                    }

                    // wait for chunk duration or until cancelled/paused
                    var waited = 0L
                    while (waited < CHUNK_MILLIS && isActive && isRecording.get() && !isPausedForCall.get()) {
                        delay(1000L)
                        waited += 1000L
                    }

                    try { recorder.stop() } catch (t: Throwable) { t.printStackTrace() }

                    // update notification with new chunk index (if not paused)
                    if (!isPausedForCall.get()) {
                        updateNotificationText("Recording — chunk ${chunkIndex + 1}")
                    }
                }
            } finally {
                // ensure recorder stopped
                try { recorder.stop() } catch (_: Exception) {}
                isRecording.set(false)
                isPausedForCall.set(false)
                stopForeground(false)
            }
        }
    }

    private fun stopRecordingAndStopSelf() {
        if (!isRecording.getAndSet(false)) return

        recordingJob?.cancel()
        try { recorder.stop() } catch (_: Exception) {}
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
        try {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            phoneStateListener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_IDLE -> {
                            // Call ended - resume recording if we were paused
                            if (isPausedForCall.getAndSet(false)) {
                                updateNotificationText("Recording — chunk ${chunkIndex + 1}")
                            }
                        }
                        TelephonyManager.CALL_STATE_RINGING, 
                        TelephonyManager.CALL_STATE_OFFHOOK -> {
                            // Call starting - pause recording
                            if (isRecording.get()) {
                                isPausedForCall.set(true)
                                try { recorder.stop() } catch (_: Exception) {}
                                updateNotificationText("Paused - Phone call")
                            }
                        }
                    }
                }
            }
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
            // Phone state permission not granted - continue without call detection
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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up phone state listener
        phoneStateListener?.let { listener ->
            try {
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_NONE)
            } catch (_: Exception) {}
        }
        
        recordingJob?.cancel()
        scope.cancel()
        try { recorder.stop() } catch (_: Exception) {}
    }
}
