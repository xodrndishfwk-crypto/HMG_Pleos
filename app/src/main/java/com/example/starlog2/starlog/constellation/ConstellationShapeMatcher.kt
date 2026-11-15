package com.example.starlog2.starlog.constellation

import com.example.starlog2.contracts.LatLon
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Compares the polyline of a journey constellation with famous sky asterisms.
 *
 * Stars are compared one-to-one in drawing order (the shorter polyline is resampled by arc length only
 * when the counts differ), centred, scaled to unit RMS radius and aligned with the optimal 2D rotation
 * (Procrustes). Reflection and reversed traversal are allowed because a drive can
 * trace the asterism mirrored or backwards. Score = exp(-k · residual²), so 1.0 is an exact match.
 */
object ConstellationShapeMatcher {
    data class SkyTemplate(val id: String, val koreanName: String, val latinName: String, val points: List<Pair<Double, Double>>)

    data class Match(val template: SkyTemplate, val score: Double, val grade: String)

    private const val SHARPNESS = 4.0

    /** Right ascension (hours) / declination (degrees) of the main stars, in drawing order. */
    val TEMPLATES: List<SkyTemplate> = listOf(
        sky(
            "ursa-major-dipper", "북두칠성", "URSA MAJOR",
            13.792 to 49.31, 13.399 to 54.93, 12.900 to 55.96, 12.257 to 57.03,
            11.897 to 53.69, 11.031 to 56.38, 11.062 to 61.75,
        ),
        sky(
            "cassiopeia", "카시오페이아", "CASSIOPEIA",
            0.153 to 59.15, 0.675 to 56.54, 0.945 to 60.72, 1.430 to 60.24, 1.907 to 63.67,
        ),
        sky(
            "ursa-minor", "작은곰자리", "URSA MINOR",
            2.530 to 89.26, 17.537 to 86.59, 16.766 to 82.04, 15.734 to 77.79,
            16.292 to 75.76, 15.345 to 71.83, 14.845 to 74.16,
        ),
        sky(
            "corona-borealis", "북쪽왕관자리", "CORONA BOREALIS",
            15.548 to 31.36, 15.464 to 29.11, 15.578 to 26.71, 15.712 to 26.30,
            15.827 to 26.07, 15.960 to 26.88, 16.024 to 29.85,
        ),
        sky(
            "orion-belt-sword", "오리온자리", "ORION",
            5.919 to 7.41, 5.679 to -1.94, 5.603 to -1.20, 5.533 to -0.30, 5.242 to -8.20,
        ),
    )

    fun bestMatch(route: List<LatLon>): Match? {
        if (route.size < 3) return null
        val journey = route.map { p -> p.lon * cos(Math.toRadians(p.lat)) to p.lat }
        return TEMPLATES.map { template -> Match(template, score(journey, template.points), "") }
            .maxByOrNull { it.score }
            ?.let { it.copy(grade = grade(it.score)) }
    }

    fun grade(score: Double): String = when {
        score >= 0.90 -> "S"
        score >= 0.80 -> "A"
        score >= 0.65 -> "B"
        else -> "C"
    }

    internal fun score(a: List<Pair<Double, Double>>, b: List<Pair<Double, Double>>): Double {
        // Stars correspond one-to-one; only when the counts differ is the shorter polyline resampled.
        val count = maxOf(a.size, b.size)
        val left = normalize(if (a.size == count) a else resample(a, count))
        val right = if (b.size == count) b else resample(b, count)
        val variants = listOf(right, right.reversed()).flatMap { listOf(it, it.map { (x, y) -> -x to y }) }
        val residual = variants.minOf { variant -> alignedResidual(left, normalize(variant)) }
        return exp(-SHARPNESS * residual).coerceIn(0.0, 1.0)
    }

    private fun sky(id: String, ko: String, latin: String, vararg raDec: Pair<Double, Double>): SkyTemplate {
        val meanDec = raDec.map { it.second }.average()
        // East is to the left on a sky chart, so x = -RA scaled by cos(declination).
        val points = raDec.map { (ra, dec) -> -ra * 15.0 * cos(Math.toRadians(meanDec)) to dec }
        return SkyTemplate(id, ko, latin, unwrapRa(points))
    }

    /** Ursa Minor wraps around RA 0h/24h; keep consecutive x values continuous. */
    private fun unwrapRa(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (points.isEmpty()) return points
        val out = mutableListOf(points.first())
        for (i in 1 until points.size) {
            var x = points[i].first
            val prev = out.last().first
            val span = 360.0 * cos(Math.toRadians(points.map { it.second }.average()))
            while (x - prev > span / 2) x -= span
            while (prev - x > span / 2) x += span
            out += x to points[i].second
        }
        return out
    }

    private fun resample(points: List<Pair<Double, Double>>, samples: Int): List<Pair<Double, Double>> {
        val segments = points.zipWithNext().map { (p, q) -> hypot(q.first - p.first, q.second - p.second) }
        val total = segments.sum().takeIf { it > 0.0 } ?: return List(samples) { points.first() }
        return List(samples) { i ->
            var target = total * i / (samples - 1)
            var index = 0
            while (index < segments.lastIndex && target > segments[index]) {
                target -= segments[index]
                index += 1
            }
            val t = if (segments[index] == 0.0) 0.0 else (target / segments[index]).coerceIn(0.0, 1.0)
            val p = points[index]
            val q = points[index + 1]
            p.first + (q.first - p.first) * t to p.second + (q.second - p.second) * t
        }
    }

    private fun normalize(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val cx = points.map { it.first }.average()
        val cy = points.map { it.second }.average()
        val centred = points.map { (x, y) -> x - cx to y - cy }
        val rms = sqrt(centred.sumOf { (x, y) -> x * x + y * y } / centred.size).takeIf { it > 0.0 } ?: 1.0
        return centred.map { (x, y) -> x / rms to y / rms }
    }

    /** Mean squared distance after the optimal rotation of [b] onto [a]. */
    private fun alignedResidual(a: List<Pair<Double, Double>>, b: List<Pair<Double, Double>>): Double {
        var sxx = 0.0
        var sxy = 0.0
        for (i in a.indices) {
            sxx += a[i].first * b[i].first + a[i].second * b[i].second
            sxy += a[i].second * b[i].first - a[i].first * b[i].second
        }
        val theta = atan2(sxy, sxx)
        val c = cos(theta)
        val s = sin(theta)
        return a.indices.sumOf { i ->
            val rx = b[i].first * c - b[i].second * s
            val ry = b[i].first * s + b[i].second * c
            val dx = a[i].first - rx
            val dy = a[i].second - ry
            dx * dx + dy * dy
        } / a.size
    }
}
