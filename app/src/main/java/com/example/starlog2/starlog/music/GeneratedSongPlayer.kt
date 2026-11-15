package com.example.starlog2.starlog.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File

class GeneratedSongPlayer(
    context: Context,
    private val stateChanged: (State) -> Unit = {},
) : AutoCloseable {
    enum class State { PREPARING, PLAYING, STOPPED, ERROR }

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change < 0) close()
        }
        .build()
    private var player: MediaPlayer? = null

    @Synchronized
    fun play(file: File) {
        require(file.isFile && file.length() > 0L) { "Generated song file is missing" }
        releaseLocked()
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stateChanged(State.ERROR)
            return
        }
        stateChanged(State.PREPARING)
        val mediaPlayer = try {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(file.absolutePath)
                setOnPreparedListener { prepared ->
                    synchronized(this@GeneratedSongPlayer) {
                        if (player === prepared) {
                            prepared.start()
                            stateChanged(State.PLAYING)
                        }
                    }
                }
                setOnCompletionListener { close() }
                setOnErrorListener { _, _, _ ->
                    close()
                    stateChanged(State.ERROR)
                    true
                }
            }
        } catch (_: Exception) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            stateChanged(State.ERROR)
            return
        }
        player = mediaPlayer
        mediaPlayer.prepareAsync()
    }

    @Synchronized
    override fun close() {
        releaseLocked()
        audioManager.abandonAudioFocusRequest(focusRequest)
        stateChanged(State.STOPPED)
    }

    private fun releaseLocked() {
        player?.let { current ->
            runCatching { current.stop() }
            current.reset()
            current.release()
        }
        player = null
    }
}
