package com.example.starlog2.starlog.melody

import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.Visit
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationMember
import kotlin.math.cos

/** Converts persisted StarLog data into the immutable input used by the melody generator. */
object MelodyInputProjector {
    fun project(
        constellation: Constellation,
        members: List<ConstellationMember>,
        storedStars: List<MemoryStar>,
        visits: List<Visit>,
    ): List<MelodyStar> {
        val starsById = storedStars.associateBy { it.id }
        val visitsById = visits.asSequence()
            .filter { it.sourceMode == constellation.sourceMode }
            .associateBy { it.id }
        val cosLatitude = cos(Math.toRadians(constellation.centerPoint.lat))

        return members.sortedBy { it.orderIndex }.mapNotNull { member ->
            val star = starsById[member.starId] ?: return@mapNotNull null
            val visit = visitsById[star.visitId]
            MelodyStar(
                starId = star.id,
                originalX = (star.point.lon - constellation.centerPoint.lon) * cosLatitude * METERS_PER_LON_DEGREE,
                originalY = -(star.point.lat - constellation.centerPoint.lat) * METERS_PER_LAT_DEGREE,
                connectionOrder = member.orderIndex,
                createdAtMs = star.createdAtMs,
                dwellTimeMs = visit?.let { it.endAtMs - it.startAtMs },
            )
        }
    }

    private const val METERS_PER_LON_DEGREE = 111_320.0
    private const val METERS_PER_LAT_DEGREE = 110_540.0
}
