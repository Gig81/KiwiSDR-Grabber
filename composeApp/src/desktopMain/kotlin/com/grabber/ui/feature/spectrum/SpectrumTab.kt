package com.grabber.ui.feature.spectrum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grabber.app.KiwiAppController
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberShapes

/**
 * Scheda spettro FFT e waterfall.
 *
 * Questa scheda offre la vista estesa ad alta risoluzione dello spettrometro FFT e del
 * waterfall 2D a scorrimento temporale, riunendo tutti i comandi avanzati:
 * - Zoom in / Zoom out e visualizzazione span di frequenza
 * - Centraggio VFO e inseguimento automatico ("Auto-Follow VFO")
 * - Selezione della palette di colori della cascata
 * - Controllo Play / Pausa del rendering e pulizia dello storico
 * - Regolazione in tempo reale della dinamica del segnale: Floor dBm (rumore) e Ceiling dBm (segnale)
 * - Selezione della velocità di aggiornamento della cascata (1 Hz, 2 Hz, 4 Hz)
 * - Sintonia rapida tramite "Click-to-Tune" e trascinamento
 * - Riepilogo frequenze (Start, Centro, VFO, Stop e risoluzione RBW)
 *
 * Tutti i comandi sono sincronizzati con il KiwiAppController e si riflettono
 * istantaneamente anche sul mini spettro fisso presente nella console radio superiore.
 */
@Composable
fun SpectrumTab(
    controller: KiwiAppController,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = GrabberColors.Surface,
        shape = RoundedCornerShape(GrabberShapes.panel),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        WaterfallAndSpectrumCard(
            controller = controller,
            isFullScreen = true,
            expandWaterfall = true,
            showScaleSummary = true,
            isMiniView = false,
            showDynamicRangeControls = true,
            modifier = Modifier.fillMaxSize().padding(6.dp)
        )
    }
}
