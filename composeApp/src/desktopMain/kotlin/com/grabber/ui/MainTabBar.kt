package com.grabber.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberShapes

/** Schede dell'area di lavoro, nell'ordine in cui compaiono nella barra di navigazione. */
enum class MainTab(val label: String, val icon: ImageVector) {
    FAVORITES("Preferiti", Icons.Default.Star),
    AUDIO("Audio", Icons.AutoMirrored.Filled.VolumeUp),
    RECORDER("Registratore", Icons.Default.Mic),
    SPECTRUM("Spettro & Cascata", Icons.Default.Timeline),
    USERS("Utenti & Monitor", Icons.Default.People),
    SETTINGS("Impostazioni avanzate", Icons.Default.Settings)
}

private val SelectedTabBackground = Color(0xFF2E3D52)

/** Barra delle schede; la scheda Utenti mostra il numero di utenti connessi. */
@Composable
internal fun MainTabBar(
    activeTab: MainTab,
    onSelect: (MainTab) -> Unit,
    activeUsersCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        color = GrabberColors.Surface,
        shape = RoundedCornerShape(GrabberShapes.panel),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                MainTabItem(
                    tab = tab,
                    selected = tab == activeTab,
                    badgeCount = if (tab == MainTab.USERS) activeUsersCount else 0,
                    onClick = { onSelect(tab) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.MainTabItem(
    tab: MainTab,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) SelectedTabBackground else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, GrabberColors.Primary) else null
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = if (selected) GrabberColors.Primary else GrabberColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else GrabberColors.TextSecondary
                )
                if (badgeCount > 0) CountBadge(badgeCount)
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(GrabberColors.Success)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text = "$count", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}
