package com.grabber.ui.feature.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.grabber.model.ModulationMode
import com.grabber.ui.designsystem.*

@Composable
internal fun FavoriteEditDialog(
    title: String,
    initialName: String,
    initialFreqKhz: String,
    initialMode: ModulationMode,
    initialGroup: String,
    duplicateWarning: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, freq: Double, mode: ModulationMode, group: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var freqText by remember { mutableStateOf(initialFreqKhz) }
    var selectedMode by remember { mutableStateOf(initialMode) }
    var group by remember { mutableStateOf(initialGroup) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(380.dp),
            shape = RoundedCornerShape(GrabberShapes.panel),
            color = GrabberColors.SurfaceElevated,
            border = BorderStroke(1.dp, GrabberColors.Border)
        ) {
            Column(
                modifier = Modifier.padding(GrabberSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(GrabberSpacing.md)
            ) {
                Text(
                    text = title,
                    style = GrabberTypography.panelTitle,
                    fontSize = 15.sp,
                    color = GrabberColors.TextPrimary
                )

                AppDivider()

                if (duplicateWarning != null) {
                    AppNotice(
                        text = duplicateWarning,
                        color = GrabberColors.Warning,
                        icon = Icons.Default.Warning
                    )
                }

                DialogTextField(
                    label = "Nome canale",
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    placeholder = "Es: CB Ch 19 Camionisti"
                )

                DialogTextField(
                    label = "Frequenza (kHz)",
                    value = freqText,
                    onValueChange = {
                        freqText = it
                        errorMessage = null
                    },
                    monospace = true,
                    placeholder = "Es: 27185.0"
                )

                Column(verticalArrangement = Arrangement.spacedBy(GrabberSpacing.xs)) {
                    Text(text = "Modo di demodulazione", style = GrabberTypography.label, fontSize = 11.sp)
                    AppSegmentedControl(
                        options = ModulationMode.entries.map { it.displayName },
                        selectedIndex = ModulationMode.entries.indexOf(selectedMode),
                        onSelectionChange = { selectedMode = ModulationMode.entries[it] },
                        size = ControlSize.Compact,
                        accentColor = GrabberColors.Primary,
                        selectedContentColor = Color.White,
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                DialogTextField(
                    label = "Gruppo / Banda (es: CB, HAM, MW, DX)",
                    value = group,
                    onValueChange = { group = it },
                    placeholder = "Es: CB"
                )

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = GrabberTypography.label,
                        color = GrabberColors.Error,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        text = "Annulla",
                        onClick = onDismiss,
                        contentColor = GrabberColors.TextSecondary
                    )

                    Spacer(modifier = Modifier.width(GrabberSpacing.sm))

                    PrimaryButton(
                        text = "Salva",
                        onClick = {
                            val cleanFreq = freqText.trim().replace(',', '.')
                            val parsedFreq = cleanFreq.toDoubleOrNull()
                            val cleanName = name.trim()

                            if (cleanName.isBlank()) {
                                errorMessage = "Il nome del canale non può essere vuoto"
                            } else if (parsedFreq == null || parsedFreq < 0.0 || parsedFreq > 30000.0) {
                                errorMessage = "Frequenza non valida (intervallo 0 - 30000 kHz)"
                            } else {
                                onConfirm(cleanName, parsedFreq, selectedMode, group.trim())
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    monospace: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(GrabberSpacing.xs)) {
        Text(text = label, style = GrabberTypography.label, fontSize = 11.sp)
        CompactTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth().height(34.dp),
            fontSize = 12.sp,
            monospace = monospace
        )
    }
}
