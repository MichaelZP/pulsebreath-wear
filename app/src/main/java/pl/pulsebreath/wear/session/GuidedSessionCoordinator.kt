package pl.pulsebreath.wear.session

import pl.pulsebreath.wear.sensor.*
import pl.pulsebreath.wear.signal.*

internal enum class GuidedStage { IDLE, CALIBRATING, READY, RUNNING, PAUSED, SUMMARY }
internal enum class GuidedPauseReason { USER, BACKGROUND }

/** Single serialized owner. All calls and dispatched callbacks use the UI thread/clock. */
internal class GuidedSessionCoordinator(
    private val sourceFactory: () -> StreamingSensorDataSource,
    private val clock: () -> Long,
    private val dispatch: (() -> Unit) -> Unit,
    private val changed: () -> Unit = {},
    private val diagnostic: (String) -> Unit = {},
    private val started: (BreathingSessionConfig, PaceEstimate) -> Unit = { _, _ -> },
    private val finalized: (Long, BreathingSessionStatus) -> Unit = { _, _ -> },
    private val completed: () -> Unit = {},
    initialSessionDurationMillis: Long = 120_000L,
) {
    companion object {
        const val MAX_CALIBRATION_ATTEMPTS = 10
    }

    var stage = GuidedStage.IDLE; private set
    var calibrationAttempt = 0; private set
    var estimate: PaceEstimate? = null; private set
    var config = BreathingSessionConfig(sessionDurationMillis = initialSessionDurationMillis); private set
    var cue = BreathingSessionState().snapshot(0, config); private set
    var pauseReason: GuidedPauseReason? = null; private set
    var notice: String? = null; private set
    var status = SensorStreamStatus(SensorStreamState.IDLE, "Sensor is stopped."); private set
    var hrv = HrvAnalyzer.analyze(emptyList()); private set
    var alignment = BreathingAlignmentAnalyzer.analyze(emptyList()); private set
    var latestSample: SensorSample? = null; private set
    var timing = TimingDiagnostics(); private set
    var meanBpm: Double? = null; private set
    var dynamicTuningEnabled = true; private set
    var dynamicTuningAllowsWeak = true; private set
    var pendingDynamicEstimate: PaceEstimate? = null; private set
    private var bpmSum = 0.0
    private var bpmCount = 0
    private var state = BreathingSessionState()
    private var source: StreamingSensorDataSource? = null
    private var generation = 0L
    private var calibrationStart = 0L
    private var epochStart = 0L
    private var lastPhaseTime: Long? = null
    private var lastBeatTime: Long? = null
    private var pendingBreak = true
    private var readyForegroundSinceMillis: Long? = null
    private var readyVisibleAccumulatedMillis = 0L
    private val phases = PhaseHistory()
    private val calibration = mutableListOf<TimedIbi>()
    private val samples = mutableListOf<SensorSample>()
    private val observations = mutableListOf<AlignmentObservation>()
    private val dynamicPaceTuner = DynamicPaceTuner()
    private var persistedRun = false

    val readyVisibleMillis: Long
        get() {
            val foregroundElapsed =
                readyForegroundSinceMillis?.let { (clock() - it).coerceAtLeast(0L) } ?: 0L
            return readyVisibleAccumulatedMillis + foregroundElapsed
        }

    val canStart: Boolean
        get() = stage == GuidedStage.READY &&
            estimate?.usedFallback != true &&
            readyVisibleMillis >= GuidedSessionDurations.READY_PREPARATION_MILLIS

    fun calibrate() {
        if (stage != GuidedStage.IDLE && stage != GuidedStage.SUMMARY) return
        diagnostic("calibrate requested stage=$stage")
        unsubscribe()
        beginCalibrationAttempt(resetPace = false)
        changed()
    }

    fun retryCalibration() {
        if (stage != GuidedStage.READY) return
        diagnostic("calibration retry requested attempt=$calibrationAttempt")
        unsubscribe()
        beginCalibrationAttempt(resetPace = true)
        changed()
    }

    fun onBackground() {
        diagnostic("onBackground stage=$stage")
        when (stage) {
            GuidedStage.READY -> pauseReadyPreparation()
            GuidedStage.RUNNING -> pause(GuidedPauseReason.BACKGROUND)
            // Calibration is a short, bounded acquisition loop. A transient Wear OS
            // onStop must not discard it and send the user back to the app list.
            GuidedStage.CALIBRATING -> Unit
            else -> Unit
        }
    }

    fun onForeground() {
        diagnostic("onForeground stage=$stage")
        if (stage == GuidedStage.READY && estimate?.usedFallback != true && readyForegroundSinceMillis == null) {
            readyForegroundSinceMillis = clock()
            changed()
        }
    }

    fun tick() {
        val now = clock()
        if (stage == GuidedStage.CALIBRATING &&
            now - calibrationStart >= PaceCalibrator.CALIBRATION_DURATION_MILLIS) {
            estimate = PaceCalibrator.estimateCalibration(calibration.toList(), now)
            diagnostic("calibration attempt=$calibrationAttempt fallback=${estimate!!.usedFallback} accepted=${estimate!!.acceptedIbiCount}")
            config = BreathingSessionConfig(
                estimate!!.inhaleMillis,
                estimate!!.exhaleMillis,
                config.sessionDurationMillis,
            )
            calibration.clear()
            unsubscribe()
            if (estimate!!.usedFallback && calibrationAttempt < MAX_CALIBRATION_ATTEMPTS) {
                calibrationAttempt++
                startCalibrationAttempt()
            } else {
                enterReadyState(now)
            }
        }
        if (stage == GuidedStage.RUNNING) {
            state = state.advance(now, config)
            cue = state.snapshot(now, config)
            pendingDynamicEstimate?.let { estimate ->
                if (cue.phase == BreathingPhase.INHALE && cue.phaseProgress <= 0.03f) {
                    state = state.reanchorPhase(now, config)
                    config = config.copy(inhaleDurationMillis = estimate.inhaleMillis, exhaleDurationMillis = estimate.exhaleMillis)
                    cue = state.snapshot(now, config)
                    pendingDynamicEstimate = null
                    diagnostic("dynamic pace applied cycle=${config.cycleDurationMillis}")
                }
            }
            if (state.status == BreathingSessionStatus.COMPLETED) {
                stop()
                return
            }
            if (lastPhaseTime != now) {
                phases.add(PhaseHistoryEntry(now, cue.phase, cue.phaseProgress))
                lastPhaseTime = now
            }
            refresh(now)
        }
        changed()
    }

    fun start() {
        if (stage == GuidedStage.READY && !canStart) return
        if ((stage != GuidedStage.READY && stage != GuidedStage.PAUSED) || estimate?.usedFallback == true) return
        diagnostic("start stage=$stage")
        val newRun = stage == GuidedStage.READY
        clearWindow()
        epochStart = clock()
        state = if (stage == GuidedStage.PAUSED) state.resume(epochStart) else state.start(epochStart)
        pauseReason = null
        clearReadyPreparation()
        stage = GuidedStage.RUNNING
        if (newRun) {
            persistedRun = true
            estimate?.let { started(config, it) }
        }
        tick()
        subscribe()
    }

    /** READY-only selection: pace timings stay fixed while the run length is chosen. */
    fun setSessionDuration(durationMillis: Long): Boolean {
        if (stage != GuidedStage.READY || estimate?.usedFallback == true || !GuidedSessionDurations.isAllowedSessionDuration(durationMillis)) {
            return false
        }
        config = config.copy(sessionDurationMillis = durationMillis)
        changed()
        return true
    }

    fun setDynamicTuningEnabled(enabled: Boolean): Boolean {
        if ((stage != GuidedStage.IDLE && stage != GuidedStage.READY) || estimate?.usedFallback == true) return false
        dynamicTuningEnabled = enabled
        pendingDynamicEstimate = null
        dynamicPaceTuner.reset()
        changed()
        return true
    }

    fun setDynamicTuningAllowsWeak(allowed: Boolean): Boolean {
        if ((stage != GuidedStage.IDLE && stage != GuidedStage.READY) || estimate?.usedFallback == true) return false
        dynamicTuningAllowsWeak = allowed
        pendingDynamicEstimate = null
        dynamicPaceTuner.reset()
        changed()
        return true
    }

    fun pause(reason: GuidedPauseReason = GuidedPauseReason.USER) {
        if (stage != GuidedStage.RUNNING) return
        tick()
        if (stage != GuidedStage.RUNNING) return
        state = state.pause(clock(), config)
        cue = state.snapshot(clock(), config)
        pauseReason = reason
        unsubscribe()
        clearWindow()
        dynamicPaceTuner.reset()
        pendingDynamicEstimate = null
        stage = GuidedStage.PAUSED
        changed()
    }

    fun stop() {
        if (stage == GuidedStage.IDLE || stage == GuidedStage.SUMMARY) return
        diagnostic("stop stage=$stage persisted=$persistedRun")
        val wasRunning = stage == GuidedStage.RUNNING
        if (wasRunning) refresh(clock())
        val activeDuration = state.snapshot(clock(), config).elapsedActiveMillis
        state = state.cancel(clock(), config)
        cue = state.snapshot(clock(), config)
        unsubscribe()
        calibration.clear()
        samples.clear()
        observations.clear()
        dynamicPaceTuner.reset()
        pendingDynamicEstimate = null
        phases.clear()
        lastPhaseTime = null
        lastBeatTime = null
        pendingBreak = true
        latestSample = null
        clearReadyPreparation()
        stage = GuidedStage.SUMMARY
        val completedRun = wasRunning && state.status == BreathingSessionStatus.COMPLETED
        if (persistedRun) {
            persistedRun = false
            finalized(activeDuration, if (completedRun) BreathingSessionStatus.COMPLETED else BreathingSessionStatus.CANCELLED)
        }
        // The record has been finalized and the coordinator is in SUMMARY, so a
        // series owner may safely begin a fresh calibration synchronously.
        if (completedRun) completed()
        changed()
    }

    internal fun dispose() {
        unsubscribe()
    }

    private fun subscribe() {
        val token = ++generation
        // A fresh source isolates late SDK connection callbacks from the next subscription.
        val next = sourceFactory()
        source = next
        try {
            next.start(onStatus = { value -> dispatch {
                if (token == generation) {
                    status = value
                    diagnostic("sensor status=${value.state} message=${value.message}")
                    if (value.state == SensorStreamState.ERROR || value.state == SensorStreamState.UNSUPPORTED) {
                        unsubscribe()
                        // Calibration timer still produces an explicit pace_v1 fallback.
                        if (stage == GuidedStage.RUNNING) pause()
                        status = value
                    }
                    changed()
                }
            } }, onSample = { sample -> dispatch {
                if (token == generation) accept(sample)
            } })
        } catch (_: Exception) {
            diagnostic("sensor start exception")
            unsubscribe()
            if (stage == GuidedStage.RUNNING) pause()
            status = SensorStreamStatus(SensorStreamState.ERROR, "Could not start sensor. Calibration may use fallback.")
            changed()
        }
    }

    private fun startCalibrationAttempt() {
        calibration.clear()
        clearWindow()
        latestSample = null
        calibrationStart = clock()
        epochStart = calibrationStart
        stage = GuidedStage.CALIBRATING
        subscribe()
    }

    private fun interruptCalibration() {
        unsubscribe()
        calibration.clear()
        estimate = null
        latestSample = null
        notice = "Calibration interrupted because the app moved to the background. Start again to retry."
        stage = GuidedStage.IDLE
        changed()
    }

    private fun beginCalibrationAttempt(resetPace: Boolean) {
        clearWindow()
        estimate = null
        notice = null
        pauseReason = null
        if (resetPace) {
            config = BreathingSessionConfig(sessionDurationMillis = config.sessionDurationMillis)
        }
        state = state.reset()
        cue = state.snapshot(clock(), config)
        latestSample = null
        timing = TimingDiagnostics()
        meanBpm = null
        bpmSum = 0.0
        bpmCount = 0
        calibrationAttempt = 1
        startCalibrationAttempt()
    }

    private fun enterReadyState(now: Long) {
        clearReadyPreparation()
        stage = GuidedStage.READY
        if (estimate?.usedFallback != true) {
            readyForegroundSinceMillis = now
        }
    }

    private fun pauseReadyPreparation() {
        val startedAt = readyForegroundSinceMillis ?: return
        readyVisibleAccumulatedMillis += (clock() - startedAt).coerceAtLeast(0L)
        readyForegroundSinceMillis = null
        changed()
    }

    private fun clearReadyPreparation() {
        readyForegroundSinceMillis = null
        readyVisibleAccumulatedMillis = 0L
    }

    private fun unsubscribe() {
        generation++
        source?.stop()
        source = null
        status = SensorStreamStatus(SensorStreamState.IDLE, "Sensor is stopped.")
    }

    private fun accept(sample: SensorSample) {
        if (stage != GuidedStage.CALIBRATING && stage != GuidedStage.RUNNING) return
        tick()
        if (stage != GuidedStage.CALIBRATING && stage != GuidedStage.RUNNING) return
        val receipt = sample.monotonicTimestampMillis
        if (receipt < epochStart || receipt > clock()) return
        latestSample = sample
        timing = timing.add(sample)
        samples.add(sample)
        val batch = expandBatch(receipt, sample)
        batch.intervals.forEach { original ->
            val beat = original.copy(breakBefore = original.breakBefore || pendingBreak)
            pendingBreak = false
            val end = beat.endMillis
            if (stage == GuidedStage.CALIBRATING) {
                calibration.add(if (end != null && end < epochStart) beat.copy(endMillis = null) else beat)
            } else {
                if (beat.breakBefore) observations.clear()
                val phase = end?.takeIf { it >= epochStart && (lastBeatTime == null || it > lastBeatTime!!) }
                    ?.let { phases.phaseAt(it, 250) }
                if (!beat.accepted || end == null || phase == null) {
                    observations.clear()
                    pendingBreak = true
                } else {
                    observations.add(AlignmentObservation(
                        sample.copy(monotonicTimestampMillis = end, ibiMillis = listOf(beat.ibiMillis),
                            ibiBreakBeforeIndices = if (beat.breakBefore) setOf(0) else emptySet(),
                            rejectedIbiCount = 0, timing = null),
                        phase.phase, phase.phaseProgress,
                    ))
                    lastBeatTime = end
                }
            }
        }
        if (stage == GuidedStage.CALIBRATING && batch.trailingBreak) {
            pendingBreak = true
            calibration.add(TimedIbi(null, 0, false, 0, true))
        } else if (stage == GuidedStage.RUNNING &&
            (batch.trailingBreak || batch.intervals.isEmpty() || sample.quality != SensorSignalQuality.GOOD)) {
            pendingBreak = true
            observations.clear()
        }
        if (stage == GuidedStage.RUNNING && sample.quality == SensorSignalQuality.GOOD) {
            sample.beatsPerMinute?.takeIf { it.isFinite() && it > 0 }?.let {
                bpmSum += it
                bpmCount++
                meanBpm = bpmSum / bpmCount
            }
        }
        if (stage == GuidedStage.RUNNING && dynamicTuningEnabled) {
            dynamicPaceTuner.observe(batch.intervals, receipt, config.cycleDurationMillis, dynamicTuningAllowsWeak)?.let {
                pendingDynamicEstimate = it
                diagnostic("dynamic pace pending cycle=${it.cycleMillis}")
            }
        }
        refresh(clock())
        changed()
    }

    private fun refresh(now: Long) {
        samples.removeAll { it.monotonicTimestampMillis < now - 60_000 }
        observations.removeAll { it.sample.monotonicTimestampMillis < now - 60_000 }
        hrv = HrvAnalyzer.analyze(samples)
        val result = BreathingAlignmentAnalyzer.analyze(observations)
        // Beat expansion must never inflate raw-event coverage or hide invalid input.
        alignment = if (hrv.quality != HrvWindowQuality.ADEQUATE || hrv.invalidIbiCount > 0) {
            result.copy(availability = AlignmentAvailability.INSUFFICIENT_QUALITY, score = null)
        } else result
    }

    private fun clearWindow() {
        samples.clear()
        observations.clear()
        phases.clear()
        lastPhaseTime = null
        lastBeatTime = null
        pendingBreak = true
        hrv = HrvAnalyzer.analyze(emptyList())
        alignment = BreathingAlignmentAnalyzer.analyze(emptyList())
    }
}
