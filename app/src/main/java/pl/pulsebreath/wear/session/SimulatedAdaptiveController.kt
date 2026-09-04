package pl.pulsebreath.wear.session

import kotlin.math.abs

/** Software-test protocol only. No defaults are a prescription for human breathing. */
internal data class SimulationProtocol(
    val cycleMillis: List<Long>,
    val windowMillis: Long,
    val restMillis: Long,
    val freshnessMillis: Long,
    val timeoutMillis: Long,
    val sessionMillis: Long,
    val minMargin: Double,
    val maxCycleChangeMillis: Long,
) {
    init {
        require(cycleMillis.size >= 2 && cycleMillis.distinct().size == cycleMillis.size)
        require(cycleMillis.all { it > 0 && it <= windowMillis })
        require(windowMillis > 0 && restMillis > 0 && freshnessMillis > 0)
        require(timeoutMillis >= windowMillis && sessionMillis > 0)
        require(minMargin.isFinite() && minMargin > 0 && maxCycleChangeMillis > 0)
    }
}

internal enum class AdaptiveStage {
    IDLE, ACQUIRING, TRIAL, REST, UNAVAILABLE, READY, ACTIVE, SUSPENDED,
    PAUSED, COMPLETED, CANCELLED, ERROR,
}

/** Synthetic comparison scores, not HRV, measured resonance, or a sensor adapter. */
internal data class SimulatedWindow(
    val startMillis: Long,
    val endMillis: Long,
    val scores: Map<Long, Double>,
    val adequate: Boolean = true,
)

internal data class AdaptiveSnapshot(
    val stage: AdaptiveStage,
    val cycleMillis: Long?,
    val cycleProgress: Double?,
    val trialIndex: Int,
    val pendingCycleMillis: Long?,
    val reason: String?,
)

/** Explicitly simulation-only; must not be wired to Samsung data as a scoring algorithm. */
internal class SimulatedAdaptiveController(protocol: SimulationProtocol) {
    private val p = protocol.copy(cycleMillis = protocol.cycleMillis.toList())
    private var stage = AdaptiveStage.IDLE
    private var lastNow = 0L
    private var since = 0L
    private var evidenceAfter = 0L
    private var lastEvidenceEnd = -1L
    private var cycle: Long? = null
    private var anchor = 0L
    private var activeStart = 0L
    private var activeSpent = 0L
    private var trial = 0
    private val results = mutableListOf<Double>()
    private var pending: Long? = null
    private var pendingAt = 0L
    private var preferred: Long? = null
    private var reason: String? = null
    private var resumeAdaptive = false

    fun start(now: Long) {
        require(now >= 0)
        lastNow = now
        since = now
        evidenceAfter = now
        lastEvidenceEnd = -1
        cycle = null
        trial = 0
        results.clear()
        clearDecision()
        activeSpent = 0
        resumeAdaptive = false
        reason = null
        stage = AdaptiveStage.ACQUIRING
    }

    fun tick(now: Long): AdaptiveSnapshot {
        if (now < lastNow) {
            fail("clock moved backwards")
            return snapshot(lastNow)
        }
        lastNow = now
        if (stage == AdaptiveStage.ACTIVE || stage == AdaptiveStage.SUSPENDED) {
            if (activeSpent + now - activeStart >= p.sessionMillis) {
                stage = AdaptiveStage.COMPLETED
                clearDecision()
            } else if (stage == AdaptiveStage.ACTIVE && now - lastEvidenceEnd > p.freshnessMillis) {
                suspendAt(now, "stale or absent evidence")
            }
        }
        if ((stage == AdaptiveStage.ACQUIRING || stage == AdaptiveStage.TRIAL) &&
            now - since > p.timeoutMillis
        ) unavailable("signal acquisition or trial timed out")
        if (stage == AdaptiveStage.REST && now - since >= p.restMillis) {
            beginTrial(now)
        }
        if (stage == AdaptiveStage.ACTIVE && pending != null && now >= pendingAt) {
            cycle = pending
            anchor = pendingAt
            evidenceAfter = pendingAt
            clearDecision()
        }
        return snapshot(now)
    }

    fun accept(window: SimulatedWindow, now: Long) {
        tick(now)
        if (stage !in setOf(AdaptiveStage.ACQUIRING, AdaptiveStage.TRIAL,
                AdaptiveStage.ACTIVE, AdaptiveStage.SUSPENDED)) return
        val valid = window.adequate && window.startMillis >= evidenceAfter &&
            window.startMillis >= 0 && window.endMillis <= now &&
            window.endMillis - window.startMillis == p.windowMillis &&
            now - window.endMillis <= p.freshnessMillis &&
            window.startMillis >= lastEvidenceEnd && window.endMillis > lastEvidenceEnd &&
            window.scores.isNotEmpty() && window.scores.values.all { it.isFinite() }
        if (!valid) {
            if (stage == AdaptiveStage.ACTIVE || stage == AdaptiveStage.SUSPENDED) {
                suspendAt(now, "unusable simulation window")
            } else unavailable("unusable simulation window")
            return
        }
        when (stage) {
            AdaptiveStage.ACQUIRING -> {
                lastEvidenceEnd = window.endMillis
                if (resumeAdaptive) {
                    resumeAdaptive = false
                    stage = AdaptiveStage.ACTIVE
                    activeStart = now
                    anchor = now
                    evidenceAfter = now
                } else beginTrial(now)
            }
            AdaptiveStage.TRIAL -> {
                val score = window.scores[cycle]
                if (window.scores.size != 1 || score == null) {
                    unavailable("trial requires its own cycle score")
                    return
                }
                lastEvidenceEnd = window.endMillis
                results.add(score)
                trial++
                if (trial == p.cycleMillis.size * 2) selectCandidate() else {
                    stage = AdaptiveStage.REST
                    since = now
                }
            }
            AdaptiveStage.ACTIVE, AdaptiveStage.SUSPENDED -> {
                if (window.scores.keys != p.cycleMillis.toSet()) {
                    suspendAt(now, "incomplete synthetic comparison")
                    return
                }
                lastEvidenceEnd = window.endMillis
                stage = AdaptiveStage.ACTIVE
                reason = null
                val best = winner(window.scores)
                if (pending != null) return
                if (best == null || best == cycle || abs(best - cycle!!) > p.maxCycleChangeMillis) {
                    preferred = null
                    return
                }
                if (preferred == best) {
                    pending = best
                    pendingAt = now + (cycle!! - (now - anchor) % cycle!!)
                } else preferred = best
            }
            else -> Unit
        }
    }

    fun beginSession(now: Long) {
        tick(now)
        if (stage != AdaptiveStage.READY) return
        resumeAdaptive = true
        stage = AdaptiveStage.ACQUIRING
        since = now
        evidenceAfter = now
        clearDecision()
    }

    fun pause(now: Long) {
        tick(now)
        if (stage !in setOf(AdaptiveStage.ACQUIRING, AdaptiveStage.TRIAL,
                AdaptiveStage.REST, AdaptiveStage.READY, AdaptiveStage.ACTIVE, AdaptiveStage.SUSPENDED)) return
        resumeAdaptive = stage == AdaptiveStage.ACTIVE || stage == AdaptiveStage.SUSPENDED ||
            stage == AdaptiveStage.READY || (stage == AdaptiveStage.ACQUIRING && resumeAdaptive)
        if (stage == AdaptiveStage.ACTIVE || stage == AdaptiveStage.SUSPENDED) activeSpent += now - activeStart
        stage = AdaptiveStage.PAUSED
        clearDecision()
    }

    fun resume(now: Long) {
        tick(now)
        if (stage != AdaptiveStage.PAUSED) return
        if (!resumeAdaptive) {
            trial = 0
            results.clear()
            cycle = null
        }
        stage = AdaptiveStage.ACQUIRING
        since = now
        evidenceAfter = now
        reason = null
    }

    fun stop(now: Long) {
        tick(now)
        stage = AdaptiveStage.CANCELLED
        clearDecision()
    }

    fun fail(message: String) {
        stage = AdaptiveStage.ERROR
        reason = message
        clearDecision()
    }

    private fun beginTrial(now: Long) {
        stage = AdaptiveStage.TRIAL
        cycle = p.cycleMillis[trial % p.cycleMillis.size]
        anchor = now
        since = now
        evidenceAfter = now
    }

    private fun selectCandidate() {
        val n = p.cycleMillis.size
        val first = winner(p.cycleMillis.zip(results.take(n)).toMap())
        val second = winner(p.cycleMillis.zip(results.drop(n)).toMap())
        if (first == null || first != second) unavailable("tie or non-repeatable preference") else {
            cycle = first
            stage = AdaptiveStage.READY
        }
    }

    private fun winner(scores: Map<Long, Double>): Long? {
        val sorted = scores.entries.sortedByDescending { it.value }
        return sorted.first().key.takeIf {
            sorted.size >= 2 && sorted[0].value - sorted[1].value >= p.minMargin
        }
    }

    private fun suspendAt(now: Long, message: String) {
        stage = AdaptiveStage.SUSPENDED
        evidenceAfter = now
        reason = message
        clearDecision()
    }

    private fun unavailable(message: String) {
        stage = AdaptiveStage.UNAVAILABLE
        cycle = null
        reason = message
        clearDecision()
    }

    private fun clearDecision() {
        pending = null
        preferred = null
    }

    private fun snapshot(now: Long) = AdaptiveSnapshot(
        stage = stage,
        cycleMillis = cycle,
        cycleProgress = if (stage == AdaptiveStage.ACTIVE || stage == AdaptiveStage.TRIAL ||
            stage == AdaptiveStage.SUSPENDED) ((now - anchor) % cycle!!).toDouble() / cycle!! else null,
        trialIndex = trial,
        pendingCycleMillis = pending,
        reason = reason,
    )
}
