package pl.pulsebreath.wear.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.pulsebreath.wear.signal.PaceEstimate
import pl.pulsebreath.wear.signal.PaceEstimateConfidence
import pl.pulsebreath.wear.signal.PaceEstimateMode
import pl.pulsebreath.wear.signal.TimedIbi

class DynamicPaceTunerTest {
    private val interval = listOf(TimedIbi(1_000L, 1_000L, true, 0, false))
    private fun estimate(cycle: Long, confidence: PaceEstimateConfidence = PaceEstimateConfidence.HIGH) = PaceEstimate(cycle, cycle * 45 / 100, cycle * 55 / 100,
        false, 16, 0.8, null, 16, PaceEstimateMode.CONTINUOUS, confidence)

    @Test fun appliesOnlyAfterTwoHighConfidenceWindowsAgree() {
        val tuner = DynamicPaceTuner { estimate(9_000L) }
        assertNull(tuner.observe(interval, 0L, 8_750L, allowWeakConfidence = true))
        assertEquals(9_000L, tuner.observe(interval, 60_000L, 8_750L, allowWeakConfidence = true)?.cycleMillis)
    }

    @Test fun rejectsLargeOrWeakChanges() {
        val tuner = DynamicPaceTuner { estimate(10_000L) }
        assertNull(tuner.observe(interval, 0L, 8_750L, allowWeakConfidence = true))
        assertNull(tuner.observe(interval, 60_000L, 8_750L, allowWeakConfidence = true))
    }

    @Test fun weakConfidenceIsOptional() {
        val tuner = DynamicPaceTuner { estimate(9_000L, PaceEstimateConfidence.WEAK) }
        assertNull(tuner.observe(interval, 0L, 8_750L, allowWeakConfidence = false))
        assertNull(tuner.observe(interval, 60_000L, 8_750L, allowWeakConfidence = true))
        assertEquals(9_000L, tuner.observe(interval, 120_000L, 8_750L, allowWeakConfidence = true)?.cycleMillis)
    }
}
