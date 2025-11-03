package com.example.myapplication

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager


/**
 * Custom Application class for initializing app-wide components
 */
class RecorderApplication : Application(), Configuration.Provider {
    
    companion object {
        private const val TAG = "RecorderApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application starting up - simplified version without chunk recovery")
        
        try {
            // Initialize WorkManager (but don't start any recovery tasks)
            WorkManager.initialize(this, workManagerConfiguration)
            Log.d(TAG, "WorkManager initialized successfully")
            
            // Cancel any existing WorkManager tasks that might be processing chunks
            val workManager = WorkManager.getInstance(this)
            workManager.cancelAllWork()
            workManager.pruneWork() // Remove completed/cancelled work from database
            Log.d(TAG, "Cancelled and pruned all existing WorkManager tasks")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during application initialization", e)
            // Don't crash the app
        }
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
    

}