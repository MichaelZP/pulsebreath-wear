package pl.pulsebreath.wear.signal

import pl.pulsebreath.wear.sensor.SensorSample

/** Receipt-anchored estimate, never a measured beat timestamp. Null means unplaceable. */
internal data class TimedIbi(
    val endMillis: Long?,
    val ibiMillis: Long,
    val accepted: Boolean,
    val sourceIndex: Int,
    val breakBefore: Boolean,
)

internal data class EstimatedIbiBatch(
    val intervals: List<TimedIbi>,
    val rejectedEntryCount: Int,
    val trailingBreak: Boolean,
) {
    val unplacedCount: Int get() = intervals.count { it.endMillis == null }
}

/** Assumes chronological retained IBI and anchors only the final continuous suffix. */
internal fun expandBatch(receiptMillis: Long, sample: SensorSample): EstimatedIbiBatch {
    require(receiptMillis >= 0)
    require(sample.rejectedIbiCount >= 0)
    val values = sample.ibiMillis
    require(sample.ibiBreakBeforeIndices.all { it in 0..values.size })
    val trailingBreak = values.size in sample.ibiBreakBeforeIndices
    val unknownRejectionPosition = sample.rejectedIbiCount > 0 && sample.ibiBreakBeforeIndices.isEmpty()
    var end: Long? = receiptMillis.takeUnless { trailingBreak || unknownRejectionPosition }
    val reversed = mutableListOf<TimedIbi>()
    for (index in values.indices.reversed()) {
        val ibi = values[index]
        // Samsung mapping has already retained only NORMAL-status IBI values. Pace
        // placement must not discard those intervals when BPM quality flickers.
        val accepted = ibi > 0
        if (!accepted) end = null
        reversed.add(TimedIbi(end, ibi, accepted, index,
            index in sample.ibiBreakBeforeIndices || unknownRejectionPosition))
        end = end?.let { if (ibi <= it) it - ibi else null }
        // The rejected interval's duration was not retained; never step across it.
        if (index in sample.ibiBreakBeforeIndices) end = null
    }
    return EstimatedIbiBatch(reversed.asReversed().toList(), sample.rejectedIbiCount, trailingBreak)
}
