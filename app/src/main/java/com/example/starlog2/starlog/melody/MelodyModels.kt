package com.example.starlog2.starlog.melody

/** Immutable input. Coordinates are display coordinates: smaller y values are visually higher. */
data class MelodyStar(
    val starId: String,
    val originalX: Double,
    val originalY: Double,
    val connectionOrder: Int,
    val createdAtMs: Long? = null,
    val dwellTimeMs: Long? = null,
)

data class Point2(val x: Double, val y: Double)

enum class MelodyKey(val rootPitchClass: Int, val displayName: String) {
    C_MAJOR(0, "C Major"),
    D_MAJOR(2, "D Major"),
    E_MAJOR(4, "E Major"),
    F_MAJOR(5, "F Major"),
    G_MAJOR(7, "G Major"),
    A_MAJOR(9, "A Major"),
    B_MAJOR(11, "B Major"),
}

data class MelodyConfig(
    val coarseRotationStepDeg: Int = 5,
    val fineRotationStepDeg: Int = 1,
    val staffSpacing: Double = 48.0,
    val chordThresholdRatio: Double = 0.25,
    val chordGenerationEnabled: Boolean = true,
    val octaveCorrectionEnabled: Boolean = true,
    val mappingMinMidi: Int = 48,
    val mappingMaxMidi: Int = 96,
    val preferredMinMidi: Int = 60,
    val preferredMaxMidi: Int = 84,
    val tempoBpm: Int = 88,
    val beatsPerBar: Int = 4,
    val beatUnit: Int = 4,
    val keySignature: MelodyKey = MelodyKey.C_MAJOR,
    val noteDurationBeats: Double = 1.0,
    val finalNoteDurationBeats: Double = 2.0,
    val maxAllowedSimilarity: Double = 0.65,
    val octaveLeapWeight: Double = 1.0,
    val octaveShiftWeight: Double = 0.16,
    val pitchRangeWeight: Double = 1.5,
    val similarityWeight: Double = 0.42,
    val leapWeight: Double = 0.23,
    val octaveWeight: Double = 0.12,
    val rangeWeight: Double = 0.10,
    val repetitionWeight: Double = 0.07,
    val harmonyWeight: Double = 0.06,
    val algorithmVersion: String = "starlog-melody-1.0.0",
) {
    init {
        require(coarseRotationStepDeg in 1..180)
        require(fineRotationStepDeg in 1..coarseRotationStepDeg)
        require(staffSpacing > 0.0)
        require(chordThresholdRatio >= 0.0)
        require(mappingMinMidi <= mappingMaxMidi)
        require(preferredMinMidi <= preferredMaxMidi)
        require(tempoBpm > 0)
        require(maxAllowedSimilarity in 0.0..1.0)
    }
}

data class MelodyNote(
    val starId: String,
    val connectionOrder: Int,
    val originalPoint: Point2,
    val rotatedPoint: Point2,
    val originalMidi: Int,
    val correctedMidi: Int,
    val startBeat: Double,
    val durationBeats: Double,
    val chordGroupId: String?,
    val createdAtMs: Long? = null,
    val dwellTimeMs: Long? = null,
)

data class MelodyChord(
    val id: String,
    val starIds: List<String>,
    val startBeat: Double,
)

data class SimilarityBreakdown(
    val intervalProgression: Double,
    val pitchSequence: Double,
    val rhythm: Double,
    val harmony: Double,
) {
    val total: Double
        get() = intervalProgression * 0.40 + pitchSequence * 0.25 + rhythm * 0.20 + harmony * 0.15
}

enum class MelodyGenerationStatus { SUCCESS, BEST_EFFORT, INSUFFICIENT_DATA, GENERATION_FAILED }

data class BaseMelody(
    val id: String,
    val constellationId: String,
    val rotationAngleDeg: Int,
    val notes: List<MelodyNote>,
    val chords: List<MelodyChord>,
    val tempoBpm: Int,
    val beatsPerBar: Int,
    val beatUnit: Int,
    val maxSimilarity: Double,
    val mostSimilarMelodyId: String?,
    val similarityBreakdown: SimilarityBreakdown,
    val naturalnessScore: Double,
    val totalCost: Double,
    val generationStatus: MelodyGenerationStatus,
    val algorithmVersion: String,
    val keySignature: MelodyKey = MelodyKey.C_MAJOR,
)

data class MelodyGenerationResult(
    val status: MelodyGenerationStatus,
    val melody: BaseMelody?,
    val evaluatedCandidateCount: Int,
    val message: String? = null,
)

enum class ArrangementGenre { AMBIENT, CLASSICAL, JAZZ, POP, ELECTRONIC }
enum class ArrangementRhythm { STRAIGHT, WALTZ, SWING, SYNCOPATED }
enum class ArrangementInstrument { MUSIC_BOX, PIANO, STRINGS, SYNTH, GUITAR }

data class ArrangementPreferences(
    val genre: ArrangementGenre,
    val rhythm: ArrangementRhythm,
    val instruments: List<ArrangementInstrument>,
    val tempoBpm: Int,
)

/** Provider-neutral payload. It does not claim that any external service was called. */
data class ArrangementRequest(
    val melodyId: String,
    val prompt: String,
    val noteMidi: List<Int>,
    val noteDurations: List<Double>,
    val noteStartBeats: List<Double>,
    val chordMidiGroups: List<List<Int>>,
    val preferences: ArrangementPreferences,
)
