package com.example.starlog2.navigation

data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(lat.isFinite() && lat in -90.0..90.0) { "Latitude must be finite and between -90 and 90" }
        require(lon.isFinite() && lon in -180.0..180.0) { "Longitude must be finite and between -180 and 180" }
    }
}

enum class SourceMode { LIVE, REPLAY }

enum class Gear { P, N, D, R, UNKNOWN }

enum class Quality { VALID, STALE, UNAVAILABLE }

enum class Reference { TRUE_NORTH, MAGNETIC, UNKNOWN }

data class TimedValue<T>(
    val value: T?,
    val quality: Quality,
    val ageMs: Long,
) {
    init {
        require(ageMs >= 0) { "ageMs must not be negative" }
    }
}

data class LocationFix(
    val point: LatLon,
    val accuracyM: Double?,
    val headingDeg: Double?,
    val headingReference: Reference,
) {
    init {
        require(accuracyM == null || accuracyM.isFinite() && accuracyM >= 0) {
            "accuracyM must be null or a non-negative finite value"
        }
        require(headingDeg == null || headingDeg.isFinite() && headingDeg >= 0.0 && headingDeg < 360.0) {
            "headingDeg must be null or in the range [0, 360)"
        }
    }
}

data class Telemetry(
    val seq: Long,
    val observedAtMs: Long,
    val monotonicMs: Long,
    val sourceMode: SourceMode,
    val providerId: String,
    val location: TimedValue<LocationFix>,
    val speedMps: TimedValue<Double>,
    val gear: TimedValue<Gear>,
    val ignitionOn: TimedValue<Boolean>,
)

data class AppError(
    val code: String,
    val messageKey: String,
    val retryable: Boolean,
    val requestId: String?,
)

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>

    data class Err(val error: AppError) : Outcome<Nothing>
}

fun interface CancelHandle {
    fun cancel()
}

interface TelemetryProvider {
    fun start(onSample: (Telemetry) -> Unit, onError: (AppError) -> Unit)

    fun stop()
}

data class RouteRequest(
    val requestId: String,
    val start: LatLon,
    val end: LatLon,
) {
    init {
        require(requestId.isNotBlank()) { "requestId must not be blank" }
    }
}

data class Maneuver(
    val index: Int,
    val beginPointIndex: Int,
    val endPointIndex: Int,
    val instruction: String,
    val typeCode: Int?,
    val lengthM: Double,
    val durationS: Double,
)

data class Route(
    val routeId: String,
    val requestId: String,
    val points: List<LatLon>,
    val distanceM: Double,
    val durationS: Double,
    val maneuvers: List<Maneuver>,
)

interface RouteProvider {
    fun request(input: RouteRequest, done: (Outcome<Route>) -> Unit): CancelHandle
}

enum class GuidanceStatus { IDLE, ACTIVE, OFF_ROUTE, ARRIVED, NO_FIX, ERROR }

data class GuidanceState(
    val routeId: String?,
    val status: GuidanceStatus,
    val snappedPoint: LatLon?,
    val alongM: Double?,
    val remainingM: Double?,
    val remainingS: Double?,
    val maneuverIndex: Int?,
    val nextInstruction: String?,
    val distanceToManeuverM: Double?,
)

interface GuidanceEngine {
    fun setRoute(route: Route)

    fun update(sample: Telemetry): GuidanceState

    fun clear()
}

internal fun navigationError(
    code: String,
    requestId: String? = null,
    retryable: Boolean = false,
): AppError = AppError(
    code = code,
    messageKey = "navigation.${code.lowercase()}",
    retryable = retryable,
    requestId = requestId,
)
