package com.grabber.ui.feature.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grabber.app.KiwiAppController

/**
 * Scheda dei controlli audio: tempi dello squelch a sinistra, DSP (equalizzatore e filtri) al centro,
 * Voice Activity Detection a destra.
 * I valori sono sincronizzati in tempo reale con la console radio superiore.
 */
@Composable
fun AudioTab(
    controller: KiwiAppController,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit = {}
) {
    val settings by controller.settings.collectAsState()
    val isMachineLicensed = remember(settings.licenseKey) {
        controller.isLicenseValid(settings.licenseKey)
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AudioSquelchTimingPanel(
            controller = controller,
            modifier = Modifier.weight(0.9f).fillMaxHeight()
        )
        AudioDspPanel(
            controller = controller,
            isMachineLicensed = isMachineLicensed,
            onActivate = onActivate,
            modifier = Modifier.weight(1.2f).fillMaxHeight()
        )
        AudioVadPanel(
            controller = controller,
            isMachineLicensed = isMachineLicensed,
            onActivate = onActivate,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}
