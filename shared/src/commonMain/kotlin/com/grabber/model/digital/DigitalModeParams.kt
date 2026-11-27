package com.grabber.model.digital

import kotlinx.serialization.Serializable

/** Bit di stop di una trasmissione asincrona ITA2 (5 bit di dati, nessuna parità). */
@Serializable
enum class FskFraming(val label: String, val stopBits: Double) {
    STOP_1("5N1", 1.0),
    STOP_1_5("5N1.5", 1.5),
    STOP_2("5N2", 2.0)
}

/** Preset RTTY/FSK; [CUSTOM] usa i valori impostati dall'utente. */
@Serializable
enum class FskPreset(val label: String) {
    HAM("Radioamatori 170 Hz / 45,45 Bd"),
    DWD("Meteo DWD 450 Hz / 50 Bd"),
    CUSTOM("Personalizzato")
}

/**
 * Parametri RTTY/FSK. [inverted] scambia mark e space: senza inversione mark è il tono più alto
 * (in CW/USB la frequenza RF più alta), come nelle emissioni radioamatoriali.
 * [centerHz] è il centro audio del segnale, dove cade la frequenza mostrata.
 */
@Serializable
data class FskParams(
    val preset: FskPreset = FskPreset.HAM,
    val shiftHz: Int = 170,
    val baud: Double = 45.45,
    val inverted: Boolean = false,
    val framing: FskFraming = FskFraming.STOP_1_5,
    val centerHz: Int = 1000
) {
    fun normalized(): FskParams = copy(
        shiftHz = shiftHz.coerceIn(MIN_SHIFT_HZ, MAX_SHIFT_HZ),
        baud = baud.coerceIn(MIN_BAUD, MAX_BAUD),
        centerHz = centerHz.coerceIn(MIN_CENTER_HZ, MAX_CENTER_HZ)
    )

    companion object {
        const val MIN_SHIFT_HZ = 50
        const val MAX_SHIFT_HZ = 1000
        const val MIN_BAUD = 20.0
        const val MAX_BAUD = 300.0
        const val MIN_CENTER_HZ = 500
        const val MAX_CENTER_HZ = 2500

        fun forPreset(preset: FskPreset, current: FskParams = FskParams()): FskParams = when (preset) {
            FskPreset.HAM -> FskParams(FskPreset.HAM, 170, 45.45, false, FskFraming.STOP_1_5, 1000)
            // DWD: mark sul tono basso (verificato sulla registrazione di 10100,8 kHz e nei menu del client web).
            FskPreset.DWD -> FskParams(FskPreset.DWD, 450, 50.0, true, FskFraming.STOP_1_5, 1000)
            FskPreset.CUSTOM -> current.copy(preset = FskPreset.CUSTOM)
        }
    }
}

/**
 * Parametri dei decoder, salvati nelle impostazioni (tutti con valori predefiniti, così i file
 * `settings.json` precedenti restano validi).
 */
@Serializable
data class DigitalModeParams(
    /** FT8/FT4: mostra solo i messaggi che iniziano con CQ (filtro lato app). */
    val ft8OnlyCq: Boolean = false,
    val cwAutoWpm: Boolean = true,
    val cwWpm: Int = 20,
    val cwAutoThreshold: Boolean = true,
    val cwThresholdDb: Int = 47,
    val cwWordSpaceCorrection: Boolean = true,
    /** CW Skimmer: 0 rapporto medio, 1 fondo medio, 2 soglia. */
    val skimmerPowerCalc: Int = 0,
    val skimmerFilterNeighbors: Boolean = true,
    val faxLpm: Int = 120,
    val faxPhasing: Boolean = true,
    val faxAutoStop: Boolean = true,
    val faxAutoSave: Boolean = false,
    val sstvAutoAdjust: Boolean = true,
    val sstvAutoSave: Boolean = false,
    val rtty: FskParams = FskParams(),
    /** NAVTEX: stazioni (lettera B1) da mostrare, vuoto = tutte. */
    val navtexStations: String = "",
    /** NAVTEX: tipi di messaggio (lettera B2) da mostrare, vuoto = tutti. */
    val navtexTypes: String = "",
    val navtexDiscardDuplicates: Boolean = true
) {
    fun normalized(): DigitalModeParams = copy(
        cwWpm = cwWpm.coerceIn(5, 60),
        cwThresholdDb = cwThresholdDb.coerceIn(20, 80),
        skimmerPowerCalc = skimmerPowerCalc.coerceIn(0, 2),
        faxLpm = if (faxLpm in com.grabber.kiwi.ExtProtocol.Fax.LPM_VALUES) faxLpm else 120,
        rtty = rtty.normalized(),
        navtexStations = navtexStations.filter { it.isLetter() }.uppercase(),
        navtexTypes = navtexTypes.filter { it.isLetter() }.uppercase()
    )
}
