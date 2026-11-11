package com.grabber.model

/**
 * Modalità di demodulazione supportate da KiwiSDR.
 *
 * La scelta della modalità determina la banda passante e i valori di low/high cut che
 * vengono inviati al server per adattare il filtro di ricezione al tipo di segnale atteso.
 */
enum class ModulationMode(
    val code: String,
    val displayName: String,
    val defaultLowCut: Int,
    val defaultHighCut: Int
) {
    AM("am", "AM", -4000, 4000),
    USB("usb", "USB", 300, 2700),
    LSB("lsb", "LSB", -2700, -300),
    CW("cw", "CW", 300, 700),
    NFM("nbfm", "NFM", -6000, 6000)
}

