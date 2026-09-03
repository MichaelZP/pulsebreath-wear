package com.example.pulsebreathwear.sensor

import com.example.pulsebreathwear.session.BreathingPhase
import kotlin.math.round

internal enum class FakeSensorScenario {
    CALM,
    RESPIRATORY_SINUS_ARRHYTHMIA,
    MOTION_ARTIFACT,
    SIGNAL_LOSS,
    RECOVERY,
}

internal data class FakeSensorFrame(
    val scenario: FakeSensorScenario,
    val sample: SensorSample,
)

internal class FakeSensorDataSource : SensorDataSource {
    override fun sample(request: SensorSampleRequest): SensorSample = frameAt(request).sample

    fun frameAt(request: SensorSampleRequest): FakeSensorFrame {
        val scenario = scenarioAt(request.sessionElapsedMillis)
        val sample =
            when (scenario) {
                FakeSensorScenario.CALM -> goodSample(request, beatsPerMinute = 60.0)
                FakeSensorScenario.RESPIRATORY_SINUS_ARRHYTHMIA -> rsaSample(request)
                FakeSensorScenario.MOTION_ARTIFACT -> motionArtifactSample(request)
                FakeSensorScenario.SIGNAL_LOSS -> signalLossSample(request)
                FakeSensorScenario.RECOVERY -> recoverySample(request)
            }

        return FakeSensorFrame(
            scenario = scenario,
            sample = sample,
        )
    }

    fun scenarioAt(sessionElapsedMillis: Long): FakeSensorScenario {
        val positionMillis = sessionElapsedMillis.coerceAtLeast(0L) % SCRIPT_DURATION_MILLIS
        return when {
            positionMillis < RSA_START_MILLIS -> FakeSensorScenario.CALM
            positionMillis < MOTION_START_MILLIS ->
                FakeSensorScenario.RESPIRATORY_SINUS_ARRHYTHMIA

            positionMillis < SIGNAL_LOSS_START_MILLIS -> FakeSensorScenario.MOTION_ARTIFACT
            positionMillis < RECOVERY_START_MILLIS -> FakeSensorScenario.SIGNAL_LOSS
            else -> FakeSensorScenario.RECOVERY
        }
    }

    private fun rsaSample(request: SensorSampleRequest): SensorSample {
        val phaseProgress = request.phaseProgress.coerceIn(0f, 1f).toDouble()
        val respiratoryWave =
            when (request.breathingPhase) {
                BreathingPhase.INHALE -> -1.0 + 2.0 * phaseProgress
                BreathingPhase.EXHALE -> 1.0 - 2.0 * phaseProgress
            }
        val beatsPerMinute = roundToSingleDecimal(BASE_BPM + RSA_AMPLITUDE_BPM * respiratoryWave)
        return goodSample(request, beatsPerMinute)
    }

    private fun motionArtifactSample(request: SensorSampleRequest): SensorSample {
        val beatsPerMinute =
            when ((request.monotonicTimestampMillis / 1_000L) % 3L) {
                0L -> 88.0
                1L -> 42.0
                else -> 96.0
            }
        return SensorSample(
            monotonicTimestampMillis = request.monotonicTimestampMillis,
            beatsPerMinute = beatsPerMinute,
            ibiMillis = listOf(420L, 1_450L),
            quality = SensorSignalQuality.MOTION_ARTIFACT,
            sourceType = SensorSourceType.SIMULATED,
        )
    }

    private fun signalLossSample(request: SensorSampleRequest): SensorSample =
        SensorSample(
            monotonicTimestampMillis = request.monotonicTimestampMillis,
            beatsPerMinute = null,
            ibiMillis = emptyList(),
            quality = SensorSignalQuality.SIGNAL_LOST,
            sourceType = SensorSourceType.SIMULATED,
        )

    private fun recoverySample(request: SensorSampleRequest): SensorSample {
        val positionMillis = request.sessionElapsedMillis.coerceAtLeast(0L) % SCRIPT_DURATION_MILLIS
        val recoveryProgress =
            ((positionMillis - RECOVERY_START_MILLIS).toDouble() / RECOVERY_DURATION_MILLIS)
                .coerceIn(0.0, 1.0)
        val beatsPerMinute =
            roundToSingleDecimal(
                RECOVERY_START_BPM -
                    (RECOVERY_START_BPM - BASE_BPM) * recoveryProgress,
            )
        val ibiMillis = listOf(ibiFromBpm(beatsPerMinute))
        return SensorSample(
            monotonicTimestampMillis = request.monotonicTimestampMillis,
            beatsPerMinute = beatsPerMinute,
            ibiMillis = ibiMillis,
            quality = SensorSignalQuality.RECOVERING,
            sourceType = SensorSourceType.SIMULATED,
        )
    }

    private fun goodSample(
        request: SensorSampleRequest,
        beatsPerMinute: Double,
    ): SensorSample =
        SensorSample(
            monotonicTimestampMillis = request.monotonicTimestampMillis,
            beatsPerMinute = beatsPerMinute,
            ibiMillis = listOf(ibiFromBpm(beatsPerMinute)),
            quality = SensorSignalQuality.GOOD,
            sourceType = SensorSourceType.SIMULATED,
        )

    private fun ibiFromBpm(beatsPerMinute: Double): Long = round(60_000.0 / beatsPerMinute).toLong()

    private fun roundToSingleDecimal(value: Double): Double = round(value * 10.0) / 10.0

    private companion object {
        const val BASE_BPM = 60.0
        const val RSA_AMPLITUDE_BPM = 6.0
        const val RECOVERY_START_BPM = 72.0

        const val RSA_START_MILLIS = 10_000L
        const val MOTION_START_MILLIS = 20_000L
        const val SIGNAL_LOSS_START_MILLIS = 25_000L
        const val RECOVERY_START_MILLIS = 30_000L
        const val SCRIPT_DURATION_MILLIS = 40_000L
        const val RECOVERY_DURATION_MILLIS = 10_000.0
    }
}
