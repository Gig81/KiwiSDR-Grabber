package com.grabber.ui.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.grabber.app.FavoriteFrequency
import com.grabber.app.KiwiAppController
import com.grabber.model.ModulationMode
import com.grabber.ui.designsystem.AppConfirmDialog
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberSpacing
import java.util.Locale
import kotlin.math.abs

/**
 * Scheda dei canali preferiti: a sinistra ricerca, filtri per banda/categoria e azioni globali,
 * a destra la griglia dei canali del filtro attivo con sintonia, modifica ed eliminazione.
 */
@Composable
fun FavoritesTab(
    controller: KiwiAppController,
    favorites: List<FavoriteFrequency>,
    currentFreqKhz: Double,
    currentMode: ModulationMode,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterId by remember { mutableStateOf("CB") }

    var showAddDialog by remember { mutableStateOf(false) }
    var showSaveVfoDialog by remember { mutableStateOf(false) }
    var editingFavorite by remember { mutableStateOf<FavoriteFrequency?>(null) }
    var deletingFavorite by remember { mutableStateOf<FavoriteFrequency?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val availableFilters = remember(favorites) { availableBandFilters(favorites) }
    val filterCounts = remember(favorites) {
        availableFilters.associate { filter -> filter.id to favorites.count { matchesBandFilter(it, filter.id) } }
    }
    val filteredFavorites = remember(favorites, selectedFilterId, searchQuery) {
        favorites.filter { matchesBandFilter(it, selectedFilterId) && matchesSearchQuery(it, searchQuery) }
    }

    Row(
        modifier = modifier.fillMaxSize().padding(GrabberSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(GrabberSpacing.md)
    ) {
        FavoriteFiltersPanel(
            totalFavorites = favorites.size,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            filters = availableFilters,
            filterCounts = filterCounts,
            selectedFilterId = selectedFilterId,
            onSelectFilter = { selectedFilterId = it },
            onSaveVfo = { showSaveVfoDialog = true },
            onAddChannel = { showAddDialog = true },
            onSyncFromServer = controller::syncFavoritesFromServer,
            onResetDefaults = { showResetConfirm = true },
            modifier = Modifier.width(260.dp).fillMaxHeight()
        )
        FavoriteChannelsPanel(
            filterLabel = availableFilters.find { it.id.equals(selectedFilterId, ignoreCase = true) }?.label
                ?: selectedFilterId,
            channels = filteredFavorites,
            searchQuery = searchQuery,
            onClearSearch = { searchQuery = "" },
            isActive = { abs(it.frequencyKhz - currentFreqKhz) < 0.1 && it.mode == currentMode },
            onTune = { controller.tune(it.frequencyKhz, it.mode) },
            onEdit = { editingFavorite = it },
            onDelete = { deletingFavorite = it },
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }

    if (showSaveVfoDialog) {
        val duplicateExisting = favorites.find { abs(it.frequencyKhz - currentFreqKhz) < 0.1 && it.mode == currentMode }
        FavoriteEditDialog(
            title = "Salva Frequenza Attuale",
            initialName = duplicateExisting?.name ?: defaultVfoName(currentFreqKhz, currentMode),
            initialFreqKhz = String.format(Locale.US, "%.1f", currentFreqKhz),
            initialMode = currentMode,
            initialGroup = autoDetectGroup(currentFreqKhz),
            duplicateWarning = duplicateExisting?.let { "Frequenza già salvata con nome \"${it.name}\"" },
            onDismiss = { showSaveVfoDialog = false },
            onConfirm = { name, freq, mode, group ->
                controller.addFavorite(favoriteOf(name, freq, mode, group))
                showSaveVfoDialog = false
            }
        )
    }

    if (showAddDialog) {
        FavoriteEditDialog(
            title = "Aggiungi Canale Preferito",
            initialName = "",
            initialFreqKhz = if (currentFreqKhz > 0.0) String.format(Locale.US, "%.1f", currentFreqKhz) else "27205.0",
            initialMode = currentMode,
            initialGroup = if (selectedFilterId.uppercase() == "TUTTI") "CB" else selectedFilterId,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, freq, mode, group ->
                controller.addFavorite(favoriteOf(name, freq, mode, group))
                showAddDialog = false
            }
        )
    }

    editingFavorite?.let { fav ->
        FavoriteEditDialog(
            title = "Modifica Canale",
            initialName = fav.name,
            initialFreqKhz = String.format(Locale.US, "%.3f", fav.frequencyKhz),
            initialMode = fav.mode,
            initialGroup = fav.group,
            onDismiss = { editingFavorite = null },
            onConfirm = { name, freq, mode, group ->
                controller.editFavorite(fav, favoriteOf(name, freq, mode, group))
                editingFavorite = null
            }
        )
    }

    deletingFavorite?.let { fav ->
        AppConfirmDialog(
            title = "Elimina Canale",
            icon = null,
            message = "Sei sicuro di voler rimuovere dai preferiti il canale \"${fav.name}\" " +
                "(${String.format(Locale.US, "%.1f", fav.frequencyKhz)} kHz)?",
            confirmText = "Elimina",
            onConfirm = {
                controller.removeFavorite(fav)
                deletingFavorite = null
            },
            onDismiss = { deletingFavorite = null }
        )
    }

    if (showResetConfirm) {
        AppConfirmDialog(
            title = "Ripristina Canali Predefiniti",
            icon = null,
            message = "Questa operazione ripristinerà l'elenco completo dei 40 canali CB e delle frequenze " +
                "radioamatoriali predefinite del server KiwiSDR. Eventuali canali personalizzati andranno persi. " +
                "Vuoi procedere?",
            confirmText = "Ripristina",
            onConfirm = {
                controller.resetFavoritesToDefault()
                showResetConfirm = false
            },
            onDismiss = { showResetConfirm = false },
            accentColor = GrabberColors.Warning,
            confirmContentColor = Color.Black
        )
    }
}

private fun favoriteOf(name: String, frequencyKhz: Double, mode: ModulationMode, group: String) =
    FavoriteFrequency(name = name, frequencyKhz = frequencyKhz, modeCode = mode.code, group = group)

/** Filtri standard più le categorie personalizzate presenti nei preferiti. */
private fun availableBandFilters(favorites: List<FavoriteFrequency>): List<FilterBandItem> {
    val standardIds = STANDARD_BAND_FILTERS.map { it.id.uppercase() }.toSet()
    val customGroups = favorites
        .map { it.group.trim() }
        .filter { it.isNotEmpty() && it.uppercase() !in standardIds }
        .distinct()
        .map { FilterBandItem(it, it, "Gruppo $it") }
    return STANDARD_BAND_FILTERS + customGroups
}
