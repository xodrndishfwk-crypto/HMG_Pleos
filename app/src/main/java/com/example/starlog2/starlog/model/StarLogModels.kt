package com.example.starlog2.starlog.model

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.MemoryCard
import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.SourceMode

/**
 * StarLog Domain-Specific Models & Configuration Thresholds.
 *
 * Canonical shared contract types ([LatLon], [SourceMode], [com.example.starlog2.contracts.Gear],
 * [com.example.starlog2.contracts.Quality], [com.example.starlog2.contracts.Reference],
 * [com.example.starlog2.contracts.TimedValue], [com.example.starlog2.contracts.LocationFix],
 * [com.example.starlog2.contracts.Telemetry], [com.example.starlog2.contracts.AppError],
 * [com.example.starlog2.contracts.Outcome], [com.example.starlog2.contracts.Visit],
 * [MemoryStar], [MemoryCard], [com.example.starlog2.contracts.SkyItem],
 * [com.example.starlog2.contracts.ExperienceMode], [com.example.starlog2.contracts.ExperienceState],
 * [com.example.starlog2.contracts.MemoryRepository], [com.example.starlog2.contracts.SessionCheckpoint],
 * [com.example.starlog2.contracts.StarLogUpdate], [com.example.starlog2.contracts.StarLogSession],
 * [com.example.starlog2.contracts.ExperienceAction], [com.example.starlog2.contracts.ExperienceCommand])
 * are imported directly from `com.example.starlog2.contracts` (Gate G1 `starlog-contracts-1.0.0`).
 */
object StarLogErrorCodes {
    const val INVALID_STATE = "INVALID_STATE"
    const val INVALID_INPUT = "INVALID_INPUT"
    const val UNSUPPORTED = "UNSUPPORTED"
    const val STORAGE_FAILED = "STORAGE_FAILED"
    const val STALE_DATA = "STALE_DATA"
    const val NO_DATA = "NO_DATA"
}

/**
 * Converts a canonical [MemoryStar] to a renderer-ready [MemoryCard].
 * Never fabricates fake photos, fake titles, or synthetic weather strings.
 */
fun MemoryStar.toMemoryCard(placeLabel: String? = null): MemoryCard = MemoryCard(
    starId = id,
    title = title,
    placeLabel = placeLabel,
    dateAtMs = createdAtMs,
    photoRef = photoRef,
)

enum class ConstellationKind {
    DAILY_CLUSTER,
    TRIP_ROUTE,
}

/**
 * Represents either a daily routine star cluster (성단) or an ordered trip constellation (여행 별자리).
 */
data class Constellation(
    val id: String,
    val kind: ConstellationKind,
    val label: String?,
    val tripId: String?,
    val centerPoint: LatLon,
    val createdAtMs: Long,
    val sourceMode: SourceMode,
)

data class ConstellationMember(
    val constellationId: String,
    val starId: String,
    val orderIndex: Int,
)

/**
 * Proposed P1 trip line model connecting ordered stars within an explicit `tripId`.
 */
data class TripEdge(
    val tripId: String,
    val fromStarId: String,
    val toStarId: String,
    val orderIndex: Int,
    val distanceM: Double,
)

/**
 * Configurable hypothesis thresholds for [com.example.starlog2.starlog.experience.ExperienceReducer].
 * Note: These values are initial engineering defaults and remain subject to user/product confirmation.
 */
data class ExperienceConfig(
    val stopRequiredMs: Long = 180_000L,
    val maxSpeedMps: Double = 0.3,
    val maxTelemetryAgeMs: Long = 2_000L,
    val maxSampleGapMs: Long = Long.MAX_VALUE,
    val recapSupported: Boolean = true,
)

/**
 * Configurable hypothesis thresholds for [com.example.starlog2.starlog.visit.VisitDetector].
 * Note: P-gear + 10-minute stay is the initial v1 hypothesis to exclude traffic signals;
 * product confirmation is required for edge cases (e.g., short genuine drop-offs).
 */
data class VisitConfig(
    val requiredStayDurationMs: Long = 600_000L,
    val maxRadiusM: Double = 50.0,
    val maxSpeedMps: Double = 0.3,
    val maxAccuracyM: Double = 30.0,
    val maxTelemetryAgeMs: Long = 2_000L,
    val maxSampleGapMs: Long = Long.MAX_VALUE,
)

/**
 * Configurable thresholds for [com.example.starlog2.starlog.bearing.BearingProjector].
 */
data class BearingConfig(
    val fovHalfAngleDeg: Double = 30.0,
    val maxLocationAgeMs: Long = 2_000L,
    val minValidDistanceM: Double = 1.0,
)
