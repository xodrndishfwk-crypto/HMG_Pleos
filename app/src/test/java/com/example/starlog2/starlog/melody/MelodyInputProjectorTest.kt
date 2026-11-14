package com.example.starlog2.starlog.melody

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Visit
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationKind
import com.example.starlog2.starlog.model.ConstellationMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MelodyInputProjectorTest {
    @Test
    fun project_actualVisitPath_preservesMetadataAndDoesNotMixSourceModes() {
        val constellation = Constellation(
            id = "live-constellation",
            kind = ConstellationKind.TRIP_ROUTE,
            label = null,
            tripId = "trip",
            centerPoint = LatLon(37.5, 127.0),
            createdAtMs = 30_000L,
            sourceMode = SourceMode.LIVE,
        )
        val stars = listOf(
            MemoryStar("star-a", "visit-a", LatLon(37.5001, 127.0001), 11_000L, null, null),
            MemoryStar("star-b", "visit-b", LatLon(37.5002, 127.0002), 22_000L, null, null),
            MemoryStar("star-replay", "visit-replay", LatLon(37.5003, 127.0003), 33_000L, null, null),
        )
        val members = listOf(
            ConstellationMember(constellation.id, "star-b", 1),
            ConstellationMember(constellation.id, "star-a", 0),
            ConstellationMember(constellation.id, "star-replay", 2),
        )
        val visits = listOf(
            Visit("visit-a", "dedupe-a", "trip", stars[0].point, 1_000L, 8_000L, SourceMode.LIVE, null),
            Visit("visit-b", "dedupe-b", "trip", stars[1].point, 2_000L, 15_000L, SourceMode.LIVE, null),
            Visit("visit-replay", "dedupe-r", "trip", stars[2].point, 3_000L, 99_000L, SourceMode.REPLAY, null),
        )

        val projected = MelodyInputProjector.project(constellation, members, stars, visits)

        assertEquals(listOf("star-a", "star-b", "star-replay"), projected.map { it.starId })
        assertEquals(listOf(0, 1, 2), projected.map { it.connectionOrder })
        assertEquals(listOf(11_000L, 22_000L, 33_000L), projected.map { it.createdAtMs })
        assertEquals(7_000L, projected[0].dwellTimeMs)
        assertEquals(13_000L, projected[1].dwellTimeMs)
        assertNull(projected[2].dwellTimeMs)

        val notes = MelodyGenerator().preview(constellation.id, projected, 0).notes.sortedBy { it.connectionOrder }
        assertEquals(projected.map { it.starId }, notes.map { it.starId })
        assertEquals(projected.map { it.createdAtMs }, notes.map { it.createdAtMs })
        assertEquals(projected.map { it.dwellTimeMs }, notes.map { it.dwellTimeMs })
    }
}
