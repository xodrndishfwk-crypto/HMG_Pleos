package com.example.starlog2.starlog.melody

object ArrangementPromptBuilder {
    fun build(melody: BaseMelody, preferences: ArrangementPreferences): ArrangementRequest {
        require(preferences.instruments.isNotEmpty())
        require(preferences.tempoBpm > 0)
        val ordered = melody.notes.sortedWith(compareBy<MelodyNote> { it.startBeat }.thenBy { it.connectionOrder })
        val noteNames = ordered.joinToString(" ") { midiName(it.correctedMidi) }
        val durations = ordered.joinToString(" ") { "${it.durationBeats}beat" }
        val prompt = buildString {
            append("Instrumental ").append(preferences.genre.name.lowercase()).append(" arrangement. ")
            append("Use a ").append(preferences.rhythm.name.lowercase()).append(" rhythmic feel at ")
            append(preferences.tempoBpm).append(" BPM with ")
            append(preferences.instruments.joinToString(", ") { it.name.lowercase().replace('_', ' ') }).append(". ")
            append("Key: ").append(melody.keySignature.displayName).append(". ")
            append("Preserve this lead melody exactly in order: ").append(noteNames).append(". ")
            append("Durations: ").append(durations).append(". ")
            if (melody.chords.isNotEmpty()) {
                append("Simultaneous chord groups: ")
                append(melody.chords.joinToString("; ") { chord ->
                    val pitches = ordered.filter { it.chordGroupId == chord.id }.joinToString("+") { midiName(it.correctedMidi) }
                    "beat ${chord.startBeat}: $pitches"
                }).append(". ")
            }
            append("Do not replace, transpose, reorder, or omit the lead notes; accompaniment may expand around them.")
        }
        return ArrangementRequest(
            melodyId = melody.id,
            prompt = prompt,
            noteMidi = ordered.map { it.correctedMidi },
            noteDurations = ordered.map { it.durationBeats },
            noteStartBeats = ordered.map { it.startBeat },
            chordMidiGroups = melody.chords.map { chord ->
                ordered.filter { it.chordGroupId == chord.id }.map { it.correctedMidi }
            },
            preferences = preferences,
        )
    }

    fun midiName(midi: Int): String {
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[Math.floorMod(midi, 12)] + (midi / 12 - 1)
    }
}
