package pl.pulsebreath.wear.signal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PaceCalibratorTest {
    private fun oscillation(period: Long = 10_000, trend: Double = 0.0): List<TimedIbi> {
        val beats = mutableListOf<TimedIbi>()
        var t = 0L
        while (t < 35_000) {
            val ibi = (800 + 65 * sin(2 * PI * t / period) + trend * t / 1000).roundToLong()
            t += ibi
            if (t <= 35_000) beats.add(TimedIbi(t, ibi, true, beats.size, false))
        }
        return beats
    }

    @Test fun recoversTenSecondOscillationWithAndWithoutTrend() {
        for (trend in listOf(0.0, 2.0)) {
            val estimate = PaceCalibrator.estimate(oscillation(trend = trend))
            assertFalse(estimate.toString(), estimate.usedFallback)
            assertTrue(estimate.toString(), abs(estimate.cycleMillis - 10_000) <= 500)
            assertEquals(estimate.cycleMillis, estimate.inhaleMillis + estimate.exhaleMillis)
            assertEquals((estimate.cycleMillis * 0.45).roundToLong(), estimate.inhaleMillis)
            assertNull(estimate.fallbackReason)
            assertEquals(PaceEstimateMode.CONTINUOUS, estimate.estimateMode)
            assertEquals(PaceEstimateConfidence.HIGH, estimate.confidence)
        }
    }

    @Test fun clampsDetectedCycleToConfiguredRange() {
        for ((period, expected) in listOf(7_000L to 8_000L, 15_000L to 14_000L)) {
            val estimate = PaceCalibrator.estimate(oscillation(period))
            assertFalse(estimate.toString(), estimate.usedFallback)
            assertEquals(expected, estimate.cycleMillis)
        }
    }

    @Test fun insufficientDataUsesAnExplicitFallbackButFlatDataStartsWithTheDefaultCue() {
        val flat = (1..43).map { TimedIbi(it * 800L, 800, true, it, false) }
        for (input in listOf(emptyList(), oscillation().take(11))) {
            val estimate = PaceCalibrator.estimate(input)
            assertTrue(estimate.usedFallback)
            assertEquals(4_500L, estimate.inhaleMillis)
            assertEquals(5_500L, estimate.exhaleMillis)
            assertNotNull(estimate.fallbackReason)
        }
        val estimate = PaceCalibrator.estimate(flat)
        assertFalse(estimate.usedFallback)
        assertEquals(PaceEstimateMode.DEFAULT_NO_PEAK, estimate.estimateMode)
        assertEquals(PaceEstimateConfidence.DEFAULT, estimate.confidence)
    }

    @Test fun pooledPathUsesAcceptedPlacedPointsAcrossExplicitBreaks() {
        val broken = oscillation().mapIndexed { index, beat ->
            beat.copy(breakBefore = index > 0 && index % 4 == 0)
        }
        val estimate = PaceCalibrator.estimate(broken)
        assertFalse(estimate.toString(), estimate.usedFallback)
        assertEquals(PaceEstimateMode.POOLED, estimate.estimateMode)
        assertTrue(estimate.analyzedIbiCount >= 12)
    }

    @Test fun timeOrderGlitchesBreakSegmentsWithoutRejectingWholeEstimate() {
        val input = oscillation()
        val duplicate = PaceCalibrator.estimate(listOf(input.first()) + input)
        assertNotEquals(PaceFallbackReason.INVALID_TIME_ORDER, duplicate.fallbackReason)
        assertFalse(duplicate.toString(), duplicate.usedFallback)
        assertEquals(PaceEstimateMode.CONTINUOUS, duplicate.estimateMode)
        val reversed = PaceCalibrator.estimate(input.reversed())
        assertTrue(reversed.usedFallback)
        assertNotEquals(PaceFallbackReason.INVALID_TIME_ORDER, reversed.fallbackReason)
    }

    @Test fun unexplainedDeliveryGapSplitsTheSegment() {
        val input = oscillation().mapIndexed { index, beat ->
            if (index > 20) beat.copy(endMillis = beat.endMillis!! + 2_000) else beat
        }
        val estimate = PaceCalibrator.estimate(input)
        assertFalse(estimate.toString(), estimate.usedFallback)
        assertEquals(PaceEstimateMode.POOLED, estimate.estimateMode)
    }

    @Test fun nonperiodicNoiseNeverClaimsHighConfidence() {
        val random = Random(71)
        var t = 0L
        val input = (0..40).map { index ->
            val ibi = 800L + random.nextInt(-90, 91)
            t += ibi
            TimedIbi(t, ibi, true, index, false)
        }
        val estimate = PaceCalibrator.estimate(input)
        assertFalse(estimate.usedFallback)
        assertNotEquals(PaceEstimateConfidence.HIGH, estimate.confidence)
    }

    @Test fun oldValidOscillationCannotRescueRecentFlatData() {
        val old = oscillation()
        var t = old.last().endMillis!!
        val recent = (0..45).map { index ->
            t += 800
            TimedIbi(t, 800, true, index, false)
        }
        val estimate = PaceCalibrator.estimate(old + recent)
        assertFalse(estimate.usedFallback)
        assertEquals(44, estimate.acceptedIbiCount)
        assertEquals(44, estimate.analyzedIbiCount)
        assertEquals(PaceEstimateMode.DEFAULT_NO_PEAK, estimate.estimateMode)
    }

    @Test fun enoughIntervalsButTooLittleTimeUseTheDefaultCue() {
        val estimate = PaceCalibrator.estimate(oscillation().take(15))
        assertFalse(estimate.usedFallback)
        assertEquals(PaceEstimateMode.DEFAULT_NO_PEAK, estimate.estimateMode)
    }

    @Test fun fewerThanTwelveEligibleIntervalsStillFallsBack() {
        val estimate = PaceCalibrator.estimate(oscillation().take(11))
        assertEquals(PaceFallbackReason.TOO_FEW_INTERVALS, estimate.fallbackReason)
        assertEquals(PaceEstimateMode.FALLBACK, estimate.estimateMode)
        assertEquals(PaceEstimateConfidence.NONE, estimate.confidence)
    }

    @Test fun moderatePeriodicEvidenceUsesAWeakPeak() {
        val random = Random(38)
        val beats = mutableListOf<TimedIbi>()
        var time = 0L
        while (time < 35_000) {
            val ibi = (800 + 32 * sin(2 * PI * time / 10_000) + random.nextInt(-45, 46)).roundToLong()
            time += ibi
            if (time <= 35_000) beats.add(TimedIbi(time, ibi, true, beats.size, false))
        }
        val estimate = PaceCalibrator.estimate(beats)
        assertFalse(estimate.toString(), estimate.usedFallback)
        assertEquals(PaceEstimateMode.CONTINUOUS, estimate.estimateMode)
        assertEquals(PaceEstimateConfidence.WEAK, estimate.confidence)
        assertNotNull(estimate.peakCorrelation)
        assertTrue(estimate.peakCorrelation!! >= 0.35)
    }
}
