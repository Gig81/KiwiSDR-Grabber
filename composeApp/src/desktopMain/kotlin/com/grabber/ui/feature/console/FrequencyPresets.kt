package com.grabber.ui.feature.console

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.grabber.model.ModulationMode

enum class FrequencyStep(val deltaKhz: Double, val label: String) {
    STEP_10HZ(0.01, "10 Hz"),
    STEP_100HZ(0.1, "100 Hz"),
    STEP_500HZ(0.5, "500 Hz"),
    STEP_1KHZ(1.0, "1 kHz"),
    STEP_5KHZ(5.0, "5 kHz"),
    STEP_10KHZ(10.0, "10 kHz"),
    STEP_50KHZ(50.0, "50 kHz"),
    STEP_100KHZ(100.0, "100 kHz")
}

data class BandPreset(
    val bandName: String,
    val frequencyKhz: Double,
    val defaultMode: ModulationMode
)

val PRESET_BANDS = listOf(
    BandPreset("160m", 1840.0, ModulationMode.LSB),
    BandPreset("80m", 3650.0, ModulationMode.LSB),
    BandPreset("40m", 7100.0, ModulationMode.LSB),
    BandPreset("30m", 10120.0, ModulationMode.CW),
    BandPreset("20m", 14200.0, ModulationMode.USB),
    BandPreset("17m", 18130.0, ModulationMode.USB),
    BandPreset("15m", 21250.0, ModulationMode.USB),
    BandPreset("12m", 24950.0, ModulationMode.USB),
    BandPreset("11m CB", 27205.0, ModulationMode.AM),
    BandPreset("10m", 28400.0, ModulationMode.USB),
    BandPreset("Air", 11810.0, ModulationMode.AM),
    BandPreset("MW", 900.0, ModulationMode.AM)
)
