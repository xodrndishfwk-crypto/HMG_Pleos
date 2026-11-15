package com.example.starlog2.starlog.music

import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.ArrangementPreferences
import com.example.starlog2.starlog.melody.ArrangementPromptBuilder
import com.example.starlog2.starlog.melody.BaseMelody
import java.security.MessageDigest

object AiMusicRequestFactory {
    fun create(
        melody: BaseMelody,
        sourceMode: SourceMode,
        preferences: ArrangementPreferences,
        createdAtMs: Long,
    ): AiMusicRequest {
        val arrangement = ArrangementPromptBuilder.build(melody, preferences)
        val identity = buildString {
            append("starlog-ai-music-request-1|")
            append(melody.constellationId).append('|')
            append(sourceMode.name).append('|')
            append(melody.id).append('|')
            append(preferences.genre.name).append('|')
            append(preferences.rhythm.name).append('|')
            append(preferences.instruments.joinToString(",") { it.name }).append('|')
            append(preferences.tempoBpm).append('|')
            append(arrangement.prompt)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return AiMusicRequest(
            requestId = "music-$digest",
            constellationId = melody.constellationId,
            sourceMode = sourceMode,
            baseMelodyId = melody.id,
            createdAtMs = createdAtMs,
            prompt = arrangement.prompt,
            noteMidi = arrangement.noteMidi,
            noteStartBeats = arrangement.noteStartBeats,
            noteDurations = arrangement.noteDurations,
            chordMidiGroups = arrangement.chordMidiGroups,
            preferences = preferences,
        )
    }
}
