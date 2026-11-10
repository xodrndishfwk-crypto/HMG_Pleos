package com.example.starlog2.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGeometryTest {
    @Test
    fun distanceM_oneThousandthLatitude_isAbout111Meters() {
        val distance = NavigationGeometry.distanceM(LatLon(0.0, 0.0), LatLon(0.001, 0.0))

        assertEquals(111.2, distance, 1.0)
    }

    @Test
    fun bearingDeg_northThenEast_returnsCardinalBearings() {
        assertEquals(0.0, NavigationGeometry.bearingDeg(LatLon(0.0, 0.0), LatLon(0.001, 0.0)), 0.1)
        assertEquals(90.0, NavigationGeometry.bearingDeg(LatLon(0.0, 0.0), LatLon(0.0, 0.001)), 0.1)
    }

    @Test
    fun smoothBearing_crossesNorth_usesShortestArc() {
        val smoothed = NavigationGeometry.smoothBearing(359.0, 1.0, 0.5)

        assertEquals(0.0, smoothed, 0.0001)
    }

    @Test
    fun lookAheadBearing_afterRightAngle_pointsTowardTurn() {
        val route = listOf(LatLon(0.0, 0.0), LatLon(0.001, 0.0), LatLon(0.001, 0.001))

        val bearing = NavigationGeometry.lookAheadBearing(route, alongM = 100.0, lookAheadM = 100.0)

        assertEquals(90.0, bearing, 8.0)
    }
}
