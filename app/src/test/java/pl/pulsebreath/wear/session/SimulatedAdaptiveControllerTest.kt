package pl.pulsebreath.wear.session

import org.junit.Assert.*
import org.junit.Test

class SimulatedAdaptiveControllerTest {
    // Deliberately tiny software clock units, not a human protocol.
    private fun controller() = SimulatedAdaptiveController(
        SimulationProtocol(listOf(10L, 20L), 40, 5, 100, 150, 1_000, 0.1, 10),
    )

    private fun window(end: Long, scores: Map<Long, Double> = mapOf(10L to 0.8)) =
        SimulatedWindow(end - 40, end, scores)

    private fun calibrated(c: SimulatedAdaptiveController, tied: Boolean = false): Long {
        c.start(0)
        c.accept(window(40), 40)
        var now = 40L
        repeat(4) { i ->
            now += 40
            val rate = if (i % 2 == 0) 10L else 20L
            c.accept(window(now, mapOf(rate to if (tied || rate == 10L) 0.8 else 0.2)), now)
            if (i < 3) {
                now += 5
                c.tick(now)
            }
        }
        return now
    }

    private fun active(c: SimulatedAdaptiveController): Long {
        val ready = calibrated(c)
        c.beginSession(ready)
        c.accept(window(ready + 40), ready + 40)
        return ready + 40
    }

    @Test fun repeatedTrialsSelectCandidateButDoNotAutoStart() {
        val c = controller()
        val now = calibrated(c)
        assertEquals(AdaptiveStage.READY, c.tick(now).stage)
        assertEquals(10L, c.tick(now).cycleMillis)
        assertNull(c.tick(now).cycleProgress)
    }

    @Test fun tiesDoNotSelectCandidate() {
        val c = controller()
        assertEquals(AdaptiveStage.UNAVAILABLE, c.tick(calibrated(c, true)).stage)
    }

    @Test fun changeRequiresTwoIndependentWindowsAndNextCycleBoundary() {
        val c = controller()
        val start = active(c)
        val scores = mapOf(10L to 0.2, 20L to 0.8)
        c.accept(window(start + 40, scores), start + 40)
        assertNull(c.tick(start + 40).pendingCycleMillis)
        c.accept(window(start + 80, scores), start + 80)
        assertEquals(20L, c.tick(start + 80).pendingCycleMillis)
        assertEquals(10L, c.tick(start + 89).cycleMillis)
        assertEquals(0.9, c.tick(start + 89).cycleProgress!!, 0.00001)
        assertEquals(20L, c.tick(start + 90).cycleMillis)
        assertEquals(0.0, c.tick(start + 90).cycleProgress!!, 0.00001)
    }

    @Test fun noCallbacksSuspendAndClearPendingDecision() {
        val c = controller()
        val start = active(c)
        val state = c.tick(start + 101)
        assertEquals(AdaptiveStage.SUSPENDED, state.stage)
        assertNull(state.pendingCycleMillis)
        c.accept(window(start + 141, mapOf(10L to 0.8, 20L to 0.2)), start + 141)
        assertEquals(AdaptiveStage.ACTIVE, c.tick(start + 141).stage)
    }

    @Test fun invalidWindowsFailClosed() {
        val invalid = listOf(
            window(40).copy(adequate = false),
            window(40).copy(endMillis = 41),
            window(40).copy(startMillis = -1),
            window(40).copy(scores = mapOf(10L to Double.NaN)),
            window(40).copy(scores = emptyMap()),
        )
        invalid.forEach { w ->
            val c = controller()
            c.start(0)
            c.accept(w, 40)
            assertEquals(AdaptiveStage.UNAVAILABLE, c.tick(40).stage)
        }
    }

    @Test fun repeatedEvidenceCannotDriveAdaptation() {
        val c = controller()
        val start = active(c)
        val w = window(start + 40, mapOf(10L to 0.2, 20L to 0.8))
        c.accept(w, start + 40)
        c.accept(w, start + 41)
        assertEquals(AdaptiveStage.SUSPENDED, c.tick(start + 41).stage)
        assertNull(c.tick(start + 41).pendingCycleMillis)
    }

    @Test fun pauseRequiresFreshAcquisitionAndExcludesPausedTime() {
        val c = controller()
        val start = active(c)
        c.pause(start + 10)
        c.resume(start + 2_000)
        assertEquals(AdaptiveStage.ACQUIRING, c.tick(start + 2_000).stage)
        c.accept(window(start + 2_040), start + 2_040)
        assertEquals(AdaptiveStage.ACTIVE, c.tick(start + 2_040).stage)
        assertEquals(AdaptiveStage.COMPLETED, c.tick(start + 3_030).stage)
    }

    @Test fun pauseDuringCalibrationRestartsTrials() {
        val c = controller()
        c.start(0)
        c.accept(window(40), 40)
        c.pause(50)
        c.resume(60)
        assertEquals(AdaptiveStage.ACQUIRING, c.tick(60).stage)
        assertEquals(0, c.tick(60).trialIndex)
    }

    @Test fun timeoutAndBackwardClockHaveExplicitOutcomes() {
        val c = controller()
        c.start(0)
        assertEquals(AdaptiveStage.UNAVAILABLE, c.tick(151).stage)
        c.start(200)
        assertEquals(AdaptiveStage.ERROR, c.tick(199).stage)
    }

    @Test fun cancellationAndSourceFailureCannotBeRevivedByEvidence() {
        val c = controller()
        val start = active(c)
        c.stop(start)
        c.accept(window(start + 40), start + 40)
        assertEquals(AdaptiveStage.CANCELLED, c.tick(start + 40).stage)
        c.start(start + 40)
        c.fail("source error")
        c.accept(window(start + 80), start + 80)
        assertEquals(AdaptiveStage.ERROR, c.tick(start + 80).stage)
    }

    @Test fun newStartAfterPausedSessionClearsResumeIntent() {
        val c = controller()
        val start = active(c)
        c.pause(start)
        c.start(start)
        c.accept(window(start + 40), start + 40)
        assertEquals(AdaptiveStage.TRIAL, c.tick(start + 40).stage)
    }

    @Test fun noisyAlternatingPreferenceDoesNotQueueChange() {
        val c = controller()
        val start = active(c)
        c.accept(window(start + 40, mapOf(10L to 0.2, 20L to 0.8)), start + 40)
        c.accept(window(start + 80, mapOf(10L to 0.8, 20L to 0.2)), start + 80)
        c.accept(window(start + 120, mapOf(10L to 0.2, 20L to 0.8)), start + 120)
        assertNull(c.tick(start + 120).pendingCycleMillis)
    }

    @Test fun gapOrMixedRateWindowCannotBeUsedAfterRateChange() {
        val c = controller()
        val start = active(c)
        val scores = mapOf(10L to 0.2, 20L to 0.8)
        c.accept(window(start + 40, scores), start + 40)
        c.accept(window(start + 80, scores), start + 80)
        c.tick(start + 90)
        c.accept(window(start + 120, scores), start + 120)
        assertEquals(AdaptiveStage.SUSPENDED, c.tick(start + 120).stage)
    }

    @Test fun boundedAdjustmentRejectsTooLargeStep() {
        val c = SimulatedAdaptiveController(
            SimulationProtocol(listOf(10L, 20L), 40, 5, 100, 150, 1_000, 0.1, 5),
        )
        val start = active(c)
        val scores = mapOf(10L to 0.2, 20L to 0.8)
        c.accept(window(start + 40, scores), start + 40)
        c.accept(window(start + 80, scores), start + 80)
        assertNull(c.tick(start + 80).pendingCycleMillis)
        assertEquals(10L, c.tick(start + 90).cycleMillis)
    }

    @Test fun staleWindowDoesNotStartCalibration() {
        val c = controller()
        c.start(0)
        c.accept(window(40), 141)
        assertEquals(AdaptiveStage.UNAVAILABLE, c.tick(141).stage)
    }

    @Test fun incompleteComparisonSuspendsInsteadOfChoosingOnlyAvailableRate() {
        val c = controller()
        val start = active(c)
        c.accept(window(start + 40), start + 40)
        assertEquals(AdaptiveStage.SUSPENDED, c.tick(start + 40).stage)
    }
}
