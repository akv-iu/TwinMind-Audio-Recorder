// playback/AndroidAudioPlayer.kt
package com.example.myapplication.playback

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class AndroidAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun playFile(file: File) {
        stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun setOnCompletionListener(listener: MediaPlayer.OnCompletionListener?) {
        mediaPlayer?.setOnCompletionListener(listener)
    }
}