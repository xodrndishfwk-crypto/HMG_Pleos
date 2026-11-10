package com.example.starlog2.contracts

private fun coordinate(value: List<Double>) {
    require(value.size == 2); LatLon(value[1], value[0])
}
data class RenderRoute(val routeId: String, val coordinates: List<List<Double>>) {
    init { require(routeId.isNotBlank() && coordinates.size in 2..50_000); coordinates.forEach(::coordinate) }
}
data class RenderPose(val coordinate: List<Double>, val headingDeg: Double?, val quality: Quality) {
    init { coordinate(coordinate); headingDeg?.let(::heading) }
}
data class CameraConfig(val pitchDeg: Double, val zoom: Double, val lookAheadM: Double) {
    init {
        require(pitchDeg.isFinite() && pitchDeg in 0.0..85.0)
        require(zoom.isFinite() && zoom in 0.0..24.0); nonNegative(lookAheadM)
    }
}
data class MemoryCard(
    val starId: String, val title: String?, val placeLabel: String?, val dateAtMs: Long, val photoRef: String?,
) { init { require(starId.isNotBlank()); safeCounter(dateAtMs) } }
data class RenderState(
    val sourceMode: SourceMode, val route: RenderRoute?, val pose: RenderPose?, val camera: CameraConfig,
    val guidance: GuidanceState?, val experience: ExperienceState, val skyItems: List<SkyItem>,
    val memory: MemoryCard?, val error: AppError?,
)
data class RenderEnvelope(val version: Int, val type: String, val messageId: String, val seq: Long, val payload: RenderState) {
    init { require(version == 1 && type == "renderState" && messageId.isNotBlank()); safeCounter(seq) }
}
