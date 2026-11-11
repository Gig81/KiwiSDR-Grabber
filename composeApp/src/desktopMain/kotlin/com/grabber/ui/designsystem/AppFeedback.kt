package com.grabber.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Stato vuoto centrato di una lista. Senza `detail` il messaggio è secondario;
 * con `detail` il messaggio diventa il titolo e `detail` la spiegazione.
 */
@Composable
fun AppEmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    iconSize: Dp = 32.dp,
    action: (@Composable () -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GrabberColors.TextSecondary.copy(alpha = if (detail == null) 0.6f else 0.5f),
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = message,
                color = if (detail == null) GrabberColors.TextSecondary else GrabberColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = if (detail == null) FontWeight.Normal else FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (detail != null) {
                Text(text = detail, color = GrabberColors.TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
            }
            action?.invoke()
        }
    }
}

/** Scrollbar verticale sottile delle liste e dei pannelli scorrevoli. */
@Composable
fun AppVerticalScrollbar(adapter: ScrollbarAdapter, modifier: Modifier = Modifier) {
    VerticalScrollbar(
        adapter = adapter,
        modifier = modifier,
        style = defaultScrollbarStyle().copy(
            unhoverColor = Color.White.copy(alpha = 0.2f),
            hoverColor = GrabberColors.Primary,
            shape = RoundedCornerShape(4.dp),
            thickness = 6.dp
        )
    )
}

/** Avviso in linea (es. dato duplicato), con icona e testo nel colore indicato. */
@Composable
fun AppNotice(text: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(GrabberShapes.control),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(text = text, fontSize = 10.sp, color = color)
        }
    }
}

/** Campo di ricerca con icona e pulsante per cancellare il testo. */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Cerca..."
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(32.dp),
        singleLine = true,
        textStyle = GrabberTypography.body.copy(color = GrabberColors.TextPrimary, fontSize = 12.sp),
        cursorBrush = SolidColor(GrabberColors.Primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(GrabberShapes.control))
                    .background(GrabberColors.SurfaceVariant)
                    .border(1.dp, GrabberColors.Border, RoundedCornerShape(GrabberShapes.control))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = "Cerca", tint = GrabberColors.TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, fontSize = 11.sp, color = GrabberColors.TextDisabled)
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancella",
                        tint = GrabberColors.TextSecondary,
                        modifier = Modifier.size(14.dp).clickable { onValueChange("") }
                    )
                }
            }
        }
    )
}

/** Colonna che scorre verticalmente con la scrollbar comune, per pannelli con molti controlli. */
@Composable
fun AppScrollableColumn(
    modifier: Modifier = Modifier,
    contentSpacing: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(end = 6.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content
        )
        AppVerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}
