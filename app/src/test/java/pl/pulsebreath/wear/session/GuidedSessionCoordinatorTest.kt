package pl.pulsebreath.wear.session

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToLong
import pl.pulsebreath.wear.sensor.*
import pl.pulsebreath.wear.signal.*

class GuidedSessionCoordinatorTest {
    private class Source : StreamingSensorDataSource {
        lateinit var sample: (SensorSample) -> Unit
        lateinit var status: (SensorStreamStatus) -> Unit
        var stopped = false
        override fun start(onStatus: (SensorStreamStatus) -> Unit, onSample: (SensorSample) -> Unit) {
            sample = onSample
            status = onStatus
        }
        override fun stop() { stopped = true }
    }
    private class Harness {
        var now = 0L
        val sources = mutableListOf<Source>()
        val owner = GuidedSessionCoordinator(
            { Source().also(sources::add) }, { now }, { it() },
        )
        fun ready() {
            owner.calibrate()
            emitSuccessfulCalibrationAttempt()
            now = 35_000L
            owner.tick()
        }
        fun running() {
            ready()
            advance(55_000L)
            owner.start()
        }
        fun foreground() { owner.onForeground() }
        fun advance(to: Long) {
            while (now < to) { now = minOf(to, now + 50); owner.tick() }
        }
        fun emit(
            values: List<Long>,
            breaks: Set<Int> = emptySet(),
            rejected: Int = 0,
            quality: SensorSignalQuality = SensorSignalQuality.GOOD,
        ) {
            sources.last().sample(SensorSample(now, 60.0, values, quality,
                SensorSourceType.SAMSUNG, breaks, rejected))
        }
        fun emitSuccessfulCalibrationAttempt() {
            val start = now
            var time = start
            while (true) {
                val ibi = (800 + 65 * sin(2 * PI * (time - start) / 10_000)).roundToLong()
                time += ibi
                if (time > start + 35_000) return
                now = time
                emit(listOf(ibi))
            }
        }
    }

    @Test fun syntheticTimedBeatsProduceNonFallbackConfiguration() {
        val h = Harness()
        h.owner.calibrate()
        val beats = mutableListOf<TimedIbi>()
        var t = 0L
        while (true) {
            val ibi = (800 + 65 * sin(2 * PI * t / 10_000)).roundToLong()
            t += ibi
            if (t > 35_000) break
            val beat = TimedIbi(t, ibi, true, beats.size, false)
            beats.add(beat)
            h.now = beat.endMillis!!
            h.emit(listOf(beat.ibiMillis))
        }
        h.now = 35_000
        h.owner.tick()
        assertFalse(h.owner.estimate!!.usedFallback)
        assertEquals(PaceCalibrator.estimate(beats).cycleMillis, h.owner.config.cycleDurationMillis)
    }
    @Test fun calibrationKeepsMappedIbiWhenBpmQualityIsNotGood() {
        val h = Harness()
        h.owner.calibrate()
        var time = 0L
        while (true) {
            val ibi = (800 + 65 * sin(2 * PI * time / 10_000)).roundToLong()
            time += ibi
            if (time > 35_000) break
            h.now = time
            h.emit(listOf(ibi), quality = SensorSignalQuality.MOTION_ARTIFACT)
        }
        h.now = 35_000
        h.owner.tick()
        assertEquals(GuidedStage.READY, h.owner.stage)
        assertFalse(h.owner.estimate!!.usedFallback)
        assertTrue(h.owner.estimate!!.acceptedIbiCount >= 12)
    }
    @Test fun readySessionDurationCanChangeButIsFixedAfterStart() {
        val h = Harness()
        h.owner.calibrate()
        h.emitSuccessfulCalibrationAttempt()
        h.now = 35_000L
        h.owner.tick()
        assertEquals(GuidedStage.READY, h.owner.stage)
        h.owner.setSessionDuration(300_000)
        assertEquals(300_000, h.owner.config.sessionDurationMillis)
        h.advance(55_000L)
        h.owner.start()
        h.owner.setSessionDuration(600_000)
        assertEquals(300_000, h.owner.config.sessionDurationMillis)
    }

    @Test fun readyRequiresTwentyThousandMillisOfVisibleForegroundTimeBeforeStart() {
        val h = Harness()
        h.ready()
        h.advance(54_999L)
        assertFalse(h.owner.canStart)
        h.advance(55_000L)
        assertTrue(h.owner.canStart)
    }

    @Test fun backgroundPausesReadyCountdown() {
        val h = Harness()
        h.ready()
        h.advance(45_000L)
        h.owner.onBackground()
        h.advance(65_000L)
        assertFalse(h.owner.canStart)
        h.foreground()
        h.advance(74_999L)
        assertFalse(h.owner.canStart)
        h.advance(75_000L)
        assertTrue(h.owner.canStart)
    }
    @Test fun noDataCalibrationRetriesExactlyTenTimesThenStops() {
        val h = Harness()
        assertEquals(GuidedStage.IDLE, h.owner.stage)
        h.owner.calibrate()
        repeat(GuidedSessionCoordinator.MAX_CALIBRATION_ATTEMPTS) { attempt ->
            h.now += 35_000
            h.owner.tick()
            if (attempt < GuidedSessionCoordinator.MAX_CALIBRATION_ATTEMPTS - 1) {
                assertEquals(GuidedStage.CALIBRATING, h.owner.stage)
                assertEquals(attempt + 2, h.owner.calibrationAttempt)
            }
        }
        assertEquals(GuidedStage.READY, h.owner.stage)
        assertEquals(GuidedSessionCoordinator.MAX_CALIBRATION_ATTEMPTS, h.owner.calibrationAttempt)
        assertTrue(h.owner.estimate!!.usedFallback)
        assertEquals(PaceFallbackReason.TOO_FEW_INTERVALS, h.owner.estimate!!.fallbackReason)
        assertEquals(10_000L, h.owner.config.cycleDurationMillis)
        assertEquals(GuidedSessionCoordinator.MAX_CALIBRATION_ATTEMPTS, h.sources.size)
        assertTrue(h.sources.all { it.stopped })
    }

    @Test fun readyRetryRestartsCalibrationFromAttemptOneWithOneActiveSubscription() {
        val h = Harness()
        h.owner.calibrate()
        repeat(GuidedSessionCoordinator.MAX_CALIBRATION_ATTEMPTS) {
            h.now += 35_000
            h.owner.tick()
        }
        assertEquals(GuidedStage.READY, h.owner.stage)
        assertEquals(GuidedSessionCoordinator.MAX_CALIBRATION_ATTEMPTS, h.owner.calibrationAttempt)
        val previousSources = h.sources.toList()

        h.owner.retryCalibration()

        assertEquals(GuidedStage.CALIBRATING, h.owner.stage)
        assertEquals(1, h.owner.calibrationAttempt)
        assertNull(h.owner.estimate)
        assertEquals(1, h.sources.count { !it.stopped })
        assertTrue(previousSources.all { it.stopped })
        assertFalse(h.sources.last().stopped)
        assertEquals(previousSources.size + 1, h.sources.size)
    }

    @Test fun durationSelectionSurvivesRecalibration() {
        val h = Harness()
        h.ready()
        h.owner.setSessionDuration(900_000)
        h.owner.retryCalibration()
        h.emitSuccessfulCalibrationAttempt()
        h.now = 70_000L
        h.owner.tick()
        assertEquals(GuidedStage.READY, h.owner.stage)
        assertEquals(900_000L, h.owner.config.sessionDurationMillis)
    }

    @Test fun calibrationMapsLiveEstimateAndRetainsTrailingBreaks() {
        val h = Harness()
        h.owner.calibrate()
        val beats = mutableListOf<TimedIbi>()
        for (i in 1..34) {
            h.now = i * 1000L
            val rejected = if (i == 18) 1 else 0
            val breaks = if (rejected > 0) setOf(1) else emptySet()
            val sample = SensorSample(h.now, 60.0, listOf(1000), SensorSignalQuality.GOOD,
                SensorSourceType.SAMSUNG, breaks, rejected)
            val batch = expandBatch(h.now, sample)
            beats.addAll(batch.intervals)
            if (batch.trailingBreak) beats.add(TimedIbi(null, 0, false, 0, true))
            h.sources.last().sample(sample)
        }
        h.now = 35_000
        h.owner.tick()
        assertFalse(h.owner.estimate!!.usedFallback)
        assertEquals(PaceEstimateMode.DEFAULT_NO_PEAK, h.owner.estimate!!.estimateMode)
        assertEquals(h.owner.estimate!!.inhaleMillis, h.owner.config.inhaleDurationMillis)
        assertEquals(GuidedStage.READY, h.owner.stage)
        assertEquals(1, h.owner.calibrationAttempt)
    }

    @Test fun batchUsesIndividualHistoricalPhasesAndUnchangedPearson() {
        val h = Harness()
        h.running()
        h.advance(67_000)
        val values = List(12) { if (it % 2 == 0) 900L else 1100L }
        h.emit(values)
        val sample = SensorSample(h.now, 60.0, values, SensorSignalQuality.GOOD, SensorSourceType.SAMSUNG)
        val state = BreathingSessionState().start(55_000)
        val expected = expandBatch(h.now, sample).intervals.map { beat ->
            val cue = state.snapshot(beat.endMillis!!, h.owner.config)
            AlignmentObservation(sample.copy(monotonicTimestampMillis = beat.endMillis,
                ibiMillis = listOf(beat.ibiMillis)), cue.phase, cue.phaseProgress)
        }
        val metric = BreathingAlignmentAnalyzer.analyze(expected)
        assertEquals(AlignmentAvailability.AVAILABLE, h.owner.alignment.availability)
        assertEquals(12, h.owner.alignment.validIbiCount)
        assertEquals(metric.score!!, h.owner.alignment.score!!, 1e-9)
        assertEquals(1, h.owner.hrv.sampleEventCount)
    }

    @Test fun emptyRawEventsCannotBeHiddenByBatchExpansion() {
        val h = Harness()
        h.running()
        repeat(4) { h.advance(h.now + 1000); h.emit(emptyList()) }
        h.advance(61_000)
        h.emit(List(12) { if (it % 2 == 0) 900L else 1100L })
        assertEquals(20.0, h.owner.hrv.ibiEventCoveragePercent, 0.0)
        assertNull(h.owner.alignment.score)
    }

    @Test fun pauseResumeRejectsOldCallbacksAndBeatsAndKeepsPace() {
        val h = Harness()
        h.running()
        h.advance(60_000)
        h.emit(listOf(1000))
        val old = h.sources.last()
        val config = h.owner.config
        h.owner.pause()
        assertTrue(old.stopped)
        assertEquals(GuidedStage.PAUSED, h.owner.stage)
        h.now += 5000
        h.owner.start()
        assertEquals(config, h.owner.config)
        assertEquals(5000L, h.owner.cue.elapsedActiveMillis)
        old.sample(SensorSample(h.now, 200.0, listOf(300), SensorSignalQuality.GOOD, SensorSourceType.SAMSUNG))
        assertEquals(0, h.owner.hrv.sampleEventCount)
        h.emit(listOf(1000, 1000))
        assertEquals(1, h.owner.alignment.validIbiCount)
        h.owner.stop()
        assertTrue(h.sources.last().stopped)
        assertEquals(GuidedStage.SUMMARY, h.owner.stage)
        assertNull(h.owner.latestSample)
        assertEquals(60.0, h.owner.meanBpm!!, 0.0)
    }

    @Test fun rejectionAndStaleWindowCannotKeepAnOldScore() {
        val h = Harness()
        h.running()
        h.advance(h.now + 12_000)
        val values = List(12) { if (it % 2 == 0) 900L else 1100L }
        h.emit(values)
        assertNotNull(h.owner.alignment.score)
        h.advance(h.now + 1_000)
        h.emit(listOf(1000), setOf(1), 1)
        assertNull(h.owner.alignment.score)
        assertEquals(1, h.owner.hrv.rejectedIbiCount)
        h.advance(h.now + 12_000)
        h.emit(values)
        assertNotNull(h.owner.alignment.score)
        h.advance(h.now + 60_050)
        assertNull(h.owner.alignment.score)
        assertEquals(0, h.owner.hrv.sampleEventCount)
    }

    @Test fun sensorFailureDuringCalibrationStillReachesExplicitFallback() {
        val h = Harness()
        h.owner.calibrate()
        h.sources.last().status(SensorStreamStatus(SensorStreamState.ERROR, "Disconnected"))
        assertTrue(h.sources.last().stopped)
        h.now = 35_000
        h.owner.tick()
        assertEquals(GuidedStage.CALIBRATING, h.owner.stage)
        assertEquals(2, h.owner.calibrationAttempt)
        assertEquals(PaceFallbackReason.TOO_FEW_INTERVALS, h.owner.estimate!!.fallbackReason)
    }
    @Test fun successfulRetryExitsLoopImmediately() {
        val h = Harness()
        h.owner.calibrate()
        h.now = 35_000
        h.owner.tick()
        assertEquals(GuidedStage.CALIBRATING, h.owner.stage)
        assertEquals(2, h.owner.calibrationAttempt)
        h.emitSuccessfulCalibrationAttempt()
        h.now = 70_000
        h.owner.tick()
        assertEquals(GuidedStage.READY, h.owner.stage)
        assertEquals(2, h.owner.calibrationAttempt)
        assertFalse(h.owner.estimate!!.usedFallback)
        assertEquals(2, h.sources.size)
        assertTrue(h.sources.last().stopped)
    }
    @Test fun cancellationDuringRetryStopsSensorAndPreventsAnotherAttempt() {
        val h = Harness()
        h.owner.calibrate()
        h.now = 35_000
        h.owner.tick()
        val retrySource = h.sources.last()
        h.owner.stop()
        assertEquals(GuidedStage.SUMMARY, h.owner.stage)
        assertTrue(retrySource.stopped)
        h.now += 35_000
        h.owner.tick()
        assertEquals(GuidedStage.SUMMARY, h.owner.stage)
        assertEquals(2, h.sources.size)
    }
    @Test fun cancellationCompletionAndSensorFailureCleanUp() {
        val h = Harness()
        h.owner.calibrate()
        h.owner.stop()
        assertTrue(h.sources.last().stopped)
        h.ready()
        h.advance(55_000)
        h.owner.start()
        h.sources.last().status(SensorStreamStatus(SensorStreamState.ERROR, "Disconnected"))
        assertEquals(GuidedStage.PAUSED, h.owner.stage)
        assertTrue(h.sources.last().stopped)
        h.owner.start()
        h.now += 120_000
        h.owner.tick()
        assertEquals(GuidedStage.SUMMARY, h.owner.stage)
        assertEquals(BreathingSessionStatus.COMPLETED, h.owner.cue.status)
        assertTrue(h.sources.last().stopped)
    }

    @Test fun backgroundPausesRunningSessionAndRequiresExplicitResume() {
        val h = Harness()
        h.running()
        h.advance(60_000)
        h.emit(listOf(1000))
        val backgroundSource = h.sources.last()

        h.owner.onBackground()

        assertEquals(GuidedStage.PAUSED, h.owner.stage)
        assertEquals(GuidedPauseReason.BACKGROUND, h.owner.pauseReason)
        assertTrue(backgroundSource.stopped)
        h.now += 10_000
        h.owner.tick()
        assertEquals(GuidedStage.PAUSED, h.owner.stage)

        h.owner.start()
        assertEquals(GuidedStage.RUNNING, h.owner.stage)
        assertEquals(1, h.sources.count { !it.stopped })
        assertNull(h.owner.pauseReason)
    }

    @Test fun backgroundInterruptsCalibrationWithNoticeInsteadOfCompleting() {
        val h = Harness()
        h.owner.calibrate()
        val calibrationSource = h.sources.last()

        h.owner.onBackground()

        assertEquals(GuidedStage.IDLE, h.owner.stage)
        assertTrue(calibrationSource.stopped)
        assertNotNull(h.owner.notice)
        h.now += 35_000
        h.owner.tick()
        assertEquals(GuidedStage.IDLE, h.owner.stage)
        assertNull(h.owner.estimate)
    }

    @Test fun backgroundLeavesReadySessionAvailable() {
        val h = Harness()
        h.ready()

        h.owner.onBackground()

        assertEquals(GuidedStage.READY, h.owner.stage)
        assertNotNull(h.owner.estimate)
    }

    @Test fun newCalibrationAfterCompletedSessionStartsWithCleanStateAndCue() {
        val h = Harness()
        h.ready()
        h.advance(55_000)
        h.owner.start()
        h.now += h.owner.config.sessionDurationMillis
        h.owner.tick()
        assertEquals(GuidedStage.SUMMARY, h.owner.stage)
        assertEquals(BreathingSessionStatus.COMPLETED, h.owner.cue.status)
        assertEquals(120_000L, h.owner.cue.elapsedActiveMillis)

        h.owner.calibrate()
        assertEquals(GuidedStage.CALIBRATING, h.owner.stage)
        assertEquals(BreathingSessionStatus.IDLE, h.owner.cue.status)
        assertEquals(0L, h.owner.cue.elapsedActiveMillis)

        h.owner.stop()
        assertEquals(GuidedStage.SUMMARY, h.owner.stage)
        assertEquals(BreathingSessionStatus.IDLE, h.owner.cue.status)
        assertEquals(0L, h.owner.cue.elapsedActiveMillis)
    }
}
