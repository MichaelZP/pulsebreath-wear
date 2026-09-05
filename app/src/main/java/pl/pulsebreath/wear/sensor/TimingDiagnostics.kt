package pl.pulsebreath.wear.sensor

/** Constant-size, session-local summary. Does not retain BPM or IBI values. */
internal data class TimingDiagnostics(
    val points: Long = 0,
    val callbackGroups: Long = 0,
    val multiPointGroups: Long = 0,
    val missingMetadata: Long = 0,
    val orderingErrors: Long = 0,
    val emptyCompanions: Long = 0,
    val maxBatchSize: Int = 0,
    val minSdkDelta: Long? = null,
    val maxSdkDelta: Long? = null,
    val minReceiptDelta: Long? = null,
    val maxReceiptDelta: Long? = null,
    val lastReason: TimingBlockReason? = null,
    val previous: SensorTiming? = null,
) {
    fun add(sample: SensorSample): TimingDiagnostics {
        val t = sample.timing ?: return copy(
            points = points + 1, missingMetadata = missingMetadata + 1,
            lastReason = TimingBlockReason.MISSING_METADATA, previous = null,
        )
        val reason = AdaptationTimingGate.assess(sample, t.receivedElapsedMillis, 1, previous)
        val newGroup = previous?.callbackSequence != t.callbackSequence
        // Only differences within the same clock are reported; never call this latency.
        val sdkDelta = previous?.let { nonnegativeDifference(t.sdkTimestampMillis, it.sdkTimestampMillis) }
        val receiptDelta = previous?.takeIf { newGroup }?.let {
            nonnegativeDifference(t.receivedElapsedMillis, it.receivedElapsedMillis)
        }
        return copy(
            points = points + 1,
            callbackGroups = callbackGroups + if (newGroup) 1 else 0,
            multiPointGroups = multiPointGroups + if (newGroup && t.pointCount > 1) 1 else 0,
            orderingErrors = orderingErrors + if (reason == TimingBlockReason.ORDERING_ERROR) 1 else 0,
            emptyCompanions = emptyCompanions + if (reason == TimingBlockReason.EMPTY_BATCH_COMPANION) 1 else 0,
            maxBatchSize = maxOf(maxBatchSize, t.pointCount),
            minSdkDelta = minimum(minSdkDelta, sdkDelta),
            maxSdkDelta = maximum(maxSdkDelta, sdkDelta),
            minReceiptDelta = minimum(minReceiptDelta, receiptDelta),
            maxReceiptDelta = maximum(maxReceiptDelta, receiptDelta),
            lastReason = reason,
            previous = t,
        )
    }

    fun receiptAge(now: Long): Long? = previous?.let {
        nonnegativeDifference(now, it.receivedElapsedMillis)
    }
}

private fun nonnegativeDifference(a: Long, b: Long): Long? =
    if (a < b) null else runCatching { Math.subtractExact(a, b) }.getOrNull()
private fun minimum(a: Long?, b: Long?): Long? = when {
    a == null -> b
    b == null -> a
    else -> minOf(a, b)
}
private fun maximum(a: Long?, b: Long?): Long? = when {
    a == null -> b
    b == null -> a
    else -> maxOf(a, b)
}
