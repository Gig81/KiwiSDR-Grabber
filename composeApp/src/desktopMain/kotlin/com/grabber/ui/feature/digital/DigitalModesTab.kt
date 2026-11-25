package com.grabber.ui.feature.digital

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grabber.app.KiwiAppController
import com.grabber.model.digital.DigitalMode
import com.grabber.ui.designsystem.FeatureLockedOverlay

/**
 * Scheda "Modi digitali": a sinistra l'elenco dei modi raggruppati per sorgente (server, app) con
 * disponibilità e stato, a destra intestazione, impostazioni e uscita del modo scelto.
 * Tutta la funzione richiede la licenza (decisione D1): senza attivazione la scheda è coperta.
 */
@Composable
fun DigitalModesTab(
    controller: KiwiAppController,
    isMachineLicensed: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by controller.settings.collectAsState()
    val modes by controller.digitalModes.collectAsState()
    val decoderState by controller.digitalDecoderState.collectAsState()
    var selected by remember { mutableStateOf(settings.lastDigitalMode ?: DigitalMode.FT8) }

    // Il modo attivo resta selezionato anche se il decoder è stato avviato altrove (es. dopo un riavvio della scheda).
    LaunchedEffect(decoderState.activeMode) {
        decoderState.activeMode?.let { selected = it }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DigitalModeList(
                modes = modes,
                selected = selected,
                activeMode = decoderState.activeMode,
                decoderState = decoderState,
                onSelect = { selected = it },
                modifier = Modifier.width(220.dp).fillMaxHeight(),
                header = { RecognitionHeader(controller, onShowMode = { selected = it }) }
            )
            DigitalModeDetail(
                controller = controller,
                mode = selected,
                availability = modes.firstOrNull { it.mode == selected },
                params = settings.digitalModeParams,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        if (!isMachineLicensed) {
            FeatureLockedOverlay(
                message = "Decoder dei modi digitali, riconoscimento automatico e autocentraggio sono funzioni avanzate.",
                onActivate = onActivate
            )
        }
    }
}
