package com.example.starlog2.integration

import com.example.starlog2.contracts.*
import com.example.starlog2.navigation.Route as LegacyRoute
import com.example.starlog2.navigation.Telemetry as LegacyTelemetry
import com.example.starlog2.navigation.GuidanceState as LegacyGuidance

/** PRE-G1 compatibility only; remove with L1's canonical-type migration PR. */
object NavigationContractAdapter {
    fun route(value: LegacyRoute): Route = Route(value.routeId, value.requestId,
        value.points.map { LatLon(it.lat, it.lon) }, value.distanceM, value.durationS,
        value.maneuvers.map { Maneuver(it.index, it.beginPointIndex, it.endPointIndex, it.instruction,
            it.typeCode, it.lengthM, it.durationS) })

    fun telemetry(value: LegacyTelemetry): Telemetry = Telemetry(value.seq, value.observedAtMs, value.monotonicMs,
        SourceMode.valueOf(value.sourceMode.name), value.providerId,
        TimedValue(value.location.value?.let {
            LocationFix(LatLon(it.point.lat, it.point.lon), it.accuracyM, it.headingDeg, Reference.valueOf(it.headingReference.name))
        }, Quality.valueOf(value.location.quality.name), value.location.ageMs),
        TimedValue(value.speedMps.value, Quality.valueOf(value.speedMps.quality.name), value.speedMps.ageMs),
        TimedValue(value.gear.value?.let { Gear.valueOf(it.name) }, Quality.valueOf(value.gear.quality.name), value.gear.ageMs),
        TimedValue(value.ignitionOn.value, Quality.valueOf(value.ignitionOn.quality.name), value.ignitionOn.ageMs))

    fun guidance(value: LegacyGuidance): GuidanceState = GuidanceState(value.routeId, GuidanceStatus.valueOf(value.status.name),
        value.snappedPoint?.let { LatLon(it.lat, it.lon) }, value.alongM, value.remainingM, value.remainingS,
        value.maneuverIndex, value.nextInstruction, value.distanceToManeuverM)
}
