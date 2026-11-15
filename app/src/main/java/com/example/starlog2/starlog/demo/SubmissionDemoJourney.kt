package com.example.starlog2.starlog.demo

import com.example.starlog2.contracts.AppError
import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.LocationFix
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.Reference
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Telemetry
import com.example.starlog2.contracts.TimedValue
import com.example.starlog2.starlog.journey.ConstellationEdgeKind
import com.example.starlog2.starlog.journey.JourneyLeg
import com.example.starlog2.starlog.journey.JourneyPoint
import com.example.starlog2.starlog.journey.JourneyRecord
import com.example.starlog2.starlog.journey.JourneyStatus
import com.example.starlog2.starlog.journey.StoredConstellationEdge
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationKind
import com.example.starlog2.starlog.model.ConstellationMember
import com.example.starlog2.starlog.model.StarLogErrorCodes
import com.example.starlog2.starlog.storage.StarLogMemoryRepository
import com.example.starlog2.starlog.visit.VisitDetector

/** Deterministic submission-only REPLAY path. Every raw stop is qualified by [VisitDetector]. */
class SubmissionDemoJourney(
    private val repository: StarLogMemoryRepository,
    val tripId: String = TRIP_ID,
    val sourceMode: SourceMode = SourceMode.REPLAY,
    val route: DemoRoute = DemoRoute.GUMI_BUSAN,
) {
    private val stops: List<DemoStop> get() = route.stops
    val constellationId: String = "constellation-trip-$tripId"
    data class DemoStop(val label: String, val point: LatLon, val leg: JourneyLeg)

    fun start(startedAtMs: Long = DEFAULT_START_AT_MS): Outcome<JourneyRecord> {
        val existing = repository.loadJourney(tripId)
        if (existing is Outcome.Err) return existing
        (existing as Outcome.Ok).value?.let { return Outcome.Ok(it) }
        val created = JourneyRecord(
            id = tripId,
            constellationId = constellationId,
            status = JourneyStatus.ACTIVE,
            sourceMode = sourceMode,
            startedAtMs = startedAtMs,
            completedAtMs = null,
            points = emptyList(),
            edges = emptyList(),
        )
        return when (val saved = repository.saveJourney(created)) {
            is Outcome.Ok -> Outcome.Ok(created)
            is Outcome.Err -> saved
        }
    }

    fun advance(): Outcome<JourneyRecord> {
        val current = current() ?: return invalidState("demo journey has not started")
        if (current.status == JourneyStatus.COMPLETED || current.points.size >= stops.size) {
            return invalidState("demo journey has no remaining point")
        }
        val order = current.points.size
        val stop = stops[order]
        val detector = VisitDetector(defaultTripId = tripId)
        val logicalStart = current.startedAtMs + order * STOP_STRIDE_MS
        val monotonicStart = order * STOP_STRIDE_MS
        detector.update(telemetry(order * 3L, logicalStart, monotonicStart, stop, Gear.P))
        val visits = detector.update(
            telemetry(
                order * 3L + 1,
                logicalStart + LOGICAL_STAY_MS,
                monotonicStart + LOGICAL_STAY_MS,
                stop,
                Gear.P,
            ),
        )
        detector.update(
            telemetry(
                order * 3L + 2,
                logicalStart + LOGICAL_STAY_MS + 1,
                monotonicStart + LOGICAL_STAY_MS + 1,
                stop,
                Gear.D,
            ),
        )
        val visit = visits.singleOrNull()
            ?: return invalidState("VisitDetector did not qualify the demo stop")

        val existingPoint = current.points.firstOrNull { it.label == stop.label }
        val createsNewStar = existingPoint == null
        val starId = if (existingPoint != null) {
            existingPoint.starId
        } else {
            when (val saved = repository.saveVisit(visit)) {
                is Outcome.Ok -> saved.value.id
                is Outcome.Err -> return saved
            }
        }
        val updated = current.copy(
            points = current.points + JourneyPoint(
                journeyId = current.id,
                orderIndex = order,
                label = stop.label,
                point = stop.point,
                visitedAtMs = logicalStart + LOGICAL_STAY_MS,
                leg = stop.leg,
                starId = starId,
                createsNewStar = createsNewStar,
            ),
        )
        return when (val saved = repository.saveJourney(updated)) {
            is Outcome.Ok -> Outcome.Ok(updated)
            is Outcome.Err -> saved
        }
    }

    fun complete(completedAtMs: Long? = null): Outcome<JourneyRecord> {
        val current = current() ?: return invalidState("demo journey has not started")
        if (current.status == JourneyStatus.COMPLETED) return Outcome.Ok(current)
        if (current.points.size != stops.size) {
            return invalidState("demo journey cannot finish before all ${stops.size} points")
        }
        val effectiveCompletedAtMs = maxOf(completedAtMs ?: 0L, current.points.last().visitedAtMs)
        val unique = current.points.filter { it.createsNewStar }
        val constellation = Constellation(
            id = current.constellationId,
            kind = ConstellationKind.TRIP_ROUTE,
            label = route.constellationLabel,
            tripId = current.id,
            centerPoint = LatLon(unique.map { it.point.lat }.average(), unique.map { it.point.lon }.average()),
            createdAtMs = effectiveCompletedAtMs,
            sourceMode = sourceMode,
        )
        val members = unique.mapIndexed { index, point ->
            ConstellationMember(current.constellationId, point.starId, index)
        }
        when (val saved = repository.saveConstellation(constellation, members)) {
            is Outcome.Err -> return saved
            is Outcome.Ok -> Unit
        }

        val outbound = current.points.filter { it.leg == JourneyLeg.OUTBOUND }
        val outboundEdges = outbound.zipWithNext().mapIndexed { index, (from, to) ->
            StoredConstellationEdge(current.constellationId, index, from.starId, to.starId, ConstellationEdgeKind.OUTBOUND)
        }
        // Routes without a new return branch (e.g. one-way Mungyeong) simply have no BRANCH edge.
        val branch = current.points.singleOrNull { it.leg == JourneyLeg.RETURN_NEW }?.let { returnNew ->
            StoredConstellationEdge(
                constellationId = current.constellationId,
                orderIndex = outbound.size - 1,
                fromStarId = outbound.last().starId,
                toStarId = returnNew.starId,
                kind = ConstellationEdgeKind.BRANCH,
            )
        }
        val edges = outboundEdges + listOfNotNull(branch)
        val completed = current.copy(
            status = JourneyStatus.COMPLETED,
            completedAtMs = effectiveCompletedAtMs,
            edges = edges,
        )
        return when (val saved = repository.saveJourney(completed)) {
            is Outcome.Ok -> Outcome.Ok(completed)
            is Outcome.Err -> saved
        }
    }

    fun current(): JourneyRecord? = (repository.loadJourney(tripId) as? Outcome.Ok)?.value

    private fun telemetry(seq: Long, epochMs: Long, monotonicMs: Long, stop: DemoStop, gear: Gear) = Telemetry(
        seq = seq,
        observedAtMs = epochMs,
        monotonicMs = monotonicMs,
        sourceMode = sourceMode,
        providerId = "submission-demo-replay",
        location = TimedValue(LocationFix(stop.point, 5.0, 0.0, Reference.TRUE_NORTH), Quality.VALID, 0),
        speedMps = TimedValue(if (gear == Gear.P) 0.0 else 8.0, Quality.VALID, 0),
        gear = TimedValue(gear, Quality.VALID, 0),
        ignitionOn = TimedValue(true, Quality.VALID, 0),
    )

    private fun invalidState(message: String): Outcome.Err = Outcome.Err(
        AppError(StarLogErrorCodes.INVALID_STATE, message, retryable = false, requestId = null),
    )

    companion object {
        const val TRIP_ID = "submission-gumi-busan-roundtrip"
        const val CONSTELLATION_ID = "constellation-trip-$TRIP_ID"
        const val LOGICAL_STAY_MS = 600_000L
        const val STOP_STRIDE_MS = 900_000L
        const val DEFAULT_START_AT_MS = 1_800_000_000_000L

        /** Stops of the default Gumi → Busan route, kept for existing callers and tests. */
        val STOPS: List<DemoStop> get() = DemoRoute.GUMI_BUSAN.stops
    }
}
