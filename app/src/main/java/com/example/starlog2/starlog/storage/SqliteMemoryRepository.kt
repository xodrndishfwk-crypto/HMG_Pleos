package com.example.starlog2.starlog.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.starlog2.contracts.AppError
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SessionCheckpoint
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Visit
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationKind
import com.example.starlog2.starlog.model.ConstellationMember
import com.example.starlog2.starlog.model.StarLogErrorCodes
import com.example.starlog2.starlog.melody.BaseMelody
import com.example.starlog2.starlog.music.AiMusicJob
import com.example.starlog2.starlog.music.AiMusicJson
import com.example.starlog2.starlog.music.AiMusicStatus
import org.json.JSONObject

/**
 * Android SQLite-backed implementation of canonical [com.example.starlog2.contracts.MemoryRepository]
 * using [SQLiteOpenHelper].
 *
 * Guarantees:
 * - Foreign key constraints enabled (`ON DELETE CASCADE`)
 * - Atomic `saveVisit` transaction with idempotent `dedupe_key` upsert
 * - `SourceMode.LIVE` vs `SourceMode.REPLAY` namespace isolation
 * - Non-destructive schema migration (`onUpgrade` never executes `DROP TABLE`)
 * - Explicit `STORAGE_FAILED` error mapping on SQLite failures
 */
class SqliteMemoryRepository(
    context: Context,
    databaseName: String? = StarLogDbSchema.DATABASE_NAME,
    databaseVersion: Int = StarLogDbSchema.CURRENT_DATABASE_VERSION,
) : SQLiteOpenHelper(context, databaseName, null, databaseVersion), StarLogMemoryRepository {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        for (sql in StarLogDbSchema.createAllStatements()) {
            db.execSQL(sql)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (sql in StarLogDbSchema.migrationStatements(oldVersion, newVersion)) {
            db.execSQL(sql)
        }
    }

    override fun saveVisit(visit: Visit): Outcome<MemoryStar> {
        val db = try {
            writableDatabase
        } catch (e: Exception) {
            return Outcome.Err(storageError("storage.open_failed"))
        }

        db.beginTransaction()
        return try {
            val existing = findVisitByDedupeKey(db, visit.dedupeKey)
            if (existing != null && existing.sourceMode != visit.sourceMode) {
                throw IllegalStateException(
                    "SourceMode conflict for Visit ${existing.id}: ${existing.sourceMode} vs ${visit.sourceMode}",
                )
            }
            val canonicalVisitId = existing?.id ?: visit.id
            val effectiveEndAtMs = if (existing != null) {
                maxOf(existing.endAtMs, visit.endAtMs)
            } else {
                visit.endAtMs
            }
            val effectiveTripId = visit.tripId ?: existing?.tripId
            val effectiveLabel = visit.label ?: existing?.label

            if (existing == null) {
                val visitValues = ContentValues().apply {
                    put("id", canonicalVisitId)
                    put("dedupe_key", visit.dedupeKey)
                    put("trip_id", effectiveTripId)
                    put("lat", visit.point.lat)
                    put("lon", visit.point.lon)
                    put("start_at_ms", visit.startAtMs)
                    put("end_at_ms", effectiveEndAtMs)
                    put("source_mode", visit.sourceMode.name)
                    put("label", effectiveLabel)
                }
                val rowId = db.insertOrThrow(StarLogDbSchema.TABLE_VISITS, null, visitValues)
                if (rowId == -1L) {
                    throw IllegalStateException("Failed to insert Visit $canonicalVisitId")
                }
            } else {
                val updateValues = ContentValues().apply {
                    put("end_at_ms", effectiveEndAtMs)
                    put("trip_id", effectiveTripId)
                    put("label", effectiveLabel)
                }
                db.update(
                    StarLogDbSchema.TABLE_VISITS,
                    updateValues,
                    "id = ?",
                    arrayOf(canonicalVisitId),
                )
            }

            val starId = "star-$canonicalVisitId"
            val existingStar = findStarByVisitId(db, canonicalVisitId)
            val star = if (existingStar == null) {
                val created = MemoryStar(
                    id = starId,
                    visitId = canonicalVisitId,
                    point = visit.point,
                    createdAtMs = visit.startAtMs,
                    title = effectiveLabel,
                    photoRef = null,
                )
                val starValues = ContentValues().apply {
                    put("id", created.id)
                    put("visit_id", created.visitId)
                    put("lat", created.point.lat)
                    put("lon", created.point.lon)
                    put("created_at_ms", created.createdAtMs)
                    put("title", created.title)
                    put("photo_ref", created.photoRef)
                    put("source_mode", visit.sourceMode.name)
                }
                val starRowId = db.insertOrThrow(StarLogDbSchema.TABLE_MEMORY_STARS, null, starValues)
                if (starRowId == -1L) {
                    throw IllegalStateException("Failed to insert MemoryStar $starId")
                }
                created
            } else {
                val updatedStar = existingStar.copy(
                    title = effectiveLabel ?: existingStar.title,
                )
                val starUpdate = ContentValues().apply {
                    put("title", updatedStar.title)
                }
                db.update(
                    StarLogDbSchema.TABLE_MEMORY_STARS,
                    starUpdate,
                    "id = ?",
                    arrayOf(existingStar.id),
                )
                updatedStar
            }

            db.setTransactionSuccessful()
            Outcome.Ok(star)
        } catch (e: Exception) {
            Outcome.Err(storageError("storage.save_visit_failed"))
        } finally {
            db.endTransaction()
        }
    }

    override fun listStars(sourceMode: SourceMode): Outcome<List<MemoryStar>> = try {
        val db = readableDatabase
        val stars = mutableListOf<MemoryStar>()
        db.query(
            StarLogDbSchema.TABLE_MEMORY_STARS,
            arrayOf("id", "visit_id", "lat", "lon", "created_at_ms", "title", "photo_ref"),
            "source_mode = ?",
            arrayOf(sourceMode.name),
            null,
            null,
            "created_at_ms ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                stars.add(
                    MemoryStar(
                        id = cursor.getString(0),
                        visitId = cursor.getString(1),
                        point = LatLon(cursor.getDouble(2), cursor.getDouble(3)),
                        createdAtMs = cursor.getLong(4),
                        title = if (cursor.isNull(5)) null else cursor.getString(5),
                        photoRef = if (cursor.isNull(6)) null else cursor.getString(6),
                    ),
                )
            }
        }
        Outcome.Ok(stars)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.list_stars_failed"))
    }

    override fun listVisits(sourceMode: SourceMode): Outcome<List<Visit>> = try {
        val db = readableDatabase
        val visits = mutableListOf<Visit>()
        db.query(
            StarLogDbSchema.TABLE_VISITS,
            arrayOf("id", "dedupe_key", "trip_id", "lat", "lon", "start_at_ms", "end_at_ms", "source_mode", "label"),
            "source_mode = ?",
            arrayOf(sourceMode.name),
            null,
            null,
            "start_at_ms ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                visits.add(
                    Visit(
                        id = cursor.getString(0),
                        dedupeKey = cursor.getString(1),
                        tripId = if (cursor.isNull(2)) null else cursor.getString(2),
                        point = LatLon(cursor.getDouble(3), cursor.getDouble(4)),
                        startAtMs = cursor.getLong(5),
                        endAtMs = cursor.getLong(6),
                        sourceMode = SourceMode.valueOf(cursor.getString(7)),
                        label = if (cursor.isNull(8)) null else cursor.getString(8),
                    ),
                )
            }
        }
        Outcome.Ok(visits)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.list_visits_failed"))
    }

    override fun deleteVisit(visitId: String): Outcome<Unit> {
        val db = try {
            writableDatabase
        } catch (e: Exception) {
            return Outcome.Err(storageError("storage.open_failed"))
        }

        db.beginTransaction()
        return try {
            db.delete(StarLogDbSchema.TABLE_VISITS, "id = ?", arrayOf(visitId))
            db.setTransactionSuccessful()
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Err(storageError("storage.delete_visit_failed"))
        } finally {
            db.endTransaction()
        }
    }

    override fun saveCheckpoint(checkpoint: SessionCheckpoint): Outcome<Unit> {
        val db = try {
            writableDatabase
        } catch (e: Exception) {
            return Outcome.Err(storageError("storage.open_failed"))
        }

        db.beginTransaction()
        return try {
            val payloadJson = checkpoint.qualifiedPendingVisit?.let { visit ->
                JSONObject().apply {
                    put("id", visit.id)
                    put("dedupeKey", visit.dedupeKey)
                    put("tripId", visit.tripId ?: JSONObject.NULL)
                    put("lat", visit.point.lat)
                    put("lon", visit.point.lon)
                    put("startAtMs", visit.startAtMs)
                    put("endAtMs", visit.endAtMs)
                    put("sourceMode", visit.sourceMode.name)
                    put("label", visit.label ?: JSONObject.NULL)
                }.toString()
            }

            val values = ContentValues().apply {
                put("source_mode", checkpoint.sourceMode.name)
                put("session_id", checkpoint.sessionId)
                put("schema_version", checkpoint.schemaVersion)
                put("updated_at_ms", checkpoint.updatedAtMs)
                put("payload_json", payloadJson)
            }

            val rowId = db.insertWithOnConflict(
                StarLogDbSchema.TABLE_CHECKPOINTS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            if (rowId == -1L) {
                throw IllegalStateException("Failed to upsert checkpoint")
            }

            db.setTransactionSuccessful()
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Err(storageError("storage.save_checkpoint_failed"))
        } finally {
            db.endTransaction()
        }
    }

    override fun loadCheckpoint(sourceMode: SourceMode): Outcome<SessionCheckpoint?> = try {
        val db = readableDatabase
        var result: SessionCheckpoint? = null
        db.query(
            StarLogDbSchema.TABLE_CHECKPOINTS,
            arrayOf("session_id", "schema_version", "updated_at_ms", "payload_json"),
            "source_mode = ?",
            arrayOf(sourceMode.name),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val sessionId = cursor.getString(0)
                val schemaVersion = cursor.getInt(1)
                val updatedAtMs = cursor.getLong(2)
                val payloadJson = if (cursor.isNull(3)) null else cursor.getString(3)
                val pendingVisit = payloadJson?.let { raw ->
                    val json = JSONObject(raw)
                    Visit(
                        id = json.getString("id"),
                        dedupeKey = json.getString("dedupeKey"),
                        tripId = if (json.isNull("tripId")) null else json.getString("tripId"),
                        point = LatLon(json.getDouble("lat"), json.getDouble("lon")),
                        startAtMs = json.getLong("startAtMs"),
                        endAtMs = json.getLong("endAtMs"),
                        sourceMode = SourceMode.valueOf(json.getString("sourceMode")),
                        label = if (json.isNull("label")) null else json.getString("label"),
                    )
                }
                result = SessionCheckpoint(
                    schemaVersion = schemaVersion,
                    sessionId = sessionId,
                    sourceMode = sourceMode,
                    updatedAtMs = updatedAtMs,
                    qualifiedPendingVisit = pendingVisit,
                )
            }
        }
        Outcome.Ok(result)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.load_checkpoint_failed"))
    }

    override fun saveConstellation(
        constellation: Constellation,
        members: List<ConstellationMember>,
    ): Outcome<Unit> {
        val db = try {
            writableDatabase
        } catch (e: Exception) {
            return Outcome.Err(storageError("storage.open_failed"))
        }

        db.beginTransaction()
        return try {
            val cValues = ContentValues().apply {
                put("id", constellation.id)
                put("kind", constellation.kind.name)
                put("label", constellation.label)
                put("trip_id", constellation.tripId)
                put("center_lat", constellation.centerPoint.lat)
                put("center_lon", constellation.centerPoint.lon)
                put("created_at_ms", constellation.createdAtMs)
                put("source_mode", constellation.sourceMode.name)
            }
            val cRow = db.insertWithOnConflict(
                StarLogDbSchema.TABLE_CONSTELLATIONS,
                null,
                cValues,
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            if (cRow == -1L) {
                val updated = db.update(
                    StarLogDbSchema.TABLE_CONSTELLATIONS,
                    cValues,
                    "id = ?",
                    arrayOf(constellation.id),
                )
                if (updated != 1) throw IllegalStateException("Failed to upsert Constellation ${constellation.id}")
            }

            db.delete(
                StarLogDbSchema.TABLE_CONSTELLATION_MEMBERS,
                "constellation_id = ?",
                arrayOf(constellation.id),
            )

            for (member in members.sortedBy { it.orderIndex }) {
                val mValues = ContentValues().apply {
                    put("constellation_id", constellation.id)
                    put("star_id", member.starId)
                    put("order_index", member.orderIndex)
                }
                val mRow = db.insertOrThrow(StarLogDbSchema.TABLE_CONSTELLATION_MEMBERS, null, mValues)
                if (mRow == -1L) {
                    throw IllegalStateException("Failed to insert ConstellationMember ${member.starId}")
                }
            }

            db.setTransactionSuccessful()
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Err(storageError("storage.save_constellation_failed"))
        } finally {
            db.endTransaction()
        }
    }

    override fun listConstellations(
        sourceMode: SourceMode,
    ): Outcome<List<Pair<Constellation, List<ConstellationMember>>>> = try {
        val db = readableDatabase
        val constellations = mutableListOf<Constellation>()
        db.query(
            StarLogDbSchema.TABLE_CONSTELLATIONS,
            arrayOf("id", "kind", "label", "trip_id", "center_lat", "center_lon", "created_at_ms", "source_mode"),
            "source_mode = ?",
            arrayOf(sourceMode.name),
            null,
            null,
            "created_at_ms ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                constellations.add(
                    Constellation(
                        id = cursor.getString(0),
                        kind = ConstellationKind.valueOf(cursor.getString(1)),
                        label = if (cursor.isNull(2)) null else cursor.getString(2),
                        tripId = if (cursor.isNull(3)) null else cursor.getString(3),
                        centerPoint = LatLon(cursor.getDouble(4), cursor.getDouble(5)),
                        createdAtMs = cursor.getLong(6),
                        sourceMode = SourceMode.valueOf(cursor.getString(7)),
                    ),
                )
            }
        }

        val results = constellations.map { constellation ->
            val members = mutableListOf<ConstellationMember>()
            db.query(
                StarLogDbSchema.TABLE_CONSTELLATION_MEMBERS,
                arrayOf("constellation_id", "star_id", "order_index"),
                "constellation_id = ?",
                arrayOf(constellation.id),
                null,
                null,
                "order_index ASC",
            ).use { mCursor ->
                while (mCursor.moveToNext()) {
                    members.add(
                        ConstellationMember(
                            constellationId = mCursor.getString(0),
                            starId = mCursor.getString(1),
                            orderIndex = mCursor.getInt(2),
                        ),
                    )
                }
            }
            constellation to members
        }
        Outcome.Ok(results)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.list_constellations_failed"))
    }

    override fun saveBaseMelody(melody: BaseMelody): Outcome<Unit> {
        val db = try { writableDatabase } catch (e: Exception) {
            return Outcome.Err(storageError("storage.open_failed"))
        }
        db.beginTransaction()
        return try {
            var existingId: String? = null
            db.query(
                StarLogDbSchema.TABLE_BASE_MELODIES,
                arrayOf("id"),
                "constellation_id = ?",
                arrayOf(melody.constellationId),
                null, null, null,
            ).use { cursor -> if (cursor.moveToFirst()) existingId = cursor.getString(0) }
            if (existingId != null && existingId != melody.id) {
                throw IllegalStateException("A confirmed base melody already exists for ${melody.constellationId}")
            }
            val values = ContentValues().apply {
                put("id", melody.id)
                put("constellation_id", melody.constellationId)
                put("algorithm_version", melody.algorithmVersion)
                put("payload_json", BaseMelodyJson.encode(melody))
            }
            if (existingId == null) {
                db.insertOrThrow(StarLogDbSchema.TABLE_BASE_MELODIES, null, values)
            } else {
                val updated = db.update(
                    StarLogDbSchema.TABLE_BASE_MELODIES,
                    values,
                    "id = ?",
                    arrayOf(melody.id),
                )
                if (updated != 1) throw IllegalStateException("Failed to update base melody")
            }
            db.setTransactionSuccessful()
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Err(storageError("storage.save_base_melody_failed"))
        } finally {
            db.endTransaction()
        }
    }

    override fun loadBaseMelody(constellationId: String): Outcome<BaseMelody?> = try {
        var value: BaseMelody? = null
        readableDatabase.query(
            StarLogDbSchema.TABLE_BASE_MELODIES,
            arrayOf("payload_json"),
            "constellation_id = ?",
            arrayOf(constellationId),
            null, null, null,
        ).use { cursor -> if (cursor.moveToFirst()) value = BaseMelodyJson.decode(cursor.getString(0)) }
        Outcome.Ok(value)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.load_base_melody_failed"))
    }

    override fun listBaseMelodies(): Outcome<List<BaseMelody>> = try {
        val values = mutableListOf<BaseMelody>()
        readableDatabase.query(
            StarLogDbSchema.TABLE_BASE_MELODIES,
            arrayOf("payload_json"),
            null, null, null, null,
            "id ASC",
        ).use { cursor -> while (cursor.moveToNext()) values += BaseMelodyJson.decode(cursor.getString(0)) }
        Outcome.Ok(values)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.list_base_melodies_failed"))
    }

    override fun saveAiMusicJob(job: AiMusicJob): Outcome<Unit> {
        val db = try { writableDatabase } catch (e: Exception) {
            return Outcome.Err(storageError("storage.open_failed"))
        }
        db.beginTransaction()
        return try {
            var confirmedMelodyId: String? = null
            db.query(
                StarLogDbSchema.TABLE_BASE_MELODIES,
                arrayOf("id"),
                "constellation_id = ?",
                arrayOf(job.request.constellationId),
                null, null, null,
            ).use { cursor -> if (cursor.moveToFirst()) confirmedMelodyId = cursor.getString(0) }
            require(confirmedMelodyId == job.request.baseMelodyId) {
                "AI music job must reference the confirmed base melody"
            }

            var existing: AiMusicJob? = null
            db.query(
                StarLogDbSchema.TABLE_AI_MUSIC_JOBS,
                arrayOf("payload_json"),
                "request_id = ?",
                arrayOf(job.request.requestId),
                null, null, null,
            ).use { cursor -> if (cursor.moveToFirst()) existing = AiMusicJson.decodeJob(cursor.getString(0)) }
            require(existing == null || existing?.request == job.request) {
                "AI music request identity conflict: ${job.request.requestId}"
            }
            require(existing?.status != AiMusicStatus.SUCCEEDED || job.status == AiMusicStatus.SUCCEEDED) {
                "A completed AI music job cannot regress"
            }

            val values = ContentValues().apply {
                put("request_id", job.request.requestId)
                put("provider_job_id", job.providerJobId)
                put("constellation_id", job.request.constellationId)
                put("source_mode", job.request.sourceMode.name)
                put("base_melody_id", job.request.baseMelodyId)
                put("status", job.status.name)
                put("updated_at_ms", job.updatedAtMs)
                put("payload_json", AiMusicJson.encodeJob(job))
            }
            db.insertWithOnConflict(
                StarLogDbSchema.TABLE_AI_MUSIC_JOBS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            ).also { if (it == -1L) throw IllegalStateException("Failed to save AI music job") }
            db.setTransactionSuccessful()
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Err(storageError("storage.save_ai_music_job_failed"))
        } finally {
            db.endTransaction()
        }
    }

    override fun loadAiMusicJob(requestId: String): Outcome<AiMusicJob?> = try {
        var value: AiMusicJob? = null
        readableDatabase.query(
            StarLogDbSchema.TABLE_AI_MUSIC_JOBS,
            arrayOf("payload_json"),
            "request_id = ?",
            arrayOf(requestId),
            null, null, null,
        ).use { cursor -> if (cursor.moveToFirst()) value = AiMusicJson.decodeJob(cursor.getString(0)) }
        Outcome.Ok(value)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.load_ai_music_job_failed"))
    }

    override fun listAiMusicJobs(constellationId: String): Outcome<List<AiMusicJob>> = try {
        val values = mutableListOf<AiMusicJob>()
        readableDatabase.query(
            StarLogDbSchema.TABLE_AI_MUSIC_JOBS,
            arrayOf("payload_json"),
            "constellation_id = ?",
            arrayOf(constellationId),
            null, null,
            "updated_at_ms DESC, request_id ASC",
        ).use { cursor -> while (cursor.moveToNext()) values += AiMusicJson.decodeJob(cursor.getString(0)) }
        Outcome.Ok(values)
    } catch (e: Exception) {
        Outcome.Err(storageError("storage.list_ai_music_jobs_failed"))
    }

    private fun findVisitByDedupeKey(db: SQLiteDatabase, dedupeKey: String): Visit? {
        db.query(
            StarLogDbSchema.TABLE_VISITS,
            arrayOf("id", "dedupe_key", "trip_id", "lat", "lon", "start_at_ms", "end_at_ms", "source_mode", "label"),
            "dedupe_key = ?",
            arrayOf(dedupeKey),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return Visit(
                    id = cursor.getString(0),
                    dedupeKey = cursor.getString(1),
                    tripId = if (cursor.isNull(2)) null else cursor.getString(2),
                    point = LatLon(cursor.getDouble(3), cursor.getDouble(4)),
                    startAtMs = cursor.getLong(5),
                    endAtMs = cursor.getLong(6),
                    sourceMode = SourceMode.valueOf(cursor.getString(7)),
                    label = if (cursor.isNull(8)) null else cursor.getString(8),
                )
            }
        }
        return null
    }

    private fun findStarByVisitId(db: SQLiteDatabase, visitId: String): MemoryStar? {
        db.query(
            StarLogDbSchema.TABLE_MEMORY_STARS,
            arrayOf("id", "visit_id", "lat", "lon", "created_at_ms", "title", "photo_ref"),
            "visit_id = ?",
            arrayOf(visitId),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return MemoryStar(
                    id = cursor.getString(0),
                    visitId = cursor.getString(1),
                    point = LatLon(cursor.getDouble(2), cursor.getDouble(3)),
                    createdAtMs = cursor.getLong(4),
                    title = if (cursor.isNull(5)) null else cursor.getString(5),
                    photoRef = if (cursor.isNull(6)) null else cursor.getString(6),
                )
            }
        }
        return null
    }

    private fun storageError(messageKey: String): AppError = AppError(
        code = StarLogErrorCodes.STORAGE_FAILED,
        messageKey = messageKey,
        retryable = true,
        requestId = null,
    )
}
