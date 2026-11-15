package com.example.starlog2.integration

import com.example.starlog2.contracts.*
import com.example.starlog2.navigation.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class DestinationFlowTest {
    private class Immediate : AbstractExecutorService() {
        var ended=false
        override fun execute(command:Runnable){command.run()}
        override fun shutdown(){ended=true}
        override fun shutdownNow()= mutableListOf<Runnable>().also{shutdown()}
        override fun isShutdown()=ended
        override fun isTerminated()=ended
        override fun awaitTermination(timeout:Long,unit:TimeUnit)=ended
    }
    private class Search : DestinationSearchProvider,AutoCloseable {
        val callbacks=mutableListOf<(Outcome<List<DestinationCandidate>>)->Unit>();var cancels=0;var closed=false;var failClose=false
        override fun search(query:String,done:(Outcome<List<DestinationCandidate>>)->Unit):CancelHandle{
            callbacks+=done;return CancelHandle{cancels++}
        }
        override fun close(){closed=true;if(failClose)throw IllegalStateException("synthetic close failure")}
    }
    private class Routes : RouteProvider,AutoCloseable {
        val inputs=mutableListOf<RouteRequest>();val callbacks=mutableListOf<(Outcome<Route>)->Unit>();var cancels=0;var closed=false
        override fun request(input:RouteRequest,done:(Outcome<Route>)->Unit):CancelHandle{
            inputs+=input;callbacks+=done;return CancelHandle{cancels++}
        }
        fun complete(index:Int=inputs.lastIndex) {
            val q=inputs[index]
            callbacks[index](Outcome.Ok(Route("route-${q.requestId}",q.requestId,listOf(q.start,q.end),111.0,30.0,
                listOf(Maneuver(0,0,1,"직진",null,111.0,30.0)))))
        }
        override fun close(){closed=true}
    }
    private class Deadline : DestinationDeadline,AutoCloseable {
        val callbacks=mutableListOf<()->Unit>();var closed=false
        override fun schedule(delayMs:Long,task:()->Unit):CancelHandle{callbacks+=task;return CancelHandle{}}
        fun fire(index:Int=callbacks.lastIndex){callbacks[index]()}
        override fun close(){closed=true}
    }
    private class Harness(withSample:Boolean=true) {
        val clock=FakeClock();val core=Routes();val plans=Routes();val search=Search();val deadline=Deadline()
        val frames=mutableListOf<RenderEnvelope>();val states=mutableListOf<DestinationState>();val telemetry=FakeTelemetryProvider()
        val navigation=NavigationSession(core,DefaultGuidanceEngine(),clock,frames::add,queue=Immediate())
        val flow=DestinationFlow(navigation,search,plans,states::add,deadline=deadline)
        val place=DestinationCandidate("a","station","address",null,LatLon(37.501,127.0))
        init{navigation.attachTelemetry(telemetry,SourceMode.REPLAY);if(withSample)telemetry.emit(ContractSamples.telemetry(clock,gear=Gear.D,speed=10.0))}
        fun results(){flow.search("station");search.callbacks.last()(Outcome.Ok(listOf(place)))}
        fun plan(){results();flow.select(place.id);plans.complete()}
    }
    @Test fun selectedRouteStartsWithoutSecondNetworkRequestAndFinishedDeadlineCannotEraseIt() {
        val h=Harness();h.plan();assertEquals(DestinationPhase.READY_TO_START,h.states.last().phase)
        h.deadline.fire();assertEquals(DestinationPhase.READY_TO_START,h.states.last().phase)
        h.flow.startGuidance();h.flow.startGuidance()
        assertEquals(DestinationPhase.GUIDING,h.states.last().phase);assertEquals(1,h.plans.inputs.size);assertTrue(h.core.inputs.isEmpty())
        assertEquals(SourceMode.REPLAY,h.frames.last().payload.sourceMode)
        assertNotNull(h.frames.last().payload.guidance);assertEquals(listOf(127.0,37.5),h.frames.last().payload.route!!.coordinates.first())
        h.navigation.close()
    }
    @Test fun unavailableAndAgedLocationNeverCallsPlanningProvider() {
        val missing=Harness(false);missing.results();missing.flow.select("a")
        assertEquals("LOCATION_UNAVAILABLE",missing.states.last().error!!.code);assertTrue(missing.plans.inputs.isEmpty());missing.navigation.close()
        val stale=Harness();stale.clock.advance(2_001);stale.results();stale.flow.select("a")
        assertEquals("LOCATION_STALE",stale.states.last().error!!.code);assertTrue(stale.plans.inputs.isEmpty());stale.navigation.close()
    }
    @Test fun timeoutCancelsSearchAndLateResultCannotReviveIt() {
        val h=Harness();h.flow.search("pending");h.deadline.fire()
        assertEquals("NETWORK_TIMEOUT",h.states.last().error!!.code);assertEquals(1,h.search.cancels)
        h.search.callbacks.last()(Outcome.Ok(listOf(h.place)));assertEquals(DestinationPhase.ERROR,h.states.last().phase);h.navigation.close()
    }
    @Test fun planningTimeoutAndExplicitCancelRejectLateRoutes() {
        val h=Harness();h.results();h.flow.select("a");h.deadline.fire()
        assertEquals("NETWORK_TIMEOUT",h.states.last().error!!.code);h.plans.complete();assertEquals(DestinationPhase.ERROR,h.states.last().phase)
        h.results();h.flow.select("a");h.flow.cancelPending();h.plans.complete()
        assertEquals(DestinationPhase.CANCELLED,h.states.last().phase);assertNull(h.frames.last().payload.route);h.navigation.close()
    }
    @Test fun selectingAnotherDestinationRejectsFirstPlan() {
        val h=Harness();h.results();h.flow.select("a");h.flow.select("a");h.plans.complete(0)
        assertEquals(DestinationPhase.PLANNING,h.states.last().phase);h.plans.complete(1)
        assertEquals(h.plans.inputs[1].requestId,h.states.last().plannedRoute!!.requestId);assertEquals(1,h.plans.cancels);h.navigation.close()
    }
    @Test fun sourceGenerationChangeInvalidatesReadyRoute() {
        val h=Harness();h.plan();h.navigation.attachTelemetry(FakeTelemetryProvider(),SourceMode.REPLAY);h.flow.startGuidance()
        assertEquals("INVALID_STATE",h.states.last().error!!.code);assertNull(h.frames.last().payload.route);h.navigation.close()
    }
    @Test fun stopGuidanceClearsRouteButTelemetryCanContinue() {
        val h=Harness();h.plan();h.flow.startGuidance();h.flow.stopGuidance()
        assertNull(h.frames.last().payload.route);assertEquals(GuidanceStatus.IDLE,h.frames.last().payload.guidance!!.status)
        val count=h.frames.size;h.telemetry.emit(ContractSamples.telemetry(h.clock,1,10.0,Gear.D))
        assertTrue(h.frames.size>count);assertNotNull(h.frames.last().payload.pose);h.navigation.close()
    }
    @Test fun sessionCloseDisposesSearchPlanningAndTimerAndCannotEmitMoreState() {
        val h=Harness();h.flow.search("pending");h.navigation.close();val count=h.states.size
        h.search.callbacks.last()(Outcome.Ok(listOf(h.place)));h.deadline.fire();h.flow.search("closed")
        assertEquals(count,h.states.size);assertTrue(h.search.closed);assertTrue(h.plans.closed);assertTrue(h.deadline.closed)
    }
    @Test fun oneProviderCloseFailureCannotSkipOtherResourceCleanup() {
        val h=Harness();h.flow.search("pending");h.search.failClose=true;h.navigation.close()
        assertTrue(h.plans.closed);assertTrue(h.deadline.closed)
    }
    @Test fun independentSearchControllersNeverReusePlanningRequestIds() {
        val first=Harness();val second=Harness();first.plan();second.plan()
        assertNotEquals(first.plans.inputs.single().requestId,second.plans.inputs.single().requestId)
        first.navigation.close();second.navigation.close()
    }
}
