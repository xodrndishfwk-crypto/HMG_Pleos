package com.example.starlog2.platform

import android.os.SystemClock
import com.example.starlog2.contracts.Clock

object AndroidClock : Clock {
    override fun wallTimeMs(): Long = System.currentTimeMillis()
    override fun monotonicMs(): Long = SystemClock.elapsedRealtime()
}
