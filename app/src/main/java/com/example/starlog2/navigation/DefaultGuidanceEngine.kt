package com.example.starlog2.navigation

import com.example.starlog2.contracts.GuidanceEngine
import com.example.starlog2.contracts.GuidanceState
import com.example.starlog2.contracts.GuidanceStatus
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.Maneuver
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.Route
import com.example.starlog2.contracts.Telemetry
import kotlin.math.max

data class GuidanceConfig(
    val deviationThresholdM: Double = 40.0,
    val deviationFixCount: Int = 3,
    val maximumAccuracyM: Double = 30.0,
    val arrivalAlongM: Double = 20.0,
    val arrivalEndpointM: Double = 20.0,
    val backwardSegmentWindow: Int = 2,
    val forwardSegmentWindow: Int = 8,
    val maximumBacktrackM: Double = 15.0,
    val maximumForwardJumpM: Double = 500.0,
)

class DefaultGuidanceEngine(
    private val config: GuidanceConfig = GuidanceConfig(),
) : GuidanceEngine {
    private var route: Route? = null
    private var cumulative = DoubleArray(0)
    private var lastSegmentIndex: Int? = null
    private var lastAlongM: Double? = null
    private var deviationCount = 0
    private var arrived = false

    init {
        require(config.deviationThresholdM > 0)
        require(config.deviationFixCount > 0)
        require(config.maximumAccuracyM >= 0)
        require(config.arrivalAlongM >= 0)
        require(config.arrivalEndpointM >= 0)
        require(config.backwardSegmentWindow >= 0)
        require(config.forwardSegmentWindow >= 0)
        require(config.maximumBacktrackM >= 0)
        require(config.maximumForwardJumpM > 0)
    }

    override fun setRoute(route: Route) {
        validateRoute(route)
        this.route = route
        cumulative = NavigationGeometry.cumulativeDistances(route.points)
        lastSegmentIndex = null
        lastAlongM = null
        deviationCount = 0
        arrived = false
    }

    override fun update(sample: Telemetry): GuidanceState {
        val activeRoute = route ?: return idleState()
        if (arrived) return arrivedState(activeRoute)
        val location = sample.location
        val fix = location.value
        if (location.quality != Quality.VALID || fix == null) {
            return emptyState(activeRoute.routeId, GuidanceStatus.NO_FIX)
        }

        val projection = findProjection(activeRoute.points, fix.point)
        lastSegmentIndex = projection.segmentIndex
        val alongM = cumulative[projection.segmentIndex] +
            (cumulative[projection.segmentIndex + 1] - cumulative[projection.segmentIndex]) * projection.fraction
        lastAlongM = alongM
        val geometryLengthM = cumulative.last()
        val geometryRemainingM = max(0.0, geometryLengthM - alongM)
        val endpointDistanceM = NavigationGeometry.distanceM(fix.point, activeRoute.points.last())
        if (geometryRemainingM <= config.arrivalAlongM && endpointDistanceM <= config.arrivalEndpointM) {
            arrived = true
            deviationCount = 0
            return arrivedState(activeRoute, projection.point, alongM)
        }

        val accurate = fix.accuracyM == null || fix.accuracyM <= config.maximumAccuracyM
        if (accurate) {
            deviationCount = if (projection.distanceM > config.deviationThresholdM) deviationCount + 1 else 0
        }
        val status = if (deviationCount >= config.deviationFixCount) {
            GuidanceStatus.OFF_ROUTE
        } else {
            GuidanceStatus.ACTIVE
        }
        val distanceFractionRemaining = if (geometryLengthM == 0.0) 0.0 else geometryRemainingM / geometryLengthM
        val maneuver = nextManeuver(activeRoute, alongM)
        val maneuverDistance = maneuver?.let {
            max(0.0, cumulative[it.beginPointIndex] - alongM)
        }
        return GuidanceState(
            routeId = activeRoute.routeId,
            status = status,
            snappedPoint = projection.point,
            alongM = alongM,
            remainingM = activeRoute.distanceM * distanceFractionRemaining,
            remainingS = activeRoute.durationS * distanceFractionRemaining,
            maneuverIndex = maneuver?.index,
            nextInstruction = maneuver?.instruction,
            distanceToManeuverM = maneuverDistance,
        )
    }

    override fun clear() {
        route = null
        cumulative = DoubleArray(0)
        lastSegmentIndex = null
        lastAlongM = null
        deviationCount = 0
        arrived = false
    }

    private fun findProjection(points: List<LatLon>, point: LatLon): RouteProjection {
        val previous = lastSegmentIndex
        val firstIndex = if (previous == null) 0 else max(0, previous - config.backwardSegmentWindow)
        val lastIndex = if (previous == null) {
            points.lastIndex - 1
        } else {
            minOf(points.lastIndex - 1, previous + config.forwardSegmentWindow)
        }
        var best: RouteProjection? = null
        for (index in firstIndex..lastIndex) {
            val projection = NavigationGeometry.projectToSegment(point, points[index], points[index + 1])
            val candidateAlongM = cumulative[index] +
                (cumulative[index + 1] - cumulative[index]) * projection.fraction
            if (isAllowedProgress(candidateAlongM) && (best == null || projection.distanceM < best.distanceM)) {
                best = RouteProjection(index, projection.point, projection.fraction, projection.distanceM)
            }
        }
        return best ?: projectionAtLastProgress(points, point)
    }

    private fun isAllowedProgress(candidateAlongM: Double): Boolean {
        val previousAlongM = lastAlongM ?: return true
        return candidateAlongM >= previousAlongM - config.maximumBacktrackM &&
            candidateAlongM <= previousAlongM + config.maximumForwardJumpM
    }

    private fun projectionAtLastProgress(points: List<LatLon>, point: LatLon): RouteProjection {
        val segmentIndex = checkNotNull(lastSegmentIndex)
        val alongM = checkNotNull(lastAlongM)
        val segmentLengthM = cumulative[segmentIndex + 1] - cumulative[segmentIndex]
        val fraction = if (segmentLengthM == 0.0) {
            0.0
        } else {
            ((alongM - cumulative[segmentIndex]) / segmentLengthM).coerceIn(0.0, 1.0)
        }
        val snappedPoint = NavigationGeometry.pointAtDistance(points, alongM)
        return RouteProjection(
            segmentIndex = segmentIndex,
            point = snappedPoint,
            fraction = fraction,
            distanceM = NavigationGeometry.distanceM(point, snappedPoint),
        )
    }

    private fun nextManeuver(route: Route, alongM: Double): Maneuver? {
        if (route.maneuvers.isEmpty()) return null
        return route.maneuvers.firstOrNull { cumulative[it.beginPointIndex] > alongM + MANEUVER_EPSILON_M }
            ?: route.maneuvers.last()
    }

    private fun validateRoute(route: Route) {
        require(route.routeId.isNotBlank()) { "routeId must not be blank" }
        require(route.requestId.isNotBlank()) { "requestId must not be blank" }
        require(route.points.size in 2..Polyline6.DEFAULT_MAX_POINTS) { "Route must have 2 to 50000 points" }
        require(route.distanceM.isFinite() && route.distanceM >= 0) { "distanceM must be finite and non-negative" }
        require(route.durationS.isFinite() && route.durationS >= 0) { "durationS must be finite and non-negative" }
        route.maneuvers.forEachIndexed { expectedIndex, maneuver ->
            require(maneuver.index == expectedIndex) { "Maneuver indices must be contiguous" }
            require(maneuver.beginPointIndex in route.points.indices) { "Invalid maneuver begin index" }
            require(maneuver.endPointIndex in route.points.indices) { "Invalid maneuver end index" }
            require(maneuver.beginPointIndex <= maneuver.endPointIndex) { "Invalid maneuver index range" }
            require(maneuver.lengthM.isFinite() && maneuver.lengthM >= 0) { "Invalid maneuver length" }
            require(maneuver.durationS.isFinite() && maneuver.durationS >= 0) { "Invalid maneuver duration" }
        }
    }

    private fun idleState(): GuidanceState = emptyState(null, GuidanceStatus.IDLE)

    private fun emptyState(routeId: String?, status: GuidanceStatus): GuidanceState = GuidanceState(
        routeId = routeId,
        status = status,
        snappedPoint = null,
        alongM = null,
        remainingM = null,
        remainingS = null,
        maneuverIndex = null,
        nextInstruction = null,
        distanceToManeuverM = null,
    )

    private fun arrivedState(
        route: Route,
        snappedPoint: LatLon = route.points.last(),
        alongM: Double = cumulative.last(),
    ): GuidanceState = GuidanceState(
        routeId = route.routeId,
        status = GuidanceStatus.ARRIVED,
        snappedPoint = snappedPoint,
        alongM = alongM,
        remainingM = 0.0,
        remainingS = 0.0,
        maneuverIndex = route.maneuvers.lastOrNull()?.index,
        nextInstruction = route.maneuvers.lastOrNull()?.instruction,
        distanceToManeuverM = 0.0,
    )

    private data class RouteProjection(
        val segmentIndex: Int,
        val point: LatLon,
        val fraction: Double,
        val distanceM: Double,
    )

    private companion object {
        const val MANEUVER_EPSILON_M = 1.0
    }
}
