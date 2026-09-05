package pl.pulsebreath.wear.session

/**
 * The user-selected sequence of full guided sessions.  This contains no sensor
 * readings or persisted health data; it only controls the UI flow.
 */
internal class SessionSeries(initialSelectedCount: Int = SINGLE_SESSION) {
    companion object {
        const val SINGLE_SESSION = 1
        val allowedSessionCounts = setOf(SINGLE_SESSION, 3, 5, 7, 11)

        fun normalizeSelectedCount(value: Int): Int =
            value.takeIf { it in allowedSessionCounts } ?: SINGLE_SESSION
    }

    var selectedCount: Int = normalizeSelectedCount(initialSelectedCount)
        private set
    var completedCount: Int = 0
        private set
    var active: Boolean = false
        private set
    private var automaticStartPending = false

    val currentSessionNumber: Int
        get() = (completedCount + 1).coerceAtMost(selectedCount)
    val hasMoreAfterCurrent: Boolean
        get() = active && completedCount + 1 < selectedCount
    val isFirstSession: Boolean
        get() = completedCount == 0
    val showPreCheckIn: Boolean
        get() = isFirstSession
    val showPostCheckIn: Boolean
        get() = !active && completedCount == selectedCount

    fun select(count: Int): Boolean {
        if (active || count !in allowedSessionCounts) return false
        selectedCount = count
        return true
    }

    fun begin() {
        if (!active) {
            active = true
            completedCount = 0
            automaticStartPending = false
        }
    }

    /** Returns true when a new calibration should begin immediately. */
    fun completeCurrent(): Boolean {
        if (!active) return false
        completedCount = (completedCount + 1).coerceAtMost(selectedCount)
        val continueSeries = completedCount < selectedCount
        automaticStartPending = continueSeries
        if (!continueSeries) active = false
        return continueSeries
    }

    /** Consumes the next-session start only after the coordinator's READY gate passes. */
    fun consumeAutomaticStart(canStart: Boolean): Boolean {
        if (!automaticStartPending || !canStart) return false
        automaticStartPending = false
        return true
    }

    fun cancel() {
        active = false
        automaticStartPending = false
    }

    fun reset() {
        active = false
        completedCount = 0
        automaticStartPending = false
    }
}
