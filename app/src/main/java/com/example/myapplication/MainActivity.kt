package com.example.myapplication


import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow

import android.Manifest
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.RecordingViewModel
import com.example.myapplication.ui.theme.RecordingItem
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.permissions.NotificationPermissionHelper
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private val multiplePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
        android.util.Log.e("TWINMIND_DEBUG", "PERMISSIONS CALLBACK RECEIVED")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.e("TWINMIND_DEBUG", "MAINACTIVITY ONCREATE CALLED - APP IS STARTING")
        super.onCreate(savedInstanceState)
        
        try {
            android.util.Log.e("TWINMIND_DEBUG", "MAINACTIVITY ONCREATE TRY BLOCK STARTED")
            enableEdgeToEdge()
            android.util.Log.e("TWINMIND_DEBUG", "EDGE TO EDGE ENABLED")
            
            // Request necessary permissions
            val permissions = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE
            )
            
            // Add notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            
            // Add Bluetooth permission for Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            
            // Add foreground service permissions for Android 14+ (API 34)
            if (Build.VERSION.SDK_INT >= 34) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            }
            
            multiplePermissionLauncher.launch(permissions.toTypedArray())
            android.util.Log.d("MainActivity", "Permission request sent")
        } catch (e: Exception) {
            android.util.Log.e("TWINMIND_DEBUG", "ERROR IN MAINACTIVITY ONCREATE", e)
        }
        android.util.Log.e("TWINMIND_DEBUG", "MAINACTIVITY ONCREATE METHOD COMPLETED")

        try {
            android.util.Log.w("TwinMindApp", "ABOUT TO SET CONTENT AND CREATE UI")
            setContent {
                MyApplicationTheme {
                    AudioRecorderApp()
                }
            }
            android.util.Log.w("TwinMindApp", "MAINACTIVITY ONCREATE COMPLETED SUCCESSFULLY")
        } catch (e: Exception) {
            android.util.Log.e("TwinMindApp", "ERROR SETTING CONTENT", e)
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Important: update the activity's intent
        android.util.Log.d("MainActivity", "onNewIntent called - bringing app to foreground")
        
        // Handle any specific intent actions if needed in the future
        // For now, just bringing the app to foreground is sufficient
    }

}

@Composable
fun AudioRecorderApp() {
    android.util.Log.w("MainActivity", "CREATING RECORDINGVIEWMODEL...")
    val viewModel: RecordingViewModel = viewModel()
    android.util.Log.w("MainActivity", "RECORDINGVIEWMODEL CREATED SUCCESSFULLY")
    
    val status by viewModel.status
    val timerText by viewModel.timerText
    val recordings by viewModel.recordings
    val currentlyPlayingItem by viewModel.currentlyPlayingItem
    
    // Notification permission check
    var showNotificationDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Check notification permissions when recording starts
    LaunchedEffect(status) {
        if (status is RecordingViewModel.Status.Recording && !NotificationPermissionHelper.areNotificationsEnabled(context)) {
            showNotificationDialog = true
        }
    }
    
    // Notification permission dialog
    if (showNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = { Text("Lock Screen Notifications") },
            text = { 
                Text("To see recording status on your lock screen, please enable notifications for this app in Settings.")
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        context.startActivity(NotificationPermissionHelper.getNotificationSettingsIntent(context))
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error opening notification settings", e)
                    }
                    showNotificationDialog = false
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDialog = false }) {
                    Text("Later")
                }
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "TwinMind Audio Recorder",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 32.dp)
        )

        // Timer Display
        Text(
            text = timerText,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Main Record Button
        FloatingActionButton(
            onClick = { viewModel.toggleRecord() },
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            containerColor = when (status) {
                is RecordingViewModel.Status.Recording -> MaterialTheme.colorScheme.error
                is RecordingViewModel.Status.Paused -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            }
        ) {
            Icon(
                imageVector = when (status) {
                    is RecordingViewModel.Status.Recording -> Icons.Default.MicOff
                    is RecordingViewModel.Status.Paused -> Icons.Default.PlayArrow
                    else -> Icons.Default.Mic
                },
                contentDescription = when (status) {
                    is RecordingViewModel.Status.Recording -> "Stop Recording"
                    is RecordingViewModel.Status.Paused -> "Resume Recording"
                    else -> "Start Recording"
                },
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }

        // Pause/Resume Button (only show when recording or paused)
        if (status is RecordingViewModel.Status.Recording || status is RecordingViewModel.Status.Paused) {
            FloatingActionButton(
                onClick = { viewModel.togglePause() },
                modifier = Modifier
                    .size(60.dp)
                    .padding(bottom = 32.dp),
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                    imageVector = if (status is RecordingViewModel.Status.Recording) 
                        Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (status is RecordingViewModel.Status.Recording) 
                        "Pause Recording" else "Resume Recording",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
        } else {
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Recordings List
        if (recordings.isNotEmpty()) {
            Text(
                text = "Recordings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recordings) { recording ->
                    RecordingCard(
                        item = recording, 
                        viewModel = viewModel,
                        isCurrentlyPlaying = currentlyPlayingItem == recording
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingCard(item: RecordingItem, viewModel: RecordingViewModel, isCurrentlyPlaying: Boolean = false) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                Color(0xFFF8FAFC)
        ),
        border = if (isCurrentlyPlaying) 
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isCurrentlyPlaying) {
                        viewModel.stopPlayback()
                    } else {
                        viewModel.play(item)
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isCurrentlyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isCurrentlyPlaying) "Pause" else "Play",
                    tint = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
                Spacer(Modifier.height(4.dp))
                val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val duration = formatDuration(item.durationSec)
                Text(
                    text = "${timeFmt.format(item.date)} · $duration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray
                )
            }
        }
    }
}


private fun formatDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}