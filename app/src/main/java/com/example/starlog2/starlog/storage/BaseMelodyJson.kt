package com.example.starlog2.starlog.storage

import com.example.starlog2.starlog.melody.BaseMelody
import com.example.starlog2.starlog.melody.MelodyChord
import com.example.starlog2.starlog.melody.MelodyGenerationStatus
import com.example.starlog2.starlog.melody.MelodyKey
import com.example.starlog2.starlog.melody.MelodyNote
import com.example.starlog2.starlog.melody.Point2
import com.example.starlog2.starlog.melody.SimilarityBreakdown
import org.json.JSONArray
import org.json.JSONObject

internal object BaseMelodyJson {
    fun encode(value: BaseMelody): String = JSONObject().apply {
        put("id", value.id)
        put("constellationId", value.constellationId)
        put("rotationAngleDeg", value.rotationAngleDeg)
        put("tempoBpm", value.tempoBpm)
        put("beatsPerBar", value.beatsPerBar)
        put("beatUnit", value.beatUnit)
        put("maxSimilarity", value.maxSimilarity)
        put("mostSimilarMelodyId", value.mostSimilarMelodyId ?: JSONObject.NULL)
        put("naturalnessScore", value.naturalnessScore)
        put("totalCost", value.totalCost)
        put("generationStatus", value.generationStatus.name)
        put("algorithmVersion", value.algorithmVersion)
        put("keySignature", value.keySignature.name)
        put("similarity", similarity(value.similarityBreakdown))
        put("notes", JSONArray().apply { value.notes.forEach { put(note(it)) } })
        put("chords", JSONArray().apply { value.chords.forEach { put(chord(it)) } })
    }.toString()

    fun decode(raw: String): BaseMelody {
        val json = JSONObject(raw)
        val notesJson = json.getJSONArray("notes")
        val chordsJson = json.getJSONArray("chords")
        val notes = (0 until notesJson.length()).map { decodeNote(notesJson.getJSONObject(it)) }
        val chords = (0 until chordsJson.length()).map { decodeChord(chordsJson.getJSONObject(it)) }
        val score = json.getJSONObject("similarity")
        return BaseMelody(
            id = json.getString("id"),
            constellationId = json.getString("constellationId"),
            rotationAngleDeg = json.getInt("rotationAngleDeg"),
            notes = notes,
            chords = chords,
            tempoBpm = json.getInt("tempoBpm"),
            beatsPerBar = json.getInt("beatsPerBar"),
            beatUnit = json.getInt("beatUnit"),
            maxSimilarity = json.getDouble("maxSimilarity"),
            mostSimilarMelodyId = if (json.isNull("mostSimilarMelodyId")) null else json.getString("mostSimilarMelodyId"),
            similarityBreakdown = SimilarityBreakdown(
                score.getDouble("intervalProgression"),
                score.getDouble("pitchSequence"),
                score.getDouble("rhythm"),
                score.getDouble("harmony"),
            ),
            naturalnessScore = json.getDouble("naturalnessScore"),
            totalCost = json.getDouble("totalCost"),
            generationStatus = MelodyGenerationStatus.valueOf(json.getString("generationStatus")),
            algorithmVersion = json.getString("algorithmVersion"),
            keySignature = MelodyKey.valueOf(json.getString("keySignature")),
        )
    }

    private fun similarity(value: SimilarityBreakdown) = JSONObject().apply {
        put("intervalProgression", value.intervalProgression)
        put("pitchSequence", value.pitchSequence)
        put("rhythm", value.rhythm)
        put("harmony", value.harmony)
    }

    private fun point(value: Point2) = JSONObject().apply {
        put("x", value.x)
        put("y", value.y)
    }

    private fun note(value: MelodyNote) = JSONObject().apply {
        put("starId", value.starId)
        put("connectionOrder", value.connectionOrder)
        put("originalPoint", point(value.originalPoint))
        put("rotatedPoint", point(value.rotatedPoint))
        put("originalMidi", value.originalMidi)
        put("correctedMidi", value.correctedMidi)
        put("startBeat", value.startBeat)
        put("durationBeats", value.durationBeats)
        put("chordGroupId", value.chordGroupId ?: JSONObject.NULL)
        put("createdAtMs", value.createdAtMs ?: JSONObject.NULL)
        put("dwellTimeMs", value.dwellTimeMs ?: JSONObject.NULL)
    }

    private fun decodeNote(json: JSONObject) = MelodyNote(
        starId = json.getString("starId"),
        connectionOrder = json.getInt("connectionOrder"),
        originalPoint = decodePoint(json.getJSONObject("originalPoint")),
        rotatedPoint = decodePoint(json.getJSONObject("rotatedPoint")),
        originalMidi = json.getInt("originalMidi"),
        correctedMidi = json.getInt("correctedMidi"),
        startBeat = json.getDouble("startBeat"),
        durationBeats = json.getDouble("durationBeats"),
        chordGroupId = if (json.isNull("chordGroupId")) null else json.getString("chordGroupId"),
        createdAtMs = if (json.isNull("createdAtMs")) null else json.getLong("createdAtMs"),
        dwellTimeMs = if (json.isNull("dwellTimeMs")) null else json.getLong("dwellTimeMs"),
    )

    private fun chord(value: MelodyChord) = JSONObject().apply {
        put("id", value.id)
        put("starIds", JSONArray(value.starIds))
        put("startBeat", value.startBeat)
    }

    private fun decodeChord(json: JSONObject): MelodyChord {
        val ids = json.getJSONArray("starIds")
        return MelodyChord(json.getString("id"), (0 until ids.length()).map { ids.getString(it) }, json.getDouble("startBeat"))
    }

    private fun decodePoint(json: JSONObject) = Point2(json.getDouble("x"), json.getDouble("y"))
}
