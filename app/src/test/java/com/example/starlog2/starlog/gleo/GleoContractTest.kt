package com.example.starlog2.starlog.gleo

import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.ArrangementGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GleoContractTest {
    @Test
    fun actionRequest_acceptsSupportedActionAndNormalizesParameters() {
        val request = GleoActionRequest.from(
            functionName = GleoContract.ACTION_PREPARE_CONSTELLATION_MUSIC,
            constellationId = " constellation-42 ",
            sourceMode = "replay",
            genre = "ambient",
        )

        assertEquals("constellation-42", request?.constellationId)
        assertEquals(SourceMode.REPLAY, request?.sourceMode)
        assertEquals(ArrangementGenre.AMBIENT, request?.genre)
    }

    @Test
    fun actionRequest_rejectsUnknownActionAndInvalidEnums() {
        assertNull(GleoActionRequest.from("UNKNOWN", null, null, null))
        assertNull(GleoActionRequest.from(GleoContract.ACTION_OPEN_MELODY_STUDIO, null, "MOCK", null))
        assertNull(GleoActionRequest.from(GleoContract.ACTION_PREPARE_CONSTELLATION_MUSIC, null, null, "ROCK"))
    }

    @Test
    fun actionRequest_doesNotAllowGenerationStyleOnPlaybackAction() {
        assertNull(
            GleoActionRequest.from(
                GleoContract.ACTION_PLAY_CONSTELLATION_MUSIC,
                null,
                SourceMode.LIVE.name,
                ArrangementGenre.JAZZ.name,
            ),
        )
    }
}
