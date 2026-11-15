package com.example.starlog2.starlog.journey

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.SourceMode

enum class JourneyStatus { ACTIVE, COMPLETED }

enum class JourneyLeg { OUTBOUND, RETURN, RETURN_NEW }

enum class ConstellationEdgeKind { OUTBOUND, BRANCH }

data class JourneyPoint(
    val journeyId: String,
    val orderIndex: Int,
    val label: String,
    val point: LatLon,
    val visitedAtMs: Long,
    val leg: JourneyLeg,
    val starId: String,
    val createsNewStar: Boolean,
)

data class StoredConstellationEdge(
    val constellationId: String,
    val orderIndex: Int,
    val fromStarId: String,
    val toStarId: String,
    val kind: ConstellationEdgeKind,
)

data class JourneyRecord(
    val id: String,
    val constellationId: String,
    val status: JourneyStatus,
    val sourceMode: SourceMode,
    val startedAtMs: Long,
    val completedAtMs: Long?,
    val points: List<JourneyPoint>,
    val edges: List<StoredConstellationEdge>,
)

data class StarPhoto(
    val id: String,
    val starId: String,
    val filePath: String,
    val createdAtMs: Long,
)
