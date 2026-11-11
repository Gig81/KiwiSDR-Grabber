package com.grabber.ui.feature.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.model.ModulationMode
import com.grabber.ui.designsystem.AppSegmentedControl
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors

/**
 * Selettore Modalità & Preset di Banda Compatto
 */
@Composable
internal fun ModeAndBandsCard(
    currentMode: ModulationMode,
    onSelectMode: (ModulationMode) -> Unit,
    onSelectPreset: (BandPreset) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GrabberColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, GrabberColors.Border),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            AppSegmentedControl(
                options = ModulationMode.entries.map { it.displayName },
                selectedIndex = ModulationMode.entries.indexOf(currentMode),
                onSelectionChange = { onSelectMode(ModulationMode.entries[it]) },
                size = ControlSize.Compact,
                accentColor = GrabberColors.Primary,
                fillWidth = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                PRESET_BANDS.chunked(6).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(GrabberColors.SurfaceElevated)
                                    .border(1.dp, GrabberColors.Border, RoundedCornerShape(3.dp))
                                    .clickable { onSelectPreset(preset) }
                                    .padding(vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = preset.bandName,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (preset.bandName.contains("CB")) GrabberColors.Warning else GrabberColors.Primary
                                    )
                                    Text(
                                        text = "${preset.frequencyKhz.toInt()}k",
                                        fontSize = 7.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
