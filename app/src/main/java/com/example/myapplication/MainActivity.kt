package com.example.myapplication

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.playback.AndroidAudioPlayer
import com.example.myapplication.record.AndroidAudioRecorder
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val recorder by lazy { AndroidAudioRecorder(applicationContext) }
    private val player by lazy { AndroidAudioPlayer(applicationContext) }

    private var audioFile: File? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // Handle permission denial (e.g., show a message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AudioRecorderScreen(
                    onStartRecording = {
                        audioFile = File(cacheDir, "recording.m4a").also { recorder.start(it) }
                    },
                    onStopRecording = { recorder.stop() },
                    onPlay = { audioFile?.let { player.playFile(it) } },
                    onStopPlaying = { player.stop() }
                )
            }
        }
    }
}

@Composable
fun AudioRecorderScreen(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlay: () -> Unit,
    onStopPlaying: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                onStartRecording()
                isRecording = true
            },
            enabled = !isRecording
        ) {
            Text("Start Recording")
        }

        Button(
            onClick = {
                onStopRecording()
                isRecording = false
            },
            enabled = isRecording
        ) {
            Text("Stop Recording")
        }

        Button(
            onClick = {
                onPlay()
                isPlaying = true
            },
            enabled = !isRecording && !isPlaying
        ) {
            Text("Play")
        }

        Button(
            onClick = {
                onStopPlaying()
                isPlaying = false
            },
            enabled = isPlaying
        ) {
            Text("Stop Playing")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AudioRecorderScreenPreview() {
    MyApplicationTheme {
        AudioRecorderScreen(
            onStartRecording = { },
            onStopRecording = { },
            onPlay = { },
            onStopPlaying = { }
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}