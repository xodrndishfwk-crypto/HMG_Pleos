package com.example.starlog2.starlog.experience

import com.example.starlog2.contracts.AppError
import com.example.starlog2.contracts.ExperienceAction
import com.example.starlog2.contracts.ExperienceCommand
import com.example.starlog2.contracts.ExperienceMode
import com.example.starlog2.contracts.ExperienceState
import com.example.starlog2.contracts.Gear
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.Quality
import com.example.starlog2.contracts.Telemetry
import com.example.starlog2.starlog.model.ExperienceConfig
import com.example.starlog2.starlog.model.StarLogErrorCodes

/**
 * Pure Kotlin Experience Reducer.
 * 0% Android SDK dependency.
 *
 * Deterministic state machine governing:
 * `DRIVING_SAFE` <-> `STOP_PENDING` <-> `MEMORY_FRAME` <-> `RECAP`.
 *
 * Safety invariants:
 * - Driving safety ALWAYS takes precedence: any invalid/stale telemetry, gear D/R/UNKNOWN,
 *   or speed > 0.3 m/s immediately forces `DRIVING_SAFE` (even from `MEMORY_FRAME` or `RECAP`).
 * - Stop timer uses injected `monotonicMs` only; timestamp rollback or inactive gaps reset the timer.
 * - `entryEventId` is generated once per transition into `MEMORY_FRAME`.
 */
class ExperienceReducer(
    private val config: ExperienceConfig = ExperienceConfig(),
) {
    constructor(
        stopRequiredMs: Long,
        maxSpeedMps: Double = 0.3,
        maxTelemetryAgeMs: Long = 2_000L,
    ) : this(
        ExperienceConfig(
            stopRequiredMs = stopRequiredMs,
            maxSpeedMps = maxSpeedMps,
            maxTelemetryAgeMs = maxTelemetryAgeMs,
        ),
    )

    private var stopStartedMonotonicMs: Long? = null
    private var lastMonotonicMs: Long? = null
    private var currentMode: ExperienceMode = ExperienceMode.DRIVING_SAFE
    private var activeEntryEventId: String? = null
    private var selectedStarId: String? = null

    fun reset() {
        stopStartedMonotonicMs = null
        lastMonotonicMs = null
        currentMode = ExperienceMode.DRIVING_SAFE
        activeEntryEventId = null
        selectedStarId = null
    }

    fun update(sample: Telemetry, availableStarIds: List<String>): ExperienceState {
        // 1. Driving safety always overrides all modes (including RECAP and MEMORY_FRAME)
        if (!isVehicleStoppedAndValid(sample)) {
            reset()
            return ExperienceState(
                mode = ExperienceMode.DRIVING_SAFE,
                selectedStarId = null,
                stopElapsedMs = 0L,
                entryEventId = null,
            )
        }

        // 2. Guard against monotonic clock rollback or app suspension gaps
        val previousMonotonic = lastMonotonicMs
        if (previousMonotonic != null) {
            val delta = sample.monotonicMs - previousMonotonic
            if (delta < 0L || delta > config.maxSampleGapMs) {
                stopStartedMonotonicMs = sample.monotonicMs
                activeEntryEventId = null
                selectedStarId = null
                if (currentMode != ExperienceMode.RECAP) {
                    currentMode = ExperienceMode.STOP_PENDING
                }
            }
        }
        lastMonotonicMs = sample.monotonicMs

        val startTime = stopStartedMonotonicMs ?: sample.monotonicMs.also {
            stopStartedMonotonicMs = it
        }
        val elapsedMs = (sample.monotonicMs - startTime).coerceAtLeast(0L)

        // 3. If user explicitly opened RECAP while validly stopped, stay in RECAP
        if (currentMode == ExperienceMode.RECAP) {
            return ExperienceState(
                mode = ExperienceMode.RECAP,
                selectedStarId = selectedStarId,
                stopElapsedMs = elapsedMs,
                entryEventId = null,
            )
        }

        // 4. Evaluate 180,000ms stop boundary
        return if (elapsedMs >= config.stopRequiredMs) {
            if (currentMode != ExperienceMode.MEMORY_FRAME) {
                currentMode = ExperienceMode.MEMORY_FRAME
                activeEntryEventId = "entry-${sample.observedAtMs}-${sample.seq}"
            }
            if (selectedStarId == null || !availableStarIds.contains(selectedStarId)) {
                selectedStarId = availableStarIds.firstOrNull()
            }
            ExperienceState(
                mode = ExperienceMode.MEMORY_FRAME,
                selectedStarId = selectedStarId,
                stopElapsedMs = elapsedMs,
                entryEventId = activeEntryEventId,
            )
        } else {
            currentMode = ExperienceMode.STOP_PENDING
            activeEntryEventId = null
            selectedStarId = null
            ExperienceState(
                mode = ExperienceMode.STOP_PENDING,
                selectedStarId = null,
                stopElapsedMs = elapsedMs,
                entryEventId = null,
            )
        }
    }

    fun handleCommand(
        command: ExperienceCommand,
        sample: Telemetry,
        availableStarIds: List<String>,
    ): Outcome<ExperienceState> {
        // Re-verify valid stop state before accepting any UI action
        if (!isVehicleStoppedAndValid(sample)) {
            reset()
            return Outcome.Err(
                AppError(
                    code = StarLogErrorCodes.INVALID_STATE,
                    messageKey = "experience.invalid_state",
                    retryable = false,
                    requestId = null,
                ),
            )
        }

        return when (command.action) {
            ExperienceAction.OPEN_RECAP -> {
                if (!config.recapSupported) {
                    Outcome.Err(
                        AppError(
                            code = StarLogErrorCodes.UNSUPPORTED,
                            messageKey = "experience.recap_unsupported",
                            retryable = false,
                            requestId = null,
                        ),
                    )
                } else {
                    currentMode = ExperienceMode.RECAP
                    val elapsedMs = currentStopElapsedMs(sample.monotonicMs)
                    Outcome.Ok(
                        ExperienceState(
                            mode = ExperienceMode.RECAP,
                            selectedStarId = selectedStarId,
                            stopElapsedMs = elapsedMs,
                            entryEventId = null,
                        ),
                    )
                }
            }

            ExperienceAction.CLOSE_RECAP -> {
                if (currentMode == ExperienceMode.RECAP) {
                    currentMode = ExperienceMode.DRIVING_SAFE
                }
                Outcome.Ok(update(sample, availableStarIds))
            }

            ExperienceAction.SELECT_MEMORY -> {
                // Ensure stop state is up-to-date and in MEMORY_FRAME
                val updated = update(sample, availableStarIds)
                if (updated.mode != ExperienceMode.MEMORY_FRAME) {
                    return Outcome.Err(
                        AppError(
                            code = StarLogErrorCodes.INVALID_STATE,
                            messageKey = "experience.memory_frame_not_active",
                            retryable = false,
                            requestId = null,
                        ),
                    )
                }

                val targetId = command.starId
                if (targetId.isNullOrBlank() || !availableStarIds.contains(targetId)) {
                    Outcome.Err(
                        AppError(
                            code = StarLogErrorCodes.INVALID_INPUT,
                            messageKey = "experience.star_not_in_fov",
                            retryable = false,
                            requestId = null,
                        ),
                    )
                } else {
                    selectedStarId = targetId
                    Outcome.Ok(
                        updated.copy(selectedStarId = targetId),
                    )
                }
            }
        }
    }

    private fun currentStopElapsedMs(currentMonotonicMs: Long): Long {
        val start = stopStartedMonotonicMs ?: return 0L
        return (currentMonotonicMs - start).coerceAtLeast(0L)
    }

    private fun isVehicleStoppedAndValid(sample: Telemetry): Boolean {
        val gearTimed = sample.gear
        if (gearTimed.quality != Quality.VALID || gearTimed.ageMs < 0L || gearTimed.ageMs > config.maxTelemetryAgeMs) {
            return false
        }
        val gear = gearTimed.value ?: return false
        if (gear != Gear.P && gear != Gear.N) {
            return false
        }

        val speedTimed = sample.speedMps
        if (speedTimed.quality != Quality.VALID || speedTimed.ageMs < 0L || speedTimed.ageMs > config.maxTelemetryAgeMs) {
            return false
        }
        val speed = speedTimed.value ?: return false
        if (!speed.isFinite() || speed < 0.0 || speed > config.maxSpeedMps) {
            return false
        }

        return true
    }
}
