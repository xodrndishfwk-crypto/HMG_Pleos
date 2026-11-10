package com.example.starlog2.integration

import com.example.starlog2.contracts.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class NavigationSessionTest {
    private class InlineQueue : AbstractExecutorService() {
        private var closed = false
        override fun execute(command: Runnable) { command.run() }
        override fun shutdown() { closed = true }
        override fun shutdownNow() = mutableListOf<Runnable>().also { shutdown() }
        override fun isShutdown() = closed
        override fun isTerminated() = closed
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = closed
    }
    private class Routes : RouteProvider, AutoCloseable {
        val callbacks = mutableListOf<(Outcome<Route>) -> Unit>()
        var cancelled = 0; var closed = false
        override fun request(input: RouteRequest, done: (Outcome<Route>) -> Unit): CancelHandle {
            callbacks += done; return CancelHandle { cancelled++ }
        }
        override fun close() { closed = true }
    }
    private class Guidance : GuidanceEngine {
        var status = GuidanceStatus.ACTIVE; var routeId = "r"
        override fun setRoute(route: Route) { routeId = route.routeId }
        override fun clear() { }
        override fun update(sample: Telemetry) = GuidanceState(routeId, status, sample.location.value?.point,
            0.0, 111.0, 30.0, 0, "직진", 111.0)
    }
    private val request = RouteRequest("a", ContractSamples.point, LatLon(37.501,127.0))
    @Test fun oldRouteReplyCannotReplaceNewRequestAndCoordinatesAreLonLat() {
        val routes = Routes(); val states = mutableListOf<RenderEnvelope>()
        val session = NavigationSession(routes, Guidance(), FakeClock(), states::add, queue=InlineQueue())
        session.requestRoute(request); session.requestRoute(request.copy(requestId="b"))
        routes.callbacks[0](Outcome.Ok(ContractSamples.route("a")))
        assertTrue(states.isEmpty())
        routes.callbacks[1](Outcome.Ok(ContractSamples.route("b")))
        assertEquals(listOf(127.0,37.5),states.last().payload.route!!.coordinates.first())
        assertEquals(1,routes.cancelled); session.close()
    }
    @Test fun replaySourceUnknownHeadingAndRegressingSequencePreserved() {
        val states=mutableListOf<RenderEnvelope>(); val provider=FakeTelemetryProvider(); val clock=FakeClock()
        val session=NavigationSession(Routes(),Guidance(),clock,states::add,queue=InlineQueue())
        session.attachTelemetry(provider,SourceMode.REPLAY)
        val sample=ContractSamples.telemetry(clock,2)
        provider.emit(sample.copy(location=sample.location.copy(value=sample.location.value!!.copy(headingReference=Reference.MAGNETIC))))
        assertEquals(SourceMode.REPLAY,states.last().payload.sourceMode); assertNull(states.last().payload.pose!!.headingDeg)
        val count=states.size; provider.emit(sample.copy(seq=1)); assertEquals(count,states.size); session.close()
    }
    @Test fun offRouteRequestsAreDebouncedForFiveSeconds() {
        val routes=Routes(); val guidance=Guidance(); val clock=FakeClock(); val provider=FakeTelemetryProvider()
        val session=NavigationSession(routes,guidance,clock,{},queue=InlineQueue())
        session.requestRoute(request); routes.callbacks[0](Outcome.Ok(ContractSamples.route("a")))
        session.attachTelemetry(provider,SourceMode.REPLAY); guidance.status=GuidanceStatus.OFF_ROUTE
        provider.emit(ContractSamples.telemetry(clock,1)); assertEquals(2,routes.callbacks.size)
        clock.advance(4_999); provider.emit(ContractSamples.telemetry(clock,2)); assertEquals(2,routes.callbacks.size)
        clock.advance(1); provider.emit(ContractSamples.telemetry(clock,3)); assertEquals(3,routes.callbacks.size); session.close()
    }
    @Test fun arrivalIsEmittedOncePerRouteAndCloseSuppressesLateCallbacks() {
        val routes=Routes(); val guidance=Guidance(); val clock=FakeClock(); val provider=FakeTelemetryProvider()
        var arrivals=0; val states=mutableListOf<RenderEnvelope>()
        val session=NavigationSession(routes,guidance,clock,states::add,{arrivals++},InlineQueue())
        session.requestRoute(request); routes.callbacks[0](Outcome.Ok(ContractSamples.route("a")))
        session.attachTelemetry(provider,SourceMode.REPLAY); guidance.status=GuidanceStatus.ARRIVED
        provider.emit(ContractSamples.telemetry(clock,1)); provider.emit(ContractSamples.telemetry(clock,2)); assertEquals(1,arrivals)
        session.requestRoute(request.copy(requestId="b")); routes.callbacks.last()(Outcome.Ok(ContractSamples.route("b").copy(routeId="route-b")))
        provider.emit(ContractSamples.telemetry(clock,3)); assertEquals(2,arrivals)
        session.close(); session.close(); val count=states.size
        routes.callbacks.last()(Outcome.Ok(ContractSamples.route("b"))); provider.emit(ContractSamples.telemetry(clock,4))
        assertEquals(count,states.size); assertTrue(routes.closed)
    }
    @Test fun sourceSwitchStopsOldProviderAndRejectsOldGeneration() {
        val a=FakeTelemetryProvider(); val b=FakeTelemetryProvider(); val clock=FakeClock(); val states=mutableListOf<RenderEnvelope>()
        val session=NavigationSession(Routes(),Guidance(),clock,states::add,queue=InlineQueue())
        session.attachTelemetry(a,SourceMode.REPLAY); session.attachTelemetry(b,SourceMode.REPLAY)
        val count=states.size; a.emit(ContractSamples.telemetry(clock)); assertEquals(count,states.size)
        b.emit(ContractSamples.telemetry(clock)); assertEquals(count+1,states.size); session.close()
    }
}
