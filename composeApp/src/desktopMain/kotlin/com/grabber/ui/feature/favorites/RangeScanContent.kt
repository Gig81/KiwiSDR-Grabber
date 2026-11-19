package com.grabber.ui.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.app.RangeScanLimits
import com.grabber.app.ScanHit
import com.grabber.app.ScanKind
import com.grabber.app.ScanState
import com.grabber.app.SettingsData
import com.grabber.model.ModulationMode
import com.grabber.ui.designsystem.AppCheckbox
import com.grabber.ui.designsystem.AppMenuButton
import com.grabber.ui.designsystem.AppSlider
import com.grabber.ui.designsystem.AppSubtleDivider
import com.grabber.ui.designsystem.CompactTextField
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.DangerButton
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberTypography
import com.grabber.ui.designsystem.IconButton
import com.grabber.ui.designsystem.PrimaryButton
import com.grabber.ui.designsystem.SecondaryButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun formatKhz(value: Double): String = String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

private fun parseKhz(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/**
 * Scansione di un intervallo: inizio, fine, passo e modo; tempo di ascolto per passo; segnali
 * trovati (sintonizza, aggiungi ai preferiti, escludi) e frequenze escluse.
 */
@Composable
internal fun RangeScanContent(
    controller: KiwiAppController,
    settings: SettingsData,
    scanState: ScanState
) {
    val hits by controller.rangeScanHits.collectAsState()
    val thresholdDbm by controller.squelchThresholdDbm.collectAsState()

    var startText by remember(settings.rangeScanStartKhz) { mutableStateOf(formatKhz(settings.rangeScanStartKhz)) }
    var endText by remember(settings.rangeScanEndKhz) { mutableStateOf(formatKhz(settings.rangeScanEndKhz)) }
    var stepText by remember(settings.rangeScanStepKhz) { mutableStateOf(formatKhz(settings.rangeScanStepKhz)) }
    var mode by remember(settings.rangeScanModeCode) { mutableStateOf(settings.rangeScanMode) }
    var error by remember { mutableStateOf<String?>(null) }

    val isDirty = parseKhz(startText) != settings.rangeScanStartKhz ||
        parseKhz(endText) != settings.rangeScanEndKhz ||
        parseKhz(stepText) != settings.rangeScanStepKhz ||
        mode != settings.rangeScanMode

    /** Salva i campi; restituisce true se validi. */
    fun applyFields(): Boolean {
        val start = parseKhz(startText)
        val end = parseKhz(endText)
        val step = parseKhz(stepText)
        error = if (start == null || end == null || step == null) {
            "Inserisci numeri validi in kHz (es. 26965 o 7100,5)."
        } else {
            controller.setRangeScanParameters(start, end, step, mode)
        }
        return error == null
    }

    val isRangeActive = when (scanState) {
        is ScanState.Scanning -> scanState.kind == ScanKind.RANGE
        is ScanState.SignalFound -> scanState.kind == ScanKind.RANGE
        ScanState.Idle -> false
    }
    val statusText = when {
        scanState is ScanState.Scanning && isRangeActive -> "Ascolto: ${scanState.target.label}"
        scanState is ScanState.SignalFound && isRangeActive -> "Segnale su: ${scanState.target.label}"
        scanState != ScanState.Idle -> "È in corso la scansione dei canali"
        else -> "Si ferma quando il segnale supera la soglia squelch (${thresholdDbm.roundToInt()} dBm)"
    }
    Text(
        text = statusText,
        style = GrabberTypography.label,
        color = if (scanState is ScanState.SignalFound && isRangeActive) GrabberColors.Success else GrabberColors.TextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )

    RangeField("Inizio (kHz)", startText) { startText = it; error = null }
    RangeField("Fine (kHz)", endText) { endText = it; error = null }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Passo", style = GrabberTypography.label, fontSize = 10.sp, modifier = Modifier.width(36.dp))
        CompactTextField(
            value = stepText,
            onValueChange = { stepText = it; error = null },
            modifier = Modifier.weight(1f).height(26.dp),
            fontSize = 10.sp,
            monospace = true
        )
        AppMenuButton(
            selected = mode,
            options = ModulationMode.entries,
            label = { it.displayName },
            onSelect = { mode = it; error = null },
            size = ControlSize.Dense
        )
    }
    error?.let { Text(it, fontSize = 10.sp, color = GrabberColors.Error) }
    if (isDirty) {
        SecondaryButton(
            text = "Applica intervallo",
            onClick = { applyFields() },
            icon = Icons.Default.Check,
            size = ControlSize.Dense,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (!isRangeActive) {
        PrimaryButton(
            text = "Avvia scansione intervallo",
            onClick = { if (applyFields()) controller.startRangeScan() },
            icon = Icons.Default.PlayArrow,
            size = ControlSize.Compact,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DangerButton(
                text = "Ferma",
                onClick = controller::stopScan,
                icon = Icons.Default.Stop,
                size = ControlSize.Compact,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Escludi",
                onClick = controller::excludeCurrentFromRangeScan,
                icon = Icons.Default.Block,
                size = ControlSize.Compact,
                modifier = Modifier.weight(1f)
            )
        }
    }

    val dwellSeconds = settings.rangeScanDwellMs / 1000f
    AppSlider(
        label = "Ascolto per passo",
        valueText = String.format(Locale.US, "%.1f s", dwellSeconds),
        value = dwellSeconds,
        valueRange = RangeScanLimits.MIN_DWELL_MS / 1000f..RangeScanLimits.MAX_DWELL_MS / 1000f,
        steps = (RangeScanLimits.MAX_DWELL_MS - RangeScanLimits.MIN_DWELL_MS) / 100 - 1,
        size = ControlSize.Compact,
        onValueChange = { controller.setRangeScanDwellMs((it * 10).roundToInt() * 100) }
    )
    AppCheckbox(
        checked = settings.rangeScanAutoResume,
        onCheckedChange = controller::setRangeScanAutoResume,
        text = "Riprendi dopo il segnale",
        size = ControlSize.Compact
    )

    AppSubtleDivider()
    SectionHeader(
        title = "SEGNALI TROVATI (${hits.size})",
        actionLabel = if (hits.isNotEmpty()) "Svuota" else null,
        onAction = controller::clearRangeScanHits
    )
    if (hits.isEmpty()) {
        Text("Nessun segnale in questa sessione.", fontSize = 10.sp, color = GrabberColors.TextDisabled)
    } else {
        hits.forEach { hit -> HitRow(controller, hit) }
    }

    AppSubtleDivider()
    val excluded = settings.rangeScanExcludedKhz
    SectionHeader(
        title = "FREQUENZE ESCLUSE (${excluded.size})",
        actionLabel = if (excluded.isNotEmpty()) "Svuota" else null,
        onAction = controller::clearRangeScanExclusions
    )
    if (excluded.isEmpty()) {
        Text("Nessuna frequenza esclusa.", fontSize = 10.sp, color = GrabberColors.TextDisabled)
    } else {
        excluded.forEach { frequency ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatKhz(frequency)} kHz",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = GrabberColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { controller.removeRangeScanExclusion(frequency) }, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Rimuovi esclusione", tint = GrabberColors.TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun RangeField(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = GrabberTypography.label, fontSize = 10.sp, modifier = Modifier.width(72.dp))
        CompactTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(26.dp),
            fontSize = 10.sp,
            monospace = true
        )
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String?, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextSecondary, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            SecondaryButton(text = actionLabel, onClick = onAction, icon = Icons.Default.DeleteSweep, size = ControlSize.Mini)
        }
    }
}

@Composable
private fun HitRow(controller: KiwiAppController, hit: ScanHit) {
    val time = remember(hit.lastSeenMillis) { SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(Date(hit.lastSeenMillis)) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatKhz(hit.frequencyKhz)} kHz ${hit.mode.displayName}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = GrabberColors.TextPrimary
            )
            Text(
                text = "$time · ${hit.count}× · picco ${hit.peakRssiDbm.roundToInt()} dBm",
                fontSize = 9.sp,
                color = GrabberColors.TextSecondary
            )
        }
        IconButton(onClick = { controller.tune(hit.frequencyKhz, hit.mode) }, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Default.Radio, contentDescription = "Sintonizza", tint = GrabberColors.Primary, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = { controller.addScanHitToFavorites(hit) }, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Default.Star, contentDescription = "Aggiungi ai preferiti", tint = GrabberColors.Warning, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = { controller.excludeFromRangeScan(hit.frequencyKhz) }, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Default.Block, contentDescription = "Escludi dalla scansione", tint = GrabberColors.Error, modifier = Modifier.size(14.dp))
        }
    }
}
