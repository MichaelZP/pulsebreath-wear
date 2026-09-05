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
import androidx.activity.viewModels
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
import pl.pulsebreath.wear.sensor.SensorStreamState
import pl.pulsebreath.wear.sensor.TimingDiagnostics
import pl.pulsebreath.wear.session.GuidedSessionDurations
import pl.pulsebreath.wear.session.GuidedSessionCoordinator
import pl.pulsebreath.wear.session.GuidedStage
import pl.pulsebreath.wear.session.BreathingPhase
import pl.pulsebreath.wear.history.SessionHistoryRecord
import pl.pulsebreath.wear.history.SessionOutcome
import java.util.Locale

internal fun requiredHeartRatePermission(): String =
    if (Build.VERSION.SDK_INT >= 36) HealthPermissions.READ_HEART_RATE else Manifest.permission.BODY_SENSORS

class SamsungSensorActivity : ComponentActivity() {
    private val sessionViewModel: SamsungSessionViewModel by viewModels()
    private val session: GuidedSessionCoordinator
        get() = sessionViewModel.session
    private var permissionGranted by mutableStateOf(false)
    private var permissionMessage by mutableStateOf<String?>(null)
    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
            permissionMessage = if (granted) "Permission granted." else "Heart-rate permission denied."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGranted = checkSelfPermission(requiredHeartRatePermission()) == PackageManager.PERMISSION_GRANTED
        setContent {
            // Observable revision publishes the serialized owner's current snapshot.
            @Suppress("UNUSED_VARIABLE") val currentRevision = sessionViewModel.revision
            var historySelectedId by remember { mutableStateOf<String?>(null) }
            var confirmClearHistory by remember { mutableStateOf(false) }
            val haptics = remember { SessionHaptics(applicationContext) }
            val hapticEdges = remember { SessionHapticEdges() }
            LaunchedEffect(session.stage, session.cue.phase) {
                if (hapticEdges.readyTransition(session.stage == GuidedStage.READY)) haptics.ready()
                when (hapticEdges.phaseTransition(session.stage == GuidedStage.RUNNING, session.cue.phase)) {
                    BreathingPhase.INHALE -> haptics.inhale()
                    BreathingPhase.EXHALE -> haptics.exhale()
                    null -> Unit
                }
                if (session.stage != GuidedStage.RUNNING && session.stage != GuidedStage.READY) haptics.cancel()
            }
            DisposableEffect(Unit) { onDispose { haptics.cancel() } }
            LaunchedEffect(session.stage) {
                while (session.stage == GuidedStage.RUNNING || session.stage == GuidedStage.CALIBRATING || session.stage == GuidedStage.READY) {
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
                            Text("Wellness only. Raw readings stay in memory; derived session history is local.", textAlign = TextAlign.Center)
                            Button(onClick = { historySelectedId = null }) { Text("History (${sessionViewModel.history.size})") }
                            if (historySelectedId != null) {
                                sessionViewModel.history.firstOrNull { it.sessionId == historySelectedId }?.let { record ->
                                    HistoryDetail(record, onBack = { historySelectedId = null }, onDelete = {
                                        sessionViewModel.deleteHistory(record.sessionId)
                                        historySelectedId = null
                                    })
                                } ?: run { historySelectedId = null }
                            } else {
                                HistoryList(sessionViewModel.history, onSelect = { historySelectedId = it.sessionId }, onClear = { confirmClearHistory = true })
                            }
                            Text(session.stage.name)
                            session.notice?.let { Text(it, textAlign = TextAlign.Center) }
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
                                        Button(onClick = session::retryCalibration) { Text("Try again") }
                                        Button(onClick = session::stop) { Text("Cancel") }
                                    } else {
                                        Text("Pace ready: ${session.config.cycleDurationMillis / 1000.0} s / breath")
                                        Text("READY prep: ${session.readyVisibleMillis / 1000.0} / 20.0 s", textAlign = TextAlign.Center)
                                        Text("Session length", textAlign = TextAlign.Center)
                                        listOf(120L, 300L, 600L, 900L, 1_800L).forEach { seconds ->
                                            val selected = session.config.sessionDurationMillis == seconds * 1_000L
                                            Button(onClick = { sessionViewModel.selectSessionDuration(seconds * 1_000L) },
                                                enabled = !selected) {
                                                Text(if (seconds == 120L) "120 seconds" else "${seconds / 60} minutes")
                                            }
                                        }
                                        Text("Selected: ${formatDuration(session.config.sessionDurationMillis)}", textAlign = TextAlign.Center)
                                        Button(onClick = session::start, enabled = session.canStart) {
                                            Text(if (session.canStart) "Start breathing" else "Start in ${((GuidedSessionDurations.READY_PREPARATION_MILLIS - session.readyVisibleMillis).coerceAtLeast(0L) + 999L) / 1000L}s")
                                        }
                                        Button(onClick = session::stop) { Text("Cancel") }
                                    }
                                }
                                GuidedStage.RUNNING, GuidedStage.PAUSED -> {
                                    Text(if (session.stage == GuidedStage.PAUSED) {
                                        if (session.pauseReason == pl.pulsebreath.wear.session.GuidedPauseReason.BACKGROUND)
                                            "Paused: background"
                                        else "Paused"
                                    } else session.cue.phase.name)
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
                                    Text("Selected duration: ${formatDuration(session.config.sessionDurationMillis)}", textAlign = TextAlign.Center)
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
            if (confirmClearHistory) {
                Text("Clear all locally stored session summaries?", textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { sessionViewModel.clearHistory(); confirmClearHistory = false }) { Text("Clear") }
                    Button(onClick = { confirmClearHistory = false }) { Text("Cancel") }
                }
            }
        }
    }

    override fun onStop() {
        session.onBackground()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        session.onForeground()
    }
}

@Composable
private fun HistoryList(records: List<SessionHistoryRecord>, onSelect: (SessionHistoryRecord) -> Unit, onClear: () -> Unit) {
    Text("Guided history", textAlign = TextAlign.Center)
    if (records.isEmpty()) Text("No guided sessions yet.", textAlign = TextAlign.Center)
    records.forEach { record ->
        Button(onClick = { onSelect(record) }) {
            Text("${record.outcome?.name ?: "ACTIVE"} · ${formatDuration(record.activeDurationMillis)}")
        }
    }
    if (records.isNotEmpty()) Button(onClick = onClear) { Text("Clear all history") }
}

@Composable
private fun HistoryDetail(record: SessionHistoryRecord, onBack: () -> Unit, onDelete: () -> Unit) {
    Text("Session detail", textAlign = TextAlign.Center)
    Text("Outcome: ${record.outcome?.name ?: "ACTIVE"}")
    Text("Started: ${record.startedAtMillis}")
    Text("Planned: ${formatDuration(record.plannedDurationMillis)}")
    Text("Active: ${formatDuration(record.activeDurationMillis)}")
    Text("Pace: ${record.cycleMillis?.let { "${it / 1000.0} s cycle" } ?: "unavailable"}")
    Text("Mode: ${record.estimateMode ?: "—"}; confidence: ${record.confidence ?: "—"}")
    Text("Fallback: ${record.usedFallback?.toString() ?: "—"}${record.fallbackReason?.let { " ($it)" } ?: ""}", textAlign = TextAlign.Center)
    Button(onClick = onBack) { Text("Back to history") }
    Button(onClick = onDelete) { Text("Delete this session") }
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1_000L
    return if (seconds < 300L) "$seconds seconds" else "${seconds / 60} minutes"
}

@Composable
private fun PaceDetails(session: GuidedSessionCoordinator) {
    session.estimate?.let {
        Text("usedFallback: ${it.usedFallback}")
        Text("Fallback reason: ${it.fallbackReason?.name ?: "none"}", textAlign = TextAlign.Center)
        Text("Estimate mode: ${it.estimateMode.name}", textAlign = TextAlign.Center)
        Text("Confidence: ${it.confidence.name}", textAlign = TextAlign.Center)
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
    Text("Delta is not latency. No raw sensor export or storage.", textAlign = TextAlign.Center)
}
