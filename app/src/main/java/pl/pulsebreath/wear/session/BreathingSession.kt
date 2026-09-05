package pl.pulsebreath.wear.session

internal data class BreathingSessionConfig(
    val inhaleDurationMillis: Long = 4_500L,
    val exhaleDurationMillis: Long = 5_500L,
    val sessionDurationMillis: Long = 120_000L,
) {
    init {
        require(inhaleDurationMillis > 0L) { "Inhale duration must be positive" }
        require(exhaleDurationMillis > 0L) { "Exhale duration must be positive" }
        require(sessionDurationMillis > 0L) { "Session duration must be positive" }
    }

    val cycleDurationMillis: Long = inhaleDurationMillis + exhaleDurationMillis
}

internal enum class BreathingPhase {
    INHALE,
    EXHALE,
}

internal enum class BreathingSessionStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
}

internal data class BreathingSessionSnapshot(
    val status: BreathingSessionStatus,
    val phase: BreathingPhase,
    val elapsedActiveMillis: Long,
    val remainingMillis: Long,
    val phaseProgress: Float,
    val overallProgress: Float,
    val breathExpansionFraction: Float,
)

internal data class BreathingSessionState(
    val status: BreathingSessionStatus = BreathingSessionStatus.IDLE,
    val accumulatedActiveMillis: Long = 0L,
    val runningSinceMillis: Long? = null,
    val phaseAnchorActiveMillis: Long = 0L,
) {
    fun start(nowMillis: Long): BreathingSessionState =
        copy(
            status = BreathingSessionStatus.RUNNING,
            accumulatedActiveMillis = 0L,
            runningSinceMillis = nowMillis,
            phaseAnchorActiveMillis = 0L,
        )

    fun pause(
        nowMillis: Long,
        config: BreathingSessionConfig,
    ): BreathingSessionState {
        val advanced = advance(nowMillis, config)
        if (advanced.status != BreathingSessionStatus.RUNNING) return advanced

        return advanced.copy(
            status = BreathingSessionStatus.PAUSED,
            accumulatedActiveMillis = advanced.activeElapsedAt(nowMillis, config),
            runningSinceMillis = null,
        )
    }

    fun resume(nowMillis: Long): BreathingSessionState {
        if (status != BreathingSessionStatus.PAUSED) return this

        return copy(
            status = BreathingSessionStatus.RUNNING,
            runningSinceMillis = nowMillis,
        )
    }

    fun cancel(
        nowMillis: Long,
        config: BreathingSessionConfig,
    ): BreathingSessionState {
        if (status != BreathingSessionStatus.RUNNING && status != BreathingSessionStatus.PAUSED) {
            return this
        }

        return copy(
            status = BreathingSessionStatus.CANCELLED,
            accumulatedActiveMillis = activeElapsedAt(nowMillis, config),
            runningSinceMillis = null,
        )
    }

    fun reset(): BreathingSessionState = BreathingSessionState()

    /** Starts the next cue cycle without changing accumulated session duration. */
    fun reanchorPhase(nowMillis: Long, config: BreathingSessionConfig): BreathingSessionState =
        copy(phaseAnchorActiveMillis = activeElapsedAt(nowMillis, config))

    fun advance(
        nowMillis: Long,
        config: BreathingSessionConfig,
    ): BreathingSessionState {
        if (status != BreathingSessionStatus.RUNNING) return this
        val elapsedMillis = activeElapsedAt(nowMillis, config)

        return if (elapsedMillis >= config.sessionDurationMillis) {
            copy(
                status = BreathingSessionStatus.COMPLETED,
                accumulatedActiveMillis = config.sessionDurationMillis,
                runningSinceMillis = null,
            )
        } else {
            this
        }
    }

    fun snapshot(
        nowMillis: Long,
        config: BreathingSessionConfig,
    ): BreathingSessionSnapshot {
        val elapsedMillis = activeElapsedAt(nowMillis, config)
        val phaseReferenceMillis =
            (elapsedMillis - phaseAnchorActiveMillis).coerceAtLeast(0L)
                .coerceAtMost(config.sessionDurationMillis - 1L)
                .coerceAtLeast(0L)
        val positionInCycle = phaseReferenceMillis % config.cycleDurationMillis
        val phase =
            if (positionInCycle < config.inhaleDurationMillis) {
                BreathingPhase.INHALE
            } else {
                BreathingPhase.EXHALE
            }
        val phaseElapsedMillis =
            when (phase) {
                BreathingPhase.INHALE -> positionInCycle
                BreathingPhase.EXHALE -> positionInCycle - config.inhaleDurationMillis
            }
        val phaseDurationMillis =
            when (phase) {
                BreathingPhase.INHALE -> config.inhaleDurationMillis
                BreathingPhase.EXHALE -> config.exhaleDurationMillis
            }
        val phaseProgress =
            (phaseElapsedMillis.toFloat() / phaseDurationMillis.toFloat()).coerceIn(0f, 1f)
        val expansionFraction =
            when (phase) {
                BreathingPhase.INHALE -> phaseProgress
                BreathingPhase.EXHALE -> 1f - phaseProgress
            }

        return BreathingSessionSnapshot(
            status = status,
            phase = phase,
            elapsedActiveMillis = elapsedMillis,
            remainingMillis = (config.sessionDurationMillis - elapsedMillis).coerceAtLeast(0L),
            phaseProgress = phaseProgress,
            overallProgress =
                (elapsedMillis.toFloat() / config.sessionDurationMillis.toFloat())
                    .coerceIn(0f, 1f),
            breathExpansionFraction = expansionFraction.coerceIn(0f, 1f),
        )
    }

    private fun activeElapsedAt(
        nowMillis: Long,
        config: BreathingSessionConfig,
    ): Long {
        val runningDeltaMillis =
            if (status == BreathingSessionStatus.RUNNING && runningSinceMillis != null) {
                (nowMillis - runningSinceMillis).coerceAtLeast(0L)
            } else {
                0L
            }

        return (accumulatedActiveMillis + runningDeltaMillis)
            .coerceIn(0L, config.sessionDurationMillis)
    }
}
