package com.example.starlog2.navigation

import com.example.starlog2.contracts.*

/** Proposed navigation-local boundary, pending HIGH review; no provider is configured here. */
data class DestinationCandidate(
    val id: String, val name: String, val address: String,
    val category: String?, val point: LatLon,
) {
    init { require(id.isNotBlank() && name.isNotBlank()) }
}

/** Implementations must report NETWORK_TIMEOUT separately from NETWORK_UNAVAILABLE. */
fun interface DestinationSearchProvider {
    fun search(query: String, done: (Outcome<List<DestinationCandidate>>) -> Unit): CancelHandle
}

/** Callbacks may arrive on provider threads. Consumers must dispatch to their reducer/UI queue. */
class DestinationSearch(private val provider: DestinationSearchProvider) {
    private val lock = Any()
    private var generation = 0L
    private var active: CancelHandle? = null

    fun search(query: String, done: (Outcome<List<DestinationCandidate>>) -> Unit) {
        synchronized(lock) {
            val token = ++generation
            active?.cancel(); active = null
            if (query.isBlank()) {
                done(Outcome.Err(navigationError("INVALID_INPUT", null)))
                return
            }
            var completed = false
            val handle = provider.search(query.trim()) { result ->
                synchronized(lock) {
                    if (token == generation && !completed) {
                        completed = true
                        active = null
                        done(result)
                    }
                }
            }
            if (token == generation && !completed) active = handle else handle.cancel()
        }
    }

    fun cancel() = synchronized(lock) { ++generation; active?.cancel(); active = null }
}

/** Provides canonical requests to NavigationSession; does not start guidance or invent location. */
class DestinationRoutes(private val provider: RouteProvider, private val maxLocationAgeMs: Long) {
    private val lock = Any()
    private var generation = 0L
    private var active: CancelHandle? = null
    init { require(maxLocationAgeMs >= 0) }

    fun request(requestId: String, destination: LatLon, location: TimedValue<LocationFix>?, done: (Outcome<Route>) -> Unit) {
        synchronized(lock) {
            val token = ++generation
            active?.cancel(); active = null
            val code = when {
                location == null || location.quality == Quality.UNAVAILABLE -> "LOCATION_UNAVAILABLE"
                location.quality == Quality.STALE || location.ageMs > maxLocationAgeMs -> "LOCATION_STALE"
                location.value == null -> "LOCATION_UNAVAILABLE"
                else -> null
            }
            if (code != null) { done(Outcome.Err(navigationError(code, requestId))); return }
            val request = RouteRequest(requestId, location!!.value!!.point, destination)
            var completed = false
            val handle = provider.request(request) { result ->
                synchronized(lock) {
                    if (token == generation && !completed) {
                        completed = true
                        active = null
                        done(result)
                    }
                }
            }
            if (token == generation && !completed) active = handle else handle.cancel()
        }
    }

    fun cancel() = synchronized(lock) { ++generation; active?.cancel(); active = null }
}
