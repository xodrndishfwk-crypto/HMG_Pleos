package com.example.starlog2.navigation

import com.example.starlog2.contracts.AppError
import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.LatLon
import com.example.starlog2.contracts.LocationFix
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.Reference
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.contracts.Telemetry
import com.example.starlog2.contracts.TelemetryProvider
import com.example.starlog2.contracts.TimedValue
import java.util.concurrent.atomic.AtomicBoolean

data class ReplayFrame(
    val offsetMs: Long,
    val observedAtMs: Long,
    val point: LatLon?,
    val accuracyM: Double?,
    val headingDeg: Double?,
    val speedMps: Double?,
    val gear: Gear,
    val ignitionOn: Boolean,
)

fun interface ReplayClock {
    fun nowMs(): Long
}

class ReplayTelemetryProvider(
    frames: List<ReplayFrame>,
    private val providerId: String = "starlog-replay",
    private val clock: ReplayClock = ReplayClock { System.nanoTime() / 1_000_000L },
    private val tickMs: Long = 10L,
) : TelemetryProvider {
    private val frames = frames.sortedBy { it.offsetMs }
    private val lock = Any()
    private val running = AtomicBoolean(false)
    private var paused = false
    private var cursor = 0
    private var positionMs = 0L
    private var startedAtMs = 0L
    private var onSample: ((Telemetry) -> Unit)? = null
    private var onError: ((AppError) -> Unit)? = null
    private var worker: Thread? = null

    init {
        require(tickMs > 0) { "tickMs must be positive" }
        require(this.frames.zipWithNext().all { (first, second) -> first.offsetMs < second.offsetMs }) {
            "Replay offsets must be unique"
        }
        require(this.frames.all { it.offsetMs >= 0 }) { "Replay offsets must not be negative" }
    }

    override fun start(onSample: (Telemetry) -> Unit, onError: (AppError) -> Unit) {
        synchronized(lock) {
            this.onSample = onSample
            this.onError = onError
            if (!running.compareAndSet(false, true)) return
            paused = false
            startedAtMs = clock.nowMs() - positionMs
            worker = Thread(::runReplay, "starlog-replay").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun pause() {
        synchronized(lock) {
            if (!running.get() || paused) return
            positionMs = (clock.nowMs() - startedAtMs).coerceAtLeast(0L)
            paused = true
        }
    }

    fun resume() {
        synchronized(lock) {
            if (!running.get() || !paused) return
            startedAtMs = clock.nowMs() - positionMs
            paused = false
        }
    }

    fun seek(positionMs: Long) {
        require(positionMs >= 0) { "positionMs must not be negative" }
        synchronized(lock) {
            this.positionMs = positionMs
            cursor = frames.indexOfFirst { it.offsetMs >= positionMs }.let { if (it < 0) frames.size else it }
            startedAtMs = clock.nowMs() - positionMs
        }
    }

    fun reset() = seek(0L)

    fun emitUntil(positionMs: Long): List<Telemetry> {
        require(positionMs >= 0) { "positionMs must not be negative" }
        synchronized(lock) {
            val emitted = ArrayList<Telemetry>()
            while (cursor < frames.size && frames[cursor].offsetMs <= positionMs) {
                emitted += frames[cursor].toTelemetry(cursor)
                cursor++
            }
            this.positionMs = positionMs
            return emitted
        }
    }

    override fun stop() {
        running.set(false)
        worker?.interrupt()
        synchronized(lock) {
            worker = null
            onSample = null
            onError = null
        }
    }

    private fun runReplay() {
        try {
            while (running.get()) {
                val callback: ((Telemetry) -> Unit)?
                val samples: List<Telemetry>
                synchronized(lock) {
                    callback = onSample
                    samples = if (paused) emptyList() else emitUntil(clock.nowMs() - startedAtMs)
                }
                if (callback != null) samples.forEach(callback)
                if (cursor >= frames.size) {
                    running.set(false)
                    break
                }
                Thread.sleep(tickMs)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            onError?.invoke(navigationError("INVALID_STATE"))
            running.set(false)
        }
    }

    private fun ReplayFrame.toTelemetry(index: Int): Telemetry {
        val locationFix = point?.let {
            LocationFix(it, accuracyM, headingDeg, Reference.TRUE_NORTH)
        }
        return Telemetry(
            seq = index.toLong() + 1L,
            observedAtMs = observedAtMs,
            monotonicMs = offsetMs,
            sourceMode = SourceMode.REPLAY,
            providerId = providerId,
            location = TimedValue(
                value = locationFix,
                quality = if (locationFix == null) Quality.UNAVAILABLE else Quality.VALID,
                ageMs = 0,
            ),
            speedMps = TimedValue(
                value = speedMps,
                quality = if (speedMps == null) Quality.UNAVAILABLE else Quality.VALID,
                ageMs = 0,
            ),
            gear = TimedValue(gear, Quality.VALID, 0),
            ignitionOn = TimedValue(ignitionOn, Quality.VALID, 0),
        )
    }
}
