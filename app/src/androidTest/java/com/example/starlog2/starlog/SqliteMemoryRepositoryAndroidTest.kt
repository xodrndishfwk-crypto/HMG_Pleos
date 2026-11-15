package com.example.starlog2.starlog

import android.content.Context
import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.LocationFix
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.Reference
import com.example.starlog2.contracts.SessionCheckpoint
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Telemetry
import com.example.starlog2.contracts.TimedValue
import com.example.starlog2.contracts.Visit
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationKind
import com.example.starlog2.starlog.model.ConstellationMember
import com.example.starlog2.starlog.model.StarLogErrorCodes
import com.example.starlog2.starlog.storage.SqliteMemoryRepository
import com.example.starlog2.starlog.visit.VisitDetector

/**
 * Android SQLite 계측 검증 스위트 (HIGH_PC 에뮬레이터/단말 TEST_DELEGATION 실행용).
 *
 * 외부 테스트 러너 라이브러리 의존성 없이 Android SDK [Context]만으로 컴파일 및 실행 가능하도록 설계되었으며,
 * `HIGH_PC`에서 `connectedDebugAndroidTest` 실행 시 [verifyAll]을 호출하여 다음 5대 불변식을 검증한다:
 * 1. 동일 `dedupeKey` 2회 저장 시 단일 행 Upsert 보장
 * 2. `label`, `title`, `photoRef` null 값 보존 (가짜 기본값 생성 금지)
 * 3. `LIVE` / `REPLAY` sourceMode 데이터 격리
 * 4. `deleteVisit` 호출 시 외래키(FK Cascade)로 연결된 `MemoryStar` 및 `ConstellationMember` 연쇄 삭제
 * 5. `SessionCheckpoint` 저장 및 복구
 */
object SqliteMemoryRepositoryAndroidTest {

    private const val TEST_DB_NAME = "starlog_memory_instrumented_test.db"

    fun verifyAll(context: Context) {
        context.deleteDatabase(TEST_DB_NAME)
        val repository = SqliteMemoryRepository(context = context, databaseName = TEST_DB_NAME)
        try {
            verifyDedupeKeyUpsertAndNullPreservation(repository)
            verifyCascadeDeleteAndCheckpointPersistence(repository)
            verifyLiveAndReplaySameTimestampDetectorIsolationAndPkConflict(repository)
        } finally {
            repository.close()
            context.deleteDatabase(TEST_DB_NAME)
        }
    }

    private fun verifyDedupeKeyUpsertAndNullPreservation(repository: SqliteMemoryRepository) {
        val visit1 = Visit(
            id = "visit-1",
            dedupeKey = "visit_LIVE_37.56650_126.97800_1700000000000",
            tripId = null,
            point = LatLon(37.5665, 126.9780),
            startAtMs = 1_700_000_000_000L,
            endAtMs = 1_700_000_600_000L,
            sourceMode = SourceMode.LIVE,
            label = null,
        )
        check(repository.saveVisit(visit1) is Outcome.Ok) { "First saveVisit failed" }

        val visit2 = visit1.copy(
            id = "visit-retry",
            endAtMs = 1_700_000_900_000L,
        )
        check(repository.saveVisit(visit2) is Outcome.Ok) { "Second saveVisit upsert failed" }

        val visits = (repository.listVisits(SourceMode.LIVE) as Outcome.Ok).value
        check(visits.size == 1) { "Expected 1 upserted visit, found ${visits.size}" }
        check(visits.single().id == "visit-1") { "Expected canonical id visit-1" }
        check(visits.single().endAtMs == 1_700_000_900_000L) { "Expected updated endAtMs" }
        check(visits.single().label == null) { "Expected null label preserved" }

        val stars = (repository.listStars(SourceMode.LIVE) as Outcome.Ok).value
        check(stars.size == 1) { "Expected 1 upserted star, found ${stars.size}" }
        check(stars.single().title == null) { "Expected null star title preserved" }
        check(stars.single().photoRef == null) { "Expected null photoRef preserved" }
    }

    private fun verifyCascadeDeleteAndCheckpointPersistence(repository: SqliteMemoryRepository) {
        val visit = Visit(
            id = "visit-cascade",
            dedupeKey = "visit_LIVE_37.56650_126.97800_1700000000001",
            tripId = null,
            point = LatLon(37.5665, 126.9780),
            startAtMs = 1_700_000_000_000L,
            endAtMs = 1_700_000_600_000L,
            sourceMode = SourceMode.LIVE,
            label = null,
        )
        val star = (repository.saveVisit(visit) as Outcome.Ok).value

        val constellation = Constellation(
            id = "constellation-1",
            kind = ConstellationKind.DAILY_CLUSTER,
            label = null,
            tripId = null,
            centerPoint = LatLon(37.5665, 126.9780),
            createdAtMs = 1_700_000_600_000L,
            sourceMode = SourceMode.LIVE,
        )
        val member = ConstellationMember(
            constellationId = "constellation-1",
            starId = star.id,
            orderIndex = 0,
        )
        check(repository.saveConstellation(constellation, listOf(member)) is Outcome.Ok) {
            "saveConstellation failed"
        }

        val checkpoint = SessionCheckpoint(
            schemaVersion = 1,
            sessionId = "session-instrumented",
            sourceMode = SourceMode.LIVE,
            updatedAtMs = 1_700_000_615_000L,
            qualifiedPendingVisit = visit,
        )
        check(repository.saveCheckpoint(checkpoint) is Outcome.Ok) { "saveCheckpoint failed" }
        val loaded = (repository.loadCheckpoint(SourceMode.LIVE) as Outcome.Ok).value
        check(loaded != null && loaded.sessionId == "session-instrumented") {
            "loadCheckpoint failed"
        }

        check(repository.deleteVisit("visit-cascade") is Outcome.Ok) { "deleteVisit failed" }
        val remainingStars = (repository.listStars(SourceMode.LIVE) as Outcome.Ok).value
        check(remainingStars.none { it.id == star.id }) { "Expected star to be cascade deleted" }

        val constellations = (repository.listConstellations(SourceMode.LIVE) as Outcome.Ok).value
        check(constellations.single().second.isEmpty()) {
            "Expected constellation member to be cascade deleted"
        }
    }

    private fun verifyLiveAndReplaySameTimestampDetectorIsolationAndPkConflict(
        repository: SqliteMemoryRepository,
    ) {
        val epoch0 = 1_800_000_000_000L
        fun sample(ms: Long, mode: SourceMode): Telemetry = Telemetry(
            seq = ms + 1L,
            observedAtMs = epoch0 + ms,
            monotonicMs = ms,
            sourceMode = mode,
            providerId = "instrumented-${mode.name}",
            location = TimedValue(
                value = LocationFix(LatLon(37.5, 127.0), 5.0, 0.0, Reference.TRUE_NORTH),
                quality = Quality.VALID,
                ageMs = 0L,
            ),
            speedMps = TimedValue(0.0, Quality.VALID, 0L),
            gear = TimedValue(Gear.P, Quality.VALID, 0L),
            ignitionOn = TimedValue(true, Quality.VALID, 0L),
        )

        val liveDetector = VisitDetector()
        liveDetector.update(sample(0L, SourceMode.LIVE))
        val liveVisit = liveDetector.update(sample(600_000L, SourceMode.LIVE)).single()

        val replayDetector = VisitDetector()
        replayDetector.update(sample(0L, SourceMode.REPLAY))
        val replayVisit = replayDetector.update(sample(600_000L, SourceMode.REPLAY)).single()

        check(liveVisit.id != replayVisit.id) { "LIVE and REPLAY detector visit IDs must not collide" }
        check(repository.saveVisit(liveVisit) is Outcome.Ok) { "Saving LIVE detector visit failed" }
        check(repository.saveVisit(replayVisit) is Outcome.Ok) { "Saving REPLAY detector visit failed" }

        val liveStarsAfter = (repository.listStars(SourceMode.LIVE) as Outcome.Ok).value
        val replayStarsAfter = (repository.listStars(SourceMode.REPLAY) as Outcome.Ok).value
        check(liveStarsAfter.any { it.visitId == liveVisit.id }) { "Expected LIVE star persisted" }
        check(replayStarsAfter.any { it.visitId == replayVisit.id }) { "Expected REPLAY star persisted" }

        val conflictingPkVisit = replayVisit.copy(
            id = liveVisit.id,
            dedupeKey = "visit_REPLAY_37.50000_127.00000_9999999999999",
        )
        val conflictRes = repository.saveVisit(conflictingPkVisit)
        check(conflictRes is Outcome.Err && conflictRes.error.code == StarLogErrorCodes.STORAGE_FAILED) {
            "Expected PK conflict with different dedupeKey to return STORAGE_FAILED"
        }

        check(repository.deleteVisit(replayVisit.id) is Outcome.Ok) { "Deleting REPLAY visit failed" }
        check((repository.listStars(SourceMode.REPLAY) as Outcome.Ok).value.none { it.visitId == replayVisit.id }) {
            "Expected REPLAY star deleted"
        }
        check((repository.listStars(SourceMode.LIVE) as Outcome.Ok).value.any { it.visitId == liveVisit.id }) {
            "Expected LIVE star preserved after REPLAY deletion"
        }
    }
}
