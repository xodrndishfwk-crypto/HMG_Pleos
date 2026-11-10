package com.example.starlog2.contracts

data class Visit(
    val id: String, val dedupeKey: String, val tripId: String?, val point: LatLon,
    val startAtMs: Long, val endAtMs: Long, val sourceMode: SourceMode, val label: String?,
) {
    init {
        require(id.isNotBlank() && dedupeKey.isNotBlank())
        safeCounter(startAtMs); safeCounter(endAtMs); require(endAtMs >= startAtMs)
    }
}
data class MemoryStar(
    val id: String, val visitId: String, val point: LatLon, val createdAtMs: Long,
    val title: String?, val photoRef: String?,
) {
    init { require(id.isNotBlank() && visitId.isNotBlank()); safeCounter(createdAtMs) }
}
data class SkyItem(val starId: String, val relativeBearingDeg: Double, val distanceM: Double, val selected: Boolean) {
    init {
        require(starId.isNotBlank())
        require(relativeBearingDeg.isFinite() && relativeBearingDeg >= -180 && relativeBearingDeg < 180)
        nonNegative(distanceM)
    }
}
enum class ExperienceMode { DRIVING_SAFE, STOP_PENDING, MEMORY_FRAME, RECAP }
data class ExperienceState(
    val mode: ExperienceMode, val selectedStarId: String?, val stopElapsedMs: Long, val entryEventId: String?,
) { init { safeCounter(stopElapsedMs) } }
interface MemoryRepository {
    fun saveVisit(visit: Visit): Outcome<MemoryStar>
    fun listStars(sourceMode: SourceMode): Outcome<List<MemoryStar>>
    fun deleteVisit(visitId: String): Outcome<Unit>
    fun saveCheckpoint(checkpoint: SessionCheckpoint): Outcome<Unit>
    fun loadCheckpoint(sourceMode: SourceMode): Outcome<SessionCheckpoint?>
}
data class SessionCheckpoint(
    val schemaVersion: Int, val sessionId: String, val sourceMode: SourceMode,
    val updatedAtMs: Long, val qualifiedPendingVisit: Visit?,
) {
    init { require(schemaVersion == 1 && sessionId.isNotBlank()); safeCounter(updatedAtMs) }
}
data class StarLogUpdate(val experience: ExperienceState, val skyItems: List<SkyItem>, val completedVisits: List<Visit>)
interface StarLogSession {
    fun update(sample: Telemetry, memories: List<MemoryStar>): StarLogUpdate
    fun finish(atMs: Long): List<Visit>
    fun handleCommand(command: ExperienceCommand, sample: Telemetry, memories: List<MemoryStar>): Outcome<StarLogUpdate>
    fun restore(checkpoint: SessionCheckpoint): List<Visit>
    fun checkpoint(atMs: Long): SessionCheckpoint
}
enum class ExperienceAction { OPEN_RECAP, CLOSE_RECAP, SELECT_MEMORY }
data class ExperienceCommand(val action: ExperienceAction, val starId: String?) {
    init { require(action != ExperienceAction.SELECT_MEMORY || !starId.isNullOrBlank()) }
}
