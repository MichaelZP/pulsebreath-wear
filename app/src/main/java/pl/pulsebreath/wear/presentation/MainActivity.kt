package pl.pulsebreath.wear.presentation

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import pl.pulsebreath.wear.presentation.theme.PulseBreathWearTheme
import pl.pulsebreath.wear.session.BreathingPhase
import pl.pulsebreath.wear.session.BreathingSessionConfig
import pl.pulsebreath.wear.session.BreathingSessionState
import pl.pulsebreath.wear.session.BreathingSessionStatus
import kotlinx.coroutines.delay

private const val COMPLETION_SCREEN_GRACE_MILLIS = 5_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseBreathApp()
        }
    }
}

internal fun interface MonotonicTimeSource {
    fun nowMillis(): Long
}

private object SystemMonotonicTimeSource : MonotonicTimeSource {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

@Composable
internal fun PulseBreathApp(
    config: BreathingSessionConfig = BreathingSessionConfig(),
    timeSource: MonotonicTimeSource = SystemMonotonicTimeSource,
) {
    var sessionState by remember(config) { mutableStateOf(BreathingSessionState()) }
    var nowMillis by remember(config) { mutableLongStateOf(timeSource.nowMillis()) }

    LaunchedEffect(sessionState.status, config, timeSource) {
        while (sessionState.status == BreathingSessionStatus.RUNNING) {
            withFrameNanos { }
            val tickMillis = timeSource.nowMillis()
            nowMillis = tickMillis
            sessionState = sessionState.advance(tickMillis, config)
        }
    }

    val snapshot = sessionState.snapshot(nowMillis, config)
    val rootView = LocalView.current
    val shouldKeepScreenOn =
        sessionState.status == BreathingSessionStatus.RUNNING ||
            sessionState.status == BreathingSessionStatus.COMPLETED

    DisposableEffect(rootView, shouldKeepScreenOn) {
        rootView.keepScreenOn = shouldKeepScreenOn
        onDispose {
            rootView.keepScreenOn = false
        }
    }

    LaunchedEffect(rootView, sessionState.status) {
        if (sessionState.status == BreathingSessionStatus.COMPLETED) {
            delay(COMPLETION_SCREEN_GRACE_MILLIS)
            rootView.keepScreenOn = false
        }
    }

    val hapticFeedback = LocalHapticFeedback.current
    var lastHapticPhase by remember { mutableStateOf<BreathingPhase?>(null) }

    LaunchedEffect(sessionState.status, snapshot.phase) {
        if (sessionState.status == BreathingSessionStatus.RUNNING) {
            if (snapshot.phase != lastHapticPhase) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                lastHapticPhase = snapshot.phase
            }
        } else if (sessionState.status != BreathingSessionStatus.PAUSED) {
            lastHapticPhase = null
        }
    }

    PulseBreathWearTheme {
        AppScaffold {
            ScreenScaffold { contentPadding ->
                BreathingSessionScreen(
                    snapshot = snapshot,
                    onStart = {
                        val timestamp = timeSource.nowMillis()
                        nowMillis = timestamp
                        sessionState = sessionState.start(timestamp)
                    },
                    onPause = {
                        val timestamp = timeSource.nowMillis()
                        nowMillis = timestamp
                        sessionState = sessionState.pause(timestamp, config)
                    },
                    onResume = {
                        val timestamp = timeSource.nowMillis()
                        nowMillis = timestamp
                        sessionState = sessionState.resume(timestamp)
                    },
                    onCancel = {
                        val timestamp = timeSource.nowMillis()
                        nowMillis = timestamp
                        sessionState = sessionState.cancel(timestamp, config)
                    },
                    onReset = {
                        nowMillis = timeSource.nowMillis()
                        sessionState = sessionState.reset()
                    },
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
    }
}

@WearPreviewDevices
@Composable
private fun DefaultPreview() {
    PulseBreathApp()
}
