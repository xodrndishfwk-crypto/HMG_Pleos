package com.example.starlog2.starlog

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.Reference
import com.example.starlog2.starlog.bearing.BearingProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BearingProjectorTest {

    private val projector = BearingProjector()

    @Test
    fun normalizeAngleDiff_boundariesAndWrapAround_returnsHalfOpenRangeMinus180To180() {
        assertEquals(0.0, BearingProjector.normalizeAngleDiff(0.0), 1e-6)
        assertEquals(0.0, BearingProjector.normalizeAngleDiff(360.0), 1e-6)
        assertEquals(-180.0, BearingProjector.normalizeAngleDiff(180.0), 1e-6)
        assertEquals(-180.0, BearingProjector.normalizeAngleDiff(-180.0), 1e-6)
        assertEquals(-179.0, BearingProjector.normalizeAngleDiff(181.0), 1e-6)
        assertEquals(179.0, BearingProjector.normalizeAngleDiff(-181.0), 1e-6)
    }

    @Test
    fun normalizeRelativeBearing_359To1And1To359_preserves2DegreeContinuity() {
        // Vehicle heading 359 deg, target bearing 1 deg -> target is +2 deg to the right
        val diffRight = BearingProjector.normalizeRelativeBearing(
            targetBearingDeg = 1.0,
            vehicleHeadingDeg = 359.0,
        )
        assertEquals(2.0, diffRight, 1e-6)

        // Vehicle heading 1 deg, target bearing 359 deg -> target is -2 deg to the left
        val diffLeft = BearingProjector.normalizeRelativeBearing(
            targetBearingDeg = 359.0,
            vehicleHeadingDeg = 1.0,
        )
        assertEquals(-2.0, diffLeft, 1e-6)
    }

    @Test
    fun project_sameLocationZeroDistance_excludesUndefinedBearing() {
        val origin = StarLogFixtures.SEOUL_CITY_HALL
        // Direct initialBearingDeg on identical coordinates must return null (undefined bearing)
        assertNull(BearingProjector.initialBearingDeg(origin, origin))

        val sample = StarLogFixtures.createTelemetry(
            point = origin,
            headingDeg = 0.0,
        )
        val samePointStar = StarLogFixtures.createMemoryStar(
            id = "star-zero-dist",
            point = origin,
        )
        val validNorthStar = StarLogFixtures.createMemoryStar(
            id = "star-north",
            point = StarLogFixtures.GYEONGBOKGUNG_SOUTH,
        )

        val projected = projector.project(
            vehicleLocation = sample.location,
            memories = listOf(samePointStar, validNorthStar),
        )
        assertEquals(1, projected.size)
        assertEquals("star-north", projected.first().starId)
    }

    @Test
    fun project_fieldOfViewFilter_includesPlusMinus30DegreesAndExcludesOutside() {
        val origin = StarLogFixtures.SEOUL_CITY_HALL
        val sample = StarLogFixtures.createTelemetry(
            point = origin,
            headingDeg = 0.0,
        )
        // Due north (~0 deg relative bearing) -> inside +-30 deg FOV
        val northStar = StarLogFixtures.createMemoryStar(
            id = "star-north",
            point = StarLogFixtures.GYEONGBOKGUNG_SOUTH,
        )
        // Due east (~90 deg relative bearing) -> outside +-30 deg FOV
        val eastStar = StarLogFixtures.createMemoryStar(
            id = "star-east",
            point = StarLogFixtures.EULJIRO_EAST,
        )

        val result = projector.project(sample.location, listOf(northStar, eastStar))
        assertEquals(1, result.size)
        assertEquals("star-north", result.first().starId)
        assertEquals(0.0, result.first().relativeBearingDeg, 0.5)
    }

    @Test
    fun project_wrapAroundNorthHeading350_includesTargetAtNorth10Degrees() {
        val origin = StarLogFixtures.SEOUL_CITY_HALL
        // Vehicle heading 350 deg (10 deg left of North), target is due North (0 deg) -> relative bearing +10 deg
        val sample = StarLogFixtures.createTelemetry(
            point = origin,
            headingDeg = 350.0,
        )
        val northStar = StarLogFixtures.createMemoryStar(
            id = "star-north",
            point = StarLogFixtures.GYEONGBOKGUNG_SOUTH,
        )

        val result = projector.project(sample.location, listOf(northStar))
        assertEquals(1, result.size)
        assertEquals(10.0, result.first().relativeBearingDeg, 0.5)
    }

    @Test
    fun project_sorting_ordersByAbsRelativeBearingThenDistanceThenStarId() {
        val origin = StarLogFixtures.SEOUL_CITY_HALL
        val sample = StarLogFixtures.createTelemetry(
            point = origin,
            headingDeg = 0.0,
        )

        // Two stars at identical distance & 0 deg bearing -> tie-broken by starId ("star-a" before "star-b")
        val nearStarB = StarLogFixtures.createMemoryStar(
            id = "star-b",
            point = StarLogFixtures.GYEONGBOKGUNG_SOUTH,
        )
        val nearStarA = StarLogFixtures.createMemoryStar(
            id = "star-a",
            point = StarLogFixtures.GYEONGBOKGUNG_SOUTH,
        )
        // Farther star at 0 deg bearing (~1111m)
        val farStar = StarLogFixtures.createMemoryStar(
            id = "star-far",
            point = StarLogFixtures.GYEONGBOKGUNG_NORTH,
        )
        // Star slightly off-center (~5 deg east of north)
        val offCenterStar = StarLogFixtures.createMemoryStar(
            id = "star-off-center",
            point = LatLon(37.5765, 126.9790),
        )

        val projected = projector.project(
            vehicleLocation = sample.location,
            memories = listOf(offCenterStar, farStar, nearStarB, nearStarA),
            selectedStarId = "star-far",
        )

        assertEquals(listOf("star-a", "star-b", "star-far", "star-off-center"), projected.map { it.starId })
        assertTrue(projected[2].selected)
    }

    @Test
    fun project_missingFixOrMagneticReferenceOrStaleLocation_returnsEmptyList() {
        val star = StarLogFixtures.createMemoryStar(id = "star-north")

        val noFixSample = StarLogFixtures.createTelemetry(includeLocationValue = false)
        assertTrue(projector.project(noFixSample.location, listOf(star)).isEmpty())

        val noHeadingSample = StarLogFixtures.createTelemetry(headingDeg = null)
        assertTrue(projector.project(noHeadingSample.location, listOf(star)).isEmpty())

        val magneticSample = StarLogFixtures.createTelemetry(headingReference = Reference.MAGNETIC)
        assertTrue(projector.project(magneticSample.location, listOf(star)).isEmpty())

        val staleQualitySample = StarLogFixtures.createTelemetry(locationQuality = Quality.STALE)
        assertTrue(projector.project(staleQualitySample.location, listOf(star)).isEmpty())

        val expiredAgeSample = StarLogFixtures.createTelemetry(locationAgeMs = 2_001L)
        assertTrue(projector.project(expiredAgeSample.location, listOf(star)).isEmpty())
    }

    @Test
    fun distanceMeters_knownSeoulPoints_returnsAccurateMeters() {
        val dist = BearingProjector.distanceMeters(
            StarLogFixtures.SEOUL_CITY_HALL,
            StarLogFixtures.GYEONGBOKGUNG_SOUTH,
        )
        // 0.001 deg latitude is ~111.195 meters
        assertEquals(111.195, dist, 1.0)
    }
}
