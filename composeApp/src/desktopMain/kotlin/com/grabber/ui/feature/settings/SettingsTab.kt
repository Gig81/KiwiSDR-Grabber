package com.grabber.ui.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.ui.designsystem.*

/**
 * Sezioni espandibili delle impostazioni avanzate.
 */
enum class SettingsSection {
    SERVER,
    CALLSIGN,
    LOGS,
    STT,
    TELEGRAM,
    LICENSE,
    INFO
}

/**
 * Scheda delle impostazioni applicative.
 *
 * Raccoglie tutte le configurazioni globali e avanzate della stazione:
 * - Server KiwiSDR / Ricevitore remoto (indirizzo, porta, link mappa)
 * - Identificativo operatore / Callsign trasmesso al server
 * - Livello di Log & diagnostica su file
 * - Trascrizione vocale offline con Vosk (cartella modello locale)
 * - Configurazione Bot Telegram per allerte QSO e parole chiave sentinella
 * - Gestione licenza hardware-locked con attivazione manuale offline
 * - Informazioni sullo sviluppatore
 *
 * Tutte le sezioni sono organizzate in accordion uniformi conformi al Design System,
 * con comandi "Espandi Tutte" / "Collassa Tutte", e nessuna impostazione duplicata
 * rispetto alle schede dedicate (Preferiti, Audio, Registratore, Spettro, Utenti & Monitor).
 */
@Composable
fun SettingsTab(
    controller: KiwiAppController,
    modifier: Modifier = Modifier
) {
    val settings by controller.settings.collectAsState()
    val isMachineLicensed = remember(settings.licenseKey) {
        controller.isLicenseValid(settings.licenseKey)
    }

    var expandedSections by remember(isMachineLicensed) {
        mutableStateOf(if (isMachineLicensed) emptySet() else setOf(SettingsSection.LICENSE))
    }

    fun toggleSection(section: SettingsSection) {
        expandedSections = if (section in expandedSections) {
            expandedSections - section
        } else {
            expandedSections + section
        }
    }

    val settingsScrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = GrabberColors.Surface,
        shape = RoundedCornerShape(GrabberShapes.panel),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 12.dp, top = 8.dp, bottom = 8.dp, start = 8.dp)
                    .verticalScroll(settingsScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Scheda con bottoni Espandi / Collassa Tutti
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = GrabberColors.Secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "IMPOSTAZIONI AVANZATE & CONFIGURAZIONI",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrabberColors.TextPrimary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SecondaryButton(
                            text = "Espandi Tutte",
                            onClick = { expandedSections = SettingsSection.entries.toSet() },
                            icon = Icons.Default.ExpandMore,
                            size = ControlSize.Dense,
                            contentColor = GrabberColors.TextPrimary
                        )

                        SecondaryButton(
                            text = "Collassa Tutte",
                            onClick = { expandedSections = emptySet() },
                            icon = Icons.Default.ExpandLess,
                            size = ControlSize.Dense,
                            contentColor = GrabberColors.TextPrimary
                        )
                    }
                }

                AppDivider()

                ServerSettingsSection(
                    controller,
                    settings,
                    isExpanded = SettingsSection.SERVER in expandedSections,
                    onToggle = { toggleSection(SettingsSection.SERVER) }
                )

                CallsignSettingsSection(
                    controller,
                    settings,
                    isExpanded = SettingsSection.CALLSIGN in expandedSections,
                    onToggle = { toggleSection(SettingsSection.CALLSIGN) }
                )

                LogSettingsSection(
                    controller,
                    settings,
                    isExpanded = SettingsSection.LOGS in expandedSections,
                    onToggle = { toggleSection(SettingsSection.LOGS) }
                )

                TranscriptionSettingsSection(
                    controller,
                    settings,
                    isMachineLicensed,
                    onActivateLicense = { expandedSections = expandedSections + SettingsSection.LICENSE },
                    isExpanded = SettingsSection.STT in expandedSections,
                    onToggle = { toggleSection(SettingsSection.STT) }
                )

                TelegramSettingsSection(
                    controller,
                    settings,
                    isMachineLicensed,
                    onActivateLicense = { expandedSections = expandedSections + SettingsSection.LICENSE },
                    isExpanded = SettingsSection.TELEGRAM in expandedSections,
                    onToggle = { toggleSection(SettingsSection.TELEGRAM) }
                )

                LicenseSettingsSection(
                    controller,
                    settings,
                    isMachineLicensed,
                    isExpanded = SettingsSection.LICENSE in expandedSections,
                    onToggle = { toggleSection(SettingsSection.LICENSE) }
                )

                DeveloperInfoSection(
                    isExpanded = SettingsSection.INFO in expandedSections,
                    onToggle = { toggleSection(SettingsSection.INFO) }
                )
            }

            AppVerticalScrollbar(
                adapter = rememberScrollbarAdapter(settingsScrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp)
            )
        }
    }
}
