package com.example.starlog2.navigation

import com.example.starlog2.contracts.*

/** Approved navigation-local feature boundary, not a duplicate of the frozen common contracts. */
data class DestinationCandidate(
    val id: String, val name: String, val address: String,
    val category: String?, val point: LatLon,
) {
    init { require(id.isNotBlank() && name.isNotBlank()) }
}

/** Must return promptly; callbacks may be synchronous or on another thread. */
fun interface DestinationSearchProvider {
    fun search(query: String, done: (Outcome<List<DestinationCandidate>>) -> Unit): CancelHandle
}

/** Only ticket bookkeeping uses the lock. Provider, cancellation and consumer code never does. */
private class LatestDestinationRequest<T> {
    private class Ticket<T>(val done: (Outcome<T>) -> Unit)
    private val lock = Any()
    private var current: Ticket<T>? = null
    private var active: CancelHandle? = null
    private var closed = false

    fun run(error: AppError, done: (Outcome<T>) -> Unit, start: ((Outcome<T>) -> Unit) -> CancelHandle) {
        val ticket = Ticket(done)
        val previous = synchronized(lock) {
            if (closed) return
            val old = active; active = null; current = ticket; old
        }
        cancelSafely(previous)
        if (!synchronized(lock) { current === ticket }) return
        val handle = try {
            start { result -> complete(ticket, result) }
        } catch (_: Exception) {
            complete(ticket, Outcome.Err(error))
            return
        }
        val obsolete = synchronized(lock) {
            if (current === ticket) { active = handle; false } else true
        }
        if (obsolete) cancelSafely(handle)
    }

    private fun complete(ticket: Ticket<T>, result: Outcome<T>) {
        val done = synchronized(lock) {
            if (current !== ticket) null
            else { current = null; active = null; ticket.done }
        }
        done?.invoke(result)
    }
    fun cancel() {
        val previous = synchronized(lock) { current = null; val old = active; active = null; old }
        cancelSafely(previous)
    }
    fun close() {
        val previous = synchronized(lock) { closed = true; current = null; val old = active; active = null; old }
        cancelSafely(previous)
    }
    private fun cancelSafely(handle: CancelHandle?) {
        try { handle?.cancel() } catch (_: Exception) { /* cancellation cannot revive a stale ticket */ }
    }
}

/** Completion is committed at its ticket check; consumers still serialize display state. */
class DestinationSearch(private val provider: DestinationSearchProvider) : AutoCloseable {
    private val requests = LatestDestinationRequest<List<DestinationCandidate>>()
    fun search(query: String, done: (Outcome<List<DestinationCandidate>>) -> Unit) {
        requests.run(navigationError("NETWORK_UNAVAILABLE", retryable = true), done) { deliver ->
            if (query.isBlank()) { deliver(Outcome.Err(navigationError("INVALID_INPUT"))); CancelHandle {} }
            else provider.search(query.trim(), deliver)
        }
    }
    fun cancel() = requests.cancel()
    override fun close() = requests.close()
}

/** Caller supplies an aged location snapshot and preserves its LIVE/REPLAY source separately. */
class DestinationRoutes(private val provider: RouteProvider, private val maxLocationAgeMs: Long) : AutoCloseable {
    private val requests = LatestDestinationRequest<Route>()
    init { require(maxLocationAgeMs >= 0) }

    fun request(requestId: String, destination: LatLon, location: TimedValue<LocationFix>?, done: (Outcome<Route>) -> Unit) {
        requests.run(navigationError("NETWORK_UNAVAILABLE", requestId, retryable = true), done) { deliver ->
            val code = when {
                requestId.isBlank() -> "INVALID_INPUT"
                location == null || location.quality == Quality.UNAVAILABLE -> "LOCATION_UNAVAILABLE"
                location.quality == Quality.STALE || location.ageMs > maxLocationAgeMs -> "LOCATION_STALE"
                location.value == null -> "LOCATION_UNAVAILABLE"
                else -> null
            }
            if (code != null) {
                deliver(Outcome.Err(navigationError(code, requestId)))
                CancelHandle {}
            } else {
                val request = RouteRequest(requestId, location!!.value!!.point, destination)
                provider.request(request) { result ->
                    deliver(if (result is Outcome.Ok && result.value.requestId != requestId)
                        Outcome.Err(navigationError("INVALID_RESPONSE", requestId)) else result)
                }
            }
        }
    }
    fun cancel() = requests.cancel()
    override fun close() = requests.close()
}
