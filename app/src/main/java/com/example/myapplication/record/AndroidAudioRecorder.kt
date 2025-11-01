package com.example.myapplication.record

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.carousel.rememberCarouselState
import java.io.File
import java.io.FileOutputStream

class AndroidAudioRecorder(
    private val context: Context
        ):AudioRecorder {
    private var recorder: MediaRecorder? = null

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createRecorder(): MediaRecorder{

    return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    {
        MediaRecorder(context)

    } else MediaRecorder()
}
    @RequiresApi(Build.VERSION_CODES.S)
    override fun start(outputFile: File) {
        createRecorder().apply{
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(FileOutputStream(outputFile).fd)

            prepare()
            start()

            recorder = this
        }

    }

    override fun stop() {
        recorder?.stop()
        recorder?.reset()
        recorder = null


    }
}