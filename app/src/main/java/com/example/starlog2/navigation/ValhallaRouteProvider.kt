package com.example.starlog2.navigation

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ValhallaRouteProvider(
    private val clientId: String,
    endpoint: String = DEFAULT_ENDPOINT,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "starlog-route").apply { isDaemon = true }
    },
) : RouteProvider, AutoCloseable {
    private val endpoint = URI.create(endpoint).toURL()
    private val generation = AtomicLong(0)
    private val lock = Any()
    private var active: RequestTask? = null

    init {
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        require(this.endpoint.protocol == "https") { "Route endpoint must use HTTPS" }
    }

    override fun request(input: RouteRequest, done: (Outcome<Route>) -> Unit): CancelHandle {
        val currentGeneration = generation.incrementAndGet()
        val task = RequestTask(currentGeneration, input.requestId)
        synchronized(lock) {
            active?.cancel()
            active = task
            task.future = executor.submit {
                val result = executeWithRateLimitRetry(input, task)
                if (!task.cancelled.get() && generation.get() == currentGeneration) {
                    done(result)
                }
                synchronized(lock) {
                    if (active === task) active = null
                }
            }
        }
        return CancelHandle { task.cancel() }
    }

    override fun close() {
        synchronized(lock) {
            active?.cancel()
            active = null
        }
        executor.shutdownNow()
    }

    private fun executeWithRateLimitRetry(input: RouteRequest, task: RequestTask): Outcome<Route> {
        var attempt = 0
        while (!task.cancelled.get()) {
            val response = execute(input, task)
            if (response !is HttpOutcome.RateLimited || attempt >= MAX_RATE_LIMIT_RETRIES) {
                return when (response) {
                    is HttpOutcome.Success -> parseResponse(response.body, input)
                    is HttpOutcome.Failure -> Outcome.Err(response.error)
                    is HttpOutcome.RateLimited -> Outcome.Err(
                        navigationError("RATE_LIMITED", input.requestId, retryable = true),
                    )
                }
            }
            attempt++
            if (response.retryAfterMs <= 0L) {
                return Outcome.Err(navigationError("RATE_LIMITED", input.requestId, retryable = true))
            }
            try {
                Thread.sleep(response.retryAfterMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Outcome.Err(navigationError("NETWORK_UNAVAILABLE", input.requestId, retryable = true))
            }
        }
        return Outcome.Err(navigationError("NETWORK_UNAVAILABLE", input.requestId, retryable = true))
    }

    private fun execute(input: RouteRequest, task: RequestTask): HttpOutcome {
        var connection: HttpURLConnection? = null
        return try {
            connection = endpoint.openConnection() as HttpURLConnection
            task.connection = connection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Client-Id", clientId)
            val body = requestBody(input).toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (task.cancelled.get()) {
                return HttpOutcome.Failure(
                    navigationError("NETWORK_UNAVAILABLE", input.requestId, retryable = true),
                )
            }
            val status = connection.responseCode
            if (status == HTTP_TOO_MANY_REQUESTS) {
                return HttpOutcome.RateLimited(parseRetryAfterMs(connection.getHeaderField("Retry-After")))
            }
            if (status !in 200..299) {
                connection.errorStream?.use { readBody(it) }
                return HttpOutcome.Failure(
                    navigationError("NETWORK_UNAVAILABLE", input.requestId, retryable = status >= 500),
                )
            }
            HttpOutcome.Success(connection.inputStream.use(::readBody))
        } catch (_: SocketTimeoutException) {
            HttpOutcome.Failure(navigationError("NETWORK_TIMEOUT", input.requestId, retryable = true))
        } catch (_: ResponseTooLargeException) {
            HttpOutcome.Failure(navigationError("INVALID_RESPONSE", input.requestId))
        } catch (_: Exception) {
            HttpOutcome.Failure(navigationError("NETWORK_UNAVAILABLE", input.requestId, retryable = true))
        } finally {
            task.connection = null
            connection?.disconnect()
        }
    }

    internal fun parseResponse(body: String, request: RouteRequest): Outcome<Route> {
        return try {
            val root = SimpleJson.parse(body).objectValue()
            val trip = root.getValue("trip").objectValue()
            val legs = trip.getValue("legs").arrayValue()
            if (legs.size != 1) {
                return Outcome.Err(navigationError("UNSUPPORTED_ROUTE", request.requestId))
            }
            val summary = trip.getValue("summary").objectValue()
            val distanceM = summary.getValue("length").numberValue() * METERS_PER_KILOMETER
            val durationS = summary.getValue("time").numberValue()
            if (!distanceM.isFinite() || distanceM < 0 || !durationS.isFinite() || durationS < 0) {
                return invalidResponse(request.requestId)
            }
            val leg = legs.single().objectValue()
            val points = when (val decoded = Polyline6.decode(leg.getValue("shape").stringValue())) {
                is Outcome.Ok -> decoded.value
                is Outcome.Err -> return invalidResponse(request.requestId)
            }
            val maneuversJson = leg.getValue("maneuvers").arrayValue()
            val maneuvers = ArrayList<Maneuver>(maneuversJson.size)
            for (index in maneuversJson.indices) {
                val item = maneuversJson[index].objectValue()
                val begin = item.getValue("begin_shape_index").numberValue().toExactInt()
                val end = item.getValue("end_shape_index").numberValue().toExactInt()
                val lengthM = item.getValue("length").numberValue() * METERS_PER_KILOMETER
                val maneuverDurationS = item.getValue("time").numberValue()
                if (begin !in points.indices || end !in points.indices || begin > end ||
                    !lengthM.isFinite() || lengthM < 0 ||
                    !maneuverDurationS.isFinite() || maneuverDurationS < 0
                ) {
                    return invalidResponse(request.requestId)
                }
                maneuvers += Maneuver(
                    index = index,
                    beginPointIndex = begin,
                    endPointIndex = end,
                    instruction = item["instruction"]?.stringValue() ?: "",
                    typeCode = item["type"]?.let { value ->
                        if (value is JsonValue.NullValue) null else value.numberValue().toExactInt()
                    },
                    lengthM = lengthM,
                    durationS = maneuverDurationS,
                )
            }
            Outcome.Ok(
                Route(
                    routeId = request.requestId,
                    requestId = request.requestId,
                    points = points,
                    distanceM = distanceM,
                    durationS = durationS,
                    maneuvers = maneuvers,
                ),
            )
        } catch (_: Exception) {
            invalidResponse(request.requestId)
        }
    }

    private fun requestBody(input: RouteRequest): String =
        "{\"locations\":[${locationJson(input.start)},${locationJson(input.end)}]," +
            "\"costing\":\"auto\",\"units\":\"kilometers\",\"language\":\"ko-KR\"}"

    private fun locationJson(point: LatLon): String =
        "{\"lat\":${point.lat},\"lon\":${point.lon},\"type\":\"break\"}"

    private fun readBody(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_RESPONSE_BYTES) throw ResponseTooLargeException()
            output.write(buffer, 0, count)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun parseRetryAfterMs(value: String?): Long {
        val seconds = value?.trim()?.toLongOrNull() ?: return 0L
        if (seconds <= 0 || seconds > MAX_RETRY_AFTER_SECONDS) return 0L
        return seconds * 1_000L
    }

    private fun invalidResponse(requestId: String): Outcome.Err =
        Outcome.Err(navigationError("INVALID_RESPONSE", requestId))

    private fun Double.toExactInt(): Int {
        require(this in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() && this % 1.0 == 0.0)
        return toInt()
    }

    private class RequestTask(
        val generation: Long,
        val requestId: String,
    ) {
        val cancelled = AtomicBoolean(false)
        @Volatile var connection: HttpURLConnection? = null
        @Volatile var future: Future<*>? = null

        fun cancel() {
            if (!cancelled.compareAndSet(false, true)) return
            connection?.disconnect()
            future?.cancel(true)
        }
    }

    private sealed interface HttpOutcome {
        data class Success(val body: String) : HttpOutcome
        data class Failure(val error: AppError) : HttpOutcome
        data class RateLimited(val retryAfterMs: Long) : HttpOutcome
    }

    private class ResponseTooLargeException : RuntimeException()

    private companion object {
        const val DEFAULT_ENDPOINT = "https://valhalla1.openstreetmap.de/route"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_RATE_LIMIT_RETRIES = 1
        const val MAX_RETRY_AFTER_SECONDS = 60L
        const val METERS_PER_KILOMETER = 1_000.0
    }
}
