package pl.pulsebreath.wear.diagnostics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDiagnosticsTest {
    @Test fun keepsOnlyTheLastHundredEvents() {
        val file = File.createTempFile("diagnostics", ".log")
        try {
            val diagnostics = SessionDiagnostics(file)
            repeat(105) { diagnostics.record("event=$it") }
            val events = diagnostics.read()
            assertEquals(100, events.size)
            assertTrue(events.first().endsWith("event=5"))
            assertTrue(events.last().endsWith("event=104"))
        } finally { file.delete() }
    }
}
