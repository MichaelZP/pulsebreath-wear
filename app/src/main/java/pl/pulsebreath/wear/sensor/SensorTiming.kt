package pl.pulsebreath.wear.sensor

/** SDK timestamp is opaque: its epoch and beat anchor have not been established. */
internal data class SensorTiming(
    val receivedElapsedMillis: Long,
    val sdkTimestampMillis: Long,
    val callbackSequence: Long,
    val pointIndex: Int,
    val pointCount: Int,
    val rawIbiCount: Int,
    val rawStatusCount: Int,
)

internal enum class TimingBlockReason {
    MISSING_METADATA, INVALID_METADATA, STALE_RECEIPT, CLOCK_ERROR,
    ORDERING_ERROR, MISMATCHED_LISTS, REJECTED_IBI, INADEQUATE_SIGNAL,
    EMPTY_BATCH_COMPANION, NO_IBI, UNKNOWN_BEAT_ALIGNMENT,
}

/** No eligible outcome exists until measurement clock and beat timing are validated. */
internal object AdaptationTimingGate {
    fun assess(
        sample: SensorSample,
        nowElapsedMillis: Long,
        maxReceiptAgeMillis: Long,
        previous: SensorTiming? = null,
    ): TimingBlockReason {
        require(maxReceiptAgeMillis > 0)
        val t = sample.timing ?: return TimingBlockReason.MISSING_METADATA
        if (t.receivedElapsedMillis < 0 || t.callbackSequence < 0 || t.pointCount <= 0 ||
            t.pointIndex !in 0 until t.pointCount || t.rawIbiCount < 0 || t.rawStatusCount < 0 ||
            sample.monotonicTimestampMillis != t.receivedElapsedMillis
        ) return TimingBlockReason.INVALID_METADATA
        if (nowElapsedMillis < t.receivedElapsedMillis) return TimingBlockReason.CLOCK_ERROR
        if (nowElapsedMillis - t.receivedElapsedMillis > maxReceiptAgeMillis) {
            return TimingBlockReason.STALE_RECEIPT
        }
        if (previous != null) {
            val sameCallback = t.callbackSequence == previous.callbackSequence
            if (t.callbackSequence < previous.callbackSequence ||
                t.receivedElapsedMillis < previous.receivedElapsedMillis ||
                t.sdkTimestampMillis <= previous.sdkTimestampMillis ||
                (sameCallback && (t.pointIndex != previous.pointIndex + 1 ||
                    t.pointCount != previous.pointCount ||
                    t.receivedElapsedMillis != previous.receivedElapsedMillis)) ||
                (!sameCallback && (t.callbackSequence != previous.callbackSequence + 1 ||
                    t.pointIndex != 0 || previous.pointIndex != previous.pointCount - 1))
            ) return TimingBlockReason.ORDERING_ERROR
        } else if (t.pointIndex != 0) return TimingBlockReason.ORDERING_ERROR
        if (t.rawIbiCount != t.rawStatusCount) return TimingBlockReason.MISMATCHED_LISTS
        if (sample.rejectedIbiCount > 0 || sample.ibiBreakBeforeIndices.isNotEmpty() ||
            sample.ibiMillis.any { it <= 0 }) return TimingBlockReason.REJECTED_IBI
        if (sample.quality != SensorSignalQuality.GOOD) return TimingBlockReason.INADEQUATE_SIGNAL
        if (t.rawIbiCount == 0 && sample.ibiMillis.isEmpty()) {
            return if (t.pointCount > 1 && t.pointIndex > 0) TimingBlockReason.EMPTY_BATCH_COMPANION
            else TimingBlockReason.NO_IBI
        }
        if (t.rawIbiCount != sample.ibiMillis.size) return TimingBlockReason.INVALID_METADATA
        return TimingBlockReason.UNKNOWN_BEAT_ALIGNMENT
    }
}
