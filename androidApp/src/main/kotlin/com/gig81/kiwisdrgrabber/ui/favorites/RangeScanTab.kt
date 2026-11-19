package com.gig81.kiwisdrgrabber.ui.favorites

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gig81.kiwisdrgrabber.ui.components.Panel
import com.gig81.kiwisdrgrabber.ui.theme.KiwiColors
import com.gig81.kiwisdrgrabber.ui.theme.NumericStyle
import com.grabber.app.RangeScanLimits
import com.grabber.app.ScanHit
import com.grabber.app.ScanState
import com.grabber.mobile.MobileAppController
import com.grabber.model.ModulationMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun formatKhz(value: Double): String = String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

private fun parseKhz(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/**
 * Scansione di un intervallo di frequenze, con gli stessi comandi del desktop: inizio, fine, passo,
 * modo, tempo per passo, ripresa automatica, segnali trovati e frequenze escluse.
 */
@Composable
internal fun RangeScanTab(controller: MobileAppController) {
    val activated by controller.isActivated.collectAsStateWithLifecycle()
    val settings by controller.settings.collectAsStateWithLifecycle()
    val scanState by controller.scanState.collectAsStateWithLifecycle()
    val hits by controller.rangeScanHits.collectAsStateWithLifecycle()
    val threshold by controller.squelchThresholdDbm.collectAsStateWithLifecycle()

    var startText by remember(settings.rangeScanStartKhz) { mutableStateOf(formatKhz(settings.rangeScanStartKhz)) }
    var endText by remember(settings.rangeScanEndKhz) { mutableStateOf(formatKhz(settings.rangeScanEndKhz)) }
    var stepText by remember(settings.rangeScanStepKhz) { mutableStateOf(formatKhz(settings.rangeScanStepKhz)) }
    var mode by remember(settings.rangeScanModeCode) { mutableStateOf(settings.rangeScanMode) }
    var error by remember { mutableStateOf<String?>(null) }
    val scanning = scanState != ScanState.Idle

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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).wrapContentWidth().widthIn(max = 640.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!activated) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = KiwiColors.Warning)
                    Spacer(Modifier.width(8.dp))
                    Text("La scansione è disponibile nella versione attivata (gratuita).", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Panel(title = "Scansione di un intervallo") {
            val status = when (val state = scanState) {
                is ScanState.Scanning -> "Ascolto: ${state.target.label} (${state.position}/${state.total})"
                is ScanState.SignalFound -> "Segnale su: ${state.target.label}"
                ScanState.Idle -> "Si ferma quando il segnale supera la soglia dello squelch (${threshold.roundToInt()} dBm), regolabile nella schermata Radio."
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (scanState is ScanState.SignalFound) KiwiColors.Success else KiwiColors.TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhzField("Inizio (kHz)", startText, Modifier.weight(1f)) { startText = it; error = null }
                KhzField("Fine (kHz)", endText, Modifier.weight(1f)) { endText = it; error = null }
            }
            KhzField("Passo (kHz)", stepText, Modifier.fillMaxWidth()) { stepText = it; error = null }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModulationMode.entries.forEach { item ->
                    FilterChip(selected = item == mode, onClick = { mode = item; error = null }, label = { Text(item.displayName) })
                }
            }
            error?.let { Text(it, color = KiwiColors.Error, style = MaterialTheme.typography.bodySmall) }

            if (!scanning) {
                Button(
                    onClick = { if (controller.canUseScan() && applyFields()) controller.startRangeScan() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (activated) Icons.Filled.PlayArrow else Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Avvia scansione")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = controller::stopScan,
                        colors = ButtonDefaults.buttonColors(containerColor = KiwiColors.Error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ferma")
                    }
                    OutlinedButton(onClick = controller::excludeCurrentFromRangeScan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Block, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Escludi")
                    }
                }
                TextButton(onClick = { applyFields() }) { Text("Applica i valori modificati") }
            }

            val dwellSeconds = settings.rangeScanDwellMs / 1000f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ascolto per passo", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(String.format(Locale.ITALY, "%.1f s", dwellSeconds), style = NumericStyle)
            }
            Slider(
                value = dwellSeconds,
                onValueChange = { controller.setRangeScanDwellMs((it * 10).roundToInt() * 100) },
                valueRange = RangeScanLimits.MIN_DWELL_MS / 1000f..RangeScanLimits.MAX_DWELL_MS / 1000f
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Riprendi dopo il segnale", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = settings.rangeScanAutoResume, onCheckedChange = controller::setRangeScanAutoResume)
            }
        }

        Panel(title = "Segnali trovati (${hits.size})") {
            if (hits.isEmpty()) {
                Text("Nessun segnale in questa sessione.", style = MaterialTheme.typography.bodySmall, color = KiwiColors.TextSecondary)
            } else {
                hits.forEach { hit -> HitRow(controller, hit) }
                TextButton(onClick = controller::clearRangeScanHits) { Text("Svuota elenco") }
            }
        }

        val excluded = settings.rangeScanExcludedKhz
        Panel(title = "Frequenze escluse (${excluded.size})") {
            if (excluded.isEmpty()) {
                Text("Nessuna frequenza esclusa.", style = MaterialTheme.typography.bodySmall, color = KiwiColors.TextSecondary)
            } else {
                excluded.forEach { frequency ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatKhz(frequency)} kHz", style = NumericStyle, modifier = Modifier.weight(1f))
                        IconButton(onClick = { controller.removeRangeScanExclusion(frequency) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Riammetti ${formatKhz(frequency)} kHz")
                        }
                    }
                }
                TextButton(onClick = controller::clearRangeScanExclusions) { Text("Svuota") }
            }
        }
    }
}

@Composable
private fun KhzField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun HitRow(controller: MobileAppController, hit: ScanHit) {
    val time = remember(hit.lastSeenMillis) { SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(Date(hit.lastSeenMillis)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${formatKhz(hit.frequencyKhz)} kHz ${hit.mode.displayName}", style = NumericStyle)
            Text(
                "$time · ${hit.count}× · picco ${hit.peakRssiDbm.roundToInt()} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = KiwiColors.TextSecondary
            )
        }
        IconButton(onClick = { controller.tune(hit.frequencyKhz, hit.mode) }) {
            Icon(Icons.Filled.Radio, contentDescription = "Sintonizza", tint = KiwiColors.Primary)
        }
        IconButton(onClick = { controller.addScanHitToFavorites(hit) }) {
            Icon(Icons.Filled.Star, contentDescription = "Aggiungi ai preferiti", tint = KiwiColors.SignalAmber)
        }
        IconButton(onClick = { controller.excludeFromRangeScan(hit.frequencyKhz) }) {
            Icon(Icons.Filled.Block, contentDescription = "Escludi dalla scansione", tint = KiwiColors.Error)
        }
    }
}
