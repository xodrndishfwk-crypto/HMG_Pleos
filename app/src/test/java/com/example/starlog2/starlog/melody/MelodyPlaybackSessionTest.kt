package com.example.starlog2.starlog.melody

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodyPlaybackSessionTest {
    @Test
    fun play_replacesQueuedRequestAndOnlyLatestRequestRuns() {
        val executor = ManualExecutorService()
        val played = mutableListOf<String>()
        var stops = 0
        val session = MelodyPlaybackSession(
            executor = executor,
            playAction = { melody, isCurrent -> if (isCurrent()) played += melody.id },
            stopAction = { stops += 1 },
        )
        session.activate()

        session.play(melody("first"))
        session.play(melody("second"))
        executor.drain()

        assertEquals(listOf("second"), played)
        assertEquals(2, stops)
        session.close()
    }

    @Test
    fun deactivate_cancelsQueuedRequestAndRejectsPlaybackUntilReactivated() {
        val executor = ManualExecutorService()
        val played = mutableListOf<String>()
        var stops = 0
        val session = MelodyPlaybackSession(
            executor = executor,
            playAction = { melody, isCurrent -> if (isCurrent()) played += melody.id },
            stopAction = { stops += 1 },
        )
        session.activate()
        session.play(melody("queued"))

        session.deactivate()
        session.play(melody("inactive"))
        executor.drain()

        assertTrue(played.isEmpty())
        assertEquals(2, stops)
        session.activate()
        session.play(melody("resumed"))
        executor.drain()
        assertEquals(listOf("resumed"), played)
        session.close()
    }

    private fun melody(id: String): BaseMelody = MelodyGenerator().preview(
        constellationId = id,
        stars = listOf(
            MelodyStar("$id-a", 0.0, 0.0, 0),
            MelodyStar("$id-b", 10.0, 20.0, 1),
        ),
        angleDeg = 0,
    ).copy(id = id)

    private class ManualExecutorService : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        override fun execute(command: Runnable) {
            check(!shutdown)
            tasks += command
        }

        fun drain() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return tasks.toMutableList().also { tasks.clear() }
        }

        override fun isShutdown(): Boolean = shutdown
        override fun isTerminated(): Boolean = shutdown && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated
    }
}
