package pl.pulsebreath.wear.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SamsungHeartRateReadingMapperTest {
    @Test
    fun successfulReadingKeepsOnlyIbiValuesWithNormalStatus() {
        val sample = SamsungHeartRateReadingMapper.map(
            monotonicTimestampMillis = 12_345L,
            heartRate = 64,
            heartRateStatus = 1,
            ibiValuesMillis = listOf(930, 940, 0),
            ibiStatuses = listOf(0, -1, 0),
        )

        assertEquals(12_345L, sample.monotonicTimestampMillis)
        assertEquals(64.0, sample.beatsPerMinute!!, 0.0)
        assertEquals(listOf(930L), sample.ibiMillis)
        assertEquals(SensorSignalQuality.GOOD, sample.quality)
        assertEquals(SensorSourceType.SAMSUNG, sample.sourceType)
    }

    @Test
    fun motionStatusRejectsHeartRateAndMarksArtifact() {
        val sample = SamsungHeartRateReadingMapper.map(
            monotonicTimestampMillis = 1L,
            heartRate = 81,
            heartRateStatus = -2,
            ibiValuesMillis = emptyList(),
            ibiStatuses = emptyList(),
        )

        assertNull(sample.beatsPerMinute)
        assertEquals(SensorSignalQuality.MOTION_ARTIFACT, sample.quality)
    }

    @Test
    fun missingIbiStatusDoesNotAssumeThatIbiIsValid() {
        val sample = SamsungHeartRateReadingMapper.map(
            monotonicTimestampMillis = 1L,
            heartRate = 70,
            heartRateStatus = 1,
            ibiValuesMillis = listOf(850),
            ibiStatuses = emptyList(),
        )

        assertEquals(emptyList<Long>(), sample.ibiMillis)
    }
}
