package com.oblutack.timenote

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.oblutack.timenote.feature_timer.domain.AudioPlayer

class AndroidAudioPlayer(private val context: Context) : AudioPlayer {

    private var player: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    // 1. REQUEST FOCUS
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) // Pauses Spotify!
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // Tells Android this is important talking
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { /* Handle focus changes if needed */ }
                .build()
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    // 2. ABANDON FOCUS
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    override fun play(filePath: String, onComplete: () -> Unit) {
        if (player != null) {
            try {
                if (!player!!.isPlaying) {
                    requestAudioFocus() // Re-grab focus if resuming
                    player?.start()
                    return
                }
            } catch (e: Exception) { }
        }

        safeRelease()

        try {
            requestAudioFocus() // Grab focus before playing!

            player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()

                setOnCompletionListener {
                    safeRelease()
                    abandonAudioFocus() // Give Spotify back its music!
                    onComplete()
                }

                setOnErrorListener { _, _, _ ->
                    safeRelease()
                    abandonAudioFocus()
                    onComplete()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            safeRelease()
            abandonAudioFocus()
            onComplete()
        }
    }

    override fun pause() {
        try {
            if (player?.isPlaying == true) {
                player?.pause()
                abandonAudioFocus() // Let music play while we are paused
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stop() {
        safeRelease()
        abandonAudioFocus()
    }

    private fun safeRelease() {
        try { player?.stop() } catch (e: Exception) { }
        try { player?.release() } catch (e: Exception) { }
        player = null
    }

    override fun isPlaying(): Boolean {
        return try { player?.isPlaying ?: false } catch (e: Exception) { false }
    }
}