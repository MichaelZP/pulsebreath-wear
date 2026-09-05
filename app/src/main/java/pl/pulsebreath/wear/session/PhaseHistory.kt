package pl.pulsebreath.wear.session

internal data class PhaseHistoryEntry(
    val atMillis: Long,
    val phase: BreathingPhase,
    val phaseProgress: Float,
) {
    init {
        require(atMillis >= 0)
        require(phaseProgress.isFinite() && phaseProgress in 0f..1f)
    }
}

/** Bounded cue history. Clear on session/rate epoch changes and pause/resume. */
internal class PhaseHistory(
    private val retentionMillis: Long = 90_000,
    private val maxEntries: Int = 6_000,
) {
    private val entries = ArrayDeque<PhaseHistoryEntry>()
    init { require(retentionMillis > 0 && maxEntries > 0) }

    fun clear() = entries.clear()

    fun add(entry: PhaseHistoryEntry) {
        require(entries.lastOrNull()?.let { entry.atMillis > it.atMillis } != false)
        entries.addLast(entry)
        while (entries.size > maxEntries || entry.atMillis - entries.first().atMillis > retentionMillis) {
            entries.removeFirst()
        }
    }

    /** Previous recorded cue only. No interpolation, future extrapolation or gap filling. */
    fun phaseAt(tMillis: Long, maxSnapshotAgeMillis: Long): PhaseHistoryEntry? {
        require(maxSnapshotAgeMillis >= 0)
        val last = entries.lastOrNull() ?: return null
        if (tMillis < 0 || tMillis > last.atMillis) return null
        return entries.lastOrNull { it.atMillis <= tMillis }
            ?.takeIf { tMillis - it.atMillis <= maxSnapshotAgeMillis }
    }
}
