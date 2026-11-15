package com.example.starlog2.starlog.melody

/**
 * Persistence treats IEEE-754 signed zero coordinates as the same numeric point.
 * Stable IDs and existing serialized melodies are intentionally not rewritten.
 */
fun Point2.numericallyEquals(other: Point2): Boolean = x == other.x && y == other.y

fun BaseMelody.numericallyEqualsPersisted(other: BaseMelody): Boolean {
    if (copy(notes = emptyList()) != other.copy(notes = emptyList())) return false
    if (notes.size != other.notes.size) return false
    return notes.zip(other.notes).all { (left, right) ->
        left.copy(originalPoint = Point2(0.0, 0.0), rotatedPoint = Point2(0.0, 0.0)) ==
            right.copy(originalPoint = Point2(0.0, 0.0), rotatedPoint = Point2(0.0, 0.0)) &&
            left.originalPoint.numericallyEquals(right.originalPoint) &&
            left.rotatedPoint.numericallyEquals(right.rotatedPoint)
    }
}
