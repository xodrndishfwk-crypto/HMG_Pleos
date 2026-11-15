package com.example.starlog2.starlog.journey

import com.example.starlog2.contracts.AppError
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.demo.DemoRoute
import com.example.starlog2.starlog.demo.SubmissionDemoJourney
import com.example.starlog2.starlog.model.StarLogErrorCodes
import com.example.starlog2.starlog.storage.StarLogMemoryRepository
import java.util.UUID

enum class JourneyControlOperation { START, END, STATUS }

data class JourneyControlSnapshot(
    val tripId: String?,
    val journeyStatus: String,
    val sourceMode: SourceMode,
    val startedAt: Long?,
    val endedAt: Long?,
    val recordedPointCount: Int,
    val constellationId: String?,
)

sealed interface JourneyControlResponse {
    val snapshot: JourneyControlSnapshot
    val message: String

    data class Success(
        override val snapshot: JourneyControlSnapshot,
        override val message: String,
        val record: JourneyRecord?,
    ) : JourneyControlResponse

    data class Asking(
        override val snapshot: JourneyControlSnapshot,
        override val message: String,
        val record: JourneyRecord?,
    ) : JourneyControlResponse

    data class Failure(
        override val snapshot: JourneyControlSnapshot,
        override val message: String,
        val error: AppError,
    ) : JourneyControlResponse
}

/** Repository-backed orchestration shared by screen controls and Gleo intents. */
class JourneyController(
    private val repository: StarLogMemoryRepository,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val tripIdFactory: (Long) -> String = { now -> "submission-$now-${UUID.randomUUID()}" },
    /** Route used for newly started journeys. Existing journeys keep the route encoded in their trip id. */
    val route: DemoRoute = DemoRoute.GUMI_BUSAN,
) {
    private var inputEnabled = false

    /** Route of a stored journey; legacy ids without a route slug use this controller's route. */
    fun routeOf(record: JourneyRecord): DemoRoute = DemoRoute.forTrip(record.id, route)

    @Synchronized
    fun control(operation: JourneyControlOperation, sourceMode: SourceMode = SourceMode.REPLAY): JourneyControlResponse =
        when (operation) {
            JourneyControlOperation.START -> start(sourceMode)
            JourneyControlOperation.END -> end(sourceMode)
            JourneyControlOperation.STATUS -> status(sourceMode)
        }

    @Synchronized
    fun start(sourceMode: SourceMode = SourceMode.REPLAY): JourneyControlResponse {
        if (sourceMode != SourceMode.REPLAY) {
            return failure(sourceMode, "The submission demonstration can start only a clearly identified REPLAY journey.", StarLogErrorCodes.UNSUPPORTED)
        }
        val current = current(sourceMode)
        if (current?.status == JourneyStatus.ACTIVE) {
            inputEnabled = true
            return JourneyControlResponse.Success(
                snapshot(current),
                "The existing active StarLog journey will continue without creating a duplicate.",
                current,
            )
        }
        val startedAt = nowMs()
        val tripId = tripIdFactory(startedAt)
        val journey = SubmissionDemoJourney(repository, tripId, sourceMode, DemoRoute.forTrip(tripId, route))
        return when (val result = journey.start(startedAt)) {
            is Outcome.Ok -> {
                inputEnabled = true
                JourneyControlResponse.Success(
                    snapshot(result.value),
                    "The StarLog journey has started with clearly identified demonstration REPLAY data.",
                    result.value,
                )
            }
            is Outcome.Err -> failure(sourceMode, "The StarLog journey could not be started.", result.error.code, result.error)
        }
    }

    @Synchronized
    fun advance(sourceMode: SourceMode = SourceMode.REPLAY): Outcome<JourneyRecord> {
        if (!inputEnabled) return Outcome.Err(error(StarLogErrorCodes.INVALID_STATE, "journey.input_stopped"))
        val active = current(sourceMode)?.takeIf { it.status == JourneyStatus.ACTIVE }
            ?: return Outcome.Err(error(StarLogErrorCodes.INVALID_STATE, "journey.not_active"))
        return SubmissionDemoJourney(repository, active.id, sourceMode, routeOf(active)).advance()
    }

    @Synchronized
    fun end(sourceMode: SourceMode = SourceMode.REPLAY): JourneyControlResponse {
        inputEnabled = false
        val current = current(sourceMode)
            ?: return JourneyControlResponse.Asking(
                readySnapshot(sourceMode),
                "No journey is active. Start a journey before requesting its completion.",
                null,
            )
        if (current.status == JourneyStatus.COMPLETED) return completed(current)
        val currentRoute = routeOf(current)
        if (current.points.size < currentRoute.stops.size) {
            return JourneyControlResponse.Asking(
                snapshot(current),
                "The journey has recorded ${current.points.size} points. Continue recording before ending it.",
                current,
            )
        }
        return when (val result = SubmissionDemoJourney(repository, current.id, sourceMode, currentRoute).complete(nowMs())) {
            is Outcome.Ok -> completed(result.value)
            is Outcome.Err -> failure(sourceMode, "The journey could not be completed and its constellation was not finalized.", result.error.code, result.error)
        }
    }

    @Synchronized
    fun status(sourceMode: SourceMode = SourceMode.REPLAY): JourneyControlResponse {
        val current = current(sourceMode)
        return if (current == null) {
            JourneyControlResponse.Success(readySnapshot(sourceMode), "No StarLog journey has been started yet.", null)
        } else {
            JourneyControlResponse.Success(
                snapshot(current),
                when (current.status) {
                    JourneyStatus.ACTIVE -> "The active journey has recorded ${current.points.size} points."
                    JourneyStatus.COMPLETED -> "The completed journey contains ${current.points.size} recorded visits."
                },
                current,
            )
        }
    }

    @Synchronized
    fun current(sourceMode: SourceMode = SourceMode.REPLAY): JourneyRecord? =
        (repository.loadLatestJourney(sourceMode) as? Outcome.Ok)?.value

    private fun completed(record: JourneyRecord): JourneyControlResponse.Success {
        val stars = record.points.map { it.starId }.distinct().size
        val outbound = record.edges.count { it.kind == ConstellationEdgeKind.OUTBOUND }
        val branches = record.edges.count { it.kind == ConstellationEdgeKind.BRANCH }
        return JourneyControlResponse.Success(
            snapshot(record),
            "The journey was completed with ${record.points.size} visits, ${stars} stars, ${outbound} outbound edges, and ${branches} branch edge.",
            record,
        )
    }

    private fun snapshot(record: JourneyRecord) = JourneyControlSnapshot(
        tripId = record.id,
        journeyStatus = record.status.name,
        sourceMode = record.sourceMode,
        startedAt = record.startedAtMs,
        endedAt = record.completedAtMs,
        recordedPointCount = record.points.size,
        constellationId = record.constellationId.takeIf { record.status == JourneyStatus.COMPLETED },
    )

    private fun readySnapshot(sourceMode: SourceMode) = JourneyControlSnapshot(
        tripId = null,
        journeyStatus = "READY",
        sourceMode = sourceMode,
        startedAt = null,
        endedAt = null,
        recordedPointCount = 0,
        constellationId = null,
    )

    private fun failure(
        sourceMode: SourceMode,
        message: String,
        code: String,
        cause: AppError = error(code, "journey.control_failed"),
    ) = JourneyControlResponse.Failure(
        snapshot = current(sourceMode)?.let(::snapshot) ?: readySnapshot(sourceMode),
        message = message,
        error = cause,
    )

    private fun error(code: String, messageKey: String) = AppError(code, messageKey, false, null)
}
