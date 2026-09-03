package com.example.pulsebreathwear.presentation

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.pulsebreathwear.R
import com.example.pulsebreathwear.presentation.theme.PulseBreathWearTheme
import com.example.pulsebreathwear.sensor.FakeSensorDataSource
import com.example.pulsebreathwear.sensor.FakeSensorFrame
import com.example.pulsebreathwear.sensor.FakeSensorScenario
import com.example.pulsebreathwear.sensor.SensorSampleRequest
import com.example.pulsebreathwear.sensor.SensorSignalQuality
import com.example.pulsebreathwear.session.BreathingPhase
import com.example.pulsebreathwear.session.BreathingSessionConfig
import com.example.pulsebreathwear.session.BreathingSessionState
import kotlinx.coroutines.delay

class DebugDiagnosticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseBreathWearTheme {
                AppScaffold {
                    ScreenScaffold { contentPadding ->
                        LiveFakeSensorDiagnostics(
                            modifier = Modifier.padding(contentPadding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LiveFakeSensorDiagnostics(modifier: Modifier = Modifier) {
    val dataSource = remember { FakeSensorDataSource() }
    val startedAtMillis = remember { SystemClock.elapsedRealtime() }
    var nowMillis by remember { mutableLongStateOf(startedAtMillis) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = SystemClock.elapsedRealtime()
            delay(500L)
        }
    }

    val elapsedMillis = nowMillis - startedAtMillis
    val breathingSnapshot =
        BreathingSessionState()
            .start(startedAtMillis)
            .snapshot(nowMillis, BreathingSessionConfig())
    val frame =
        dataSource.frameAt(
            SensorSampleRequest(
                monotonicTimestampMillis = nowMillis,
                sessionElapsedMillis = elapsedMillis,
                breathingPhase = breathingSnapshot.phase,
                phaseProgress = breathingSnapshot.phaseProgress,
            ),
        )

    FakeSensorDiagnosticsScreen(
        frame = frame,
        breathingPhase = breathingSnapshot.phase,
        modifier = modifier,
    )
}

@Composable
internal fun FakeSensorDiagnosticsScreen(
    frame: FakeSensorFrame,
    breathingPhase: BreathingPhase,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.simulated_data),
            color = Color(0xFFFFB4AB),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = scenarioLabel(frame.scenario),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text =
                frame.sample.beatsPerMinute?.let {
                    stringResource(R.string.bpm_value, it)
                } ?: stringResource(R.string.bpm_unavailable),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text =
                frame.sample.ibiMillis.firstOrNull()?.let {
                    stringResource(R.string.ibi_value, it)
                } ?: stringResource(R.string.ibi_unavailable),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = qualityLabel(frame.sample.quality),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text =
                when (breathingPhase) {
                    BreathingPhase.INHALE -> stringResource(R.string.phase_inhale)
                    BreathingPhase.EXHALE -> stringResource(R.string.phase_exhale)
                },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun scenarioLabel(scenario: FakeSensorScenario): String =
    when (scenario) {
        FakeSensorScenario.CALM -> stringResource(R.string.scenario_calm)
        FakeSensorScenario.RESPIRATORY_SINUS_ARRHYTHMIA -> stringResource(R.string.scenario_rsa)
        FakeSensorScenario.MOTION_ARTIFACT -> stringResource(R.string.scenario_motion)
        FakeSensorScenario.SIGNAL_LOSS -> stringResource(R.string.scenario_loss)
        FakeSensorScenario.RECOVERY -> stringResource(R.string.scenario_recovery)
    }

@Composable
private fun qualityLabel(quality: SensorSignalQuality): String =
    when (quality) {
        SensorSignalQuality.GOOD -> stringResource(R.string.quality_good)
        SensorSignalQuality.MOTION_ARTIFACT -> stringResource(R.string.quality_motion)
        SensorSignalQuality.SIGNAL_LOST -> stringResource(R.string.quality_lost)
        SensorSignalQuality.RECOVERING -> stringResource(R.string.quality_recovering)
    }
