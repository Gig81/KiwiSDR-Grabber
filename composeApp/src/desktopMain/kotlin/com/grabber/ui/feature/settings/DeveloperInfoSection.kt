package com.grabber.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import com.grabber.ui.designsystem.*

/** Informazioni sullo sviluppatore. */
@Composable
internal fun DeveloperInfoSection(
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    CollapsibleSettingsCard(
        title = "INFORMAZIONI SULLO SVILUPPATORE",
        icon = Icons.Default.Info,
        iconTint = GrabberColors.Primary,
        isExpanded = isExpanded,
        onToggle = onToggle,
        subtitle = "Luigi Colella · Gig81 · IU7IYA · CB Max Power"
    ) {
        Text(
            text = "Sviluppatore: Luigi Colella\nNominativi: Gig81, IU7IYA, CB Max Power",
            fontSize = 10.sp,
            color = GrabberColors.TextPrimary
        )
        Text(
            text = "Per assistenza o richieste di attivazione, comunica il Machine ID tramite i canali di contatto ufficiali del progetto.",
            fontSize = 10.sp,
            color = GrabberColors.TextSecondary
        )
    }
}
