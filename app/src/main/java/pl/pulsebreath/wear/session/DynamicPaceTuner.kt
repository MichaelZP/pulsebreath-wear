package pl.pulsebreath.wear.session

import pl.pulsebreath.wear.signal.PaceEstimate
import pl.pulsebreath.wear.signal.PaceEstimateConfidence
import pl.pulsebreath.wear.signal.PaceCalibrator
import pl.pulsebreath.wear.signal.TimedIbi
import kotlin.math.abs

/** Session-local, bounded cue retuning from the existing receipt-anchored IBI estimator. */
internal class DynamicPaceTuner(
    private val estimator: (List<TimedIbi>) -> PaceEstimate = PaceCalibrator::estimate,
) {
    companion object {
        const val RECHECK_MILLIS = 60_000L
        const val MAX_CYCLE_DELTA_MILLIS = 500L
        private const val AGREEMENT_MILLIS = 250L
    }

    private val ibi = mutableListOf<TimedIbi>()
    private var lastCheckedAt = Long.MIN_VALUE
    private var priorCandidate: Long? = null

    fun reset() {
        ibi.clear()
        lastCheckedAt = Long.MIN_VALUE
        priorCandidate = null
    }

    /** Returns a replacement estimate only after two independent, high-confidence windows agree. */
    fun observe(
        intervals: List<TimedIbi>,
        nowMillis: Long,
        currentCycleMillis: Long,
        allowWeakConfidence: Boolean,
    ): PaceEstimate? {
        ibi += intervals
        ibi.removeAll { it.endMillis != null && it.endMillis < nowMillis - 70_000L }
        if (lastCheckedAt != Long.MIN_VALUE && nowMillis - lastCheckedAt < RECHECK_MILLIS) return null
        lastCheckedAt = nowMillis
        val candidate = estimator(ibi)
        val confidenceAllowed = candidate.confidence == PaceEstimateConfidence.HIGH ||
            (allowWeakConfidence && candidate.confidence == PaceEstimateConfidence.WEAK)
        if (candidate.usedFallback || !confidenceAllowed ||
            abs(candidate.cycleMillis - currentCycleMillis) > MAX_CYCLE_DELTA_MILLIS
        ) {
            priorCandidate = null
            return null
        }
        val previous = priorCandidate
        priorCandidate = candidate.cycleMillis
        return candidate.takeIf { previous != null && abs(previous - candidate.cycleMillis) <= AGREEMENT_MILLIS }
    }
}
