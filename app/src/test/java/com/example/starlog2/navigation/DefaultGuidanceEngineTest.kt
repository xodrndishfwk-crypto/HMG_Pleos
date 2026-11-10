package com.example.starlog2.navigation

import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.GuidanceStatus
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.LocationFix
import com.example.starlog2.contracts.Maneuver
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.Reference
import com.example.starlog2.contracts.Route
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Telemetry
import com.example.starlog2.contracts.TimedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultGuidanceEngineTest {
    @Test
    fun update_beforeRightTurn_reportsNextManeuverAndRemainingValues() {
        val engine = DefaultGuidanceEngine()
        engine.setRoute(rightTurnRoute())

        val state = engine.update(telemetry(LatLon(0.0005, 0.0)))

        assertEquals(GuidanceStatus.ACTIVE, state.status)
        assertEquals(1, state.maneuverIndex)
        assertEquals("우회전", state.nextInstruction)
        assertEquals(55.6, state.alongM!!, 2.0)
        assertEquals(55.6, state.distanceToManeuverM!!, 2.0)
        assertNotNull(state.remainingM)
        assertNotNull(state.remainingS)
    }

    @Test
    fun update_threeAccurateDeviations_marksOffRoute() {
        val engine = DefaultGuidanceEngine()
        engine.setRoute(straightRoute())
        val offRoute = telemetry(LatLon(0.001, 0.001), accuracyM = 5.0)

        assertEquals(GuidanceStatus.ACTIVE, engine.update(offRoute).status)
        assertEquals(GuidanceStatus.ACTIVE, engine.update(offRoute.copy(seq = 2)).status)
        assertEquals(GuidanceStatus.OFF_ROUTE, engine.update(offRoute.copy(seq = 3)).status)
    }

    @Test
    fun update_lowAccuracyDeviation_doesNotAccumulateOffRouteCounter() {
        val engine = DefaultGuidanceEngine()
        engine.setRoute(straightRoute())
        val inaccurate = telemetry(LatLon(0.001, 0.001), accuracyM = 31.0)

        repeat(5) { index ->
            assertEquals(GuidanceStatus.ACTIVE, engine.update(inaccurate.copy(seq = index.toLong())).status)
        }
    }

    @Test
    fun update_noLocationFix_returnsNoFixWithNullProgress() {
        val engine = DefaultGuidanceEngine()
        engine.setRoute(straightRoute())

        val state = engine.update(
            telemetry(LatLon(0.0, 0.0)).copy(
                location = TimedValue(null, Quality.UNAVAILABLE, 0),
            ),
        )

        assertEquals(GuidanceStatus.NO_FIX, state.status)
        assertEquals(null, state.alongM)
    }

    @Test
    fun update_nearEndpoint_arrivesAndRemainsArrived() {
        val engine = DefaultGuidanceEngine()
        engine.setRoute(straightRoute())

        val first = engine.update(telemetry(LatLon(0.00195, 0.0)))
        val second = engine.update(telemetry(LatLon(0.001, 0.001)))

        assertEquals(GuidanceStatus.ARRIVED, first.status)
        assertEquals(GuidanceStatus.ARRIVED, second.status)
        assertEquals(0.0, second.remainingM!!, 0.0)
    }

    @Test
    fun setRoute_newRoute_resetsArrivalAndProgress() {
        val engine = DefaultGuidanceEngine()
        engine.setRoute(straightRoute())
        assertEquals(GuidanceStatus.ARRIVED, engine.update(telemetry(LatLon(0.002, 0.0))).status)

        engine.setRoute(rightTurnRoute().copy(routeId = "replacement"))
        val state = engine.update(telemetry(LatLon(0.0, 0.0)))

        assertEquals(GuidanceStatus.ACTIVE, state.status)
        assertEquals("replacement", state.routeId)
        assertTrue(state.alongM!! < 1.0)
    }

    @Test
    fun update_largeBackwardTeleport_doesNotRewindProgress() {
        val engine = DefaultGuidanceEngine(
            GuidanceConfig(maximumBacktrackM = 10.0, maximumForwardJumpM = 2_000.0),
        )
        engine.setRoute(longStraightRoute())
        val beforeTeleport = engine.update(telemetry(LatLon(0.015, 0.0)))

        val afterTeleport = engine.update(telemetry(LatLon(0.005, 0.0)).copy(seq = 2))

        assertEquals(beforeTeleport.alongM!!, afterTeleport.alongM!!, 0.1)
    }

    @Test
    fun update_largeForwardTeleport_doesNotJumpProgress() {
        val engine = DefaultGuidanceEngine(
            GuidanceConfig(maximumBacktrackM = 10.0, maximumForwardJumpM = 300.0),
        )
        engine.setRoute(longStraightRoute())
        val beforeTeleport = engine.update(telemetry(LatLon(0.001, 0.0)))

        val afterTeleport = engine.update(telemetry(LatLon(0.015, 0.0)).copy(seq = 2))

        assertEquals(beforeTeleport.alongM!!, afterTeleport.alongM!!, 0.1)
    }

    @Test
    fun update_parallelReturnSegment_doesNotJumpToDistantRouteProgress() {
        val engine = DefaultGuidanceEngine(
            GuidanceConfig(forwardSegmentWindow = 3, maximumForwardJumpM = 500.0),
        )
        engine.setRoute(parallelReturnRoute())
        engine.update(telemetry(LatLon(0.0005, 0.0)))

        val state = engine.update(telemetry(LatLon(0.0005, 0.0001)).copy(seq = 2))

        assertTrue(state.alongM!! < 200.0)
    }

    private fun straightRoute(): Route {
        val points = listOf(LatLon(0.0, 0.0), LatLon(0.002, 0.0))
        return Route(
            routeId = "straight",
            requestId = "request-straight",
            points = points,
            distanceM = NavigationGeometry.distanceM(points[0], points[1]),
            durationS = 20.0,
            maneuvers = listOf(Maneuver(0, 0, 1, "도착", 4, 222.4, 20.0)),
        )
    }

    private fun rightTurnRoute(): Route {
        val points = listOf(LatLon(0.0, 0.0), LatLon(0.001, 0.0), LatLon(0.001, 0.001))
        return Route(
            routeId = "right-turn",
            requestId = "request-turn",
            points = points,
            distanceM = 222.4,
            durationS = 40.0,
            maneuvers = listOf(
                Maneuver(0, 0, 1, "직진", 1, 111.2, 20.0),
                Maneuver(1, 1, 2, "우회전", 10, 111.2, 20.0),
            ),
        )
    }

    private fun longStraightRoute(): Route {
        val points = listOf(LatLon(0.0, 0.0), LatLon(0.02, 0.0))
        val distanceM = NavigationGeometry.distanceM(points.first(), points.last())
        return Route(
            routeId = "long-straight",
            requestId = "request-long",
            points = points,
            distanceM = distanceM,
            durationS = 180.0,
            maneuvers = listOf(Maneuver(0, 0, 1, "도착", 4, distanceM, 180.0)),
        )
    }

    private fun parallelReturnRoute(): Route {
        val points = listOf(
            LatLon(0.0, 0.0),
            LatLon(0.001, 0.0),
            LatLon(0.002, 0.0),
            LatLon(0.003, 0.0),
            LatLon(0.003, 0.001),
            LatLon(0.002, 0.001),
            LatLon(0.001, 0.001),
            LatLon(0.0, 0.001),
            LatLon(0.0, 0.0001),
            LatLon(0.001, 0.0001),
        )
        val distanceM = NavigationGeometry.cumulativeDistances(points).last()
        return Route(
            routeId = "parallel-return",
            requestId = "request-parallel",
            points = points,
            distanceM = distanceM,
            durationS = 300.0,
            maneuvers = listOf(Maneuver(0, 0, points.lastIndex, "도착", 4, distanceM, 300.0)),
        )
    }

    private fun telemetry(point: LatLon, accuracyM: Double = 5.0): Telemetry = Telemetry(
        seq = 1,
        observedAtMs = 1_000,
        monotonicMs = 1_000,
        sourceMode = SourceMode.REPLAY,
        providerId = "test",
        location = TimedValue(LocationFix(point, accuracyM, 0.0, Reference.TRUE_NORTH), Quality.VALID, 0),
        speedMps = TimedValue(10.0, Quality.VALID, 0),
        gear = TimedValue(Gear.D, Quality.VALID, 0),
        ignitionOn = TimedValue(true, Quality.VALID, 0),
    )
}
