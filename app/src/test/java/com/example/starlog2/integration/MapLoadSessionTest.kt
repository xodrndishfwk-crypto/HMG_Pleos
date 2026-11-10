package com.example.starlog2.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLoadSessionTest {
    @Test fun retry_ignoresOldPageReadyAndError() {
        val state = MapLoadSession()
        val old = state.begin()
        val current = state.begin()
        assertFalse(state.ready(old))
        assertFalse(state.fail(old))
        assertTrue(state.ready(current))
        assertFalse(state.ready(current))
    }

    @Test fun timeout_rejectsLateSuccess_untilExplicitRetry() {
        val state = MapLoadSession()
        val token = state.begin()
        assertTrue(state.fail(token))
        assertFalse(state.ready(token))
        assertTrue(state.ready(state.begin()))
    }

    @Test fun destroyedOwner_rejectsEveryLateCallback() {
        val state = MapLoadSession()
        val token = state.begin()
        state.close()
        assertFalse(state.ready(token))
        assertFalse(state.fail(token))
        assertEquals(MapLoadSession.Phase.CLOSED, state.phase)
    }
}
