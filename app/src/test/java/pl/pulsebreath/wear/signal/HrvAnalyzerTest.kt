package pl.pulsebreath.wear.signal

import pl.pulsebreath.wear.sensor.SensorSample
import pl.pulsebreath.wear.sensor.SensorSignalQuality
import pl.pulsebreath.wear.sensor.SensorSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HrvAnalyzerTest {
    @Test
    fun metricsMatchIndependentQualityV1Fixture() {
        loadFixture().forEach { row ->
            val metrics = HrvAnalyzer.analyze(row.samples)

            assertEquals(row.expectedMeanBpm, metrics.meanBpm!!, 0.0000001)
            assertEquals(row.expectedValidIbiCount, metrics.validIbiCount)
            assertEquals(row.expectedInvalidIbiCount, metrics.invalidIbiCount)
            assertEquals(row.expectedCoveragePercent, metrics.ibiEventCoveragePercent, 0.0000001)
            assertEquals(row.expectedRmssdMillis, metrics.rmssdMillis!!, 0.0000001)
            assertEquals(row.expectedQuality, metrics.quality)
        }
    }

    @Test
    fun emptyWindowIsExplicitlyInsufficient() {
        val metrics = HrvAnalyzer.analyze(emptyList())

        assertEquals(0, metrics.sampleEventCount)
        assertEquals(0.0, metrics.ibiEventCoveragePercent, 0.0)
        assertNull(metrics.meanBpm)
        assertNull(metrics.rmssdMillis)
        assertEquals(HrvWindowQuality.INSUFFICIENT, metrics.quality)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveWindowIsRejectedInsteadOfSilentlyChanged() {
        HrvAnalyzer.analyze(samples = emptyList(), windowMillis = 0L)
    }

    @Test
    fun goodEventWithoutIbiDoesNotInventOrBreakTheNextValidDifference() {
        val metrics =
            HrvAnalyzer.analyze(
                listOf(
                    sample(timestamp = 0L, ibiMillis = listOf(1_000L)),
                    sample(timestamp = 1_000L, ibiMillis = emptyList()),
                    sample(timestamp = 2_000L, ibiMillis = listOf(900L)),
                ),
            )

        assertEquals(2, metrics.validIbiCount)
        assertEquals(200.0 / 3.0, metrics.ibiEventCoveragePercent, 0.0000001)
        assertEquals(100.0, metrics.rmssdMillis!!, 0.0)
    }

    @Test
    fun invalidIbiInNonGoodEventIsCountedButNeverUsed() {
        val metrics =
            HrvAnalyzer.analyze(
                listOf(
                    sample(timestamp = 0L, ibiMillis = listOf(1_000L)),
                    sample(
                        timestamp = 1_000L,
                        ibiMillis = listOf(-1L),
                        quality = SensorSignalQuality.MOTION_ARTIFACT,
                    ),
                    sample(timestamp = 2_000L, ibiMillis = listOf(900L)),
                ),
            )

        assertEquals(1, metrics.invalidIbiCount)
        assertNull(metrics.rmssdMillis)
    }

    private fun loadFixture(): List<ExpectedRow> {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/hrv_quality_v1.csv"))
        return stream.bufferedReader().useLines { lines ->
            lines
                .drop(1)
                .filter(String::isNotBlank)
                .map(::parseRow)
                .toList()
        }
    }

    private fun parseRow(line: String): ExpectedRow {
        val fields = line.split(',', limit = 8)
        return ExpectedRow(
            samples =
                fields[1]
                    .split(';')
                    .map(::parseSample),
            expectedMeanBpm = fields[2].toDouble(),
            expectedValidIbiCount = fields[3].toInt(),
            expectedInvalidIbiCount = fields[4].toInt(),
            expectedCoveragePercent = fields[5].toDouble(),
            expectedRmssdMillis = fields[6].toDouble(),
            expectedQuality = HrvWindowQuality.valueOf(fields[7]),
        )
    }

    private fun parseSample(value: String): SensorSample {
        val fields = value.split('|')
        return SensorSample(
            monotonicTimestampMillis = fields[0].toLong(),
            beatsPerMinute = fields[1].takeIf(String::isNotEmpty)?.toDouble(),
            quality = SensorSignalQuality.valueOf(fields[2]),
            ibiMillis = fields.drop(3).filter(String::isNotEmpty).map(String::toLong),
            sourceType = SensorSourceType.SIMULATED,
        )
    }

    private fun sample(
        timestamp: Long,
        ibiMillis: List<Long>,
        quality: SensorSignalQuality = SensorSignalQuality.GOOD,
    ) =
        SensorSample(
            monotonicTimestampMillis = timestamp,
            beatsPerMinute = 60.0,
            ibiMillis = ibiMillis,
            quality = quality,
            sourceType = SensorSourceType.SIMULATED,
        )

    private data class ExpectedRow(
        val samples: List<SensorSample>,
        val expectedMeanBpm: Double,
        val expectedValidIbiCount: Int,
        val expectedInvalidIbiCount: Int,
        val expectedCoveragePercent: Double,
        val expectedRmssdMillis: Double,
        val expectedQuality: HrvWindowQuality,
    )
}
