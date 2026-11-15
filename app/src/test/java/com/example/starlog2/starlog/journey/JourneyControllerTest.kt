package com.example.starlog2.starlog.journey

import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.demo.SubmissionDemoJourney
import com.example.starlog2.starlog.gleo.GleoJourneyCallbackData
import com.example.starlog2.starlog.storage.TransactionalMemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyControllerTest {
    @Test
    fun startStatusEnd_shareOneIdempotentRepositoryPipeline() {
        val repository = TransactionalMemoryRepository()
        var now = 10_000L
        var controller = JourneyController(repository, { now }, { "trip-$it" })

        val ready = controller.status()
        assertEquals("READY", ready.snapshot.journeyStatus)
        assertNull((repository.loadLatestJourney(SourceMode.REPLAY) as Outcome.Ok).value)

        val started = controller.start() as JourneyControlResponse.Success
        assertEquals("trip-10000", started.snapshot.tripId)
        assertEquals(JourneyStatus.ACTIVE.name, started.snapshot.journeyStatus)
        assertEquals(0, started.snapshot.recordedPointCount)
        assertEquals(SourceMode.REPLAY, started.snapshot.sourceMode)
        assertTrue((repository.listVisits(SourceMode.REPLAY) as Outcome.Ok).value.isEmpty())

        now += 1_000L
        val duplicateStart = controller.start() as JourneyControlResponse.Success
        assertEquals(started.record, duplicateStart.record)
        assertEquals(started.snapshot.tripId, duplicateStart.snapshot.tripId)

        val beforeStatus = (repository.loadJourney("trip-10000") as Outcome.Ok).value
        val status = controller.status() as JourneyControlResponse.Success
        val afterStatus = (repository.loadJourney("trip-10000") as Outcome.Ok).value
        assertEquals(beforeStatus, afterStatus)
        assertEquals(0, status.snapshot.recordedPointCount)

        val earlyEnd = controller.end()
        assertTrue(earlyEnd is JourneyControlResponse.Asking)
        assertEquals(JourneyStatus.ACTIVE, (repository.loadJourney("trip-10000") as Outcome.Ok).value?.status)
        assertTrue(controller.advance() is Outcome.Err)

        controller = JourneyController(repository, { now }, { "unused-$it" })
        assertEquals(JourneyStatus.ACTIVE.name, controller.status().snapshot.journeyStatus)
        assertTrue(controller.advance() is Outcome.Err)
        controller.start()
        repeat(SubmissionDemoJourney.STOPS.size) { assertTrue(controller.advance() is Outcome.Ok) }
        val completed = controller.end() as JourneyControlResponse.Success
        assertEquals(9, completed.snapshot.recordedPointCount)
        assertEquals(JourneyStatus.COMPLETED.name, completed.snapshot.journeyStatus)
        assertEquals("constellation-trip-trip-10000", completed.snapshot.constellationId)
        assertEquals(6, completed.record!!.points.map { it.starId }.distinct().size)
        assertEquals(4, completed.record.edges.count { it.kind == ConstellationEdgeKind.OUTBOUND })
        assertEquals(1, completed.record.edges.count { it.kind == ConstellationEdgeKind.BRANCH })

        val duplicateEnd = controller.end() as JourneyControlResponse.Success
        assertEquals(completed.record, duplicateEnd.record)
        assertEquals(completed.snapshot.constellationId, duplicateEnd.snapshot.constellationId)

        val callback = GleoJourneyCallbackData.from(duplicateEnd)
        assertEquals("trip-10000", callback["tripId"])
        assertEquals("COMPLETED", callback["journeyStatus"])
        assertEquals("REPLAY", callback["sourceMode"])
        assertEquals("9", callback["recordedPointCount"])
        assertEquals("constellation-trip-trip-10000", callback["constellationId"])
        assertTrue(callback.keys.none { it.contains("token", ignoreCase = true) || it.contains("backend", ignoreCase = true) })
    }

    @Test
    fun unsupportedLiveStart_hasNoSideEffect() {
        val repository = TransactionalMemoryRepository()
        val controller = JourneyController(repository, { 1L }, { "must-not-be-used" })

        assertTrue(controller.start(SourceMode.LIVE) is JourneyControlResponse.Failure)
        assertNull((repository.loadLatestJourney(SourceMode.LIVE) as Outcome.Ok).value)
        assertNull((repository.loadLatestJourney(SourceMode.REPLAY) as Outcome.Ok).value)
    }
}
