package com.example.starlog2.integration

import com.example.starlog2.contracts.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/** All route, live and Replay callbacks converge on this single reducer queue. */
class NavigationSession(
    private val routes: RouteProvider,
    private val guidance: GuidanceEngine,
    private val clock: Clock,
    private val render: (RenderEnvelope) -> Unit,
    private val onArrival: (String) -> Unit = {},
    private val queue: ExecutorService = Executors.newSingleThreadExecutor(),
    starLog: StarLogComponents? = null,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private var route: Route? = null
    private var request: RouteRequest? = null
    private var cancel: CancelHandle? = null
    private var generation = 0L
    private var sourceGeneration = 0L
    private var provider: TelemetryProvider? = null
    private val cursor = TelemetryCursor()
    private var sample: Telemetry? = null
    private var source = SourceMode.REPLAY
    private var state: GuidanceState? = null
    private var error: AppError? = null
    private var lastRerouteAt: Long? = null
    private var arrivalRoute: String? = null
    private var seq = 0L
    private var paused = false
    private var sampleReceivedAt = 0L
    private val ownedResources = mutableListOf<AutoCloseable>()
    private val memory = starLog?.let { StarLogCoordinator(it, clock, { block -> submit(block) }, { publish() }) }

    private fun submit(block: () -> Unit) {
        if (closed.get()) return
        try { queue.execute { if (!closed.get()) block() } } catch (_: RejectedExecutionException) { }
    }
    internal fun dispatch(block: () -> Unit) = submit(block)
    internal fun sourceToken() = sourceGeneration
    internal fun sourceMode() = source
    internal fun freshLocation(): TimedValue<LocationFix>? {
        if (paused) return null
        val location = sample?.location ?: return null
        val elapsed = (clock.monotonicMs() - sampleReceivedAt).coerceAtLeast(0)
        return location.copy(ageMs = (location.ageMs + elapsed).coerceAtMost(MAX_SAFE_INTEGER))
    }
    internal fun own(resource: AutoCloseable) {
        try { queue.execute { if (closed.get()) resource.close() else ownedResources.add(resource) } }
        catch (_: RejectedExecutionException) { resource.close() }
    }
    fun adoptRoute(input: RouteRequest, value: Route, done: (Outcome<Unit>) -> Unit = {}) = submit {
        done(adoptRouteOnQueue(input, value))
    }
    internal fun adoptRouteOnQueue(input: RouteRequest, value: Route): Outcome<Unit> {
        if (value.requestId != input.requestId) return Outcome.Err(AppError("INVALID_RESPONSE", "navigation.request_mismatch", false, input.requestId))
        cancel?.cancel(); cancel = null; generation++
        request = input; route = value; guidance.setRoute(value); arrivalRoute = null; lastRerouteAt = null; error = null
        state = sample?.let(guidance::update); publish()
        return Outcome.Ok(Unit)
    }
    fun stopGuidance() = submit { stopGuidanceOnQueue() }
    internal fun stopGuidanceOnQueue() {
        generation++; cancel?.cancel(); cancel = null; route = null; request = null; arrivalRoute = null; lastRerouteAt = null
        guidance.clear(); state = GuidanceState(null, GuidanceStatus.IDLE, null, null, null, null, null, null, null)
        error = null; publish()
    }
    fun requestRoute(value: RouteRequest) = submit { requestOnQueue(value) }
    private fun requestOnQueue(value: RouteRequest) {
        cancel?.cancel(); val token = ++generation
        error = null
        cancel = routes.request(value) { result -> submit {
            if (token != generation) return@submit
            when (result) {
                is Outcome.Ok -> {
                    if (result.value.requestId != value.requestId) return@submit
                    request = value; route = result.value; guidance.setRoute(result.value); arrivalRoute = null
                    state = sample?.let(guidance::update)
                }
                is Outcome.Err -> error = result.error
            }
            publish()
        } }
    }
    fun attachTelemetry(value: TelemetryProvider, mode: SourceMode) = submit {
        provider?.stop(); ++sourceGeneration
        provider = value; source = mode; sample = null; state = null; cursor.reset(); lastRerouteAt = null
        memory?.begin(mode)
        if (!paused) startProvider(value)
        publish()
    }
    private fun startProvider(value: TelemetryProvider) {
        val token = sourceGeneration
        value.start({ next -> submit {
            if (token == sourceGeneration && next.sourceMode == source) consume(next)
        } }, { failure -> submit { if (token == sourceGeneration) { error = failure; publish() } } })
    }
    fun handleCommand(command: ExperienceCommand) = submit { memory?.command(command) }
    fun refreshTelemetryAge() = submit { memory?.refresh() }
    fun pause() = submit {
        if (!paused) { paused = true; sourceGeneration++; provider?.stop(); memory?.suspend(); publish() }
    }
    fun resume() = submit {
        if (paused) {
            paused = false; sourceGeneration++; cursor.reset(); memory?.begin(source)
            provider?.let(::startProvider)
        }
    }
    private fun consume(value: Telemetry) {
        if (!cursor.accept(value)) return
        sample = value; sampleReceivedAt = clock.monotonicMs(); state = guidance.update(value)
        memory?.telemetry(value)
        val current = state
        if (current?.status == GuidanceStatus.ARRIVED && current.routeId != arrivalRoute) {
            arrivalRoute = current.routeId; current.routeId?.let(onArrival)
        }
        if (current?.status == GuidanceStatus.OFF_ROUTE && value.location.quality == Quality.VALID) {
            val now = clock.monotonicMs(); val last = lastRerouteAt
            val fix = value.location.value; val target = request
            if (fix != null && target != null && (last == null || now - last >= 5_000)) {
                lastRerouteAt = now
                requestOnQueue(RouteRequest("reroute-${++seq}", fix.point, target.end))
            }
        }
        publish()
    }
    private fun publish() {
        val fix = sample?.location?.value
        val pose = fix?.let {
            RenderPose(it.point.webCoordinate(), if (it.headingReference == Reference.TRUE_NORTH) it.headingDeg else null,
                sample!!.location.quality)
        }
        val payload = RenderState(source, route?.let { RenderRoute(it.routeId, it.points.map(LatLon::webCoordinate)) },
            pose, CameraConfig(76.0, 17.7, 310.0), state,
            ExperienceState(ExperienceMode.DRIVING_SAFE, null, 0, null), emptyList(), null, error)
        val next = ++seq
        render(RenderEnvelope(1, "renderState", "render-$next", next, memory?.decorate(payload) ?: payload))
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        queue.execute {
            generation++; sourceGeneration++; cancel?.cancel(); provider?.stop()
            (routes as? AutoCloseable)?.close(); guidance.clear()
            memory?.close()
            ownedResources.forEach { resource -> runCatching { resource.close() } }
            ownedResources.clear()
        }
        queue.shutdown()
    }
}
