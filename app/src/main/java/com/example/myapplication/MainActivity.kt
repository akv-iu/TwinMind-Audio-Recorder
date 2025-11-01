package com.example.myapplication

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.RecordingViewModel
import com.example.myapplication.ui.RecordingItem  // Import from ui package
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            MyApplicationTheme {
                RecordingScreen()
            }
        }
    }
}

@Composable
fun RecordingScreen(vm: RecordingViewModel = viewModel()) {
    val status by vm.status
    val timer by vm.timerText
    val recordings by vm.recordings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ───── RECORD / STOP BUTTON ─────
        Button(
            onClick = { vm.toggleRecord() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (status is RecordingViewModel.Status.Recording)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(56.dp)
        ) {
            Text(
                text = if (status is RecordingViewModel.Status.Recording) "Stop" else "Record",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ───── TIMER ─────
        Text(
            text = timer,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ───── STATUS ─────
        val (statusText, statusColor) = when (status) {
            is RecordingViewModel.Status.Recording -> "Recording..." to Color(0xFF4CAF50)
            else -> "Stopped" to Color.Gray
        }
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // ───── DASHBOARD ─────
        Text(
            text = "Meeting History",
            style = MaterialTheme.typography.titleLarge
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (recordings.isEmpty()) {
            Text(
                "No recordings yet",
                color = Color.Gray,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(recordings) { item ->
                    RecordingRow(item) { vm.play(item) }
                }
            }
        }
    }
}

@Composable
fun RecordingRow(item: RecordingItem, onPlay: () -> Unit) {
    Card(
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = item.name, fontWeight = FontWeight.Medium)
                val fmt = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                Text(
                    text = "${fmt.format(item.date)} • ${formatDuration(item.durationSec)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

private fun formatDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return String.format("%02d:%02d", m, s)
}