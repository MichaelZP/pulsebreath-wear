package pl.pulsebreath.wear.presentation

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import pl.pulsebreath.wear.sensor.SamsungSensorDataSource
import pl.pulsebreath.wear.session.GuidedSessionCoordinator
import pl.pulsebreath.wear.session.BreathingSessionStatus
import pl.pulsebreath.wear.session.SessionSeries
import pl.pulsebreath.wear.history.*
import java.util.UUID
import androidx.compose.runtime.mutableStateOf
import pl.pulsebreath.wear.diagnostics.SessionDiagnostics

internal class SamsungSessionViewModel(application: Application) : AndroidViewModel(application) {
    var revision by mutableLongStateOf(0L)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val historyStore = SessionHistoryStore(application.noBackupFilesDir.resolve("guided-session-history.json"))
    val diagnostics = SessionDiagnostics(application.noBackupFilesDir.resolve("guided-session-diagnostics.log"))
    var history by mutableStateOf(historyStore.load())
        private set
    var stressPreDecision by mutableStateOf<StressCheckIn?>(null)
        private set
    var stressPostDecision by mutableStateOf<StressCheckIn?>(null)
        private set
    var postCheckInEligible by mutableStateOf(false)
        private set
    val series = SessionSeries(
        SessionDurationPreferences.loadSessionSeriesCount(application.applicationContext),
    )
    var completionHapticSequence by mutableLongStateOf(0L)
        private set
    var lastCompletionFinishedSeries by mutableStateOf(false)
        private set
    private var activeSessionId: String? = null
    private var finalizedSessionId: String? = null
    val session: GuidedSessionCoordinator

    init {
        session = GuidedSessionCoordinator(
        sourceFactory = { SamsungSensorDataSource(application.applicationContext) },
        clock = android.os.SystemClock::elapsedRealtime,
        dispatch = { action -> mainHandler.post(action) },
        changed = { revision++ },
        diagnostic = diagnostics::record,
        started = { config, estimate ->
            val id = UUID.randomUUID().toString()
            activeSessionId = id
            historyStore.start(SessionHistoryRecord(
                sessionId = id,
                startedAtMillis = System.currentTimeMillis(),
                endedAtMillis = null,
                plannedDurationMillis = config.sessionDurationMillis,
                activeDurationMillis = 0L,
                outcome = null,
                cycleMillis = estimate.cycleMillis,
                inhaleMillis = estimate.inhaleMillis,
                exhaleMillis = estimate.exhaleMillis,
                estimateMode = estimate.estimateMode.name,
                confidence = estimate.confidence.name,
                usedFallback = estimate.usedFallback,
                fallbackReason = estimate.fallbackReason?.name,
                stressPre = stressPreDecision?.value?.takeIf { series.isFirstSession },
                stressPreAnswered = series.isFirstSession && stressPreDecision?.answered == true,
            ))
            history = historyStore.load(recoverInterrupted = false)
        },
        finalized = { activeMillis, status ->
            activeSessionId?.let { id ->
                historyStore.finalize(id, System.currentTimeMillis(), activeMillis,
                    if (status == BreathingSessionStatus.COMPLETED) SessionOutcome.COMPLETED else SessionOutcome.STOPPED)
                finalizedSessionId = id
                postCheckInEligible = status == BreathingSessionStatus.COMPLETED && !series.hasMoreAfterCurrent
                activeSessionId = null
                history = historyStore.load()
            }
        },
        completed = {
            val continueSeries = series.completeCurrent()
            lastCompletionFinishedSeries = !continueSeries
            completionHapticSequence++
            if (continueSeries) {
                // Intermediate sessions never surface a stress check-in. The next
                // run is independently calibrated and records its own history row.
                postCheckInEligible = false
                finalizedSessionId = null
                diagnostics.record("series continuing session=${series.currentSessionNumber}/${series.selectedCount}")
                session.calibrate()
            } else {
                diagnostics.record("series completed sessions=${series.selectedCount}")
            }
        },
        initialSessionDurationMillis = SessionDurationPreferences.loadLastSelectedDurationMillis(application.applicationContext),
        ).also {
            it.setDynamicTuningEnabled(SessionDurationPreferences.loadDynamicTuningEnabled(application.applicationContext))
            it.setDynamicTuningAllowsWeak(SessionDurationPreferences.loadDynamicTuningAllowsWeak(application.applicationContext))
        }
    }

    fun beginCalibration() {
        diagnostics.record("begin calibration")
        series.reset()
        stressPreDecision = null
        stressPostDecision = null
        postCheckInEligible = false
        finalizedSessionId = null
        session.calibrate()
    }

    fun saveStressPre(value: Int) {
        stressPreDecision = StressCheckIn.answered(value)
    }

    fun skipStressPre() {
        stressPreDecision = StressCheckIn.skipped
    }

    fun saveStressPost(value: Int) {
        val id = finalizedSessionId ?: return
        stressPostDecision = StressCheckIn.answered(value)
        historyStore.updateStress(id, post = stressPostDecision)
        history = historyStore.load()
    }

    fun skipStressPost() {
        val id = finalizedSessionId ?: return
        stressPostDecision = StressCheckIn.skipped
        historyStore.updateStress(id, post = stressPostDecision)
        history = historyStore.load()
    }

    fun selectSessionDuration(durationMillis: Long) {
        if (session.setSessionDuration(durationMillis)) {
            SessionDurationPreferences.saveLastSelectedDurationMillis(getApplication(), durationMillis)
        }
    }

    fun selectSessionSeries(count: Int) {
        if (series.select(count)) {
            SessionDurationPreferences.saveSessionSeriesCount(getApplication(), count)
            revision++
        }
    }

    fun startSession() {
        series.begin()
        session.start()
    }

    /** Starts only an already-planned next session after its fresh READY gate has elapsed. */
    fun startNextSeriesSessionWhenReady() {
        if (series.consumeAutomaticStart(session.canStart)) {
            diagnostics.record("series auto-start session=${series.currentSessionNumber}/${series.selectedCount}")
            session.start()
        }
    }

    fun setDynamicTuningEnabled(enabled: Boolean) {
        if (session.setDynamicTuningEnabled(enabled)) {
            SessionDurationPreferences.saveDynamicTuningEnabled(getApplication(), enabled)
        }
    }

    fun setDynamicTuningAllowsWeak(allowed: Boolean) {
        if (session.setDynamicTuningAllowsWeak(allowed)) {
            SessionDurationPreferences.saveDynamicTuningAllowsWeak(getApplication(), allowed)
        }
    }

    fun cancelSession() {
        series.cancel()
        postCheckInEligible = false
        session.stop()
    }

    fun deleteHistory(sessionId: String) { historyStore.delete(sessionId); history = historyStore.load() }
    fun clearHistory() { historyStore.clear(); history = historyStore.load() }

    override fun onCleared() {
        diagnostics.record("view model cleared stage=${session.stage}")
        session.dispose()
        mainHandler.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
