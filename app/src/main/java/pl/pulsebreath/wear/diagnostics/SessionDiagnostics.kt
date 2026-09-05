package pl.pulsebreath.wear.diagnostics

import java.io.File

/** Small local ring log for lifecycle/session diagnosis. Never receives raw sensor data. */
internal class SessionDiagnostics(private val file: File) {
    companion object { const val MAX_EVENTS = 100 }

    @Synchronized
    fun record(event: String) {
        val line = "${System.currentTimeMillis()} $event"
        val lines = readLines().takeLast(MAX_EVENTS - 1) + line
        runCatching { file.writeText(lines.joinToString("\n") + "\n") }
    }

    @Synchronized
    fun read(): List<String> = readLines()

    private fun readLines(): List<String> = runCatching { if (file.exists()) file.readLines() else emptyList() }.getOrDefault(emptyList())
}
