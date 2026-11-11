package com.example.starlog2.starlog.bearing

import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.LocationFix
import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.Reference
import com.example.starlog2.contracts.SkyItem
import com.example.starlog2.contracts.TimedValue
import com.example.starlog2.starlog.model.BearingConfig
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin Bearing and Field-of-View Projector.
 * 0% Android SDK dependency.
 *
 * Computes relative vehicle bearings against True North heading, wraps angles to [-180.0, 180.0),
 * excludes undefined zero-distance bearings, and filters/sorts candidates within +-30 degrees FOV.
 */
class BearingProjector(
    private val config: BearingConfig = BearingConfig(),
) {
    constructor(fovHalfAngleDeg: Double) : this(BearingConfig(fovHalfAngleDeg = fovHalfAngleDeg))

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0
        private const val ZERO_DISTANCE_EPSILON_M = 0.01

        /**
         * Normalizes any angle difference (in degrees) to the half-open interval [-180.0, 180.0).
         */
        fun normalizeAngleDiff(angleDeg: Double): Double {
            if (!angleDeg.isFinite()) return 0.0
            var diff = (angleDeg + 180.0) % 360.0
            if (diff < 0.0) diff += 360.0
            return diff - 180.0
        }

        /**
         * Normalizes (targetBearingDeg - vehicleHeadingDeg) into [-180.0, 180.0).
         */
        fun normalizeRelativeBearing(targetBearingDeg: Double, vehicleHeadingDeg: Double): Double =
            normalizeAngleDiff(targetBearingDeg - vehicleHeadingDeg)

        /**
         * Computes Great-Circle Distance (Haversine) in meters between two coordinates.
         */
        fun distanceMeters(from: LatLon, to: LatLon): Double {
            val dLat = Math.toRadians(to.lat - from.lat)
            val dLon = Math.toRadians(to.lon - from.lon)
            val lat1 = Math.toRadians(from.lat)
            val lat2 = Math.toRadians(to.lat)

            val a = sin(dLat / 2.0).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2.0).pow(2.0)
            val clampedA = a.coerceIn(0.0, 1.0)
            val c = 2.0 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
            return EARTH_RADIUS_M * c
        }

        /**
         * Computes forward azimuth (true bearing in [0.0, 360.0)) from [from] to [to].
         * Returns `null` if [from] and [to] are identical or closer than [ZERO_DISTANCE_EPSILON_M],
         * because bearing between two identical points is mathematically undefined.
         */
        fun initialBearingDeg(from: LatLon, to: LatLon): Double? {
            if (distanceMeters(from, to) < ZERO_DISTANCE_EPSILON_M) {
                return null
            }
            val lat1 = Math.toRadians(from.lat)
            val lat2 = Math.toRadians(to.lat)
            val dLon = Math.toRadians(to.lon - from.lon)

            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            if (abs(x) < 1e-15 && abs(y) < 1e-15) {
                return null
            }
            val initialBearingRad = atan2(y, x)
            val initialBearingDeg = Math.toDegrees(initialBearingRad)
            return (initialBearingDeg + 360.0) % 360.0
        }
    }

    /**
     * Projects candidate memories into [SkyItem]s relative to current vehicle pose.
     *
     * Rules:
     * - Returns `emptyList()` if location quality is not `VALID`, age exceeds `maxLocationAgeMs`,
     *   heading is `null`/non-finite, or `headingReference != Reference.TRUE_NORTH`.
     * - Excludes stars at the same location (`distanceM < config.minValidDistanceM`) where bearing is undefined.
     * - Filters strictly by `abs(relativeBearingDeg) <= config.fovHalfAngleDeg`.
     * - Sorts deterministically by `abs(relativeBearingDeg)` -> `distanceM` -> `starId`.
     */
    fun project(
        vehicleLocation: TimedValue<LocationFix>,
        memories: List<MemoryStar>,
        selectedStarId: String? = null,
    ): List<SkyItem> {
        if (vehicleLocation.quality != Quality.VALID) return emptyList()
        if (vehicleLocation.ageMs < 0L || vehicleLocation.ageMs > config.maxLocationAgeMs) return emptyList()

        val fix = vehicleLocation.value ?: return emptyList()
        val vehicleHeading = fix.headingDeg ?: return emptyList()
        if (!vehicleHeading.isFinite() || fix.headingReference != Reference.TRUE_NORTH) {
            return emptyList()
        }

        val origin = fix.point

        return memories.mapNotNull { star ->
            val dist = distanceMeters(origin, star.point)
            if (!dist.isFinite() || dist < config.minValidDistanceM) {
                return@mapNotNull null
            }

            val absoluteBearing = initialBearingDeg(origin, star.point) ?: return@mapNotNull null
            val relBearing = normalizeRelativeBearing(absoluteBearing, vehicleHeading)

            if (abs(relBearing) <= config.fovHalfAngleDeg) {
                SkyItem(
                    starId = star.id,
                    relativeBearingDeg = relBearing,
                    distanceM = dist,
                    selected = (star.id == selectedStarId),
                )
            } else {
                null
            }
        }.sortedWith(
            compareBy<SkyItem> { abs(it.relativeBearingDeg) }
                .thenBy { it.distanceM }
                .thenBy { it.starId },
        )
    }
}
