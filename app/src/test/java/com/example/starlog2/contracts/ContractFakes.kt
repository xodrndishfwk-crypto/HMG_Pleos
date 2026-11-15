package com.example.starlog2.contracts

/** Shared, manually driven JVM harness; these never claim to be live providers. */
class FakeClock(private var wall: Long = 1_800_000_000_000L, private var mono: Long = 0) : Clock {
    override fun wallTimeMs(): Long = wall
    override fun monotonicMs(): Long = mono
    fun advance(ms: Long) { require(ms >= 0); mono = Math.addExact(mono, ms); wall = Math.addExact(wall, ms) }
    fun setWallTimeMs(ms: Long) { safeCounter(ms); wall = ms }
}

class FakeTelemetryProvider : TelemetryProvider {
    private var sample: ((Telemetry) -> Unit)? = null
    private var error: ((AppError) -> Unit)? = null
    override fun start(onSample: (Telemetry) -> Unit, onError: (AppError) -> Unit) {
        sample = onSample; error = onError
    }
    fun emit(value: Telemetry) { require(value.sourceMode == SourceMode.REPLAY); sample?.invoke(value) }
    fun fail(value: AppError) { error?.invoke(value) }
    override fun stop() { sample = null; error = null }
}

class FakeRouteProvider : RouteProvider {
    private var active: Pending? = null
    private class Pending(val request: RouteRequest, val done: (Outcome<Route>) -> Unit)
    override fun request(input: RouteRequest, done: (Outcome<Route>) -> Unit): CancelHandle {
        val pending = Pending(input, done)
        active = pending
        return CancelHandle { if (active === pending) active = null }
    }
    fun complete(outcome: Outcome<Route>) {
        val pending = active ?: return
        if (outcome is Outcome.Ok) require(outcome.value.requestId == pending.request.requestId)
        active = null; pending.done(outcome)
    }
}

object ContractSamples {
    val point = LatLon(37.5, 127.0)
    fun telemetry(clock: Clock, seq: Long = 0, speed: Double? = 0.0, gear: Gear = Gear.P) = Telemetry(
        seq, clock.wallTimeMs(), clock.monotonicMs(), SourceMode.REPLAY, "g1-fake",
        TimedValue(LocationFix(point, 5.0, 359.0, Reference.TRUE_NORTH), Quality.VALID, 0),
        TimedValue(speed, if (speed == null) Quality.UNAVAILABLE else Quality.VALID, 0),
        TimedValue(gear, Quality.VALID, 0), TimedValue(true, Quality.VALID, 0))
    fun route(id: String = "request-a") = Route("route-a", id, listOf(point, LatLon(37.501, 127.0)), 111.2, 30.0,
        listOf(Maneuver(0, 0, 1, "직진", null, 111.2, 30.0)))
}
