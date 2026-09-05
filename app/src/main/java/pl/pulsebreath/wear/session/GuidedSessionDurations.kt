package pl.pulsebreath.wear.session

internal object GuidedSessionDurations {
    const val READY_PREPARATION_MILLIS = 20_000L

    val allowedSessionDurationsMillis = listOf(
        120_000L,
        300_000L,
        600_000L,
        900_000L,
        1_800_000L,
    )

    fun isAllowedSessionDuration(durationMillis: Long): Boolean =
        durationMillis in allowedSessionDurationsMillis
}
