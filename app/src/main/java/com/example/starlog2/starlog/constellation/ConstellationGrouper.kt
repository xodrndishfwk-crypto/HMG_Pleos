package com.example.starlog2.starlog.constellation

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Visit
import com.example.starlog2.starlog.bearing.BearingProjector
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationKind
import com.example.starlog2.starlog.model.ConstellationMember
import com.example.starlog2.starlog.model.TripEdge

/**
 * Pure Kotlin Constellation & Trip Edge Builder.
 * 0% Android SDK dependency.
 *
 * Implements:
 * 1. Daily repeated visit clustering ([ConstellationKind.DAILY_CLUSTER]) using explicit labels
 *    or spatial proximity within `clusterRadiusM`.
 * 2. Ordered trip constellation and edge construction ([ConstellationKind.TRIP_ROUTE], [TripEdge])
 *    using explicit `tripId`s without unapproved automatic home/work/trip heuristics.
 */
object ConstellationGrouper {

    /**
     * Groups repeated visits within [clusterRadiusM] (at least [minVisitCount] visits) into
     * daily routine constellations ([ConstellationKind.DAILY_CLUSTER]).
     */
    fun groupDailyConstellations(
        visits: List<Visit>,
        stars: List<MemoryStar>,
        sourceMode: SourceMode = SourceMode.LIVE,
        clusterRadiusM: Double = 100.0,
        minVisitCount: Int = 2,
    ): List<Pair<Constellation, List<ConstellationMember>>> {
        val starByVisitId = stars.associateBy { it.visitId }
        val filteredVisits = visits
            .filter { it.sourceMode == sourceMode }
            .sortedWith(compareBy<Visit> { it.startAtMs }.thenBy { it.id })

        val assignedVisitIds = mutableSetOf<String>()
        val results = mutableListOf<Pair<Constellation, List<ConstellationMember>>>()

        for (seed in filteredVisits) {
            if (seed.id in assignedVisitIds) continue

            val clusterVisits = filteredVisits.filter { candidate ->
                candidate.id !in assignedVisitIds && (
                    (!seed.label.isNullOrBlank() && seed.label == candidate.label) ||
                        BearingProjector.distanceMeters(seed.point, candidate.point) <= clusterRadiusM
                    )
            }

            if (clusterVisits.size >= minVisitCount) {
                clusterVisits.forEach { assignedVisitIds.add(it.id) }

                val matchedStars = clusterVisits.mapNotNull { starByVisitId[it.id] }
                if (matchedStars.isEmpty()) continue

                val avgLat = clusterVisits.map { it.point.lat }.average()
                val avgLon = clusterVisits.map { it.point.lon }.average()
                val createdAtMs = clusterVisits.maxOf { it.endAtMs }
                val label = clusterVisits.firstNotNullOfOrNull { it.label }
                val constellationId = "constellation-daily-${seed.id}"

                val constellation = Constellation(
                    id = constellationId,
                    kind = ConstellationKind.DAILY_CLUSTER,
                    label = label,
                    tripId = null,
                    centerPoint = LatLon(avgLat, avgLon),
                    createdAtMs = createdAtMs,
                    sourceMode = sourceMode,
                )

                val members = matchedStars.mapIndexed { idx, star ->
                    ConstellationMember(
                        constellationId = constellationId,
                        starId = star.id,
                        orderIndex = idx,
                    )
                }

                results.add(constellation to members)
            }
        }

        return results
    }

    /**
     * Builds an ordered [ConstellationKind.TRIP_ROUTE] constellation for an explicit [tripId].
     */
    fun buildTripConstellation(
        tripId: String,
        visits: List<Visit>,
        stars: List<MemoryStar>,
        label: String? = null,
    ): Pair<Constellation, List<ConstellationMember>>? {
        if (tripId.isBlank()) return null

        val starByVisitId = stars.associateBy { it.visitId }
        val tripVisits = visits
            .filter { it.tripId == tripId }
            .sortedWith(compareBy<Visit> { it.startAtMs }.thenBy { it.id })

        val orderedStars = tripVisits.mapNotNull { starByVisitId[it.id] }
        if (orderedStars.isEmpty()) return null

        val avgLat = orderedStars.map { it.point.lat }.average()
        val avgLon = orderedStars.map { it.point.lon }.average()
        val firstVisit = tripVisits.first()
        val lastVisit = tripVisits.last()
        val constellationId = "constellation-trip-$tripId"

        val constellation = Constellation(
            id = constellationId,
            kind = ConstellationKind.TRIP_ROUTE,
            label = label ?: tripVisits.firstNotNullOfOrNull { it.label },
            tripId = tripId,
            centerPoint = LatLon(avgLat, avgLon),
            createdAtMs = lastVisit.endAtMs,
            sourceMode = firstVisit.sourceMode,
        )

        val members = orderedStars.mapIndexed { idx, star ->
            ConstellationMember(
                constellationId = constellationId,
                starId = star.id,
                orderIndex = idx,
            )
        }

        return constellation to members
    }

    /**
     * Builds sequential [TripEdge] segments connecting ordered stars for an explicit [tripId].
     */
    fun buildOrderedTripEdges(
        tripId: String,
        visits: List<Visit>,
        stars: List<MemoryStar>,
    ): List<TripEdge> {
        if (tripId.isBlank()) return emptyList()

        val starByVisitId = stars.associateBy { it.visitId }
        val orderedStars = visits
            .filter { it.tripId == tripId }
            .sortedWith(compareBy<Visit> { it.startAtMs }.thenBy { it.id })
            .mapNotNull { starByVisitId[it.id] }

        if (orderedStars.size < 2) return emptyList()

        return (0 until orderedStars.size - 1).map { idx ->
            val from = orderedStars[idx]
            val to = orderedStars[idx + 1]
            TripEdge(
                tripId = tripId,
                fromStarId = from.id,
                toStarId = to.id,
                orderIndex = idx,
                distanceM = BearingProjector.distanceMeters(from.point, to.point),
            )
        }
    }
}
