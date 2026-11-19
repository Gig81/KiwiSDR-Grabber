package com.grabber.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.app.SettingsData
import com.grabber.model.UiThemeMode
import com.grabber.ui.designsystem.AppSegmentedControl
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors

/** Tema dell'interfaccia: scuro, chiaro o come il sistema. Applicato subito, senza riavvio. */
@Composable
internal fun AppearanceSettingsSection(
    controller: KiwiAppController,
    settings: SettingsData,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val themes = UiThemeMode.entries
    CollapsibleSettingsCard(
        title = "ASPETTO & TEMA DELL'INTERFACCIA",
        icon = Icons.Default.Palette,
        iconTint = GrabberColors.Primary,
        isExpanded = isExpanded,
        onToggle = onToggle,
        subtitle = "Tema attivo: ${settings.uiTheme.displayName}"
    ) {
        Text(
            text = "Scegli il tema dei pannelli. Il display della frequenza, lo spettro e il waterfall restano scuri in tutti i temi, come il pannello di una radio. \"Sistema\" segue il tema chiaro/scuro delle app di Windows.",
            fontSize = 10.sp,
            color = GrabberColors.TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Tema:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextSecondary)
            AppSegmentedControl(
                options = themes.map { it.displayName },
                selectedIndex = themes.indexOf(settings.uiTheme),
                onSelectionChange = { controller.setUiTheme(themes[it]) },
                size = ControlSize.Compact
            )
        }
    }
}
