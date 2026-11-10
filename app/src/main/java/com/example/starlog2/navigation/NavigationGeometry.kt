package com.example.starlog2.navigation

import com.example.starlog2.contracts.LatLon
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object NavigationGeometry {
    fun distanceM(first: LatLon, second: LatLon): Double {
        val latitude1 = Math.toRadians(first.lat)
        val latitude2 = Math.toRadians(second.lat)
        val latitudeDelta = latitude2 - latitude1
        val longitudeDelta = Math.toRadians(second.lon - first.lon)
        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(latitude1) * cos(latitude2) * sin(longitudeDelta / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(min(1.0, haversine)))
    }

    fun bearingDeg(first: LatLon, second: LatLon): Double {
        val latitude1 = Math.toRadians(first.lat)
        val latitude2 = Math.toRadians(second.lat)
        val longitudeDelta = Math.toRadians(second.lon - first.lon)
        val y = sin(longitudeDelta) * cos(latitude2)
        val x = cos(latitude1) * sin(latitude2) -
            sin(latitude1) * cos(latitude2) * cos(longitudeDelta)
        return normalizeBearing(Math.toDegrees(atan2(y, x)))
    }

    fun normalizeBearing(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    fun smoothBearing(previous: Double, target: Double, fraction: Double): Double {
        require(fraction in 0.0..1.0) { "fraction must be between 0 and 1" }
        val delta = ((target - previous + 540.0) % 360.0) - 180.0
        return normalizeBearing(previous + delta * fraction)
    }

    internal fun cumulativeDistances(points: List<LatLon>): DoubleArray {
        val cumulative = DoubleArray(points.size)
        for (index in 1 until points.size) {
            cumulative[index] = cumulative[index - 1] + distanceM(points[index - 1], points[index])
        }
        return cumulative
    }

    internal fun projectToSegment(point: LatLon, start: LatLon, end: LatLon): SegmentProjection {
        val referenceLatitude = Math.toRadians((start.lat + end.lat + point.lat) / 3.0)
        val startX = Math.toRadians(start.lon) * EARTH_RADIUS_M * cos(referenceLatitude)
        val startY = Math.toRadians(start.lat) * EARTH_RADIUS_M
        val endX = Math.toRadians(end.lon) * EARTH_RADIUS_M * cos(referenceLatitude)
        val endY = Math.toRadians(end.lat) * EARTH_RADIUS_M
        val pointX = Math.toRadians(point.lon) * EARTH_RADIUS_M * cos(referenceLatitude)
        val pointY = Math.toRadians(point.lat) * EARTH_RADIUS_M
        val deltaX = endX - startX
        val deltaY = endY - startY
        val lengthSquared = deltaX * deltaX + deltaY * deltaY
        val fraction = if (lengthSquared == 0.0) {
            0.0
        } else {
            ((pointX - startX) * deltaX + (pointY - startY) * deltaY) / lengthSquared
        }.coerceIn(0.0, 1.0)
        val projectedX = startX + fraction * deltaX
        val projectedY = startY + fraction * deltaY
        val snapped = LatLon(
            lat = Math.toDegrees(projectedY / EARTH_RADIUS_M),
            lon = Math.toDegrees(projectedX / (EARTH_RADIUS_M * cos(referenceLatitude))),
        )
        return SegmentProjection(snapped, fraction, distanceM(point, snapped))
    }

    fun pointAtDistance(points: List<LatLon>, distanceM: Double): LatLon {
        require(points.size >= 2) { "A route must have at least two points" }
        val cumulative = cumulativeDistances(points)
        val target = distanceM.coerceIn(0.0, cumulative.last())
        var endIndex = 1
        while (endIndex < cumulative.size - 1 && cumulative[endIndex] < target) endIndex++
        val segmentLength = cumulative[endIndex] - cumulative[endIndex - 1]
        val fraction = if (segmentLength == 0.0) 0.0 else (target - cumulative[endIndex - 1]) / segmentLength
        return interpolate(points[endIndex - 1], points[endIndex], fraction)
    }

    fun lookAheadBearing(points: List<LatLon>, alongM: Double, lookAheadM: Double = 310.0): Double {
        val from = pointAtDistance(points, alongM)
        val to = pointAtDistance(points, alongM + max(0.0, lookAheadM))
        return if (distanceM(from, to) < 0.01) {
            bearingDeg(points[points.lastIndex - 1], points.last())
        } else {
            bearingDeg(from, to)
        }
    }

    private fun interpolate(start: LatLon, end: LatLon, fraction: Double): LatLon = LatLon(
        lat = start.lat + (end.lat - start.lat) * fraction.coerceIn(0.0, 1.0),
        lon = start.lon + (end.lon - start.lon) * fraction.coerceIn(0.0, 1.0),
    )

    internal data class SegmentProjection(
        val point: LatLon,
        val fraction: Double,
        val distanceM: Double,
    )

    private const val EARTH_RADIUS_M = 6_371_008.8
}
