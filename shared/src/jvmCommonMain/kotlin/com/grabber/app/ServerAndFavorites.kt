package com.grabber.app

import com.grabber.kiwi.KiwiServerDefaults
import com.grabber.model.ModulationMode
import kotlinx.serialization.Serializable

@Serializable
data class FavoriteFrequency(
    val name: String,
    val frequencyKhz: Double,
    val modeCode: String, // "am", "usb", "lsb", "cw", "nbfm"
    val group: String = "", // "LW", "MW", "HAM", "CB", "BC", ecc.
    val scanEnabled: Boolean = false // Incluso nella scansione dei canali
) {
    val mode: ModulationMode get() = ModulationMode.entries.find { it.code == modeCode } ?: ModulationMode.AM
}

const val DEFAULT_SERVER_ADDRESS = KiwiServerDefaults.ADDRESS

fun parseServerAddress(address: String): Pair<String, Int> {
    var clean = address.trim()
    if (clean.startsWith("http://", ignoreCase = true)) clean = clean.substring(7)
    if (clean.startsWith("https://", ignoreCase = true)) clean = clean.substring(8)
    if (clean.startsWith("ws://", ignoreCase = true)) clean = clean.substring(5)
    if (clean.startsWith("wss://", ignoreCase = true)) clean = clean.substring(6)
    while (clean.endsWith("/")) clean = clean.substring(0, clean.length - 1)

    val parts = clean.split(":")
    val host = parts[0].trim().ifEmpty { KiwiServerDefaults.HOST }
    val port = if (parts.size > 1) parts[1].filter { it.isDigit() }.toIntOrNull() ?: KiwiServerDefaults.PORT else KiwiServerDefaults.PORT
    return Pair(host, port)
}

/** Gruppo di banda proposto per un canale aggiunto dall'utente. */
fun bandGroupFor(freqKhz: Double): String = when {
    freqKhz in 26965.0..27405.0 -> "CB"
    freqKhz in 1800.0..2000.0 || freqKhz in 3500.0..3800.0 || freqKhz in 7000.0..7200.0 ||
        freqKhz in 14000.0..14350.0 || freqKhz in 21000.0..21450.0 || freqKhz in 28000.0..29700.0 -> "HAM"
    freqKhz < 530.0 -> "LW"
    freqKhz in 530.0..1700.0 -> "MW"
    else -> "DX"
}
