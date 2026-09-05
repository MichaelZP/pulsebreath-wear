package pl.pulsebreath.wear.history

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHistoryTest {
    private fun store(file: File) = SessionHistoryStore(file) { target, text -> target.writeText(text) }
    private fun record(id: String, outcome: SessionOutcome? = SessionOutcome.STOPPED) = SessionHistoryRecord(
        sessionId = id, startedAtMillis = 10L, endedAtMillis = 20L,
        plannedDurationMillis = 120_000L, activeDurationMillis = 5_000L, outcome = outcome,
        cycleMillis = 10_000L, estimateMode = "CONTINUOUS", confidence = "HIGH", usedFallback = false,
    )

    @Test fun saveAndLoadRoundTrip() {
        val file = File.createTempFile("history", ".json")
        try {
            store(file).save(listOf(record("one")))
            assertEquals(record("one"), store(file).load().single())
        } finally { file.delete() }
    }

    @Test fun duplicateIdsKeepNewestInputAndLimitToTwoHundred() {
        val file = File.createTempFile("history", ".json")
        try {
            val records = listOf(record("same"), record("same", SessionOutcome.COMPLETED)) + (0..200).map { record(it.toString()) }
            store(file).save(records)
            val loaded = store(file).load()
            assertEquals(200, loaded.size)
            assertEquals(1, loaded.count { it.sessionId == "same" })
            assertEquals(SessionOutcome.STOPPED, loaded.first { it.sessionId == "same" }.outcome)
        } finally { file.delete() }
    }

    @Test fun activeRecordIsRecoveredAsInterrupted() {
        val file = File.createTempFile("history", ".json")
        try {
            store(file).start(record("active", null))
            val recovered = store(file).load().single()
            assertEquals(SessionOutcome.INTERRUPTED, recovered.outcome)
            assertEquals(10L, recovered.endedAtMillis)
        } finally { file.delete() }
    }

    @Test fun corruptFileIsIgnoredWithoutThrowing() {
        val file = File.createTempFile("history", ".json")
        try {
            file.writeText("not-json")
            assertTrue(store(file).load().isEmpty())
        } finally { file.delete() }
    }

    @Test fun answeredStressPairsShowDeltaAndSkippedSidesStayNeutral() {
        assertEquals("Przed: 1 · Po: 3 · Δ 2", StressCheckInPairing.summary(StressCheckIn.answered(1), StressCheckIn.answered(3)))
        assertEquals("Przed: 1 · Po: —", StressCheckInPairing.summary(StressCheckIn.answered(1), StressCheckIn.skipped))
        assertEquals("Przed: — · Po: 3", StressCheckInPairing.summary(StressCheckIn.skipped, StressCheckIn.answered(3)))
        assertEquals("Przed: — · Po: —", StressCheckInPairing.summary(StressCheckIn.skipped, StressCheckIn.skipped))
    }

    @Test fun stressFieldsRoundTripAndSkipIsExplicit() {
        val file = File.createTempFile("history", ".json")
        try {
            val original = record("stress").copy(stressPre = 2, stressPreAnswered = true, stressPostAnswered = false)
            store(file).save(listOf(original))
            assertEquals(original, store(file).load().single())
        } finally { file.delete() }
    }
}
