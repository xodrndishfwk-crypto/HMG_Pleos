package com.example.starlog2.integration

import com.example.starlog2.contracts.*
import com.example.starlog2.starlog.model.toMemoryCard
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** State/domain methods run on the navigation reducer; repository methods run on IO only. */
data class StarLogComponents(
    val repository: MemoryRepository,
    val createSession: () -> StarLogSession,
    val io: ExecutorService = Executors.newSingleThreadExecutor(),
)

class StarLogCoordinator(
    private val components: StarLogComponents,
    private val clock: Clock,
    private val dispatch: (() -> Unit) -> Unit,
    private val changed: () -> Unit,
) : AutoCloseable {
    private var generation = 0L
    private var mode = SourceMode.REPLAY
    private var domain: StarLogSession? = null
    private var memories = emptyList<MemoryStar>()
    private var latest: Telemetry? = null
    private var receivedAt = 0L
    private var loading = false
    private var active = false
    private var closed = false
    private var flushing = false
    private val pendingWrites = linkedMapOf<String, Visit>()
    private var update = emptyUpdate()
    private var failure: AppError? = null

    fun begin(source: SourceMode) {
        if (closed) return
        if (active) suspend()
        val token = ++generation
        mode = source; active = true; loading = true; latest = null; memories = emptyList()
        update = emptyUpdate(); failure = null; domain = components.createSession()
        components.io.execute {
            val checkpoint = attempt { components.repository.loadCheckpoint(source) }
            val stars = attempt { components.repository.listStars(source) }
            dispatch {
                if (closed || !active || token != generation) return@dispatch
                if (stars is Outcome.Ok) memories = stars.value else failure = (stars as Outcome.Err).error
                if (checkpoint is Outcome.Ok && checkpoint.value != null) {
                    val value = checkpoint.value
                    if (value.sourceMode == source && (value.qualifiedPendingVisit == null || value.qualifiedPendingVisit.sourceMode == source)) {
                        enqueue(domain!!.restore(value))
                    } else failure = AppError("INVALID_RESPONSE", "storage.checkpoint_source_mismatch", false, null)
                } else if (checkpoint is Outcome.Err) failure = checkpoint.error
                loading = false
                freshSample()?.let { apply(domain!!.update(it, memories)) }
                flush(); changed()
            }
        }
    }

    fun telemetry(sample: Telemetry) {
        if (!active || closed || sample.sourceMode != mode) return
        latest = sample; receivedAt = clock.monotonicMs()
        if (loading) return
        if (failure?.code != "STORAGE_FAILED") failure = null
        apply(domain!!.update(sample, memories)); flush()
    }

    fun command(command: ExperienceCommand) {
        val sample = freshSample()
        if (!active || loading || sample == null) {
            failure = AppError("INVALID_STATE", "experience.no_fresh_sample", false, null)
        } else {
            when (val outcome = domain!!.handleCommand(command, sample, memories)) {
                is Outcome.Ok -> { failure = null; apply(outcome.value) }
                is Outcome.Err -> {
                    apply(domain!!.update(sample, memories))
                    failure = outcome.error
                }
            }
        }
        changed()
    }

    fun refresh() {
        if (!active || closed || loading || latest == null || clock.monotonicMs() - receivedAt <= 2_000) return
        apply(domain!!.update(freshSample()!!, memories)); flush()
        failure = AppError("STALE_DATA", "experience.stale_data", true, null)
        changed()
    }

    fun decorate(state: RenderState): RenderState = state.copy(experience = update.experience,
        skyItems = update.skyItems, memory = memories.firstOrNull { it.id == update.experience.selectedStarId }?.toMemoryCard(),
        error = failure ?: state.error)

    private fun apply(value: StarLogUpdate) { update = value; enqueue(value.completedVisits) }
    private fun enqueue(visits: List<Visit>) {
        for (visit in visits) pendingWrites[visit.dedupeKey] = visit
    }
    private fun flush() {
        if (closed || flushing || pendingWrites.isEmpty()) return
        flushing = true
        val batch = pendingWrites.values.toList(); val token = generation; val source = mode
        components.io.execute {
            val results = batch.map { it to attempt { components.repository.saveVisit(it) } }
            val stars = attempt { components.repository.listStars(source) }
            dispatch {
                if (closed) return@dispatch
                flushing = false
                results.forEach { (visit, result) ->
                    if (result is Outcome.Ok && pendingWrites[visit.dedupeKey] == visit) pendingWrites.remove(visit.dedupeKey)
                }
                if (active && token == generation) {
                    val error = results.map { it.second }.filterIsInstance<Outcome.Err>().firstOrNull()?.error
                    if (error != null) failure = error
                    else if (stars is Outcome.Err) failure = stars.error
                    else {
                        if (failure?.code == "STORAGE_FAILED") failure = null
                        memories = (stars as Outcome.Ok).value
                        freshSample()?.let { apply(domain!!.update(it, memories)) }
                    }
                    changed()
                }
                // A newer end timestamp may have arrived during IO. Failed batches wait for an explicit next tick.
                if (results.all { it.second is Outcome.Ok } && pendingWrites.isNotEmpty()) flush()
            }
        }
    }

    private fun freshSample(): Telemetry? {
        val value = latest ?: return null
        val elapsed = (clock.monotonicMs() - receivedAt).coerceAtLeast(0)
        fun <T> age(t: TimedValue<T>) = t.copy(ageMs = (t.ageMs + elapsed).coerceAtMost(MAX_SAFE_INTEGER))
        return value.copy(monotonicMs = (value.monotonicMs + elapsed).coerceAtMost(MAX_SAFE_INTEGER),
            location = age(value.location), speedMps = age(value.speedMps), gear = age(value.gear), ignitionOn = age(value.ignitionOn))
    }

    fun suspend() {
        if (!active || closed) return
        active = false; generation++
        val checkpoint = domain!!.checkpoint(latest?.observedAtMs ?: clock.wallTimeMs()).copy(sourceMode = mode)
        // Capture the newest final end timestamp even if an older upsert is still in flight.
        val finalWrites = pendingWrites.values.toList()
        components.io.execute {
            val writes = finalWrites.map { it to attempt { components.repository.saveVisit(it) } }
            val result = attempt { components.repository.saveCheckpoint(checkpoint) }
            dispatch {
                if (!closed) {
                    writes.forEach { (visit, saved) ->
                        if (saved is Outcome.Ok && pendingWrites[visit.dedupeKey] == visit) pendingWrites.remove(visit.dedupeKey)
                    }
                    val error = writes.map { it.second }.filterIsInstance<Outcome.Err>().firstOrNull()?.error
                        ?: (result as? Outcome.Err)?.error
                    if (error != null) { failure = error; changed() }
                }
            }
        }
        latest = null; update = emptyUpdate(); changed()
    }

    override fun close() {
        if (closed) return
        suspend(); closed = true
        components.io.execute { (components.repository as? AutoCloseable)?.close() }
        components.io.shutdown()
    }

    private fun <T> attempt(block: () -> Outcome<T>): Outcome<T> = try { block() }
        catch (_: Exception) { Outcome.Err(AppError("STORAGE_FAILED", "storage.operation_failed", true, null)) }

    private fun emptyUpdate() = StarLogUpdate(ExperienceState(ExperienceMode.DRIVING_SAFE, null, 0, null), emptyList(), emptyList())
}
