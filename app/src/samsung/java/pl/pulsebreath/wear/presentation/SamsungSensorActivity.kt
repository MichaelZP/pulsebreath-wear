package pl.pulsebreath.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.health.connect.HealthPermissions
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import pl.pulsebreath.wear.sensor.TimingDiagnostics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import pl.pulsebreath.wear.presentation.theme.PulseBreathWearTheme
import pl.pulsebreath.wear.sensor.SamsungSensorDataSource
import pl.pulsebreath.wear.sensor.SensorSample
import pl.pulsebreath.wear.sensor.SensorStreamState
import pl.pulsebreath.wear.sensor.SensorStreamStatus
import pl.pulsebreath.wear.signal.HrvAnalyzer
import pl.pulsebreath.wear.signal.HrvMetrics
import java.util.Locale

internal fun requiredHeartRatePermission(): String =
    if (Build.VERSION.SDK_INT >= 36) {
        HealthPermissions.READ_HEART_RATE
    } else {
        Manifest.permission.BODY_SENSORS
    }

class SamsungSensorActivity : ComponentActivity() {
    private lateinit var sensorDataSource: SamsungSensorDataSource
    private var permissionGranted by mutableStateOf(false)
    private var permissionMessage by mutableStateOf<String?>(null)
    private var streamStatus by mutableStateOf(
        SensorStreamStatus(SensorStreamState.IDLE, "Sensor is stopped."),
    )
    private var latestSample by mutableStateOf<SensorSample?>(null)
    private var latestValidIbiMillis by mutableStateOf<List<Long>>(emptyList())
    private val sessionSamples = mutableStateListOf<SensorSample>()
    private var timingDiagnostics by mutableStateOf(TimingDiagnostics())
    private var sessionGeneration = 0L

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
            permissionMessage = if (granted) {
                "Permission granted. You can start the sensor."
            } else {
                "Permission denied. Real BPM and IBI cannot be measured."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorDataSource = SamsungSensorDataSource(applicationContext)
        permissionGranted =
            checkSelfPermission(requiredHeartRatePermission()) == PackageManager.PERMISSION_GRANTED

        setContent {
            SamsungSensorApp(
                permissionGranted = permissionGranted,
                permissionMessage = permissionMessage,
                status = streamStatus,
                latestSample = latestSample,
                latestValidIbiMillis = latestValidIbiMillis,
                metrics = HrvAnalyzer.analyze(sessionSamples),
                timingDiagnostics = timingDiagnostics,
                onRequestPermission = {
                    permissionRequest.launch(requiredHeartRatePermission())
                },
                onStart = ::startSensor,
                onStop = ::stopSensor,
            )
        }
    }

    override fun onStop() {
        stopSensor()
        super.onStop()
    }

    override fun onDestroy() {
        sensorDataSource.stop()
        super.onDestroy()
    }

    private fun startSensor() {
        if (!permissionGranted) {
            permissionMessage = "Grant heart-rate access before starting."
            return
        }
        latestSample = null
        latestValidIbiMillis = emptyList()
        sessionSamples.clear()
        timingDiagnostics = TimingDiagnostics()
        val generation = ++sessionGeneration
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorDataSource.start(
            onStatus = { status -> runOnUiThread {
                if (generation == sessionGeneration) streamStatus = status
            } },
            onSample = { sample ->
                runOnUiThread {
                    if (generation != sessionGeneration) return@runOnUiThread
                    timingDiagnostics = timingDiagnostics.add(sample)
                    latestSample = sample
                    sessionSamples.add(sample)
                    val oldestAllowedMillis = sample.monotonicTimestampMillis - 60_000L
                    sessionSamples.removeAll { observedSample ->
                        observedSample.monotonicTimestampMillis < oldestAllowedMillis
                    }
                    if (sample.ibiMillis.isNotEmpty()) {
                        latestValidIbiMillis = sample.ibiMillis
                    }
                }
            },
        )
    }

    private fun stopSensor() {
        sessionGeneration++
        if (::sensorDataSource.isInitialized) {
            sensorDataSource.stop()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        streamStatus = SensorStreamStatus(SensorStreamState.IDLE, "Sensor is stopped.")
    }
}

@Composable
private fun SamsungSensorApp(
    permissionGranted: Boolean,
    permissionMessage: String?,
    status: SensorStreamStatus,
    latestSample: SensorSample?,
    latestValidIbiMillis: List<Long>,
    metrics: HrvMetrics,
    timingDiagnostics: TimingDiagnostics,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    PulseBreathWearTheme {
        AppScaffold {
            ScreenScaffold { contentPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding)
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "SAMSUNG — REAL SENSOR",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Heart-rate access is used only for the current local BPM/IBI measurement. This screen does not save or share readings.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!permissionGranted) {
                        Button(onClick = onRequestPermission) {
                            Text("Allow heart rate")
                        }
                    }
                    permissionMessage?.let {
                        Text(it, textAlign = TextAlign.Center)
                    }
                    Text(status.message, textAlign = TextAlign.Center)
                    latestSample?.let { sample ->
                        Text(
                            text = "BPM: ${sample.beatsPerMinute?.toInt() ?: "—"}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (latestValidIbiMillis.isEmpty()) {
                                "Last valid IBI: waiting"
                            } else {
                                "Last valid IBI: ${latestValidIbiMillis.joinToString()} ms"
                            },
                            textAlign = TextAlign.Center,
                        )
                        Text("Signal: ${sample.quality.name}")
                    }
                    Text(
                        text = "HRV window: ${metrics.quality.name}",
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "IBI coverage: ${metrics.ibiEventCoveragePercent.toInt()}%",
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Valid IBI: ${metrics.validIbiCount}",
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = metrics.displayRmssdMillis?.let {
                            String.format(Locale.US, "RMSSD: %.1f ms", it)
                        }
                            ?: "RMSSD: unavailable (quality or continuity)",
                        textAlign = TextAlign.Center,
                    )
                    Text("Rejected IBI entries: ${metrics.rejectedIbiCount}")
                    if (status.state == SensorStreamState.CONNECTING ||
                        status.state == SensorStreamState.TRACKING
                    ) {
                        Button(onClick = onStop) {
                            Text("Stop sensor")
                        }
                    } else if (permissionGranted) {
                        Button(onClick = onStart) {
                            Text("Start sensor")
                        }
                    }
                    TimingDiagnosticsPanel(timingDiagnostics, status.state)
                }
            }
        }
    }
}

@Composable
private fun TimingDiagnosticsPanel(summary: TimingDiagnostics, state: SensorStreamState) {
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val running = state == SensorStreamState.TRACKING || state == SensorStreamState.CONNECTING
    LaunchedEffect(running, summary) {
        now = SystemClock.elapsedRealtime()
        while (running) {
            delay(1_000)
            now = SystemClock.elapsedRealtime()
        }
    }
    Text("Timing diagnostics — session only", textAlign = TextAlign.Center)
    Text("Adaptation disabled: beat timing unverified", textAlign = TextAlign.Center)
    Text("Points / groups: ${summary.points} / ${summary.callbackGroups}")
    Text("Multi-point groups: ${summary.multiPointGroups}; max: ${summary.maxBatchSize}", textAlign = TextAlign.Center)
    Text("Empty companions: ${summary.emptyCompanions}")
    Text("Missing metadata: ${summary.missingMetadata}")
    Text("Ordering flags: ${summary.orderingErrors}")
    Text("SDK delta: ${summary.minSdkDelta ?: "—"}..${summary.maxSdkDelta ?: "—"} ms", textAlign = TextAlign.Center)
    Text("Receipt delta: ${summary.minReceiptDelta ?: "—"}..${summary.maxReceiptDelta ?: "—"} ms", textAlign = TextAlign.Center)
    Text(if (running) "Receipt age: ${summary.receiptAge(now) ?: "—"} ms" else "Stopped — retained summary")
    Text("Last timing block: ${summary.lastReason ?: "no data"}", textAlign = TextAlign.Center)
    Text("Delta is not latency. No export or storage.", textAlign = TextAlign.Center)
}
