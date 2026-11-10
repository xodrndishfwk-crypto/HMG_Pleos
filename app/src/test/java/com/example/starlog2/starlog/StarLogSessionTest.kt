package com.example.starlog2.starlog

import com.example.starlog2.contracts.ExperienceAction
import com.example.starlog2.contracts.ExperienceCommand
import com.example.starlog2.contracts.ExperienceMode
import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.constellation.ConstellationGrouper
import com.example.starlog2.starlog.model.ConstellationKind
import com.example.starlog2.starlog.model.toMemoryCard
import com.example.starlog2.starlog.session.StarLogSessionImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarLogSessionTest {

    @Test
    fun update_fullStationaryToVisitLifecycle_entersMemoryFrameAndCompletesVisit() {
        val session = StarLogSessionImpl()
        val t0 = 10_000L
        val e0 = 1_700_000_000_000L
        val candidateStars = listOf(
            StarLogFixtures.createMemoryStar(
                id = "star-north",
                point = StarLogFixtures.GYEONGBOKGUNG_SOUTH,
            ),
        )

        // Initial parked frame at SEOUL_CITY_HALL
        val u0 = session.update(
            sample = StarLogFixtures.createTelemetry(monotonicMs = t0, observedAtMs = e0, gear = Gear.P, speedMps = 0.0),
            memories = candidateStars,
        )
        assertEquals(ExperienceMode.STOP_PENDING, u0.experience.mode)
        assertTrue(u0.completedVisits.isEmpty())

        // Advance in 20s increments up to 180,000ms -> enters MEMORY_FRAME and selects star-north
        var currentMs = t0
        for (i in 1..9) {
            currentMs += 20_000L
            val update = session.update(
                sample = StarLogFixtures.createTelemetry(
                    seq = i.toLong() + 1L,
                    monotonicMs = currentMs,
                    observedAtMs = e0 + (currentMs - t0),
                    gear = Gear.P,
                    speedMps = 0.0,
                ),
                memories = candidateStars,
            )
            if (i == 9) {
                assertEquals(ExperienceMode.MEMORY_FRAME, update.experience.mode)
                assertNotNull(update.experience.entryEventId)
                assertEquals("star-north", update.experience.selectedStarId)
                assertTrue(update.skyItems.single().selected)
            }
        }

        // Continue stationary up to 600,000ms -> VisitDetector emits completedVisit
        while (currentMs < t0 + 600_000L) {
            currentMs += 20_000L
            val update = session.update(
                sample = StarLogFixtures.createTelemetry(
                    monotonicMs = currentMs,
                    observedAtMs = e0 + (currentMs - t0),
                    gear = Gear.P,
                    speedMps = 0.0,
                ),
                memories = candidateStars,
            )
            if (currentMs == t0 + 600_000L) {
                assertEquals(1, update.completedVisits.size)
                assertNull(update.completedVisits.single().label)
            }
        }
    }

    @Test
    fun checkpointAndRestore_preservesPendingQualifiedVisitAndResetsStopTimerForSafety() {
        val session1 = StarLogSessionImpl()
        val t0 = 10_000L
        val e0 = 1_700_000_000_000L

        // Reach 600,000ms qualified stay and enter MEMORY_FRAME
        var currentMs = t0
        session1.update(
            StarLogFixtures.createTelemetry(monotonicMs = currentMs, observedAtMs = e0, gear = Gear.P),
            emptyList(),
        )
        while (currentMs < t0 + 600_000L) {
            currentMs += 20_000L
            session1.update(
                StarLogFixtures.createTelemetry(
                    monotonicMs = currentMs,
                    observedAtMs = e0 + (currentMs - t0),
                    gear = Gear.P,
                ),
                emptyList(),
            )
        }

        val checkpoint = session1.checkpoint(atMs = e0 + 605_000L)
        assertNotNull(checkpoint.qualifiedPendingVisit)
        assertEquals(e0 + 605_000L, checkpoint.qualifiedPendingVisit!!.endAtMs)

        // Restore into a new session after app restart
        val session2 = StarLogSessionImpl()
        val restoredVisits = session2.restore(checkpoint)
        assertEquals(1, restoredVisits.size)
        assertEquals(checkpoint.qualifiedPendingVisit!!.dedupeKey, restoredVisits.single().dedupeKey)

        // First frame after restore must be STOP_PENDING with 0ms elapsed (180s timer reset on restore per spec)
        val afterRestoreUpdate = session2.update(
            StarLogFixtures.createTelemetry(
                monotonicMs = currentMs + 5_000L,
                observedAtMs = e0 + 605_000L,
                gear = Gear.P,
            ),
            emptyList(),
        )
        assertEquals(ExperienceMode.STOP_PENDING, afterRestoreUpdate.experience.mode)
        assertEquals(0L, afterRestoreUpdate.experience.stopElapsedMs)
    }

    @Test
    fun groupDailyConstellations_clustersNearbyRepeatedVisitsIntoDailyConstellation() {
        val visit1 = StarLogFixtures.createVisit(
            id = "visit-home-1",
            point = LatLon(37.5665, 126.9780),
            startAtMs = 1_700_000_000_000L,
            endAtMs = 1_700_000_600_000L,
        )
        val visit2 = StarLogFixtures.createVisit(
            id = "visit-home-2",
            point = LatLon(37.5666, 126.9780), // ~11m away (within 100m cluster radius)
            startAtMs = 1_700_086_400_000L,
            endAtMs = 1_700_087_000_000L,
        )
        val isolatedVisit = StarLogFixtures.createVisit(
            id = "visit-isolated",
            point = LatLon(37.6000, 127.0000), // > 3.7km away, only 1 visit -> not a daily constellation
            startAtMs = 1_700_090_000_000L,
            endAtMs = 1_700_090_600_000L,
        )

        val star1 = StarLogFixtures.createMemoryStar(id = "star-home-1", visitId = "visit-home-1", point = visit1.point)
        val star2 = StarLogFixtures.createMemoryStar(id = "star-home-2", visitId = "visit-home-2", point = visit2.point)
        val starIsolated = StarLogFixtures.createMemoryStar(
            id = "star-isolated",
            visitId = "visit-isolated",
            point = isolatedVisit.point,
        )

        val constellations = ConstellationGrouper.groupDailyConstellations(
            visits = listOf(visit2, visit1, isolatedVisit),
            stars = listOf(star2, star1, starIsolated),
            sourceMode = SourceMode.LIVE,
        )
        assertEquals(1, constellations.size)
        val (constellation, members) = constellations.single()
        assertEquals(ConstellationKind.DAILY_CLUSTER, constellation.kind)
        assertNull(constellation.label)
        assertEquals(listOf("star-home-1", "star-home-2"), members.map { it.starId })
        assertEquals(listOf(0, 1), members.map { it.orderIndex })
    }

    @Test
    fun buildOrderedTripEdges_connectsStarsSharingExplicitTripIdInTimeOrder() {
        val v1 = StarLogFixtures.createVisit(
            id = "v-trip-1",
            tripId = "gangwon-trip-2026",
            point = LatLon(37.5665, 126.9780),
            startAtMs = 1_700_000_000_000L,
            endAtMs = 1_700_000_600_000L,
        )
        val v2 = StarLogFixtures.createVisit(
            id = "v-trip-2",
            tripId = "gangwon-trip-2026",
            point = LatLon(37.5675, 126.9780),
            startAtMs = 1_700_010_000_000L,
            endAtMs = 1_700_010_600_000L,
        )
        val v3 = StarLogFixtures.createVisit(
            id = "v-trip-3",
            tripId = "gangwon-trip-2026",
            point = LatLon(37.5765, 126.9780),
            startAtMs = 1_700_020_000_000L,
            endAtMs = 1_700_020_600_000L,
        )
        val vOther = StarLogFixtures.createVisit(
            id = "v-other",
            tripId = "busan-trip",
            startAtMs = 1_700_015_000_000L,
            endAtMs = 1_700_015_600_000L,
        )

        val s1 = StarLogFixtures.createMemoryStar(id = "trip-stop-1", visitId = "v-trip-1", point = v1.point)
        val s2 = StarLogFixtures.createMemoryStar(id = "trip-stop-2", visitId = "v-trip-2", point = v2.point)
        val s3 = StarLogFixtures.createMemoryStar(id = "trip-stop-3", visitId = "v-trip-3", point = v3.point)
        val sOther = StarLogFixtures.createMemoryStar(id = "other-stop", visitId = "v-other", point = vOther.point)

        val edges = ConstellationGrouper.buildOrderedTripEdges(
            tripId = "gangwon-trip-2026",
            visits = listOf(v3, vOther, v1, v2),
            stars = listOf(s3, sOther, s1, s2),
        )
        assertEquals(2, edges.size)
        assertEquals("trip-stop-1", edges[0].fromStarId)
        assertEquals("trip-stop-2", edges[0].toStarId)
        assertEquals(0, edges[0].orderIndex)
        assertEquals("trip-stop-2", edges[1].fromStarId)
        assertEquals("trip-stop-3", edges[1].toStarId)
        assertEquals(1, edges[1].orderIndex)

        val tripConstellation = ConstellationGrouper.buildTripConstellation(
            tripId = "gangwon-trip-2026",
            visits = listOf(v3, v1, v2),
            stars = listOf(s3, s1, s2),
        )
        assertNotNull(tripConstellation)
        assertEquals(ConstellationKind.TRIP_ROUTE, tripConstellation!!.first.kind)
        assertEquals(3, tripConstellation.second.size)
    }

    @Test
    fun toMemoryCard_preservesNullFieldsWithoutGeneratingFakeDefaults() {
        val star = StarLogFixtures.createMemoryStar(
            id = "star-raw",
            title = null,
            photoRef = null,
        )
        val card = star.toMemoryCard(placeLabel = null)
        assertEquals("star-raw", card.starId)
        assertNull(card.title)
        assertNull(card.placeLabel)
        assertNull(card.photoRef)
    }

    @Test
    fun handleCommand_whileStoppedInMemoryFrame_transitionsToRecapAndClosesRecap() {
        val session = StarLogSessionImpl()
        val t0 = 10_000L
        var currentMs = t0
        session.update(StarLogFixtures.createTelemetry(monotonicMs = currentMs, gear = Gear.P), emptyList())
        for (i in 1..9) {
            currentMs += 20_000L
            session.update(StarLogFixtures.createTelemetry(monotonicMs = currentMs, gear = Gear.P), emptyList())
        }

        val sampleAt180s = StarLogFixtures.createTelemetry(monotonicMs = currentMs, gear = Gear.P)
        val recapRes = session.handleCommand(
            command = ExperienceCommand(action = ExperienceAction.OPEN_RECAP, starId = null),
            sample = sampleAt180s,
            memories = emptyList(),
        )
        assertTrue(recapRes is Outcome.Ok)
        assertEquals(ExperienceMode.RECAP, (recapRes as Outcome.Ok).value.experience.mode)

        val closeRes = session.handleCommand(
            command = ExperienceCommand(action = ExperienceAction.CLOSE_RECAP, starId = null),
            sample = sampleAt180s,
            memories = emptyList(),
        )
        assertTrue(closeRes is Outcome.Ok)
        assertEquals(ExperienceMode.MEMORY_FRAME, (closeRes as Outcome.Ok).value.experience.mode)
    }
}
