package com.example.starlog2

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import com.example.starlog2.integration.MapLoadSession
import kotlin.math.*

class GlNavigationActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var instruction: TextView
    private lateinit var retryButton: Button
    private lateinit var infoPanel: LinearLayout
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val mapSession = MapLoadSession()
    private var destroyed = false
    private var routeTask: Future<*>? = null
    @Volatile private var routeGeneration = 0
    private val connectionLock = Any()
    private var activeConnection: HttpURLConnection? = null
    private var mapReady = false
    private var pendingRoute: Route? = null

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
        setContentView(buildUi())
        startMap()
    }

    private inner class Bridge {
        @JavascriptInterface fun onMapReady(token: Int) = runOnUiThread {
            if (destroyed || !mapSession.ready(token)) return@runOnUiThread
            mapReady = true
            retryButton.visibility = View.GONE
            updateAttributionInset()
            status.text = "76° WebGL 지도 준비 완료"
            pendingRoute?.let(::drawRoute) ?: loadRoute()
        }

        @JavascriptInterface fun onMapError(token: Int, fatal: Boolean) = runOnUiThread {
            if (destroyed || token != mapSession.generation) return@runOnUiThread
            if (fatal) failMap(token) else status.text = "일부 지도 데이터를 불러오지 못했습니다."
        }
    }

    private fun startMap() {
        if (destroyed) return
        cancelRoute()
        mapReady = false
        pendingRoute = null
        val token = mapSession.begin()
        retryButton.visibility = View.GONE
        status.text = "WebGL 지도 초기화 중…"
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
        cancelRoute()
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
            addView(label("↱  530m", 34f, Color.WHITE))
            addView(label("테헤란로 방면", 18f, Color.WHITE))
        }
        root.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            val m = (14*d).toInt(); setMargins(m, m, 0, 0)
        })
        root.addView(Button(this).apply {
            text = "차량 SDK 진단"
            setOnClickListener { startActivity(Intent(this@GlNavigationActivity, MainActivity::class.java)) }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            val m = (14*d).toInt(); setMargins(0, m, m, 0)
        })
        root.addView(label("▲", 30f, Color.rgb(0,104,232)).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke((3*d).toInt(), Color.rgb(195,201,210))
            }
            elevation = 10*d
        }, FrameLayout.LayoutParams((62*d).toInt(), (62*d).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (116*d).toInt()
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
        cancelRoute()
        val token = routeGeneration
        retryButton.visibility = View.GONE
        status.text = "Valhalla 경로 요청 중…"
        instruction.text = "강남역 → 테헤란로"
        routeTask = worker.submit {
            try {
                val route = parseRoute(requestRoute(token))
                runOnUiThread {
                    if (!destroyed && token == routeGeneration && mapReady) drawRoute(route)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (destroyed || token != routeGeneration) return@runOnUiThread
                    Log.w("StarlogMap", "Route request failed: ${e.javaClass.simpleName}")
                    status.text = "경로를 불러오지 못했습니다."
                    instruction.text = "네트워크와 경로 서버 상태를 확인하세요."
                    retryButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun cancelRoute() {
        routeGeneration += 1
        routeTask?.cancel(true)
        routeTask = null
        synchronized(connectionLock) {
            activeConnection?.disconnect()
            activeConnection = null
        }
    }

    private fun requestRoute(token: Int): JSONObject {
        val body=JSONObject()
            .put("locations",JSONArray()
                .put(JSONObject().put("lat",START.lat).put("lon",START.lon).put("type","break"))
                .put(JSONObject().put("lat",END.lat).put("lon",END.lon).put("type","break")))
            .put("costing","auto").put("units","kilometers").put("language","ko-KR")
        val c=(URL(ROUTE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod="POST"; connectTimeout=10_000; readTimeout=15_000; doOutput=true
            setRequestProperty("Content-Type","application/json")
            setRequestProperty("X-Client-Id","starlog-navigation-prototype")
        }
        synchronized(connectionLock) {
            check(!Thread.currentThread().isInterrupted && token == routeGeneration)
            activeConnection = c
        }
        return try {
            c.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val stream=if(c.responseCode in 200..299)c.inputStream else c.errorStream
            val response=stream.bufferedReader().use { it.readText() }
            if(c.responseCode !in 200..299) error("HTTP ${c.responseCode}: $response")
            JSONObject(response)
        } finally {
            c.disconnect()
            synchronized(connectionLock) { if (activeConnection === c) activeConnection = null }
        }
    }

    private fun parseRoute(json: JSONObject): Route {
        val trip=json.getJSONObject("trip")
        val summary=trip.getJSONObject("summary")
        val leg=trip.getJSONArray("legs").getJSONObject(0)
        val items=leg.getJSONArray("maneuvers")
        val maneuvers=buildList { for(i in 0 until items.length()) add(items.getJSONObject(i).optString("instruction","안내 정보 없음")) }
        return Route(decode(leg.getString("shape")),summary.getDouble("length"),summary.getDouble("time"),maneuvers)
    }

    private fun drawRoute(route: Route) {
        pendingRoute=null
        val first=route.points.firstOrNull()?:START
        val ahead=lookAhead(route.points,310.0)
        val payload=JSONObject()
            .put("coordinates",JSONArray().apply { route.points.forEach { put(JSONArray().put(it.lon).put(it.lat)) } })
            .put("lookAhead",JSONArray().put(ahead.lon).put(ahead.lat))
            .put("bearing",bearing(first,ahead))
        webView.evaluateJavascript("showRoute(${JSONObject.quote(payload.toString())})",null)
        val min=(route.seconds/60).toInt().coerceAtLeast(1)
        status.text="76° 3D 주행 · %.2f km · 약 %d분".format(route.km,min)
        instruction.text=route.maneuvers.firstOrNull()?:"경로 안내 없음"
    }

    private fun decode(s:String):List<Point>{
        val out=ArrayList<Point>(); var i=0; var lat=0L; var lon=0L
        while(i<s.length){ val a=value(s,i);i=a.second;lat+=a.first;val b=value(s,i);i=b.second;lon+=b.first;out+=Point(lat/1e6,lon/1e6) }
        return out
    }
    private fun value(s:String,start:Int):Pair<Long,Int>{
        var i=start;var shift=0;var value=0L
        while(i<s.length){val c=s[i++].code-63;value=value or((c and 31).toLong() shl shift);shift+=5;if(c<32)break}
        return (if(value and 1L !=0L)(value shr 1).inv() else value shr 1) to i
    }
    private fun lookAhead(p:List<Point>,meters:Double):Point{
        if(p.isEmpty())return START;var total=0.0
        for(i in 1 until p.size){total+=distance(p[i-1],p[i]);if(total>=meters)return p[i]};return p.last()
    }
    private fun distance(a:Point,b:Point):Double{
        val dy=Math.toRadians(b.lat-a.lat);val dx=Math.toRadians(b.lon-a.lon)
        val h=sin(dy/2).pow(2)+cos(Math.toRadians(a.lat))*cos(Math.toRadians(b.lat))*sin(dx/2).pow(2)
        return 12_742_000.0*atan2(sqrt(h),sqrt(1-h))
    }
    private fun bearing(a:Point,b:Point):Double{
        val p1=Math.toRadians(a.lat);val p2=Math.toRadians(b.lat);val dl=Math.toRadians(b.lon-a.lon)
        return (Math.toDegrees(atan2(sin(dl)*cos(p2),cos(p1)*sin(p2)-sin(p1)*cos(p2)*cos(dl)))+360)%360
    }

    override fun onResume(){super.onResume();webView.onResume()}
    override fun onPause(){webView.onPause();super.onPause()}
    override fun onDestroy() {
        destroyed = true
        mapSession.close()
        handler.removeCallbacksAndMessages(null)
        cancelRoute()
        worker.shutdownNow()
        webView.removeJavascriptInterface("Android")
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private data class Point(val lat:Double,val lon:Double)
    private data class Route(val points:List<Point>,val km:Double,val seconds:Double,val maneuvers:List<String>)
    private companion object{
        const val ROUTE_URL="https://valhalla1.openstreetmap.de/route"
        val START=Point(37.497952,127.027619)
        val END=Point(37.504501,127.048962)
    }
}
