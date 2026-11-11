package com.grabber.ui.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.ui.designsystem.*

/**
 * Card Accordion/Collassabile per le impostazioni conforme al Design System.
 */
@Composable
internal fun CollapsibleSettingsCard(
    title: String,
    icon: ImageVector,
    iconTint: Color = GrabberColors.Secondary,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    badge: @Composable (() -> Unit)? = null,
    locked: Boolean = false,
    onActivate: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GrabberShapes.control),
        color = GrabberColors.SurfaceElevated,
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = iconTint)
                        if (subtitle != null && !isExpanded) {
                            Text(subtitle, fontSize = 9.sp, color = GrabberColors.TextSecondary, maxLines = 1)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (badge != null) {
                        badge()
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Comprimi" else "Espandi",
                        tint = GrabberColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isExpanded) {
                AppDivider()
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = content
                    )
                    if (locked) {
                        FeatureLockedOverlay(
                            modifier = Modifier.padding(10.dp),
                            onActivate = onActivate
                        )
                    }
                }
            }
        }
    }
}
