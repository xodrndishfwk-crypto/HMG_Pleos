package com.example.starlog2

import android.graphics.Color
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
import org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionColor
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionHeight
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class NativeMapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var statusView: TextView
    private lateinit var instructionView: TextView
    private lateinit var routeButton: Button
    private var mapStyle: Style? = null
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        setContentView(createContentView())

        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder()
                .target(START)
                .zoom(13.8)
                .tilt(35.0)
                .bearing(0.0)
                .build()
            map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                mapStyle = style
                style.layers.filterIsInstance<FillExtrusionLayer>().forEach { layer ->
                    layer.setProperties(
                        fillExtrusionHeight(36f),
                        fillExtrusionColor("#C8CDD4"),
                        fillExtrusionOpacity(0.52f),
                    )
                }
                showStatus("지도 준비 완료", false)
                requestTestRoute()
            }
        }
        routeButton.setOnClickListener { requestTestRoute() }
    }

    private fun createContentView(): FrameLayout {
        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(12, 15, 22))
        }
        root.addView(mapView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val atmosphere = AtmosphereView(this)
        root.addView(
            atmosphere,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        val maneuverBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * density).toInt(), (12 * density).toInt(), (28 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(Color.argb(242, 0, 91, 210))
            }
            addView(TextView(this@NativeMapActivity).apply {
                setText(R.string.prototype_maneuver)
                textSize = 34f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@NativeMapActivity).apply {
                text = "테헤란로 방면"
                textSize = 18f
                setTextColor(Color.WHITE)
            })
        }
        root.addView(
            maneuverBanner,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                val margin = (14 * density).toInt()
                setMargins(margin, margin, 0, 0)
            },
        )
        val vehicleMarker = TextView(this).apply {
            text = "▲"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0, 104, 232))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(245, 255, 255, 255))
                setStroke((3 * density).toInt(), Color.rgb(195, 201, 210))
            }
            elevation = 10 * density
        }
        root.addView(
            vehicleMarker,
            FrameLayout.LayoutParams((62 * density).toInt(), (62 * density).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = (116 * density).toInt()
            },
        )
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (14 * density).toInt(), (18 * density).toInt(), (14 * density).toInt())
            setBackgroundColor(Color.argb(225, 14, 18, 27))
        }
        statusView = TextView(this).apply {
            text = "지도 초기화 중…"
            textSize = 14f
            setTextColor(Color.rgb(185, 198, 219))
        }
        panel.addView(statusView)
        instructionView = TextView(this).apply {
            text = "경로를 불러오고 있습니다."
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, (8 * density).toInt(), 0, (10 * density).toInt())
        }
        panel.addView(instructionView)
        routeButton = Button(this).apply {
            text = "경로 다시 불러오기"
            isEnabled = false
            visibility = View.GONE
        }
        panel.addView(routeButton)
        root.addView(
            panel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                val margin = (16 * density).toInt()
                setMargins(margin, 0, margin, margin)
            },
        )
        return root
    }

    private fun requestTestRoute() {
        if (mapStyle == null) return
        showStatus("Valhalla 경로 요청 중…", true)
        instructionView.text = "강남역 → 테헤란로"
        worker.execute {
            try {
                val route = parseRoute(requestRoute())
                runOnUiThread { renderRoute(route) }
            } catch (error: Exception) {
                runOnUiThread {
                    showStatus("경로 요청 실패: ${error.message ?: error.javaClass.simpleName}", false)
                    instructionView.text = "네트워크와 경로 서버 상태를 확인하세요."
                }
            }
        }
    }

    private fun requestRoute(): JSONObject {
        val requestBody = JSONObject()
            .put(
                "locations",
                org.json.JSONArray()
                    .put(JSONObject().put("lat", START.latitude).put("lon", START.longitude).put("type", "break"))
                    .put(JSONObject().put("lat", DESTINATION.latitude).put("lon", DESTINATION.longitude).put("type", "break")),
            )
            .put("costing", "auto")
            .put("units", "kilometers")
            .put("language", "ko-KR")
        val connection = (URL(VALHALLA_ROUTE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Client-Id", "starlog-navigation-prototype")
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(requestBody.toString()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $body")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRoute(response: JSONObject): RouteResult {
        val trip = response.getJSONObject("trip")
        val summary = trip.getJSONObject("summary")
        val leg = trip.getJSONArray("legs").getJSONObject(0)
        val maneuverArray = leg.getJSONArray("maneuvers")
        val maneuvers = buildList {
            for (index in 0 until maneuverArray.length()) {
                val item = maneuverArray.getJSONObject(index)
                add(item.optString("instruction", "안내 정보 없음"))
            }
        }
        return RouteResult(
            points = decodePolyline6(leg.getString("shape")),
            distanceKm = summary.getDouble("length"),
            timeSeconds = summary.getDouble("time"),
            maneuvers = maneuvers,
        )
    }

    private fun renderRoute(route: RouteResult) {
        val style = mapStyle ?: return
        val geoJson = buildRouteGeoJson(route.points)
        val existingSource = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
        if (existingSource == null) {
            style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, geoJson))
            style.addLayer(
                LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    lineColor("#10172B"),
                    lineWidth(30f),
                    lineOpacity(0.72f),
                    lineCap(LINE_CAP_ROUND),
                    lineJoin(LINE_JOIN_ROUND),
                ),
            )
            style.addLayer(
                LineLayer(ROUTE_GLOW_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    lineColor("#0068E8"),
                    lineWidth(24f),
                    lineOpacity(0.48f),
                    lineCap(LINE_CAP_ROUND),
                    lineJoin(LINE_JOIN_ROUND),
                ),
            )
            style.addLayer(
                LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    lineColor("#087DFF"),
                    lineWidth(15f),
                    lineOpacity(1f),
                    lineCap(LINE_CAP_ROUND),
                    lineJoin(LINE_JOIN_ROUND),
                ),
            )
        } else {
            existingSource.setGeoJson(geoJson)
        }
        val minutes = (route.timeSeconds / 60.0).toInt().coerceAtLeast(1)
        showStatus("3D 주행 · %.2f km · 약 %d분 · 안내 %d개".format(route.distanceKm, minutes, route.maneuvers.size), false)
        instructionView.text = route.maneuvers.firstOrNull() ?: "경로 안내 없음"
        mapView.getMapAsync { map ->
            val snappedStart = route.points.firstOrNull() ?: START
            val lookAhead = findLookAheadPoint(route.points, 310.0)
            val routeBearing = bearingDegrees(snappedStart, lookAhead)
            val density = resources.displayMetrics.density
            map.setPadding(0, 0, 0, (115 * density).toInt())
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(lookAhead)
                        .zoom(17.25)
                        .tilt(48.0)
                        .bearing(routeBearing)
                        .build(),
                ),
                900,
            )
        }
    }

    private fun findLookAheadPoint(points: List<LatLng>, targetMeters: Double): LatLng {
        if (points.isEmpty()) return START
        var traveledMeters = 0.0
        for (index in 1 until points.size) {
            traveledMeters += distanceMeters(points[index - 1], points[index])
            if (traveledMeters >= targetMeters) return points[index]
        }
        return points.last()
    }

    private fun distanceMeters(from: LatLng, to: LatLng): Double {
        val earthRadiusMeters = 6_371_000.0
        val latitudeDelta = Math.toRadians(to.latitude - from.latitude)
        val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
        val fromLatitude = Math.toRadians(from.latitude)
        val toLatitude = Math.toRadians(to.latitude)
        val a = kotlin.math.sin(latitudeDelta / 2) * kotlin.math.sin(latitudeDelta / 2) +
            kotlin.math.cos(fromLatitude) * kotlin.math.cos(toLatitude) *
            kotlin.math.sin(longitudeDelta / 2) * kotlin.math.sin(longitudeDelta / 2)
        return earthRadiusMeters * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    private fun bearingDegrees(from: LatLng, to: LatLng): Double {
        val fromLatitude = Math.toRadians(from.latitude)
        val toLatitude = Math.toRadians(to.latitude)
        val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
        val y = kotlin.math.sin(longitudeDelta) * kotlin.math.cos(toLatitude)
        val x = kotlin.math.cos(fromLatitude) * kotlin.math.sin(toLatitude) -
            kotlin.math.sin(fromLatitude) * kotlin.math.cos(toLatitude) * kotlin.math.cos(longitudeDelta)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    }

    private fun showStatus(message: String, busy: Boolean) {
        statusView.text = message
        routeButton.isEnabled = !busy && mapStyle != null
    }

    private fun buildRouteGeoJson(points: List<LatLng>): String {
        val coordinates = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        return """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
    }

    private fun decodePolyline6(encoded: String): List<LatLng> {
        val result = ArrayList<LatLng>()
        var index = 0
        var latitude = 0L
        var longitude = 0L
        while (index < encoded.length) {
            val latitudeResult = decodePolylineValue(encoded, index)
            index = latitudeResult.nextIndex
            latitude += latitudeResult.delta
            val longitudeResult = decodePolylineValue(encoded, index)
            index = longitudeResult.nextIndex
            longitude += longitudeResult.delta
            result.add(LatLng(latitude / 1_000_000.0, longitude / 1_000_000.0))
        }
        return result
    }

    private fun decodePolylineValue(encoded: String, startIndex: Int): DecodedValue {
        var index = startIndex
        var shift = 0
        var value = 0L
        while (index < encoded.length) {
            val current = encoded[index++].code - 63
            value = value or ((current and 0x1f).toLong() shl shift)
            shift += 5
            if (current < 0x20) break
        }
        val delta = if ((value and 1L) != 0L) (value shr 1).inv() else value shr 1
        return DecodedValue(delta, index)
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { worker.shutdownNow(); mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    private data class RouteResult(val points: List<LatLng>, val distanceKm: Double, val timeSeconds: Double, val maneuvers: List<String>)
    private data class DecodedValue(val delta: Long, val nextIndex: Int)

    private class AtmosphereView(context: android.content.Context) : View(context) {
        private val atmospherePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            atmospherePaint.shader = LinearGradient(
                0f,
                0f,
                0f,
                h.toFloat(),
                intArrayOf(
                    Color.argb(255, 55, 139, 194),
                    Color.argb(255, 72, 153, 204),
                    Color.argb(245, 132, 191, 221),
                    Color.argb(150, 200, 222, 226),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.18f, 0.23f, 0.28f, 0.34f),
                Shader.TileMode.CLAMP,
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), atmospherePaint)
        }
    }

    private companion object {
        const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val VALHALLA_ROUTE_URL = "https://valhalla1.openstreetmap.de/route"
        const val ROUTE_SOURCE_ID = "starlog-route-source"
        const val ROUTE_CASING_LAYER_ID = "starlog-route-casing-layer"
        const val ROUTE_GLOW_LAYER_ID = "starlog-route-glow-layer"
        const val ROUTE_LAYER_ID = "starlog-route-layer"
        val START = LatLng(37.497952, 127.027619)
        val DESTINATION = LatLng(37.504501, 127.048962)
    }
}
