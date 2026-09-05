package pl.pulsebreath.wear.signal

import org.junit.Assert.*
import org.junit.Test
import pl.pulsebreath.wear.sensor.*

class TimedIbiTest {
    private fun sample(
        values: List<Long>,
        breaks: Set<Int> = emptySet(),
        rejects: Int = 0,
        quality: SensorSignalQuality = SensorSignalQuality.GOOD,
    ) = SensorSample(10_000, null, values, quality, SensorSourceType.SAMSUNG,
            ibiBreakBeforeIndices = breaks, rejectedIbiCount = rejects)

    @Test fun chronologicalIntervalsAreUnwrappedBackwardFromReceipt() {
        val batch = expandBatch(10_000, sample(listOf(800, 900, 850)))
        assertEquals(listOf(8250L, 9150L, 10000L), batch.intervals.map { it.endMillis })
        assertEquals(listOf(800L, 900L, 850L), batch.intervals.map { it.ibiMillis })
    }
    @Test fun rejectedMiddlePreservesEarlierValueWithoutInventingItsTime() {
        val batch = expandBatch(10_000, sample(listOf(800, 850), setOf(1), 1))
        assertNull(batch.intervals[0].endMillis)
        assertTrue(batch.intervals[0].accepted)
        assertEquals(10000L, batch.intervals[1].endMillis)
        assertTrue(batch.intervals[1].breakBefore)
        assertEquals(1, batch.rejectedEntryCount)
    }
    @Test fun trailingOrUnlocatedRejectionMakesAnchorUnavailable() {
        assertEquals(2, expandBatch(10_000, sample(listOf(800, 850), setOf(2), 1)).unplacedCount)
        assertEquals(2, expandBatch(10_000, sample(listOf(800, 850), rejects = 1)).unplacedCount)
    }
    @Test fun emptyInvalidAndUnderflowInputsDoNotFabricateIntervals() {
        assertTrue(expandBatch(10_000, sample(emptyList())).intervals.isEmpty())
        val invalid = expandBatch(10_000, sample(listOf(800, 0, 850)))
        assertFalse(invalid.intervals[1].accepted)
        assertNull(invalid.intervals[0].endMillis)
        assertNull(expandBatch(100, sample(listOf(800, 850))).intervals[0].endMillis)
    }

    @Test fun positiveMappedIntervalsRemainPaceAcceptedWhenBpmQualityFlickers() {
        for (quality in listOf(SensorSignalQuality.MOTION_ARTIFACT, SensorSignalQuality.SIGNAL_LOST)) {
            val batch = expandBatch(10_000, sample(listOf(800, 850), quality = quality))
            assertTrue(batch.intervals.all { it.accepted })
            assertEquals(listOf(9150L, 10_000L), batch.intervals.map { it.endMillis })
        }
    }
}
