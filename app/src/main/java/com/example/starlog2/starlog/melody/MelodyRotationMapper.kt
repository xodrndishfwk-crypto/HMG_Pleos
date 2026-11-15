package com.example.starlog2.starlog.melody

/** Single bridge used by rotation UI frames and the score preview calculation. */
object MelodyRotationMapper {
    fun previewAtDisplayedAngle(
        constellationId: String,
        stars: List<MelodyStar>,
        displayedAngleDeg: Int,
        config: MelodyConfig = MelodyConfig(),
    ): BaseMelody {
        val normalized = Math.floorMod(displayedAngleDeg, 360)
        return MelodyGenerator(config).preview(constellationId, stars, normalized).also {
            check(it.rotationAngleDeg == normalized)
        }
    }
}
