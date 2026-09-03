package pl.pulsebreath.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun PulseBreathWearTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(content = content)
}
