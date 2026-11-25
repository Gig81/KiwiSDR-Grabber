package com.grabber.ui.feature.digital

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.kiwi.ext.ExtensionClient
import com.grabber.model.digital.DecoderOutputKind
import com.grabber.model.digital.DecoderState
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeAvailability
import com.grabber.model.digital.DigitalModeCatalog
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.DigitalPreset
import com.grabber.ui.designsystem.AppMenuButton
import com.grabber.ui.designsystem.AppNotice
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.PrimaryButton
import com.grabber.ui.designsystem.SecondaryButton
import com.grabber.ui.designsystem.StatusBadge
import java.util.Locale

/** Dettagli del modo scelto: intestazione con stato, frequenze note, parametri, comandi e uscita. */
@Composable
internal fun DigitalModeDetail(
    controller: KiwiAppController,
    mode: DigitalMode,
    availability: DigitalModeAvailability?,
    params: DigitalModeParams,
    modifier: Modifier = Modifier
) {
    val decoderState by controller.digitalDecoderState.collectAsState()
    val metrics by controller.decoderMetrics.collectAsState()
    val currentMode by controller.currentMode.collectAsState()
    val passband by controller.currentPassband.collectAsState()
    val appLimit by controller.receiverAppConnectionLimit.collectAsState()
    val isActive = decoderState.activeMode == mode
    val presets = remember(mode) { DigitalModeCatalog.presets(mode) }
    var preset by remember(mode) { mutableStateOf(presets.firstOrNull()) }
    val compatible = remember(mode, currentMode, passband, params) { controller.isTuningCompatible(mode) }

    AppPanel(
        modifier = modifier,
        title = mode.displayName.uppercase(Locale.ITALIAN),
        icon = Icons.Default.Tune,
        size = ControlSize.Compact,
        contentSpacing = 6.dp,
        titleAccessory = {
            StatusBadge(mode.source.label, color = GrabberColors.Secondary, size = ControlSize.Compact)
        },
        actions = { DecoderStatusChip(if (isActive) decoderState else DecoderState.Idle) }
    ) {
        Text(mode.description, fontSize = 10.sp, color = GrabberColors.TextSecondary)

        // Frequenze note e comandi principali.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val current = preset
            if (current != null) {
                AppMenuButton(
                    selected = current,
                    options = presets,
                    label = DigitalPreset::label,
                    onSelect = { preset = it },
                    size = ControlSize.Dense
                )
                SecondaryButton(
                    text = "Sintonizza",
                    onClick = { controller.tuneForDigitalMode(mode, current.frequencyKhz) },
                    icon = Icons.Default.Tune,
                    size = ControlSize.Dense
                )
            }
            Box(modifier = Modifier.weight(1f))
            if (!isActive) {
                PrimaryButton(
                    text = "Avvia",
                    onClick = { controller.startDigitalDecoder(mode, tuneForMode = true, presetKhz = current?.frequencyKhz) },
                    icon = Icons.Default.PlayArrow,
                    size = ControlSize.Dense,
                    enabled = availability?.isAvailable != false
                )
                SecondaryButton(
                    text = "Avvia qui",
                    onClick = { controller.startDigitalDecoder(mode, tuneForMode = false) },
                    size = ControlSize.Dense,
                    enabled = availability?.isAvailable != false
                )
            } else {
                PrimaryButton(
                    text = "Ferma",
                    onClick = controller::stopDigitalDecoder,
                    icon = Icons.Default.Stop,
                    size = ControlSize.Dense,
                    containerColor = GrabberColors.Error
                )
            }
            SecondaryButton("Pulisci", onClick = controller::clearDecodedOutput, icon = Icons.Default.Delete, size = ControlSize.Dense)
            SecondaryButton("Salva", onClick = { controller.saveDecodedOutput() }, icon = Icons.Default.Save, size = ControlSize.Dense)
        }

        DigitalModeParamsPanel(mode = mode, params = params, onChange = controller::updateDigitalModeParams)

        // Avvisi: non disponibile, rifiuti del ricevitore, sintonia non adatta.
        availability?.reason?.let { AppNotice(it, GrabberColors.Warning, Icons.Default.Info) }
        if (mode.isServer && appLimit != null && appLimit in 1..2) {
            AppNotice(
                "Il ricevitore ammette $appLimit connessioni per le app: con audio e waterfall attivi il decoder potrebbe essere rifiutato.",
                GrabberColors.TextSecondary,
                Icons.Default.Info
            )
        }
        if (isActive) {
            when (val state = decoderState) {
                is DecoderState.Unavailable -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppNotice(state.message, GrabberColors.Error, Icons.Default.Warning)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.message == ExtensionClient.MESSAGE_NOT_ALLOWED) {
                            SecondaryButton(
                                text = "Libera una connessione",
                                onClick = controller::freeConnectionForDecoder,
                                icon = Icons.Default.LinkOff,
                                size = ControlSize.Dense
                            )
                        }
                        SecondaryButton(
                            text = "Riprova",
                            onClick = { controller.startDigitalDecoder(mode, tuneForMode = false) },
                            icon = Icons.Default.Refresh,
                            size = ControlSize.Dense
                        )
                    }
                }
                is DecoderState.Error -> AppNotice(state.message, GrabberColors.Warning, Icons.Default.Warning)
                else -> Unit
            }
            if (!compatible) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppNotice(
                        "Modo o banda passante non adatti a ${mode.displayName}.",
                        GrabberColors.Warning,
                        Icons.Default.Warning,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton("Ripristina", onClick = controller::restoreTuningForDecoder, size = ControlSize.Dense)
                }
            }
        }

        DecoderIndicators(metrics = metrics, visible = isActive)

        val outputModifier = Modifier.fillMaxWidth().weight(1f)
        when (mode.output) {
            DecoderOutputKind.IMAGE -> DecoderImageOutput(controller, mode, isActive, outputModifier)
            else -> DecoderItemsOutput(controller, mode, params, isActive, outputModifier)
        }
    }
}
