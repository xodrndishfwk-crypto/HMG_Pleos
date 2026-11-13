package com.example.starlog2.navigation

import com.example.starlog2.contracts.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DestinationConcurrencyTest {
    private val place=DestinationCandidate("p","place","address",null,LatLon(37.5,127.0))
    private val fix=TimedValue(LocationFix(LatLon(37.49,127.0),5.0,null,Reference.UNKNOWN),Quality.VALID,0)
    private fun route(id:String)=Route("r",id,listOf(fix.value!!.point,place.point),100.0,20.0,emptyList())
    private fun crossThread(block:()->Unit):Boolean {
        val finished=CountDownLatch(1)
        Thread({try {block()} finally {finished.countDown()}},"destination-cross-callback").apply{isDaemon=true;start()}
        return finished.await(2,TimeUnit.SECONDS)
    }
    @Test fun searchProviderCanWaitForCallbackOnAnotherThread() {
        var completedWithoutLock=false; var delivered=0
        val search=DestinationSearch(DestinationSearchProvider{_,done ->
            completedWithoutLock=crossThread{done(Outcome.Ok(listOf(place)))};CancelHandle{}
        })
        search.search("place"){delivered++}
        assertTrue("provider callback blocked on DestinationSearch lock",completedWithoutLock)
        assertEquals(1,delivered)
    }
    @Test fun routeProviderCanWaitForCallbackOnAnotherThread() {
        var completedWithoutLock=false;var delivered=0
        val routes=DestinationRoutes(object:RouteProvider {
            override fun request(input:RouteRequest,done:(Outcome<Route>)->Unit):CancelHandle {
                completedWithoutLock=crossThread{done(Outcome.Ok(route(input.requestId)))};return CancelHandle{}
            }
        },2_000)
        routes.request("a",place.point,fix){delivered++}
        assertTrue("provider callback blocked on DestinationRoutes lock",completedWithoutLock);assertEquals(1,delivered)
    }
    @Test fun cancellationHandleCanWaitForLateCallbackOnAnotherThread() {
        var cancellationCompleted=false
        val search=DestinationSearch(DestinationSearchProvider{_,done ->
            CancelHandle{cancellationCompleted=crossThread{done(Outcome.Ok(listOf(place)))}}
        })
        search.search("old"){fail("cancelled callback")};search.cancel()
        assertTrue("cancel called external code under lock",cancellationCompleted)
    }
    @Test fun consumerCanCancelFromAnotherThreadWithoutDeadlock() {
        lateinit var callback:(Outcome<List<DestinationCandidate>>)->Unit
        val search=DestinationSearch(DestinationSearchProvider{_,done ->callback=done;CancelHandle{}})
        var cancelled=false
        search.search("place"){cancelled=crossThread{search.cancel()}}
        callback(Outcome.Ok(listOf(place)))
        assertTrue("consumer called under lock",cancelled)
    }
    @Test fun invalidRequestIdIsReportedWithoutThrowingOrCallingProvider() {
        var calls=0;var result:Outcome<Route>?=null
        val routes=DestinationRoutes(object:RouteProvider{
            override fun request(input:RouteRequest,done:(Outcome<Route>)->Unit):CancelHandle{calls++;return CancelHandle{}}
        },2_000)
        routes.request(" ",place.point,fix){result=it}
        assertEquals("INVALID_INPUT",(result as Outcome.Err).error.code);assertEquals(0,calls)
    }
    @Test fun wrongRequestIdCannotAdoptUnrelatedRoute() {
        val routes=DestinationRoutes(object:RouteProvider{
            override fun request(input:RouteRequest,done:(Outcome<Route>)->Unit):CancelHandle{done(Outcome.Ok(route("other")));return CancelHandle{}}
        },2_000)
        var result:Outcome<Route>?=null;routes.request("current",place.point,fix){result=it}
        assertEquals("INVALID_RESPONSE",(result as Outcome.Err).error.code)
    }
    @Test fun providerStartupFailureIsDeliveredAsErrorOnce() {
        var count=0;var result:Outcome<List<DestinationCandidate>>?=null
        val search=DestinationSearch(DestinationSearchProvider{_,_->throw IllegalStateException("synthetic provider failure")})
        search.search("place"){result=it;count++}
        assertEquals(1,count);assertEquals("NETWORK_UNAVAILABLE",(result as Outcome.Err).error.code)
    }
    @Test fun cancelDuringProviderStartupCancelsHandleWhenItArrives() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val cancelDone=CountDownLatch(1)
        val handleCancelled=AtomicBoolean(false)
        val search=DestinationSearch(DestinationSearchProvider{_,_->entered.countDown();release.await(3,TimeUnit.SECONDS);CancelHandle{handleCancelled.set(true)}})
        val worker=Thread{search.search("place"){fail("must not deliver")}}.apply{isDaemon=true;start()}
        assertTrue(entered.await(2,TimeUnit.SECONDS))
        val cancelling=Thread{search.cancel();cancelDone.countDown()}.apply{isDaemon=true;start()}
        val returnedBeforeProvider=cancelDone.await(1,TimeUnit.SECONDS)
        release.countDown();worker.join(2_000);cancelling.join(2_000)
        assertTrue("cancel waits for provider under lock",returnedBeforeProvider);assertTrue(handleCancelled.get())
    }
    @Test fun closedSearchAndRouteBoundaryCannotStartNewProviderWork() {
        var searches=0;var routing=0
        val search=DestinationSearch(DestinationSearchProvider{_,_->searches++;CancelHandle{}})
        val routes=DestinationRoutes(object:RouteProvider{
            override fun request(input:RouteRequest,done:(Outcome<Route>)->Unit):CancelHandle{routing++;return CancelHandle{}}
        },2_000)
        search.close();search.close();routes.close();routes.close()
        search.search("closed"){fail("closed callback")};routes.request("closed",place.point,fix){fail("closed callback")}
        assertEquals(0,searches);assertEquals(0,routing)
    }
}
