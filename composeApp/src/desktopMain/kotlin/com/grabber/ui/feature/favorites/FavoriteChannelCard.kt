package com.grabber.ui.feature.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.FavoriteFrequency
import com.grabber.model.ModulationMode
import com.grabber.ui.designsystem.*
import java.util.Locale

/**
 * Card compatta del canale per layout a griglia a 2 o 3 colonne.
 * Riorganizza i dati su 2 righe bilanciate eliminando lo spazio vuoto orizzontale.
 */
@Composable
internal fun FavoriteChannelCard(
    favorite: FavoriteFrequency,
    isActive: Boolean,
    onTune: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GrabberShapes.control))
            .clickable { onTune() },
        color = if (isActive) GrabberColors.Primary.copy(alpha = 0.14f) else GrabberColors.SurfaceVariant,
        shape = RoundedCornerShape(GrabberShapes.control),
        border = BorderStroke(
            1.dp,
            if (isActive) GrabberColors.Primary else GrabberColors.Border
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Riga Superiore: [Dot Attivo] Nome Canale (Sinistra) + Badge Gruppo & Modo (Destra)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(GrabberColors.SignalGreen)
                        )
                    }
                    Text(
                        text = favorite.name,
                        style = GrabberTypography.body,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isActive) GrabberColors.Primary else GrabberColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (favorite.group.isNotBlank()) {
                        val groupColor = when (favorite.group.uppercase()) {
                            "CB" -> GrabberColors.SignalAmber
                            "HAM" -> GrabberColors.Primary
                            "MW", "LW", "BC" -> GrabberColors.SignalGreen
                            "AIR" -> GrabberColors.Secondary
                            else -> GrabberColors.TextSecondary
                        }
                        StatusBadge(
                            text = favorite.group,
                            color = groupColor,
                            size = ControlSize.Compact
                        )
                    }

                    val modeColor = when (favorite.mode) {
                        ModulationMode.AM -> Color(0xFFFBBF24)
                        ModulationMode.USB -> Color(0xFF58A6FF)
                        ModulationMode.LSB -> Color(0xFFBC8CFF)
                        ModulationMode.CW -> Color(0xFFFF7B72)
                        ModulationMode.NFM -> Color(0xFF34D399)
                    }
                    StatusBadge(
                        text = favorite.mode.displayName,
                        color = modeColor,
                        size = ControlSize.Compact
                    )
                }
            }

            // Riga Inferiore: Frequenza Monospace (Sinistra) + Pulsanti Sintonizza / Modifica / Elimina (Destra)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (favorite.frequencyKhz >= 1000.0) {
                        "${String.format(Locale.US, "%.1f", favorite.frequencyKhz)} kHz"
                    } else {
                        "${favorite.frequencyKhz.toInt()} kHz"
                    },
                    style = GrabberTypography.numeric,
                    fontSize = 12.sp,
                    color = if (isActive) GrabberColors.Primary else GrabberColors.SignalAmber
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onTune,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Radio,
                            contentDescription = "Sintonizza canale",
                            tint = if (isActive) GrabberColors.SignalGreen else GrabberColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Modifica canale",
                            tint = GrabberColors.TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Elimina canale",
                            tint = GrabberColors.Error.copy(alpha = 0.75f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
