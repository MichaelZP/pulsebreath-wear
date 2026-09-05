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

internal class SamsungSessionViewModel(application: Application) : AndroidViewModel(application) {
    var revision by mutableLongStateOf(0L)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    val session = GuidedSessionCoordinator(
        sourceFactory = { SamsungSensorDataSource(application.applicationContext) },
        clock = android.os.SystemClock::elapsedRealtime,
        dispatch = { action -> mainHandler.post(action) },
        changed = { revision++ },
        initialSessionDurationMillis = SessionDurationPreferences.loadLastSelectedDurationMillis(application.applicationContext),
    )

    fun selectSessionDuration(durationMillis: Long) {
        if (session.setSessionDuration(durationMillis)) {
            SessionDurationPreferences.saveLastSelectedDurationMillis(getApplication(), durationMillis)
        }
    }

    override fun onCleared() {
        session.dispose()
        mainHandler.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
