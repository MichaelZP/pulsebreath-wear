package com.example.pulsebreathwear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.pulsebreathwear.R
import com.example.pulsebreathwear.session.BreathingPhase
import com.example.pulsebreathwear.session.BreathingSessionSnapshot
import com.example.pulsebreathwear.session.BreathingSessionStatus
import kotlin.math.ceil

@Composable
internal fun BreathingSessionScreen(
    snapshot: BreathingSessionSnapshot,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (snapshot.status) {
        BreathingSessionStatus.IDLE ->
            HomeContent(
                onStart = onStart,
                modifier = modifier,
            )

        BreathingSessionStatus.RUNNING,
        BreathingSessionStatus.PAUSED,
        ->
            ActiveSessionContent(
                snapshot = snapshot,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                modifier = modifier,
            )

        BreathingSessionStatus.COMPLETED ->
            ResultContent(
                message = stringResource(R.string.session_completed),
                actionLabel = stringResource(R.string.restart),
                onAction = onStart,
                modifier = modifier,
            )

        BreathingSessionStatus.CANCELLED ->
            ResultContent(
                message = stringResource(R.string.session_cancelled),
                actionLabel = stringResource(R.string.back),
                onAction = onReset,
                modifier = modifier,
            )
    }
}

@Composable
private fun HomeContent(
    onStart: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            maxLines = 2,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.session_summary),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CenteredButtonLabel(text = stringResource(R.string.start))
        }
    }
}

@Composable
private fun ActiveSessionContent(
    snapshot: BreathingSessionSnapshot,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val isPaused = snapshot.status == BreathingSessionStatus.PAUSED
    val phaseLabel =
        when {
            isPaused -> stringResource(R.string.paused)
            snapshot.phase == BreathingPhase.INHALE -> stringResource(R.string.inhale)
            else -> stringResource(R.string.exhale)
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = phaseLabel,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(2.dp))
        BreathingVisualizer(
            snapshot = snapshot,
            phaseLabel = phaseLabel,
        )
        Text(
            text = remainingTime(snapshot.remainingMillis),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = if (isPaused) onResume else onPause,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp),
            ) {
                CenteredButtonLabel(
                    text =
                        if (isPaused) {
                            stringResource(R.string.resume)
                        } else {
                            stringResource(R.string.pause)
                        },
                    compact = true,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCancel,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp),
            ) {
                CenteredButtonLabel(
                    text = stringResource(R.string.cancel),
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun BreathingVisualizer(
    snapshot: BreathingSessionSnapshot,
    phaseLabel: String,
) {
    val phaseColor =
        when (snapshot.phase) {
            BreathingPhase.INHALE -> MaterialTheme.colorScheme.primary
            BreathingPhase.EXHALE -> MaterialTheme.colorScheme.tertiary
        }
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val progressColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier =
            Modifier
                .size(72.dp)
                .semantics {
                    contentDescription = phaseLabel
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            current = snapshot.overallProgress,
                            range = 0f..1f,
                        )
                },
    ) {
        val strokeWidth = 5.dp.toPx()
        val radius = size.minDimension / 2f - strokeWidth
        drawCircle(
            color = trackColor,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * snapshot.overallProgress,
            useCenter = false,
            topLeft = Offset(strokeWidth, strokeWidth),
            size =
                androidx.compose.ui.geometry.Size(
                    width = size.width - 2f * strokeWidth,
                    height = size.height - 2f * strokeWidth,
                ),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        val minimumBreathRadius = size.minDimension * 0.16f
        val maximumBreathRadius = size.minDimension * 0.34f
        val breathRadius =
            minimumBreathRadius +
                (maximumBreathRadius - minimumBreathRadius) *
                snapshot.breathExpansionFraction
        drawCircle(
            color = phaseColor,
            radius = breathRadius,
            center = center,
        )
    }
}

@Composable
private fun ResultContent(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CenteredButtonLabel(text = actionLabel)
        }
    }
}

@Composable
private fun CenteredButtonLabel(
    text: String,
    compact: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 1,
        textAlign = TextAlign.Center,
        style =
            if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelLarge
            },
    )
}

@Composable
private fun remainingTime(remainingMillis: Long): String {
    val totalSeconds = ceil(remainingMillis / 1_000.0).toLong()
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return stringResource(R.string.remaining_time, minutes, seconds)
}
