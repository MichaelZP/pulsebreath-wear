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
        }
    }

    @Test fun clampsDetectedCycleToConfiguredRange() {
        for ((period, expected) in listOf(7_000L to 8_000L, 15_000L to 14_000L)) {
            val estimate = PaceCalibrator.estimate(oscillation(period))
            assertFalse(estimate.toString(), estimate.usedFallback)
            assertEquals(expected, estimate.cycleMillis)
        }
    }

    @Test fun insufficientAndFlatDataUseExplicitDefaults() {
        val flat = (1..43).map { TimedIbi(it * 800L, 800, true, it, false) }
        for (input in listOf(emptyList(), oscillation().take(11), flat)) {
            val estimate = PaceCalibrator.estimate(input)
            assertTrue(estimate.usedFallback)
            assertEquals(4_500L, estimate.inhaleMillis)
            assertEquals(5_500L, estimate.exhaleMillis)
            assertNotNull(estimate.fallbackReason)
        }
    }

    @Test fun rejectionUnknownTimeAndExplicitBreakNeverGlueShortSegments() {
        val input = oscillation()
        val middle = input.size / 2
        for (replacement in listOf(input[middle].copy(accepted = false),
            input[middle].copy(endMillis = null), input[middle].copy(breakBefore = true))) {
            val broken = input.toMutableList().also { it[middle] = replacement }
            assertEquals(PaceFallbackReason.SHORT_CONTINUOUS_SEGMENT,
                PaceCalibrator.estimate(broken).fallbackReason)
        }
    }

    @Test fun duplicateOrReversedTimesFailClosed() {
        val input = oscillation()
        assertEquals(PaceFallbackReason.INVALID_TIME_ORDER,
            PaceCalibrator.estimate(input.reversed()).fallbackReason)
        assertEquals(PaceFallbackReason.INVALID_TIME_ORDER,
            PaceCalibrator.estimate(listOf(input.first()) + input).fallbackReason)
    }

    @Test fun unexplainedDeliveryGapSplitsTheSegment() {
        val input = oscillation().mapIndexed { index, beat ->
            if (index > 20) beat.copy(endMillis = beat.endMillis!! + 2_000) else beat
        }
        assertTrue(PaceCalibrator.estimate(input).usedFallback)
    }

    @Test fun nonperiodicNoiseFallsBack() {
        val random = Random(71)
        var t = 0L
        val input = (0..40).map { index ->
            val ibi = 800L + random.nextInt(-90, 91)
            t += ibi
            TimedIbi(t, ibi, true, index, false)
        }
        assertEquals(PaceFallbackReason.NO_CLEAR_PEAK, PaceCalibrator.estimate(input).fallbackReason)
    }

    @Test fun oldValidOscillationCannotRescueRecentFlatData() {
        val old = oscillation()
        var t = old.last().endMillis!!
        val recent = (0..45).map { index ->
            t += 800
            TimedIbi(t, 800, true, index, false)
        }
        val estimate = PaceCalibrator.estimate(old + recent)
        assertTrue(estimate.usedFallback)
        assertEquals(44, estimate.acceptedIbiCount)
        assertEquals(44, estimate.analyzedIbiCount)
    }

    @Test fun enoughIntervalsButTooLittleTimeStillFallsBack() {
        val estimate = PaceCalibrator.estimate(oscillation().take(15))
        assertEquals(PaceFallbackReason.SHORT_CONTINUOUS_SEGMENT, estimate.fallbackReason)
    }
}
