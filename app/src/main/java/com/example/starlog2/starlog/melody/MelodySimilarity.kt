package com.example.starlog2.starlog.melody

import kotlin.math.abs
import kotlin.math.max

object MelodySimilarity {
    fun compare(a: BaseMelody, b: BaseMelody): SimilarityBreakdown {
        val aNotes = a.notes.sortedWith(compareBy<MelodyNote> { it.connectionOrder }.thenBy { it.starId })
        val bNotes = b.notes.sortedWith(compareBy<MelodyNote> { it.connectionOrder }.thenBy { it.starId })
        val aPitches = aNotes.map { it.correctedMidi }
        val bPitches = bNotes.map { it.correctedMidi }
        val aIntervals = aPitches.zipWithNext { left, right -> right - left }
        val bIntervals = bPitches.zipWithNext { left, right -> right - left }

        // Relative pitches make whole-song transposition and octave shifts compare as the same contour.
        val aRelative = aPitches.firstOrNull()?.let { root -> aPitches.map { it - root } }.orEmpty()
        val bRelative = bPitches.firstOrNull()?.let { root -> bPitches.map { it - root } }.orEmpty()
        val aRhythm = rhythmTokens(a.notes.sortedWith(compareBy<MelodyNote> { it.startBeat }.thenBy { it.connectionOrder }))
        val bRhythm = rhythmTokens(b.notes.sortedWith(compareBy<MelodyNote> { it.startBeat }.thenBy { it.connectionOrder }))
        val aHarmony = harmonyTokens(a)
        val bHarmony = harmonyTokens(b)

        return SimilarityBreakdown(
            intervalProgression = sequenceSimilarity(aIntervals, bIntervals),
            pitchSequence = sequenceSimilarity(aRelative, bRelative),
            rhythm = sequenceSimilarity(aRhythm, bRhythm),
            harmony = when {
                aHarmony.isEmpty() && bHarmony.isEmpty() -> 1.0
                aHarmony.isEmpty() || bHarmony.isEmpty() -> 0.0
                else -> sequenceSimilarity(aHarmony, bHarmony)
            },
        )
    }

    private fun rhythmTokens(notes: List<MelodyNote>): List<Int> {
        if (notes.isEmpty()) return emptyList()
        return notes.flatMapIndexed { index, note ->
            val next = notes.getOrNull(index + 1)
            listOf(
                (note.durationBeats * 100.0).toInt(),
                (((next?.startBeat ?: note.startBeat) - note.startBeat) * 100.0).toInt(),
            )
        }
    }

    private fun harmonyTokens(melody: BaseMelody): List<Int> = melody.chords
        .sortedBy { it.startBeat }
        .flatMap { chord ->
            val pitches = melody.notes.filter { it.chordGroupId == chord.id }.map { it.correctedMidi }.sorted()
            listOf(pitches.size) + pitches.drop(1).map { it - pitches.first() }
        }

    internal fun <T> sequenceSimilarity(a: List<T>, b: List<T>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val denominator = max(a.size, b.size)
        if (denominator == 0) return 1.0
        val previous = IntArray(b.size + 1) { it }
        val current = IntArray(b.size + 1)
        for (i in 1..a.size) {
            current[0] = i
            for (j in 1..b.size) {
                val substitution = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + substitution)
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return (1.0 - previous[b.size].toDouble() / denominator).coerceIn(0.0, 1.0)
    }
}
