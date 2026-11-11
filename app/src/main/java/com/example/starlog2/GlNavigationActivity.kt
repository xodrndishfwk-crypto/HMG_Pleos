package com.example.starlog2

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import com.example.starlog2.integration.MapLoadSession

class GlNavigationActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var instruction: TextView
    private lateinit var retryButton: Button
    private lateinit var infoPanel: LinearLayout
    private lateinit var navigation: com.example.starlog2.integration.NavigationSession
    private var currentRoute: com.example.starlog2.contracts.RenderRoute? = null
    private var latestRender: com.example.starlog2.contracts.RenderEnvelope? = null
    private lateinit var maneuverLabel: TextView
    private var navigationStarted = false
    private val receivedActions = linkedSetOf<String>()
    private fun newNavigation() = com.example.starlog2.integration.NavigationSession(
        com.example.starlog2.navigation.ValhallaRouteProvider("starlog-android"),
        com.example.starlog2.navigation.DefaultGuidanceEngine(), com.example.starlog2.platform.AndroidClock,
        { envelope -> runOnUiThread { if (!destroyed) renderNavigation(envelope) } },
        starLog = com.example.starlog2.integration.StarLogComponents(
            com.example.starlog2.starlog.storage.SqliteMemoryRepository(applicationContext),
            { com.example.starlog2.starlog.session.StarLogSessionImpl(
                experienceReducer = com.example.starlog2.starlog.experience.ExperienceReducer(
                    com.example.starlog2.starlog.model.ExperienceConfig(maxSampleGapMs = 2_000)),
                visitDetector = com.example.starlog2.starlog.visit.VisitDetector(
                    com.example.starlog2.starlog.model.VisitConfig(maxSampleGapMs = 2_000)),
                sessionId = java.util.UUID.randomUUID().toString()) }))
    private val handler = Handler(Looper.getMainLooper())
    private val checkTelemetryFreshness = object : Runnable {
        override fun run() {
            if (!destroyed) {
                navigation.refreshTelemetryAge()
                handler.postDelayed(this, 1_000)
            }
        }
    }
    private val mapSession = MapLoadSession()
    private var destroyed = false
    private var mapReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(104, 185, 237))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failMap(request.url.getQueryParameter("session")?.toIntOrNull() ?: mapSession.generation)
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) failMap(request.url.getQueryParameter("session")?.toIntOrNull() ?: mapSession.generation)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return !request.url.toString().startsWith("file:///android_asset/map.html")
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(Bridge(), "Android")
        }
        navigation = newNavigation()
        setContentView(buildUi())
        startMap()
        handler.post(checkTelemetryFreshness)
    }

    private inner class Bridge {
        @JavascriptInterface fun onEvent(raw: String) {
            val event = try { com.example.starlog2.contracts.ContractJson.decodeEvent(raw) }
                catch (_: Exception) { return }
            runOnUiThread {
                if (destroyed || !mapReady || event.optInt("session", -1) != mapSession.generation) return@runOnUiThread
                if (event.getString("type") != "userAction") return@runOnUiThread
                val id = event.getString("messageId")
                if (!receivedActions.add(id)) return@runOnUiThread
                if (receivedActions.size > 128) receivedActions.remove(receivedActions.first())
                val payload = event.getJSONObject("payload")
                navigation.handleCommand(com.example.starlog2.contracts.ExperienceCommand(
                    com.example.starlog2.contracts.ExperienceAction.valueOf(payload.getString("action")),
                    if (payload.isNull("starId")) null else payload.getString("starId")))
            }
        }
        @JavascriptInterface fun onMapReady(token: Int) = runOnUiThread {
            if (destroyed || !mapSession.ready(token)) return@runOnUiThread
            mapReady = true
            retryButton.visibility = View.GONE
            updateAttributionInset()
            status.setText(R.string.map_ready)
            latestRender?.let(::renderNavigation)
            if (!navigationStarted) loadRoute()
        }

        @JavascriptInterface fun onMapError(token: Int, fatal: Boolean) = runOnUiThread {
            if (destroyed || token != mapSession.generation) return@runOnUiThread
            if (fatal) failMap(token) else status.text = "일부 지도 데이터를 불러오지 못했습니다."
        }
    }

    private fun startMap() {
        if (destroyed) return
        mapReady = false
        val token = mapSession.begin()
        receivedActions.clear()
        retryButton.visibility = View.GONE
        status.setText(R.string.map_loading)
        instruction.text = "지도와 경로를 준비합니다."
        webView.stopLoading()
        webView.loadUrl("file:///android_asset/map.html?session=$token")
        handler.postDelayed({
            if (!destroyed && token == mapSession.generation && !mapReady) failMap(token)
        }, 20_000)
    }

    private fun failMap(token: Int) {
        if (destroyed || !mapSession.fail(token)) return
        mapReady = false
        status.text = "지도를 불러오지 못했습니다."
        instruction.text = "네트워크를 확인한 뒤 다시 시도해 주세요."
        retryButton.visibility = View.VISIBLE
        retryButton.isEnabled = true
    }

    private fun updateAttributionInset() {
        if (!destroyed && mapReady) {
            val inset = infoPanel.height / resources.displayMetrics.density + 24
            webView.evaluateJavascript("setOverlayInset($inset)", null)
        }
    }

    private fun buildUi(): FrameLayout {
        val d = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22*d).toInt(), (12*d).toInt(), (28*d).toInt(), (12*d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 18*d
                setColor(Color.argb(242, 0, 91, 210))
            }
            maneuverLabel = label("—", 30f, Color.WHITE)
            addView(maneuverLabel)
        }
        root.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            val m = (14*d).toInt(); setMargins(m, m, 0, 0)
        })
        root.addView(Button(this).apply {
            setText(R.string.vehicle_diagnostic_button)
            setOnClickListener { startActivity(Intent(this@GlNavigationActivity, MainActivity::class.java)) }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            val m = (14*d).toInt(); setMargins(0, m, m, 0)
        })
        infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18*d).toInt(), (14*d).toInt(), (18*d).toInt(), (14*d).toInt())
            setBackgroundColor(Color.argb(225,14,18,27))
        }
        status = label("WebGL 지도 초기화 중…", 14f, Color.rgb(185,198,219))
        instruction = label("경로를 불러오고 있습니다.", 22f, Color.WHITE).apply { setPadding(0,(8*d).toInt(),0,(10*d).toInt()) }
        retryButton = Button(this).apply {
            text = "다시 시도"
            visibility = View.GONE
            setOnClickListener { if (mapReady) loadRoute() else startMap() }
        }
        infoPanel.addView(status)
        infoPanel.addView(instruction)
        infoPanel.addView(retryButton)
        infoPanel.addView(Button(this).apply {
            setText(R.string.replay_start)
            setOnClickListener { startReplay() }
        })
        infoPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateAttributionInset() }
        root.addView(infoPanel, FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply {
            val m=(16*d).toInt(); setMargins(m,0,m,m)
        })
        return root
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text=value; textSize=size; setTextColor(color)
    }

    private fun loadRoute() {
        if (destroyed || !mapReady) return
        navigationStarted = true
        retryButton.visibility = View.GONE
        status.setText(R.string.route_loading)
        navigation.requestRoute(com.example.starlog2.contracts.RouteRequest("route-${System.nanoTime()}",
            com.example.starlog2.contracts.LatLon(START.lat, START.lon),
            com.example.starlog2.contracts.LatLon(END.lat, END.lon)))
    }

    private fun renderNavigation(envelope: com.example.starlog2.contracts.RenderEnvelope) {
        latestRender = envelope
        val p = envelope.payload
        currentRoute = p.route
        if (!mapReady) return
        val json = try { com.example.starlog2.contracts.ContractJson.encode(envelope) }
        catch (_: IllegalArgumentException) {
            status.setText(R.string.navigation_failed)
            instruction.setText(R.string.render_too_large)
            return
        }
        webView.evaluateJavascript("window.starlog.receive(${JSONObject.quote(json)})", null)
        if (p.error != null) {
            val memoryError = p.error.messageKey.startsWith("storage.") || p.error.messageKey.startsWith("experience.")
            status.setText(if (memoryError) R.string.memory_operation_failed else R.string.navigation_failed)
            instruction.text = p.error.code
            retryButton.visibility = if (memoryError) View.GONE else View.VISIBLE
        } else if (p.route != null) {
            status.text = getString(R.string.navigation_replay_status, p.sourceMode.name)
            retryButton.visibility = View.GONE
            instruction.text = p.guidance?.nextInstruction ?: getString(R.string.replay_ready)
        }
        maneuverLabel.text = p.guidance?.distanceToManeuverM?.let {
            getString(R.string.maneuver_distance, it.toInt())
        } ?: "—"
    }

    private fun startReplay() {
        val geometry = currentRoute ?: return
        val points = geometry.coordinates.map { com.example.starlog2.contracts.LatLon(it[1], it[0]) }
        val total = points.zipWithNext().sumOf { (a,b) -> com.example.starlog2.navigation.NavigationGeometry.distanceM(a,b) }
        val count = kotlin.math.ceil(total / 20).toInt().coerceAtLeast(1)
        val wall = System.currentTimeMillis()
        val frames = (0..count).map { i ->
            val along = (i * 20.0).coerceAtMost(total)
            com.example.starlog2.navigation.ReplayFrame(i * 2000L, wall + i * 2000L,
                com.example.starlog2.navigation.NavigationGeometry.pointAtDistance(points, along), 5.0,
                com.example.starlog2.navigation.NavigationGeometry.lookAheadBearing(points, along),
                10.0, com.example.starlog2.contracts.Gear.D, true)
        }
        navigation.attachTelemetry(com.example.starlog2.navigation.ReplayTelemetryProvider(frames,
            clock = com.example.starlog2.navigation.ReplayClock { com.example.starlog2.platform.AndroidClock.monotonicMs() }),
            com.example.starlog2.contracts.SourceMode.REPLAY)
    }

    override fun onResume(){super.onResume();webView.onResume();navigation.resume()}
    override fun onPause(){navigation.pause();webView.onPause();super.onPause()}
    override fun onDestroy() {
        destroyed = true
        mapSession.close()
        handler.removeCallbacksAndMessages(null)
        navigation.close()
        webView.removeJavascriptInterface("Android")
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private data class Point(val lat:Double,val lon:Double)
    private companion object{
        val START=Point(37.497952,127.027619)
        val END=Point(37.504501,127.048962)
    }
}
