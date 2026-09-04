package pl.pulsebreath.wear.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptationTimingGateTest {
    private val timing = SensorTiming(1000, 987654321, 0, 0, 1, 1, 1)
    private fun sample(t: SensorTiming? = timing) = SensorSample(
        1000, 60.0, listOf(1000), SensorSignalQuality.GOOD, SensorSourceType.SAMSUNG,
        timing = t,
    )
    private fun assess(s: SensorSample, now: Long = 1000, prior: SensorTiming? = null) =
        AdaptationTimingGate.assess(s, now, 100, prior)

    @Test fun freshGoodDataStillCannotAuthorizeAdaptation() {
        assertEquals(TimingBlockReason.UNKNOWN_BEAT_ALIGNMENT, assess(sample()))
    }
    @Test fun absenceOfCallbacksAgesOutWithoutNewSamples() {
        assertEquals(TimingBlockReason.STALE_RECEIPT, assess(sample(), 1101))
        assertEquals(TimingBlockReason.CLOCK_ERROR, assess(sample(), 999))
    }
    @Test fun missingAndMalformedMetadataAreBlocked() {
        assertEquals(TimingBlockReason.MISSING_METADATA, assess(sample(null)))
        assertEquals(TimingBlockReason.INVALID_METADATA, assess(sample(timing.copy(pointCount = 0))))
        assertEquals(TimingBlockReason.INVALID_METADATA, assess(sample(timing.copy(rawIbiCount = 2, rawStatusCount = 2))))
    }
    @Test fun duplicateAndOutOfOrderEventsAreBlocked() {
        assertEquals(TimingBlockReason.ORDERING_ERROR, assess(sample(), prior = timing))
        assertEquals(TimingBlockReason.ORDERING_ERROR,
            assess(sample(timing.copy(callbackSequence = 1, sdkTimestampMillis = 0)), prior = timing))
    }
    @Test fun emptyBatchCompanionIsNotLabeledSignalLoss() {
        val first = timing.copy(pointCount = 2)
        val next = first.copy(pointIndex = 1, sdkTimestampMillis = first.sdkTimestampMillis + 1,
            rawIbiCount = 0, rawStatusCount = 0)
        assertEquals(TimingBlockReason.EMPTY_BATCH_COMPANION,
            assess(sample(next).copy(ibiMillis = emptyList()), prior = first))
    }
    @Test fun mismatchesAndRejectionsRemainVisible() {
        assertEquals(TimingBlockReason.MISMATCHED_LISTS, assess(sample(timing.copy(rawStatusCount = 0))))
        assertEquals(TimingBlockReason.REJECTED_IBI, assess(sample().copy(rejectedIbiCount = 1)))
        assertEquals(TimingBlockReason.INADEQUATE_SIGNAL,
            assess(sample().copy(quality = SensorSignalQuality.SIGNAL_LOST)))
    }
}
