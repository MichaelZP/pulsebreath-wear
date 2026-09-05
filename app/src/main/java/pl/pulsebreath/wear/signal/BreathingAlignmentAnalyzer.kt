package pl.pulsebreath.wear.signal

import pl.pulsebreath.wear.sensor.SensorSample
import pl.pulsebreath.wear.sensor.SensorSignalQuality
import pl.pulsebreath.wear.session.BreathingPhase
import kotlin.math.sqrt

internal const val ALIGNMENT_V1_WINDOW_MILLIS = QUALITY_V1_WINDOW_MILLIS

internal data class AlignmentObservation(
    val sample: SensorSample,
    val breathingPhase: BreathingPhase,
    val phaseProgress: Float,
) {
    init {
        require(phaseProgress.isFinite() && phaseProgress in 0f..1f) {
            "phaseProgress must be finite and within [0, 1]."
        }
    }
}

internal enum class AlignmentAvailability {
    AVAILABLE,
    INSUFFICIENT_QUALITY,
    INSUFFICIENT_PHASE_VARIATION,
    NO_IBI_VARIATION,
}

internal data class AlignmentMetrics(
    val analysisEndMillis: Long?,
    val validIbiCount: Int,
    val availability: AlignmentAvailability,
    val score: Double?,
)

internal object BreathingAlignmentAnalyzer {
    fun analyze(
        observations: List<AlignmentObservation>,
        windowMillis: Long = ALIGNMENT_V1_WINDOW_MILLIS,
    ): AlignmentMetrics {
        require(windowMillis > 0L) { "windowMillis must be positive." }

        val analysisEndMillis = observations.maxOfOrNull { it.sample.monotonicTimestampMillis }
            ?: return unavailable(null, 0, AlignmentAvailability.INSUFFICIENT_QUALITY)
        val hrvMetrics = HrvAnalyzer.analyze(observations.map(AlignmentObservation::sample), windowMillis)
        val windowStartMillis = analysisEndMillis - windowMillis
        val pairs =
            observations
                .filter { it.sample.monotonicTimestampMillis in windowStartMillis..analysisEndMillis }
                .flatMap { observation ->
                    observation.sample.ibiMillis
                        .filter { it > 0L && observation.sample.quality == SensorSignalQuality.GOOD }
                        .map { ibiMillis ->
                            AlignmentPair(
                                template = templateAt(observation.breathingPhase, observation.phaseProgress),
                                ibiMillis = ibiMillis.toDouble(),
                            )
                        }
                }

        if (hrvMetrics.quality != HrvWindowQuality.ADEQUATE || hrvMetrics.invalidIbiCount > 0) {
            return unavailable(analysisEndMillis, pairs.size, AlignmentAvailability.INSUFFICIENT_QUALITY)
        }

        val templateValues = pairs.map(AlignmentPair::template)
        val ibiValues = pairs.map(AlignmentPair::ibiMillis)
        if (!templateValues.hasVariation()) {
            return unavailable(
                analysisEndMillis,
                pairs.size,
                AlignmentAvailability.INSUFFICIENT_PHASE_VARIATION,
            )
        }
        if (!ibiValues.hasVariation()) {
            return unavailable(analysisEndMillis, pairs.size, AlignmentAvailability.NO_IBI_VARIATION)
        }

        return AlignmentMetrics(
            analysisEndMillis = analysisEndMillis,
            validIbiCount = pairs.size,
            availability = AlignmentAvailability.AVAILABLE,
            score = pearsonCorrelation(templateValues, ibiValues),
        )
    }

    private fun templateAt(
        phase: BreathingPhase,
        phaseProgress: Float,
    ): Double =
        when (phase) {
            BreathingPhase.INHALE -> 1.0 - 2.0 * phaseProgress
            BreathingPhase.EXHALE -> -1.0 + 2.0 * phaseProgress
        }

    private fun pearsonCorrelation(
        templateValues: List<Double>,
        ibiValues: List<Double>,
    ): Double {
        val meanTemplate = templateValues.average()
        val meanIbi = ibiValues.average()
        var covariance = 0.0
        var templateVariance = 0.0
        var ibiVariance = 0.0
        templateValues.zip(ibiValues).forEach { (template, ibiMillis) ->
            val centeredTemplate = template - meanTemplate
            val centeredIbi = ibiMillis - meanIbi
            covariance += centeredTemplate * centeredIbi
            templateVariance += centeredTemplate * centeredTemplate
            ibiVariance += centeredIbi * centeredIbi
        }
        return (covariance / sqrt(templateVariance * ibiVariance)).coerceIn(-1.0, 1.0)
    }

    private fun unavailable(
        analysisEndMillis: Long?,
        validIbiCount: Int,
        availability: AlignmentAvailability,
    ) =
        AlignmentMetrics(
            analysisEndMillis = analysisEndMillis,
            validIbiCount = validIbiCount,
            availability = availability,
            score = null,
        )
}

private data class AlignmentPair(
    val template: Double,
    val ibiMillis: Double,
)

private fun List<Double>.hasVariation(): Boolean =
    isNotEmpty() && any { value -> value != first() }
