package com.example.starlog2.integration

/** Rejects callbacks from old pages, timed-out loads, and destroyed owners. */
class MapLoadSession {
    enum class Phase { IDLE, LOADING, READY, FAILED, CLOSED }
    var generation: Int = 0
        private set
    var phase: Phase = Phase.IDLE
        private set

    fun begin(): Int {
        check(phase != Phase.CLOSED)
        generation += 1
        phase = Phase.LOADING
        return generation
    }

    fun ready(token: Int): Boolean {
        if (token != generation || phase != Phase.LOADING) return false
        phase = Phase.READY
        return true
    }

    fun fail(token: Int): Boolean {
        if (token != generation || phase == Phase.CLOSED || phase == Phase.FAILED) return false
        phase = Phase.FAILED
        return true
    }

    fun close() { phase = Phase.CLOSED }
}
