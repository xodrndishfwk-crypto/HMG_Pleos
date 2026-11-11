package com.example.starlog2

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.starlog2.contracts.*
import com.example.starlog2.integration.NavigationSession
import com.example.starlog2.navigation.DefaultGuidanceEngine
import com.example.starlog2.platform.AndroidClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NavigationReplayInstrumentedTest {
    @Test fun syntheticRouteReplayReachesRealWebViewAndStopsOnDestroy() {
        val i=InstrumentationRegistry.getInstrumentation()
        val activity=i.startActivitySync(Intent(i.targetContext,GlNavigationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GlNavigationActivity
        fun walk(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap {walk(v.getChildAt(it))} else emptyList()
        val web=walk(activity.window.decorView).filterIsInstance<WebView>().single()
        fun js(code:String):String {
            val value=AtomicReference<String?>(); i.runOnMainSync {web.evaluateJavascript(code){value.set(it)}}
            val limit=System.nanoTime()+5_000_000_000; while(value.get()==null&&System.nanoTime()<limit)Thread.sleep(25)
            return value.get()?:error("JS timeout")
        }
        fun await(condition:()->Boolean) {
            val until=System.nanoTime()+35_000_000_000
            while(System.nanoTime()<until){if(condition())return;Thread.sleep(100)}
            fail("Replay UI condition timeout")
        }
        var session:NavigationSession?=null
        try {
            await { js("typeof window.starlog === 'object' && map.isStyleLoaded()") == "true" }
            val route=ContractJson.decodeRoute(i.context.assets.open("contracts/route-valid.json").bufferedReader().use{it.readText()})
            i.runOnMainSync {
                val field=GlNavigationActivity::class.java.getDeclaredField("navigation").apply{isAccessible=true}
                (field.get(activity) as NavigationSession).close()
                val method=GlNavigationActivity::class.java.getDeclaredMethod("renderNavigation",RenderEnvelope::class.java).apply{isAccessible=true}
                session=NavigationSession(object:RouteProvider {
                    override fun request(input:RouteRequest,done:(Outcome<Route>)->Unit):CancelHandle {
                        done(Outcome.Ok(route.copy(requestId=input.requestId)));return CancelHandle{}
                    }
                },DefaultGuidanceEngine(),AndroidClock,{ e -> activity.runOnUiThread{method.invoke(activity,e)} })
                field.set(activity,session)
                // A replacement controller is a new renderer generation, just like a page/session restart.
                web.evaluateJavascript("lastRenderSeq=-1;renderedRouteId=null;true") {
                    session!!.requestRoute(RouteRequest("ui-test",route.points.first(),route.points.last()))
                }
            }
            await { js("!!(window.starlog.lastState && window.starlog.lastState.payload.route && window.starlog.lastState.payload.route.routeId==='route-a')") == "true" }
            i.runOnMainSync {walk(activity.window.decorView).filterIsInstance<Button>().single{it.text=="모의 주행 시작"}.performClick()}
            await {js("!!(window.starlog.lastState.payload.pose && window.starlog.lastState.payload.guidance && window.starlog.lastState.payload.guidance.alongM>5)")=="true"}
            assertEquals("true",js("window.starlog.lastState.payload.sourceMode==='REPLAY' && vehicleMarker.getLngLat().lng===127 && window.starlog.lastState.payload.guidance.nextInstruction!==null"))
        } finally { i.runOnMainSync{activity.finish()};i.waitForIdleSync() }
        val closed=NavigationSession::class.java.getDeclaredField("closed").apply{isAccessible=true}.get(session) as java.util.concurrent.atomic.AtomicBoolean
        assertTrue(closed.get())
    }
}
