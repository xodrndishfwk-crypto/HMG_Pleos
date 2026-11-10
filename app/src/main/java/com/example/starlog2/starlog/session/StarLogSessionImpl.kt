package com.example.starlog2.starlog.session

import com.example.starlog2.contracts.ExperienceCommand
import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SessionCheckpoint
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.StarLogSession
import com.example.starlog2.contracts.StarLogUpdate
import com.example.starlog2.contracts.Telemetry
import com.example.starlog2.contracts.Visit
import com.example.starlog2.starlog.bearing.BearingProjector
import com.example.starlog2.starlog.experience.ExperienceReducer
import com.example.starlog2.starlog.visit.VisitDetector

/**
 * Pure Kotlin implementation of canonical [com.example.starlog2.contracts.StarLogSession].
 * 0% Android SDK dependency.
 *
 * Coordinates [VisitDetector], [BearingProjector], and [ExperienceReducer] deterministically.
 * All IO/DB persistence is returned via [StarLogUpdate.completedVisits] for the Manager's
 * single IO queue to persist.
 */
class StarLogSessionImpl(
    private val experienceReducer: ExperienceReducer = ExperienceReducer(),
    private val bearingProjector: BearingProjector = BearingProjector(),
    private val visitDetector: VisitDetector = VisitDetector(),
    private val sessionId: String = "session-default",
    private val schemaVersion: Int = 1,
) : StarLogSession {

    private var activeSourceMode: SourceMode = SourceMode.LIVE

    override fun update(sample: Telemetry, memories: List<MemoryStar>): StarLogUpdate {
        activeSourceMode = sample.sourceMode

        val completedVisits = visitDetector.update(sample)
        val projectedSkyItems = bearingProjector.project(
            vehicleLocation = sample.location,
            memories = memories,
        )
        val candidateStarIds = projectedSkyItems.map { it.starId }
        val experience = experienceReducer.update(sample, candidateStarIds)

        val updatedSkyItems = projectedSkyItems.map { item ->
            item.copy(selected = (item.starId == experience.selectedStarId))
        }

        return StarLogUpdate(
            experience = experience,
            skyItems = updatedSkyItems,
            completedVisits = completedVisits,
        )
    }

    override fun finish(atMs: Long): List<Visit> = visitDetector.finish(atMs)

    override fun handleCommand(
        command: ExperienceCommand,
        sample: Telemetry,
        memories: List<MemoryStar>,
    ): Outcome<StarLogUpdate> {
        activeSourceMode = sample.sourceMode

        val projectedSkyItems = bearingProjector.project(
            vehicleLocation = sample.location,
            memories = memories,
        )
        val candidateIds = projectedSkyItems.map { it.starId }

        return when (val outcome = experienceReducer.handleCommand(command, sample, candidateIds)) {
            is Outcome.Ok -> {
                val updatedItems = projectedSkyItems.map { item ->
                    item.copy(selected = (item.starId == outcome.value.selectedStarId))
                }
                Outcome.Ok(
                    StarLogUpdate(
                        experience = outcome.value,
                        skyItems = updatedItems,
                        completedVisits = emptyList(),
                    ),
                )
            }
            is Outcome.Err -> outcome
        }
    }

    override fun restore(checkpoint: SessionCheckpoint): List<Visit> {
        activeSourceMode = checkpoint.sourceMode
        experienceReducer.reset()
        return visitDetector.restore(checkpoint)
    }

    override fun checkpoint(atMs: Long): SessionCheckpoint {
        val pendingVisit = visitDetector.currentQualifiedPendingVisit(atEpochMs = atMs)
        return SessionCheckpoint(
            schemaVersion = schemaVersion,
            sessionId = sessionId,
            sourceMode = activeSourceMode,
            updatedAtMs = atMs,
            qualifiedPendingVisit = pendingVisit,
        )
    }
}
