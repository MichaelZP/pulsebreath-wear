package pl.pulsebreath.wear.sensor

import org.junit.Assert.*
import org.junit.Test

class TimingDiagnosticsTest {
    private fun sample(seq: Long, index: Int, count: Int, receipt: Long, sdk: Long, empty: Boolean = false): SensorSample {
        val n = if (empty) 0 else 1
        return SensorSample(receipt, null, if (empty) emptyList() else listOf(800L),
            SensorSignalQuality.GOOD, SensorSourceType.SAMSUNG,
            timing = SensorTiming(receipt, sdk, seq, index, count, n, n))
    }

    @Test fun batchCountsAndClockDeltasRemainSeparate() {
        val s = TimingDiagnostics()
            .add(sample(0, 0, 2, 100, 1000))
            .add(sample(0, 1, 2, 100, 2000, true))
            .add(sample(1, 0, 1, 150, 3000))
        assertEquals(3L, s.points)
        assertEquals(2L, s.callbackGroups)
        assertEquals(1L, s.multiPointGroups)
        assertEquals(1L, s.emptyCompanions)
        assertEquals(1000L, s.minSdkDelta)
        assertEquals(50L, s.minReceiptDelta)
        assertEquals(0L, s.orderingErrors)
    }

    @Test fun ageAdvancesWithoutNewEventsAndHandlesBackwardClock() {
        val s = TimingDiagnostics().add(sample(0, 0, 1, 100, 1000))
        assertEquals(100L, s.receiptAge(200))
        assertEquals(300L, s.receiptAge(400))
        assertNull(s.receiptAge(99))
    }

    @Test fun duplicatesAreFlaggedAndMissingMetadataClearsComparison() {
        val p = sample(0, 0, 1, 100, 1000)
        val s = TimingDiagnostics().add(p).add(p).add(p.copy(timing = null))
        assertEquals(1L, s.orderingErrors)
        assertEquals(1L, s.missingMetadata)
        assertNull(s.previous)
        assertEquals(0L, TimingDiagnostics().points)
    }
}
