package pl.pulsebreath.wear.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSeriesTest {
    @Test fun selectedCountAcceptsOnlySupportedValuesAndRestoresPersistedChoice() {
        val restored = SessionSeries(SessionSeries.normalizeSelectedCount(7))

        assertEquals(7, restored.selectedCount)
        assertTrue(restored.select(11))
        assertEquals(11, restored.selectedCount)
        assertFalse(restored.select(2))
        assertEquals(11, restored.selectedCount)
        assertEquals(SessionSeries.SINGLE_SESSION, SessionSeries.normalizeSelectedCount(99))
    }

    @Test fun completedSessionsRequireFreshCalibrationUntilSeriesIsFinished() {
        val series = SessionSeries(3)
        series.begin()

        assertEquals(1, series.currentSessionNumber)
        assertTrue(series.completeCurrent())
        assertEquals(2, series.currentSessionNumber)
        assertTrue(series.completeCurrent())
        assertEquals(3, series.currentSessionNumber)
        assertFalse(series.completeCurrent())
        assertFalse(series.active)
        assertEquals(3, series.completedCount)
    }

    @Test fun nextSessionStartsAutomaticallyOnlyAfterItsFreshReadyGate() {
        val series = SessionSeries(3)
        series.begin()

        assertTrue(series.completeCurrent())
        assertFalse(series.consumeAutomaticStart(canStart = false))
        assertTrue(series.consumeAutomaticStart(canStart = true))
        assertFalse(series.consumeAutomaticStart(canStart = true))
    }

    @Test fun optionalStressCheckInsAreLimitedToSeriesEndpoints() {
        val series = SessionSeries(3)
        assertTrue(series.showPreCheckIn)
        assertFalse(series.showPostCheckIn)

        series.begin()
        series.completeCurrent()
        assertFalse(series.showPreCheckIn)
        assertFalse(series.showPostCheckIn)

        series.completeCurrent()
        assertFalse(series.showPostCheckIn)
        series.completeCurrent()
        assertTrue(series.showPostCheckIn)
    }

    @Test fun cancellationPreventsAnyFurtherAutomaticSession() {
        val series = SessionSeries(5)
        series.begin()
        series.cancel()

        assertFalse(series.completeCurrent())
        assertFalse(series.consumeAutomaticStart(canStart = true))
        assertFalse(series.active)
        assertEquals(0, series.completedCount)
    }

    @Test fun resetMakesTheNextSeriesFirstSessionForTheOptionalPreCheckIn() {
        val series = SessionSeries(3)
        series.begin()
        series.completeCurrent()
        assertFalse(series.isFirstSession)

        series.reset()

        assertTrue(series.isFirstSession)
        assertEquals(1, series.currentSessionNumber)
    }
}
