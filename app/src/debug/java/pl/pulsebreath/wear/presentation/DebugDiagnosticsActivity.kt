package pl.pulsebreath.wear.presentation

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
import androidx.compose.runtime.mutableStateListOf
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
import pl.pulsebreath.wear.R
import pl.pulsebreath.wear.presentation.theme.PulseBreathWearTheme
import pl.pulsebreath.wear.sensor.FakeSensorDataSource
import pl.pulsebreath.wear.sensor.FakeSensorFrame
import pl.pulsebreath.wear.sensor.FakeSensorScenario
import pl.pulsebreath.wear.sensor.SensorSampleRequest
import pl.pulsebreath.wear.sensor.SensorSample
import pl.pulsebreath.wear.sensor.SensorSignalQuality
import pl.pulsebreath.wear.signal.AlignmentAvailability
import pl.pulsebreath.wear.signal.AlignmentMetrics
import pl.pulsebreath.wear.signal.AlignmentObservation
import pl.pulsebreath.wear.signal.BreathingAlignmentAnalyzer
import pl.pulsebreath.wear.signal.HrvAnalyzer
import pl.pulsebreath.wear.signal.HrvMetrics
import pl.pulsebreath.wear.signal.HrvWindowQuality
import pl.pulsebreath.wear.session.BreathingPhase
import pl.pulsebreath.wear.session.BreathingSessionConfig
import pl.pulsebreath.wear.session.BreathingSessionState
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
    val sampleHistory = remember { mutableStateListOf<SensorSample>() }
    val alignmentHistory = remember { mutableStateListOf<AlignmentObservation>() }
    val startedAtMillis = remember { SystemClock.elapsedRealtime() }
    var nowMillis by remember { mutableLongStateOf(startedAtMillis) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = SystemClock.elapsedRealtime()
            delay(1_000L)
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
    LaunchedEffect(frame.sample.monotonicTimestampMillis) {
        sampleHistory.add(frame.sample)
        alignmentHistory.add(
            AlignmentObservation(
                sample = frame.sample,
                breathingPhase = breathingSnapshot.phase,
                phaseProgress = breathingSnapshot.phaseProgress,
            ),
        )
        val oldestAllowedMillis = frame.sample.monotonicTimestampMillis - 60_000L
        sampleHistory.removeAll { sample ->
            sample.monotonicTimestampMillis < oldestAllowedMillis
        }
        alignmentHistory.removeAll { observation ->
            observation.sample.monotonicTimestampMillis < oldestAllowedMillis
        }
    }
    val metrics = HrvAnalyzer.analyze(sampleHistory)
    val alignmentMetrics = BreathingAlignmentAnalyzer.analyze(alignmentHistory)

    FakeSensorDiagnosticsScreen(
        frame = frame,
        breathingPhase = breathingSnapshot.phase,
        metrics = metrics,
        alignmentMetrics = alignmentMetrics,
        modifier = modifier,
    )
}

@Composable
internal fun FakeSensorDiagnosticsScreen(
    frame: FakeSensorFrame,
    breathingPhase: BreathingPhase,
    metrics: HrvMetrics? = null,
    alignmentMetrics: AlignmentMetrics? = null,
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
        metrics?.let { currentMetrics ->
            Text(
                text = windowQualityLabel(currentMetrics.quality),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = stringResource(
                    R.string.ibi_event_coverage,
                    currentMetrics.ibiEventCoveragePercent,
                ),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text =
                    currentMetrics.rmssdMillis?.let {
                        stringResource(R.string.rmssd_value, it)
                    } ?: stringResource(R.string.rmssd_unavailable),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        alignmentMetrics?.let { currentAlignmentMetrics ->
            Text(
                text = alignmentLabel(currentAlignmentMetrics),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
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

@Composable
private fun windowQualityLabel(quality: HrvWindowQuality): String =
    when (quality) {
        HrvWindowQuality.ADEQUATE -> stringResource(R.string.window_quality_adequate)
        HrvWindowQuality.INSUFFICIENT -> stringResource(R.string.window_quality_insufficient)
    }

@Composable
private fun alignmentLabel(metrics: AlignmentMetrics): String =
    when (metrics.availability) {
        AlignmentAvailability.AVAILABLE ->
            stringResource(R.string.alignment_value, requireNotNull(metrics.score))

        AlignmentAvailability.INSUFFICIENT_QUALITY ->
            stringResource(R.string.alignment_insufficient_quality)

        AlignmentAvailability.INSUFFICIENT_PHASE_VARIATION ->
            stringResource(R.string.alignment_insufficient_phase_variation)

        AlignmentAvailability.NO_IBI_VARIATION ->
            stringResource(R.string.alignment_no_ibi_variation)
    }
