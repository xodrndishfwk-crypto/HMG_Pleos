package com.example.starlog2

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G0RecoveryInstrumentedTest {
    @Before fun useDeterministicRoute() { NavigationTestRoutes.install() }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val activities = mutableListOf<Activity>()

    private fun launch(): GlNavigationActivity {
        val intent = Intent(instrumentation.targetContext, GlNavigationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return (instrumentation.startActivitySync(intent) as GlNavigationActivity).also { activities += it }
    }

    private fun views(activity: Activity): List<View> {
        fun walk(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
            (0 until view.childCount).flatMap { walk(view.getChildAt(it)) }
        } else emptyList()
        return walk(activity.window.decorView)
    }

    private fun awaitUi(timeoutMs: Long = 35_000, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            var passed = false
            instrumentation.runOnMainSync { passed = predicate() }
            if (passed) return
            Thread.sleep(100)
        }
        throw AssertionError("UI condition was not reached within $timeoutMs ms")
    }

    private fun text(activity: Activity, fragment: String): Boolean = views(activity)
        .filterIsInstance<TextView>().any { it.text.contains(fragment) }

    private fun web(activity: Activity): WebView = views(activity).filterIsInstance<WebView>().single()

    private fun script(activity: Activity, source: String): String {
        val result = AtomicReference<String?>()
        instrumentation.runOnMainSync { web(activity).evaluateJavascript(source) { result.set(it) } }
        val deadline = System.nanoTime() + 5_000_000_000
        while (result.get() == null && System.nanoTime() < deadline) Thread.sleep(50)
        return result.get() ?: throw AssertionError("JavaScript callback timeout")
    }

    private fun awaitRoute(activity: Activity) = awaitUi {
        text(activity, "76° 3D 주행")
    }

    private fun retry(activity: Activity) = instrumentation.runOnMainSync {
        views(activity).filterIsInstance<Button>().single { it.text == "다시 시도" }.performClick()
    }

    @After fun finishActivities() {
        instrumentation.runOnMainSync { activities.reversed().forEach { if (!it.isFinishing) it.finish() } }
        instrumentation.waitForIdleSync()
        NavigationTestRoutes.restore()
    }

    @Test fun remoteStyleFailure_retryRestoresMapAndVisibleAttribution() {
        val activity = launch()
        awaitRoute(activity)
        script(activity, "map.setStyle('https://127.0.0.1:1/unavailable.json'); true")
        awaitUi { text(activity, "지도를 불러오지 못했습니다.") }
        retry(activity)
        awaitRoute(activity)
        val raw = script(activity, """
            JSON.stringify({pitch:map.getPitch(),buildingsHidden:map.getStyle().layers
              .filter(l=>l.type==='fill-extrusion').every(l=>map.getLayoutProperty(l.id,'visibility')==='none'),
              attribution:(()=>{const e=document.querySelector('.maplibregl-ctrl-attrib');
                const r=e.getBoundingClientRect();return r.height>0&&r.top>=0&&
                r.bottom<innerHeight-Number.parseFloat(getComputedStyle(document.documentElement)
                .getPropertyValue('--overlay-inset'));})()})
        """.trimIndent())
        val json = JSONObject(JSONTokener(raw).nextValue() as String)
        assertEquals(76.0, json.getDouble("pitch"), 0.1)
        assertTrue(json.getBoolean("buildingsHidden"))
        assertTrue(json.getBoolean("attribution"))
    }

    @Test fun missingReadyCallback_timesOutAndCanRetry() {
        val activity = launch()
        awaitRoute(activity)
        instrumentation.runOnMainSync {
            val method = GlNavigationActivity::class.java.getDeclaredMethod("startMap")
            method.isAccessible = true
            method.invoke(activity)
            web(activity).loadDataWithBaseURL("file:///android_asset/", "<html>no ready callback</html>", "text/html", "UTF-8", null)
        }
        awaitUi(25_000) { text(activity, "지도를 불러오지 못했습니다.") }
        retry(activity)
        awaitRoute(activity)
    }

    @Test fun missingMainPage_showsErrorAndRetryRestoresMap() {
        val activity = launch()
        awaitRoute(activity)
        instrumentation.runOnMainSync {
            web(activity).loadUrl("file:///android_asset/missing-g0-page.html")
        }
        awaitUi { text(activity, "지도를 불러오지 못했습니다.") }
        retry(activity)
        awaitRoute(activity)
    }

    @Test fun vehicleDiagnostic_readsActualValueAndReleasesSession() {
        val activity = launch()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync {
                views(activity).filterIsInstance<Button>().single { it.text == "차량 SDK 진단" }.performClick()
            }
            val diagnostic = monitor.waitForActivityWithTimeout(10_000) as? MainActivity
                ?: throw AssertionError("Vehicle diagnostic did not open")
            activities += diagnostic
            awaitUi { text(diagnostic, "조회 성공: ROW_1_LEFT") }
            instrumentation.runOnMainSync {
                views(diagnostic).filterIsInstance<Button>()
                    .single { it.text == "1열 왼쪽 도어 위치 다시 읽기" }.performClick()
            }
            awaitUi { text(diagnostic, "조회 성공: ROW_1_LEFT") }
            instrumentation.runOnMainSync { diagnostic.finish() }
            instrumentation.waitForIdleSync()
            awaitUi {
                val field = MainActivity::class.java.getDeclaredField("vehicle")
                field.isAccessible = true
                diagnostic.isDestroyed && field.get(diagnostic) == null
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
