package com.grabber.ui.feature.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grabber.app.KiwiAppController
import com.grabber.model.ConnectionState
import com.grabber.model.ModulationMode
import com.grabber.ui.designsystem.AppConfirmDialog
import java.util.Locale

/**
 * Scheda utenti e monitoraggio della stazione.
 *
 * A sinistra gli utenti connessi in tempo reale (con sintonia rapida) e le informazioni del server;
 * a destra lo storico SQLite degli utenti, con selezione multipla, eliminazione e l'interruttore
 * di registrazione. Lo storico richiede la licenza.
 */
@Composable
fun UsersTab(
    controller: KiwiAppController,
    modifier: Modifier = Modifier,
    isMachineLicensed: Boolean = true,
    onActivate: () -> Unit = {}
) {
    val connectionState by controller.connectionState.collectAsState()
    val stationInfo by controller.stationInfo.collectAsState()
    val activeUsers by controller.activeUsers.collectAsState()
    val userLogs by controller.userLogs.collectAsState()
    val settings by controller.settings.collectAsState()

    var selectedLogIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteLogsDialog by remember { mutableStateOf(false) }
    var showClearAllLogsDialog by remember { mutableStateOf(false) }

    /** Il modo riportato dal server (es. "usb") viene applicato solo se corrisponde a un modo noto. */
    fun tuneTo(frequencyKhz: Double, modeName: String) {
        val mode = ModulationMode.entries.firstOrNull { it.name == modeName.uppercase(Locale.US) }
        controller.tune(frequencyKhz, mode)
    }

    if (showDeleteLogsDialog && selectedLogIds.isNotEmpty()) {
        AppConfirmDialog(
            title = "Elimina Voci Monitoraggio",
            icon = Icons.Default.Delete,
            message = "Vuoi eliminare definitivamente i ${selectedLogIds.size} record selezionati dallo storico SQLite?",
            confirmText = "Elimina (${selectedLogIds.size})",
            onConfirm = {
                controller.deleteUserLogs(selectedLogIds.toList())
                selectedLogIds = emptySet()
                showDeleteLogsDialog = false
            },
            onDismiss = { showDeleteLogsDialog = false }
        )
    }

    if (showClearAllLogsDialog) {
        AppConfirmDialog(
            title = "Svuota Database Monitoraggio",
            icon = Icons.Default.DeleteForever,
            message = "Vuoi eliminare definitivamente TUTTI i record di monitoraggio dal database SQLite?\n\n" +
                "Questa operazione è irreversibile e cancellerà l'intero storico, non solo le righe attualmente visibili.",
            confirmText = "Svuota Tutto",
            onConfirm = {
                controller.clearAllUserLogs()
                selectedLogIds = emptySet()
                showClearAllLogsDialog = false
            },
            onDismiss = { showClearAllLogsDialog = false }
        )
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1.15f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiveUsersPanel(
                isConnected = connectionState is ConnectionState.Connected,
                activeUsers = activeUsers,
                onTuneToUser = ::tuneTo,
                modifier = Modifier.weight(1.3f).fillMaxWidth()
            )
            ServerInfoPanel(
                stationInfo = stationInfo,
                serverEndpoint = controller.serverEndpoint,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }

        UserHistoryPanel(
            userLogs = userLogs,
            selectedLogIds = selectedLogIds,
            onSelectionChange = { selectedLogIds = it },
            isRecordingUsers = settings.recordConnectedUsers,
            onRecordingUsersChange = controller::setRecordConnectedUsers,
            onDeleteSelected = { showDeleteLogsDialog = true },
            onClearAll = { showClearAllLogsDialog = true },
            onRefresh = controller::refreshHistory,
            onTune = ::tuneTo,
            isMachineLicensed = isMachineLicensed,
            onActivate = onActivate,
            modifier = Modifier.weight(1.45f).fillMaxHeight()
        )
    }
}
