package com.example.starlog2.integration

import com.example.starlog2.contracts.*
import com.example.starlog2.starlog.session.StarLogSessionImpl
import com.example.starlog2.starlog.storage.TransactionalMemoryRepository
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class StarLogCoordinatorTest {
    private class ManualIo : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>(); var ended = false
        override fun execute(command: Runnable) { check(!ended); tasks.addLast(command) }
        fun drain() { var count=0; while(tasks.isNotEmpty()) { check(count++<100); tasks.removeFirst().run() } }
        override fun shutdown() { ended=true }
        override fun shutdownNow() = tasks.toMutableList().also { tasks.clear(); shutdown() }
        override fun isShutdown() = ended
        override fun isTerminated() = ended && tasks.isEmpty()
        override fun awaitTermination(timeout:Long,unit:TimeUnit)=isTerminated
    }
    private class Harness {
        val clock=FakeClock(); val repo=TransactionalMemoryRepository(); val io=ManualIo()
        val coordinator=StarLogCoordinator(StarLogComponents(repo,{StarLogSessionImpl()},io),clock,{it()},{})
        fun tick(ms:Long, mode:SourceMode=SourceMode.REPLAY, gear:Gear=Gear.P, speed:Double=0.0) {
            clock.advance(ms-clock.monotonicMs())
            coordinator.telemetry(ContractSamples.telemetry(clock,ms,speed,gear).copy(sourceMode=mode))
        }
        fun begin(mode:SourceMode=SourceMode.REPLAY) { coordinator.begin(mode);io.drain() }
        fun state():RenderState=coordinator.decorate(RenderState(SourceMode.REPLAY,null,null,CameraConfig(76.0,17.7,310.0),null,
            ExperienceState(ExperienceMode.DRIVING_SAFE,null,0,null),emptyList(),null,null))
        fun seed(id:String,mode:SourceMode=SourceMode.REPLAY) {
            assertTrue(repo.saveVisit(Visit(id,id,null,LatLon(37.501,127.0),1,2,mode,null)) is Outcome.Ok)
        }
    }
    @Test fun stopBoundaryEmptyMemoryAndNullFieldsArePreserved() {
        val h=Harness();h.begin();h.tick(0);h.tick(179_999)
        assertEquals(ExperienceMode.STOP_PENDING,h.state().experience.mode)
        h.tick(180_000);assertEquals(ExperienceMode.MEMORY_FRAME,h.state().experience.mode)
        assertTrue(h.state().skyItems.isEmpty());assertNull(h.state().memory)
        h.coordinator.close();h.io.drain();assertTrue(h.io.ended)
    }
    @Test fun commandsSelectRecapAndDepartureRevalidatesState() {
        val h=Harness();h.seed("north");h.begin();h.tick(0);h.tick(180_000)
        assertEquals("star-north",h.state().memory!!.starId);assertNull(h.state().memory!!.title);assertNull(h.state().memory!!.photoRef)
        h.coordinator.command(ExperienceCommand(ExperienceAction.SELECT_MEMORY,"missing"))
        assertEquals("INVALID_INPUT",h.state().error!!.code)
        h.coordinator.command(ExperienceCommand(ExperienceAction.SELECT_MEMORY,"star-north"));assertNull(h.state().error)
        h.coordinator.command(ExperienceCommand(ExperienceAction.OPEN_RECAP,null));assertEquals(ExperienceMode.RECAP,h.state().experience.mode)
        h.coordinator.command(ExperienceCommand(ExperienceAction.CLOSE_RECAP,null));assertEquals(ExperienceMode.MEMORY_FRAME,h.state().experience.mode)
        h.tick(180_001,gear=Gear.D,speed=5.0);assertEquals(ExperienceMode.DRIVING_SAFE,h.state().experience.mode);assertNull(h.state().memory)
        h.coordinator.command(ExperienceCommand(ExperienceAction.OPEN_RECAP,null));assertEquals("INVALID_STATE",h.state().error!!.code)
        h.coordinator.close();h.io.drain()
    }
    @Test fun silentTelemetryAgesBeforeCommandsAndCannotOpenMemory() {
        val h=Harness();h.seed("north");h.begin();h.tick(0);h.tick(180_000);h.clock.advance(3_000)
        h.coordinator.command(ExperienceCommand(ExperienceAction.OPEN_RECAP,null))
        assertEquals("INVALID_STATE",h.state().error!!.code);assertEquals(ExperienceMode.DRIVING_SAFE,h.state().experience.mode)
        h.coordinator.close();h.io.drain()
    }
    @Test fun freshnessWatchdogHidesMemoryWithoutAnotherProviderTick() {
        val h=Harness();h.seed("north");h.begin();h.tick(0);h.tick(180_000)
        assertEquals(ExperienceMode.MEMORY_FRAME,h.state().experience.mode)
        h.clock.advance(2_001);h.coordinator.refresh()
        assertEquals(ExperienceMode.DRIVING_SAFE,h.state().experience.mode)
        assertNull(h.state().memory);assertEquals("STALE_DATA",h.state().error!!.code)
        h.coordinator.close();h.io.drain()
    }
    @Test fun qualifiedVisitWritesOnIoAndRestoreUpsertsWithoutInactiveTime() {
        val h=Harness();h.begin();h.tick(0);h.tick(600_000)
        assertTrue((h.repo.listStars(SourceMode.REPLAY) as Outcome.Ok).value.isEmpty())
        h.io.drain();assertEquals(1,(h.repo.listStars(SourceMode.REPLAY) as Outcome.Ok).value.size)
        h.tick(650_000);h.coordinator.suspend();h.io.drain()
        val cp=(h.repo.loadCheckpoint(SourceMode.REPLAY) as Outcome.Ok).value!!
        assertNotNull(cp.qualifiedPendingVisit);assertEquals(1_800_000_650_000L,cp.qualifiedPendingVisit!!.endAtMs)
        h.clock.advance(900_000);h.begin();h.tick(1_550_000);h.io.drain()
        assertEquals(0,h.state().experience.stopElapsedMs)
        assertEquals(1,(h.repo.listStars(SourceMode.REPLAY) as Outcome.Ok).value.size)
        h.coordinator.close();h.io.drain()
    }
    @Test fun storageFailureIsVisibleAndVisitRetriesOnNextTick() {
        val h=Harness();h.begin();h.tick(0);h.repo.injectWriteFailureOnce();h.tick(600_000);h.io.drain()
        assertEquals("STORAGE_FAILED",h.state().error!!.code)
        assertTrue((h.repo.listStars(SourceMode.REPLAY) as Outcome.Ok).value.isEmpty())
        h.tick(600_001);h.io.drain();assertNull(h.state().error)
        assertEquals(1,(h.repo.listStars(SourceMode.REPLAY) as Outcome.Ok).value.size)
        h.coordinator.close();h.io.drain()
    }
    @Test fun sourceSwitchDiscardsOldLoadAndQueriesOnlyActiveNamespace() {
        val h=Harness();h.seed("replay",SourceMode.REPLAY);h.seed("live",SourceMode.LIVE)
        h.coordinator.begin(SourceMode.REPLAY);h.coordinator.begin(SourceMode.LIVE);h.io.drain()
        h.tick(0,SourceMode.LIVE, Gear.D,10.0)
        assertEquals(listOf("star-live"),h.state().skyItems.map{it.starId})
        h.coordinator.close();h.io.drain()
    }
}
