package com.example.starlog2.navigation

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.Route
import com.example.starlog2.contracts.RouteRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
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

    @Test
    fun request_rateLimitedOnce_retriesAfterHeaderAndReturnsRoute() {
        val connections = ArrayDeque(
            listOf(
                FakeConnection(status = 429, headers = mapOf("Retry-After" to "2")),
                FakeConnection(status = 200, body = validResponse()),
            ),
        )
        val sleeps = ArrayList<Long>()
        val results = ArrayList<Outcome<Route>>()
        ValhallaRouteProvider(
            clientId = "test-client",
            executor = ImmediateExecutorService(),
            connectionFactory = { connections.removeFirst() },
            sleeper = sleeps::add,
        ).use { provider ->
            provider.request(request(), results::add)
        }

        assertEquals(listOf(2_000L), sleeps)
        assertEquals(1, results.size)
        assertTrue(results.single() is Outcome.Ok)
        assertTrue(connections.isEmpty())
    }

    @Test
    fun request_socketTimeout_returnsRetryableTimeoutError() {
        val results = ArrayList<Outcome<Route>>()
        ValhallaRouteProvider(
            clientId = "test-client",
            executor = ImmediateExecutorService(),
            connectionFactory = {
                FakeConnection(status = 200, responseFailure = SocketTimeoutException("timeout"))
            },
        ).use { provider ->
            provider.request(request(), results::add)
        }

        val error = (results.single() as Outcome.Err).error
        assertEquals("NETWORK_TIMEOUT", error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun request_invalidInputStatus_returnsNonRetryableInputError() {
        val results = ArrayList<Outcome<Route>>()
        ValhallaRouteProvider(
            clientId = "test-client",
            executor = ImmediateExecutorService(),
            connectionFactory = { FakeConnection(status = 400, body = "not-json") },
        ).use { provider ->
            provider.request(request(), results::add)
        }

        val error = (results.single() as Outcome.Err).error
        assertEquals("INVALID_INPUT", error.code)
        assertEquals(false, error.retryable)
    }

    @Test
    fun request_newerRequestCancelsQueuedRequest_onlyLatestCallbackRuns() {
        val executor = ManualExecutorService()
        val results = ArrayList<Pair<String, Outcome<Route>>>()
        ValhallaRouteProvider(
            clientId = "test-client",
            executor = executor,
            connectionFactory = { FakeConnection(status = 200, body = validResponse()) },
        ).use { provider ->
            provider.request(request().copy(requestId = "old")) { results += "old" to it }
            provider.request(request().copy(requestId = "latest")) { results += "latest" to it }
            executor.runAll()
        }

        assertEquals(listOf("latest"), results.map { it.first })
        assertEquals("latest", ((results.single().second as Outcome.Ok).value).requestId)
    }

    @Test
    fun request_cancelBeforeExecution_suppressesCallback() {
        val executor = ManualExecutorService()
        val results = ArrayList<Outcome<Route>>()
        ValhallaRouteProvider(
            clientId = "test-client",
            executor = executor,
            connectionFactory = { FakeConnection(status = 200, body = validResponse()) },
        ).use { provider ->
            val handle = provider.request(request(), results::add)
            handle.cancel()
            executor.runAll()
        }

        assertTrue(results.isEmpty())
    }

    @Test
    fun parseRetryAfterMs_httpDate_usesInjectedWallClock() {
        val nowMs = 1_784_467_200_000L
        ValhallaRouteProvider(
            clientId = "test-client",
            wallClockMs = { nowMs },
        ).use { provider ->
            val delay = provider.parseRetryAfterMs("Sun, 19 Jul 2026 13:20:30 GMT")

            assertEquals(30_000L, delay)
        }
    }

    @Test
    fun parseRetryAfterMs_extremeSeconds_doesNotOverflowIntoAllowedDelay() {
        ValhallaRouteProvider("test-client").use { provider ->
            val delay = provider.parseRetryAfterMs("2066035336255469781")

            assertEquals(0L, delay)
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

    private class FakeConnection(
        private val status: Int,
        private val body: String = "",
        private val headers: Map<String, String> = emptyMap(),
        private val responseFailure: Exception? = null,
    ) : HttpURLConnection(URI.create("https://example.test/route").toURL()) {
        private val requestBody = ByteArrayOutputStream()

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): ByteArrayOutputStream = requestBody

        override fun getResponseCode(): Int {
            responseFailure?.let { throw it }
            return status
        }

        override fun getInputStream(): ByteArrayInputStream =
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))

        override fun getErrorStream(): ByteArrayInputStream =
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))

        override fun getHeaderField(name: String): String? = headers[name]
    }

    private class ImmediateExecutorService : AbstractExecutorService() {
        private var shutdown = false

        override fun execute(command: Runnable) = command.run()

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = shutdown
    }

    private class ManualExecutorService : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        override fun execute(command: Runnable) {
            tasks += command
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return tasks.toMutableList().also { tasks.clear() }
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown && tasks.isEmpty()

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated
    }
}
