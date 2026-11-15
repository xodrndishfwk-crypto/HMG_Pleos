package com.example.starlog2.starlog

import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.LocationFix
import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.Reference
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Telemetry
import com.example.starlog2.contracts.TimedValue
import com.example.starlog2.contracts.Visit
import com.example.starlog2.starlog.clock.Clock

class FakeClock(
    var currentMonotonicMs: Long = 10_000L,
    var currentEpochMs: Long = 1_700_000_000_000L,
) : Clock {
    override fun monotonicMs(): Long = currentMonotonicMs

    override fun epochMs(): Long = currentEpochMs
}

object StarLogFixtures {

    val SEOUL_CITY_HALL = LatLon(37.5665, 126.9780)
    // Approx 111.195m due north of SEOUL_CITY_HALL
    val GYEONGBOKGUNG_SOUTH = LatLon(37.5675, 126.9780)
    // Approx 1111.95m due north of SEOUL_CITY_HALL
    val GYEONGBOKGUNG_NORTH = LatLon(37.5765, 126.9780)
    // Approx 88.3m due east of SEOUL_CITY_HALL
    val EULJIRO_EAST = LatLon(37.5665, 126.9790)

    fun createTelemetry(
        seq: Long = 1L,
        observedAtMs: Long = 1_700_000_000_000L,
        monotonicMs: Long = 10_000L,
        sourceMode: SourceMode = SourceMode.LIVE,
        providerId: String = "fake-telemetry",
        point: LatLon = SEOUL_CITY_HALL,
        accuracyM: Double? = 5.0,
        headingDeg: Double? = 0.0,
        headingReference: Reference = Reference.TRUE_NORTH,
        locationQuality: Quality = Quality.VALID,
        locationAgeMs: Long = 100L,
        includeLocationValue: Boolean = true,
        speedMps: Double? = 0.0,
        speedQuality: Quality = Quality.VALID,
        speedAgeMs: Long = 100L,
        gear: Gear? = Gear.P,
        gearQuality: Quality = Quality.VALID,
        gearAgeMs: Long = 100L,
        ignitionOn: Boolean = true,
    ): Telemetry {
        val fix = if (includeLocationValue && locationQuality != Quality.UNAVAILABLE) {
            LocationFix(
                point = point,
                accuracyM = accuracyM,
                headingDeg = headingDeg,
                headingReference = headingReference,
            )
        } else {
            null
        }
        val effectiveLocationQuality = if (fix == null && locationQuality == Quality.VALID) {
            Quality.UNAVAILABLE
        } else {
            locationQuality
        }
        val effectiveSpeedQuality = if (speedMps == null && speedQuality == Quality.VALID) {
            Quality.UNAVAILABLE
        } else {
            speedQuality
        }
        val effectiveGearQuality = if (gear == null && gearQuality == Quality.VALID) {
            Quality.UNAVAILABLE
        } else {
            gearQuality
        }

        return Telemetry(
            seq = seq,
            observedAtMs = observedAtMs,
            monotonicMs = monotonicMs,
            sourceMode = sourceMode,
            providerId = providerId,
            location = TimedValue(
                value = fix,
                quality = effectiveLocationQuality,
                ageMs = locationAgeMs,
            ),
            speedMps = TimedValue(
                value = speedMps,
                quality = effectiveSpeedQuality,
                ageMs = speedAgeMs,
            ),
            gear = TimedValue(
                value = gear,
                quality = effectiveGearQuality,
                ageMs = gearAgeMs,
            ),
            ignitionOn = TimedValue(
                value = ignitionOn,
                quality = Quality.VALID,
                ageMs = 100L,
            ),
        )
    }

    fun createMemoryStar(
        id: String = "star-1",
        visitId: String = "visit-1",
        point: LatLon = GYEONGBOKGUNG_SOUTH,
        createdAtMs: Long = 1_700_000_000_000L,
        title: String? = null,
        photoRef: String? = null,
    ): MemoryStar = MemoryStar(
        id = id,
        visitId = visitId,
        point = point,
        createdAtMs = createdAtMs,
        title = title,
        photoRef = photoRef,
    )

    fun createVisit(
        id: String = "visit-1",
        dedupeKey: String = "visit_LIVE_37.56650_126.97800_1700000000000",
        tripId: String? = null,
        point: LatLon = SEOUL_CITY_HALL,
        startAtMs: Long = 1_700_000_000_000L,
        endAtMs: Long = 1_700_000_600_000L,
        sourceMode: SourceMode = SourceMode.LIVE,
        label: String? = null,
    ): Visit = Visit(
        id = id,
        dedupeKey = dedupeKey,
        tripId = tripId,
        point = point,
        startAtMs = startAtMs,
        endAtMs = endAtMs,
        sourceMode = sourceMode,
        label = label,
    )
}
