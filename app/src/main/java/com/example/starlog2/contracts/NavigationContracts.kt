package com.example.starlog2.contracts

const val CONTRACT_VERSION = "starlog-contracts-1.0.0"
const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

internal fun safeCounter(value: Long) = require(value in 0..MAX_SAFE_INTEGER)
internal fun nonNegative(value: Double) = require(value.isFinite() && value >= 0)
internal fun heading(value: Double) = require(value.isFinite() && value >= 0 && value < 360)

data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(lat.isFinite() && lat in -90.0..90.0)
        require(lon.isFinite() && lon in -180.0..180.0)
    }
    fun webCoordinate(): List<Double> = listOf(lon, lat)
}
enum class SourceMode { LIVE, REPLAY }
enum class Gear { P, N, D, R, UNKNOWN }
enum class Quality { VALID, STALE, UNAVAILABLE }
enum class Reference { TRUE_NORTH, MAGNETIC, UNKNOWN }

data class TimedValue<T>(val value: T?, val quality: Quality, val ageMs: Long) {
    init {
        safeCounter(ageMs)
        require(quality != Quality.VALID || value != null)
        require(quality != Quality.UNAVAILABLE || value == null)
    }
}
data class LocationFix(
    val point: LatLon, val accuracyM: Double?, val headingDeg: Double?, val headingReference: Reference,
) {
    init { accuracyM?.let(::nonNegative); headingDeg?.let(::heading) }
}
data class Telemetry(
    val seq: Long, val observedAtMs: Long, val monotonicMs: Long,
    val sourceMode: SourceMode, val providerId: String,
    val location: TimedValue<LocationFix>, val speedMps: TimedValue<Double>,
    val gear: TimedValue<Gear>, val ignitionOn: TimedValue<Boolean>,
) {
    init {
        safeCounter(seq); safeCounter(observedAtMs); safeCounter(monotonicMs)
        require(providerId.isNotBlank()); speedMps.value?.let(::nonNegative)
    }
}
data class AppError(val code: String, val messageKey: String, val retryable: Boolean, val requestId: String?) {
    init { require(code.isNotBlank() && messageKey.isNotBlank()) }
}
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>
}
fun interface CancelHandle { fun cancel() }
interface TelemetryProvider {
    fun start(onSample: (Telemetry) -> Unit, onError: (AppError) -> Unit)
    fun stop()
}
data class RouteRequest(val requestId: String, val start: LatLon, val end: LatLon) {
    init { require(requestId.isNotBlank()) }
}
data class Maneuver(
    val index: Int, val beginPointIndex: Int, val endPointIndex: Int, val instruction: String,
    val typeCode: Int?, val lengthM: Double, val durationS: Double,
) {
    init {
        require(index >= 0 && beginPointIndex >= 0 && endPointIndex >= beginPointIndex)
        nonNegative(lengthM); nonNegative(durationS)
    }
}
data class Route(
    val routeId: String, val requestId: String, val points: List<LatLon>,
    val distanceM: Double, val durationS: Double, val maneuvers: List<Maneuver>,
) {
    init {
        require(routeId.isNotBlank() && requestId.isNotBlank())
        require(points.size in 2..50_000); nonNegative(distanceM); nonNegative(durationS)
        require(maneuvers.withIndex().all { (i, m) ->
            m.index == i && m.endPointIndex < points.size
        })
    }
}
interface RouteProvider {
    fun request(input: RouteRequest, done: (Outcome<Route>) -> Unit): CancelHandle
}
enum class GuidanceStatus { IDLE, ACTIVE, OFF_ROUTE, ARRIVED, NO_FIX, ERROR }
data class GuidanceState(
    val routeId: String?, val status: GuidanceStatus, val snappedPoint: LatLon?,
    val alongM: Double?, val remainingM: Double?, val remainingS: Double?, val maneuverIndex: Int?,
    val nextInstruction: String?, val distanceToManeuverM: Double?,
) {
    init {
        listOfNotNull(alongM, remainingM, remainingS, distanceToManeuverM).forEach(::nonNegative)
        require(maneuverIndex == null || maneuverIndex >= 0)
    }
}
interface GuidanceEngine {
    fun setRoute(route: Route)
    fun update(sample: Telemetry): GuidanceState
    fun clear()
}
