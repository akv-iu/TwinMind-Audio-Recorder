package com.example.myapplication.permissions

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Helper class for managing notification permissions and settings
 */
object NotificationPermissionHelper {
    
    /**
     * Check if notifications are enabled for the app
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    
    /**
     * Check if lock screen notifications are likely to be shown
     * This is a best-effort check as the exact lock screen behavior 
     * depends on device settings we can't directly access
     */
    fun canShowOnLockScreen(context: Context): Boolean {
        if (!areNotificationsEnabled(context)) {
            return false
        }
        
        // For Android 8.0+ we can check channel importance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check if Do Not Disturb is affecting notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val filter = notificationManager.currentInterruptionFilter
                if (filter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                    return false
                }
            }
        }
        
        return true
    }
    
    /**
     * Get intent to open notification settings for the app
     */
    fun getNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * Get intent to open lock screen notification settings
     */
    fun getLockScreenSettingsIntent(): Intent {
        return Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
    
    /**
     * Get a user-friendly message about notification permissions
     */
    fun getPermissionMessage(context: Context): String {
        return when {
            !areNotificationsEnabled(context) -> 
                "Notifications are disabled. Enable them in Settings to see recording status on lock screen."
            !canShowOnLockScreen(context) -> 
                "Lock screen notifications may be limited by your device settings."
            else -> 
                "Notifications are enabled. Recording status should appear on lock screen."
        }
    }
}