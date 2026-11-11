package com.grabber.ui.feature.users

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.kiwi.KiwiUser
import com.grabber.monitor.UserLogEntry
import com.grabber.ui.designsystem.*
import java.util.Locale

/**
 * Riga Utente Live
 */
@Composable
internal fun LiveUserRow(
    user: KiwiUser,
    onTuneToUser: (Double, String) -> Unit
) {
    val isMy = user.isMySession
    val freqKhz = user.freqHz / 1000.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GrabberShapes.control),
        color = if (isMy) GrabberColors.SurfaceVariant else GrabberColors.SurfaceElevated,
        border = BorderStroke(1.dp, if (isMy) GrabberColors.Secondary else GrabberColors.Border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Badge Slot
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isMy) GrabberColors.Secondary else GrabberColors.Success.copy(alpha = 0.18f))
                        .border(1.dp, if (isMy) GrabberColors.Secondary else GrabberColors.Success.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${user.slot}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMy) Color.Black else GrabberColors.Success
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user.name.ifBlank { "Anonimo" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMy) GrabberColors.Secondary else GrabberColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isMy) {
                            StatusBadge(
                                text = "TU",
                                color = GrabberColors.Secondary,
                                size = ControlSize.Compact
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", freqKhz)} kHz",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = GrabberColors.Secondary
                        )
                        ModeBadge(user.mode)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (user.connected.isNotBlank()) {
                    Text(
                        text = user.connected,
                        fontSize = 9.sp,
                        color = GrabberColors.TextSecondary,
                        maxLines = 1
                    )
                }

                // Sintonia rapida sulla frequenza dell'utente
                IconButton(
                    onClick = { onTuneToUser(freqKhz, user.mode) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Sintonizza su questo canale",
                        tint = GrabberColors.Secondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * Riga Storico Monitoraggio SQLite
 */
@Composable
internal fun UserLogRow(
    log: UserLogEntry,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onTune: (Double, String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(GrabberShapes.control),
        color = if (isSelected) GrabberColors.SurfaceVariant else GrabberColors.SurfaceElevated,
        border = BorderStroke(1.dp, if (isSelected) GrabberColors.Primary else GrabberColors.Border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.size(20.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = GrabberColors.Primary,
                    uncheckedColor = GrabberColors.Border,
                    checkmarkColor = Color.White
                )
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = log.timestamp,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrabberColors.TextSecondary
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", log.frequencyKhz)} kHz",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = GrabberColors.Secondary
                        )
                        ModeBadge(log.mode)
                    }

                    StatusBadge(
                        text = "${log.activeUsers} / ${log.maxUsers} Utenti",
                        color = GrabberColors.Warning,
                        size = ControlSize.Compact
                    )
                }

                if (log.userNames.isNotBlank()) {
                    Text(
                        text = "Utenti: ${log.userNames}",
                        fontSize = 9.sp,
                        color = GrabberColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Pulsante sintonia rapida sulla frequenza del log
            IconButton(
                onClick = { onTune(log.frequencyKhz, log.mode) },
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "Sintonizza su questo VFO",
                    tint = GrabberColors.Secondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Singola riga Chiave - Valore per il Server KiwiSDR
 */
@Composable
internal fun ServerInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = GrabberColors.TextSecondary
        )
        Text(
            text = value.ifBlank { "N/D" },
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = GrabberColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Badge Modalità Semantico (AM, USB, LSB, CW, NFM)
 */
@Composable
private fun ModeBadge(mode: String) {
    val cleanMode = mode.trim().uppercase(Locale.US)
    val badgeColor = when (cleanMode) {
        "USB" -> GrabberColors.Primary
        "LSB" -> Color(0xFF9C27B0)
        "CW" -> GrabberColors.Warning
        "AM" -> GrabberColors.Secondary
        "NFM", "FM" -> GrabberColors.Success
        else -> GrabberColors.TextSecondary
    }

    StatusBadge(
        text = cleanMode,
        color = badgeColor,
        size = ControlSize.Compact
    )
}
