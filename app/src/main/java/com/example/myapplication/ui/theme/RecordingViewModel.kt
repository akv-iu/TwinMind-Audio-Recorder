// ui/RecordingViewModel.kt
package com.example.myapplication.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.record.AndroidAudioRecorder
import com.example.myapplication.playback.AndroidAudioPlayer
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    sealed class Status {
        object Stopped : Status()
        object Recording : Status()
    }

    // Public read-only State
    private val _status = mutableStateOf<Status>(Status.Stopped)
    val status: State<Status> = _status

    private val _timerText = mutableStateOf("00:00")
    val timerText: State<String> = _timerText

    private val _recordings = mutableStateOf<List<RecordingItem>>(emptyList())
    val recordings: State<List<RecordingItem>> = _recordings

    // Internals
    private val recorder = AndroidAudioRecorder(app)
    private val player = AndroidAudioPlayer(app)
    private var currentFile: File? = null
    private var timerJob: Job? = null
    private var startTimeMs = 0L

    fun toggleRecord() {
        when (_status.value) {
            is Status.Recording -> stopRecording()
            is Status.Stopped -> startRecording()
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
        try { recorder.stop() } catch (_: Exception) {}

        _status.value = Status.Stopped
        _timerText.value = "00:00"

        currentFile?.takeIf { it.exists() && it.length() > 0 }?.let { file ->
            val duration = elapsedSeconds()
            val item = RecordingItem(file, file.nameWithoutExtension, Date(), duration)
            _recordings.value = _recordings.value + item
        }

        currentFile = null
        startTimeMs = 0L
    }

    fun play(item: RecordingItem) = player.playFile(item.file)

    private fun startTimer() {
        startTimeMs = System.currentTimeMillis()
        timerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
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