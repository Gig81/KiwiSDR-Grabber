package com.grabber.model

import kotlinx.serialization.Serializable

/**
 * Rappresenta un'etichetta/segnalibro DX fornito dal server KiwiSDR tramite il comando load_dxcfg.
 *
 * @param f Frequenza in kHz
 * @param o Offset (solitamente 0)
 * @param m Modalità: 1=AM, 2=LSB, 3=USB, 4=CW, 5=NFM
 * @param w Larghezza filtro / passband
 * @param i Nome identificativo / label del canale
 * @param g Gruppo / Categoria (es. LW, MW, HAM, CB)
 * @param p Passband offset
 * @param e Flag estesi
 */
@Serializable
data class KiwiDxLabel(
    val f: Double,
    val o: Double = 0.0,
    val m: Int = 1,
    val w: Double = 0.0,
    val i: String = "",
    val g: String = "",
    val p: Int = 0,
    val e: Int = 0
) {
    val mode: ModulationMode
        get() = when (m) {
            1 -> ModulationMode.AM
            2 -> ModulationMode.LSB
            3 -> ModulationMode.USB
            4 -> ModulationMode.CW
            5 -> ModulationMode.NFM
            else -> ModulationMode.AM
        }
}

