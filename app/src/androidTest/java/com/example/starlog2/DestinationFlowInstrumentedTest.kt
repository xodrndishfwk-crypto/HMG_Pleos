package com.example.starlog2

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.starlog2.contracts.*
import com.example.starlog2.integration.*
import com.example.starlog2.navigation.*
import com.example.starlog2.platform.AndroidClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DestinationFlowInstrumentedTest {
    @Before fun setup(){NavigationTestRoutes.install()}
    @After fun cleanup(){NavigationTestRoutes.restore()}
    private val i=InstrumentationRegistry.getInstrumentation()
    private fun walk(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{walk(v.getChildAt(it))}else emptyList()
    private fun await(label:String,condition:()->Boolean){
        val end=System.nanoTime()+35_000_000_000
        while(System.nanoTime()<end){if(condition())return;Thread.sleep(80)}
        fail("Timed out: $label")
    }
    private fun js(a:GlNavigationActivity,s:String):String{
        val output=AtomicReference<String?>()
        i.runOnMainSync{walk(a.window.decorView).filterIsInstance<WebView>().single().evaluateJavascript(s){output.set(it)}}
        await("JS"){output.get()!=null};return output.get()!!
    }
    private class Planning : RouteProvider,AutoCloseable {
        val requests=CopyOnWriteArrayList<RouteRequest>();val replies=CopyOnWriteArrayList<(Outcome<Route>)->Unit>()
        val closed=AtomicBoolean(false)
        override fun request(input:RouteRequest,done:(Outcome<Route>)->Unit):CancelHandle{requests+=input;replies+=done;return CancelHandle{}}
        override fun close(){closed.set(true)}
    }
    @Test fun selectionAdoptsOnceAndStopRemovesRealMapRouteWithoutStoppingTelemetry(){
        val route=ContractJson.decodeRoute(i.context.assets.open("contracts/route-valid.json").bufferedReader().use{it.readText()})
        val planning=Planning();val core=Planning();val telemetry=AtomicReference<((Telemetry)->Unit)?>(null)
        val pageState=AtomicReference<DestinationState?>();var flow:DestinationFlow?=null;var nav:NavigationSession?=null
        val activity=i.startActivitySync(Intent(i.targetContext,GlNavigationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GlNavigationActivity
        fun emit(seq:Long){
            telemetry.get()!!.invoke(Telemetry(seq,AndroidClock.wallTimeMs(),AndroidClock.monotonicMs(),SourceMode.REPLAY,"destination-instrument",
                TimedValue(LocationFix(route.points.first(),5.0,0.0,Reference.TRUE_NORTH),Quality.VALID,0),
                TimedValue(10.0,Quality.VALID,0),TimedValue(Gear.D,Quality.VALID,0),TimedValue(true,Quality.VALID,0)))
        }
        try{
            await("map ready"){js(activity,"typeof window.starlog==='object' && map.isStyleLoaded()") == "true"}
            // Let the Activity's original bootstrap finish before swapping its test controller.
            // Otherwise onMapReady can start the initial route on the replacement controller.
            await("native startup route") {
                var ready=false
                i.runOnMainSync {
                    val mapReady=GlNavigationActivity::class.java.getDeclaredField("mapReady").apply{isAccessible=true}.getBoolean(activity)
                    val existing=GlNavigationActivity::class.java.getDeclaredField("currentRoute").apply{isAccessible=true}.get(activity)
                    ready=mapReady && existing!=null
                    if (!ready) walk(activity.window.decorView).filterIsInstance<Button>()
                        .firstOrNull{it.text=="다시 시도" && it.visibility==View.VISIBLE}?.performClick()
                }
                ready
            }
            i.runOnMainSync{
                val field=GlNavigationActivity::class.java.getDeclaredField("navigation").apply{isAccessible=true}
                (field.get(activity) as NavigationSession).close()
                val render=GlNavigationActivity::class.java.getDeclaredMethod("renderNavigation",RenderEnvelope::class.java).apply{isAccessible=true}
                nav=NavigationSession(core,DefaultGuidanceEngine(),AndroidClock,{e->activity.runOnUiThread{render.invoke(activity,e)}})
                field.set(activity,nav)
                val factory:()->RouteProvider={planning}
                GlNavigationActivity::class.java.getDeclaredField("routeProviderFactory").apply{isAccessible=true}.set(null,factory)
                flow=activity.connectDestinationSearch(DestinationSearchProvider{query,done->
                    val point=if(query=="second")LatLon(37.502,127.0)else route.points.last()
                    done(Outcome.Ok(listOf(DestinationCandidate("chosen","chosen place","synthetic address",null,point))));CancelHandle{}
                }){pageState.set(it)}
                nav!!.attachTelemetry(object:TelemetryProvider{
                    override fun start(onSample:(Telemetry)->Unit,onError:(AppError)->Unit){telemetry.set(onSample)}
                    override fun stop(){telemetry.set(null)}
                },SourceMode.REPLAY)
            }
            await("subscription"){telemetry.get()!=null}
            js(activity,"lastRenderSeq=-1;renderedRouteId=null;true")
            emit(1);flow!!.search("chosen")
            await("results"){pageState.get()?.phase==DestinationPhase.RESULTS}
            emit(2);flow!!.select("chosen")
            await("one planning request"){planning.requests.size==1}
            val q=planning.requests[0];planning.replies[0](Outcome.Ok(route.copy(requestId=q.requestId,routeId="selected-destination")))
            await("preview ready"){pageState.get()?.phase==DestinationPhase.READY_TO_START}
            emit(3);flow!!.startGuidance()
            await("start result"){pageState.get()?.phase in listOf(DestinationPhase.GUIDING,DestinationPhase.ERROR)}
            assertEquals("start error: ${pageState.get()?.error}",DestinationPhase.GUIDING,pageState.get()?.phase)
            try {
                await("actual renderer adoption"){js(activity,"window.starlog.lastState?.payload.route?.routeId==='selected-destination' && !!map.getSource('route')") == "true"}
            } catch (failure:AssertionError) {
                val ready=GlNavigationActivity::class.java.getDeclaredField("mapReady").apply{isAccessible=true}.get(activity)
                throw AssertionError("$failure; nativeReady=$ready; phase=${pageState.get()?.phase}; JS="+
                    js(activity,"JSON.stringify({last:lastRenderSeq,state:window.starlog.lastState,style:map.isStyleLoaded()})"),failure)
            }
            assertEquals(1,planning.requests.size);assertTrue(core.requests.isEmpty())
            assertEquals(SourceMode.REPLAY,pageState.get()!!.sourceMode)
            flow!!.search("second");await("replacement results"){pageState.get()?.phase==DestinationPhase.RESULTS}
            emit(4);flow!!.select("chosen");await("replacement plan"){planning.requests.size==2}
            val replacement=planning.requests[1]
            planning.replies[1](Outcome.Ok(Route("selected-destination",replacement.requestId,listOf(replacement.start,replacement.end),
                NavigationGeometry.distanceM(replacement.start,replacement.end),40.0,emptyList())))
            await("replacement ready"){pageState.get()?.phase==DestinationPhase.READY_TO_START}
            emit(5);flow!!.startGuidance()
            await("same ID new geometry updates actual map"){js(activity,"map.getStyle().sources.route.data.geometry.coordinates[1][1]===37.502") == "true"}
            assertEquals(2,planning.requests.size);assertTrue(core.requests.isEmpty())
            flow!!.stopGuidance()
            await("actual map clears route"){js(activity,"window.starlog.lastState.payload.route===null && !map.getSource('route') && !map.getLayer('route-main')") == "true"}
            emit(6);assertNotNull(telemetry.get())
            flow!!.search("next");await("next results"){pageState.get()?.phase==DestinationPhase.RESULTS}
            emit(7);flow!!.select("chosen");await("third plan"){planning.requests.size==3}
            flow!!.stopGuidance();await("stop pending selection"){pageState.get()?.phase==DestinationPhase.IDLE}
            planning.replies[2](Outcome.Ok(route.copy(requestId=planning.requests[2].requestId,routeId="late")))
            assertEquals("true",js(activity,"window.starlog.lastState.payload.route===null"))
        } finally {i.runOnMainSync{activity.finish()};i.waitForIdleSync()}
        await("planning resource closes"){planning.closed.get()}
        assertTrue(core.closed.get());assertNull(telemetry.get())
    }
}
