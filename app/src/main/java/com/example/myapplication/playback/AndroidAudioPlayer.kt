package com.example.myapplication.playback

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class AndroidAudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun playFile(file: File, onCompletion: () -> Unit = {}) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                onCompletion()
                releasePlayer()
            }
            prepare()
            start()
        }
    }

    fun stop() {
        player?.stop()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}