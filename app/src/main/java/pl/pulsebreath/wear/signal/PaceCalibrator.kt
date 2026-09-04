package pl.pulsebreath.wear.signal

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

internal enum class PaceFallbackReason {
    TOO_FEW_INTERVALS, SHORT_CONTINUOUS_SEGMENT, INVALID_TIME_ORDER, NO_CLEAR_PEAK,
}

internal data class PaceEstimate(
    val cycleMillis: Long,
    val inhaleMillis: Long,
    val exhaleMillis: Long,
    val usedFallback: Boolean,
    val acceptedIbiCount: Int,
    val peakCorrelation: Double?,
    val fallbackReason: PaceFallbackReason?,
    val analyzedIbiCount: Int,
)

/** Experimental pace_v1; estimates IBI periodicity, not measured respiration. */
internal object PaceCalibrator {
    const val CALIBRATION_DURATION_MILLIS = 35_000L

    fun estimate(timedIbi: List<TimedIbi>): PaceEstimate {
        val latest = timedIbi.mapNotNull { it.endMillis }.maxOrNull() ?: 0L
        val start = (latest - CALIBRATION_DURATION_MILLIS).coerceAtLeast(0)
        val acceptedCount = timedIbi.count {
            it.accepted && it.ibiMillis > 0 && it.endMillis?.let { t -> t in start..latest } == true
        }
        fun fallback(reason: PaceFallbackReason, analyzed: Int = 0, peak: Double? = null) =
            PaceEstimate(10_000, 4_500, 5_500, true, acceptedCount, peak, reason, analyzed)

        // Preserve input order and every explicit break. Never sort away delivery disorder.
        val segments = mutableListOf<MutableList<TimedIbi>>()
        var current = mutableListOf<TimedIbi>()
        var previousTime: Long? = null
        for (beat in timedIbi) {
            val t = beat.endMillis
            if (t != null) {
                if (t < 0 || previousTime?.let { t <= it } == true) {
                    return fallback(PaceFallbackReason.INVALID_TIME_ORDER)
                }
                previousTime = t
            }
            if (!beat.accepted || beat.ibiMillis <= 0 || t == null || t < start) {
                current = mutableListOf()
                continue
            }
            val previous = current.lastOrNull()
            val timingGap = previous != null &&
                abs((t - previous.endMillis!!).toDouble() - beat.ibiMillis.toDouble()) > 250.0
            if (beat.breakBefore || timingGap) current = mutableListOf()
            if (current.isEmpty()) segments.add(current)
            current.add(beat)
        }
        if (acceptedCount < 12) return fallback(PaceFallbackReason.TOO_FEW_INTERVALS)
        val segment = segments.filter { it.size >= 12 }
            .maxByOrNull { it.last().endMillis!! - it.first().endMillis!! }
            ?: return fallback(PaceFallbackReason.SHORT_CONTINUOUS_SEGMENT)
        val span = segment.last().endMillis!! - segment.first().endMillis!!
        if (span < 24_000) return fallback(PaceFallbackReason.SHORT_CONTINUOUS_SEGMENT, segment.size)

        val times = segment.map { (it.endMillis!! - segment.first().endMillis!!).toDouble() }
        val values = segment.map { it.ibiMillis.toDouble() }
        val meanT = times.average()
        val meanY = values.average()
        val slope = times.indices.sumOf { (times[it] - meanT) * (values[it] - meanY) } /
            times.sumOf { (it - meanT) * (it - meanT) }
        val residuals = times.indices.map { values[it] - meanY - slope * (times[it] - meanT) }
        if (residuals.sumOf { it * it } / residuals.size < 1.0) {
            return fallback(PaceFallbackReason.NO_CLEAR_PEAK, segment.size)
        }

        // Irregular-time lag bins: real pairs only, no generated tachogram samples.
        val correlations = (6_000L..16_000L step 250L).map { lag ->
            val pairs = mutableListOf<Pair<Double, Double>>()
            for (i in times.indices) for (j in i + 1 until times.size) {
                if (abs(times[j] - times[i] - lag) <= 400.0) {
                    pairs.add(residuals[i] to residuals[j])
                }
            }
            if (pairs.size < 8 || span < 2 * lag) null else correlation(pairs)
        }
        val peakIndex = (1 until correlations.lastIndex).filter { i ->
            val value = correlations[i]
            value != null && correlations[i - 1]?.let { value >= it } == true &&
                correlations[i + 1]?.let { value > it } == true
        }.maxByOrNull { correlations[it]!! }
        val peak = peakIndex?.let { correlations[it] }
        val trough = correlations.filterNotNull().minOrNull()
        if (peak == null || peak < 0.6 || trough == null || peak - trough < 0.3) {
            return fallback(PaceFallbackReason.NO_CLEAR_PEAK, segment.size, peak)
        }
        val cycle = (6_000L + peakIndex * 250L).coerceIn(8_000, 14_000)
        val inhale = (cycle * 0.45).roundToLong()
        return PaceEstimate(cycle, inhale, cycle - inhale, false, acceptedCount, nullIfNonFinite(peak),
            null, segment.size)
    }

    private fun nullIfNonFinite(value: Double) = value.takeIf { it.isFinite() }

    private fun correlation(pairs: List<Pair<Double, Double>>): Double? {
        val x = pairs.map { it.first }.average()
        val y = pairs.map { it.second }.average()
        val xx = pairs.sumOf { (it.first - x) * (it.first - x) }
        val yy = pairs.sumOf { (it.second - y) * (it.second - y) }
        if (xx <= 1e-9 || yy <= 1e-9) return null
        val xy = pairs.sumOf { (it.first - x) * (it.second - y) }
        return nullIfNonFinite(xy / sqrt(xx * yy))?.coerceIn(-1.0, 1.0)
    }
}
