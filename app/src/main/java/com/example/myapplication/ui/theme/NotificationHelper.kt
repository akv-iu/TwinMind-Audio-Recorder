package com.example.myapplication.ui.theme

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myapplication.MainActivity

class NotificationHelper(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "recording_pause_channel"
        private const val CHANNEL_NAME = "Recording Pause Notifications"
        private const val NOTIFICATION_ID = 2001
        
        private const val AUDIO_SOURCE_CHANNEL_ID = "audio_source_channel"
        private const val AUDIO_SOURCE_CHANNEL_NAME = "Audio Source Changes"
        private const val AUDIO_SOURCE_NOTIFICATION_ID = 2002
        
        private const val STORAGE_CHANNEL_ID = "storage_warnings_channel"
        private const val STORAGE_CHANNEL_NAME = "Storage Warnings"
        private const val STORAGE_NOTIFICATION_ID = 2003
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Recording pause channel
            val pauseChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when recording is paused due to phone calls"
            }
            
            // Audio source change channel
            val audioSourceChannel = NotificationChannel(
                AUDIO_SOURCE_CHANNEL_ID,
                AUDIO_SOURCE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications when audio recording source changes"
                setShowBadge(false)
            }
            
            // Storage warning channel
            val storageChannel = NotificationChannel(
                STORAGE_CHANNEL_ID,
                STORAGE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warnings when storage is running low during recording"
            }
            
            notificationManager.createNotificationChannels(listOf(pauseChannel, audioSourceChannel, storageChannel))
        }
    }
    
    fun showRecordingPausedNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT 
            else 
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("Recording Paused")
            .setContentText("Recording paused due to phone call")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your audio recording has been automatically paused because of an incoming or outgoing phone call. Recording will resume when the call ends."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted - silently fail
        }
    }
    
    fun hideRecordingPausedNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
    
    /**
     * Show notification when audio recording source changes
     */
    fun showAudioSourceChangeNotification(deviceName: String, isRecording: Boolean = false) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT 
            else 
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val title = if (isRecording) "Recording Source Changed" else "Audio Source Changed"
        val contentText = if (isRecording) 
            "Recording switched to $deviceName" 
        else 
            "Audio source: $deviceName"
        
        val notification = NotificationCompat.Builder(context, AUDIO_SOURCE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(3000) // Auto dismiss after 3 seconds
            .setContentIntent(pendingIntent)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(AUDIO_SOURCE_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted - silently fail
        }
    }
    
    /**
     * Hide audio source change notification
     */
    fun hideAudioSourceChangeNotification() {
        NotificationManagerCompat.from(context).cancel(AUDIO_SOURCE_NOTIFICATION_ID)
    }
    
    /**
     * Show low storage warning notification
     */
    fun showLowStorageNotification(availableMB: Long, isCritical: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT 
            else 
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val (title, message, icon) = if (isCritical) {
            Triple(
                "Recording Stopped - Low Storage",
                "Recording stopped automatically. Only ${availableMB}MB storage remaining.",
                android.R.drawable.ic_dialog_alert
            )
        } else {
            Triple(
                "Low Storage Warning",
                "Storage running low: ${availableMB}MB remaining. Recording may stop soon.",
                android.R.drawable.stat_sys_warning
            )
        }
        
        val notification = NotificationCompat.Builder(context, STORAGE_CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(STORAGE_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted - silently fail
        }
    }
    
    /**
     * Show insufficient storage error notification
     */
    fun showInsufficientStorageNotification(availableMB: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT 
            else 
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, STORAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Cannot Start Recording")
            .setContentText("Insufficient storage: ${availableMB}MB available. Need at least 50MB.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Cannot start recording due to insufficient storage space. ${availableMB}MB available, but at least 50MB is required. Please free up some space and try again."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(STORAGE_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted - silently fail
        }
    }
    
    /**
     * Hide storage notifications
     */
    fun hideStorageNotifications() {
        NotificationManagerCompat.from(context).cancel(STORAGE_NOTIFICATION_ID)
    }
}