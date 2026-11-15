package com.example.starlog2.starlog.gleo

import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.ArrangementGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.starlog2.starlog.journey.JourneyControlOperation

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

    @Test
    fun publicSchemaActions_areLimitedToJourneyPrepareAndPlay() {
        assertEquals(
            setOf(
                GleoContract.ACTION_CONTROL_STARLOG_JOURNEY,
                GleoContract.ACTION_PREPARE_CONSTELLATION_MUSIC,
                GleoContract.ACTION_PLAY_CONSTELLATION_MUSIC,
            ),
            GleoContract.publicActions,
        )
        assertEquals(3, GleoContract.publicActions.size)
        assertTrue(GleoContract.ACTION_OPEN_MELODY_STUDIO in GleoContract.supportedActions)
        assertTrue(GleoContract.ACTION_OPEN_MELODY_STUDIO !in GleoContract.publicActions)
    }

    @Test
    fun journeyRequest_parsesOperationsAndRejectsMalformedValuesWithoutFallback() {
        assertEquals(
            GleoJourneyActionRequest(JourneyControlOperation.START, SourceMode.REPLAY),
            GleoJourneyActionRequest.from(GleoContract.ACTION_CONTROL_STARLOG_JOURNEY, "start", null),
        )
        assertEquals(
            GleoJourneyActionRequest(JourneyControlOperation.STATUS, SourceMode.REPLAY),
            GleoJourneyActionRequest.from(GleoContract.ACTION_CONTROL_STARLOG_JOURNEY, "STATUS", "replay"),
        )
        assertNull(GleoJourneyActionRequest.from(GleoContract.ACTION_CONTROL_STARLOG_JOURNEY, "PAUSE", "REPLAY"))
        assertNull(GleoJourneyActionRequest.from(GleoContract.ACTION_CONTROL_STARLOG_JOURNEY, "START", "MOCK"))
        assertNull(GleoJourneyActionRequest.from(GleoContract.ACTION_PREPARE_CONSTELLATION_MUSIC, "START", "REPLAY"))
    }
}
