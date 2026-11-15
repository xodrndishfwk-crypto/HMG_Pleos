package com.example.starlog2.starlog.melody

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object MelodyGeometry {
    fun center(stars: List<MelodyStar>): Point2 {
        require(stars.isNotEmpty())
        return Point2(stars.sumOf { it.originalX } / stars.size, stars.sumOf { it.originalY } / stars.size)
    }

    /** Always rotates from immutable original coordinates, so repeated previews cannot accumulate error. */
    fun rotate(stars: List<MelodyStar>, angleDeg: Int): Map<String, Point2> {
        if (stars.isEmpty()) return emptyMap()
        val center = center(stars)
        val radians = Math.floorMod(angleDeg, 360) * PI / 180.0
        val cosine = cos(radians)
        val sine = sin(radians)
        return stars.associate { star ->
            val dx = star.originalX - center.x
            val dy = star.originalY - center.y
            star.starId to Point2(
                x = center.x + dx * cosine - dy * sine,
                y = center.y + dx * sine + dy * cosine,
            )
        }
    }

    fun squaredDistance(a: Point2, b: Point2): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }
}
