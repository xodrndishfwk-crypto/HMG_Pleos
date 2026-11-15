package com.example.starlog2.starlog.gleo

import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.ArrangementGenre
import com.example.starlog2.starlog.journey.JourneyControlOperation
import com.example.starlog2.starlog.journey.JourneyControlResponse

/** Public Gleo Interface actions declared in assets/schema.json. */
object GleoContract {
    const val ACTION_CONTROL_STARLOG_JOURNEY = "CONTROL_STARLOG_JOURNEY"
    const val ACTION_OPEN_MELODY_STUDIO = "OPEN_MELODY_STUDIO"
    const val ACTION_PREPARE_CONSTELLATION_MUSIC = "PREPARE_CONSTELLATION_MUSIC"
    const val ACTION_PLAY_CONSTELLATION_MUSIC = "PLAY_CONSTELLATION_MUSIC"
    const val EXTRA_ACTION_PARAM = "ACTION_PARAM"
    const val CALLBACK_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    val publicActions = setOf(
        ACTION_CONTROL_STARLOG_JOURNEY,
        ACTION_PREPARE_CONSTELLATION_MUSIC,
        ACTION_PLAY_CONSTELLATION_MUSIC,
    )

    val musicActions = setOf(
        ACTION_OPEN_MELODY_STUDIO,
        ACTION_PREPARE_CONSTELLATION_MUSIC,
        ACTION_PLAY_CONSTELLATION_MUSIC,
    )

    val supportedActions = publicActions + ACTION_OPEN_MELODY_STUDIO
}

data class GleoJourneyActionRequest(
    val operation: JourneyControlOperation,
    val sourceMode: SourceMode,
) {
    companion object {
        fun from(functionName: String?, operation: String?, sourceMode: String?): GleoJourneyActionRequest? {
            if (functionName != GleoContract.ACTION_CONTROL_STARLOG_JOURNEY) return null
            val parsedOperation = operation?.trim()?.uppercase()?.let {
                runCatching { JourneyControlOperation.valueOf(it) }.getOrNull()
            } ?: return null
            val parsedSourceMode = sourceMode?.trim()?.uppercase()?.let {
                runCatching { SourceMode.valueOf(it) }.getOrNull()
            } ?: SourceMode.REPLAY
            if (!sourceMode.isNullOrBlank() && sourceMode.trim().uppercase() != parsedSourceMode.name) return null
            return GleoJourneyActionRequest(parsedOperation, parsedSourceMode)
        }
    }
}

object GleoJourneyCallbackData {
    fun from(response: JourneyControlResponse): Map<String, String> {
        val snapshot = response.snapshot
        return linkedMapOf(
            "tripId" to (snapshot.tripId ?: "null"),
            "journeyStatus" to snapshot.journeyStatus,
            "sourceMode" to snapshot.sourceMode.name,
            "startedAt" to (snapshot.startedAt?.toString() ?: "null"),
            "endedAt" to (snapshot.endedAt?.toString() ?: "null"),
            "recordedPointCount" to snapshot.recordedPointCount.toString(),
            "constellationId" to (snapshot.constellationId ?: "null"),
        ).apply {
            if (response is JourneyControlResponse.Failure) {
                put("errorCode", response.error.code)
                put("retryable", response.error.retryable.toString())
                put("requestId", response.error.requestId ?: "null")
            }
        }
    }
}

data class GleoActionRequest(
    val functionName: String,
    val constellationId: String?,
    val sourceMode: SourceMode?,
    val genre: ArrangementGenre?,
) {
    companion object {
        fun from(
            functionName: String?,
            constellationId: String?,
            sourceMode: String?,
            genre: String?,
        ): GleoActionRequest? {
            val supportedFunctionName = functionName?.takeIf { it in GleoContract.musicActions } ?: return null
            val normalizedConstellationId = constellationId?.trim()?.takeIf { it.isNotEmpty() }
            val parsedSourceMode = sourceMode?.trim()?.uppercase()?.let {
                runCatching { SourceMode.valueOf(it) }.getOrNull()
            }
            if (!sourceMode.isNullOrBlank() && parsedSourceMode == null) return null
            val parsedGenre = genre?.trim()?.uppercase()?.let {
                runCatching { ArrangementGenre.valueOf(it) }.getOrNull()
            }
            if (!genre.isNullOrBlank() && parsedGenre == null) return null
            if (supportedFunctionName != GleoContract.ACTION_PREPARE_CONSTELLATION_MUSIC && parsedGenre != null) return null
            return GleoActionRequest(
                functionName = supportedFunctionName,
                constellationId = normalizedConstellationId,
                sourceMode = parsedSourceMode,
                genre = parsedGenre,
            )
        }
    }
}
