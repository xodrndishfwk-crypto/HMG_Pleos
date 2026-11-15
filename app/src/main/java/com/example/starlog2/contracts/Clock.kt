package com.example.starlog2.contracts

/** Wall time records dates; monotonic time alone measures elapsed durations. */
interface Clock {
    fun wallTimeMs(): Long
    fun monotonicMs(): Long
}
