package com.grabber.ui.feature.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.grabber.app.FavoriteFrequency
import com.grabber.model.ModulationMode
import com.grabber.ui.designsystem.*
import java.util.Locale

internal data class FilterBandItem(
    val id: String,
    val label: String,
    val description: String? = null
)

internal val STANDARD_BAND_FILTERS = listOf(
    FilterBandItem("TUTTI", "Tutti i canali"),
    FilterBandItem("CB", "Banda CB (11m)", "26.9 - 27.4 MHz"),
    FilterBandItem("HAM", "Tutte le bande HAM", "Radioamatori"),
    FilterBandItem("160m", "160m", "1.8 - 2.0 MHz"),
    FilterBandItem("80m", "80m", "3.5 - 3.8 MHz"),
    FilterBandItem("60m", "60m", "5.3 MHz"),
    FilterBandItem("40m", "40m", "7.0 - 7.3 MHz"),
    FilterBandItem("30m", "30m", "10.1 MHz"),
    FilterBandItem("20m", "20m", "14.0 - 14.35 MHz"),
    FilterBandItem("17m", "17m", "18.1 MHz"),
    FilterBandItem("15m", "15m", "21.0 - 21.45 MHz"),
    FilterBandItem("12m", "12m", "24.9 MHz"),
    FilterBandItem("10m", "10m", "28.0 - 29.7 MHz"),
    FilterBandItem("Air", "Airband HF", "Aeronautica"),
    FilterBandItem("MW", "Onde Medie (MW)", "530 - 1600 kHz"),
    FilterBandItem("LW", "Onde Lunghe (LW)", "< 530 kHz")
)

private fun isHamFrequency(f: Double): Boolean {
    return f in 1800.0..2000.0 ||
            f in 3500.0..3850.0 ||
            f in 5250.0..5450.0 ||
            f in 7000.0..7300.0 ||
            f in 10100.0..10150.0 ||
            f in 14000.0..14350.0 ||
            f in 18068.0..18168.0 ||
            f in 21000.0..21450.0 ||
            f in 24890.0..24990.0 ||
            f in 28000.0..29700.0
}

internal fun matchesBandFilter(fav: FavoriteFrequency, filterId: String): Boolean {
    val f = fav.frequencyKhz
    val g = fav.group.uppercase().trim()
    val n = fav.name.uppercase()
    return when (filterId.uppercase()) {
        "TUTTI" -> true
        "CB", "11M CB", "11M" -> g == "CB" || n.contains("CB") || f in 26965.0..27405.0
        "HAM" -> g == "HAM" || n.contains("HAM") || isHamFrequency(f)
        "160M" -> n.contains("160M") || f in 1800.0..2000.0
        "80M" -> n.contains("80M") || f in 3500.0..3850.0
        "60M" -> n.contains("60M") || f in 5250.0..5450.0
        "40M" -> n.contains("40M") || f in 7000.0..7300.0
        "30M" -> n.contains("30M") || f in 10100.0..10150.0
        "20M" -> n.contains("20M") || f in 14000.0..14350.0
        "17M" -> n.contains("17M") || f in 18068.0..18168.0
        "15M" -> n.contains("15M") || f in 21000.0..21450.0
        "12M" -> n.contains("12M") || f in 24890.0..24990.0
        "10M" -> n.contains("10M") || f in 28000.0..29700.0
        "AIR" -> g == "AIR" || n.contains("AIR") || f in 11800.0..137000.0 || kotlin.math.abs(f - 11810.0) < 1.0
        "MW" -> g == "MW" || n.contains("MW") || f in 526.5..1606.5
        "LW" -> g == "LW" || n.contains("LW") || f < 526.5
        else -> g == filterId.uppercase().trim() || n.contains(filterId.uppercase())
    }
}

internal fun matchesSearchQuery(fav: FavoriteFrequency, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    val freqStr = fav.frequencyKhz.toString()
    val freqFormatted = String.format(Locale.US, "%.1f", fav.frequencyKhz)
    val freqInt = fav.frequencyKhz.toInt().toString()
    return fav.name.contains(q, ignoreCase = true) ||
            freqStr.contains(q) ||
            freqFormatted.contains(q) ||
            freqInt.contains(q) ||
            fav.mode.displayName.contains(q, ignoreCase = true) ||
            fav.group.contains(q, ignoreCase = true)
}

internal fun autoDetectGroup(freqKhz: Double): String {
    return when {
        freqKhz in 26965.0..27405.0 -> "CB"
        isHamFrequency(freqKhz) -> "HAM"
        freqKhz < 526.5 -> "LW"
        freqKhz in 526.5..1606.5 -> "MW"
        freqKhz in 11800.0..137000.0 -> "Air"
        else -> "DX"
    }
}

internal fun defaultVfoName(freqKhz: Double, mode: ModulationMode): String {
    return when {
        freqKhz in 26965.0..27405.0 -> {
            val ch = ((freqKhz - 26965.0) / 10.0).toInt() + 1
            "CB Ch $ch"
        }
        else -> "${mode.displayName} ${String.format(Locale.US, "%.1f", freqKhz)} kHz"
    }
}
