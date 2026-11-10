package com.example.starlog2.starlog

import com.example.starlog2.contracts.ExperienceAction
import com.example.starlog2.contracts.ExperienceCommand
import com.example.starlog2.contracts.ExperienceMode
import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.experience.ExperienceReducer
import com.example.starlog2.starlog.model.ExperienceConfig
import com.example.starlog2.starlog.model.StarLogErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceReducerTest {

    @Test
    fun update_stationary179999ms_remainsStopPending_matchingG1Fixture() {
        val reducer = ExperienceReducer()
        val s0 = StarLogFixtures.createTelemetry(
            seq = 0L,
            observedAtMs = 1_800_000_000_000L,
            monotonicMs = 0L,
            sourceMode = SourceMode.REPLAY,
            providerId = "g1-synthetic",
            point = LatLon(37.5, 127.0),
            headingDeg = 359.0,
            gear = Gear.P,
            speedMps = 0.0,
            locationAgeMs = 0L,
            speedAgeMs = 0L,
            gearAgeMs = 0L,
        )
        val res0 = reducer.update(s0, availableStarIds = listOf("star-1"))
        assertEquals(ExperienceMode.STOP_PENDING, res0.mode)
        assertNull(res0.entryEventId)

        val s1 = s0.copy(
            seq = 1L,
            observedAtMs = 1_800_000_179_999L,
            monotonicMs = 179_999L,
        )
        val resBeforeThreshold = reducer.update(s1, availableStarIds = listOf("star-1"))
        assertEquals(ExperienceMode.STOP_PENDING, resBeforeThreshold.mode)
        assertEquals(179_999L, resBeforeThreshold.stopElapsedMs)
        assertNull(resBeforeThreshold.entryEventId)
    }

    @Test
    fun update_stationary180000ms_entersMemoryFrameAndPreservesSingleEntryEventId_matchingG1Fixture() {
        val reducer = ExperienceReducer()
        val s0 = StarLogFixtures.createTelemetry(
            seq = 0L,
            observedAtMs = 1_800_000_000_000L,
            monotonicMs = 0L,
            sourceMode = SourceMode.REPLAY,
            providerId = "g1-synthetic",
            point = LatLon(37.5, 127.0),
            headingDeg = 359.0,
            gear = Gear.P,
            speedMps = 0.0,
            locationAgeMs = 0L,
            speedAgeMs = 0L,
            gearAgeMs = 0L,
        )
        reducer.update(s0, availableStarIds = listOf("star-1"))

        val s1 = s0.copy(
            seq = 1L,
            observedAtMs = 1_800_000_180_000L,
            monotonicMs = 180_000L,
        )
        val resAtThreshold = reducer.update(s1, availableStarIds = listOf("star-1"))
        assertEquals(ExperienceMode.MEMORY_FRAME, resAtThreshold.mode)
        assertEquals(180_000L, resAtThreshold.stopElapsedMs)
        assertNotNull(resAtThreshold.entryEventId)
        val firstEventId = resAtThreshold.entryEventId
        assertEquals("star-1", resAtThreshold.selectedStarId)

        // Subsequent stationary tick in MEMORY_FRAME must preserve the same entryEventId without re-issuing
        val s2 = s0.copy(
            seq = 2L,
            observedAtMs = 1_800_000_185_000L,
            monotonicMs = 185_000L,
        )
        val resAfter = reducer.update(s2, availableStarIds = listOf("star-1"))
        assertEquals(ExperienceMode.MEMORY_FRAME, resAfter.mode)
        assertEquals(firstEventId, resAfter.entryEventId)
    }

    @Test
    fun update_gearTransitions_pAndNQualify_dRUnknownReturnToDrivingSafeImmediately() {
        val reducer = ExperienceReducer()
        val t0 = 10_000L

        // Start in Gear.N stationary -> qualifies for MEMORY_FRAME at +180_000ms
        reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0, gear = Gear.N, speedMps = 0.2),
            availableStarIds = emptyList(),
        )
        val inMemory = reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 180_000L, gear = Gear.N, speedMps = 0.2),
            availableStarIds = emptyList(),
        )
        assertEquals(ExperienceMode.MEMORY_FRAME, inMemory.mode)

        // Shift to Gear.D even with speed 0.0 -> immediately DRIVING_SAFE
        val inDrive = reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 181_000L, gear = Gear.D, speedMps = 0.0),
            availableStarIds = emptyList(),
        )
        assertEquals(ExperienceMode.DRIVING_SAFE, inDrive.mode)

        // Re-qualify with Gear.P, then test Gear.R and Gear.UNKNOWN
        reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 200_000L, gear = Gear.P, speedMps = 0.0),
            availableStarIds = emptyList(),
        )
        val inMemoryP = reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 380_000L, gear = Gear.P, speedMps = 0.0),
            availableStarIds = emptyList(),
        )
        assertEquals(ExperienceMode.MEMORY_FRAME, inMemoryP.mode)

        val inReverse = reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 381_000L, gear = Gear.R, speedMps = 0.0),
            availableStarIds = emptyList(),
        )
        assertEquals(ExperienceMode.DRIVING_SAFE, inReverse.mode)

        reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 400_000L, gear = Gear.P, speedMps = 0.0),
            availableStarIds = emptyList(),
        )
        reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 580_000L, gear = Gear.P, speedMps = 0.0),
            availableStarIds = emptyList(),
        )
        val inUnknown = reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 581_000L, gear = Gear.UNKNOWN, speedMps = 0.0),
            availableStarIds = emptyList(),
        )
        assertEquals(ExperienceMode.DRIVING_SAFE, inUnknown.mode)
    }

    @Test
    fun update_speedBoundary_030mpsQualifies_031mpsResetsToDrivingSafe() {
        val reducer = ExperienceReducer()
        val t0 = 5_000L
        reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0, gear = Gear.P, speedMps = 0.30),
            availableStarIds = emptyList(),
        )
        val atThreshold = reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 180_000L, gear = Gear.P, speedMps = 0.30),
            availableStarIds = emptyList(),
        )
        assertEquals(ExperienceMode.MEMORY_FRAME, atThreshold.mode)

        val overSpeed = reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 181_000L, gear = Gear.P, speedMps = 0.31),
            availableStarIds = emptyList(),
        )
        assertEquals(ExperienceMode.DRIVING_SAFE, overSpeed.mode)
    }

    @Test
    fun update_staleTelemetryOrInvalidQuality_immediatelyReturnsDrivingSafe() {
        val reducer = ExperienceReducer()
        val t0 = 1_000L
        reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0), emptyList())
        val inMemory = reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0 + 180_000L), emptyList())
        assertEquals(ExperienceMode.MEMORY_FRAME, inMemory.mode)

        // Speed ageMs = 2001L (> 2000L maxTelemetryAgeMs)
        val staleSpeed = reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 181_000L, speedAgeMs = 2_001L),
            emptyList(),
        )
        assertEquals(ExperienceMode.DRIVING_SAFE, staleSpeed.mode)

        // Re-enter and test STALE gear quality
        reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0 + 200_000L), emptyList())
        val inMemory2 = reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0 + 380_000L), emptyList())
        assertEquals(ExperienceMode.MEMORY_FRAME, inMemory2.mode)

        val invalidGearQuality = reducer.update(
            StarLogFixtures.createTelemetry(monotonicMs = t0 + 381_000L, gearQuality = Quality.STALE),
            emptyList(),
        )
        assertEquals(ExperienceMode.DRIVING_SAFE, invalidGearQuality.mode)
    }

    @Test
    fun update_monotonicTimeRollbackOrExcessiveGap_resetsStopTimerSafely() {
        val reducer = ExperienceReducer(
            ExperienceConfig(maxSampleGapMs = 30_000L),
        )
        val t0 = 100_000L
        reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0), emptyList())

        // Rollback monotonicMs -> resets timer, stays STOP_PENDING with 0ms elapsed
        val rollback = reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0 - 500L), emptyList())
        assertEquals(ExperienceMode.STOP_PENDING, rollback.mode)
        assertEquals(0L, rollback.stopElapsedMs)

        // Excessive sample gap (+250_000ms single jump > 30_000ms maxSampleGapMs) -> restarts timer from new frame
        val hugeGap = reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0 + 250_000L), emptyList())
        assertEquals(ExperienceMode.STOP_PENDING, hugeGap.mode)
        assertEquals(0L, hugeGap.stopElapsedMs)

        // Advancing 180_000ms in valid increments (20_000ms per step <= 30_000ms) enters MEMORY_FRAME
        var currentMs = t0 + 250_000L
        for (step in 1..9) {
            currentMs += 20_000L
            val res = reducer.update(StarLogFixtures.createTelemetry(monotonicMs = currentMs), emptyList())
            if (step < 9) {
                assertEquals(ExperienceMode.STOP_PENDING, res.mode)
            } else {
                assertEquals(ExperienceMode.MEMORY_FRAME, res.mode)
            }
        }
    }

    @Test
    fun update_whenInRecapAndVehicleStartsMoving_forcesImmediateExitToDrivingSafe() {
        val reducer = ExperienceReducer()
        val t0 = 1_000L
        reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0), emptyList())
        val stoppedSample = StarLogFixtures.createTelemetry(monotonicMs = t0 + 180_000L)
        reducer.update(stoppedSample, emptyList())

        val openRecapResult = reducer.handleCommand(
            command = ExperienceCommand(action = ExperienceAction.OPEN_RECAP, starId = null),
            sample = stoppedSample,
            availableStarIds = emptyList(),
        )
        assertTrue(openRecapResult is Outcome.Ok)
        assertEquals(
            ExperienceMode.RECAP,
            (openRecapResult as Outcome.Ok).value.mode,
        )

        // Vehicle shifts to D and moves -> RECAP must be forcibly exited to DRIVING_SAFE
        val movingSample = StarLogFixtures.createTelemetry(
            monotonicMs = t0 + 181_000L,
            gear = Gear.D,
            speedMps = 3.5,
        )
        val afterMove = reducer.update(movingSample, emptyList())
        assertEquals(ExperienceMode.DRIVING_SAFE, afterMove.mode)
        assertNull(afterMove.selectedStarId)
    }

    @Test
    fun handleCommand_whileDriving_returnsInvalidStateError() {
        val reducer = ExperienceReducer()
        val drivingSample = StarLogFixtures.createTelemetry(
            monotonicMs = 1_000L,
            gear = Gear.D,
            speedMps = 10.0,
        )
        reducer.update(drivingSample, emptyList())

        val openResult = reducer.handleCommand(
            command = ExperienceCommand(action = ExperienceAction.OPEN_RECAP, starId = null),
            sample = drivingSample,
            availableStarIds = emptyList(),
        )
        assertTrue(openResult is Outcome.Err)
        assertEquals(StarLogErrorCodes.INVALID_STATE, (openResult as Outcome.Err).error.code)

        val selectResult = reducer.handleCommand(
            command = ExperienceCommand(action = ExperienceAction.SELECT_MEMORY, starId = "star-1"),
            sample = drivingSample,
            availableStarIds = listOf("star-1"),
        )
        assertTrue(selectResult is Outcome.Err)
        assertEquals(StarLogErrorCodes.INVALID_STATE, (selectResult as Outcome.Err).error.code)
    }

    @Test
    fun handleCommand_selectMemoryNotVisible_returnsInvalidInputError() {
        val reducer = ExperienceReducer()
        val t0 = 1_000L
        val visibleIds = listOf("star-visible-1", "star-visible-2")
        reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0), visibleIds)
        val sampleAt180s = StarLogFixtures.createTelemetry(monotonicMs = t0 + 180_000L)
        reducer.update(sampleAt180s, visibleIds)

        // Non-existent starId -> INVALID_INPUT
        val invalidTarget = reducer.handleCommand(
            command = ExperienceCommand(action = ExperienceAction.SELECT_MEMORY, starId = "star-hidden"),
            sample = sampleAt180s,
            availableStarIds = visibleIds,
        )
        assertTrue(invalidTarget is Outcome.Err)
        assertEquals(StarLogErrorCodes.INVALID_INPUT, (invalidTarget as Outcome.Err).error.code)

        // Valid starId -> updates selectedStarId
        val validTarget = reducer.handleCommand(
            command = ExperienceCommand(action = ExperienceAction.SELECT_MEMORY, starId = "star-visible-2"),
            sample = sampleAt180s,
            availableStarIds = visibleIds,
        )
        assertTrue(validTarget is Outcome.Ok)
        assertEquals("star-visible-2", (validTarget as Outcome.Ok).value.selectedStarId)
    }

    @Test
    fun handleCommand_openRecapWhenRecapUnsupported_returnsUnsupportedError() {
        val reducer = ExperienceReducer(
            config = ExperienceConfig(recapSupported = false),
        )
        val t0 = 1_000L
        reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0), emptyList())
        val sampleAt180s = StarLogFixtures.createTelemetry(monotonicMs = t0 + 180_000L)
        reducer.update(sampleAt180s, emptyList())

        val result = reducer.handleCommand(
            command = ExperienceCommand(action = ExperienceAction.OPEN_RECAP, starId = null),
            sample = sampleAt180s,
            availableStarIds = emptyList(),
        )
        assertTrue(result is Outcome.Err)
        assertEquals(StarLogErrorCodes.UNSUPPORTED, (result as Outcome.Err).error.code)
    }

    @Test
    fun handleCommand_closeRecapWhileStoppedAbove180s_returnsToMemoryFrame() {
        val reducer = ExperienceReducer()
        val t0 = 1_000L
        reducer.update(StarLogFixtures.createTelemetry(monotonicMs = t0), listOf("star-1"))
        val sampleAt180s = StarLogFixtures.createTelemetry(monotonicMs = t0 + 180_000L)
        reducer.update(sampleAt180s, listOf("star-1"))

        reducer.handleCommand(
            command = ExperienceCommand(action = ExperienceAction.OPEN_RECAP, starId = null),
            sample = sampleAt180s,
            availableStarIds = listOf("star-1"),
        )

        val closeResult = reducer.handleCommand(
            command = ExperienceCommand(action = ExperienceAction.CLOSE_RECAP, starId = null),
            sample = sampleAt180s,
            availableStarIds = listOf("star-1"),
        )
        assertTrue(closeResult is Outcome.Ok)
        assertEquals(ExperienceMode.MEMORY_FRAME, (closeResult as Outcome.Ok).value.mode)
    }
}
