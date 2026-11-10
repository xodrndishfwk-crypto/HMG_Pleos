package com.example.starlog2.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Polyline6Test {
    @Test
    fun decode_validPolyline6_returnsCoordinatesWithinOneMeter() {
        val result = Polyline6.decode("??AA")

        assertTrue(result is Outcome.Ok)
        val points = (result as Outcome.Ok).value
        assertEquals(2, points.size)
        assertEquals(0.0, points[0].lat, 0.000001)
        assertEquals(0.000001, points[1].lat, 0.000001)
        assertEquals(0.000001, points[1].lon, 0.000001)
    }

    @Test
    fun decode_truncatedPolyline_returnsInvalidResponse() {
        val result = Polyline6.decode("~")

        assertTrue(result is Outcome.Err)
        assertEquals("INVALID_RESPONSE", (result as Outcome.Err).error.code)
    }

    @Test
    fun decode_invalidCharacter_returnsInvalidResponse() {
        val result = Polyline6.decode("??\n?")

        assertTrue(result is Outcome.Err)
    }

    @Test
    fun decode_moreThanConfiguredPointLimit_returnsInvalidResponse() {
        val result = Polyline6.decode("??AAAA", maxPoints = 2)

        assertTrue(result is Outcome.Err)
    }
}
