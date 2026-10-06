package com.oblutack.timenote.feature_timer.domain

interface AudioRecorder {
    /** Returns false if recording could not start (for example the microphone permission was denied). */
    fun startRecording(fileName: String): Boolean
    fun stopRecording(): String? // Returns the file path where it was saved
}

interface AudioPlayer {
    fun play(filePath: String, onComplete: () -> Unit)
    fun pause()
    fun stop()
    fun isPlaying(): Boolean
}
