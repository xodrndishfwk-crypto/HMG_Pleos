package com.example.starlog2.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayTelemetryProviderTest {
    @Test
    fun emitUntil_resetAndReplay_producesIdenticalTelemetry() {
        val provider = ReplayTelemetryProvider(frames())

        val firstRun = provider.emitUntil(1_000)
        provider.reset()
        val secondRun = provider.emitUntil(1_000)

        assertEquals(firstRun, secondRun)
        assertEquals(listOf(1L, 2L), firstRun.map { it.seq })
        assertEquals(listOf(SourceMode.REPLAY, SourceMode.REPLAY), firstRun.map { it.sourceMode })
    }

    @Test
    fun seek_middlePosition_emitsOnlyRemainingFrames() {
        val provider = ReplayTelemetryProvider(frames())

        provider.seek(1_000)
        val result = provider.emitUntil(2_000)

        assertEquals(1, result.size)
        assertEquals(2L, result.single().seq)
        assertEquals(Gear.P, result.single().gear.value)
    }

    private fun frames(): List<ReplayFrame> = listOf(
        ReplayFrame(
            offsetMs = 0,
            observedAtMs = 10_000,
            point = LatLon(37.0, 127.0),
            accuracyM = 5.0,
            headingDeg = 90.0,
            speedMps = 10.0,
            gear = Gear.D,
            ignitionOn = true,
        ),
        ReplayFrame(
            offsetMs = 1_000,
            observedAtMs = 11_000,
            point = LatLon(37.0001, 127.0001),
            accuracyM = 5.0,
            headingDeg = 90.0,
            speedMps = 0.0,
            gear = Gear.P,
            ignitionOn = true,
        ),
    )
}
