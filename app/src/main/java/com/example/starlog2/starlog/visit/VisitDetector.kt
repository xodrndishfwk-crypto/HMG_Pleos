package com.example.starlog2.starlog.visit

import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.SessionCheckpoint
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Telemetry
import com.example.starlog2.contracts.Visit
import com.example.starlog2.starlog.bearing.BearingProjector
import com.example.starlog2.starlog.model.VisitConfig
import java.util.Locale

/**
 * Pure Kotlin Visit Detector.
 * 0% Android SDK dependency.
 *
 * Identifies genuine stays while strictly excluding traffic signal stops (Gear D/N or short stops).
 * Initial v1 qualification criteria (configurable via [VisitConfig]):
 * - Gear `P` (`VALID`, `ageMs <= 2000ms`)
 * - `speedMps <= 0.3 m/s` (`VALID`, `ageMs <= 2000ms`)
 * - Location `accuracyM <= 30.0m` (`VALID`, `ageMs <= 2000ms`)
 * - Within `50.0m` anchor radius for `>= 600,000ms` (10 minutes) of monotonic time
 */
class VisitDetector(
    private val config: VisitConfig = VisitConfig(),
    private val defaultTripId: String? = null,
    private val defaultLabel: String? = null,
) {
    constructor(
        requiredStayDurationMs: Long,
        maxRadiusM: Double = 50.0,
        maxSpeedMps: Double = 0.3,
        maxAccuracyM: Double = 30.0,
        maxAgeMs: Long = 2_000L,
    ) : this(
        VisitConfig(
            requiredStayDurationMs = requiredStayDurationMs,
            maxRadiusM = maxRadiusM,
            maxSpeedMps = maxSpeedMps,
            maxAccuracyM = maxAccuracyM,
            maxTelemetryAgeMs = maxAgeMs,
            maxSampleGapMs = Long.MAX_VALUE,
        ),
    )

    private data class CandidateStay(
        val startMonotonicMs: Long,
        val startEpochMs: Long,
        val anchorPoint: LatLon,
        val sourceMode: SourceMode,
        val tripId: String?,
        val label: String?,
        var lastMonotonicMs: Long,
        var lastEpochMs: Long,
        var qualifiedVisit: Visit? = null,
        var lastEmittedEndAtMs: Long? = null,
    )

    private var activeCandidate: CandidateStay? = null

    fun reset() {
        activeCandidate = null
    }

    /**
     * Updates detector with current telemetry sample.
     *
     * Emits a [Visit] when:
     * 1. A candidate stay first reaches [VisitConfig.requiredStayDurationMs] (10 minutes), OR
     * 2. An already-qualified stay ends (vehicle departs/shifts out of P) after its `endAtMs`
     *    advanced beyond the initial qualification timestamp (with the same `id` and `dedupeKey`
     *    for idempotent repository upsert).
     */
    fun update(
        sample: Telemetry,
        tripIdOverride: String? = defaultTripId,
        labelOverride: String? = defaultLabel,
    ): List<Visit> {
        val completed = mutableListOf<Visit>()

        if (!checkStayConditions(sample)) {
            flushFinalVisitIfUpdated(activeCandidate)?.let { completed.add(it) }
            activeCandidate = null
            return completed
        }

        val point = sample.location.value!!.point
        val currentMonotonic = sample.monotonicMs
        val currentEpoch = sample.observedAtMs

        val candidate = activeCandidate
        if (candidate == null) {
            activeCandidate = CandidateStay(
                startMonotonicMs = currentMonotonic,
                startEpochMs = currentEpoch,
                anchorPoint = point,
                sourceMode = sample.sourceMode,
                tripId = tripIdOverride,
                label = labelOverride,
                lastMonotonicMs = currentMonotonic,
                lastEpochMs = currentEpoch,
            )
            return completed
        }

        // Guard against monotonic clock rollback or inactive suspension gap
        val monotonicDelta = currentMonotonic - candidate.lastMonotonicMs
        if (monotonicDelta < 0L || monotonicDelta > config.maxSampleGapMs || candidate.sourceMode != sample.sourceMode) {
            flushFinalVisitIfUpdated(candidate)?.let { completed.add(it) }
            activeCandidate = CandidateStay(
                startMonotonicMs = currentMonotonic,
                startEpochMs = currentEpoch,
                anchorPoint = point,
                sourceMode = sample.sourceMode,
                tripId = tripIdOverride,
                label = labelOverride,
                lastMonotonicMs = currentMonotonic,
                lastEpochMs = currentEpoch,
            )
            return completed
        }

        // Check if vehicle stayed within 50m radius of anchor
        val distanceFromAnchor = BearingProjector.distanceMeters(candidate.anchorPoint, point)
        if (distanceFromAnchor > config.maxRadiusM) {
            flushFinalVisitIfUpdated(candidate)?.let { completed.add(it) }
            activeCandidate = CandidateStay(
                startMonotonicMs = currentMonotonic,
                startEpochMs = currentEpoch,
                anchorPoint = point,
                sourceMode = sample.sourceMode,
                tripId = tripIdOverride,
                label = labelOverride,
                lastMonotonicMs = currentMonotonic,
                lastEpochMs = currentEpoch,
            )
            return completed
        }

        // Update monotonic and wall-clock timestamps (prevent wall-clock rollback from making endAtMs < startAtMs)
        candidate.lastMonotonicMs = currentMonotonic
        if (currentEpoch >= candidate.lastEpochMs) {
            candidate.lastEpochMs = currentEpoch
        }

        val stayDurationMs = candidate.lastMonotonicMs - candidate.startMonotonicMs
        if (stayDurationMs >= config.requiredStayDurationMs) {
            val updatedVisit = buildVisit(candidate, candidate.lastEpochMs)
            val previouslyEmittedEnd = candidate.lastEmittedEndAtMs
            candidate.qualifiedVisit = updatedVisit
            if (previouslyEmittedEnd == null) {
                candidate.lastEmittedEndAtMs = updatedVisit.endAtMs
                completed.add(updatedVisit)
            }
        }

        return completed
    }

    /**
     * Completes and flushes an already-qualified active stay at session termination.
     * Never turns an unqualified short stop (< 10 min) into a visit.
     */
    fun finish(atEpochMs: Long): List<Visit> {
        val candidate = activeCandidate ?: return emptyList()
        val stayDurationMs = candidate.lastMonotonicMs - candidate.startMonotonicMs
        if (stayDurationMs < config.requiredStayDurationMs) {
            activeCandidate = null
            return emptyList()
        }

        val finalEndAtMs = maxOf(candidate.lastEpochMs, atEpochMs, candidate.startEpochMs)
        val finalVisit = buildVisit(candidate, finalEndAtMs)
        val previouslyEmittedEnd = candidate.lastEmittedEndAtMs
        activeCandidate = null

        return if (previouslyEmittedEnd == null || finalEndAtMs > previouslyEmittedEnd) {
            listOf(finalVisit)
        } else {
            emptyList()
        }
    }

    /**
     * Returns the currently qualified pending visit (if the active stay has already passed
     * the 10-minute qualification threshold), for saving into a [SessionCheckpoint].
     */
    fun currentQualifiedPendingVisit(atEpochMs: Long? = null): Visit? {
        val candidate = activeCandidate ?: return null
        val stayDurationMs = candidate.lastMonotonicMs - candidate.startMonotonicMs
        if (stayDurationMs < config.requiredStayDurationMs) {
            return null
        }
        val effectiveEndAtMs = maxOf(
            candidate.lastEpochMs,
            atEpochMs ?: candidate.lastEpochMs,
            candidate.startEpochMs,
        )
        return buildVisit(candidate, effectiveEndAtMs)
    }

    /**
     * Restores a qualified pending visit from a [SessionCheckpoint] with its original `dedupeKey`,
     * while resetting active timers so inactive duration is never added to a stay timer.
     */
    fun restore(checkpoint: SessionCheckpoint): List<Visit> {
        activeCandidate = null
        return checkpoint.qualifiedPendingVisit?.let { listOf(it) } ?: emptyList()
    }

    private fun flushFinalVisitIfUpdated(candidate: CandidateStay?): Visit? {
        if (candidate == null) return null
        val qualified = candidate.qualifiedVisit ?: return null
        val emittedEnd = candidate.lastEmittedEndAtMs
        return if (emittedEnd == null || qualified.endAtMs > emittedEnd) {
            candidate.lastEmittedEndAtMs = qualified.endAtMs
            qualified
        } else {
            null
        }
    }

    private fun buildVisit(candidate: CandidateStay, endEpochMs: Long): Visit {
        val safeEndMs = maxOf(endEpochMs, candidate.startEpochMs)
        val dedupeKey = generateDedupeKey(
            point = candidate.anchorPoint,
            startEpochMs = candidate.startEpochMs,
            mode = candidate.sourceMode,
        )
        return Visit(
            id = "visit-${candidate.startEpochMs}",
            dedupeKey = dedupeKey,
            tripId = candidate.tripId,
            point = candidate.anchorPoint,
            startAtMs = candidate.startEpochMs,
            endAtMs = safeEndMs,
            sourceMode = candidate.sourceMode,
            label = candidate.label,
        )
    }

    private fun checkStayConditions(sample: Telemetry): Boolean {
        val gearTimed = sample.gear
        if (gearTimed.quality != Quality.VALID || gearTimed.ageMs < 0L || gearTimed.ageMs > config.maxTelemetryAgeMs) {
            return false
        }
        if (gearTimed.value != Gear.P) {
            return false
        }

        val speedTimed = sample.speedMps
        if (speedTimed.quality != Quality.VALID || speedTimed.ageMs < 0L || speedTimed.ageMs > config.maxTelemetryAgeMs) {
            return false
        }
        val speed = speedTimed.value ?: return false
        if (!speed.isFinite() || speed < 0.0 || speed > config.maxSpeedMps) {
            return false
        }

        val locTimed = sample.location
        if (locTimed.quality != Quality.VALID || locTimed.ageMs < 0L || locTimed.ageMs > config.maxTelemetryAgeMs) {
            return false
        }
        val fix = locTimed.value ?: return false
        val accuracy = fix.accuracyM ?: return false
        if (!accuracy.isFinite() || accuracy < 0.0 || accuracy > config.maxAccuracyM) {
            return false
        }

        return true
    }

    companion object {
        fun generateDedupeKey(point: LatLon, startEpochMs: Long, mode: SourceMode): String {
            val latR = String.format(Locale.US, "%.5f", point.lat)
            val lonR = String.format(Locale.US, "%.5f", point.lon)
            return "visit_${mode.name}_${latR}_${lonR}_$startEpochMs"
        }
    }
}
