package com.grabber.model

/**
 * Canale (etichetta DX) memorizzato sul ricevitore KiwiSDR, ricevuto con il messaggio `mkr`
 * in risposta a `SET MARKER` (vedi [com.grabber.kiwi.KiwiProtocol.dxMarkers]).
 *
 * @param id Indice dell'etichetta nel database DX del ricevitore (`g`).
 * @param frequencyKhz Frequenza in kHz (`f`).
 * @param mode Modalità ricavata dai bit di `fl`, ricondotta alle modalità supportate dall'app.
 * @param name Identificativo già decodificato (`i`); può essere vuoto.
 * @param notes Note già decodificate (`n`); può essere vuoto.
 */
data class KiwiDxLabel(
    val id: Int,
    val frequencyKhz: Double,
    val mode: ModulationMode,
    val name: String,
    val notes: String = ""
) {
    companion object {
        private const val DX_MODE_MASK = 0x0000000f
        private const val DX_MODE_16 = 0x00040000

        /**
         * Il modo è un indice nell'elenco `modes_lc` del client web KiwiSDR:
         * am, amn, usb, lsb, cw, cwn, nbfm, iq, drm, usn, lsn, sam, sau, sal, sas, qam, nnfm, amw.
         */
        fun modeFromFlags(flags: Int): ModulationMode {
            val index = (if (flags and DX_MODE_16 != 0) 16 else 0) or (flags and DX_MODE_MASK)
            return when (index) {
                2, 9, 12 -> ModulationMode.USB
                3, 10, 13 -> ModulationMode.LSB
                4, 5 -> ModulationMode.CW
                6, 16 -> ModulationMode.NFM
                else -> ModulationMode.AM
            }
        }
    }
}
