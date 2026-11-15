package com.example.starlog2

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.starlog2.contracts.*
import com.example.starlog2.starlog.SqliteMemoryRepositoryAndroidTest
import com.example.starlog2.starlog.session.StarLogSessionImpl
import com.example.starlog2.starlog.storage.SqliteMemoryRepository
import com.example.starlog2.starlog.storage.StarLogDbSchema
import com.example.starlog2.starlog.visit.VisitDetector
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StarLogAcceptanceInstrumentedTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private fun fixture(name:String)=i.context.assets.open("contracts/$name.json").bufferedReader().use{it.readText()}
    private fun sample(ms:Long,mode:SourceMode)=Telemetry(ms,1_800_000_000_000L+ms,ms,mode,"acceptance-${mode.name}",
        TimedValue(LocationFix(LatLon(37.5,127.0),5.0,0.0,Reference.TRUE_NORTH),Quality.VALID,0),
        TimedValue(0.0,Quality.VALID,0),TimedValue(Gear.P,Quality.VALID,0),TimedValue(true,Quality.VALID,0))
    @Test fun delegatedSqliteSuite() { SqliteMemoryRepositoryAndroidTest.verifyAll(i.targetContext) }
    @Test fun goldenStopBoundaryRunsActualDomainAndEmptyMemories() {
        for(n in listOf(179_999L,180_000L)) {
            val golden=JSONObject(fixture("telemetry-stop-$n")); val samples=golden.getJSONArray("samples")
            val session=StarLogSessionImpl()
            session.update(ContractJson.decodeTelemetry(samples.getJSONObject(0).toString()),emptyList())
            val update=session.update(ContractJson.decodeTelemetry(samples.getJSONObject(1).toString()),emptyList())
            assertEquals(golden.getJSONObject("expected").getString("mode"),update.experience.mode.name)
            assertEquals(n,update.experience.stopElapsedMs); assertTrue(update.skyItems.isEmpty())
        }
        assertEquals(0,JSONObject(fixture("memory-empty")).getJSONArray("stars").length())
    }
    @Test fun realV1MigrationPreservesRowsAndWriteFailureRollsBack() {
        val name="high-starlog-migration-test.db"; val context=i.targetContext
        context.deleteDatabase(name)
        try {
            context.openOrCreateDatabase(name,0,null).use { db ->
                db.execSQL(StarLogDbSchema.DDL_CREATE_VISITS); db.execSQL(StarLogDbSchema.DDL_CREATE_MEMORY_STARS)
                db.execSQL(StarLogDbSchema.DDL_CREATE_CHECKPOINTS)
                db.execSQL("INSERT INTO visits VALUES ('old','old',NULL,37.5,127,1,2,'LIVE',NULL)")
                db.version=1
            }
            SqliteMemoryRepository(context,name).use { repository ->
                val visits=(repository.listVisits(SourceMode.LIVE) as Outcome.Ok).value
                assertEquals("old",visits.single().id); assertNull(visits.single().label)
                assertEquals(2,repository.readableDatabase.version)
                repository.writableDatabase.execSQL("CREATE TRIGGER fail_star BEFORE INSERT ON memory_stars BEGIN SELECT RAISE(ABORT,'synthetic failure'); END")
                val result=repository.saveVisit(Visit("rollback","rollback",null,LatLon(37.5,127.0),3,4,SourceMode.LIVE,null))
                assertTrue(result is Outcome.Err)
                assertEquals("STORAGE_FAILED",(result as Outcome.Err).error.code)
                assertEquals(1,(repository.listVisits(SourceMode.LIVE) as Outcome.Ok).value.size)
            }
            SqliteMemoryRepository(context,name).use { assertEquals("old",(it.listVisits(SourceMode.LIVE) as Outcome.Ok).value.single().id) }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun liveAndReplayVisitsWithSameTimestampCanBothPersist() {
        val name="high-starlog-isolation-test.db"; val context=i.targetContext
        context.deleteDatabase(name)
        try { SqliteMemoryRepository(context,name).use { repository ->
            for(mode in listOf(SourceMode.LIVE,SourceMode.REPLAY)) {
                val detector=VisitDetector(); detector.update(sample(0,mode))
                val visit=detector.update(sample(600_000,mode)).single()
                val saved=repository.saveVisit(visit)
                assertTrue("$mode detector-produced Visit must persist separately: $saved",saved is Outcome.Ok)
            }
            assertEquals(1,(repository.listStars(SourceMode.LIVE) as Outcome.Ok).value.size)
            assertEquals(1,(repository.listStars(SourceMode.REPLAY) as Outcome.Ok).value.size)
        } } finally { context.deleteDatabase(name) }
    }
}
