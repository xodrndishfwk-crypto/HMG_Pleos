package com.example.starlog2.starlog.music

import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.ArrangementPreferences

enum class AiMusicStatus {
    SUBMITTING,
    QUEUED,
    GENERATING,
    DOWNLOADING,
    SUCCEEDED,
    FAILED,
    ;

    val isTerminal: Boolean
        get() = this == SUCCEEDED || this == FAILED
}

enum class AiMusicErrorCode {
    AUTH_REQUIRED,
    PERMISSION_DENIED,
    CREDIT_EXHAUSTED,
    RATE_LIMITED,
    INVALID_INPUT,
    TIMEOUT,
    NETWORK,
    PROVIDER_UNAVAILABLE,
    STORAGE,
    INVALID_RESPONSE,
}

enum class MusicGenerationSource { DEMO_MOCK, SUNO }

data class AiMusicRequest(
    val requestId: String,
    val constellationId: String,
    val sourceMode: SourceMode,
    val baseMelodyId: String,
    val createdAtMs: Long,
    val prompt: String,
    val noteMidi: List<Int>,
    val noteStartBeats: List<Double>,
    val noteDurations: List<Double>,
    val chordMidiGroups: List<List<Int>>,
    val preferences: ArrangementPreferences,
)

data class AiMusicResult(
    val id: String,
    val audioUrl: String?,
    val localFilePath: String?,
    val mimeType: String,
    val durationMs: Long?,
    val model: String?,
    val createdAtMs: Long,
)

data class AiMusicJob(
    val request: AiMusicRequest,
    val provider: String,
    val providerJobId: String?,
    val status: AiMusicStatus,
    val results: List<AiMusicResult> = emptyList(),
    val selectedResultId: String? = null,
    val errorCode: AiMusicErrorCode? = null,
    val errorMessage: String? = null,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null,
    val updatedAtMs: Long,
) {
    init {
        require(provider.isNotBlank())
        require(selectedResultId == null || results.any { it.id == selectedResultId })
        require(status != AiMusicStatus.SUCCEEDED || results.isNotEmpty())
    }

    val selectedResult: AiMusicResult?
        get() = results.firstOrNull { it.id == selectedResultId } ?: results.firstOrNull()

    val generationSource: MusicGenerationSource
        get() = if (provider.equals("suno-platform", ignoreCase = true)) {
            MusicGenerationSource.SUNO
        } else {
            MusicGenerationSource.DEMO_MOCK
        }
}

sealed interface AiMusicGatewayResult<out T> {
    data class Success<T>(val value: T) : AiMusicGatewayResult<T>

    data class Failure(
        val code: AiMusicErrorCode,
        val message: String,
        val retryable: Boolean,
        val retryAfterMs: Long? = null,
    ) : AiMusicGatewayResult<Nothing>
}
