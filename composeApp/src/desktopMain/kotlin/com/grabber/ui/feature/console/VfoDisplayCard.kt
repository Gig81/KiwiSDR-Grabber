package com.grabber.ui.feature.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.ui.designsystem.AppTooltip
import com.grabber.ui.designsystem.GrabberColors
import java.util.Locale

/**
 * Display LCD VFO con sintonia manuale, pulsanti step e regolazione delta.
 */
@Composable
internal fun VfoDisplayCard(
    frequencyKhz: Double,
    canTune: Boolean,
    currentStep: FrequencyStep,
    onTune: (Double) -> Unit,
    onStepDelta: (Double) -> Unit,
    onStepChange: (FrequencyStep) -> Unit
) {
    var inputBuffer by remember { mutableStateOf(String.format(Locale.US, "%.6f", frequencyKhz / 1000.0)) }
    var isEditing by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(frequencyKhz, isEditing) {
        if (!isEditing) {
            inputBuffer = String.format(Locale.US, "%.6f", frequencyKhz / 1000.0)
            inputError = null
        }
    }

    val parsedFrequencyKhz = remember(inputBuffer) {
        inputBuffer.trim().replace(',', '.').toDoubleOrNull()?.let { value ->
            (if (value <= 30.0) value * 1000.0 else value)
                .takeIf { it in 0.1..30000.0 }
        }
    }
    val commitTuning: () -> Unit = {
        when {
            !canTune -> inputError = "Ricevitore non connesso"
            parsedFrequencyKhz == null -> inputError = "Frequenza non valida (0,1–30.000 kHz)"
            else -> {
                inputError = null
                onTune(parsedFrequencyKhz)
                isEditing = false
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = GrabberColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, GrabberColors.Border),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StepButton(
                    icon = Icons.Default.Remove,
                    description = "Diminuisci di ${currentStep.label}",
                    enabled = canTune,
                    onClick = {
                        onStepDelta(-currentStep.deltaKhz)
                        isEditing = false
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(GrabberColors.LcdBackground)
                        .border(
                            1.dp,
                            if (isEditing) GrabberColors.Warning else GrabberColors.Primary.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BasicTextField(
                            value = inputBuffer,
                            onValueChange = {
                                inputBuffer = it
                                isEditing = true
                                inputError = null
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = if (isEditing) Color.White else GrabberColors.LcdPrimary,
                                fontSize = 24.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { commitTuning() }),
                            cursorBrush = SolidColor(GrabberColors.Primary)
                        )
                        Text(
                            text = "MHz",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrabberColors.Primary
                        )
                        AppTooltip(
                            text = if (canTune) "Sintonizza la frequenza inserita"
                            else "Connetti il ricevitore per sintonizzare"
                        ) {
                            IconButton(
                                onClick = { commitTuning() },
                                enabled = canTune && parsedFrequencyKhz != null,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Sintonizza",
                                    tint = if (canTune && parsedFrequencyKhz != null) GrabberColors.Primary else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                StepButton(
                    icon = Icons.Default.Add,
                    description = "Aumenta di ${currentStep.label}",
                    enabled = canTune,
                    onClick = {
                        onStepDelta(currentStep.deltaKhz)
                        isEditing = false
                    }
                )
            }

            if (inputError != null) {
                Text(
                    text = inputError!!,
                    color = GrabberColors.Error,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else if (isEditing && parsedFrequencyKhz == null) {
                Text(
                    text = "Frequenza non valida (0,1–30.000 kHz)",
                    color = GrabberColors.Error,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FrequencyStep.entries.forEach { step ->
                    val isSelected = step == currentStep
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) GrabberColors.Primary else GrabberColors.SurfaceElevated)
                            .border(1.dp, if (isSelected) GrabberColors.Primary else GrabberColors.Border, RoundedCornerShape(3.dp))
                            .clickable { onStepChange(step) }
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = step.label,
                            fontSize = 8.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

/** Pulsante quadrato ±step con tooltip; disabilitato se il ricevitore non è connesso. */
@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    AppTooltip(text = if (enabled) description else "Connetti il ricevitore per sintonizzare") {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(GrabberColors.SurfaceElevated)
                .border(1.dp, GrabberColors.Border, RoundedCornerShape(3.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (enabled) GrabberColors.Primary else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
