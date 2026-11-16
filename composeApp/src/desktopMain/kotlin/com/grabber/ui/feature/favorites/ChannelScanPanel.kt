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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabber.app.ChannelScanState
import com.grabber.app.SCAN_DWELL_SECONDS_RANGE
import com.grabber.ui.designsystem.AppCheckbox
import com.grabber.ui.designsystem.AppDivider
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.AppScrollableColumn
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
 * Scansione dei canali spuntati: stato, tempo di ascolto per canale, ripresa automatica
 * dopo il segnale e selezione rapida. Funzione riservata alla licenza.
 */
@Composable
internal fun ChannelScanPanel(
    scanState: ChannelScanState,
    selectedCount: Int,
    dwellSeconds: Int,
    autoResume: Boolean,
    isMachineLicensed: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDwellChange: (Int) -> Unit,
    onAutoResumeChange: (Boolean) -> Unit,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (badgeText, badgeColor) = when (scanState) {
        ChannelScanState.Idle -> "FERMA" to GrabberColors.TextDisabled
        is ChannelScanState.Scanning -> "${scanState.position}/${scanState.total}" to GrabberColors.Primary
        is ChannelScanState.SignalFound -> "SEGNALE" to GrabberColors.Success
    }
    val statusText = when (scanState) {
        ChannelScanState.Idle -> "$selectedCount canali selezionati"
        is ChannelScanState.Scanning -> "Ascolto: ${scanState.channel.name}"
        is ChannelScanState.SignalFound -> "Segnale su: ${scanState.channel.name}"
    }

    AppPanel(
        modifier = modifier,
        title = "SCANSIONE CANALI",
        icon = Icons.Default.Autorenew,
        size = ControlSize.Compact,
        contentSpacing = 6.dp,
        actions = { StatusBadge(text = badgeText, color = badgeColor) },
        overlay = if (isMachineLicensed) {
            null
        } else {
            { FeatureLockedOverlay(message = "La scansione dei canali richiede l'attivazione.", onActivate = onActivate) }
        }
    ) {
        AppDivider()
        AppScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = statusText,
                style = GrabberTypography.label,
                color = if (scanState is ChannelScanState.SignalFound) GrabberColors.Success else GrabberColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (scanState == ChannelScanState.Idle) {
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
    }
}
