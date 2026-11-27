package com.grabber.ui.feature.digital

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.model.ModulationMode
import com.grabber.ui.designsystem.AppCheckbox
import com.grabber.ui.designsystem.AppSubtleDivider
import com.grabber.ui.designsystem.AppSwitch
import com.grabber.ui.designsystem.AppTooltip
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.SecondaryButton

/**
 * Riconoscimento automatico del modo (§6 del piano): interruttore, modo suggerito con confidenza e
 * azioni rapide. L'avvio automatico riguarda solo i decoder locali (decisione D3).
 */
@Composable
internal fun RecognitionHeader(controller: KiwiAppController, onShowMode: (com.grabber.model.digital.DigitalMode) -> Unit) {
    val settings by controller.settings.collectAsState()
    val suggestion by controller.modeSuggestion.collectAsState()
    val currentMode by controller.currentMode.collectAsState()
    val passband by controller.currentPassband.collectAsState()
    val enabled = settings.modeRecognitionEnabled

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GrabberColors.Secondary)
            AppTooltip("Analizza l'audio e propone il modo più probabile (FSK, NAVTEX, CW, FAX, FT8…). Serve qualche secondo di segnale.") {
                Text("Riconoscimento", fontSize = 10.sp, color = GrabberColors.TextSecondary)
            }
            AppSwitch(
                checked = enabled,
                onCheckedChange = { controller.setModeRecognition(it, settings.modeRecognitionAutoStart) },
                size = ControlSize.Compact
            )
        }
        if (enabled) {
            AppCheckbox(
                checked = settings.modeRecognitionAutoStart,
                onCheckedChange = { controller.setModeRecognition(true, it) },
                text = "Avvio automatico (solo decoder nell'app, ≥ 90%)",
                size = ControlSize.Compact
            )
            val current = suggestion
            val narrow = passband.widthHz < 1000
            Text(
                text = when {
                    current != null -> "Suggerito: ${current.label} · ${current.percent}%"
                    narrow -> "Banda stretta: allargala per analizzare"
                    else -> "Suggerito: nessuno"
                },
                fontSize = 11.sp,
                color = if (current != null) GrabberColors.Success else GrabberColors.TextSecondary
            )
            if (current != null && current.detail.isNotBlank()) {
                Text(current.detail, fontSize = 9.sp, color = GrabberColors.TextSecondary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val mode = current?.mode
                if (mode != null) {
                    SecondaryButton(
                        text = "Avvia",
                        onClick = {
                            onShowMode(mode)
                            controller.startDigitalDecoder(mode, tuneForMode = false)
                        },
                        icon = Icons.Default.PlayArrow,
                        size = ControlSize.Mini
                    )
                }
                if (narrow || currentMode == ModulationMode.CW) {
                    SecondaryButton(
                        text = "Allarga banda",
                        onClick = controller::tuneForAnalysis,
                        icon = Icons.Default.SettingsEthernet,
                        size = ControlSize.Mini
                    )
                }
            }
        }
        AppSubtleDivider()
    }
}
