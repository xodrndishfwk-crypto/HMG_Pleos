package com.example.starlog2.integration

import com.example.starlog2.contracts.*
import com.example.starlog2.navigation.DestinationCandidate
import com.example.starlog2.navigation.DestinationRoutes
import com.example.starlog2.navigation.DestinationSearch
import com.example.starlog2.navigation.DestinationSearchProvider
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

enum class DestinationPhase { IDLE, SEARCHING, RESULTS, PLANNING, READY_TO_START, GUIDING, CANCELLED, ERROR }
data class DestinationState(
    val phase: DestinationPhase = DestinationPhase.IDLE,
    val query: String = "",
    val candidates: List<DestinationCandidate> = emptyList(),
    val selected: DestinationCandidate? = null,
    val plannedRoute: Route? = null,
    val sourceMode: SourceMode = SourceMode.REPLAY,
    val error: AppError? = null,
)
fun interface DestinationDeadline {
    fun schedule(delayMs: Long, task: () -> Unit): CancelHandle
}
private class ScheduledDestinationDeadline : DestinationDeadline, AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    override fun schedule(delayMs: Long, task: () -> Unit): CancelHandle = try {
        val future = executor.schedule(task, delayMs, TimeUnit.MILLISECONDS)
        CancelHandle { future.cancel(false) }
    } catch (_: RejectedExecutionException) { CancelHandle {} }
    override fun close() { executor.shutdownNow() }
}

/** Feature API for the designed page. State/engine changes share NavigationSession's reducer. */
class DestinationFlow(
    private val session: NavigationSession,
    private val searchProvider: DestinationSearchProvider,
    private val planningProvider: RouteProvider,
    private val observe: (DestinationState) -> Unit,
    private val maxLocationAgeMs: Long = 2_000,
    private val timeoutMs: Long = 15_000,
    private val deadline: DestinationDeadline = ScheduledDestinationDeadline(),
) : AutoCloseable {
    private val search = DestinationSearch(searchProvider)
    private val routing = DestinationRoutes(planningProvider, maxLocationAgeMs)
    private val closed = AtomicBoolean(false)
    private var generation = 0L
    private var requestCounter = 0L
    private val requestPrefix = UUID.randomUUID().toString()
    @Volatile private var timer: CancelHandle? = null
    private var state = DestinationState()
    private var plannedRequest: RouteRequest? = null
    private var plannedSourceToken = 0L
    init { require(timeoutMs > 0); session.own(this) }

    private fun dispatch(block: () -> Unit) = session.dispatch { if (!closed.get()) block() }
    private fun emit(value: DestinationState) { state = value; if (!closed.get()) observe(value) }
    private fun invalidate(): Long {
        generation++; timer?.cancel(); timer = null; search.cancel(); routing.cancel(); plannedRequest = null
        return generation
    }
    private fun timeout(token: Long) {
        timer = deadline.schedule(timeoutMs) { dispatch {
            if (generation == token && state.phase in setOf(DestinationPhase.SEARCHING, DestinationPhase.PLANNING)) {
                invalidate()
                emit(state.copy(phase = DestinationPhase.ERROR, plannedRoute = null,
                    error = AppError("NETWORK_TIMEOUT", "navigation.destination_timeout", true, null)))
            }
        } }
    }

    fun search(query: String) = dispatch {
        val token = invalidate()
        emit(DestinationState(DestinationPhase.SEARCHING, query.trim(), sourceMode = session.sourceMode()))
        timeout(token)
        search.search(query) { result -> dispatch {
            if (token != generation) return@dispatch
            timer?.cancel(); timer = null
            when (result) {
                is Outcome.Ok -> emit(state.copy(phase = DestinationPhase.RESULTS, candidates = result.value.toList(), error = null))
                is Outcome.Err -> emit(state.copy(phase = DestinationPhase.ERROR, error = result.error))
            }
        } }
    }

    fun select(candidateId: String) = dispatch {
        val selected = state.candidates.firstOrNull { it.id == candidateId }
        val token = invalidate()
        if (selected == null) {
            emit(state.copy(phase = DestinationPhase.ERROR, plannedRoute = null,
                error = AppError("INVALID_INPUT", "navigation.unknown_destination", false, null)))
            return@dispatch
        }
        val source = session.sourceMode(); plannedSourceToken = session.sourceToken()
        val location = session.freshLocation(); val requestId = "destination-$requestPrefix-${++requestCounter}"
        if (location?.value != null) plannedRequest = RouteRequest(requestId, location.value.point, selected.point)
        emit(state.copy(phase = DestinationPhase.PLANNING, selected = selected, plannedRoute = null, sourceMode = source, error = null))
        timeout(token)
        routing.request(requestId, selected.point, location) { result -> dispatch {
            if (token != generation) return@dispatch
            timer?.cancel(); timer = null
            if (plannedSourceToken != session.sourceToken()) {
                emit(state.copy(phase = DestinationPhase.ERROR, plannedRoute = null,
                    error = AppError("INVALID_STATE", "navigation.source_changed", false, requestId)))
            } else when (result) {
                is Outcome.Ok -> emit(state.copy(phase = DestinationPhase.READY_TO_START, plannedRoute = result.value, error = null))
                is Outcome.Err -> emit(state.copy(phase = DestinationPhase.ERROR, plannedRoute = null, error = result.error))
            }
        } }
    }

    fun startGuidance() = dispatch {
        if (state.phase == DestinationPhase.GUIDING) return@dispatch
        val route = state.plannedRoute; val input = plannedRequest; val location = session.freshLocation()
        val code = when {
            state.phase != DestinationPhase.READY_TO_START || route == null || input == null -> "INVALID_STATE"
            plannedSourceToken != session.sourceToken() || state.sourceMode != session.sourceMode() -> "INVALID_STATE"
            location == null || location.quality == Quality.UNAVAILABLE || location.value == null -> "LOCATION_UNAVAILABLE"
            location.quality == Quality.STALE || location.ageMs > maxLocationAgeMs -> "LOCATION_STALE"
            else -> null
        }
        if (code != null) { emit(state.copy(phase = DestinationPhase.ERROR, error = AppError(code, "navigation.destination_start_failed", false, input?.requestId))); return@dispatch }
        when (val adopted = session.adoptRouteOnQueue(input!!, route!!)) {
            is Outcome.Ok -> emit(state.copy(phase = DestinationPhase.GUIDING, error = null))
            is Outcome.Err -> emit(state.copy(phase = DestinationPhase.ERROR, error = adopted.error))
        }
    }
    fun cancelPending() = dispatch {
        invalidate(); emit(state.copy(phase = DestinationPhase.CANCELLED, selected = null, plannedRoute = null, error = null))
    }
    fun stopGuidance() = dispatch {
        invalidate(); session.stopGuidanceOnQueue()
        emit(DestinationState(sourceMode = session.sourceMode()))
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { timer?.cancel() }; runCatching { search.close() }; runCatching { routing.close() }
        runCatching { (searchProvider as? AutoCloseable)?.close() }
        runCatching { (planningProvider as? AutoCloseable)?.close() }
        runCatching { (deadline as? AutoCloseable)?.close() }
    }
}
