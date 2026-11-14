package com.example.starlog2.starlog.melody

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt

class MelodyGenerator(private val config: MelodyConfig = MelodyConfig()) {
    private data class Draft(
        val angle: Int,
        val notes: List<MelodyNote>,
        val chords: List<MelodyChord>,
        val octaveCorrectionCount: Int,
    )

    fun generate(
        constellationId: String,
        stars: List<MelodyStar>,
        existingMelodies: List<BaseMelody> = emptyList(),
        comparisonCollectionVersion: String = "0",
    ): MelodyGenerationResult {
        val ordered = stars.sortedWith(compareBy<MelodyStar> { it.connectionOrder }.thenBy { it.starId })
        if (ordered.size < 2 || ordered.map { it.starId }.distinct().size != ordered.size) {
            return MelodyGenerationResult(
                status = MelodyGenerationStatus.INSUFFICIENT_DATA,
                melody = null,
                evaluatedCandidateCount = 0,
                message = "At least two uniquely identified stars are required.",
            )
        }

        return try {
            val evaluated = linkedMapOf<Int, BaseMelody>()
            fun evaluate(angle: Int): BaseMelody = evaluated.getOrPut(Math.floorMod(angle, 360)) {
                scoreDraft(
                    constellationId = constellationId,
                    draft = buildDraft(ordered, Math.floorMod(angle, 360)),
                    existing = existingMelodies,
                    comparisonCollectionVersion = comparisonCollectionVersion,
                )
            }

            for (angle in 0 until 360 step config.coarseRotationStepDeg) evaluate(angle)
            val coarseBest = evaluated.values.minWith(candidateComparator())
            val radius = config.coarseRotationStepDeg - config.fineRotationStepDeg
            for (delta in -radius..radius step config.fineRotationStepDeg) evaluate(coarseBest.rotationAngleDeg + delta)
            val selected = evaluated.values.minWith(candidateComparator())
            val status = if (selected.maxSimilarity <= config.maxAllowedSimilarity) {
                MelodyGenerationStatus.SUCCESS
            } else {
                MelodyGenerationStatus.BEST_EFFORT
            }
            MelodyGenerationResult(
                status = status,
                melody = selected.copy(generationStatus = status),
                evaluatedCandidateCount = evaluated.size,
            )
        } catch (error: Exception) {
            MelodyGenerationResult(
                status = MelodyGenerationStatus.GENERATION_FAILED,
                melody = null,
                evaluatedCandidateCount = 0,
                message = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    /** Used by the manual-angle preview. This never mutates or replaces a confirmed melody. */
    fun preview(constellationId: String, stars: List<MelodyStar>, angleDeg: Int): BaseMelody {
        require(stars.size >= 2)
        return scoreDraft(constellationId, buildDraft(stars.sortedBy { it.connectionOrder }, angleDeg), emptyList(), "preview")
    }

    private fun buildDraft(stars: List<MelodyStar>, angle: Int): Draft {
        val rotated = MelodyGeometry.rotate(stars, angle)
        val minY = rotated.values.minOf { it.y }
        val maxY = rotated.values.maxOf { it.y }
        val scale = scalePitches(config.mappingMinMidi, config.mappingMaxMidi, config.keySignature)
        val ySpan = (maxY - minY).takeIf { it > 1e-9 }
        val normalizedY = stars.associate { star ->
            val point = rotated.getValue(star.starId)
            val position = if (ySpan == null) (scale.size - 1) / 2.0 else (point.y - minY) / ySpan * (scale.size - 1)
            star.starId to position * (config.staffSpacing / 2.0)
        }
        val groups = if (config.chordGenerationEnabled) chordGroups(stars, normalizedY) else emptyList()
        val groupByStar = groups.flatMap { group -> group.map { it.starId to group } }.toMap()

        var beat = 0.0
        val startByStar = mutableMapOf<String, Double>()
        val emittedGroups = mutableSetOf<String>()
        for (star in stars) {
            val group = groupByStar[star.starId]
            val groupId = group?.let { chordId(it) }
            if (groupId == null) {
                startByStar[star.starId] = beat
                beat += config.noteDurationBeats
            } else if (emittedGroups.add(groupId)) {
                group.forEach { startByStar[it.starId] = beat }
                beat += config.noteDurationBeats
            }
        }

        val originalMidi = stars.map { star ->
            val index = ((normalizedY.getValue(star.starId) / (config.staffSpacing / 2.0)).roundToInt())
                .coerceIn(0, scale.lastIndex)
            // A smaller screen y is higher and therefore maps to a higher staff pitch.
            scale[scale.lastIndex - index]
        }
        val corrected = if (config.octaveCorrectionEnabled) correctOctaves(originalMidi) else originalMidi
        val finalStart = startByStar.values.maxOrNull() ?: 0.0
        val notes = stars.mapIndexed { index, star ->
            val group = groupByStar[star.starId]
            MelodyNote(
                starId = star.starId,
                connectionOrder = star.connectionOrder,
                originalPoint = Point2(star.originalX, star.originalY),
                rotatedPoint = rotated.getValue(star.starId),
                originalMidi = originalMidi[index],
                correctedMidi = corrected[index],
                startBeat = startByStar.getValue(star.starId),
                durationBeats = if (startByStar.getValue(star.starId) == finalStart) {
                    config.finalNoteDurationBeats
                } else {
                    config.noteDurationBeats
                },
                chordGroupId = group?.let(::chordId),
                createdAtMs = star.createdAtMs,
                dwellTimeMs = star.dwellTimeMs,
            )
        }
        val chords = groups.map { group ->
            val id = chordId(group)
            MelodyChord(id, group.sortedBy { it.connectionOrder }.map { it.starId }, startByStar.getValue(group.first().starId))
        }.sortedBy { it.startBeat }
        return Draft(angle, notes, chords, originalMidi.zip(corrected).count { (a, b) -> a != b })
    }

    /** Complete-link grouping: every pair in a group remains inside the threshold; no chain-merging. */
    private fun chordGroups(stars: List<MelodyStar>, normalizedY: Map<String, Double>): List<List<MelodyStar>> {
        val threshold = config.staffSpacing * config.chordThresholdRatio
        val sorted = stars.sortedWith(compareBy<MelodyStar> { normalizedY.getValue(it.starId) }.thenBy { it.connectionOrder })
        val result = mutableListOf<List<MelodyStar>>()
        var index = 0
        while (index < sorted.size) {
            val group = mutableListOf(sorted[index])
            val minimum = normalizedY.getValue(sorted[index].starId)
            var cursor = index + 1
            while (cursor < sorted.size && normalizedY.getValue(sorted[cursor].starId) - minimum <= threshold + 1e-9) {
                group += sorted[cursor]
                cursor++
            }
            if (group.size > 1) result += group
            index = cursor
        }
        return result
    }

    private fun correctOctaves(pitches: List<Int>): List<Int> {
        if (pitches.isEmpty()) return emptyList()
        val candidates = pitches.map { pitch ->
            (36..108).filter { Math.floorMod(it, 12) == Math.floorMod(pitch, 12) }
        }
        val costs = candidates.map { DoubleArray(it.size) { Double.POSITIVE_INFINITY } }
        val previous = candidates.map { IntArray(it.size) { -1 } }
        for (j in candidates[0].indices) costs[0][j] = localPitchCost(candidates[0][j], pitches[0])
        for (i in 1 until candidates.size) {
            for (j in candidates[i].indices) {
                val current = candidates[i][j]
                for (k in candidates[i - 1].indices) {
                    val leap = abs(current - candidates[i - 1][k])
                    val leapCost = (leap / 12.0) * config.octaveLeapWeight + if (leap > 12) (leap - 12) / 6.0 else 0.0
                    val total = costs[i - 1][k] + leapCost + localPitchCost(current, pitches[i])
                    if (total < costs[i][j] - 1e-12) {
                        costs[i][j] = total
                        previous[i][j] = k
                    }
                }
            }
        }
        var cursor = costs.last().indices.minWith(compareBy<Int> { costs.last()[it] }.thenBy { candidates.last()[it] })
        val result = IntArray(pitches.size)
        for (i in pitches.lastIndex downTo 0) {
            result[i] = candidates[i][cursor]
            cursor = previous[i][cursor].takeIf { it >= 0 } ?: 0
        }
        return result.toList()
    }

    private fun localPitchCost(candidate: Int, original: Int): Double {
        val outside = when {
            candidate < config.preferredMinMidi -> config.preferredMinMidi - candidate
            candidate > config.preferredMaxMidi -> candidate - config.preferredMaxMidi
            else -> 0
        }
        return abs(candidate - original) / 12.0 * config.octaveShiftWeight + outside / 12.0 * config.pitchRangeWeight
    }

    private fun scoreDraft(
        constellationId: String,
        draft: Draft,
        existing: List<BaseMelody>,
        comparisonCollectionVersion: String,
    ): BaseMelody {
        val provisional = BaseMelody(
            id = stableId(constellationId, draft, comparisonCollectionVersion),
            constellationId = constellationId,
            rotationAngleDeg = draft.angle,
            notes = draft.notes,
            chords = draft.chords,
            tempoBpm = config.tempoBpm,
            beatsPerBar = config.beatsPerBar,
            beatUnit = config.beatUnit,
            maxSimilarity = 0.0,
            mostSimilarMelodyId = null,
            similarityBreakdown = SimilarityBreakdown(0.0, 0.0, 0.0, 0.0),
            naturalnessScore = 0.0,
            totalCost = 0.0,
            generationStatus = MelodyGenerationStatus.SUCCESS,
            algorithmVersion = config.algorithmVersion,
            keySignature = config.keySignature,
        )
        val closest = existing.map { it to MelodySimilarity.compare(provisional, it) }
            .maxWithOrNull(
                compareBy<Pair<BaseMelody, SimilarityBreakdown>> { it.second.total }
                    .thenByDescending { it.first.id },
            )
        val similarity = closest?.second ?: SimilarityBreakdown(0.0, 0.0, 0.0, 0.0)
        val pitches = draft.notes.sortedBy { it.connectionOrder }.map { it.correctedMidi }
        val leaps = pitches.zipWithNext { a, b -> abs(b - a) }
        val leapPenalty = if (leaps.isEmpty()) 0.0 else leaps.sumOf { (it / 24.0).coerceAtMost(1.0) } / leaps.size
        val rangePenalty = pitches.count { it !in config.preferredMinMidi..config.preferredMaxMidi }.toDouble() / pitches.size
        val repetitionPenalty = if (pitches.size < 2) {
            0.0
        } else {
            pitches.zipWithNext().count { it.first == it.second }.toDouble() / (pitches.size - 1)
        }
        val harmonyPenalty = draft.chords.sumOf { chord ->
            val chordPitches = draft.notes.filter { it.chordGroupId == chord.id }.map { it.correctedMidi }
            if (chordPitches.distinct().size == 1) 1.0 else 0.0
        } / draft.chords.size.coerceAtLeast(1).toDouble()
        val octavePenalty = draft.octaveCorrectionCount.toDouble() / draft.notes.size
        val totalCost = config.similarityWeight * similarity.total +
            config.leapWeight * leapPenalty + config.octaveWeight * octavePenalty +
            config.rangeWeight * rangePenalty + config.repetitionWeight * repetitionPenalty +
            config.harmonyWeight * harmonyPenalty
        val naturalness = (1.0 - (leapPenalty + octavePenalty + rangePenalty + repetitionPenalty + harmonyPenalty) / 5.0)
            .coerceIn(0.0, 1.0)
        return provisional.copy(
            maxSimilarity = similarity.total,
            mostSimilarMelodyId = closest?.first?.id,
            similarityBreakdown = similarity,
            naturalnessScore = naturalness,
            totalCost = totalCost,
        )
    }

    private fun candidateComparator() = compareBy<BaseMelody> {
        if (it.maxSimilarity <= config.maxAllowedSimilarity) 0 else 1
    }.thenBy { it.totalCost }.thenBy { it.rotationAngleDeg }

    private fun chordId(group: List<MelodyStar>): String = "chord-" + group.map { it.starId }.sorted().joinToString("-")

    private fun scalePitches(min: Int, max: Int, key: MelodyKey): List<Int> {
        val pitchClasses = listOf(0, 2, 4, 5, 7, 9, 11)
            .map { Math.floorMod(it + key.rootPitchClass, 12) }
            .toSet()
        return (min..max).filter { Math.floorMod(it, 12) in pitchClasses }.ifEmpty { listOf(min) }
    }

    private fun stableId(constellationId: String, draft: Draft, collectionVersion: String): String {
        val input = buildString {
            append(config.algorithmVersion).append('|').append(collectionVersion).append('|').append(constellationId)
            append('|').append(draft.angle)
            append('|').append(config.keySignature.name)
            append('|').append(config.tempoBpm).append(':').append(config.beatsPerBar).append('/').append(config.beatUnit)
            append('|').append(config.chordGenerationEnabled).append(':').append(config.chordThresholdRatio)
            append('|').append(config.octaveCorrectionEnabled)
                .append(':').append(config.preferredMinMidi)
                .append('-').append(config.preferredMaxMidi)
            draft.notes.sortedBy { it.connectionOrder }.forEach {
                append('|').append(it.starId).append(':').append(it.originalPoint.x).append(',').append(it.originalPoint.y)
                    .append(':').append(it.connectionOrder).append(':').append(it.originalMidi).append('>').append(it.correctedMidi)
                    .append('@').append(it.startBeat).append('+').append(it.durationBeats).append(':').append(it.chordGroupId)
                    .append(':').append(it.createdAtMs).append(':').append(it.dwellTimeMs)
            }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        return "melody-$hash"
    }
}
