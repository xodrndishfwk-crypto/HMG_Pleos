package com.example.starlog2.starlog.melody

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException

/**
 * Keeps at most one preview request valid and prevents queued work from starting after deactivation.
 * The Android player is injected so cancellation and lifecycle transitions can be tested on the JVM.
 */
internal class MelodyPlaybackSession(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "starlog-melody-preview")
    },
    private val playAction: (BaseMelody, () -> Boolean) -> Unit,
    private val stopAction: () -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private var active = false
    private var closed = false
    private var generation = 0L
    private var pending: Future<*>? = null

    fun activate() {
        synchronized(lock) {
            if (!closed) active = true
        }
    }

    fun deactivate() {
        val task = synchronized(lock) {
            if (closed) return
            active = false
            generation += 1
            pending.also { pending = null }
        }
        task?.cancel(true)
        stopAction()
    }

    fun play(melody: BaseMelody) {
        val token: Long
        val previous: Future<*>?
        synchronized(lock) {
            if (!active || closed) return
            generation += 1
            token = generation
            previous = pending
            pending = null
        }
        previous?.cancel(true)
        stopAction()

        val submitted = try {
            executor.submit {
                if (isCurrent(token)) {
                    playAction(melody) { isCurrent(token) }
                }
            }
        } catch (_: RejectedExecutionException) {
            return
        }

        synchronized(lock) {
            if (isCurrentLocked(token)) {
                pending = submitted
            } else {
                submitted.cancel(true)
            }
        }
    }

    private fun isCurrent(token: Long): Boolean = synchronized(lock) {
        isCurrentLocked(token) && !Thread.currentThread().isInterrupted
    }

    private fun isCurrentLocked(token: Long): Boolean = active && !closed && generation == token

    override fun close() {
        val task = synchronized(lock) {
            if (closed) return
            closed = true
            active = false
            generation += 1
            pending.also { pending = null }
        }
        task?.cancel(true)
        executor.shutdownNow()
        stopAction()
    }
}
