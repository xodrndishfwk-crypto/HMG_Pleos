package com.example.starlog2.starlog.melody

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MelodyGeneratorTest {
    private val cassiopeia = listOf(
        MelodyStar("a", 0.0, 20.0, 0),
        MelodyStar("b", 25.0, 0.0, 1),
        MelodyStar("c", 50.0, 18.0, 2),
        MelodyStar("d", 75.0, -8.0, 3),
        MelodyStar("e", 100.0, 12.0, 4),
    )

    @Test
    fun rotate_90And360_preservesEveryPairwiseDistanceAndOriginalInput() {
        val original = cassiopeia.map { it.copy() }
        val rotated90 = MelodyGeometry.rotate(cassiopeia, 90)
        val rotated360 = MelodyGeometry.rotate(cassiopeia, 360)
        for (left in cassiopeia.indices) {
            for (right in left + 1 until cassiopeia.size) {
                val before = MelodyGeometry.squaredDistance(
                    Point2(cassiopeia[left].originalX, cassiopeia[left].originalY),
                    Point2(cassiopeia[right].originalX, cassiopeia[right].originalY),
                )
                val after = MelodyGeometry.squaredDistance(
                    rotated90.getValue(cassiopeia[left].starId),
                    rotated90.getValue(cassiopeia[right].starId),
                )
                assertEquals(before, after, 1e-8)
            }
        }
        cassiopeia.forEach { star ->
            assertEquals(star.originalX, rotated360.getValue(star.starId).x, 1e-8)
            assertEquals(star.originalY, rotated360.getValue(star.starId).y, 1e-8)
        }
        assertEquals(original, cassiopeia)
    }

    @Test
    fun preview_mapsVisualTopToHigherPitch_andCorrectsOctavesWithoutChangingPitchClass() {
        val stars = listOf(
            MelodyStar("top", 0.0, 0.0, 0),
            MelodyStar("middle", 10.0, 50.0, 1),
            MelodyStar("bottom", 20.0, 100.0, 2),
        )
        val melody = MelodyGenerator().preview("c", stars, 0)
        val byId = melody.notes.associateBy { it.starId }
        assertTrue(byId.getValue("top").originalMidi > byId.getValue("bottom").originalMidi)
        assertTrue(melody.notes.all { it.correctedMidi in 60..84 })
        assertTrue(melody.notes.all { Math.floorMod(it.originalMidi, 12) == Math.floorMod(it.correctedMidi, 12) })
        assertTrue(melody.notes.any { it.originalMidi != it.correctedMidi })
    }

    @Test
    fun chordGrouping_usesCompleteLinkAndDoesNotChainDistantStars() {
        val config = MelodyConfig(chordThresholdRatio = 0.15)
        val stars = listOf(
            MelodyStar("a", 0.0, 0.00, 0),
            MelodyStar("b", 1.0, 0.01, 1),
            MelodyStar("c", 2.0, 0.02, 2),
            MelodyStar("outlier", 3.0, 1.00, 3),
        )
        val melody = MelodyGenerator(config).preview("c", stars, 0)
        assertEquals(1, melody.chords.size)
        assertEquals(listOf("a", "b"), melody.chords.single().starIds)
        assertTrue(melody.notes.first { it.starId == "c" }.chordGroupId == null)
    }

    @Test
    fun chordGrouping_includesExactBoundaryAndSameHeight_excludesAboveBoundaryAndHonorsOff() {
        val boundaryStars = listOf(
            MelodyStar("a", 0.0, 0.00, 0),
            MelodyStar("b", 1.0, 0.01, 1),
            MelodyStar("outlier", 2.0, 1.00, 2),
        )
        // Default C-major mapping has 28 intervals. A 0.01/1.00 Y gap normalizes to
        // 0.01 * 28 * (staffSpacing / 2) = 6.72, exactly 14% of staffSpacing.
        val exactBoundary = MelodyGenerator(MelodyConfig(chordThresholdRatio = 0.14))
            .preview("boundary", boundaryStars, 0)
        assertEquals(listOf("a", "b"), exactBoundary.chords.single().starIds)

        val aboveBoundary = MelodyGenerator(MelodyConfig(chordThresholdRatio = 0.139))
            .preview("above", boundaryStars, 0)
        assertTrue(aboveBoundary.chords.isEmpty())

        val sameHeightStars = boundaryStars.toMutableList().apply {
            this[1] = this[1].copy(originalY = 0.0)
        }
        val sameHeight = MelodyGenerator(MelodyConfig(chordThresholdRatio = 0.0))
            .preview("same-height", sameHeightStars, 0)
        assertEquals(listOf("a", "b"), sameHeight.chords.single().starIds)

        val disabled = MelodyGenerator(
            MelodyConfig(chordThresholdRatio = 1.0, chordGenerationEnabled = false),
        ).preview("disabled", sameHeightStars, 0)
        assertTrue(disabled.chords.isEmpty())
    }

    @Test
    fun generate_sameInputAndCollectionVersion_isDeterministic() {
        val generator = MelodyGenerator()
        val first = generator.generate("cassiopeia", cassiopeia, comparisonCollectionVersion = "v3")
        val second = generator.generate("cassiopeia", cassiopeia, comparisonCollectionVersion = "v3")
        assertEquals(MelodyGenerationStatus.SUCCESS, first.status)
        assertEquals(first.melody, second.melody)
        assertTrue(first.evaluatedCandidateCount >= 72)
    }

    @Test
    fun generate_configurationThatChangesMusicalIdentity_changesStableIdAndPreservesStarMetadata() {
        val metadataStars = cassiopeia.mapIndexed { index, star ->
            star.copy(createdAtMs = 1_700_000_000_000L + index, dwellTimeMs = 10_000L + index)
        }
        val cMajor = MelodyGenerator(MelodyConfig(keySignature = MelodyKey.C_MAJOR)).generate("identity", metadataStars).melody!!
        val gMajor = MelodyGenerator(MelodyConfig(keySignature = MelodyKey.G_MAJOR)).generate("identity", metadataStars).melody!!

        assertNotEquals(cMajor.id, gMajor.id)
        assertEquals(MelodyKey.G_MAJOR, gMajor.keySignature)
        assertEquals(metadataStars.map { it.createdAtMs }, gMajor.notes.sortedBy { it.connectionOrder }.map { it.createdAtMs })
        assertEquals(metadataStars.map { it.dwellTimeMs }, gMajor.notes.sortedBy { it.connectionOrder }.map { it.dwellTimeMs })
    }

    @Test
    fun generate_oneStar_returnsInsufficientDataWithoutThrowing() {
        val result = MelodyGenerator().generate("tiny", cassiopeia.take(1))
        assertEquals(MelodyGenerationStatus.INSUFFICIENT_DATA, result.status)
        assertEquals(null, result.melody)
        assertEquals(0, result.evaluatedCandidateCount)
    }

    @Test
    fun generate_againstIdenticalExistingMelody_returnsBestEffortAndClosestId() {
        val generator = MelodyGenerator(MelodyConfig(maxAllowedSimilarity = 0.20))
        val original = generator.generate("original", cassiopeia).melody!!
        val result = generator.generate("copy", cassiopeia, listOf(original), "one")
        assertEquals(MelodyGenerationStatus.BEST_EFFORT, result.status)
        assertEquals(original.id, result.melody?.mostSimilarMelodyId)
        assertTrue(result.melody!!.maxSimilarity > 0.20)
    }

    @Test
    fun similarity_isTranspositionAndOctaveInvariantForSameContour() {
        val base = MelodyGenerator().preview("base", cassiopeia, 0)
        val transposed = base.copy(
            id = "transposed",
            notes = base.notes.map { it.copy(correctedMidi = it.correctedMidi + 12, originalMidi = it.originalMidi + 12) },
        )
        val score = MelodySimilarity.compare(base, transposed)
        assertEquals(1.0, score.intervalProgression, 1e-9)
        assertEquals(1.0, score.pitchSequence, 1e-9)
        assertEquals(1.0, score.total, 1e-9)
    }

    @Test
    fun arrangementPrompt_keepsConfirmedMelodyAndRecordsPreferences() {
        val melody = MelodyGenerator().preview("base", cassiopeia, 15)
        val request = ArrangementPromptBuilder.build(
            melody,
            ArrangementPreferences(
                genre = ArrangementGenre.JAZZ,
                rhythm = ArrangementRhythm.SWING,
                instruments = listOf(ArrangementInstrument.PIANO, ArrangementInstrument.STRINGS),
                tempoBpm = 96,
            ),
        )
        assertEquals(melody.id, request.melodyId)
        assertEquals(melody.notes.size, request.noteMidi.size)
        assertEquals(melody.notes.size, request.noteStartBeats.size)
        assertEquals(melody.chords.size, request.chordMidiGroups.size)
        assertTrue(request.prompt.contains("jazz"))
        assertTrue(request.prompt.contains("Preserve this lead melody exactly"))
        assertNotNull(request.preferences)
    }
}
