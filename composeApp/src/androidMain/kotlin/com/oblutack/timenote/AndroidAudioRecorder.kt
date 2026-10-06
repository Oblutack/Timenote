package com.oblutack.timenote

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.oblutack.timenote.core.logError
import com.oblutack.timenote.feature_timer.domain.AudioRecorder
import java.io.File
import java.io.FileOutputStream

class AndroidAudioRecorder(private val context: Context) : AudioRecorder {

    private companion object { const val TAG = "AudioRecorder" }

    private var recorder: MediaRecorder? = null
    private var outputStream: FileOutputStream? = null
    private var currentFilePath: String? = null

    override fun startRecording(fileName: String): Boolean {
        releaseQuietly()

        // filesDir (not cacheDir): the OS may clear the cache at any time, which would orphan saved voice memos
        val dir = File(context.filesDir, "voice_memos").apply { mkdirs() }
        val file = File(dir, "$fileName.m4a")

        var stream: FileOutputStream? = null
        var newRecorder: MediaRecorder? = null
        return try {
            val out = FileOutputStream(file)
            stream = out
            // MediaRecorder(context) is required on Android 12+
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            newRecorder = created
            created.apply {
                // throws "setAudioSource failed" when the microphone permission is denied
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000) // 128 kbps
                setAudioSamplingRate(44100)     // 44.1 kHz
                setOutputFile(out.fd)
                prepare()
                start()
            }
            recorder = created
            outputStream = out
            currentFilePath = file.absolutePath
            true
        } catch (e: Exception) {
            logError(TAG, "Could not start recording", e)
            runCatching { newRecorder?.release() }
            runCatching { stream?.close() }
            file.delete() // don't leave an empty file behind
            false
        }
    }

    override fun stopRecording(): String? {
        val path = currentFilePath
        return try {
            recorder?.stop()
            path
        } catch (e: Exception) {
            logError(TAG, "Could not stop recording", e)
            null
        } finally {
            releaseQuietly()
        }
    }

    private fun releaseQuietly() {
        runCatching { recorder?.release() }
        runCatching { outputStream?.close() }
        recorder = null
        outputStream = null
        currentFilePath = null
    }
}
