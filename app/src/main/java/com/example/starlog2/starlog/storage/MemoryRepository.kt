package com.example.starlog2.starlog.storage

import com.example.starlog2.contracts.AppError
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.MemoryRepository
import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SessionCheckpoint
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Visit
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationMember
import com.example.starlog2.starlog.model.StarLogErrorCodes
import com.example.starlog2.starlog.melody.BaseMelody

/**
 * Extended domain storage interface inheriting canonical [com.example.starlog2.contracts.MemoryRepository].
 *
 * The 5 core contract methods (`saveVisit`, `listStars`, `deleteVisit`, `saveCheckpoint`, `loadCheckpoint`)
 * come directly from `com.example.starlog2.contracts.MemoryRepository`.
 * Additional S2/P1 constellation and visit query methods are provided here.
 */
interface StarLogMemoryRepository : MemoryRepository {
    fun listVisits(sourceMode: SourceMode): Outcome<List<Visit>> = Outcome.Ok(emptyList())

    fun saveConstellation(
        constellation: Constellation,
        members: List<ConstellationMember>,
    ): Outcome<Unit> = Outcome.Ok(Unit)

    fun listConstellations(
        sourceMode: SourceMode,
    ): Outcome<List<Pair<Constellation, List<ConstellationMember>>>> = Outcome.Ok(emptyList())

    fun saveBaseMelody(melody: BaseMelody): Outcome<Unit>

    fun loadBaseMelody(constellationId: String): Outcome<BaseMelody?>

    fun listBaseMelodies(): Outcome<List<BaseMelody>>
}

/**
 * Standard SQLite DDL and non-destructive migration definitions for StarLog domain persistence.
 * Strictly forbids `DROP TABLE` during schema upgrades to guarantee user data preservation.
 */
object StarLogDbSchema {
    const val DATABASE_NAME = "starlog_v1.db"
    const val DATABASE_VERSION_V1 = 1
    const val DATABASE_VERSION_V2 = 2
    const val DATABASE_VERSION_V3 = 3
    const val CURRENT_DATABASE_VERSION = DATABASE_VERSION_V3

    const val TABLE_VISITS = "visits"
    const val TABLE_MEMORY_STARS = "memory_stars"
    const val TABLE_CONSTELLATIONS = "constellations"
    const val TABLE_CONSTELLATION_MEMBERS = "constellation_members"
    const val TABLE_CHECKPOINTS = "checkpoints"
    const val TABLE_BASE_MELODIES = "base_melodies"

    const val DDL_CREATE_VISITS = """
        CREATE TABLE IF NOT EXISTS $TABLE_VISITS (
            id TEXT PRIMARY KEY,
            dedupe_key TEXT NOT NULL UNIQUE,
            trip_id TEXT,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            start_at_ms INTEGER NOT NULL,
            end_at_ms INTEGER NOT NULL,
            source_mode TEXT NOT NULL,
            label TEXT
        );
    """

    const val DDL_CREATE_MEMORY_STARS = """
        CREATE TABLE IF NOT EXISTS $TABLE_MEMORY_STARS (
            id TEXT PRIMARY KEY,
            visit_id TEXT NOT NULL UNIQUE,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            created_at_ms INTEGER NOT NULL,
            title TEXT,
            photo_ref TEXT,
            source_mode TEXT NOT NULL,
            FOREIGN KEY (visit_id) REFERENCES $TABLE_VISITS(id) ON DELETE CASCADE
        );
    """

    const val DDL_CREATE_CONSTELLATIONS = """
        CREATE TABLE IF NOT EXISTS $TABLE_CONSTELLATIONS (
            id TEXT PRIMARY KEY,
            kind TEXT NOT NULL,
            label TEXT,
            trip_id TEXT,
            center_lat REAL NOT NULL,
            center_lon REAL NOT NULL,
            created_at_ms INTEGER NOT NULL,
            source_mode TEXT NOT NULL
        );
    """

    const val DDL_CREATE_CONSTELLATION_MEMBERS = """
        CREATE TABLE IF NOT EXISTS $TABLE_CONSTELLATION_MEMBERS (
            constellation_id TEXT NOT NULL,
            star_id TEXT NOT NULL,
            order_index INTEGER NOT NULL,
            PRIMARY KEY (constellation_id, star_id),
            FOREIGN KEY (constellation_id) REFERENCES $TABLE_CONSTELLATIONS(id) ON DELETE CASCADE,
            FOREIGN KEY (star_id) REFERENCES $TABLE_MEMORY_STARS(id) ON DELETE CASCADE
        );
    """

    const val DDL_CREATE_CHECKPOINTS = """
        CREATE TABLE IF NOT EXISTS $TABLE_CHECKPOINTS (
            source_mode TEXT PRIMARY KEY,
            session_id TEXT NOT NULL,
            schema_version INTEGER NOT NULL,
            updated_at_ms INTEGER NOT NULL,
            payload_json TEXT
        );
    """

    const val DDL_CREATE_BASE_MELODIES = """
        CREATE TABLE IF NOT EXISTS $TABLE_BASE_MELODIES (
            id TEXT PRIMARY KEY,
            constellation_id TEXT NOT NULL UNIQUE,
            algorithm_version TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            FOREIGN KEY (constellation_id) REFERENCES $TABLE_CONSTELLATIONS(id) ON DELETE CASCADE
        );
    """

    const val INDEX_VISITS_DEDUPE = """
        CREATE UNIQUE INDEX IF NOT EXISTS idx_visits_dedupe_key ON $TABLE_VISITS(dedupe_key);
    """

    const val INDEX_STARS_SOURCE_MODE = """
        CREATE INDEX IF NOT EXISTS idx_stars_source_mode ON $TABLE_MEMORY_STARS(source_mode, created_at_ms);
    """

    fun createAllStatements(): List<String> = listOf(
        DDL_CREATE_VISITS,
        DDL_CREATE_MEMORY_STARS,
        DDL_CREATE_CONSTELLATIONS,
        DDL_CREATE_CONSTELLATION_MEMBERS,
        DDL_CREATE_CHECKPOINTS,
        DDL_CREATE_BASE_MELODIES,
        INDEX_VISITS_DEDUPE,
        INDEX_STARS_SOURCE_MODE,
    )

    /**
     * Returns non-destructive SQL statements to migrate from [oldVersion] to [newVersion].
     * Never drops existing tables or rows.
     */
    fun migrationStatements(oldVersion: Int, newVersion: Int): List<String> {
        require(oldVersion >= 1 && newVersion >= oldVersion) {
            "Invalid schema migration range: $oldVersion -> $newVersion"
        }
        val statements = mutableListOf<String>()
        if (oldVersion < DATABASE_VERSION_V2 && newVersion >= DATABASE_VERSION_V2) {
            statements.add(DDL_CREATE_CONSTELLATIONS)
            statements.add(DDL_CREATE_CONSTELLATION_MEMBERS)
            statements.add(INDEX_STARS_SOURCE_MODE)
        }
        if (oldVersion < DATABASE_VERSION_V3 && newVersion >= DATABASE_VERSION_V3) {
            statements.add(DDL_CREATE_BASE_MELODIES)
        }
        return statements
    }
}

/**
 * Persistent backing store snapshot for deterministic cross-restart and migration testing.
 */
data class StorageSnapshot(
    var schemaVersion: Int = StarLogDbSchema.DATABASE_VERSION_V1,
    val visitsById: LinkedHashMap<String, Visit> = LinkedHashMap(),
    val visitIdByDedupeKey: LinkedHashMap<String, String> = LinkedHashMap(),
    val starsById: LinkedHashMap<String, MemoryStar> = LinkedHashMap(),
    val starSourceModes: LinkedHashMap<String, SourceMode> = LinkedHashMap(),
    val constellationsById: LinkedHashMap<String, Constellation> = LinkedHashMap(),
    val membersByConstellationId: LinkedHashMap<String, List<ConstellationMember>> = LinkedHashMap(),
    val checkpointsByMode: LinkedHashMap<SourceMode, SessionCheckpoint> = LinkedHashMap(),
    val melodiesByConstellationId: LinkedHashMap<String, BaseMelody> = LinkedHashMap(),
) {
    fun deepCopy(): StorageSnapshot = StorageSnapshot(
        schemaVersion = schemaVersion,
        visitsById = LinkedHashMap(visitsById),
        visitIdByDedupeKey = LinkedHashMap(visitIdByDedupeKey),
        starsById = LinkedHashMap(starsById),
        starSourceModes = LinkedHashMap(starSourceModes),
        constellationsById = LinkedHashMap(constellationsById),
        membersByConstellationId = LinkedHashMap(membersByConstellationId.mapValues { it.value.toList() }),
        checkpointsByMode = LinkedHashMap(checkpointsByMode),
        melodiesByConstellationId = LinkedHashMap(melodiesByConstellationId),
    )
}

/**
 * ACID-transactional [MemoryRepository] implementation in pure Kotlin.
 *
 * Enforces identical invariants to [SqliteMemoryRepository]:
 * - Atomic transaction with full rollback on failure (`STORAGE_FAILED`)
 * - Unique `dedupeKey` constraint (idempotent upsert without duplicate rows)
 * - Foreign key cascade deletion (`Visit` -> `MemoryStar` -> `ConstellationMember`)
 * - Strict `SourceMode.LIVE` vs `SourceMode.REPLAY` namespace isolation
 * - Process restart persistence and non-destructive schema migration (`v1` -> `v2`)
 */
class TransactionalMemoryRepository(
    private val backingStore: StorageSnapshot = StorageSnapshot(
        schemaVersion = StarLogDbSchema.CURRENT_DATABASE_VERSION,
    ),
    targetSchemaVersion: Int = StarLogDbSchema.CURRENT_DATABASE_VERSION,
) : StarLogMemoryRepository {

    private var failNextWrite: Boolean = false

    init {
        if (backingStore.schemaVersion < targetSchemaVersion) {
            // Non-destructive migration preserving all existing rows
            StarLogDbSchema.migrationStatements(backingStore.schemaVersion, targetSchemaVersion)
            backingStore.schemaVersion = targetSchemaVersion
        }
    }

    fun currentSchemaVersion(): Int = backingStore.schemaVersion

    fun injectWriteFailureOnce() {
        failNextWrite = true
    }

    override fun saveVisit(visit: Visit): Outcome<MemoryStar> = executeInTransaction(
        errorMessageKey = "storage.save_visit_failed",
    ) { state ->
        val existingVisitId = state.visitIdByDedupeKey[visit.dedupeKey]
        if (existingVisitId == null && state.visitsById.containsKey(visit.id)) {
            throw IllegalStateException("Primary key conflict on Visit.id: ${visit.id}")
        }
        val canonicalVisitId = existingVisitId ?: visit.id
        val existingVisit = state.visitsById[canonicalVisitId]
        if (existingVisit != null && existingVisit.sourceMode != visit.sourceMode) {
            throw IllegalStateException(
                "SourceMode conflict for Visit $canonicalVisitId: ${existingVisit.sourceMode} vs ${visit.sourceMode}",
            )
        }

        val mergedVisit = if (existingVisit != null) {
            existingVisit.copy(
                endAtMs = maxOf(existingVisit.endAtMs, visit.endAtMs),
                tripId = visit.tripId ?: existingVisit.tripId,
                label = visit.label ?: existingVisit.label,
            )
        } else {
            visit.copy(id = canonicalVisitId)
        }

        state.visitsById[canonicalVisitId] = mergedVisit
        state.visitIdByDedupeKey[visit.dedupeKey] = canonicalVisitId

        val starId = "star-$canonicalVisitId"
        val existingStar = state.starsById[starId]
        if (existingStar != null && existingStar.visitId != canonicalVisitId) {
            throw IllegalStateException("Primary key conflict on MemoryStar.id: $starId")
        }
        val star = existingStar?.copy(
            title = mergedVisit.label ?: existingStar.title,
        ) ?: MemoryStar(
            id = starId,
            visitId = canonicalVisitId,
            point = LatLon(mergedVisit.point.lat, mergedVisit.point.lon),
            createdAtMs = mergedVisit.startAtMs,
            title = mergedVisit.label,
            photoRef = null,
        )

        state.starsById[starId] = star
        state.starSourceModes[starId] = mergedVisit.sourceMode
        star
    }

    override fun listStars(sourceMode: SourceMode): Outcome<List<MemoryStar>> = try {
        val list = backingStore.starsById.values
            .filter { star -> backingStore.starSourceModes[star.id] == sourceMode }
            .sortedWith(compareBy<MemoryStar> { it.createdAtMs }.thenBy { it.id })
        Outcome.Ok(list)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.list_stars_failed"))
    }

    override fun listVisits(sourceMode: SourceMode): Outcome<List<Visit>> = try {
        val list = backingStore.visitsById.values
            .filter { it.sourceMode == sourceMode }
            .sortedWith(compareBy<Visit> { it.startAtMs }.thenBy { it.id })
        Outcome.Ok(list)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.list_visits_failed"))
    }

    override fun deleteVisit(visitId: String): Outcome<Unit> = executeInTransaction { state ->
        val removedVisit = state.visitsById.remove(visitId)
        if (removedVisit != null) {
            state.visitIdByDedupeKey.remove(removedVisit.dedupeKey)
        }
        // Foreign key ON DELETE CASCADE to memory_stars and constellation_members
        val starsToDelete = state.starsById.values
            .filter { it.visitId == visitId }
            .map { it.id }
            .toSet()

        starsToDelete.forEach { starId ->
            state.starsById.remove(starId)
            state.starSourceModes.remove(starId)
        }

        if (starsToDelete.isNotEmpty()) {
            val updatedMembers = LinkedHashMap<String, List<ConstellationMember>>()
            for ((constellationId, members) in state.membersByConstellationId) {
                val remaining = members
                    .filter { it.starId !in starsToDelete }
                    .mapIndexed { index, member -> member.copy(orderIndex = index) }
                updatedMembers[constellationId] = remaining
            }
            state.membersByConstellationId.clear()
            state.membersByConstellationId.putAll(updatedMembers)
        }
    }

    override fun saveCheckpoint(checkpoint: SessionCheckpoint): Outcome<Unit> = executeInTransaction { state ->
        state.checkpointsByMode[checkpoint.sourceMode] = checkpoint
    }

    override fun loadCheckpoint(sourceMode: SourceMode): Outcome<SessionCheckpoint?> = try {
        Outcome.Ok(backingStore.checkpointsByMode[sourceMode])
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.load_checkpoint_failed"))
    }

    override fun saveConstellation(
        constellation: Constellation,
        members: List<ConstellationMember>,
    ): Outcome<Unit> = executeInTransaction { state ->
        require(constellation.id.isNotBlank()) { "Constellation.id must not be blank" }
        for (member in members) {
            require(member.constellationId == constellation.id) {
                "Member constellationId (${member.constellationId}) does not match (${constellation.id})"
            }
            require(state.starsById.containsKey(member.starId)) {
                "Foreign key violation: starId ${member.starId} does not exist"
            }
        }
        state.constellationsById[constellation.id] = constellation
        state.membersByConstellationId[constellation.id] = members.sortedBy { it.orderIndex }
    }

    override fun listConstellations(
        sourceMode: SourceMode,
    ): Outcome<List<Pair<Constellation, List<ConstellationMember>>>> = try {
        val result = backingStore.constellationsById.values
            .filter { it.sourceMode == sourceMode }
            .sortedWith(compareBy<Constellation> { it.createdAtMs }.thenBy { it.id })
            .map { constellation ->
                val members = backingStore.membersByConstellationId[constellation.id].orEmpty()
                constellation to members
            }
        Outcome.Ok(result)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.list_constellations_failed"))
    }

    override fun saveBaseMelody(melody: BaseMelody): Outcome<Unit> = executeInTransaction { state ->
        require(state.constellationsById.containsKey(melody.constellationId)) {
            "Foreign key violation: constellationId ${melody.constellationId} does not exist"
        }
        val existing = state.melodiesByConstellationId[melody.constellationId]
        require(existing == null || existing.id == melody.id) {
            "A confirmed base melody already exists for ${melody.constellationId}"
        }
        state.melodiesByConstellationId[melody.constellationId] = melody
    }

    override fun loadBaseMelody(constellationId: String): Outcome<BaseMelody?> = try {
        Outcome.Ok(backingStore.melodiesByConstellationId[constellationId])
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.load_base_melody_failed"))
    }

    override fun listBaseMelodies(): Outcome<List<BaseMelody>> = try {
        Outcome.Ok(backingStore.melodiesByConstellationId.values.sortedBy { it.id })
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.list_base_melodies_failed"))
    }

    private fun <T> executeInTransaction(
        errorMessageKey: String = "storage.transaction_failed",
        block: (StorageSnapshot) -> T,
    ): Outcome<T> {
        val workingCopy = backingStore.deepCopy()
        return try {
            if (failNextWrite) {
                failNextWrite = false
                throw IllegalStateException("Simulated SQLite write failure")
            }
            val result = block(workingCopy)
            commitSnapshot(workingCopy)
            Outcome.Ok(result)
        } catch (e: Exception) {
            Outcome.Err(storageError(errorMessageKey))
        }
    }

    private fun commitSnapshot(committed: StorageSnapshot) {
        backingStore.schemaVersion = committed.schemaVersion
        backingStore.visitsById.clear()
        backingStore.visitsById.putAll(committed.visitsById)
        backingStore.visitIdByDedupeKey.clear()
        backingStore.visitIdByDedupeKey.putAll(committed.visitIdByDedupeKey)
        backingStore.starsById.clear()
        backingStore.starsById.putAll(committed.starsById)
        backingStore.starSourceModes.clear()
        backingStore.starSourceModes.putAll(committed.starSourceModes)
        backingStore.constellationsById.clear()
        backingStore.constellationsById.putAll(committed.constellationsById)
        backingStore.membersByConstellationId.clear()
        backingStore.membersByConstellationId.putAll(committed.membersByConstellationId)
        backingStore.checkpointsByMode.clear()
        backingStore.checkpointsByMode.putAll(committed.checkpointsByMode)
        backingStore.melodiesByConstellationId.clear()
        backingStore.melodiesByConstellationId.putAll(committed.melodiesByConstellationId)
    }

    private fun storageError(messageKey: String): AppError = AppError(
        code = StarLogErrorCodes.STORAGE_FAILED,
        messageKey = messageKey,
        retryable = true,
        requestId = null,
    )
}
