package com.example.starlog2.contracts

/** One provider generation per cursor; create/reset only when switching sessions. */
class TelemetryCursor {
    private var previous: Telemetry? = null
    fun accept(value: Telemetry): Boolean {
        val p = previous
        if (p != null && (value.providerId != p.providerId || value.sourceMode != p.sourceMode ||
                    value.seq <= p.seq || value.monotonicMs < p.monotonicMs)) return false
        previous = value
        return true
    }
    fun reset() { previous = null }
}
