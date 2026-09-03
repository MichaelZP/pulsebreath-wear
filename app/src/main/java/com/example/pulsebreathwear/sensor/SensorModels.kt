package com.example.pulsebreathwear.sensor

import com.example.pulsebreathwear.session.BreathingPhase

internal enum class SensorSourceType {
    SIMULATED,
    SAMSUNG,
}

internal enum class SensorSignalQuality {
    GOOD,
    MOTION_ARTIFACT,
    SIGNAL_LOST,
    RECOVERING,
}

internal data class SensorSample(
    val monotonicTimestampMillis: Long,
    val beatsPerMinute: Double?,
    val ibiMillis: List<Long>,
    val quality: SensorSignalQuality,
    val sourceType: SensorSourceType,
)

internal data class SensorSampleRequest(
    val monotonicTimestampMillis: Long,
    val sessionElapsedMillis: Long,
    val breathingPhase: BreathingPhase,
    val phaseProgress: Float,
)

internal fun interface SensorDataSource {
    fun sample(request: SensorSampleRequest): SensorSample
}
