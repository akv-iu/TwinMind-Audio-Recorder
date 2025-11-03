// playback/AndroidAudioPlayer.kt
package com.example.myapplication.playback

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class AndroidAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun playFile(file: File) {
        stop()
        
        android.util.Log.d("AndroidAudioPlayer", "Attempting to play file: ${file.absolutePath}")
        android.util.Log.d("AndroidAudioPlayer", "File exists: ${file.exists()}, Size: ${file.length()} bytes")
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                android.util.Log.d("AndroidAudioPlayer", "Data source set")
                
                setOnPreparedListener { mp ->
                    android.util.Log.d("AndroidAudioPlayer", "MediaPlayer prepared, starting playback")
                    mp.start()
                }
                
                setOnErrorListener { mp, what, extra ->
                    android.util.Log.e("AndroidAudioPlayer", "MediaPlayer error: what=$what, extra=$extra")
                    false
                }
                
                prepareAsync()
                android.util.Log.d("AndroidAudioPlayer", "MediaPlayer preparing asynchronously...")
            }
        } catch (e: Exception) {
            android.util.Log.e("AndroidAudioPlayer", "Error setting up MediaPlayer", e)
            mediaPlayer?.release()
            mediaPlayer = null
            throw e
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