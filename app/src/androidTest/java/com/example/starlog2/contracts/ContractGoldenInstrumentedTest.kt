package com.example.starlog2.contracts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.starlog2.integration.NavigationContractAdapter
import com.example.starlog2.navigation.NavigationGeometry
import com.example.starlog2.navigation.LatLon as LegacyPoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContractGoldenInstrumentedTest {
    private fun fixture(name: String) = InstrumentationRegistry.getInstrumentation().context.assets
        .open("contracts/$name.json").bufferedReader().use { it.readText() }

    @Test fun routeGoldenRoundTripPreservesUnitsAndIndices() {
        val json = fixture("route-valid"); val route = ContractJson.decodeRoute(json)
        same(JSONObject(json), JSONObject(ContractJson.encode(route)))
        assertEquals(199.41, route.distanceM, 0.0); assertEquals(45.0, route.durationS, 0.0)
    }
    @Test fun invalidRouteGoldensAreRejected() {
        val cases = JSONArray(fixture("route-invalid"))
        repeat(cases.length()) { reject { ContractJson.decodeRoute(cases.getJSONObject(it).getJSONObject("input").toString()) } }
    }
    @Test fun telemetryGoldenAndUnknownSpeedArePreserved() {
        for (name in listOf("telemetry-driving", "telemetry-stale")) {
            val json = fixture(name); val sample = ContractJson.decodeTelemetry(json)
            same(JSONObject(json), JSONObject(ContractJson.encode(sample)))
            assertEquals(SourceMode.REPLAY, sample.sourceMode)
        }
        val stale = ContractJson.decodeTelemetry(fixture("telemetry-stale"))
        assertNull(stale.speedMps.value); assertEquals(Quality.STALE, stale.location.quality)
    }
    @Test fun invalidTelemetryGoldensAreRejected() {
        val cases = JSONArray(fixture("telemetry-invalid"))
        repeat(cases.length()) { reject { ContractJson.decodeTelemetry(cases.getJSONObject(it).getJSONObject("input").toString()) } }
    }
    @Test fun stopFixturesEncodeExactMonotonicBoundaryWithoutClaimingDomainImplemented() {
        for (n in listOf(179_999L, 180_000L)) {
            val data = JSONObject(fixture("telemetry-stop-$n"))
            val samples = data.getJSONArray("samples")
            val start = ContractJson.decodeTelemetry(samples.getJSONObject(0).toString())
            val end = ContractJson.decodeTelemetry(samples.getJSONObject(1).toString())
            assertEquals(n, end.monotonicMs - start.monotonicMs)
            assertEquals(n, data.getJSONObject("expected").getLong("stopElapsedMs"))
            assertEquals(Gear.P, end.gear.value)
        }
    }
    @Test fun fullAndNullRenderGoldenRoundTripsPreserveExplicitNullAndEscapes() {
        for (name in listOf("render-full", "render-null")) {
            val json = fixture(name); val decoded = ContractJson.decodeRender(json)
            same(JSONObject(json), JSONObject(ContractJson.encode(decoded)))
        }
        val p = JSONObject(ContractJson.encode(ContractJson.decodeRender(fixture("render-null")))).getJSONObject("payload")
        for (name in listOf("route", "pose", "guidance", "memory", "error")) {
            assertTrue(p.has(name)); assertTrue(p.isNull(name))
        }
    }
    @Test fun renderMissingFieldsReversedCoordinateEnumsAndLargePayloadRejected() {
        val missing = JSONObject(fixture("render-null")); missing.getJSONObject("payload").remove("error")
        reject { ContractJson.decodeRender(missing.toString()) }
        val reversed = JSONObject(fixture("render-full"))
        reversed.getJSONObject("payload").getJSONObject("pose").put("coordinate", JSONArray(listOf(37.5, 127)))
        reject { ContractJson.decodeRender(reversed.toString()) }
        val bad = JSONObject(fixture("render-null")); bad.getJSONObject("payload").put("sourceMode", "MOCK")
        reject { ContractJson.decodeRender(bad.toString()) }
        reject { ContractJson.decodeRender(" ".repeat(ContractJson.MAX_BRIDGE_BYTES + 1)) }
        val overflow = JSONObject(fixture("route-valid")); overflow.getJSONArray("maneuvers").getJSONObject(0).put("typeCode", 4_294_967_296L)
        reject { ContractJson.decodeRoute(overflow.toString()) }
    }
    @Test fun bridgeEnvelopeErrorsHaveNoSideEffects() {
        val data = JSONObject(fixture("bridge-errors"))
        val valid = data.getJSONArray("valid"); repeat(valid.length()) { ContractJson.decodeEvent(valid.getJSONObject(it).toString()) }
        val invalid = data.getJSONArray("invalid"); repeat(invalid.length()) { reject { ContractJson.decodeEvent(invalid.getJSONObject(it).toString()) } }
    }
    @Test fun bearingAndGeometryGoldensMatchCurrentEngineWithDeclaredTolerance() {
        val wrap = JSONObject(fixture("heading-wrap"))
        assertEquals(0.0, NavigationGeometry.smoothBearing(wrap.getDouble("fromDeg"), wrap.getDouble("toDeg"), .5), wrap.getDouble("toleranceDeg"))
        val geometry = JSONObject(fixture("geometry")); val cases = geometry.getJSONArray("cases")
        repeat(cases.length()) { index ->
            val c = cases.getJSONObject(index); val coordinates = c.getJSONArray("points")
            fun point(a: JSONArray) = LegacyPoint(a.getDouble(1), a.getDouble(0))
            val points = (0 until coordinates.length()).map { point(coordinates.getJSONArray(it)) }
            val length = points.zipWithNext().sumOf { (a,b) -> NavigationGeometry.distanceM(a,b) }
            val route = com.example.starlog2.navigation.Route("g", "g", points, length, 60.0, emptyList())
            val sample = ContractJson.decodeTelemetry(fixture("telemetry-driving"))
            val fix = sample.location.value!!.copy(point = point(c.getJSONArray("sample")).let { LatLon(it.lat, it.lon) })
            val legacy = com.example.starlog2.navigation.Telemetry(sample.seq, sample.observedAtMs, sample.monotonicMs,
                com.example.starlog2.navigation.SourceMode.REPLAY, sample.providerId,
                com.example.starlog2.navigation.TimedValue(com.example.starlog2.navigation.LocationFix(LegacyPoint(fix.point.lat,fix.point.lon),5.0,0.0,
                    com.example.starlog2.navigation.Reference.TRUE_NORTH),com.example.starlog2.navigation.Quality.VALID,0),
                com.example.starlog2.navigation.TimedValue(10.0,com.example.starlog2.navigation.Quality.VALID,0),
                com.example.starlog2.navigation.TimedValue(com.example.starlog2.navigation.Gear.D,com.example.starlog2.navigation.Quality.VALID,0),
                com.example.starlog2.navigation.TimedValue(true,com.example.starlog2.navigation.Quality.VALID,0))
            val engine = com.example.starlog2.navigation.DefaultGuidanceEngine(); engine.setRoute(route)
            val state = NavigationContractAdapter.guidance(engine.update(legacy))
            assertEquals(c.getString("name"), c.getDouble("expectedAlongM"), state.alongM!!, geometry.getDouble("toleranceM"))
        }
    }
    @Test fun emptyMemoryAndRegressingSequenceFixtureRemainExplicit() {
        val empty = JSONObject(fixture("memory-empty")); assertEquals(0, empty.getJSONArray("stars").length()); assertTrue(empty.isNull("expectedMemory"))
        val sample = ContractJson.decodeTelemetry(fixture("telemetry-driving")); val cursor = TelemetryCursor()
        assertTrue(cursor.accept(sample.copy(seq = 2, monotonicMs = 10)))
        assertFalse(cursor.accept(sample.copy(seq = 1, monotonicMs = 11)))
        assertFalse(cursor.accept(sample.copy(seq = 3, monotonicMs = 9)))
        assertTrue(cursor.accept(sample.copy(seq = 3, monotonicMs = 10)))
    }
    private fun reject(block: () -> Any?) {
        try { block(); fail("Expected invalid JSON to be rejected") }
        catch (e: Exception) { assertFalse(e is java.io.IOException) }
    }
    private fun same(expected: Any, actual: Any) {
        when (expected) {
            is JSONObject -> {
                assertTrue(actual is JSONObject); actual as JSONObject
                val keys = expected.keys().asSequence().toSet(); assertEquals(keys, actual.keys().asSequence().toSet())
                keys.forEach { same(expected.get(it), actual.get(it)) }
            }
            is JSONArray -> {
                assertTrue(actual is JSONArray); actual as JSONArray; assertEquals(expected.length(), actual.length())
                repeat(expected.length()) { same(expected.get(it), actual.get(it)) }
            }
            is Number -> { assertTrue(actual is Number); assertEquals(expected.toDouble(), (actual as Number).toDouble(), 0.0) }
            else -> assertEquals(expected, actual)
        }
    }
}
