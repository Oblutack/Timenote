package com.oblutack.timenote

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.oblutack.timenote.feature_timer.domain.AudioRecorder
import java.io.File
import java.io.FileOutputStream

class AndroidAudioRecorder(private val context: Context) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var currentFilePath: String? = null

    override fun startRecording(fileName: String) {
        // Create a hidden file in the app's internal cache directory
        val file = File(context.cacheDir, "$fileName.m4a")
        currentFilePath = file.absolutePath

        // Create the recorder (Android 12+ requires context)
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(FileOutputStream(file).fd)

            try {
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun stopRecording(): String? {
        return try {
            recorder?.stop()
            recorder?.reset()
            val savedPath = currentFilePath
            currentFilePath = null
            savedPath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            recorder?.release()
            recorder = null
        }
    }
}