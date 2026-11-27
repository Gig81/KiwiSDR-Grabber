package com.grabber.ui.feature.digital

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.model.ModulationMode
import com.grabber.model.digital.DecodedImage
import com.grabber.model.digital.DecodedItem
import com.grabber.model.digital.DecodedTextLine
import com.grabber.model.digital.DecoderOutputKind
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.DscCall
import com.grabber.model.digital.Ft8Message
import com.grabber.model.digital.NavtexMessage
import com.grabber.model.digital.SkimmerSignal
import com.grabber.model.digital.WsprSpot
import com.grabber.ui.designsystem.AppEmptyState
import com.grabber.ui.designsystem.AppVerticalScrollbar
import com.grabber.ui.designsystem.DisplayColors
import com.grabber.ui.designsystem.GrabberColors
import java.awt.image.BufferedImage
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val utcTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC)

/** Vero se l'uscita mostrata appartiene a [mode] (decoder attivo o ultimo fermato). */
@Composable
private fun showsOutputOf(controller: KiwiAppController, mode: DigitalMode): Boolean {
    val state by controller.digitalDecoderState.collectAsState()
    val settings by controller.settings.collectAsState()
    return state.activeMode == mode || (state.activeMode == null && settings.lastDigitalMode == mode)
}

/** Uscita a elementi: tabella FT8/WSPR, testo continuo, skimmer, messaggi NAVTEX/DSC. */
@Composable
internal fun DecoderItemsOutput(
    controller: KiwiAppController,
    mode: DigitalMode,
    params: DigitalModeParams,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val allItems by controller.decodedItems.collectAsState()
    val visible = showsOutputOf(controller, mode)
    val items = when {
        !visible -> emptyList()
        (mode == DigitalMode.FT8 || mode == DigitalMode.FT4) && params.ft8OnlyCq ->
            allItems.filter { it !is Ft8Message || it.isCq }
        mode == DigitalMode.NAVTEX -> allItems.filter { item ->
            item !is NavtexMessage ||
                ((params.navtexStations.isEmpty() || item.station in params.navtexStations) &&
                    (params.navtexTypes.isEmpty() || item.type in params.navtexTypes))
        }
        else -> allItems
    }
    OutputFrame(modifier) {
        if (items.isEmpty()) {
            AppEmptyState(
                icon = Icons.Default.Notes,
                message = if (isActive) "In ascolto: le decodifiche compariranno qui." else "Avvia il decoder per vedere le decodifiche."
            )
        } else {
            when (mode.output) {
                DecoderOutputKind.TABLE -> if (mode == DigitalMode.WSPR) WsprTable(items) else Ft8Table(items)
                DecoderOutputKind.SKIMMER -> SkimmerList(items) { freq -> controller.tune(freq, ModulationMode.CW) }
                DecoderOutputKind.MESSAGES -> MessageList(items)
                else -> TextOutput(items)
            }
        }
    }
}

@Composable
private fun OutputFrame(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = GrabberColors.SurfaceVariant,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(6.dp)) { content() }
    }
}

/** Elenco con scorrimento automatico in fondo, sospeso se l'utente risale. */
@Composable
private fun AutoScrollList(
    count: Int,
    header: (@Composable () -> Unit)? = null,
    content: @Composable (index: Int) -> Unit
) {
    val state = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= state.layoutInfo.totalItemsCount - 2
        }
    }
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(atBottom) { follow = atBottom }
    LaunchedEffect(count) { if (follow && count > 0) state.scrollToItem(count - 1) }
    Column(modifier = Modifier.fillMaxSize()) {
        header?.invoke()
        Box(modifier = Modifier.fillMaxSize()) {
            ListBody(state, count, content)
            AppVerticalScrollbar(
                adapter = rememberScrollbarAdapter(state),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ListBody(state: LazyListState, count: Int, content: @Composable (Int) -> Unit) {
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
        itemsIndexed(List(count) { it }) { index, _ -> content(index) }
    }
}

@Composable
private fun Cell(text: String, width: Int? = null, modifier: Modifier = Modifier, bold: Boolean = false, color: androidx.compose.ui.graphics.Color = GrabberColors.TextPrimary) {
    Text(
        text = text,
        modifier = if (width != null) modifier.width(width.dp) else modifier,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HeaderRow(vararg columns: Pair<String, Int?>) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        columns.forEach { (title, width) ->
            Cell(title, width, if (width == null) Modifier.weight(1f) else Modifier, bold = true, color = GrabberColors.TextSecondary)
        }
    }
}

@Composable
private fun Ft8Table(items: List<DecodedItem>) {
    val clipboard = LocalClipboardManager.current
    AutoScrollList(
        count = items.size,
        header = { HeaderRow("UTC" to 64, "SNR" to 40, "dT" to 40, "Hz" to 40, "Messaggio" to null) }
    ) { index ->
        when (val item = items[index]) {
            is Ft8Message -> Row(
                modifier = Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(item.message)) }.padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Cell(item.utc, 64)
                Cell(String.format(Locale.US, "%+.0f", item.snrDb), 40)
                Cell(String.format(Locale.US, "%+.1f", item.dtSeconds), 40)
                Cell("${item.audioHz}", 40)
                Cell(item.message, modifier = Modifier.weight(1f), bold = item.isCq, color = if (item.isCq) GrabberColors.Success else GrabberColors.TextPrimary)
            }
            else -> StatusLine(item)
        }
    }
}

@Composable
private fun WsprTable(items: List<DecodedItem>) {
    val clipboard = LocalClipboardManager.current
    AutoScrollList(
        count = items.size,
        header = { HeaderRow("UTC" to 44, "SNR" to 34, "MHz" to 80, "Drift" to 36, "Nominativo" to 90, "Locatore" to 64, "Potenza" to null) }
    ) { index ->
        when (val item = items[index]) {
            is WsprSpot -> Row(
                modifier = Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(item.plainText)) }.padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Cell(item.utc, 44)
                Cell("${item.snrDb}", 34)
                Cell(String.format(Locale.US, "%.6f", item.frequencyMhz), 80)
                Cell("${item.drift}", 36)
                Cell(item.callsign, 90, bold = true)
                Cell(item.locator.orEmpty(), 64)
                Cell(listOfNotNull(item.powerDbm?.let { "$it dBm" }, item.powerLabel).joinToString(" · "), modifier = Modifier.weight(1f))
            }
            else -> StatusLine(item)
        }
    }
}

@Composable
private fun StatusLine(item: DecodedItem) {
    Text(item.plainText, fontSize = 10.sp, color = GrabberColors.TextSecondary, fontFamily = FontFamily.Monospace, maxLines = 1)
}

/** Testo continuo in monospazio (CW, RTTY, HFDL, ALE). */
@Composable
private fun TextOutput(items: List<DecodedItem>) {
    AutoScrollList(count = items.size) { index ->
        val item = items[index]
        if (item is DecodedTextLine && item.isStatus) {
            StatusLine(item)
        } else {
            Text(item.plainText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = GrabberColors.TextPrimary)
        }
    }
}

/** CW Skimmer: una riga per segnale; il clic sintonizza il segnale in CW. */
@Composable
private fun SkimmerList(items: List<DecodedItem>, onTune: (Double) -> Unit) {
    val signals = items.filterIsInstance<SkimmerSignal>()
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderRow("kHz" to 70, "WPM" to 36, "Testo" to null)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            signals.forEach { signal ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onTune(signal.frequencyKhz) }.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Cell(String.format(Locale.US, "%.1f", signal.frequencyKhz), 70, bold = true, color = GrabberColors.Primary)
                    Cell(if (signal.wpm > 0) "${signal.wpm}" else "–", 36)
                    Cell(signal.text, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Messaggi NAVTEX/DSC espandibili, dal più recente. */
@Composable
private fun MessageList(items: List<DecodedItem>) {
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    val messages = items.reversed()
    val state = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(messages) { _, item ->
                val key = item.receivedAtMillis
                val isOpen = key in expanded
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(GrabberColors.Surface, RoundedCornerShape(3.dp))
                        .clickable { expanded = if (isOpen) expanded - key else expanded + key }
                        .padding(6.dp)
                ) {
                    val time = utcTime.format(Instant.ofEpochMilli(item.receivedAtMillis))
                    when (item) {
                        is NavtexMessage -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Cell(time, 64, color = GrabberColors.TextSecondary)
                                Cell(item.id, 50, bold = true, color = GrabberColors.Primary)
                                Cell(item.typeDescription, modifier = Modifier.weight(1f))
                                if (item.errorPercent > 0) Cell("err ${item.errorPercent}%", 60, color = GrabberColors.Warning)
                                if (item.incomplete) Cell("incompleto", 76, color = GrabberColors.Warning)
                            }
                            Text(
                                item.body,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = GrabberColors.TextPrimary,
                                maxLines = if (isOpen) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is DscCall -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Cell(time, 64, color = GrabberColors.TextSecondary)
                            Cell(item.plainText, modifier = Modifier.weight(1f))
                        }
                        else -> StatusLine(item)
                    }
                }
            }
        }
        AppVerticalScrollbar(adapter = rememberScrollbarAdapter(state), modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Immagine FAX/SSTV in costruzione, adattata alla larghezza (display strumentale, scuro in ogni tema). */
@Composable
internal fun DecoderImageOutput(controller: KiwiAppController, mode: DigitalMode, isActive: Boolean, modifier: Modifier = Modifier) {
    val image by controller.decodedImage.collectAsState()
    val visible = showsOutputOf(controller, mode)
    val current = image?.takeIf { visible && it.rows.isNotEmpty() }
    Box(modifier = modifier.background(DisplayColors.SpectrumBackground, RoundedCornerShape(4.dp))) {
        if (current == null) {
            AppEmptyState(
                icon = Icons.Default.Image,
                message = if (isActive) "In attesa dell'immagine…" else "Avvia il decoder per ricevere le immagini."
            )
        } else {
            val bitmap = remember(current.version) { current.toBufferedImage().toComposeImageBitmap() }
            val scroll = rememberScrollState()
            LaunchedEffect(current.height) { scroll.scrollTo(scroll.maxValue) }
            Box(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                Image(
                    bitmap = bitmap,
                    contentDescription = current.title,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                    filterQuality = FilterQuality.Medium
                )
            }
            Text(
                "${current.title} · ${current.width}×${current.height}",
                fontSize = 10.sp,
                color = DisplayColors.Text,
                modifier = Modifier.align(Alignment.TopEnd).background(DisplayColors.LabelBackdrop).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun DecodedImage.toBufferedImage(): BufferedImage {
    val buffered = BufferedImage(width.coerceAtLeast(1), rows.size.coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
    rows.forEachIndexed { y, row ->
        val w = minOf(row.size, width)
        if (w > 0) buffered.setRGB(0, y, w, 1, row, 0, w)
    }
    return buffered
}
