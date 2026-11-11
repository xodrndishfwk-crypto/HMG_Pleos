package com.example.starlog2

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.starlog2.contracts.*
import com.example.starlog2.integration.NavigationSession
import com.example.starlog2.integration.StarLogComponents
import com.example.starlog2.navigation.DefaultGuidanceEngine
import com.example.starlog2.starlog.session.StarLogSessionImpl
import com.example.starlog2.starlog.storage.SqliteMemoryRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Synthetic telemetry and virtual time; real Activity, JS bridge, reducer, IO and SQLite. */
@RunWith(AndroidJUnit4::class)
class StarLogFlowInstrumentedTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private val dbName="high-i2-flow-test.db"
    private class TestClock:Clock {
        val now=AtomicLong(0)
        override fun monotonicMs()=now.get()
        override fun wallTimeMs()=1_800_000_000_000L+now.get()
    }
    private class ManualTelemetry:TelemetryProvider {
        val callback=AtomicReference<((Telemetry)->Unit)?>(null)
        override fun start(onSample:(Telemetry)->Unit,onError:(AppError)->Unit){callback.set(onSample)}
        override fun stop(){callback.set(null)}
    }
    private fun await(label:String,condition:()->Boolean) {
        val deadline=System.nanoTime()+40_000_000_000
        while(System.nanoTime()<deadline){if(condition())return;Thread.sleep(80)}
        fail("Timed out: $label")
    }
    private fun walk(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{walk(v.getChildAt(it))} else emptyList()
    private fun js(activity:GlNavigationActivity,code:String):String {
        val result=AtomicReference<String?>()
        i.runOnMainSync {walk(activity.window.decorView).filterIsInstance<WebView>().single().evaluateJavascript(code){result.set(it)}}
        await("JavaScript callback"){result.get()!=null};return result.get()!!
    }
    private fun launch():GlNavigationActivity = (i.startActivitySync(Intent(i.targetContext,GlNavigationActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GlNavigationActivity).also { activity ->
        await("map load"){js(activity,"typeof window.starlog === 'object' && map.isStyleLoaded()") == "true"}
    }
    private fun install(activity:GlNavigationActivity,clock:TestClock,provider:ManualTelemetry):AtomicBoolean {
        val repository=SqliteMemoryRepository(i.targetContext,dbName);val closed=AtomicBoolean(false)
        val tracked=object:MemoryRepository by repository,AutoCloseable {
            override fun close(){repository.close();closed.set(true)}
        }
        val route=ContractJson.decodeRoute(i.context.assets.open("contracts/route-valid.json").bufferedReader().use{it.readText()})
        i.runOnMainSync {
            // Virtual jumps are driven explicitly; do not mix the Activity's real-time watchdog with test time.
            val handler=GlNavigationActivity::class.java.getDeclaredField("handler").apply{isAccessible=true}.get(activity) as android.os.Handler
            val watchdog=GlNavigationActivity::class.java.getDeclaredField("checkTelemetryFreshness").apply{isAccessible=true}.get(activity) as Runnable
            handler.removeCallbacks(watchdog)
            val field=GlNavigationActivity::class.java.getDeclaredField("navigation").apply{isAccessible=true}
            (field.get(activity) as NavigationSession).close()
            val render=GlNavigationActivity::class.java.getDeclaredMethod("renderNavigation",RenderEnvelope::class.java).apply{isAccessible=true}
            val session=NavigationSession(object:RouteProvider {
                override fun request(input:RouteRequest,done:(Outcome<Route>)->Unit):CancelHandle {
                    done(Outcome.Ok(route.copy(requestId=input.requestId)));return CancelHandle{}
                }
            },DefaultGuidanceEngine(),clock,{ e -> activity.runOnUiThread{render.invoke(activity,e)} },
                starLog=StarLogComponents(tracked,{StarLogSessionImpl()}))
            field.set(activity,session)
            walk(activity.window.decorView).filterIsInstance<WebView>().single()
                .evaluateJavascript("lastRenderSeq=-1;renderedRouteId=null;true") {
                    session.requestRoute(RouteRequest("i2-test",route.points.first(),route.points.last()))
                    session.attachTelemetry(provider,SourceMode.REPLAY)
                }
        }
        await("telemetry subscription"){provider.callback.get()!=null}
        return closed
    }
    private fun tick(clock:TestClock,provider:ManualTelemetry,time:Long,gear:Gear=Gear.P,speed:Double=0.0) {
        clock.now.set(time)
        provider.callback.get()!!.invoke(Telemetry(time,clock.wallTimeMs(),time,SourceMode.REPLAY,"i2-test",
            TimedValue(LocationFix(LatLon(37.5,127.0),5.0,0.0,Reference.TRUE_NORTH),Quality.VALID,0),
            TimedValue(speed,Quality.VALID,0),TimedValue(gear,Quality.VALID,0),TimedValue(true,Quality.VALID,0)))
    }
    private fun finish(activity:GlNavigationActivity,closed:AtomicBoolean) {
        i.runOnMainSync{activity.finish()};i.waitForIdleSync();await("IO repository close"){closed.get()}
    }
    @Test fun webCommandsStayVisitAndCheckpointSurviveRecreation() {
        i.targetContext.deleteDatabase(dbName)
        val clock=TestClock();val provider=ManualTelemetry()
        var first:GlNavigationActivity?=null;var second:GlNavigationActivity?=null
        var closed1:AtomicBoolean?=null;var closed2:AtomicBoolean?=null
        try {
            SqliteMemoryRepository(i.targetContext,dbName).use { repository ->
                for((id,mode,lon) in listOf(Triple("a",SourceMode.REPLAY,127.0),Triple("b",SourceMode.REPLAY,127.0001),Triple("live",SourceMode.LIVE,127.0))) {
                    assertTrue(repository.saveVisit(Visit(id,id,null,LatLon(37.501,lon),1,2,mode,null)) is Outcome.Ok)
                }
            }
            val a=launch();first=a;closed1=install(a,clock,provider)
            tick(clock,provider,0)
            await("initial stop"){js(a,"window.starlog.lastState?.payload.experience.mode === 'STOP_PENDING'")=="true"}
            tick(clock,provider,179_999)
            await("179999 boundary"){js(a,"window.starlog.lastState.payload.experience.stopElapsedMs===179999 && window.starlog.lastState.payload.experience.mode==='STOP_PENDING'")=="true"}
            tick(clock,provider,180_000)
            await("memory frame"){js(a,"window.starlog.lastState.payload.experience.mode==='MEMORY_FRAME' && !document.getElementById('memory-panel').hidden")=="true"}
            assertEquals("true",js(a,"window.starlog.lastState.payload.memory.title===null && window.starlog.lastState.payload.memory.photoRef===null && document.getElementById('memory-title').textContent==='' && !window.starlog.lastState.payload.skyItems.some(s=>s.starId==='star-live')"))
            js(a,"document.querySelector('[data-star-id=\"star-b\"]').click();true")
            await("native SELECT_MEMORY"){js(a,"window.starlog.lastState.payload.memory.starId==='star-b'")=="true"}
            js(a,"document.getElementById('recap-open').click();true")
            await("native OPEN_RECAP"){js(a,"window.starlog.lastState.payload.experience.mode==='RECAP'")=="true"}
            js(a,"document.getElementById('recap-close').click();true")
            await("native CLOSE_RECAP"){js(a,"window.starlog.lastState.payload.experience.mode==='MEMORY_FRAME'")=="true"}
            tick(clock,provider,180_001,Gear.D,10.0)
            await("departure hides memory"){js(a,"window.starlog.lastState.payload.experience.mode==='DRIVING_SAFE' && document.getElementById('memory-panel').hidden")=="true"}
            js(a,"sendExperience('OPEN_RECAP');true")
            await("driving command rejected"){js(a,"window.starlog.lastState.payload.error?.code==='INVALID_STATE'")=="true"}
            tick(clock,provider,180_002);tick(clock,provider,780_002)
            await("qualified visit persisted") {
                SqliteMemoryRepository(i.targetContext,dbName).use{(it.listStars(SourceMode.REPLAY) as Outcome.Ok).value.size==3}
            }
            finish(a,closed1);first=null
            SqliteMemoryRepository(i.targetContext,dbName).use { repository ->
                assertNotNull((repository.loadCheckpoint(SourceMode.REPLAY) as Outcome.Ok).value!!.qualifiedPendingVisit)
                assertEquals(1,(repository.listStars(SourceMode.LIVE) as Outcome.Ok).value.size)
            }
            val b=launch();second=b;val next=ManualTelemetry();closed2=install(b,clock,next)
            tick(clock,next,780_003)
            await("restore resets stop timer"){js(b,"window.starlog.lastState?.payload.experience.mode==='STOP_PENDING' && window.starlog.lastState.payload.experience.stopElapsedMs===0")=="true"}
            SqliteMemoryRepository(i.targetContext,dbName).use { assertEquals(3,(it.listStars(SourceMode.REPLAY) as Outcome.Ok).value.size) }
            finish(b,closed2);second=null
        } finally {
            first?.let{a->closed1?.let{finish(a,it)}};second?.let{a->closed2?.let{finish(a,it)}}
            i.targetContext.deleteDatabase(dbName)
        }
    }
}
