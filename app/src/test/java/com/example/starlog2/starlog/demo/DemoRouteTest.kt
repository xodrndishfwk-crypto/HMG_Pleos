package com.example.starlog2.starlog.demo

import com.example.starlog2.contracts.Outcome
import com.example.starlog2.starlog.journey.ConstellationEdgeKind
import com.example.starlog2.starlog.journey.JourneyController
import com.example.starlog2.starlog.journey.JourneyControlResponse
import com.example.starlog2.starlog.journey.JourneyStatus
import com.example.starlog2.starlog.storage.TransactionalMemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoRouteTest {
    @Test
    fun mungyeongRouteMatchesTheVideoStoryboard() {
        val labels = DemoRoute.MUNGYEONG_SAEJAE.stops.map { it.label }
        assertEquals(listOf("대구 출발", "팔공산", "구미", "김천", "상주", "이화령 고개", "문경 새재"), labels)
        assertEquals("문경 새재자리", DemoRoute.MUNGYEONG_SAEJAE.constellationLabel)
    }

    @Test
    fun tripIdSelectsTheRouteItWasStartedWith() {
        val gumi = DemoRoute.GUMI_BUSAN
        val mungyeong = DemoRoute.MUNGYEONG_SAEJAE
        assertEquals(mungyeong, DemoRoute.forTrip("submission-mungyeong-1-abc", gumi))
        assertEquals(gumi, DemoRoute.forTrip("submission-gumi-busan-1-abc", mungyeong))
        assertEquals(mungyeong, DemoRoute.forTrip("submission-1794-legacy", mungyeong))
    }

    @Test
    fun mungyeongJourneyCompletesWithSevenStarsAndNoBranch() {
        val repository = TransactionalMemoryRepository()
        val route = DemoRoute.MUNGYEONG_SAEJAE
        var now = 1_800_000_000_000L
        val controller = JourneyController(
            repository = repository,
            nowMs = { now },
            tripIdFactory = { time -> "${route.tripIdPrefix()}$time-test" },
            route = route,
        )
        assertTrue(controller.start() is JourneyControlResponse.Success)
        repeat(route.stops.size) { assertTrue(controller.advance() is Outcome.Ok) }
        assertTrue(controller.advance() is Outcome.Err)
        now += 1_000L
        val ended = controller.end() as JourneyControlResponse.Success
        val record = ended.record!!
        assertEquals(JourneyStatus.COMPLETED, record.status)
        assertEquals(7, record.points.size)
        assertEquals(7, record.points.map { it.starId }.distinct().size)
        assertEquals(6, record.edges.count { it.kind == ConstellationEdgeKind.OUTBOUND })
        assertEquals(0, record.edges.count { it.kind == ConstellationEdgeKind.BRANCH })
        assertEquals(route, controller.routeOf(record))
    }
}
