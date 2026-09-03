package com.example.pulsebreathwear.sensor

import com.example.pulsebreathwear.session.BreathingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSensorDataSourceTest {
    private val dataSource = FakeSensorDataSource()

    @Test
    fun generatedFramesMatchIndependentFixture() {
        loadFixture().forEach { row ->
            val frame =
                dataSource.frameAt(
                    SensorSampleRequest(
                        monotonicTimestampMillis = row.timestampMillis,
                        sessionElapsedMillis = row.sessionElapsedMillis,
                        breathingPhase = row.phase,
                        phaseProgress = row.phaseProgress,
                    ),
                )

            assertEquals(row.scenario, frame.scenario)
            assertEquals(row.timestampMillis, frame.sample.monotonicTimestampMillis)
            assertEquals(row.expectedQuality, frame.sample.quality)
            assertEquals(SensorSourceType.SIMULATED, frame.sample.sourceType)
            assertEquals(row.expectedIbiMillis, frame.sample.ibiMillis)
            if (row.expectedBpm == null) {
                assertNull(frame.sample.beatsPerMinute)
            } else {
                assertEquals(row.expectedBpm, frame.sample.beatsPerMinute!!, 0.0001)
            }
        }
    }

    @Test
    fun scenarioBoundariesAndLoopAreStable() {
        assertEquals(FakeSensorScenario.CALM, dataSource.scenarioAt(9_999L))
        assertEquals(
            FakeSensorScenario.RESPIRATORY_SINUS_ARRHYTHMIA,
            dataSource.scenarioAt(10_000L),
        )
        assertEquals(FakeSensorScenario.MOTION_ARTIFACT, dataSource.scenarioAt(20_000L))
        assertEquals(FakeSensorScenario.SIGNAL_LOSS, dataSource.scenarioAt(25_000L))
        assertEquals(FakeSensorScenario.RECOVERY, dataSource.scenarioAt(30_000L))
        assertEquals(FakeSensorScenario.CALM, dataSource.scenarioAt(40_000L))
    }

    @Test
    fun equalRequestsProduceEqualFrames() {
        val request =
            SensorSampleRequest(
                monotonicTimestampMillis = 22_000L,
                sessionElapsedMillis = 22_000L,
                breathingPhase = BreathingPhase.EXHALE,
                phaseProgress = 0.25f,
            )

        assertEquals(dataSource.frameAt(request), dataSource.frameAt(request))
    }

    @Test
    fun signalLossContainsNoInventedPhysiologicalValues() {
        val frame =
            dataSource.frameAt(
                SensorSampleRequest(
                    monotonicTimestampMillis = 26_000L,
                    sessionElapsedMillis = 26_000L,
                    breathingPhase = BreathingPhase.EXHALE,
                    phaseProgress = 0.2f,
                ),
            )

        assertEquals(FakeSensorScenario.SIGNAL_LOSS, frame.scenario)
        assertNull(frame.sample.beatsPerMinute)
        assertTrue(frame.sample.ibiMillis.isEmpty())
        assertEquals(SensorSignalQuality.SIGNAL_LOST, frame.sample.quality)
    }

    private fun loadFixture(): List<ExpectedRow> {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/fake_sensor_expected.csv"))
        return stream
            .bufferedReader()
            .useLines { lines ->
                lines
                    .drop(1)
                    .filter { it.isNotBlank() }
                    .map(::parseRow)
                    .toList()
            }
    }

    private fun parseRow(line: String): ExpectedRow {
        val fields = line.split(',', limit = 8)
        return ExpectedRow(
            scenario = FakeSensorScenario.valueOf(fields[0]),
            timestampMillis = fields[1].toLong(),
            sessionElapsedMillis = fields[2].toLong(),
            phase = BreathingPhase.valueOf(fields[3]),
            phaseProgress = fields[4].toFloat(),
            expectedBpm = fields[5].takeIf(String::isNotEmpty)?.toDouble(),
            expectedIbiMillis =
                fields[6]
                    .takeIf(String::isNotEmpty)
                    ?.split('|')
                    ?.map(String::toLong)
                    .orEmpty(),
            expectedQuality = SensorSignalQuality.valueOf(fields[7]),
        )
    }

    private data class ExpectedRow(
        val scenario: FakeSensorScenario,
        val timestampMillis: Long,
        val sessionElapsedMillis: Long,
        val phase: BreathingPhase,
        val phaseProgress: Float,
        val expectedBpm: Double?,
        val expectedIbiMillis: List<Long>,
        val expectedQuality: SensorSignalQuality,
    )
}
