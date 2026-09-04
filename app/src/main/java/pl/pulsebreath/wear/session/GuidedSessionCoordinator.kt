package pl.pulsebreath.wear.session

import pl.pulsebreath.wear.sensor.*
import pl.pulsebreath.wear.signal.*

internal enum class GuidedStage { IDLE, CALIBRATING, READY, RUNNING, PAUSED, SUMMARY }

/** Single serialized owner. All calls and dispatched callbacks use the UI thread/clock. */
internal class GuidedSessionCoordinator(
    private val sourceFactory: () -> StreamingSensorDataSource,
    private val clock: () -> Long,
    private val dispatch: (() -> Unit) -> Unit,
    private val changed: () -> Unit = {},
) {
    companion object {
        const val MAX_CALIBRATION_ATTEMPTS = 10
    }

    var stage = GuidedStage.IDLE; private set
    var calibrationAttempt = 0; private set
    var estimate: PaceEstimate? = null; private set
    var config = BreathingSessionConfig(); private set
    var cue = BreathingSessionState().snapshot(0, config); private set
    var status = SensorStreamStatus(SensorStreamState.IDLE, "Sensor is stopped."); private set
    var hrv = HrvAnalyzer.analyze(emptyList()); private set
    var alignment = BreathingAlignmentAnalyzer.analyze(emptyList()); private set
    var latestSample: SensorSample? = null; private set
    var timing = TimingDiagnostics(); private set
    var meanBpm: Double? = null; private set
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
    private val phases = PhaseHistory()
    private val calibration = mutableListOf<TimedIbi>()
    private val samples = mutableListOf<SensorSample>()
    private val observations = mutableListOf<AlignmentObservation>()

    fun calibrate() {
        if (stage != GuidedStage.IDLE && stage != GuidedStage.SUMMARY) return
        unsubscribe()
        clearWindow()
        estimate = null
        latestSample = null
        timing = TimingDiagnostics()
        meanBpm = null
        bpmSum = 0.0
        bpmCount = 0
        calibrationAttempt = 1
        startCalibrationAttempt()
        changed()
    }

    fun tick() {
        val now = clock()
        if (stage == GuidedStage.CALIBRATING &&
            now - calibrationStart >= PaceCalibrator.CALIBRATION_DURATION_MILLIS) {
            estimate = PaceCalibrator.estimate(calibration.toList())
            config = BreathingSessionConfig(estimate!!.inhaleMillis, estimate!!.exhaleMillis)
            calibration.clear()
            unsubscribe()
            if (estimate!!.usedFallback && calibrationAttempt < MAX_CALIBRATION_ATTEMPTS) {
                calibrationAttempt++
                startCalibrationAttempt()
            } else {
                stage = GuidedStage.READY
            }
        }
        if (stage == GuidedStage.RUNNING) {
            state = state.advance(now, config)
            cue = state.snapshot(now, config)
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
        if ((stage != GuidedStage.READY && stage != GuidedStage.PAUSED) || estimate?.usedFallback == true) return
        clearWindow()
        epochStart = clock()
        state = if (stage == GuidedStage.PAUSED) state.resume(epochStart) else state.start(epochStart)
        stage = GuidedStage.RUNNING
        tick()
        subscribe()
    }

    /** READY-only selection: pace timings stay fixed while the run length is chosen. */
    fun setSessionDuration(durationMillis: Long) {
        if (stage != GuidedStage.READY || estimate?.usedFallback == true) return
        config = config.copy(sessionDurationMillis = durationMillis)
        changed()
    }

    fun pause() {
        if (stage != GuidedStage.RUNNING) return
        tick()
        if (stage != GuidedStage.RUNNING) return
        state = state.pause(clock(), config)
        cue = state.snapshot(clock(), config)
        unsubscribe()
        clearWindow()
        stage = GuidedStage.PAUSED
        changed()
    }

    fun stop() {
        if (stage == GuidedStage.IDLE || stage == GuidedStage.SUMMARY) return
        if (stage == GuidedStage.RUNNING) refresh(clock())
        state = state.cancel(clock(), config)
        cue = state.snapshot(clock(), config)
        unsubscribe()
        calibration.clear()
        samples.clear()
        observations.clear()
        phases.clear()
        lastPhaseTime = null
        lastBeatTime = null
        pendingBreak = true
        latestSample = null
        stage = GuidedStage.SUMMARY
        changed()
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
