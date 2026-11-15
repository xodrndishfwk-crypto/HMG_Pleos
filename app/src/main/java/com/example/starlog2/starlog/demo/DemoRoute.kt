package com.example.starlog2.starlog.demo

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.starlog.journey.JourneyLeg

/** A deterministic, clearly identified REPLAY route used by the submission demonstration. */
data class DemoRoute(
    val id: String,
    val constellationLabel: String,
    val stops: List<SubmissionDemoJourney.DemoStop>,
) {
    init {
        require(id.matches(Regex("[a-z0-9-]+"))) { "route id must be a lowercase slug" }
        require(stops.size >= 2) { "a demo route needs at least two stops" }
    }

    /** Trip ids embed the route id so a restored journey keeps replaying the route it started with. */
    fun tripIdPrefix(): String = "submission-$id-"

    companion object {
        /** Original Gumi → Busan → return route. Kept as the default for existing tests and journeys. */
        val GUMI_BUSAN = DemoRoute(
            id = "gumi-busan",
            constellationLabel = "구미-부산 왕복 시연",
            stops = listOf(
                SubmissionDemoJourney.DemoStop("구미", LatLon(36.1195, 128.3446), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("대구", LatLon(35.8714, 128.6014), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("경주", LatLon(35.8562, 129.2247), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("울산", LatLon(35.5384, 129.3114), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("부산", LatLon(35.1796, 129.0756), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("울산", LatLon(35.5384, 129.3114), JourneyLeg.RETURN),
                SubmissionDemoJourney.DemoStop("경주", LatLon(35.8562, 129.2247), JourneyLeg.RETURN),
                SubmissionDemoJourney.DemoStop("김해", LatLon(35.2285, 128.8894), JourneyLeg.RETURN_NEW),
                SubmissionDemoJourney.DemoStop("구미", LatLon(36.1195, 128.3446), JourneyLeg.RETURN),
            ),
        )

        /** Daegu → five waypoints → Mungyeong Saejae, matching the submission video storyboard. */
        val MUNGYEONG_SAEJAE = DemoRoute(
            id = "mungyeong",
            constellationLabel = "문경 새재자리",
            stops = listOf(
                SubmissionDemoJourney.DemoStop("대구 출발", LatLon(35.8797, 128.6286), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("팔공산", LatLon(36.0164, 128.6947), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("구미", LatLon(36.1195, 128.3446), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("김천", LatLon(36.1398, 128.1136), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("상주", LatLon(36.4110, 128.1590), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("이화령 고개", LatLon(36.7769, 128.0595), JourneyLeg.OUTBOUND),
                SubmissionDemoJourney.DemoStop("문경 새재", LatLon(36.7633, 128.0778), JourneyLeg.OUTBOUND),
            ),
        )

        val ALL = listOf(GUMI_BUSAN, MUNGYEONG_SAEJAE)

        /** Resolves the route a trip was started with; unknown ids fall back to [fallback]. */
        fun forTrip(tripId: String, fallback: DemoRoute): DemoRoute =
            ALL.firstOrNull { tripId.startsWith(it.tripIdPrefix()) } ?: fallback
    }
}
