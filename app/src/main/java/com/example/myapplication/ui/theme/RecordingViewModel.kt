// File: ui/theme/RecordingViewModel.kt
package com.example.myapplication.ui.theme

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.playback.AndroidAudioPlayer
import com.example.myapplication.record.AndroidAudioRecorder
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    sealed class Status {
        object Stopped : Status()
        object Recording : Status()
        object Paused : Status()
    }

    private val _status = mutableStateOf<Status>(Status.Stopped)
    val status: State<Status> = _status

    private val _timerText = mutableStateOf("00:00")
    val timerText: State<String> = _timerText

    private val _recordings = mutableStateOf<List<RecordingItem>>(emptyList())
    val recordings: State<List<RecordingItem>> = _recordings

    // SINGLE PLAYER INSTANCE — shared everywhere
    private val audioPlayer = AndroidAudioPlayer(app)
    
    // Expose player for completion listener access
    val player: AndroidAudioPlayer get() = audioPlayer

    private val recorder = AndroidAudioRecorder(app)

    private var currentFile: File? = null
    private var timerJob: Job? = null
    private var startTimeMs = 0L
    private var pausedTimeMs = 0L

    fun toggleRecord() {
        when (_status.value) {
            is Status.Recording -> stopRecording()
            is Status.Stopped -> startRecording()
            is Status.Paused -> resumeRecording()
        }
    }
    
    fun togglePause() {
        when (_status.value) {
            is Status.Recording -> pauseRecording()
            is Status.Paused -> resumeRecording()
            is Status.Stopped -> { /* No action when stopped */ }
        }
    }

    private fun startRecording() {
        val file = File(
            getApplication<Application>().getExternalFilesDir(null),
            "meeting_${timestamp()}.m4a"
        ).also { it.parentFile?.mkdirs() }

        currentFile = file
        recorder.start(file)

        _status.value = Status.Recording
        startTimer()
    }

    fun stopRecording() {
        timerJob?.cancel()
        
        // Only stop recorder if we're currently recording (not if paused)
        if (_status.value is Status.Recording) {
            try { recorder.stop() } catch (_: Exception) {}
        }

        _status.value = Status.Stopped
        _timerText.value = "00:00"

        currentFile?.takeIf { it.exists() && it.length() > 0 }?.let { file ->
            val duration = if (pausedTimeMs > 0) pausedTimeMs / 1000 else elapsedSeconds()
            val item = RecordingItem(
                file = file,
                name = file.nameWithoutExtension,
                date = Date(),
                durationSec = duration
            )
            _recordings.value = _recordings.value + item
        }

        currentFile = null
        startTimeMs = 0L
        pausedTimeMs = 0L
    }

    private fun pauseRecording() {
        if (_status.value !is Status.Recording) return
        
        timerJob?.cancel()
        try { recorder.stop() } catch (_: Exception) {}
        
        // Save how much time has elapsed when we paused
        pausedTimeMs = System.currentTimeMillis() - startTimeMs
        _status.value = Status.Paused
    }
    
    private fun resumeRecording() {
        if (_status.value !is Status.Paused) return
        
        // Create a new file for the resumed recording (MediaRecorder doesn't support appending)
        val file = File(
            getApplication<Application>().getExternalFilesDir(null),
            "meeting_${timestamp()}_resumed.m4a"
        ).also { it.parentFile?.mkdirs() }
        
        currentFile = file
        
        try {
            recorder.start(file)
            _status.value = Status.Recording
            
            // Adjust start time to account for paused time
            startTimeMs = System.currentTimeMillis() - pausedTimeMs
            startTimer()
        } catch (e: Exception) {
            _status.value = Status.Stopped
            _timerText.value = "00:00"
        }
    }

    fun play(item: RecordingItem) {
        audioPlayer.playFile(item.file)
    }

    fun stopPlayback() {
        audioPlayer.stop()
    }

    private fun startTimer() {
        if (startTimeMs == 0L) {
            startTimeMs = System.currentTimeMillis()
        }
        timerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive && _status.value is Status.Recording) {
                val secs = elapsedSeconds()
                _timerText.value = String.format("%02d:%02d", secs / 60, secs % 60)
                delay(500)
            }
        }
    }

    private fun elapsedSeconds(): Long =
        ((System.currentTimeMillis() - startTimeMs) / 1000).coerceAtLeast(0)

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}

data class RecordingItem(
    val file: File,
    val name: String,
    val date: Date,
    val durationSec: Long
)