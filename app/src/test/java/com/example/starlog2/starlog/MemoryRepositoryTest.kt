package com.example.starlog2.starlog

import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SessionCheckpoint
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationKind
import com.example.starlog2.starlog.model.ConstellationMember
import com.example.starlog2.starlog.model.StarLogErrorCodes
import com.example.starlog2.starlog.storage.StarLogDbSchema
import com.example.starlog2.starlog.storage.StorageSnapshot
import com.example.starlog2.starlog.storage.TransactionalMemoryRepository
import com.example.starlog2.starlog.visit.VisitDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryTest {

    @Test
    fun saveVisit_sameDedupeKeyTwice_upsertsSingleRowAndUpdatesEndTime() {
        val repo = TransactionalMemoryRepository()
        val v1 = StarLogFixtures.createVisit(
            id = "visit-1",
            dedupeKey = "visit_LIVE_37.56650_126.97800_1700000000000",
            startAtMs = 1_700_000_000_000L,
            endAtMs = 1_700_000_600_000L,
        )

        val res1 = repo.saveVisit(v1)
        assertTrue(res1 is Outcome.Ok)

        // Second save with identical dedupeKey (e.g. departure update or retry with a different generated id)
        val v2 = v1.copy(
            id = "visit-duplicate-attempt",
            endAtMs = 1_700_000_900_000L,
            label = "Updated Place Label",
        )
        val res2 = repo.saveVisit(v2)
        assertTrue(res2 is Outcome.Ok)

        val visits = (repo.listVisits(SourceMode.LIVE) as Outcome.Ok).value
        assertEquals(1, visits.size)
        assertEquals("visit-1", visits.single().id)
        assertEquals(1_700_000_900_000L, visits.single().endAtMs)
        assertEquals("Updated Place Label", visits.single().label)

        val stars = (repo.listStars(SourceMode.LIVE) as Outcome.Ok).value
        assertEquals(1, stars.size)
        assertEquals("star-visit-1", stars.single().id)
        assertEquals("Updated Place Label", stars.single().title)
    }

    @Test
    fun saveVisit_nullLabelAndPhotoRef_preservesNullWithoutFakeDefaults() {
        val repo = TransactionalMemoryRepository()
        val visit = StarLogFixtures.createVisit(label = null, tripId = null)

        val saveRes = repo.saveVisit(visit)
        assertTrue(saveRes is Outcome.Ok)

        val storedVisit = (repo.listVisits(SourceMode.LIVE) as Outcome.Ok).value.single()
        val storedStar = (repo.listStars(SourceMode.LIVE) as Outcome.Ok).value.single()

        assertNull(storedVisit.label)
        assertNull(storedVisit.tripId)
        assertNull(storedStar.title)
        assertNull(storedStar.photoRef)
    }

    @Test
    fun listVisitsAndListStars_liveAndReplaySourceModes_areStrictlyIsolated() {
        val repo = TransactionalMemoryRepository()

        val liveVisit = StarLogFixtures.createVisit(
            id = "visit-live",
            dedupeKey = "visit_LIVE_37.56650_126.97800_100",
            sourceMode = SourceMode.LIVE,
        )
        val replayVisit = StarLogFixtures.createVisit(
            id = "visit-replay",
            dedupeKey = "visit_REPLAY_37.56650_126.97800_100",
            sourceMode = SourceMode.REPLAY,
        )

        repo.saveVisit(liveVisit)
        repo.saveVisit(replayVisit)

        val liveVisits = (repo.listVisits(SourceMode.LIVE) as Outcome.Ok).value
        val replayVisits = (repo.listVisits(SourceMode.REPLAY) as Outcome.Ok).value
        val liveStars = (repo.listStars(SourceMode.LIVE) as Outcome.Ok).value
        val replayStars = (repo.listStars(SourceMode.REPLAY) as Outcome.Ok).value

        assertEquals(listOf("visit-live"), liveVisits.map { it.id })
        assertEquals(listOf("visit-replay"), replayVisits.map { it.id })
        assertEquals(listOf("star-visit-live"), liveStars.map { it.id })
        assertEquals(listOf("star-visit-replay"), replayStars.map { it.id })
    }

    @Test
    fun deleteVisit_cascadesToLinkedMemoryStarsAndConstellationMembers() {
        val repo = TransactionalMemoryRepository()
        val visit = StarLogFixtures.createVisit(id = "visit-cascade")
        val savedStar = (repo.saveVisit(visit) as Outcome.Ok).value

        val constellation = Constellation(
            id = "constellation-1",
            kind = ConstellationKind.DAILY_CLUSTER,
            label = null,
            tripId = null,
            centerPoint = StarLogFixtures.SEOUL_CITY_HALL,
            createdAtMs = 1_700_000_600_000L,
            sourceMode = SourceMode.LIVE,
        )
        val member = ConstellationMember(
            constellationId = "constellation-1",
            starId = savedStar.id,
            orderIndex = 0,
        )
        assertTrue(repo.saveConstellation(constellation, listOf(member)) is Outcome.Ok)

        val deleteResult = repo.deleteVisit("visit-cascade")
        assertTrue(deleteResult is Outcome.Ok)

        assertTrue((repo.listVisits(SourceMode.LIVE) as Outcome.Ok).value.isEmpty())
        assertTrue((repo.listStars(SourceMode.LIVE) as Outcome.Ok).value.isEmpty())
        val constellations = (repo.listConstellations(SourceMode.LIVE) as Outcome.Ok).value
        assertEquals(1, constellations.size)
        assertTrue(constellations.single().second.isEmpty())
    }

    @Test
    fun saveCheckpointAndLoadCheckpoint_persistsSessionStateAcrossRestart() {
        val backingStore = StorageSnapshot()
        val repo1 = TransactionalMemoryRepository(backingStore = backingStore)
        val pendingVisit = StarLogFixtures.createVisit(id = "visit-pending")
        val checkpoint = SessionCheckpoint(
            schemaVersion = 1,
            sessionId = "session-live",
            sourceMode = SourceMode.LIVE,
            updatedAtMs = 1_700_000_250_000L,
            qualifiedPendingVisit = pendingVisit,
        )

        val saveRes = repo1.saveCheckpoint(checkpoint)
        assertTrue(saveRes is Outcome.Ok)

        // Simulate app restart with the same persistent backing store
        val repo2 = TransactionalMemoryRepository(backingStore = backingStore)
        val loadRes = repo2.loadCheckpoint(SourceMode.LIVE)
        assertTrue(loadRes is Outcome.Ok)
        val loaded = (loadRes as Outcome.Ok).value
        assertNotNull(loaded)
        assertEquals("session-live", loaded!!.sessionId)
        assertEquals("visit-pending", loaded.qualifiedPendingVisit?.id)
    }

    @Test
    fun saveVisit_whenStorageFailureSimulated_rollsBackTransactionAndReturnsStorageFailed() {
        val repo = TransactionalMemoryRepository()
        val v1 = StarLogFixtures.createVisit(id = "visit-ok", dedupeKey = "visit_LIVE_1")
        repo.saveVisit(v1)

        repo.injectWriteFailureOnce()
        val failedVisit = StarLogFixtures.createVisit(id = "visit-fail", dedupeKey = "visit_LIVE_2")
        val res = repo.saveVisit(failedVisit)

        assertTrue(res is Outcome.Err)
        assertEquals(StarLogErrorCodes.STORAGE_FAILED, (res as Outcome.Err).error.code)

        val visits = (repo.listVisits(SourceMode.LIVE) as Outcome.Ok).value
        val stars = (repo.listStars(SourceMode.LIVE) as Outcome.Ok).value
        assertEquals(listOf("visit-ok"), visits.map { it.id })
        assertEquals(listOf("star-visit-ok"), stars.map { it.id })
    }

    @Test
    fun schemaMigration_v1ToV2_preservesExistingRowsWithoutDropTable() {
        val v1Snapshot = StorageSnapshot(schemaVersion = StarLogDbSchema.DATABASE_VERSION_V1)
        val repoV1 = TransactionalMemoryRepository(
            backingStore = v1Snapshot,
            targetSchemaVersion = StarLogDbSchema.DATABASE_VERSION_V1,
        )
        repoV1.saveVisit(
            StarLogFixtures.createVisit(id = "visit-v1", dedupeKey = "visit_LIVE_v1"),
        )
        assertEquals(StarLogDbSchema.DATABASE_VERSION_V1, repoV1.currentSchemaVersion())

        // Upgrade repository to v2 against the existing v1 snapshot
        val repoV2 = TransactionalMemoryRepository(
            backingStore = v1Snapshot,
            targetSchemaVersion = StarLogDbSchema.DATABASE_VERSION_V2,
        )
        assertEquals(StarLogDbSchema.DATABASE_VERSION_V2, repoV2.currentSchemaVersion())

        val visitsAfterMigration = (repoV2.listVisits(SourceMode.LIVE) as Outcome.Ok).value
        assertEquals(1, visitsAfterMigration.size)
        assertEquals("visit-v1", visitsAfterMigration.single().id)

        // Verify SQL schema helper never uses destructive DROP TABLE in migration statements
        val sqlStatements = StarLogDbSchema.migrationStatements(1, 2)
        assertTrue(sqlStatements.isNotEmpty())
        assertTrue(sqlStatements.none { it.uppercase().contains("DROP TABLE") })
    }

    @Test
    fun saveConstellation_withMissingStarForeignKey_returnsStorageFailedAndRollsBack() {
        val repo = TransactionalMemoryRepository()
        val constellation = Constellation(
            id = "constellation-invalid",
            kind = ConstellationKind.TRIP_ROUTE,
            label = null,
            tripId = "trip-1",
            centerPoint = StarLogFixtures.SEOUL_CITY_HALL,
            createdAtMs = 1_700_000_000_000L,
            sourceMode = SourceMode.LIVE,
        )
        val orphanMember = ConstellationMember(
            constellationId = "constellation-invalid",
            starId = "non-existent-star",
            orderIndex = 0,
        )

        val res = repo.saveConstellation(constellation, listOf(orphanMember))
        assertTrue(res is Outcome.Err)
        assertEquals(StarLogErrorCodes.STORAGE_FAILED, (res as Outcome.Err).error.code)
        assertTrue((repo.listConstellations(SourceMode.LIVE) as Outcome.Ok).value.isEmpty())
    }

    @Test
    fun saveVisit_liveAndReplayDetectorVisitsWithSameTimestamp_persistsQueriesAndDeletesIndependently() {
        val repo = TransactionalMemoryRepository()
        val e0 = 1_800_000_000_000L

        for (mode in listOf(SourceMode.LIVE, SourceMode.REPLAY)) {
            val detector = VisitDetector()
            detector.update(
                StarLogFixtures.createTelemetry(
                    monotonicMs = 0L,
                    observedAtMs = e0,
                    sourceMode = mode,
                    gear = Gear.P,
                    speedMps = 0.0,
                    accuracyM = 5.0,
                ),
            )
            val visit = detector.update(
                StarLogFixtures.createTelemetry(
                    monotonicMs = 600_000L,
                    observedAtMs = e0 + 600_000L,
                    sourceMode = mode,
                    gear = Gear.P,
                    speedMps = 0.0,
                    accuracyM = 5.0,
                ),
            ).single()
            val saved = repo.saveVisit(visit)
            assertTrue("$mode detector-produced Visit must persist separately: $saved", saved is Outcome.Ok)
        }

        val liveVisits = (repo.listVisits(SourceMode.LIVE) as Outcome.Ok).value
        val replayVisits = (repo.listVisits(SourceMode.REPLAY) as Outcome.Ok).value
        val liveStars = (repo.listStars(SourceMode.LIVE) as Outcome.Ok).value
        val replayStars = (repo.listStars(SourceMode.REPLAY) as Outcome.Ok).value

        assertEquals(1, liveVisits.size)
        assertEquals(1, replayVisits.size)
        assertEquals(1, liveStars.size)
        assertEquals(1, replayStars.size)

        // Deleting REPLAY visit must never remove LIVE visit or LIVE star
        assertTrue(repo.deleteVisit(replayVisits.single().id) is Outcome.Ok)
        assertTrue((repo.listVisits(SourceMode.REPLAY) as Outcome.Ok).value.isEmpty())
        assertTrue((repo.listStars(SourceMode.REPLAY) as Outcome.Ok).value.isEmpty())
        assertEquals(1, (repo.listVisits(SourceMode.LIVE) as Outcome.Ok).value.size)
        assertEquals(1, (repo.listStars(SourceMode.LIVE) as Outcome.Ok).value.size)
    }

    @Test
    fun saveVisit_sameIdWithDifferentDedupeKeyOrSourceMode_rejectsWithStorageFailedWithoutMutatingExistingLiveVisit() {
        val repo = TransactionalMemoryRepository()
        val liveVisit = StarLogFixtures.createVisit(
            id = "visit-shared-pk",
            dedupeKey = "visit_LIVE_37.56650_126.97800_1800000000000",
            sourceMode = SourceMode.LIVE,
            label = "Original Live Place",
        )
        assertTrue(repo.saveVisit(liveVisit) is Outcome.Ok)

        // Conflicting ID with a different dedupeKey and REPLAY sourceMode must fail like SQLite PK constraint
        val conflictingReplayVisit = StarLogFixtures.createVisit(
            id = "visit-shared-pk",
            dedupeKey = "visit_REPLAY_37.56650_126.97800_1800000000000",
            sourceMode = SourceMode.REPLAY,
            label = "Conflicting Replay Place",
        )
        val conflictResult = repo.saveVisit(conflictingReplayVisit)
        assertTrue(conflictResult is Outcome.Err)
        assertEquals(StarLogErrorCodes.STORAGE_FAILED, (conflictResult as Outcome.Err).error.code)
        assertEquals("storage.save_visit_failed", conflictResult.error.messageKey)

        // Existing LIVE visit and star must remain untouched, and REPLAY must remain empty
        val liveVisits = (repo.listVisits(SourceMode.LIVE) as Outcome.Ok).value
        val liveStars = (repo.listStars(SourceMode.LIVE) as Outcome.Ok).value
        assertEquals(1, liveVisits.size)
        assertEquals("Original Live Place", liveVisits.single().label)
        assertEquals(SourceMode.LIVE, liveVisits.single().sourceMode)
        assertEquals(1, liveStars.size)
        assertEquals("Original Live Place", liveStars.single().title)
        assertTrue((repo.listVisits(SourceMode.REPLAY) as Outcome.Ok).value.isEmpty())
        assertTrue((repo.listStars(SourceMode.REPLAY) as Outcome.Ok).value.isEmpty())
    }
}
