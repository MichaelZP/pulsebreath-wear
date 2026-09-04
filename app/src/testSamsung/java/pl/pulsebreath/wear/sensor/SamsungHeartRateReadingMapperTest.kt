package pl.pulsebreath.wear.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.pulsebreath.wear.signal.HrvAnalyzer

class SamsungHeartRateReadingMapperTest {
    @Test
    fun rejectedIntervalsBreakRmssdAtEveryBatchPosition() {
        val cases = listOf(
            listOf(listOf(1000), listOf(999, 900)),
            listOf(listOf(1000, 999, 900)),
            listOf(listOf(1000, 999), listOf(900)),
            listOf(listOf(1000), listOf(999), listOf(900)),
        )
        cases.forEach { batches ->
            val samples = batches.mapIndexed { index, values ->
                SamsungHeartRateReadingMapper.map(
                    index * 1000L, 60, 1, values,
                    values.map { if (it == 999) -1 else 0 },
                )
            }
            val metrics = HrvAnalyzer.analyze(samples)
            assertEquals(2, metrics.validIbiCount)
            assertEquals(1, metrics.rejectedIbiCount)
            assertNull(metrics.rmssdMillis)
        }
    }

    @Test
    fun validDifferencesWithinSegmentsArePreserved() {
        val sample = SamsungHeartRateReadingMapper.map(
            0L, 60, 1, listOf(1000, 900, 999, 1400, 1300), listOf(0, 0, -1, 0, 0),
        )
        assertEquals(100.0, HrvAnalyzer.analyze(listOf(sample)).rmssdMillis!!, 0.0)
        assertEquals(setOf(2), sample.ibiBreakBeforeIndices)
    }

    @Test
    fun unmatchedEntriesAreCountedAndBreakContinuity() {
        listOf(
            listOf(1000, 900) to listOf(0),
            listOf(1000) to listOf(0, -1),
        ).forEach { (values, statuses) ->
            val sample = SamsungHeartRateReadingMapper.map(0L, 60, 1, values, statuses)
            assertEquals(listOf(1000L), sample.ibiMillis)
            assertEquals(setOf(1), sample.ibiBreakBeforeIndices)
            assertEquals(1, sample.rejectedIbiCount)
        }
    }

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
