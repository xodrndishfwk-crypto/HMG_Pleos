package com.example.starlog2.starlog.demo

import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.journey.ConstellationEdgeKind
import com.example.starlog2.starlog.journey.JourneyLeg
import com.example.starlog2.starlog.journey.JourneyStatus
import com.example.starlog2.starlog.storage.TransactionalMemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmissionDemoJourneyTest {
    @Test
    fun submissionCycle_preservesAllDemoInvariantsThroughVisitDetectorAndRepository() {
        val repository = TransactionalMemoryRepository()
        val demo = SubmissionDemoJourney(repository)

        val started = demo.start()
        assertTrue(started is Outcome.Ok)
        assertTrue(demo.complete() is Outcome.Err)

        repeat(SubmissionDemoJourney.STOPS.size) {
            assertTrue("point $it must advance", demo.advance() is Outcome.Ok)
        }
        val completed = (demo.complete() as Outcome.Ok).value

        assertEquals(JourneyStatus.COMPLETED, completed.status)
        assertEquals(9, completed.points.size)
        assertEquals(5, completed.points.count { it.leg == JourneyLeg.OUTBOUND && it.createsNewStar })
        assertEquals(0, completed.points.count { it.leg == JourneyLeg.RETURN && it.createsNewStar })
        assertEquals(1, completed.points.count { it.leg == JourneyLeg.RETURN_NEW && it.createsNewStar })
        assertEquals(6, completed.points.map { it.starId }.distinct().size)
        assertEquals(4, completed.edges.count { it.kind == ConstellationEdgeKind.OUTBOUND })
        assertEquals(1, completed.edges.count { it.kind == ConstellationEdgeKind.BRANCH })
        assertEquals(SubmissionDemoJourney.TRIP_ID, completed.id)
        assertEquals(SubmissionDemoJourney.CONSTELLATION_ID, completed.constellationId)

        val visits = (repository.listVisits(SourceMode.REPLAY) as Outcome.Ok).value
        val constellations = (repository.listConstellations(SourceMode.REPLAY) as Outcome.Ok).value
        assertEquals(6, visits.size)
        assertTrue(visits.all { it.tripId == completed.id })
        assertEquals(completed.constellationId, constellations.single().first.id)
        assertEquals(completed.id, constellations.single().first.tripId)
        assertEquals(6, constellations.single().second.size)
    }

    @Test
    fun completedJourney_isIdempotentAndCannotRegressToActive() {
        val repository = TransactionalMemoryRepository()
        val demo = SubmissionDemoJourney(repository)
        demo.start()
        repeat(SubmissionDemoJourney.STOPS.size) { demo.advance() }

        val first = (demo.complete() as Outcome.Ok).value
        val second = (demo.complete(first.completedAtMs!! + 99_000L) as Outcome.Ok).value
        assertEquals(first, second)

        val regression = repository.saveJourney(first.copy(status = JourneyStatus.ACTIVE, completedAtMs = null))
        assertTrue(regression is Outcome.Err)
        assertEquals(first, (repository.loadJourney(first.id) as Outcome.Ok).value)
    }
}
