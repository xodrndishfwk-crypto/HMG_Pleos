package com.example.starlog2.contracts

import org.junit.Assert.*
import org.junit.Test

class ContractHarnessTest {
    @Test fun wallClockRollbackDoesNotIncreaseStopDuration() {
        val clock = FakeClock(); val start = clock.monotonicMs()
        clock.advance(179_999); clock.setWallTimeMs(1)
        assertEquals(179_999, clock.monotonicMs() - start)
        clock.advance(1); assertEquals(180_000, clock.monotonicMs() - start)
    }
    @Test fun absentSpeedStaysUnknownAndSourceStaysReplay() {
        val t = ContractSamples.telemetry(FakeClock(), speed = null)
        assertNull(t.speedMps.value); assertEquals(Quality.UNAVAILABLE, t.speedMps.quality)
        assertEquals(SourceMode.REPLAY, t.sourceMode)
    }
    @Test fun restartReplacesSubscriberAndStopSuppressesLateSamples() {
        val provider = FakeTelemetryProvider(); var old = 0; var current = 0
        provider.start({ old++ }, {}); provider.start({ current++ }, {})
        provider.emit(ContractSamples.telemetry(FakeClock()))
        provider.stop(); provider.stop(); provider.emit(ContractSamples.telemetry(FakeClock()))
        assertEquals(0, old); assertEquals(1, current)
    }
    @Test fun cancelIsIdempotentAndOldCancelDoesNotCancelReplacement() {
        val provider = FakeRouteProvider(); var callbacks = 0
        val request = RouteRequest("a", ContractSamples.point, LatLon(37.501, 127.0))
        val old = provider.request(request) { fail("old callback") }
        val current = provider.request(request.copy(requestId = "b")) { callbacks++ }
        old.cancel(); provider.complete(Outcome.Ok(ContractSamples.route("b")))
        current.cancel(); current.cancel(); provider.complete(Outcome.Ok(ContractSamples.route("b")))
        assertEquals(1, callbacks)
        val cancelled = provider.request(request) { fail("cancelled callback") }
        cancelled.cancel(); provider.complete(Outcome.Ok(ContractSamples.route("a")))
    }
    @Test fun coordinatesUseLongitudeFirst() { assertEquals(listOf(127.0, 37.5), ContractSamples.point.webCoordinate()) }
    @Test fun invalidAndNonFiniteCoordinatesAreRejected() {
        listOf({ LatLon(Double.NaN, 0.0) }, { LatLon(127.0, 37.5) }, { LatLon(0.0, Double.POSITIVE_INFINITY) }).forEach(::reject)
    }
    @Test fun invalidSampleQualityAndWireCountersAreRejected() {
        reject { TimedValue<Double>(null, Quality.VALID, 0) }
        reject { TimedValue(0.0, Quality.UNAVAILABLE, 0) }
        reject { TimedValue<Double>(null, Quality.UNAVAILABLE, -1) }
        reject { ContractSamples.telemetry(FakeClock(), seq = MAX_SAFE_INTEGER + 1) }
    }
    @Test fun malformedRouteIndicesAndLengthsAreRejected() {
        reject { ContractSamples.route().copy(points = listOf(ContractSamples.point)) }
        reject { ContractSamples.route().copy(distanceM = -1.0) }
        reject { ContractSamples.route().copy(maneuvers = listOf(Maneuver(0, 0, 2, "bad", null, 1.0, 1.0))) }
    }
    @Test fun memoryCommandsAndEpochOrderAreValidated() {
        reject { ExperienceCommand(ExperienceAction.SELECT_MEMORY, null) }
        reject { Visit("v", "d", null, ContractSamples.point, 2, 1, SourceMode.REPLAY, null) }
        assertEquals("s", ExperienceCommand(ExperienceAction.SELECT_MEMORY, "s").starId)
    }
    @Test fun canonicalRoutePreservesUnitsIndicesAndGuidance() {
        val r = Route("r", "q", listOf(LatLon(37.5, 127.0), LatLon(37.501, 127.0)),
            111.2, 30.0, listOf(Maneuver(0, 0, 1, "직진", null, 111.2, 30.0)))
        assertEquals(111.2, r.distanceM, 0.0)
        assertEquals(1, r.maneuvers.single().endPointIndex)
        val guidance = GuidanceState("r", GuidanceStatus.ACTIVE,
            r.points.first(), 1.0, 110.2, 29.0, 0, "직진", 110.2)
        assertEquals(GuidanceStatus.ACTIVE, guidance.status)
    }
    private fun reject(block: () -> Any?) {
        try { block(); fail("Expected invalid contract to be rejected") } catch (_: IllegalArgumentException) { }
    }
}
