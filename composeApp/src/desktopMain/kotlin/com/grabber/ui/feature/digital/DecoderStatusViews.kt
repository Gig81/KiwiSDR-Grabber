package com.grabber.ui.feature.digital

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.model.digital.DecoderMetrics
import com.grabber.model.digital.DecoderState
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.StatusBadge

/** Stato del decoder in forma di chip colorato. */
@Composable
internal fun DecoderStatusChip(state: DecoderState) {
    val (text, color) = when (state) {
        DecoderState.Idle -> "Fermo" to GrabberColors.TextSecondary
        is DecoderState.Starting -> "Avvio…" to GrabberColors.Warning
        is DecoderState.Running -> "In ascolto" to GrabberColors.Success
        is DecoderState.Error -> "In attesa" to GrabberColors.Warning
        is DecoderState.Unavailable -> "Non disponibile" to GrabberColors.Error
    }
    StatusBadge(text = text, color = color, size = ControlSize.Compact)
}

/** Indicatori del decoder attivo: velocità CW, livello e errori dei decoder locali, stato testuale. */
@Composable
internal fun DecoderIndicators(metrics: DecoderMetrics, visible: Boolean) {
    if (!visible) return
    val hasAny = metrics.wpm != null || metrics.level != null || metrics.errorPercent != null ||
        metrics.synchronized != null || !metrics.status.isNullOrBlank()
    if (!hasAny) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        metrics.wpm?.let { Indicator("WPM", "$it") }
        metrics.level?.let { level ->
            Text("Livello", fontSize = 10.sp, color = GrabberColors.TextSecondary)
            LevelBar(level)
        }
        metrics.synchronized?.let { sync ->
            StatusBadge(if (sync) "Sincronizzato" else "Ricerca sincronismo", if (sync) GrabberColors.Success else GrabberColors.Warning, size = ControlSize.Compact)
        }
        metrics.errorPercent?.let { Indicator("Errori", "$it%") }
        metrics.status?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 10.sp, color = GrabberColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Indicator(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = GrabberColors.TextSecondary)
        Text(value, fontSize = 11.sp, color = GrabberColors.TextPrimary)
    }
}

@Composable
private fun LevelBar(level: Float) {
    val clamped = level.coerceIn(0f, 1f)
    val color = when {
        clamped < 0.15f -> GrabberColors.TextDisabled
        clamped < 0.4f -> GrabberColors.Warning
        else -> GrabberColors.Success
    }
    Box(
        modifier = Modifier.width(80.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(GrabberColors.SurfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(clamped).background(color))
    }
}
