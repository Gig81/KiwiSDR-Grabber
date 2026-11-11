package com.grabber.ui.feature.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.FavoriteFrequency
import com.grabber.ui.designsystem.AppDivider
import com.grabber.ui.designsystem.AppEmptyState
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.AppSearchField
import com.grabber.ui.designsystem.AppVerticalScrollbar
import com.grabber.ui.designsystem.CompactButton
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberShapes
import com.grabber.ui.designsystem.GrabberSpacing
import com.grabber.ui.designsystem.GrabberTypography
import com.grabber.ui.designsystem.PrimaryButton
import com.grabber.ui.designsystem.SecondaryButton
import com.grabber.ui.designsystem.SelectableListItem
import com.grabber.ui.designsystem.StatusBadge

/** Ricerca, filtri per banda/categoria e azioni sull'intero elenco dei preferiti. */
@Composable
internal fun FavoriteFiltersPanel(
    totalFavorites: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filters: List<FilterBandItem>,
    filterCounts: Map<String, Int>,
    selectedFilterId: String,
    onSelectFilter: (String) -> Unit,
    onSaveVfo: () -> Unit,
    onAddChannel: () -> Unit,
    onSyncFromServer: () -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppPanel(
        modifier = modifier,
        title = "FILTRI & RICERCA",
        icon = Icons.Default.FilterList,
        size = ControlSize.Compact,
        actions = { StatusBadge(text = "$totalFavorites", color = GrabberColors.TextSecondary) }
    ) {
        AppSearchField(value = searchQuery, onValueChange = onSearchQueryChange, placeholder = "Cerca canale o kHz...")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GrabberSpacing.xs)) {
            PrimaryButton(
                text = "+ Salva VFO",
                onClick = onSaveVfo,
                icon = Icons.Default.Star,
                size = ControlSize.Compact,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Nuovo...",
                onClick = onAddChannel,
                icon = Icons.Default.Add,
                size = ControlSize.Compact,
                modifier = Modifier.weight(1f)
            )
        }
        AppDivider()
        Text(text = "BANDE & CATEGORIE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextSecondary)
        BandFilterList(
            filters = filters,
            filterCounts = filterCounts,
            selectedFilterId = selectedFilterId,
            onSelectFilter = onSelectFilter,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        AppDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GrabberSpacing.xs)) {
            SecondaryButton(
                text = "Sincronizza",
                onClick = onSyncFromServer,
                icon = Icons.Default.CloudDownload,
                size = ControlSize.Dense,
                contentColor = GrabberColors.Secondary,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Ripristina",
                onClick = onResetDefaults,
                icon = Icons.Default.Refresh,
                size = ControlSize.Dense,
                contentColor = GrabberColors.Warning,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BandFilterList(
    filters: List<FilterBandItem>,
    filterCounts: Map<String, Int>,
    selectedFilterId: String,
    onSelectFilter: (String) -> Unit,
    modifier: Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.verticalScroll(scrollState).padding(end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        filters.forEach { filter ->
            val isSelected = filter.id.equals(selectedFilterId, ignoreCase = true)
            SelectableListItem(
                title = filter.label,
                subtitle = filter.description,
                selected = isSelected,
                onClick = { onSelectFilter(filter.id) },
                size = ControlSize.Compact,
                modifier = Modifier.fillMaxWidth(),
                trailing = { FilterCountPill(count = filterCounts[filter.id] ?: 0, isSelected = isSelected) }
            )
        }
    }
}

@Composable
private fun FilterCountPill(count: Int, isSelected: Boolean) {
    Surface(
        color = if (isSelected) Color.White.copy(alpha = 0.25f) else GrabberColors.Surface,
        shape = RoundedCornerShape(GrabberShapes.chip),
        border = BorderStroke(1.dp, if (isSelected) Color.Transparent else GrabberColors.Border)
    ) {
        Text(
            text = "$count",
            style = GrabberTypography.status,
            fontSize = 9.sp,
            color = if (isSelected) Color.White else GrabberColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

/** Griglia adattiva dei canali del filtro attivo. */
@Composable
internal fun FavoriteChannelsPanel(
    filterLabel: String,
    channels: List<FavoriteFrequency>,
    searchQuery: String,
    onClearSearch: () -> Unit,
    isActive: (FavoriteFrequency) -> Boolean,
    onTune: (FavoriteFrequency) -> Unit,
    onEdit: (FavoriteFrequency) -> Unit,
    onDelete: (FavoriteFrequency) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    AppPanel(
        modifier = modifier,
        title = "ELENCO CANALI — $filterLabel",
        icon = Icons.Default.Radio,
        size = ControlSize.Compact,
        actions = {
            if (searchQuery.isNotBlank()) SearchFilterChip(searchQuery, onClearSearch)
            StatusBadge(text = "${channels.size} canali", color = GrabberColors.Primary)
        }
    ) {
        AppDivider()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (channels.isEmpty()) {
                AppEmptyState(
                    icon = Icons.Default.SearchOff,
                    message = if (searchQuery.isNotEmpty()) {
                        "Nessun canale corrispondente alla ricerca \"$searchQuery\""
                    } else {
                        "Nessun canale salvato in questa categoria"
                    },
                    iconSize = 36.dp,
                    action = if (searchQuery.isNotEmpty()) {
                        { CompactButton(text = "Cancella ricerca", onClick = onClearSearch) }
                    } else {
                        null
                    }
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 250.dp),
                    modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // La chiave include l'indice: due preferiti possono avere stesso nome e frequenza.
                    items(
                        count = channels.size,
                        key = { index -> "${index}_${channels[index].name}_${channels[index].frequencyKhz}" }
                    ) { index ->
                        val fav = channels[index]
                        FavoriteChannelCard(
                            favorite = fav,
                            isActive = isActive(fav),
                            onTune = { onTune(fav) },
                            onEdit = { onEdit(fav) },
                            onDelete = { onDelete(fav) }
                        )
                    }
                }
                AppVerticalScrollbar(
                    adapter = rememberScrollbarAdapter(gridState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun SearchFilterChip(searchQuery: String, onClear: () -> Unit) {
    Surface(
        color = GrabberColors.SurfaceVariant,
        shape = RoundedCornerShape(GrabberShapes.chip),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Filtro: \"$searchQuery\"", style = GrabberTypography.status, fontSize = 9.sp, color = GrabberColors.Primary)
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Rimuovi filtro",
                tint = GrabberColors.TextSecondary,
                modifier = Modifier.size(10.dp).clickable { onClear() }
            )
        }
    }
}
