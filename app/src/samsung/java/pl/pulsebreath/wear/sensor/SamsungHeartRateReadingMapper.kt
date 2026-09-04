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
        val validIbi = mutableListOf<Long>()
        val breaks = mutableSetOf<Int>()
        var rejectedCount = 0
        // Include unmatched entries; zip would silently truncate them.
        repeat(maxOf(ibiValuesMillis.size, ibiStatuses.size)) { index ->
            val ibi = ibiValuesMillis.getOrNull(index)
            val status = ibiStatuses.getOrNull(index)
            if (ibi != null && ibi > 0 && status == IBI_STATUS_NORMAL) {
                validIbi.add(ibi.toLong())
            } else {
                breaks.add(validIbi.size)
                rejectedCount += 1
            }
        }

        return SensorSample(
            monotonicTimestampMillis = monotonicTimestampMillis,
            beatsPerMinute = heartRate.takeIf {
                heartRateStatus == STATUS_SUCCESS && it > 0
            }?.toDouble(),
            ibiMillis = validIbi,
            quality = qualityFor(heartRateStatus),
            sourceType = SensorSourceType.SAMSUNG,
            ibiBreakBeforeIndices = breaks,
            rejectedIbiCount = rejectedCount,
        )
    }

    private fun qualityFor(heartRateStatus: Int): SensorSignalQuality =
        when (heartRateStatus) {
            STATUS_SUCCESS -> SensorSignalQuality.GOOD
            -2, -8, -10 -> SensorSignalQuality.MOTION_ARTIFACT
            else -> SensorSignalQuality.SIGNAL_LOST
        }
}
