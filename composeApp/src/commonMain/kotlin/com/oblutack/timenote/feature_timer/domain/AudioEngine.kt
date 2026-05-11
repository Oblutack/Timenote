package com.oblutack.timenote.feature_timer.domain

interface AudioRecorder {
    fun startRecording(fileName: String)
    fun stopRecording(): String? // Returns the file path where it was saved
}

interface AudioPlayer {
    fun play(filePath: String)
    fun pause()
    fun stop()
    fun isPlaying(): Boolean
}

object AudioLocator {
    var audioRecorder: AudioRecorder? = null
    var audioPlayer: AudioPlayer? = null
}