package com.example.starlog2.starlog.melody

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** Small offline sine/bell preview. It is intentionally separate from external arrangement providers. */
class MelodyPreviewPlayer : AutoCloseable {
    private var track: AudioTrack? = null

    fun play(melody: BaseMelody, shouldContinue: () -> Boolean = { true }) {
        if (!shouldContinue()) return
        val samplesPerBeat = (SAMPLE_RATE * 60.0 / melody.tempoBpm).toInt()
        val endBeat = melody.notes.maxOfOrNull { it.startBeat + it.durationBeats } ?: return
        val mix = DoubleArray((endBeat * samplesPerBeat).toInt().coerceAtLeast(1))
        melody.notes.forEach { note ->
            if (!shouldContinue()) return
            val start = (note.startBeat * samplesPerBeat).toInt()
            val length = (note.durationBeats * samplesPerBeat).toInt()
            val frequency = 440.0 * 2.0.pow((note.correctedMidi - 69) / 12.0)
            for (offset in 0 until length) {
                if (offset % CANCELLATION_CHECK_SAMPLES == 0 && !shouldContinue()) return
                val index = start + offset
                if (index >= mix.size) break
                val progress = offset.toDouble() / length.coerceAtLeast(1)
                val envelope = (1.0 - progress).pow(1.8)
                val time = offset.toDouble() / SAMPLE_RATE
                mix[index] += (sin(2.0 * PI * frequency * time) + 0.28 * sin(4.0 * PI * frequency * time)) * envelope
            }
        }
        if (!shouldContinue()) return
        val peak = mix.maxOfOrNull { abs(it) }?.coerceAtLeast(1.0) ?: 1.0
        val pcm = ShortArray(mix.size) { (mix[it] / peak * Short.MAX_VALUE * 0.72).toInt().toShort() }
        if (!shouldContinue()) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audioTrack.write(pcm, 0, pcm.size)
        synchronized(this) {
            if (!shouldContinue()) {
                audioTrack.release()
                return
            }
            releaseTrackLocked()
            audioTrack.play()
            track = audioTrack
        }
    }

    @Synchronized
    override fun close() {
        releaseTrackLocked()
    }

    private fun releaseTrackLocked() {
        track?.let { current ->
            try { current.stop() } catch (_: IllegalStateException) { }
            current.release()
        }
        track = null
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
        const val CANCELLATION_CHECK_SAMPLES = 2_048
    }
}
