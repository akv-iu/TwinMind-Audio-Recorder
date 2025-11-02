package com.example.myapplication


import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateBounds
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val multiplePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request necessary permissions
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )
        
        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        multiplePermissionLauncher.launch(permissions.toTypedArray())

        setContent {
            MyApplicationTheme {
                AudioRecorderApp()
            }
        }
    }
    

}

@Composable
fun AudioRecorderApp() {
    val viewModel: RecordingViewModel = viewModel()
    val status by viewModel.status
    val timerText by viewModel.timerText
    val recordings by viewModel.recordings
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
                    RecordingCard(item = recording, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun RecordingCard(item: RecordingItem, viewModel: RecordingViewModel) {
    var isPlaying by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
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
                    if (isPlaying) {
                        viewModel.stopPlayback()
                        isPlaying = false
                    } else {
                        viewModel.play(item)
                        isPlaying = true
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val duration = formatDuration(item.durationSec)
                Text(
                    text = "${timeFmt.format(item.date)} · $duration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    }

    // Auto-stop UI when playback ends
    DisposableEffect(item.file) {
        val listener = MediaPlayer.OnCompletionListener {
            isPlaying = false
        }
        viewModel.player.setOnCompletionListener(listener)  // ← now resolved

        onDispose {
            viewModel.player.setOnCompletionListener(null)
        }
    }
}


private fun formatDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}