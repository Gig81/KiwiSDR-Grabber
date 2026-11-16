package com.gig81.kiwisdrgrabber.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gig81.kiwisdrgrabber.ui.components.EmptyState
import com.gig81.kiwisdrgrabber.ui.components.Panel
import com.gig81.kiwisdrgrabber.ui.components.formatFrequencyKhz
import com.gig81.kiwisdrgrabber.ui.theme.KiwiColors
import com.grabber.app.FavoriteFrequency
import com.grabber.app.bandGroupFor
import com.grabber.mobile.MobileAppController
import com.grabber.model.ConnectionState
import com.grabber.model.ModulationMode
import java.util.Locale

private data class Channel(val name: String, val frequencyKhz: Double, val mode: ModulationMode, val group: String)

@Composable
fun FavoritesScreen(controller: MobileAppController) {
    var sourceIndex by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = sourceIndex, containerColor = KiwiColors.Surface) {
            Tab(selected = sourceIndex == 0, onClick = { sourceIndex = 0; group = null }, text = { Text("Server") })
            Tab(selected = sourceIndex == 1, onClick = { sourceIndex = 1; group = null }, text = { Text("Personali") })
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Cerca per nome o frequenza") }
        )
        if (sourceIndex == 0) {
            ServerChannels(controller, query, group) { group = it }
        } else {
            PersonalChannels(controller, query, group) { group = it }
        }
    }
}

@Composable
private fun ServerChannels(controller: MobileAppController, query: String, group: String?, onGroup: (String?) -> Unit) {
    val connection by controller.connectionState.collectAsStateWithLifecycle()
    val labels by controller.serverDxLabels.collectAsStateWithLifecycle()
    val channels = remember(labels) {
        labels.map { Channel(it.name.ifBlank { "${formatFrequencyKhz(it.frequencyKhz)} kHz" }, it.frequencyKhz, it.mode, bandGroupFor(it.frequencyKhz)) }
    }

    if (channels.isEmpty()) {
        EmptyState(
            if (connection is ConnectionState.Connected) "Il ricevitore non ha ancora inviato canali DX." else
                "I canali del server vengono caricati a ogni connessione al ricevitore.",
            actionLabel = if (connection is ConnectionState.Connected) "Ricarica" else null,
            onAction = controller::requestDxLabels
        )
        return
    }
    ChannelList(
        channels = channels,
        query = query,
        group = group,
        onGroup = onGroup,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${channels.size} canali dal ricevitore (sola lettura)", Modifier.weight(1f), color = KiwiColors.TextSecondary)
                IconButton(onClick = controller::requestDxLabels, enabled = connection is ConnectionState.Connected) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Ricarica i canali del server")
                }
            }
        },
        onTune = { controller.tune(it.frequencyKhz, it.mode) }
    )
}

@Composable
private fun PersonalChannels(controller: MobileAppController, query: String, group: String?, onGroup: (String?) -> Unit) {
    val activated by controller.isActivated.collectAsStateWithLifecycle()
    val settings by controller.settings.collectAsStateWithLifecycle()
    val favorites = settings.personalFavorites
    val currentFrequency by controller.currentFrequencyKhz.collectAsStateWithLifecycle()
    val currentMode by controller.currentMode.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FavoriteFrequency?>(null) }
    var creating by remember { mutableStateOf(false) }

    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!activated) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = KiwiColors.Warning)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "I preferiti personali si creano e modificano nella versione attivata (gratuita). " +
                                "Quelli già salvati restano sintonizzabili.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Button(onClick = { if (activated) creating = true else controller.addCurrentAsFavorite() }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Aggiungi canale")
            }
        }
    }

    if (favorites.isEmpty()) {
        Column(Modifier.padding(12.dp)) {
            header()
            EmptyState("Nessun preferito personale. Aggiungi il canale sintonizzato con la stella nella schermata Radio.")
        }
    } else {
        val channels = favorites.map { Channel(it.name, it.frequencyKhz, it.mode, it.group) }
        ChannelList(
            channels = channels,
            query = query,
            group = group,
            onGroup = onGroup,
            header = header,
            onTune = { controller.tune(it.frequencyKhz, it.mode) },
            onEdit = { channel -> editing = favorites.firstOrNull { it.matches(channel) } },
            onDelete = { channel -> favorites.firstOrNull { it.matches(channel) }?.let(controller::removeFavorite) }
        )
    }

    if (creating) {
        FavoriteEditDialog(
            initial = null,
            defaultFrequencyKhz = currentFrequency,
            defaultMode = currentMode,
            onDismiss = { creating = false },
            onSave = {
                creating = false
                controller.addFavorite(it)
            }
        )
    }
    editing?.let { favorite ->
        FavoriteEditDialog(
            initial = favorite,
            defaultFrequencyKhz = favorite.frequencyKhz,
            defaultMode = favorite.mode,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                controller.editFavorite(favorite, it)
            }
        )
    }
}

private fun FavoriteFrequency.matches(channel: Channel) =
    name == channel.name && frequencyKhz == channel.frequencyKhz && mode == channel.mode && group == channel.group

@Composable
private fun ChannelList(
    channels: List<Channel>,
    query: String,
    group: String?,
    onGroup: (String?) -> Unit,
    header: @Composable () -> Unit,
    onTune: (Channel) -> Unit,
    onEdit: ((Channel) -> Unit)? = null,
    onDelete: ((Channel) -> Unit)? = null
) {
    val groups = channels.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
    val filtered = channels.filter { channel ->
        (group == null || channel.group == group) &&
            (query.isBlank() || channel.name.contains(query, ignoreCase = true) ||
                formatFrequencyKhz(channel.frequencyKhz).contains(query) || channel.frequencyKhz.toString().contains(query))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { header() }
        if (groups.size > 1) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = group == null, onClick = { onGroup(null) }, label = { Text("Tutti") })
                    groups.forEach { item ->
                        FilterChip(selected = group == item, onClick = { onGroup(item) }, label = { Text(item) })
                    }
                }
            }
        }
        itemsIndexed(filtered, key = { index, channel -> "$index-${channel.name}-${channel.frequencyKhz}" }) { _, channel ->
            Panel(Modifier.clickable(onClickLabel = "Sintonizza") { onTune(channel) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${formatFrequencyKhz(channel.frequencyKhz)} kHz · ${channel.mode.displayName}" +
                                if (channel.group.isNotBlank()) " · ${channel.group}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = KiwiColors.TextSecondary
                        )
                    }
                    if (onEdit != null) {
                        IconButton(onClick = { onEdit(channel) }) { Icon(Icons.Filled.Edit, contentDescription = "Modifica ${channel.name}") }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = { onDelete(channel) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina ${channel.name}", tint = KiwiColors.Error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteEditDialog(
    initial: FavoriteFrequency?,
    defaultFrequencyKhz: Double,
    defaultMode: ModulationMode,
    onDismiss: () -> Unit,
    onSave: (FavoriteFrequency) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "${defaultMode.displayName} ${formatFrequencyKhz(defaultFrequencyKhz)} kHz") }
    var frequencyText by remember { mutableStateOf(String.format(Locale.US, "%.2f", defaultFrequencyKhz)) }
    var mode by remember { mutableStateOf(initial?.mode ?: defaultMode) }
    val frequency = frequencyText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..30000.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuovo preferito" else "Modifica preferito") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nome") })
                OutlinedTextField(
                    value = frequencyText,
                    onValueChange = { frequencyText = it },
                    singleLine = true,
                    isError = frequency == null,
                    label = { Text("Frequenza (kHz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModulationMode.entries.forEach { item ->
                        FilterChip(selected = item == mode, onClick = { mode = item }, label = { Text(item.displayName) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && frequency != null,
                onClick = {
                    val freq = frequency ?: return@TextButton
                    onSave(
                        FavoriteFrequency(
                            name = name.trim(),
                            frequencyKhz = freq,
                            modeCode = mode.code,
                            group = initial?.group?.takeIf { it.isNotBlank() } ?: bandGroupFor(freq)
                        )
                    )
                }
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
