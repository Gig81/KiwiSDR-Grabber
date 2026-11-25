package com.grabber.ui.feature.digital

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.grabber.model.digital.DecoderSource
import com.grabber.model.digital.DecoderState
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeAvailability
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.AppTooltip
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.ListSectionHeader
import com.grabber.ui.designsystem.SelectableListItem

/** Elenco dei modi digitali raggruppati per sorgente, con disponibilità e indicatore del decoder attivo. */
@Composable
internal fun DigitalModeList(
    modes: List<DigitalModeAvailability>,
    selected: DigitalMode,
    activeMode: DigitalMode?,
    decoderState: DecoderState,
    onSelect: (DigitalMode) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    AppPanel(
        modifier = modifier,
        title = "MODI",
        icon = Icons.Default.GraphicEq,
        size = ControlSize.Compact,
        contentSpacing = 4.dp
    ) {
        header?.invoke()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (source in DecoderSource.entries) {
                ListSectionHeader(if (source == DecoderSource.SERVER) "Sul server" else "Nell'app")
                modes.filter { it.mode.source == source }.forEach { availability ->
                    ModeRow(
                        availability = availability,
                        selected = availability.mode == selected,
                        active = availability.mode == activeMode,
                        decoderState = decoderState,
                        onClick = { onSelect(availability.mode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeRow(
    availability: DigitalModeAvailability,
    selected: Boolean,
    active: Boolean,
    decoderState: DecoderState,
    onClick: () -> Unit
) {
    val item = @Composable {
        SelectableListItem(
            title = availability.mode.displayName,
            subtitle = if (availability.isAvailable) null else "non disponibile",
            selected = selected,
            onClick = onClick,
            size = ControlSize.Compact,
            modifier = Modifier.fillMaxWidth(),
            trailing = if (active) {
                { ActiveDot(decoderState) }
            } else {
                null
            }
        )
    }
    val reason = availability.reason
    if (reason != null) AppTooltip(reason) { item() } else item()
}

@Composable
private fun ActiveDot(state: DecoderState) {
    val color = when (state) {
        is DecoderState.Running -> GrabberColors.Success
        is DecoderState.Starting -> GrabberColors.Warning
        is DecoderState.Error -> GrabberColors.Warning
        is DecoderState.Unavailable -> GrabberColors.Error
        DecoderState.Idle -> GrabberColors.TextDisabled
    }
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}
