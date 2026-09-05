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
import pl.pulsebreath.wear.history.*
import java.util.UUID
import androidx.compose.runtime.mutableStateOf

internal class SamsungSessionViewModel(application: Application) : AndroidViewModel(application) {
    var revision by mutableLongStateOf(0L)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val historyStore = SessionHistoryStore(application.noBackupFilesDir.resolve("guided-session-history.json"))
    var history by mutableStateOf(historyStore.load())
        private set
    var stressPreDecision by mutableStateOf<StressCheckIn?>(null)
        private set
    var stressPostDecision by mutableStateOf<StressCheckIn?>(null)
        private set
    var postCheckInEligible by mutableStateOf(false)
        private set
    private var activeSessionId: String? = null
    private var finalizedSessionId: String? = null
    val session = GuidedSessionCoordinator(
        sourceFactory = { SamsungSensorDataSource(application.applicationContext) },
        clock = android.os.SystemClock::elapsedRealtime,
        dispatch = { action -> mainHandler.post(action) },
        changed = { revision++ },
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
                stressPre = stressPreDecision?.value,
                stressPreAnswered = stressPreDecision?.answered == true,
            ))
            history = historyStore.load(recoverInterrupted = false)
        },
        finalized = { activeMillis, status ->
            activeSessionId?.let { id ->
                historyStore.finalize(id, System.currentTimeMillis(), activeMillis,
                    if (status == BreathingSessionStatus.COMPLETED) SessionOutcome.COMPLETED else SessionOutcome.STOPPED)
                finalizedSessionId = id
                postCheckInEligible = true
                activeSessionId = null
                history = historyStore.load()
            }
        },
        initialSessionDurationMillis = SessionDurationPreferences.loadLastSelectedDurationMillis(application.applicationContext),
    )

    fun beginCalibration() {
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

    fun deleteHistory(sessionId: String) { historyStore.delete(sessionId); history = historyStore.load() }
    fun clearHistory() { historyStore.clear(); history = historyStore.load() }

    override fun onCleared() {
        session.dispose()
        mainHandler.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
