package pl.pulsebreath.wear.signal

import pl.pulsebreath.wear.sensor.SensorSample
import pl.pulsebreath.wear.sensor.SensorSignalQuality
import kotlin.math.sqrt

internal const val QUALITY_V1_WINDOW_MILLIS = 60_000L
private const val ADEQUATE_MINIMUM_VALID_IBI_COUNT = 10
private const val ADEQUATE_MINIMUM_IBI_EVENT_COVERAGE_PERCENT = 80.0

internal enum class HrvWindowQuality {
    INSUFFICIENT,
    ADEQUATE,
}

internal data class HrvMetrics(
    val analysisEndMillis: Long?,
    val sampleEventCount: Int,
    val goodSampleEventCount: Int,
    val validIbiEventCount: Int,
    val validIbiCount: Int,
    val invalidIbiCount: Int,
    val ibiEventCoveragePercent: Double,
    val meanBpm: Double?,
    val rmssdMillis: Double?,
    val quality: HrvWindowQuality,
    val rejectedIbiCount: Int = 0,
) {
    val displayRmssdMillis: Double?
        get() = rmssdMillis.takeIf { quality == HrvWindowQuality.ADEQUATE }
}

internal object HrvAnalyzer {
    fun analyze(
        samples: List<SensorSample>,
        windowMillis: Long = QUALITY_V1_WINDOW_MILLIS,
    ): HrvMetrics {
        require(windowMillis > 0L) { "windowMillis must be positive." }

        val analysisEndMillis = samples.maxOfOrNull(SensorSample::monotonicTimestampMillis)
        if (analysisEndMillis == null) {
            return emptyMetrics()
        }

        val windowStartMillis = analysisEndMillis - windowMillis
        val windowSamples =
            samples
                .withIndex()
                .filter { (_, sample) ->
                    sample.monotonicTimestampMillis in windowStartMillis..analysisEndMillis
                }
                .sortedWith(
                    compareBy<IndexedValue<SensorSample>>(
                        { it.value.monotonicTimestampMillis },
                        { it.index },
                    ),
                )
                .map(IndexedValue<SensorSample>::value)

        var validIbiCount = 0
        var invalidIbiCount = 0
        var rejectedIbiCount = 0
        var validIbiEventCount = 0
        var goodSampleEventCount = 0
        val validBpmValues = mutableListOf<Double>()
        var previousValidIbi: Long? = null
        var squaredDifferenceSum = 0.0
        var differenceCount = 0

        windowSamples.forEach { sample ->
            rejectedIbiCount += sample.rejectedIbiCount
            if (sample.quality != SensorSignalQuality.GOOD) {
                invalidIbiCount += sample.ibiMillis.count { it <= 0L }
                previousValidIbi = null
                return@forEach
            }

            goodSampleEventCount += 1
            sample.beatsPerMinute
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.let(validBpmValues::add)

            var eventHasValidIbi = false
            sample.ibiMillis.forEachIndexed { index, ibiMillis ->
                if (index in sample.ibiBreakBeforeIndices) previousValidIbi = null
                if (ibiMillis <= 0L) {
                    invalidIbiCount += 1
                    previousValidIbi = null
                } else {
                    eventHasValidIbi = true
                    validIbiCount += 1
                    previousValidIbi?.let { priorIbi ->
                        val difference = ibiMillis - priorIbi
                        squaredDifferenceSum += difference.toDouble() * difference.toDouble()
                        differenceCount += 1
                    }
                    previousValidIbi = ibiMillis
                }
            }
            if (sample.ibiMillis.size in sample.ibiBreakBeforeIndices) previousValidIbi = null
            if (eventHasValidIbi) {
                validIbiEventCount += 1
            }
        }

        val coverage =
            if (windowSamples.isEmpty()) {
                0.0
            } else {
                100.0 * validIbiEventCount / windowSamples.size
            }
        val quality =
            if (
                validIbiCount >= ADEQUATE_MINIMUM_VALID_IBI_COUNT &&
                coverage >= ADEQUATE_MINIMUM_IBI_EVENT_COVERAGE_PERCENT
            ) {
                HrvWindowQuality.ADEQUATE
            } else {
                HrvWindowQuality.INSUFFICIENT
            }

        return HrvMetrics(
            analysisEndMillis = analysisEndMillis,
            sampleEventCount = windowSamples.size,
            goodSampleEventCount = goodSampleEventCount,
            validIbiEventCount = validIbiEventCount,
            validIbiCount = validIbiCount,
            invalidIbiCount = invalidIbiCount,
            rejectedIbiCount = rejectedIbiCount,
            ibiEventCoveragePercent = coverage,
            meanBpm = validBpmValues.averageOrNull(),
            rmssdMillis =
                if (differenceCount == 0) null else sqrt(squaredDifferenceSum / differenceCount),
            quality = quality,
        )
    }

    private fun emptyMetrics() =
        HrvMetrics(
            analysisEndMillis = null,
            sampleEventCount = 0,
            goodSampleEventCount = 0,
            validIbiEventCount = 0,
            validIbiCount = 0,
            invalidIbiCount = 0,
            ibiEventCoveragePercent = 0.0,
            meanBpm = null,
            rmssdMillis = null,
            quality = HrvWindowQuality.INSUFFICIENT,
        )
}

private fun List<Double>.averageOrNull(): Double? =
    takeIf { it.isNotEmpty() }?.average()
