package com.example.starlog2.starlog.demo

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Visit
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationKind
import com.example.starlog2.starlog.model.ConstellationMember
import com.example.starlog2.starlog.storage.StarLogMemoryRepository

/** Explicit, idempotent demo data. It is never created from an ordinary empty-state load. */
object MelodyDemoReplaySeeder {
    const val CONSTELLATION_ID = "demo-replay-melody-constellation-v1"

    fun seed(repository: StarLogMemoryRepository): Outcome<String> {
        val points = listOf(
            LatLon(37.56650, 126.97800),
            LatLon(37.56710, 126.97920),
            LatLon(37.56580, 126.98040),
            LatLon(37.56490, 126.97910),
            LatLon(37.56540, 126.97740),
        )
        val durations = listOf(7_000L, 13_000L, 11_000L, 17_000L, 19_000L)
        val stars = points.mapIndexed { index, point ->
            val start = BASE_TIME_MS + index * 60_000L
            val visit = Visit(
                id = "demo-replay-melody-visit-$index",
                dedupeKey = "demo_REPLAY_melody_v1_$index",
                tripId = "demo-replay-melody-trip-v1",
                point = point,
                startAtMs = start,
                endAtMs = start + durations[index],
                sourceMode = SourceMode.REPLAY,
                label = "Demo memory ${index + 1}",
            )
            when (val saved = repository.saveVisit(visit)) {
                is Outcome.Ok -> saved.value
                is Outcome.Err -> return saved
            }
        }
        val center = LatLon(points.map { it.lat }.average(), points.map { it.lon }.average())
        val constellation = Constellation(
            id = CONSTELLATION_ID,
            kind = ConstellationKind.TRIP_ROUTE,
            label = "Synthetic REPLAY · Melody demo",
            tripId = "demo-replay-melody-trip-v1",
            centerPoint = center,
            createdAtMs = BASE_TIME_MS + 10 * 60_000L,
            sourceMode = SourceMode.REPLAY,
        )
        return when (
            val saved = repository.saveConstellation(
                constellation,
                stars.mapIndexed { index, star -> ConstellationMember(CONSTELLATION_ID, star.id, index) },
            )
        ) {
            is Outcome.Ok -> Outcome.Ok(CONSTELLATION_ID)
            is Outcome.Err -> saved
        }
    }

    private const val BASE_TIME_MS = 1_800_000_000_000L
}
