package com.example.starlog2.starlog.music

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.ArrangementGenre
import com.example.starlog2.starlog.melody.ArrangementInstrument
import com.example.starlog2.starlog.melody.ArrangementPreferences
import com.example.starlog2.starlog.melody.ArrangementRhythm
import com.example.starlog2.starlog.melody.MelodyGenerator
import com.example.starlog2.starlog.melody.MelodyStar
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationKind
import com.example.starlog2.starlog.storage.StorageSnapshot
import com.example.starlog2.starlog.storage.TransactionalMemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMusicCoordinatorTest {
    @Test
    fun deterministicRequestAndPolling_areIdempotentAndIgnoreOutOfOrderSnapshots() {
        val melody = MelodyGenerator().preview(
            "constellation-ai",
            listOf(MelodyStar("a", 0.0, 0.0, 0), MelodyStar("b", 1.0, 1.0, 1)),
            0,
        )
        val preferences = ArrangementPreferences(
            ArrangementGenre.AMBIENT,
            ArrangementRhythm.STRAIGHT,
            listOf(ArrangementInstrument.MUSIC_BOX),
            88,
        )
        val request = AiMusicRequestFactory.create(melody, SourceMode.REPLAY, preferences, 100L)
        val sameIdentityAtAnotherTime = AiMusicRequestFactory.create(melody, SourceMode.REPLAY, preferences, 200L)
        assertEquals(request.requestId, sameIdentityAtAnotherTime.requestId)

        val snapshot = StorageSnapshot()
        snapshot.constellationsById[melody.constellationId] = Constellation(
            melody.constellationId,
            ConstellationKind.TRIP_ROUTE,
            null,
            null,
            LatLon(37.5, 127.0),
            1L,
            SourceMode.REPLAY,
        )
        snapshot.melodiesByConstellationId[melody.constellationId] = melody
        val repository = TransactionalMemoryRepository(snapshot)
        val gateway = SequencedGateway()
        var now = 1_000L
        val coordinator = AiMusicCoordinator(repository, gateway) { ++now }

        val queued = (coordinator.submitOrResume(request) as AiMusicGatewayResult.Success).value
        assertEquals(AiMusicStatus.QUEUED, queued.status)
        assertEquals(1, gateway.submitCount)

        val generating = (coordinator.refresh(queued) as AiMusicGatewayResult.Success).value
        assertEquals(AiMusicStatus.GENERATING, generating.status)
        val staleQueued = (coordinator.refresh(generating) as AiMusicGatewayResult.Success).value
        assertEquals("Out-of-order provider snapshot must not regress state", AiMusicStatus.GENERATING, staleQueued.status)
        val succeeded = (coordinator.refresh(staleQueued) as AiMusicGatewayResult.Success).value
        assertEquals(AiMusicStatus.SUCCEEDED, succeeded.status)
        assertEquals(MusicGenerationSource.DEMO_MOCK, succeeded.generationSource)

        val repeated = (coordinator.submitOrResume(request) as AiMusicGatewayResult.Success).value
        assertEquals(succeeded, repeated)
        assertEquals("Completed idempotent request must not submit again", 1, gateway.submitCount)
        assertEquals(succeeded, (repository.loadAiMusicJob(request.requestId) as Outcome.Ok).value)
        assertTrue((repository.loadBaseMelody(melody.constellationId) as Outcome.Ok).value == melody)
    }

    @Test
    fun offlineFailure_isPersistedAndSameRequestCanRetrySuccessfully() {
        val melody = MelodyGenerator().preview(
            "constellation-retry",
            listOf(MelodyStar("a", 0.0, 0.0, 0), MelodyStar("b", 1.0, 1.0, 1)),
            0,
        )
        val snapshot = StorageSnapshot().apply {
            constellationsById[melody.constellationId] = Constellation(
                melody.constellationId,
                ConstellationKind.TRIP_ROUTE,
                null,
                "trip-retry",
                LatLon(37.5, 127.0),
                1L,
                SourceMode.REPLAY,
            )
            melodiesByConstellationId[melody.constellationId] = melody
        }
        val repository = TransactionalMemoryRepository(snapshot)
        val request = AiMusicRequestFactory.create(
            melody,
            SourceMode.REPLAY,
            ArrangementPreferences(
                ArrangementGenre.AMBIENT,
                ArrangementRhythm.STRAIGHT,
                listOf(ArrangementInstrument.MUSIC_BOX),
                88,
            ),
            100L,
        )
        val gateway = FailThenQueueGateway()
        val coordinator = AiMusicCoordinator(repository, gateway) { 500L + gateway.submitCount }

        val failed = (coordinator.submitOrResume(request) as AiMusicGatewayResult.Success).value
        assertEquals(AiMusicStatus.FAILED, failed.status)
        assertEquals(AiMusicErrorCode.NETWORK, failed.errorCode)
        assertTrue(failed.retryable)
        assertEquals(failed, (repository.loadAiMusicJob(request.requestId) as Outcome.Ok).value)

        val retried = (coordinator.submitOrResume(request) as AiMusicGatewayResult.Success).value
        assertEquals(AiMusicStatus.QUEUED, retried.status)
        assertEquals(MusicGenerationSource.DEMO_MOCK, retried.generationSource)
        assertEquals(2, gateway.submitCount)
    }

    private class SequencedGateway : AiMusicGateway {
        var submitCount = 0
        private var request: AiMusicRequest? = null
        private var refreshCount = 0

        override fun submit(request: AiMusicRequest): AiMusicGatewayResult<AiMusicJob> {
            submitCount += 1
            this.request = request
            return AiMusicGatewayResult.Success(job(AiMusicStatus.QUEUED, 1L))
        }

        override fun get(providerJobId: String): AiMusicGatewayResult<AiMusicJob> {
            refreshCount += 1
            return AiMusicGatewayResult.Success(
                when (refreshCount) {
                    1 -> job(AiMusicStatus.GENERATING, 2L)
                    2 -> job(AiMusicStatus.QUEUED, 3L)
                    else -> job(AiMusicStatus.SUCCEEDED, 4L)
                },
            )
        }

        private fun job(status: AiMusicStatus, updatedAtMs: Long): AiMusicJob {
            val result = if (status == AiMusicStatus.SUCCEEDED) {
                listOf(AiMusicResult("result", "/audio.wav", null, "audio/wav", 4_000, "mock", updatedAtMs))
            } else {
                emptyList()
            }
            return AiMusicJob(
                request = requireNotNull(request),
                provider = "mock",
                providerJobId = "job",
                status = status,
                results = result,
                selectedResultId = result.firstOrNull()?.id,
                updatedAtMs = updatedAtMs,
            )
        }
    }

    private class FailThenQueueGateway : AiMusicGateway {
        var submitCount = 0

        override fun submit(request: AiMusicRequest): AiMusicGatewayResult<AiMusicJob> {
            submitCount += 1
            if (submitCount == 1) {
                return AiMusicGatewayResult.Failure(AiMusicErrorCode.NETWORK, "offline", true)
            }
            return AiMusicGatewayResult.Success(
                AiMusicJob(
                    request = request,
                    provider = "demo-mock",
                    providerJobId = "retry-job",
                    status = AiMusicStatus.QUEUED,
                    updatedAtMs = 600L,
                ),
            )
        }

        override fun get(providerJobId: String): AiMusicGatewayResult<AiMusicJob> = error("not used")
    }
}
