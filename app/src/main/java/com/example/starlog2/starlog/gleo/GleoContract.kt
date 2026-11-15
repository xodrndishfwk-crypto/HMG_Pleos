package com.example.starlog2.starlog.gleo

import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.ArrangementGenre

/** Public Gleo Interface actions declared in assets/schema.json. */
object GleoContract {
    const val ACTION_OPEN_MELODY_STUDIO = "OPEN_MELODY_STUDIO"
    const val ACTION_PREPARE_CONSTELLATION_MUSIC = "PREPARE_CONSTELLATION_MUSIC"
    const val ACTION_PLAY_CONSTELLATION_MUSIC = "PLAY_CONSTELLATION_MUSIC"
    const val EXTRA_ACTION_PARAM = "ACTION_PARAM"
    const val CALLBACK_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    val supportedActions = setOf(
        ACTION_OPEN_MELODY_STUDIO,
        ACTION_PREPARE_CONSTELLATION_MUSIC,
        ACTION_PLAY_CONSTELLATION_MUSIC,
    )
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
            val supportedFunctionName = functionName?.takeIf { it in GleoContract.supportedActions } ?: return null
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
