package com.example.starlog2.starlog.clock

import com.example.starlog2.contracts.Clock as ContractClock

/**
 * Pure Kotlin Clock interface aligned with canonical [com.example.starlog2.contracts.Clock].
 * Zero Android SDK dependency.
 */
interface Clock : ContractClock {
    override fun monotonicMs(): Long

    override fun wallTimeMs(): Long = epochMs()

    fun epochMs(): Long
}

class SystemClockImpl : Clock {
    override fun monotonicMs(): Long = System.nanoTime() / 1_000_000L

    override fun epochMs(): Long = System.currentTimeMillis()
}
