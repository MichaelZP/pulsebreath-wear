package pl.pulsebreath.wear.sensor

import pl.pulsebreath.wear.session.BreathingPhase

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
    // Index into retained IBI; size denotes a break after the final interval.
    val ibiBreakBeforeIndices: Set<Int> = emptySet(),
    val rejectedIbiCount: Int = 0,
    val timing: SensorTiming? = null,
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

internal enum class SensorStreamState {
    IDLE,
    CONNECTING,
    TRACKING,
    UNSUPPORTED,
    ERROR,
}

internal data class SensorStreamStatus(
    val state: SensorStreamState,
    val message: String,
)

internal interface StreamingSensorDataSource {
    fun start(
        onStatus: (SensorStreamStatus) -> Unit,
        onSample: (SensorSample) -> Unit,
    )

    fun stop()
}
