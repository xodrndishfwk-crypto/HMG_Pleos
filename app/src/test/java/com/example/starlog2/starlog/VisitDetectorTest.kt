package com.example.starlog2.starlog

import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.SessionCheckpoint
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.model.VisitConfig
import com.example.starlog2.starlog.visit.VisitDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitDetectorTest {

    @Test
    fun update_trafficSignalStopInGearNFor5Minutes_createsZeroVisits() {
        val detector = VisitDetector()
        val t0 = 10_000L
        val e0 = 1_700_000_000_000L
        detector.update(StarLogFixtures.createTelemetry(monotonicMs = t0, observedAtMs = e0, gear = Gear.N, speedMps = 0.0))
        val visits = detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 300_000L,
                observedAtMs = e0 + 300_000L,
                gear = Gear.N,
                speedMps = 0.0,
            ),
        )
        assertTrue(visits.isEmpty())
        assertTrue(detector.finish(e0 + 300_000L).isEmpty())
    }

    @Test
    fun update_gearDOrSpeedAbove030_createsZeroVisits() {
        val detector = VisitDetector()
        val t0 = 10_000L
        val e0 = 1_700_000_000_000L
        detector.update(StarLogFixtures.createTelemetry(monotonicMs = t0, observedAtMs = e0, gear = Gear.D, speedMps = 0.0))
        assertTrue(
            detector.update(
                StarLogFixtures.createTelemetry(
                    monotonicMs = t0 + 700_000L,
                    observedAtMs = e0 + 700_000L,
                    gear = Gear.D,
                    speedMps = 0.0,
                ),
            ).isEmpty(),
        )

        detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 800_000L,
                observedAtMs = e0 + 800_000L,
                gear = Gear.P,
                speedMps = 0.31,
            ),
        )
        assertTrue(
            detector.update(
                StarLogFixtures.createTelemetry(
                    monotonicMs = t0 + 1_500_000L,
                    observedAtMs = e0 + 1_500_000L,
                    gear = Gear.P,
                    speedMps = 0.31,
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun update_gearPStationary599999ms_doesNotQualifyUntil600000ms() {
        val detector = VisitDetector()
        val t0 = 10_000L
        val e0 = 1_700_000_000_000L

        assertTrue(
            detector.update(
                StarLogFixtures.createTelemetry(monotonicMs = t0, observedAtMs = e0, gear = Gear.P, speedMps = 0.0),
            ).isEmpty(),
        )
        // At 599,999ms -> still empty
        assertTrue(
            detector.update(
                StarLogFixtures.createTelemetry(
                    monotonicMs = t0 + 599_999L,
                    observedAtMs = e0 + 599_999L,
                    gear = Gear.P,
                    speedMps = 0.0,
                ),
            ).isEmpty(),
        )
        assertNull(detector.currentQualifiedPendingVisit())

        // At 600,000ms -> emits 1 Visit with deterministic dedupeKey
        val qualifiedList = detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 600_000L,
                observedAtMs = e0 + 600_000L,
                gear = Gear.P,
                speedMps = 0.0,
            ),
        )
        assertEquals(1, qualifiedList.size)
        val visit = qualifiedList.single()
        assertEquals(e0, visit.startAtMs)
        assertEquals(e0 + 600_000L, visit.endAtMs)
        assertTrue(visit.dedupeKey.startsWith("visit_LIVE_"))
    }

    @Test
    fun update_continuedStayAndDeparture_emitsUpdatedVisitWithSameDedupeKeyAndFinalEndAtMs() {
        val detector = VisitDetector()
        val t0 = 10_000L
        val e0 = 1_700_000_000_000L

        detector.update(StarLogFixtures.createTelemetry(monotonicMs = t0, observedAtMs = e0, gear = Gear.P))
        val firstEmit = detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 600_000L,
                observedAtMs = e0 + 600_000L,
                gear = Gear.P,
            ),
        ).single()

        // Stay continues at +900,000ms -> does not spam duplicate emissions while still parked
        val midStayEmit = detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 900_000L,
                observedAtMs = e0 + 900_000L,
                gear = Gear.P,
            ),
        )
        assertTrue(midStayEmit.isEmpty())

        // Vehicle shifts to D at +910,000ms (departure) -> emits finalized Visit with updated endAtMs = e0 + 900,000ms
        val departureEmit = detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 910_000L,
                observedAtMs = e0 + 910_000L,
                gear = Gear.D,
                speedMps = 2.0,
            ),
        ).single()

        assertEquals(firstEmit.id, departureEmit.id)
        assertEquals(firstEmit.dedupeKey, departureEmit.dedupeKey)
        assertEquals(e0 + 900_000L, departureEmit.endAtMs)
    }

    @Test
    fun update_poorGpsAccuracyOver30mOrMovementOver50m_resetsStay() {
        val detector = VisitDetector()
        val t0 = 10_000L
        val e0 = 1_700_000_000_000L

        detector.update(StarLogFixtures.createTelemetry(monotonicMs = t0, observedAtMs = e0, accuracyM = 10.0, gear = Gear.P))
        // Poor accuracy (35m > 30m) at +300,000ms resets stay
        detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 300_000L,
                observedAtMs = e0 + 300_000L,
                accuracyM = 35.0,
                gear = Gear.P,
            ),
        )
        // 300,000ms after reset is not enough for 600,000ms
        val afterPoorAccuracy = detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 600_000L,
                observedAtMs = e0 + 600_000L,
                accuracyM = 10.0,
                gear = Gear.P,
            ),
        )
        assertTrue(afterPoorAccuracy.isEmpty())

        // Movement > 50m (GYEONGBOKGUNG_SOUTH is ~111m away) resets stay and starts new anchor
        detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 900_000L,
                observedAtMs = e0 + 900_000L,
                point = StarLogFixtures.GYEONGBOKGUNG_SOUTH,
                accuracyM = 10.0,
                gear = Gear.P,
            ),
        )
        assertTrue(
            detector.update(
                StarLogFixtures.createTelemetry(
                    monotonicMs = t0 + 1_200_000L,
                    observedAtMs = e0 + 1_200_000L,
                    point = StarLogFixtures.GYEONGBOKGUNG_SOUTH,
                    accuracyM = 10.0,
                    gear = Gear.P,
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun update_monotonicTimeRollbackOrInactiveGap_resetsStayWithoutFalseQualification() {
        val detector = VisitDetector(
            VisitConfig(maxSampleGapMs = 60_000L),
        )
        val t0 = 100_000L
        detector.update(StarLogFixtures.createTelemetry(monotonicMs = t0, gear = Gear.P))
        detector.update(StarLogFixtures.createTelemetry(monotonicMs = t0 + 30_000L, gear = Gear.P))

        // Time rollback -> resets candidate
        assertTrue(detector.update(StarLogFixtures.createTelemetry(monotonicMs = t0 + 20_000L, gear = Gear.P)).isEmpty())

        // Huge inactive gap (+800_000ms single jump > 60_000ms maxSampleGapMs) -> starts new candidate, does not qualify
        assertTrue(detector.update(StarLogFixtures.createTelemetry(monotonicMs = t0 + 820_000L, gear = Gear.P)).isEmpty())
    }

    @Test
    fun finish_whenStayQualifiedAndExtended_returnsUpdatedFinalVisitWithSourceModePreserved() {
        val detector = VisitDetector()
        val t0 = 50_000L
        val e0 = 1_700_000_000_000L

        detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0,
                observedAtMs = e0,
                sourceMode = SourceMode.REPLAY,
                gear = Gear.P,
            ),
        )
        val firstVisit = detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 600_000L,
                observedAtMs = e0 + 600_000L,
                sourceMode = SourceMode.REPLAY,
                gear = Gear.P,
            ),
        ).single()
        assertEquals(SourceMode.REPLAY, firstVisit.sourceMode)

        detector.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = t0 + 750_000L,
                observedAtMs = e0 + 750_000L,
                sourceMode = SourceMode.REPLAY,
                gear = Gear.P,
            ),
        )
        val finishedVisit = detector.finish(atEpochMs = e0 + 750_000L).single()
        assertEquals(firstVisit.dedupeKey, finishedVisit.dedupeKey)
        assertEquals(e0 + 750_000L, finishedVisit.endAtMs)
        assertEquals(SourceMode.REPLAY, finishedVisit.sourceMode)
    }

    @Test
    fun restore_fromCheckpoint_returnsPendingVisitWithOriginalDedupeKeyAndResetsActiveTimer() {
        val detector = VisitDetector()
        val pending = StarLogFixtures.createVisit(
            id = "visit-restored",
            dedupeKey = "visit_LIVE_37.56650_126.97800_1700000000000",
        )
        val checkpoint = SessionCheckpoint(
            schemaVersion = 1,
            sessionId = "session-1",
            sourceMode = SourceMode.LIVE,
            updatedAtMs = 1_700_000_650_000L,
            qualifiedPendingVisit = pending,
        )

        val restoredVisits = detector.restore(checkpoint)
        assertEquals(1, restoredVisits.size)
        assertEquals("visit-restored", restoredVisits.single().id)
        assertEquals("visit_LIVE_37.56650_126.97800_1700000000000", restoredVisits.single().dedupeKey)
        assertNull(detector.currentQualifiedPendingVisit())
    }
}
