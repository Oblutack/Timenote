package com.oblutack.timenote

import android.media.MediaPlayer
import com.oblutack.timenote.feature_timer.domain.AudioPlayer

class AndroidAudioPlayer : AudioPlayer {

    private var player: MediaPlayer? = null

    override fun play(filePath: String, onComplete: () -> Unit) {
        // 1. If we are paused, try to safely resume
        if (player != null) {
            try {
                if (!player!!.isPlaying) {
                    player?.start()
                    return
                }
            } catch (e: Exception) {
                // If it fails, ignore and recreate the player below
            }
        }

        // 2. Safely destroy any existing hardware locks
        safeRelease()

        // 3. Create a fresh, crash-proof player
        try {
            player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()

                setOnCompletionListener {
                    safeRelease() // Destroys the player cleanly
                    onComplete()  // Resets the UI
                }

                // If the hardware glitches, this prevents a phone reboot!
                setOnErrorListener { _, _, _ ->
                    safeRelease()
                    onComplete()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            safeRelease()
            onComplete()
        }
    }

    override fun pause() {
        try {
            if (player?.isPlaying == true) {
                player?.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stop() {
        safeRelease()
    }

    // THE MAGIC FIX: Safely navigates the Android C++ State Machine
    private fun safeRelease() {
        try {
            player?.stop()
        } catch (e: Exception) {
            // Ignore: It was already stopped or completed
        }
        try {
            player?.release()
        } catch (e: Exception) {
            // Ignore
        }
        player = null
    }

    override fun isPlaying(): Boolean {
        return try {
            player?.isPlaying ?: false
        } catch (e: Exception) {
            false
        }
    }
}