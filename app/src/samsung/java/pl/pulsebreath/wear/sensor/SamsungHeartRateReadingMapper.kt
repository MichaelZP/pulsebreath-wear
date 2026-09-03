package pl.pulsebreath.wear.sensor

internal object SamsungHeartRateReadingMapper {
    private const val STATUS_SUCCESS = 1
    private const val IBI_STATUS_NORMAL = 0

    fun map(
        monotonicTimestampMillis: Long,
        heartRate: Int,
        heartRateStatus: Int,
        ibiValuesMillis: List<Int>,
        ibiStatuses: List<Int>,
    ): SensorSample {
        val validIbi = ibiValuesMillis.zip(ibiStatuses)
            .filter { (ibi, status) -> status == IBI_STATUS_NORMAL && ibi > 0 }
            .map { (ibi, _) -> ibi.toLong() }

        return SensorSample(
            monotonicTimestampMillis = monotonicTimestampMillis,
            beatsPerMinute = heartRate.takeIf {
                heartRateStatus == STATUS_SUCCESS && it > 0
            }?.toDouble(),
            ibiMillis = validIbi,
            quality = qualityFor(heartRateStatus),
            sourceType = SensorSourceType.SAMSUNG,
        )
    }

    private fun qualityFor(heartRateStatus: Int): SensorSignalQuality =
        when (heartRateStatus) {
            STATUS_SUCCESS -> SensorSignalQuality.GOOD
            -2, -8, -10 -> SensorSignalQuality.MOTION_ARTIFACT
            else -> SensorSignalQuality.SIGNAL_LOST
        }
}
