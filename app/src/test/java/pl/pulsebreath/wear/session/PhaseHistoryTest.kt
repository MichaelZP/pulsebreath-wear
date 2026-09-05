package pl.pulsebreath.wear.session

import org.junit.Assert.*
import org.junit.Test

class PhaseHistoryTest {
    @Test fun lookupRespectsBoundariesAndGaps() {
        val h = PhaseHistory()
        h.add(PhaseHistoryEntry(100, BreathingPhase.INHALE, 0.9f))
        h.add(PhaseHistoryEntry(200, BreathingPhase.EXHALE, 0f))
        assertEquals(BreathingPhase.INHALE, h.phaseAt(110, 10)?.phase)
        assertNull(h.phaseAt(150, 10))
        assertEquals(BreathingPhase.EXHALE, h.phaseAt(200, 0)?.phase)
        assertNull(h.phaseAt(201, 10))
        assertNull(h.phaseAt(99, 10))
        h.clear()
        assertNull(h.phaseAt(200, 10))
    }
    @Test fun historyIsBoundedByTimeAndCapacity() {
        val h = PhaseHistory(100, 2)
        for (t in listOf(0L, 50L, 100L, 250L)) h.add(PhaseHistoryEntry(t, BreathingPhase.INHALE, 0f))
        assertNull(h.phaseAt(100, 100))
        assertNotNull(h.phaseAt(250, 0))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateSnapshotTimes() {
        val h = PhaseHistory()
        val e = PhaseHistoryEntry(1, BreathingPhase.INHALE, 0f)
        h.add(e); h.add(e)
    }
}
