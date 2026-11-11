package com.grabber.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.app.LogLevel
import com.grabber.app.SettingsData
import com.grabber.ui.designsystem.*
import java.awt.Desktop
import java.io.File

/** Livello di log e accesso ai file diagnostici. */
@Composable
internal fun LogSettingsSection(
    controller: KiwiAppController,
    settings: SettingsData,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    CollapsibleSettingsCard(
        title = "REGISTRAZIONE LOG & DIAGNOSTICA SU FILE",
        icon = Icons.Default.Description,
        iconTint = GrabberColors.Success,
        isExpanded = isExpanded,
        onToggle = onToggle,
        subtitle = "Livello attivo: ${settings.logLevelName} (~/.kiwigrabber/logs/)"
    ) {
        Text(
            text = "Per garantire la massima velocità dell'interfaccia, i log non appesantiscono la GUI ma vengono scritti direttamente in un file di log locale su disco (~/.kiwigrabber/logs/).",
            fontSize = 10.sp,
            color = GrabberColors.TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Livello:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextSecondary)
            AppSegmentedControl(
                options = LogLevel.entries.map { it.name },
                selectedIndex = LogLevel.entries.indexOfFirst { it.name == settings.logLevelName },
                onSelectionChange = { controller.setLogLevel(LogLevel.entries[it]) },
                size = ControlSize.Compact
            )

            Spacer(modifier = Modifier.weight(1f))

            SecondaryButton(
                text = "Apri Cartella Log",
                onClick = {
                    try {
                        val logDir = File(System.getProperty("user.home"), ".kiwigrabber/logs")
                        logDir.mkdirs()
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(logDir)
                        }
                    } catch (_: Exception) {}
                },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                size = ControlSize.Compact,
                contentColor = GrabberColors.Secondary
            )
        }
    }
}
