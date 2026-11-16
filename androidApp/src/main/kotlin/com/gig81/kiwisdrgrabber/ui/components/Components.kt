package com.gig81.kiwisdrgrabber.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gig81.kiwisdrgrabber.ui.theme.KiwiColors
import java.util.Locale

/** Pannello con titolo, equivalente compatto delle card del desktop. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = KiwiColors.Surface),
        border = BorderStroke(1.dp, KiwiColors.Border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = KiwiColors.TextSecondary)
            }
            content()
        }
    }
}

/** Stato vuoto con messaggio e azione opzionale (es. "Connetti"). */
@Composable
fun EmptyState(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, textAlign = TextAlign.Center, color = KiwiColors.TextSecondary)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(actionLabel) }
        }
    }
}

fun formatFrequencyKhz(khz: Double): String = String.format(Locale.ITALY, "%,.2f", khz)

fun formatDuration(seconds: Long): String =
    String.format(Locale.ITALY, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.ITALY, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.ITALY, "%.0f kB", bytes / 1024.0)
    else -> "$bytes B"
}
