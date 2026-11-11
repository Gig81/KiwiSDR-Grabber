package com.grabber.ui.feature.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grabber.kiwi.KiwiUser
import com.grabber.model.StationInfo
import com.grabber.monitor.UserLogEntry
import com.grabber.ui.designsystem.AppDivider
import com.grabber.ui.designsystem.AppEmptyState
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.AppSwitch
import com.grabber.ui.designsystem.AppVerticalScrollbar
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.FeatureLockedOverlay
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.SecondaryButton
import com.grabber.ui.designsystem.StatusBadge

/** Utenti connessi al ricevitore in tempo reale, con sintonia rapida sulla loro frequenza. */
@Composable
internal fun LiveUsersPanel(
    isConnected: Boolean,
    activeUsers: List<KiwiUser>,
    onTuneToUser: (frequencyKhz: Double, mode: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUsers = isConnected && activeUsers.isNotEmpty()
    AppPanel(
        modifier = modifier,
        title = "UTENTI LIVE CONNESSI",
        icon = Icons.Default.People,
        iconTint = GrabberColors.Success,
        size = ControlSize.Compact,
        actions = {
            StatusBadge(
                text = if (isConnected) "${activeUsers.size} attivi" else "Non connesso",
                color = if (hasUsers) GrabberColors.Success else GrabberColors.TextSecondary
            )
        }
    ) {
        if (!hasUsers) {
            AppEmptyState(
                icon = if (!isConnected) Icons.Default.WifiOff else Icons.Default.PersonOutline,
                message = if (!isConnected) {
                    "Connettiti al ricevitore KiwiSDR per ricevere la lista utenti live in tempo reale."
                } else {
                    "Nessun utente live attivo rilevato sul server."
                }
            )
        } else {
            ScrollableList(items = activeUsers, key = { it.slot }) { user ->
                LiveUserRow(user = user, onTuneToUser = onTuneToUser)
            }
        }
    }
}

/** Dati di stato del server KiwiSDR letti da `/status`. */
@Composable
internal fun ServerInfoPanel(
    stationInfo: StationInfo,
    serverEndpoint: String,
    modifier: Modifier = Modifier
) {
    AppPanel(
        modifier = modifier,
        title = "INFORMAZIONI SERVER KIWISDR",
        icon = Icons.Default.Dns,
        iconTint = GrabberColors.Secondary,
        size = ControlSize.Compact,
        contentSpacing = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ServerInfoRow("Stazione", stationInfo.name)
            ServerInfoRow("Server / Host", serverEndpoint)
            ServerInfoRow("Slot Ricevitore", "${stationInfo.activeUsers} / ${stationInfo.maxUsers}")
            ServerInfoRow("Antenna", stationInfo.antenna)
            ServerInfoRow("GPS", stationInfo.gps)
            ServerInfoRow("SNR Segnale", stationInfo.snr)
            ServerInfoRow("Uptime Server", stationInfo.uptime)
            ServerInfoRow("Software Kiwi", stationInfo.version)
        }
    }
}

/** Storico SQLite degli utenti connessi, con selezione multipla ed eliminazione. Richiede la licenza. */
@Composable
internal fun UserHistoryPanel(
    userLogs: List<UserLogEntry>,
    selectedLogIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    isRecordingUsers: Boolean,
    onRecordingUsersChange: (Boolean) -> Unit,
    onDeleteSelected: () -> Unit,
    onClearAll: () -> Unit,
    onRefresh: () -> Unit,
    onTune: (frequencyKhz: Double, mode: String) -> Unit,
    isMachineLicensed: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppPanel(
        modifier = modifier,
        title = "STORICO MONITORAGGIO (SQLite)",
        icon = Icons.Default.Storage,
        iconTint = GrabberColors.Primary,
        size = ControlSize.Compact,
        titleAccessory = { StatusBadge(text = "${userLogs.size} record", color = GrabberColors.TextSecondary) },
        actions = {
            AppSwitch(
                checked = isRecordingUsers,
                onCheckedChange = onRecordingUsersChange,
                label = "Registra utenti in SQLite",
                size = ControlSize.Compact,
                accentColor = GrabberColors.Success
            )
        },
        overlay = if (isMachineLicensed) {
            null
        } else {
            { FeatureLockedOverlay(message = "Lo storico utenti richiede l'attivazione.", onActivate = onActivate) }
        }
    ) {
        HistoryToolbar(
            userLogs = userLogs,
            selectedLogIds = selectedLogIds,
            onSelectionChange = onSelectionChange,
            onDeleteSelected = onDeleteSelected,
            onClearAll = onClearAll,
            onRefresh = onRefresh
        )
        AppDivider()
        if (userLogs.isEmpty()) {
            AppEmptyState(
                icon = Icons.Default.History,
                message = "Nessun dato di monitoraggio salvato nel database SQLite.",
                detail = "Attiva lo switch 'Registra utenti in SQLite' in alto a destra per archiviare " +
                    "periodicamente le sessioni attive sul server.",
                iconSize = 36.dp
            )
        } else {
            ScrollableList(items = userLogs, key = { it.id }) { log ->
                val isSelected = log.id in selectedLogIds
                UserLogRow(
                    log = log,
                    isSelected = isSelected,
                    onToggleSelect = {
                        onSelectionChange(if (isSelected) selectedLogIds - log.id else selectedLogIds + log.id)
                    },
                    onTune = onTune
                )
            }
        }
    }
}

@Composable
private fun HistoryToolbar(
    userLogs: List<UserLogEntry>,
    selectedLogIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onDeleteSelected: () -> Unit,
    onClearAll: () -> Unit,
    onRefresh: () -> Unit
) {
    val allSelected = userLogs.isNotEmpty() && selectedLogIds.size == userLogs.size
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(
                text = if (allSelected) "Deseleziona" else "Seleziona Tutti",
                onClick = { onSelectionChange(if (allSelected) emptySet() else userLogs.map { it.id }.toSet()) },
                enabled = userLogs.isNotEmpty(),
                icon = if (allSelected) Icons.Default.Clear else Icons.Default.SelectAll,
                size = ControlSize.Dense
            )
            if (selectedLogIds.isNotEmpty()) {
                SecondaryButton(
                    text = "Elimina (${selectedLogIds.size})",
                    onClick = onDeleteSelected,
                    icon = Icons.Default.Delete,
                    size = ControlSize.Dense,
                    contentColor = GrabberColors.Error
                )
            }
            SecondaryButton(
                text = "Svuota DB",
                onClick = onClearAll,
                enabled = userLogs.isNotEmpty(),
                icon = Icons.Default.DeleteForever,
                size = ControlSize.Dense,
                contentColor = GrabberColors.Warning
            )
        }
        SecondaryButton(
            text = "Aggiorna",
            onClick = onRefresh,
            icon = Icons.Default.Refresh,
            size = ControlSize.Dense,
            contentColor = GrabberColors.Secondary
        )
    }
}

@Composable
private fun <T> ScrollableList(items: List<T>, key: (T) -> Any, row: @Composable (T) -> Unit) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(items, key = key) { row(it) }
        }
        AppVerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 2.dp)
        )
    }
}
