package com.example.starlog2.starlog.demo

import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.MelodyInputProjector
import com.example.starlog2.starlog.storage.TransactionalMemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodyDemoReplaySeederTest {
    @Test
    fun explicitSeed_isIdempotentReplayOnlyAndProjectsRealRepositoryData() {
        val repository = TransactionalMemoryRepository()
        assertTrue(MelodyDemoReplaySeeder.seed(repository) is Outcome.Ok)
        assertTrue(MelodyDemoReplaySeeder.seed(repository) is Outcome.Ok)
        assertTrue((repository.listVisits(SourceMode.LIVE) as Outcome.Ok).value.isEmpty())
        assertTrue((repository.listStars(SourceMode.LIVE) as Outcome.Ok).value.isEmpty())

        val visits = (repository.listVisits(SourceMode.REPLAY) as Outcome.Ok).value
        val stars = (repository.listStars(SourceMode.REPLAY) as Outcome.Ok).value
        val (constellation, members) =
            (repository.listConstellations(SourceMode.REPLAY) as Outcome.Ok).value.single()
        val projected = MelodyInputProjector.project(constellation, members, stars, visits)

        assertEquals(MelodyDemoReplaySeeder.CONSTELLATION_ID, constellation.id)
        assertEquals(5, visits.size)
        assertEquals(5, stars.size)
        assertEquals(listOf(7_000L, 13_000L, 11_000L, 17_000L, 19_000L), projected.map { it.dwellTimeMs })
        assertEquals((0..4).toList(), projected.map { it.connectionOrder })
    }
}
