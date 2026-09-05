package pl.pulsebreath.wear.history

import android.util.AtomicFile
import java.io.File
import java.util.Locale

internal enum class SessionOutcome { COMPLETED, STOPPED, INTERRUPTED }
internal enum class SessionMode { GUIDED, TRAINER }

internal data class SessionHistoryRecord(
    val sessionId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val plannedDurationMillis: Long,
    val activeDurationMillis: Long,
    val outcome: SessionOutcome?,
    val sessionMode: SessionMode = SessionMode.GUIDED,
    val cycleMillis: Long? = null,
    val inhaleMillis: Long? = null,
    val exhaleMillis: Long? = null,
    val estimateMode: String? = null,
    val confidence: String? = null,
    val usedFallback: Boolean? = null,
    val fallbackReason: String? = null,
)

internal object SessionHistoryJson {
    fun encode(records: List<SessionHistoryRecord>): String = records.joinToString(",", "[", "]") { record ->
        listOf(
            "sessionId" to record.sessionId,
            "startedAtMillis" to record.startedAtMillis,
            "endedAtMillis" to record.endedAtMillis,
            "plannedDurationMillis" to record.plannedDurationMillis,
            "activeDurationMillis" to record.activeDurationMillis,
            "outcome" to record.outcome?.name,
            "sessionMode" to record.sessionMode.name,
            "cycleMillis" to record.cycleMillis,
            "inhaleMillis" to record.inhaleMillis,
            "exhaleMillis" to record.exhaleMillis,
            "estimateMode" to record.estimateMode,
            "confidence" to record.confidence,
            "usedFallback" to record.usedFallback,
            "fallbackReason" to record.fallbackReason,
        ).joinToString(",", "{", "}") { (key, value) ->
            "\"$key\":" + when (value) {
                null -> "null"
                is Boolean, is Number -> value.toString()
                else -> "\"${escape(value.toString())}\""
            }
        }
    }

    fun decode(json: String): List<SessionHistoryRecord> {
        require(json.trim().startsWith("[") && json.trim().endsWith("]")) { "Invalid history JSON" }
        return Regex("\\{(?:[^{}]|\\\"(?:\\\\.|[^\\\"])*\\\")*\\}").findAll(json).map { match ->
            val objectText = match.value
            fun raw(key: String): String? = Regex("\\\"$key\\\"\\s*:\\s*(null|\\\"(?:\\\\.|[^\\\"])*\\\"|true|false|-?\\d+)")
                .find(objectText)?.groupValues?.get(1)?.let { if (it == "null") null else unquote(it) }
            fun long(key: String) = raw(key)?.toLongOrNull()
            SessionHistoryRecord(
                sessionId = raw("sessionId") ?: error("Missing sessionId"),
                startedAtMillis = long("startedAtMillis") ?: error("Missing startedAtMillis"),
                endedAtMillis = long("endedAtMillis"),
                plannedDurationMillis = long("plannedDurationMillis") ?: error("Missing plannedDurationMillis"),
                activeDurationMillis = long("activeDurationMillis") ?: error("Missing activeDurationMillis"),
                outcome = raw("outcome")?.let { SessionOutcome.valueOf(it) },
                sessionMode = raw("sessionMode")?.let { SessionMode.valueOf(it) } ?: SessionMode.GUIDED,
                cycleMillis = long("cycleMillis"), inhaleMillis = long("inhaleMillis"), exhaleMillis = long("exhaleMillis"),
                estimateMode = raw("estimateMode"), confidence = raw("confidence"),
                usedFallback = raw("usedFallback")?.toBooleanStrictOrNull(), fallbackReason = raw("fallbackReason"),
            )
        }.toList()
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun unquote(value: String) = value.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
}

internal class SessionHistoryStore(
    private val file: File,
    private val writeAtomically: (File, String) -> Unit = { target, contents ->
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            stream.write(contents.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    },
) {
    companion object { const val MAX_RECORDS = 200 }

    fun load(recoverInterrupted: Boolean = true): List<SessionHistoryRecord> {
        val loaded = runCatching { if (file.exists()) SessionHistoryJson.decode(file.readText()) else emptyList() }.getOrElse { emptyList() }
        val recovered = if (recoverInterrupted) loaded.map { if (it.outcome == null) it.copy(outcome = SessionOutcome.INTERRUPTED, endedAtMillis = it.endedAtMillis ?: it.startedAtMillis) else it } else loaded
        val result = dedupe(recovered)
        if (result != loaded) runCatching { save(result) }
        return result
    }

    fun save(records: List<SessionHistoryRecord>) = writeAtomically(file, SessionHistoryJson.encode(dedupe(records)))

    fun start(record: SessionHistoryRecord) {
        save(listOf(record.copy(outcome = null, endedAtMillis = null)) + load(false).filterNot { it.sessionId == record.sessionId })
    }

    fun finalize(sessionId: String, endedAtMillis: Long, activeDurationMillis: Long, outcome: SessionOutcome) {
        val updated = load(false).map { if (it.sessionId == sessionId && it.outcome == null) it.copy(endedAtMillis = endedAtMillis, activeDurationMillis = activeDurationMillis, outcome = outcome) else it }
        save(updated)
    }

    fun delete(sessionId: String) = save(load().filterNot { it.sessionId == sessionId })
    fun clear() = save(emptyList())

    private fun dedupe(records: List<SessionHistoryRecord>) = records.asSequence().distinctBy { it.sessionId }.take(MAX_RECORDS).toList()
}
