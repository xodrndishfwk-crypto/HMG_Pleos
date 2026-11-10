package com.example.starlog2.navigation

import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Telemetry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun start_pauseResume_doesNotEmitWhilePausedAndContinuesFromPosition() {
        val clockMs = AtomicLong(0L)
        val samples = CopyOnWriteArrayList<Telemetry>()
        val firstSample = CountDownLatch(1)
        val secondSample = CountDownLatch(1)
        val provider = ReplayTelemetryProvider(
            frames = frames(),
            clock = ReplayClock(clockMs::get),
            tickMs = 1L,
        )
        provider.start(
            onSample = { sample ->
                samples += sample
                if (sample.seq == 1L) firstSample.countDown()
                if (sample.seq == 2L) secondSample.countDown()
            },
            onError = { throw AssertionError(it) },
        )
        assertTrue(firstSample.await(1, TimeUnit.SECONDS))

        provider.pause()
        clockMs.set(1_000L)
        Thread.sleep(20L)
        assertEquals(1, samples.size)

        provider.resume()
        clockMs.set(2_000L)
        assertTrue(secondSample.await(1, TimeUnit.SECONDS))
        provider.stop()

        assertEquals(listOf(1L, 2L), samples.map { it.seq })
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_duplicateOffsets_rejectsAmbiguousReplayOrder() {
        ReplayTelemetryProvider(listOf(frames().first(), frames().first()))
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
