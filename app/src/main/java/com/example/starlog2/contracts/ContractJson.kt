package com.example.starlog2.contracts

import org.json.JSONArray
import org.json.JSONObject

/** Strict native wire boundary. Optional fields must exist, with explicit JSON null. */
object ContractJson {
    const val MAX_BRIDGE_BYTES = 1_048_576

    fun encode(value: Route): String = route(value).toString()
    fun encode(value: Telemetry): String = telemetry(value).toString()
    fun encode(value: RenderEnvelope): String = envelope(value).toString().also(::bounded)
    fun decodeRoute(json: String): Route = readRoute(JSONObject(json))
    fun decodeTelemetry(json: String): Telemetry = readTelemetry(JSONObject(json))
    fun decodeRender(json: String): RenderEnvelope {
        bounded(json)
        val o = JSONObject(json)
        val p = o.obj("payload")
        return RenderEnvelope(o.int("version"), o.string("type"), o.string("messageId"), o.long("seq"),
            RenderState(p.enum("sourceMode"), p.nullable("route") { readRenderRoute(it as JSONObject) },
                p.nullable("pose") { v -> (v as JSONObject).let {
                    RenderPose(it.doubles("coordinate"), it.nullableDouble("headingDeg"), it.enum("quality"))
                } }, p.obj("camera").let { CameraConfig(it.double("pitchDeg"), it.double("zoom"), it.double("lookAheadM")) },
                p.nullable("guidance") { readGuidance(it as JSONObject) }, readExperience(p.obj("experience")),
                p.array("skyItems").objects().map { SkyItem(it.string("starId"), it.double("relativeBearingDeg"), it.double("distanceM"), it.bool("selected")) },
                p.nullable("memory") { v -> (v as JSONObject).let {
                    MemoryCard(it.string("starId"), it.nullableString("title"), it.nullableString("placeLabel"), it.long("dateAtMs"), it.nullableString("photoRef"))
                } }, p.nullable("error") { readError(it as JSONObject) }))
    }

    /** Validates the common event envelope, without performing UI/session actions. */
    fun decodeEvent(json: String): JSONObject {
        bounded(json)
        val o = JSONObject(json)
        require(o.int("version") == 1) { "CONTRACT_VERSION_MISMATCH" }
        require(o.string("messageId").isNotBlank())
        val p = o.obj("payload")
        when (o.string("type")) {
            "ready" -> { require(p.string("rendererVersion").isNotBlank()); p.bool("webgl") }
            "ack" -> { require(p.string("receivedMessageId").isNotBlank()); p.long("seq") }
            "error" -> readError(p)
            "userAction" -> ExperienceCommand(p.enum("action"), p.nullableString("starId"))
            else -> error("INVALID_INPUT")
        }
        return o
    }

    private fun bounded(s: String) = require(s.toByteArray(Charsets.UTF_8).size <= MAX_BRIDGE_BYTES) { "PAYLOAD_TOO_LARGE" }
    private fun obj(vararg fields: Pair<String, Any?>): JSONObject = JSONObject().apply {
        fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }
    private fun point(p: LatLon) = obj("lat" to p.lat, "lon" to p.lon)
    private fun error(e: AppError) = obj("code" to e.code, "messageKey" to e.messageKey, "retryable" to e.retryable, "requestId" to e.requestId)
    private fun maneuver(m: Maneuver) = obj("index" to m.index, "beginPointIndex" to m.beginPointIndex,
        "endPointIndex" to m.endPointIndex, "instruction" to m.instruction, "typeCode" to m.typeCode,
        "lengthM" to m.lengthM, "durationS" to m.durationS)
    private fun route(r: Route) = obj("routeId" to r.routeId, "requestId" to r.requestId,
        "points" to JSONArray(r.points.map(::point)), "distanceM" to r.distanceM, "durationS" to r.durationS,
        "maneuvers" to JSONArray(r.maneuvers.map(::maneuver)))
    private fun <T> timed(v: TimedValue<T>, encode: (T) -> Any) = obj(
        "value" to v.value?.let(encode), "quality" to v.quality.name, "ageMs" to v.ageMs)
    private fun telemetry(t: Telemetry) = obj("seq" to t.seq, "observedAtMs" to t.observedAtMs,
        "monotonicMs" to t.monotonicMs, "sourceMode" to t.sourceMode.name, "providerId" to t.providerId,
        "location" to timed(t.location) { obj("point" to point(it.point), "accuracyM" to it.accuracyM,
            "headingDeg" to it.headingDeg, "headingReference" to it.headingReference.name) },
        "speedMps" to timed(t.speedMps) { it }, "gear" to timed(t.gear) { it.name }, "ignitionOn" to timed(t.ignitionOn) { it })
    private fun guidance(g: GuidanceState) = obj("routeId" to g.routeId, "status" to g.status.name,
        "snappedPoint" to g.snappedPoint?.let(::point), "alongM" to g.alongM, "remainingM" to g.remainingM,
        "remainingS" to g.remainingS, "maneuverIndex" to g.maneuverIndex, "nextInstruction" to g.nextInstruction,
        "distanceToManeuverM" to g.distanceToManeuverM)
    private fun experience(e: ExperienceState) = obj("mode" to e.mode.name, "selectedStarId" to e.selectedStarId,
        "stopElapsedMs" to e.stopElapsedMs, "entryEventId" to e.entryEventId)
    private fun envelope(e: RenderEnvelope): JSONObject {
        val p = e.payload
        return obj("version" to e.version, "type" to e.type, "messageId" to e.messageId, "seq" to e.seq,
            "payload" to obj("sourceMode" to p.sourceMode.name,
                "route" to p.route?.let { obj("routeId" to it.routeId, "coordinates" to JSONArray(it.coordinates)) },
                "pose" to p.pose?.let { obj("coordinate" to JSONArray(it.coordinate), "headingDeg" to it.headingDeg, "quality" to it.quality.name) },
                "camera" to obj("pitchDeg" to p.camera.pitchDeg, "zoom" to p.camera.zoom, "lookAheadM" to p.camera.lookAheadM),
                "guidance" to p.guidance?.let(::guidance), "experience" to experience(p.experience),
                "skyItems" to JSONArray(p.skyItems.map { obj("starId" to it.starId, "relativeBearingDeg" to it.relativeBearingDeg,
                    "distanceM" to it.distanceM, "selected" to it.selected) }),
                "memory" to p.memory?.let { obj("starId" to it.starId, "title" to it.title, "placeLabel" to it.placeLabel,
                    "dateAtMs" to it.dateAtMs, "photoRef" to it.photoRef) }, "error" to p.error?.let(::error)))
    }
    private fun readPoint(o: JSONObject) = LatLon(o.double("lat"), o.double("lon"))
    private fun readError(o: JSONObject) = AppError(o.string("code"), o.string("messageKey"), o.bool("retryable"), o.nullableString("requestId"))
    private fun readRoute(o: JSONObject) = Route(o.string("routeId"), o.string("requestId"), o.array("points").objects().map(::readPoint),
        o.double("distanceM"), o.double("durationS"), o.array("maneuvers").objects().map {
            Maneuver(it.int("index"), it.int("beginPointIndex"), it.int("endPointIndex"), it.string("instruction"),
                it.nullable("typeCode", ::smallInteger), it.double("lengthM"), it.double("durationS"))
        })
    private fun <T> readTimed(o: JSONObject, decode: (Any) -> T) = TimedValue(o.nullable("value", decode), o.enum<Quality>("quality"), o.long("ageMs"))
    private fun readTelemetry(o: JSONObject) = Telemetry(o.long("seq"), o.long("observedAtMs"), o.long("monotonicMs"),
        o.enum("sourceMode"), o.string("providerId"), readTimed(o.obj("location")) { v -> (v as JSONObject).let {
            LocationFix(readPoint(it.obj("point")), it.nullableDouble("accuracyM"), it.nullableDouble("headingDeg"), it.enum("headingReference"))
        } }, readTimed(o.obj("speedMps"), ::number), readTimed(o.obj("gear")) { Gear.valueOf(it as String) },
        readTimed(o.obj("ignitionOn")) { it as Boolean })
    private fun readRenderRoute(o: JSONObject) = RenderRoute(o.string("routeId"), o.array("coordinates").let { a ->
        (0 until a.length()).map { i -> a.getJSONArray(i).numbers() }
    })
    private fun readGuidance(o: JSONObject) = GuidanceState(o.nullableString("routeId"), o.enum("status"),
        o.nullable("snappedPoint") { readPoint(it as JSONObject) }, o.nullableDouble("alongM"), o.nullableDouble("remainingM"),
        o.nullableDouble("remainingS"), o.nullable("maneuverIndex", ::smallInteger), o.nullableString("nextInstruction"), o.nullableDouble("distanceToManeuverM"))
    private fun readExperience(o: JSONObject) = ExperienceState(o.enum("mode"), o.nullableString("selectedStarId"), o.long("stopElapsedMs"), o.nullableString("entryEventId"))

    private fun number(v: Any): Double { require(v is Number); return v.toDouble().also { require(it.isFinite()) } }
    private fun integer(v: Any): Long = number(v).let { require(it >= 0 && it <= MAX_SAFE_INTEGER && it % 1.0 == 0.0); it.toLong() }
    private fun JSONObject.double(k: String) = number(get(k))
    private fun JSONObject.long(k: String) = integer(get(k))
    private fun smallInteger(v: Any) = integer(v).also { require(it <= Int.MAX_VALUE) }.toInt()
    private fun JSONObject.int(k: String) = smallInteger(get(k))
    private fun JSONObject.string(k: String): String = get(k).let { require(it is String); it }
    private fun JSONObject.bool(k: String): Boolean = get(k).let { require(it is Boolean); it }
    private fun JSONObject.obj(k: String) = get(k) as JSONObject
    private fun JSONObject.array(k: String) = get(k) as JSONArray
    private fun JSONObject.doubles(k: String) = array(k).numbers()
    private fun JSONArray.numbers() = (0 until length()).map { number(get(it)) }
    private fun JSONArray.objects() = (0 until length()).map { get(it) as JSONObject }
    private inline fun <reified T: Enum<T>> JSONObject.enum(k: String): T = enumValueOf(string(k))
    private fun <T> JSONObject.nullable(k: String, decode: (Any) -> T): T? = get(k).let { if (it === JSONObject.NULL) null else decode(it) }
    private fun JSONObject.nullableString(k: String) = nullable(k) { require(it is String); it }
    private fun JSONObject.nullableDouble(k: String) = nullable(k, ::number)
}
