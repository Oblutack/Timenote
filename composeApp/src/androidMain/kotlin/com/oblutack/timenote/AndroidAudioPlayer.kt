package com.oblutack.timenote

import android.media.MediaPlayer
import com.oblutack.timenote.feature_timer.domain.AudioPlayer

class AndroidAudioPlayer : AudioPlayer {

    private var player: MediaPlayer? = null

    override fun play(filePath: String) {
        // If it's the same file and just paused, resume it
        if (player != null && !player!!.isPlaying) {
            player?.start()
            return
        }

        // Otherwise, load the new file
        stop()
        player = MediaPlayer().apply {
            try {
                setDataSource(filePath)
                prepare()
                start()

                // Automatically release when finished
                setOnCompletionListener {
                    stop()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun pause() {
        if (player?.isPlaying == true) {
            player?.pause()
        }
    }

    override fun stop() {
        player?.stop()
        player?.release()
        player = null
    }

    override fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }
}