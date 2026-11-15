package com.example.starlog2.starlog.melody

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MelodyRotationMapperTest {
    @Test
    fun everyDisplayedAnimationAngle_isTheActualGeneratorInputAngle() {
        val stars = listOf(
            MelodyStar("a", 0.0, 0.0, 0),
            MelodyStar("b", 100.0, 30.0, 1),
            MelodyStar("c", 30.0, 90.0, 2),
        )
        val frames = listOf(0, 45, 90, 180, 359, 360, 405)
        val previews = frames.map { angle ->
            MelodyRotationMapper.previewAtDisplayedAngle("rotation-demo", stars, angle)
        }
        assertEquals(frames.map { Math.floorMod(it, 360) }, previews.map { it.rotationAngleDeg })
        assertNotEquals(
            previews[0].notes.map { it.rotatedPoint },
            previews[2].notes.map { it.rotatedPoint },
        )
    }
}
