package com.example.pulsebreathwear.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingSessionStateTest {
    private val config = BreathingSessionConfig()

    @Test
    fun defaultConfigUsesTwoMinuteSessionAndTenSecondBreathCycle() {
        assertEquals(4_500L, config.inhaleDurationMillis)
        assertEquals(5_500L, config.exhaleDurationMillis)
        assertEquals(10_000L, config.cycleDurationMillis)
        assertEquals(120_000L, config.sessionDurationMillis)
    }

    @Test
    fun phaseChangesAtConfiguredMonotonicBoundaries() {
        val state = BreathingSessionState().start(nowMillis = 1_000L)

        assertEquals(
            BreathingPhase.INHALE,
            state.snapshot(nowMillis = 5_499L, config = config).phase,
        )
        assertEquals(
            BreathingPhase.EXHALE,
            state.snapshot(nowMillis = 5_500L, config = config).phase,
        )
        assertEquals(
            BreathingPhase.EXHALE,
            state.snapshot(nowMillis = 10_999L, config = config).phase,
        )
        assertEquals(
            BreathingPhase.INHALE,
            state.snapshot(nowMillis = 11_000L, config = config).phase,
        )
    }

    @Test
    fun pauseAndResumeExcludePausedTime() {
        val paused =
            BreathingSessionState()
                .start(nowMillis = 1_000L)
                .pause(nowMillis = 4_000L, config = config)

        assertEquals(BreathingSessionStatus.PAUSED, paused.status)
        assertEquals(
            3_000L,
            paused.snapshot(nowMillis = 10_000L, config = config).elapsedActiveMillis,
        )

        val resumed = paused.resume(nowMillis = 10_000L)
        val snapshot = resumed.snapshot(nowMillis = 11_500L, config = config)

        assertEquals(BreathingSessionStatus.RUNNING, resumed.status)
        assertEquals(4_500L, snapshot.elapsedActiveMillis)
        assertEquals(BreathingPhase.EXHALE, snapshot.phase)
    }

    @Test
    fun advanceCompletesSessionAtConfiguredDuration() {
        val completed =
            BreathingSessionState()
                .start(nowMillis = 100L)
                .advance(nowMillis = 120_100L, config = config)
        val snapshot = completed.snapshot(nowMillis = 500_000L, config = config)

        assertEquals(BreathingSessionStatus.COMPLETED, completed.status)
        assertEquals(120_000L, snapshot.elapsedActiveMillis)
        assertEquals(0L, snapshot.remainingMillis)
        assertEquals(1f, snapshot.overallProgress, 0.0001f)
    }

    @Test
    fun cancelFreezesElapsedActiveTime() {
        val cancelled =
            BreathingSessionState()
                .start(nowMillis = 1_000L)
                .cancel(nowMillis = 3_500L, config = config)
        val snapshot = cancelled.snapshot(nowMillis = 30_000L, config = config)

        assertEquals(BreathingSessionStatus.CANCELLED, cancelled.status)
        assertEquals(2_500L, snapshot.elapsedActiveMillis)
        assertEquals(117_500L, snapshot.remainingMillis)
    }

    @Test
    fun breathingExpansionGrowsOnInhaleAndShrinksOnExhale() {
        val state = BreathingSessionState().start(nowMillis = 0L)
        val inhaleMiddle = state.snapshot(nowMillis = 2_250L, config = config)
        val exhaleMiddle = state.snapshot(nowMillis = 7_250L, config = config)

        assertEquals(BreathingPhase.INHALE, inhaleMiddle.phase)
        assertEquals(0.5f, inhaleMiddle.breathExpansionFraction, 0.0001f)
        assertEquals(BreathingPhase.EXHALE, exhaleMiddle.phase)
        assertEquals(0.5f, exhaleMiddle.breathExpansionFraction, 0.0001f)
    }

    @Test
    fun configRejectsNonPositiveDurations() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                BreathingSessionConfig(inhaleDurationMillis = 0L)
            }

        assertTrue(exception.message.orEmpty().contains("Inhale duration"))
    }
}
