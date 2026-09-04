package pl.pulsebreath.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.health.connect.HealthPermissions
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import kotlinx.coroutines.delay
import pl.pulsebreath.wear.presentation.theme.PulseBreathWearTheme
import pl.pulsebreath.wear.sensor.SamsungSensorDataSource
import pl.pulsebreath.wear.sensor.SensorStreamState
import pl.pulsebreath.wear.sensor.TimingDiagnostics
import pl.pulsebreath.wear.session.GuidedSessionCoordinator
import pl.pulsebreath.wear.session.GuidedStage
import java.util.Locale

internal fun requiredHeartRatePermission(): String =
    if (Build.VERSION.SDK_INT >= 36) HealthPermissions.READ_HEART_RATE else Manifest.permission.BODY_SENSORS

class SamsungSensorActivity : ComponentActivity() {
    private lateinit var session: GuidedSessionCoordinator
    private var revision by mutableLongStateOf(0L)
    private var permissionGranted by mutableStateOf(false)
    private var permissionMessage by mutableStateOf<String?>(null)
    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
            permissionMessage = if (granted) "Permission granted." else "Heart-rate permission denied."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = GuidedSessionCoordinator(
            sourceFactory = { SamsungSensorDataSource(applicationContext) },
            clock = SystemClock::elapsedRealtime,
            dispatch = { action -> runOnUiThread { action() } },
            changed = {
                revision++
                if (session.stage == GuidedStage.RUNNING || session.stage == GuidedStage.CALIBRATING ||
                    session.stage == GuidedStage.READY) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            },
        )
        permissionGranted = checkSelfPermission(requiredHeartRatePermission()) == PackageManager.PERMISSION_GRANTED
        setContent {
            // Observable revision publishes the serialized owner's current snapshot.
            @Suppress("UNUSED_VARIABLE") val currentRevision = revision
            LaunchedEffect(session.stage) {
                while (session.stage == GuidedStage.RUNNING || session.stage == GuidedStage.CALIBRATING) {
                    session.tick()
                    delay(50)
                }
            }
            PulseBreathWearTheme {
                AppScaffold {
                    ScreenScaffold { contentPadding ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(contentPadding).padding(horizontal = 28.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("SAMSUNG — REAL SENSOR", textAlign = TextAlign.Center)
                            Text("Wellness only. Readings stay in memory for this session.", textAlign = TextAlign.Center)
                            Text(session.stage.name)
                            permissionMessage?.let { Text(it) }
                            if (!permissionGranted) {
                                Button(onClick = { permissionRequest.launch(requiredHeartRatePermission()) }) {
                                    Text("Allow heart rate")
                                }
                            }
                            when (session.stage) {
                                GuidedStage.IDLE -> if (permissionGranted) {
                                    Button(onClick = session::calibrate) { Text("Sense my pace") }
                                }
                                GuidedStage.CALIBRATING -> {
                                    Text("Sensing attempt ${session.calibrationAttempt}/${GuidedSessionCoordinator.MAX_CALIBRATION_ATTEMPTS}. Breathe naturally.", textAlign = TextAlign.Center)
                                    session.estimate?.takeIf { it.usedFallback }?.let {
                                        Text("Last attempt: ${it.fallbackReason?.name ?: "fallback"}", textAlign = TextAlign.Center)
                                    }
                                    Text(session.status.message, textAlign = TextAlign.Center)
                                    Button(onClick = session::stop) { Text("Cancel") }
                                }
                                GuidedStage.READY -> {
                                    PaceDetails(session)
                                    if (session.estimate?.usedFallback == true) {
                                        Text("No usable pace after ${session.calibrationAttempt} attempts. Signal coverage was too weak; adjust fit and retry.", textAlign = TextAlign.Center)
                                        Button(onClick = session::calibrate) { Text("Try again") }
                                        Button(onClick = session::stop) { Text("Cancel") }
                                    } else {
                                        Text("Pace ready: ${session.config.cycleDurationMillis / 1000.0} s / breath")
                                        Button(onClick = session::start) { Text("Start breathing") }
                                        Button(onClick = session::stop) { Text("Cancel") }
                                    }
                                }
                                GuidedStage.RUNNING, GuidedStage.PAUSED -> {
                                    Text(if (session.stage == GuidedStage.PAUSED) "Paused" else session.cue.phase.name)
                                    Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size((30 + 70 * session.cue.breathExpansionFraction).dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape))
                                    }
                                    Text("${session.cue.remainingMillis / 1000} s left")
                                    Text(session.status.message, textAlign = TextAlign.Center)
                                    Button(onClick = {
                                        if (session.stage == GuidedStage.RUNNING) session.pause() else session.start()
                                    }) { Text(if (session.stage == GuidedStage.RUNNING) "Pause" else "Resume") }
                                    Button(onClick = session::stop) { Text("Stop") }
                                }
                                GuidedStage.SUMMARY -> {
                                    Text("Session summary")
                                    Text("Mean BPM: ${session.meanBpm?.let { String.format(Locale.US, "%.1f", it) } ?: "unavailable"}")
                                    PaceDetails(session)
                                    if (permissionGranted) Button(onClick = session::calibrate) { Text("New session") }
                                }
                            }
                            Text("Alignment: ${session.alignment.availability}", textAlign = TextAlign.Center)
                            Text("Score: ${session.alignment.score?.let { String.format(Locale.US, "%.2f", it) } ?: "unavailable"}")
                            if (session.stage == GuidedStage.CALIBRATING || session.stage == GuidedStage.RUNNING) {
                                Text("BPM: ${session.latestSample?.beatsPerMinute?.toInt() ?: "—"}")
                                Text("Live IBI: ${session.latestSample?.ibiMillis?.lastOrNull() ?: "—"} ms")
                            }
                            Text("HRV: ${session.hrv.quality}; coverage ${session.hrv.ibiEventCoveragePercent.toInt()}%", textAlign = TextAlign.Center)
                            Text("RMSSD: ${session.hrv.displayRmssdMillis?.let { String.format(Locale.US, "%.1f ms", it) } ?: "unavailable"}")
                            Text("Experimental receipt-anchored timing; live alignment is unvalidated.", textAlign = TextAlign.Center)
                            TimingDiagnosticsPanel(session.timing,
                                if (session.stage == GuidedStage.RUNNING || session.stage == GuidedStage.CALIBRATING)
                                    session.status.state else SensorStreamState.IDLE)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        session.stop()
        super.onStop()
    }

    override fun onDestroy() {
        session.stop()
        super.onDestroy()
    }
}

@Composable
private fun PaceDetails(session: GuidedSessionCoordinator) {
    session.estimate?.let {
        Text("usedFallback: ${it.usedFallback}")
        Text("Fallback reason: ${it.fallbackReason?.name ?: "none"}", textAlign = TextAlign.Center)
        Text("Estimate mode: ${it.estimateMode.name}", textAlign = TextAlign.Center)
        Text("Accepted placed calibration IBIs: ${it.acceptedIbiCount}", textAlign = TextAlign.Center)
        Text("Longest analyzed segment: ${it.analyzedIbiCount}", textAlign = TextAlign.Center)
    } ?: Text("Calibration cancelled; no pace estimate.")
}

@Composable
private fun TimingDiagnosticsPanel(summary: TimingDiagnostics, state: SensorStreamState) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
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
