package com.grabber.ui.feature.console

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabber.app.KiwiAppController
import com.grabber.model.ConnectionState
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberShapes
import com.grabber.ui.feature.spectrum.WaterfallAndSpectrumCard

/**
 * Console radio sempre visibile: accensione e VFO a sinistra, mini spettro al centro,
 * S-meter, volume e gate a destra.
 */
@Composable
fun RadioConsole(
    controller: KiwiAppController,
    serverAddress: String,
    currentStep: FrequencyStep,
    onStepChange: (FrequencyStep) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentFrequency by controller.currentFrequencyKhz.collectAsState()
    val currentMode by controller.currentMode.collectAsState()
    val connectionState by controller.connectionState.collectAsState()

    Surface(
        modifier = modifier,
        color = GrabberColors.Surface,
        shape = RoundedCornerShape(GrabberShapes.panel),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PowerRow(
                    connectionState = connectionState,
                    serverAddress = serverAddress,
                    onConnect = controller::connect,
                    onDisconnect = controller::disconnect
                )
                VfoDisplayCard(
                    frequencyKhz = currentFrequency,
                    canTune = connectionState is ConnectionState.Connected,
                    currentStep = currentStep,
                    onTune = { controller.tune(it) },
                    onStepDelta = controller::stepFrequency,
                    onStepChange = onStepChange
                )
                ModeAndBandsCard(
                    currentMode = currentMode,
                    onSelectMode = controller::setMode,
                    onSelectPreset = { controller.tune(it.frequencyKhz, it.defaultMode) }
                )
            }
            Box(modifier = Modifier.weight(1.7f).fillMaxHeight()) {
                WaterfallAndSpectrumCard(
                    controller = controller,
                    isMiniView = true,
                    expandWaterfall = true,
                    showScaleSummary = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ConsoleAudioPanel(controller = controller, modifier = Modifier.weight(0.9f))
        }
    }
}

/** Pulsante di accensione (verde connesso, giallo in connessione, rosso spento) e indirizzo del ricevitore. */
@Composable
private fun PowerRow(
    connectionState: ConnectionState,
    serverAddress: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val connectionInProgress = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Reconnecting
    val connected = connectionState is ConnectionState.Connected
    val powerColor = when {
        connected -> GrabberColors.Success
        connectionInProgress -> GrabberColors.Warning
        else -> GrabberColors.Error
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = if (connected) onDisconnect else onConnect,
            enabled = !connectionInProgress,
            modifier = Modifier.size(32.dp).clip(CircleShape).background(powerColor.copy(alpha = 0.18f))
        ) {
            Icon(
                Icons.Default.PowerSettingsNew,
                contentDescription = if (connected) "Spegni ricevitore" else "Accendi ricevitore",
                tint = powerColor,
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            text = serverAddress,
            style = MaterialTheme.typography.labelMedium,
            color = GrabberColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
