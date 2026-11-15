package com.example.starlog2.starlog.music

import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.ArrangementGenre
import com.example.starlog2.starlog.melody.ArrangementInstrument
import com.example.starlog2.starlog.melody.ArrangementPreferences
import com.example.starlog2.starlog.melody.ArrangementRhythm
import org.json.JSONArray
import org.json.JSONObject

object AiMusicJson {
    fun encodeRequest(value: AiMusicRequest): String = request(value).toString()

    fun encodeJob(value: AiMusicJob): String = job(value).toString()

    fun decodeJob(raw: String): AiMusicJob = decodeJob(JSONObject(raw))

    fun decodeJob(json: JSONObject): AiMusicJob {
        val resultsJson = json.optJSONArray("results") ?: JSONArray()
        val results = (0 until resultsJson.length()).map { index ->
            val result = resultsJson.getJSONObject(index)
            AiMusicResult(
                id = result.getString("id"),
                audioUrl = result.nullableString("audioUrl"),
                localFilePath = result.nullableString("localFilePath"),
                mimeType = result.getString("mimeType"),
                durationMs = result.nullableLong("durationMs"),
                model = result.nullableString("model"),
                createdAtMs = result.getLong("createdAtMs"),
            )
        }
        val error = json.optJSONObject("error")
        return AiMusicJob(
            request = decodeRequest(json.getJSONObject("request")),
            provider = json.getString("provider"),
            providerJobId = json.nullableString("jobId"),
            status = AiMusicStatus.valueOf(json.getString("status")),
            results = results,
            selectedResultId = json.nullableString("selectedResultId"),
            errorCode = error?.nullableString("code")?.let(AiMusicErrorCode::valueOf),
            errorMessage = error?.nullableString("message"),
            retryable = error?.optBoolean("retryable", false) ?: false,
            retryAfterMs = error?.nullableLong("retryAfterMs"),
            updatedAtMs = json.getLong("updatedAtMs"),
        )
    }

    fun decodeFailure(raw: String, httpCode: Int): AiMusicGatewayResult.Failure {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val error = json?.optJSONObject("error") ?: json
        val reportedCode = error?.optString("code")?.takeIf { it.isNotBlank() }
        val code = reportedCode?.let { runCatching { AiMusicErrorCode.valueOf(it) }.getOrNull() }
            ?: when (httpCode) {
                401 -> AiMusicErrorCode.AUTH_REQUIRED
                403 -> AiMusicErrorCode.PERMISSION_DENIED
                408, 504 -> AiMusicErrorCode.TIMEOUT
                429 -> AiMusicErrorCode.RATE_LIMITED
                in 400..499 -> AiMusicErrorCode.INVALID_INPUT
                else -> AiMusicErrorCode.PROVIDER_UNAVAILABLE
            }
        return AiMusicGatewayResult.Failure(
            code = code,
            message = error?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "Music gateway HTTP $httpCode",
            retryable = error?.optBoolean("retryable", httpCode == 429 || httpCode >= 500)
                ?: (httpCode == 429 || httpCode >= 500),
            retryAfterMs = error?.nullableLong("retryAfterMs"),
        )
    }

    private fun request(value: AiMusicRequest) = JSONObject().apply {
        put("requestId", value.requestId)
        put("constellationId", value.constellationId)
        put("sourceMode", value.sourceMode.name)
        put("baseMelodyId", value.baseMelodyId)
        put("createdAtMs", value.createdAtMs)
        put("prompt", value.prompt)
        put("noteMidi", JSONArray(value.noteMidi))
        put("noteStartBeats", JSONArray(value.noteStartBeats))
        put("noteDurations", JSONArray(value.noteDurations))
        put("chordMidiGroups", JSONArray().apply {
            value.chordMidiGroups.forEach { put(JSONArray(it)) }
        })
        put("preferences", JSONObject().apply {
            put("genre", value.preferences.genre.name)
            put("rhythm", value.preferences.rhythm.name)
            put("instruments", JSONArray(value.preferences.instruments.map { it.name }))
            put("tempoBpm", value.preferences.tempoBpm)
        })
    }

    private fun decodeRequest(json: JSONObject): AiMusicRequest {
        val preferences = json.getJSONObject("preferences")
        return AiMusicRequest(
            requestId = json.getString("requestId"),
            constellationId = json.getString("constellationId"),
            sourceMode = SourceMode.valueOf(json.getString("sourceMode")),
            baseMelodyId = json.getString("baseMelodyId"),
            createdAtMs = json.getLong("createdAtMs"),
            prompt = json.getString("prompt"),
            noteMidi = json.getJSONArray("noteMidi").mapInts(),
            noteStartBeats = json.getJSONArray("noteStartBeats").mapDoubles(),
            noteDurations = json.getJSONArray("noteDurations").mapDoubles(),
            chordMidiGroups = json.getJSONArray("chordMidiGroups").let { outer ->
                (0 until outer.length()).map { outer.getJSONArray(it).mapInts() }
            },
            preferences = ArrangementPreferences(
                genre = ArrangementGenre.valueOf(preferences.getString("genre")),
                rhythm = ArrangementRhythm.valueOf(preferences.getString("rhythm")),
                instruments = preferences.getJSONArray("instruments").let { values ->
                    (0 until values.length()).map { ArrangementInstrument.valueOf(values.getString(it)) }
                },
                tempoBpm = preferences.getInt("tempoBpm"),
            ),
        )
    }

    private fun job(value: AiMusicJob) = JSONObject().apply {
        put("request", request(value.request))
        put("provider", value.provider)
        put("jobId", value.providerJobId ?: JSONObject.NULL)
        put("status", value.status.name)
        put("results", JSONArray().apply {
            value.results.forEach { result ->
                put(JSONObject().apply {
                    put("id", result.id)
                    put("audioUrl", result.audioUrl ?: JSONObject.NULL)
                    put("localFilePath", result.localFilePath ?: JSONObject.NULL)
                    put("mimeType", result.mimeType)
                    put("durationMs", result.durationMs ?: JSONObject.NULL)
                    put("model", result.model ?: JSONObject.NULL)
                    put("createdAtMs", result.createdAtMs)
                })
            }
        })
        put("selectedResultId", value.selectedResultId ?: JSONObject.NULL)
        put("error", if (value.errorCode == null) JSONObject.NULL else JSONObject().apply {
            put("code", value.errorCode.name)
            put("message", value.errorMessage ?: "")
            put("retryable", value.retryable)
            put("retryAfterMs", value.retryAfterMs ?: JSONObject.NULL)
        })
        put("updatedAtMs", value.updatedAtMs)
    }

    private fun JSONArray.mapInts(): List<Int> = (0 until length()).map(::getInt)

    private fun JSONArray.mapDoubles(): List<Double> = (0 until length()).map(::getDouble)

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.nullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)
}
