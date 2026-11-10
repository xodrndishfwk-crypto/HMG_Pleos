package com.example.starlog2.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaRouteProviderTest {
    @Test
    fun parseResponse_validSingleLeg_convertsKilometersAndPreservesIndices() {
        ValhallaRouteProvider("test-client").use { provider ->
            val result = provider.parseResponse(validResponse(), request())

            assertTrue(result is Outcome.Ok)
            val route = (result as Outcome.Ok).value
            assertEquals(1_250.0, route.distanceM, 0.0)
            assertEquals(95.0, route.durationS, 0.0)
            assertEquals(3, route.points.size)
            assertEquals(0, route.maneuvers.single().beginPointIndex)
            assertEquals(2, route.maneuvers.single().endPointIndex)
            assertEquals(1_250.0, route.maneuvers.single().lengthM, 0.0)
        }
    }

    @Test
    fun parseResponse_multipleLegs_returnsUnsupportedRoute() {
        ValhallaRouteProvider("test-client").use { provider ->
            val leg = validLeg()
            val response = "{\"trip\":{\"summary\":{\"length\":1.25,\"time\":95},\"legs\":[$leg,$leg]}}"

            val result = provider.parseResponse(response, request())

            assertTrue(result is Outcome.Err)
            assertEquals("UNSUPPORTED_ROUTE", (result as Outcome.Err).error.code)
        }
    }

    @Test
    fun parseResponse_outOfRangeManeuverIndex_returnsInvalidResponse() {
        ValhallaRouteProvider("test-client").use { provider ->
            val invalidLeg = validLeg().replace("\"end_shape_index\":2", "\"end_shape_index\":99")
            val response = "{\"trip\":{\"summary\":{\"length\":1.25,\"time\":95},\"legs\":[$invalidLeg]}}"

            val result = provider.parseResponse(response, request())

            assertTrue(result is Outcome.Err)
            assertEquals("INVALID_RESPONSE", (result as Outcome.Err).error.code)
        }
    }

    private fun validResponse(): String =
        "{\"trip\":{\"summary\":{\"length\":1.25,\"time\":95},\"legs\":[${validLeg()}]}}"

    private fun validLeg(): String =
        "{\"shape\":\"??AAAA\",\"maneuvers\":[{" +
            "\"begin_shape_index\":0,\"end_shape_index\":2," +
            "\"instruction\":\"직진\",\"type\":1,\"length\":1.25,\"time\":95}]}"

    private fun request(): RouteRequest = RouteRequest(
        requestId = "route-request",
        start = LatLon(0.0, 0.0),
        end = LatLon(0.000002, 0.000002),
    )
}
