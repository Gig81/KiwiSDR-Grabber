package com.grabber.ui.feature.spectrum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.model.ConnectionState
import com.grabber.model.ModulationMode
import com.grabber.ui.designsystem.AppMenuButton
import com.grabber.ui.designsystem.AppSegmentedControl
import com.grabber.ui.designsystem.AppToggleButton
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.OutlinedButton
import com.grabber.ui.designsystem.SecondaryButton
import com.grabber.ui.designsystem.StatusBadge
import com.grabber.waterfall.WaterfallClient
import com.grabber.waterfall.WaterfallPaletteType
import java.util.Locale
import kotlin.math.roundToInt

private const val DEFAULT_FLOOR_DBM = -120
private const val DEFAULT_CEILING_DBM = -50
private val WATERFALL_SPEEDS_HZ = listOf(1, 2, 4)

/** Barra dei controlli primari della scheda Spettro & Cascata. */
@Composable
internal fun WaterfallToolbar(
    controller: KiwiAppController,
    waterfallState: ConnectionState,
    connectionState: ConnectionState,
    currentZoom: Int,
    currentPalette: WaterfallPaletteType,
    isPaused: Boolean,
    autoFollow: Boolean,
    currentVfoKhz: Double,
    currentMode: ModulationMode
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WaterfallTitle("SPETTRO & WATERFALL (W/F)", iconSize = 15)
            WaterfallStatusBadge(connectionState, waterfallState, isPaused)
            MonoReadout {
                Text(
                    text = "VFO: ${String.format(Locale.US, "%.3f", currentVfoKhz)} kHz",
                    fontSize = 9.sp,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(text = currentMode.displayName, fontSize = 9.sp, color = GrabberColors.Primary, fontWeight = FontWeight.Bold)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ZoomStepButton("-", enabled = currentZoom > 0, onClick = controller::waterfallZoomOut)
            ZoomSpanReadout(currentZoom, fontSize = 10.sp)
            ZoomStepButton("+", enabled = currentZoom < WaterfallClient.MAX_ZOOM, onClick = controller::waterfallZoomIn)
            Spacer(modifier = Modifier.width(2.dp))
            OutlinedButton(
                text = "Centra VFO",
                onClick = { controller.setWaterfallCenterFrequency(currentVfoKhz) },
                icon = Icons.Default.MyLocation,
                iconTint = GrabberColors.Primary,
                size = ControlSize.Mini,
                contentColor = Color.White
            )
            AppToggleButton(
                text = "Insegui VFO",
                checked = autoFollow,
                onClick = { controller.setWaterfallAutoFollow(!autoFollow) },
                icon = if (autoFollow) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                accentColor = GrabberColors.Success
            )
            Spacer(modifier = Modifier.width(2.dp))
            AppMenuButton(
                selected = currentPalette,
                options = WaterfallPaletteType.entries,
                label = { it.displayName },
                onSelect = controller::setWaterfallPalette,
                icon = Icons.Default.Palette
            )
            AppToggleButton(
                text = if (isPaused) "Riprendi" else "Pausa",
                checked = isPaused,
                onClick = controller::toggleWaterfallPause,
                icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                accentColor = GrabberColors.Warning
            )
            OutlinedButton(
                text = "Pulisci",
                onClick = controller::clearWaterfall,
                icon = Icons.Default.DeleteOutline,
                size = ControlSize.Mini,
                contentColor = Color.LightGray
            )
        }
    }
}

/** Barra ridotta del mini spettro nella console: solo titolo, stato e span, senza comandi. */
@Composable
internal fun MiniWaterfallToolbar(
    waterfallState: ConnectionState,
    connectionState: ConnectionState,
    currentZoom: Int,
    isPaused: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WaterfallTitle("SPETTRO & WATERFALL", iconSize = 14)
            WaterfallStatusBadge(connectionState, waterfallState, isPaused)
        }
        ZoomSpanReadout(currentZoom, fontSize = 9.sp)
    }
}

/** Floor e ceiling in dBm della scala colori e velocità di aggiornamento del waterfall. */
@Composable
internal fun WaterfallDynamicRangeBar(
    controller: KiwiAppController,
    currentMinDb: Int,
    currentMaxDb: Int,
    speed: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GrabberColors.Surface,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DbmSlider(
                label = "Floor:",
                valueDbm = currentMinDb,
                valueWidth = 55,
                accentColor = GrabberColors.Primary,
                valueRange = -140f..-40f,
                steps = 99,
                onValueChange = { controller.setWaterfallDbRange(it, currentMaxDb) }
            )
            ToolbarDivider()
            DbmSlider(
                label = "Ceiling:",
                valueDbm = currentMaxDb,
                valueWidth = 50,
                accentColor = GrabberColors.Error,
                valueRange = -50f..0f,
                steps = 49,
                onValueChange = { controller.setWaterfallDbRange(currentMinDb, it) }
            )
            ToolbarDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Velocità:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                AppSegmentedControl(
                    options = WATERFALL_SPEEDS_HZ.map { "$it Hz" },
                    selectedIndex = WATERFALL_SPEEDS_HZ.indexOf(speed),
                    onSelectionChange = { controller.setWaterfallSpeed(WATERFALL_SPEEDS_HZ[it]) },
                    size = ControlSize.Dense,
                    accentColor = GrabberColors.Success
                )
            }
            ToolbarDivider()
            OutlinedButton(
                text = "Reset dBm",
                onClick = { controller.setWaterfallDbRange(DEFAULT_FLOOR_DBM, DEFAULT_CEILING_DBM) },
                size = ControlSize.Mini,
                contentColor = Color.LightGray
            )
        }
    }
}

@Composable
private fun WaterfallTitle(title: String, iconSize: Int) {
    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = GrabberColors.Primary, modifier = Modifier.size(iconSize.dp))
    Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
}

/** Stato del flusso W/F: disconnesso, in pausa, live o in connessione. */
@Composable
private fun WaterfallStatusBadge(connectionState: ConnectionState, waterfallState: ConnectionState, isPaused: Boolean) {
    val (color, text) = when {
        connectionState !is ConnectionState.Connected -> Color.Gray to "DISCONNESSO"
        isPaused -> GrabberColors.Warning to "IN PAUSA"
        waterfallState is ConnectionState.Connected -> GrabberColors.Success to "LIVE (1024 BIN)"
        else -> GrabberColors.Warning to "CONNESSIONE..."
    }
    StatusBadge(text = text, color = color, size = ControlSize.Compact)
}

/** Livello di zoom e ampiezza della finestra visualizzata (es. "Z3 (3.8 MHz)"). */
@Composable
private fun ZoomSpanReadout(currentZoom: Int, fontSize: TextUnit) {
    val spanMhz = WaterfallClient.calculateSpanKhz(currentZoom) / 1000.0
    val spanText = if (spanMhz >= 1.0) {
        String.format(Locale.US, "Z%d (%.1f MHz)", currentZoom, spanMhz)
    } else {
        String.format(Locale.US, "Z%d (%.0f kHz)", currentZoom, spanMhz * 1000.0)
    }
    MonoReadout {
        Text(text = spanText, fontSize = fontSize, color = GrabberColors.Primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MonoReadout(content: @Composable RowScope.() -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(GrabberColors.Surface)
            .border(1.dp, GrabberColors.Border, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun ZoomStepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    SecondaryButton(
        text = symbol,
        onClick = onClick,
        enabled = enabled,
        size = ControlSize.Mini,
        modifier = Modifier.width(24.dp)
    )
}

@Composable
private fun RowScope.DbmSlider(
    label: String,
    valueDbm: Int,
    valueWidth: Int,
    accentColor: Color,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.weight(1.2f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
        Text(
            "$valueDbm dBm",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.width(valueWidth.dp)
        )
        Slider(
            value = valueDbm.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f).height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = GrabberColors.SurfaceElevated
            )
        )
    }
}

@Composable
private fun ToolbarDivider() {
    VerticalDivider(modifier = Modifier.height(18.dp), color = GrabberColors.Border)
}
