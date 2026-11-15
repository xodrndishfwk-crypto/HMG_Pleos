package com.example.starlog2.starlog.music

import com.example.starlog2.contracts.Outcome
import com.example.starlog2.starlog.storage.StarLogMemoryRepository

interface AiMusicGateway {
    fun submit(request: AiMusicRequest): AiMusicGatewayResult<AiMusicJob>

    fun get(providerJobId: String): AiMusicGatewayResult<AiMusicJob>
}

/**
 * Owns idempotent request/job persistence. The provider can return duplicate or delayed snapshots,
 * but it may not change the request identity or replace a completed result with an earlier state.
 */
class AiMusicCoordinator(
    private val repository: StarLogMemoryRepository,
    private val gateway: AiMusicGateway,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    @Synchronized
    fun submitOrResume(request: AiMusicRequest): AiMusicGatewayResult<AiMusicJob> {
        val existing = load(request.requestId)
        if (existing != null) {
            if (!existing.request.sameIdentity(request)) {
                return failure(AiMusicErrorCode.INVALID_INPUT, "Request ID identity conflict", false)
            }
            if (existing.status == AiMusicStatus.SUCCEEDED) return AiMusicGatewayResult.Success(existing)
            if (existing.status == AiMusicStatus.FAILED && !existing.retryable) {
                return AiMusicGatewayResult.Success(existing)
            }
            if (existing.providerJobId != null && existing.status != AiMusicStatus.FAILED) {
                return refresh(existing)
            }
        }

        val submitting = if (existing?.status == AiMusicStatus.FAILED) {
            existing.copy(
                providerJobId = null,
                status = AiMusicStatus.SUBMITTING,
                errorCode = null,
                errorMessage = null,
                retryable = false,
                retryAfterMs = null,
                updatedAtMs = nowMs(),
            )
        } else {
            existing ?: AiMusicJob(
                request = request,
                provider = "starlog-gateway",
                providerJobId = null,
                status = AiMusicStatus.SUBMITTING,
                updatedAtMs = nowMs(),
            )
        }
        if (!save(submitting)) return storageFailure()
        return when (val response = gateway.submit(submitting.request)) {
            is AiMusicGatewayResult.Failure -> persistFailure(submitting, response)
            is AiMusicGatewayResult.Success -> acceptSnapshot(submitting, response.value)
        }
    }

    @Synchronized
    fun refresh(job: AiMusicJob): AiMusicGatewayResult<AiMusicJob> {
        if (job.status.isTerminal) return AiMusicGatewayResult.Success(job)
        val providerJobId = job.providerJobId
            ?: return failure(AiMusicErrorCode.INVALID_RESPONSE, "Missing provider job ID", false)
        return when (val response = gateway.get(providerJobId)) {
            is AiMusicGatewayResult.Failure -> {
                if (response.retryable) response else persistFailure(job, response)
            }
            is AiMusicGatewayResult.Success -> acceptSnapshot(job, response.value)
        }
    }

    @Synchronized
    fun saveDownloadedResult(job: AiMusicJob, resultId: String, localFilePath: String): AiMusicGatewayResult<AiMusicJob> {
        val updatedResults = job.results.map { result ->
            if (result.id == resultId) result.copy(localFilePath = localFilePath) else result
        }
        if (updatedResults.none { it.id == resultId }) {
            return failure(AiMusicErrorCode.INVALID_INPUT, "Unknown result ID", false)
        }
        val updated = job.copy(
            status = AiMusicStatus.SUCCEEDED,
            results = updatedResults,
            selectedResultId = resultId,
            updatedAtMs = nowMs(),
        )
        return if (save(updated)) AiMusicGatewayResult.Success(updated) else storageFailure()
    }

    private fun acceptSnapshot(previous: AiMusicJob, incoming: AiMusicJob): AiMusicGatewayResult<AiMusicJob> {
        if (incoming.request != previous.request) {
            return failure(AiMusicErrorCode.INVALID_RESPONSE, "Provider changed request identity", false)
        }
        if (previous.status == AiMusicStatus.SUCCEEDED && incoming.status != AiMusicStatus.SUCCEEDED) {
            return AiMusicGatewayResult.Success(previous)
        }
        if (!incoming.status.isTerminal && statusRank(incoming.status) < statusRank(previous.status)) {
            return AiMusicGatewayResult.Success(previous)
        }
        val accepted = incoming.copy(
            provider = incoming.provider.ifBlank { previous.provider },
            updatedAtMs = maxOf(previous.updatedAtMs, incoming.updatedAtMs),
        )
        return if (save(accepted)) AiMusicGatewayResult.Success(accepted) else storageFailure()
    }

    private fun persistFailure(previous: AiMusicJob, failure: AiMusicGatewayResult.Failure): AiMusicGatewayResult<AiMusicJob> {
        val failed = previous.copy(
            status = AiMusicStatus.FAILED,
            errorCode = failure.code,
            errorMessage = failure.message,
            retryable = failure.retryable,
            retryAfterMs = failure.retryAfterMs,
            updatedAtMs = nowMs(),
        )
        return if (save(failed)) AiMusicGatewayResult.Success(failed) else storageFailure()
    }

    private fun load(requestId: String): AiMusicJob? =
        (repository.loadAiMusicJob(requestId) as? Outcome.Ok)?.value

    private fun save(job: AiMusicJob): Boolean = repository.saveAiMusicJob(job) is Outcome.Ok

    private fun storageFailure() = failure(AiMusicErrorCode.STORAGE, "AI music job persistence failed", true)

    private fun failure(code: AiMusicErrorCode, message: String, retryable: Boolean) =
        AiMusicGatewayResult.Failure(code, message, retryable)

    private fun statusRank(status: AiMusicStatus): Int = when (status) {
        AiMusicStatus.SUBMITTING -> 0
        AiMusicStatus.QUEUED -> 1
        AiMusicStatus.GENERATING -> 2
        AiMusicStatus.DOWNLOADING -> 3
        AiMusicStatus.SUCCEEDED -> 4
        AiMusicStatus.FAILED -> 4
    }

    private fun AiMusicRequest.sameIdentity(other: AiMusicRequest): Boolean =
        copy(createdAtMs = 0L) == other.copy(createdAtMs = 0L)
}
