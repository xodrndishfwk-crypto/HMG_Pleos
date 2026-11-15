package com.example.starlog2.starlog.constellation

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.starlog.demo.DemoRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstellationShapeMatcherTest {
    @Test
    fun identicalShapeScoresOneEvenWhenRotatedScaledAndReversed() {
        val dipper = ConstellationShapeMatcher.TEMPLATES.first { it.id == "ursa-major-dipper" }.points
        val angle = Math.toRadians(37.0)
        val transformed = dipper.reversed().map { (x, y) ->
            val rx = x * Math.cos(angle) - y * Math.sin(angle)
            val ry = x * Math.sin(angle) + y * Math.cos(angle)
            rx * 3.5 + 10.0 to ry * 3.5 - 4.0
        }
        assertEquals(1.0, ConstellationShapeMatcher.score(transformed, dipper), 1e-6)
    }

    @Test
    fun mirroredShapeStillMatches() {
        val cassiopeia = ConstellationShapeMatcher.TEMPLATES.first { it.id == "cassiopeia" }.points
        val mirrored = cassiopeia.map { (x, y) -> -x to y }
        assertEquals(1.0, ConstellationShapeMatcher.score(mirrored, cassiopeia), 1e-6)
    }

    @Test
    fun curvedArcMatchesCoronaBetterThanAStraightLineDoes() {
        val corona = ConstellationShapeMatcher.TEMPLATES.first { it.id == "corona-borealis" }.points
        val arc = List(7) { i -> val t = Math.PI * i / 6; Math.cos(t) to Math.sin(t) }
        val line = List(7) { i -> i.toDouble() to 0.0 }
        assertTrue(ConstellationShapeMatcher.score(arc, corona) > ConstellationShapeMatcher.score(line, corona))
    }

    @Test
    fun mungyeongRouteHasADeterministicBestMatchAndGrade() {
        val route = DemoRoute.MUNGYEONG_SAEJAE.stops.map { it.point }
        val first = ConstellationShapeMatcher.bestMatch(route)
        val second = ConstellationShapeMatcher.bestMatch(route)
        assertNotNull(first)
        assertEquals(first, second)
        assertTrue(first!!.score in 0.0..1.0)
        assertEquals(ConstellationShapeMatcher.grade(first.score), first.grade)
    }

    @Test
    fun tooFewPointsHaveNoMatch() {
        assertEquals(null, ConstellationShapeMatcher.bestMatch(listOf(LatLon(36.0, 128.0), LatLon(36.1, 128.1))))
    }
}
