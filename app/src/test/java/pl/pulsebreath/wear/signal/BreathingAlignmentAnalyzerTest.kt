package pl.pulsebreath.wear.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.pulsebreath.wear.sensor.SensorSample
import pl.pulsebreath.wear.sensor.SensorSignalQuality
import pl.pulsebreath.wear.sensor.SensorSourceType
import pl.pulsebreath.wear.session.BreathingPhase

class BreathingAlignmentAnalyzerTest {
    @Test
    fun idealTemplateMatchedIbiHasPositiveOneScore() {
        val metrics = BreathingAlignmentAnalyzer.analyze(alternatingObservations())

        assertEquals(AlignmentAvailability.AVAILABLE, metrics.availability)
        assertEquals(1.0, metrics.score!!, 0.0)
        assertEquals(10, metrics.validIbiCount)
    }

    @Test
    fun phaseShiftedIbiHasNegativeOneScore() {
        val metrics = BreathingAlignmentAnalyzer.analyze(alternatingObservations(invertIbi = true))

        assertEquals(AlignmentAvailability.AVAILABLE, metrics.availability)
        assertEquals(-1.0, metrics.score!!, 0.0)
    }

    @Test
    fun noisyTemplateMatchedIbiKeepsAPositiveNonPerfectScore() {
        val metrics = BreathingAlignmentAnalyzer.analyze(noisyObservations())

        assertEquals(AlignmentAvailability.AVAILABLE, metrics.availability)
        requireNotNull(metrics.score).also { score ->
            assert(score > 0.8)
            assert(score < 1.0)
        }
    }

    @Test
    fun fewerThanTenIbiIsExplicitlyUnavailable() {
        val metrics = BreathingAlignmentAnalyzer.analyze(alternatingObservations().take(9))

        assertEquals(AlignmentAvailability.INSUFFICIENT_QUALITY, metrics.availability)
        assertNull(metrics.score)
        assertEquals(9, metrics.validIbiCount)
    }

    @Test
    fun nonRespiratoryConstantIbiIsExplicitlyUnavailable() {
        val metrics = BreathingAlignmentAnalyzer.analyze(alternatingObservations(ibiMillis = 1_000L))

        assertEquals(AlignmentAvailability.NO_IBI_VARIATION, metrics.availability)
        assertNull(metrics.score)
    }

    @Test
    fun invalidIbiPreventsAResultInsteadOfBeingRepaired() {
        val observations = alternatingObservations().toMutableList()
        observations += observation(timestamp = 10_000, phase = BreathingPhase.INHALE, ibiMillis = -1L)

        val metrics = BreathingAlignmentAnalyzer.analyze(observations)

        assertEquals(AlignmentAvailability.INSUFFICIENT_QUALITY, metrics.availability)
        assertNull(metrics.score)
    }

    private fun alternatingObservations(
        invertIbi: Boolean = false,
        ibiMillis: Long? = null,
    ): List<AlignmentObservation> =
        (0 until 10).map { index ->
            val inhale = index % 2 == 0
            val expectedIbi = if (inhale) 1_200L else 800L
            observation(
                timestamp = index * 1_000L,
                phase = if (inhale) BreathingPhase.INHALE else BreathingPhase.EXHALE,
                ibiMillis = ibiMillis ?: if (invertIbi) 2_000L - expectedIbi else expectedIbi,
            )
        }

    private fun noisyObservations(): List<AlignmentObservation> =
        listOf(1_200L, 820L, 1_160L, 870L, 1_220L, 760L, 1_100L, 930L, 1_180L, 800L)
            .mapIndexed { index, ibiMillis ->
                observation(
                    timestamp = index * 1_000L,
                    phase = if (index % 2 == 0) BreathingPhase.INHALE else BreathingPhase.EXHALE,
                    ibiMillis = ibiMillis,
                )
            }

    private fun observation(
        timestamp: Long,
        phase: BreathingPhase,
        ibiMillis: Long,
    ) =
        AlignmentObservation(
            sample =
                SensorSample(
            monotonicTimestampMillis = timestamp,
                    beatsPerMinute = 60.0,
                    ibiMillis = listOf(ibiMillis),
                    quality = SensorSignalQuality.GOOD,
                    sourceType = SensorSourceType.SIMULATED,
                ),
            breathingPhase = phase,
            phaseProgress = 0f,
        )
}
