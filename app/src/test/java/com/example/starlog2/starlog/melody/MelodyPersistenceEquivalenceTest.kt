package com.example.starlog2.starlog.melody

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodyPersistenceEquivalenceTest {
    @Test
    fun signedZero_isNumericallyEquivalentWithoutChangingStableIdentityPolicy() {
        val original = MelodyGenerator().preview(
            constellationId = "signed-zero",
            stars = listOf(
                MelodyStar("origin", 0.0, -0.0, 0),
                MelodyStar("second", 1.0, 1.0, 1),
            ),
            angleDeg = 0,
        )
        val restored = original.copy(
            notes = original.notes.map { note ->
                note.copy(
                    originalPoint = Point2(
                        if (note.originalPoint.x == 0.0) 0.0 else note.originalPoint.x,
                        if (note.originalPoint.y == 0.0) 0.0 else note.originalPoint.y,
                    ),
                    rotatedPoint = Point2(
                        if (note.rotatedPoint.x == 0.0) 0.0 else note.rotatedPoint.x,
                        if (note.rotatedPoint.y == 0.0) 0.0 else note.rotatedPoint.y,
                    ),
                )
            },
        )

        assertFalse("Kotlin data-class equality remains bit-sensitive for signed zero", original == restored)
        assertTrue(original.numericallyEqualsPersisted(restored))
        assertTrue(original.id == restored.id)
    }
}
