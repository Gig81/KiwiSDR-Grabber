package com.grabber.ui.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabber.app.FavoriteFrequency
import com.grabber.app.KiwiAppController
import com.grabber.app.SCAN_DWELL_SECONDS_RANGE
import com.grabber.app.ScanKind
import com.grabber.app.ScanState
import com.grabber.app.SettingsData
import com.grabber.ui.designsystem.AppCheckbox
import com.grabber.ui.designsystem.AppDivider
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.AppScrollableColumn
import com.grabber.ui.designsystem.AppSegmentedControl
import com.grabber.ui.designsystem.AppSlider
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.DangerButton
import com.grabber.ui.designsystem.FeatureLockedOverlay
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberTypography
import com.grabber.ui.designsystem.PrimaryButton
import com.grabber.ui.designsystem.SecondaryButton
import com.grabber.ui.designsystem.StatusBadge
import kotlin.math.roundToInt

/**
 * Scansione dei canali spuntati o di un intervallo di frequenze, con stato, tempo di ascolto,
 * ripresa automatica dopo il segnale. Una sola scansione alla volta. Funzione riservata alla licenza.
 */
@Composable
internal fun ScanPanel(
    controller: KiwiAppController,
    settings: SettingsData,
    scanState: ScanState,
    favorites: List<FavoriteFrequency>,
    visibleFavorites: List<FavoriteFrequency>,
    isMachineLicensed: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeKind = when (scanState) {
        ScanState.Idle -> null
        is ScanState.Scanning -> scanState.kind
        is ScanState.SignalFound -> scanState.kind
    }
    var selectedKind by remember { mutableStateOf(activeKind ?: ScanKind.CHANNELS) }
    // Quando parte una scansione si mostra la sua scheda; l'utente può comunque cambiare vista.
    LaunchedEffect(activeKind) { if (activeKind != null) selectedKind = activeKind }
    val shownKind = selectedKind

    val (badgeText, badgeColor) = when (scanState) {
        ScanState.Idle -> "FERMA" to GrabberColors.TextDisabled
        is ScanState.Scanning -> "${scanState.position}/${scanState.total}" to GrabberColors.Primary
        is ScanState.SignalFound -> "SEGNALE" to GrabberColors.Success
    }

    AppPanel(
        modifier = modifier,
        title = "SCANSIONE",
        icon = Icons.Default.Autorenew,
        size = ControlSize.Compact,
        contentSpacing = 6.dp,
        actions = { StatusBadge(text = badgeText, color = badgeColor) },
        overlay = if (isMachineLicensed) {
            null
        } else {
            { FeatureLockedOverlay(message = "La scansione richiede l'attivazione.", onActivate = onActivate) }
        }
    ) {
        AppSegmentedControl(
            options = listOf("Canali", "Intervallo"),
            selectedIndex = if (shownKind == ScanKind.CHANNELS) 0 else 1,
            onSelectionChange = { selectedKind = if (it == 0) ScanKind.CHANNELS else ScanKind.RANGE },
            size = ControlSize.Dense,
            fillWidth = true,
            modifier = Modifier.fillMaxWidth()
        )
        AppDivider()
        AppScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (shownKind == ScanKind.CHANNELS) {
                ChannelScanContent(
                    scanState = scanState,
                    selectedCount = favorites.count { it.scanEnabled },
                    dwellSeconds = settings.scanDwellSeconds,
                    autoResume = settings.scanAutoResume,
                    onStart = controller::startChannelScan,
                    onStop = controller::stopScan,
                    onDwellChange = controller::setScanDwellSeconds,
                    onAutoResumeChange = controller::setScanAutoResume,
                    onSelectVisible = { controller.setFavoritesScanEnabled(visibleFavorites, true) },
                    onClearSelection = { controller.setFavoritesScanEnabled(favorites, false) }
                )
            } else {
                RangeScanContent(controller = controller, settings = settings, scanState = scanState)
            }
        }
    }
}

@Composable
private fun ChannelScanContent(
    scanState: ScanState,
    selectedCount: Int,
    dwellSeconds: Int,
    autoResume: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDwellChange: (Int) -> Unit,
    onAutoResumeChange: (Boolean) -> Unit,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit
) {
    val isChannelScanActive = when (scanState) {
        is ScanState.Scanning -> scanState.kind == ScanKind.CHANNELS
        is ScanState.SignalFound -> scanState.kind == ScanKind.CHANNELS
        ScanState.Idle -> false
    }
    val statusText = when {
        scanState is ScanState.Scanning && isChannelScanActive -> "Ascolto: ${scanState.target.label}"
        scanState is ScanState.SignalFound && isChannelScanActive -> "Segnale su: ${scanState.target.label}"
        scanState != ScanState.Idle -> "È in corso la scansione dell'intervallo"
        else -> "$selectedCount canali selezionati"
    }
    Text(
        text = statusText,
        style = GrabberTypography.label,
        color = if (scanState is ScanState.SignalFound && isChannelScanActive) GrabberColors.Success else GrabberColors.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    if (!isChannelScanActive) {
        PrimaryButton(
            text = "Avvia scansione",
            onClick = onStart,
            enabled = selectedCount > 0,
            icon = Icons.Default.PlayArrow,
            size = ControlSize.Compact,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        DangerButton(
            text = "Ferma scansione",
            onClick = onStop,
            icon = Icons.Default.Stop,
            size = ControlSize.Compact,
            modifier = Modifier.fillMaxWidth()
        )
    }
    AppSlider(
        label = "Ascolto per canale",
        valueText = "$dwellSeconds s",
        value = dwellSeconds.toFloat(),
        valueRange = SCAN_DWELL_SECONDS_RANGE.first.toFloat()..SCAN_DWELL_SECONDS_RANGE.last.toFloat(),
        steps = SCAN_DWELL_SECONDS_RANGE.last - SCAN_DWELL_SECONDS_RANGE.first - 1,
        size = ControlSize.Compact,
        onValueChange = { onDwellChange(it.roundToInt()) }
    )
    AppCheckbox(
        checked = autoResume,
        onCheckedChange = onAutoResumeChange,
        text = "Riprendi dopo il segnale",
        size = ControlSize.Compact
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SecondaryButton(
            text = "Spunta elenco",
            onClick = onSelectVisible,
            icon = Icons.Default.DoneAll,
            size = ControlSize.Dense,
            modifier = Modifier.weight(1f)
        )
        SecondaryButton(
            text = "Togli spunte",
            onClick = onClearSelection,
            enabled = selectedCount > 0,
            icon = Icons.Default.ClearAll,
            size = ControlSize.Dense,
            modifier = Modifier.weight(1f)
        )
    }
}
