package com.grabber.decoder

import com.grabber.kiwi.ReceiverConfig
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeAvailability

/**
 * Disponibilità dei modi sul ricevitore corrente: estensioni installate (`extint_list_json` su W/F),
 * abilitazioni dell'amministratore (`load_cfg`), limite `ext_api_nchans` e frequenza del canale.
 * I decoder locali dipendono solo dalla connessione.
 */
object DigitalModeAvailabilityRules {
    const val REASON_DISCONNECTED = "Connettiti a un ricevitore."
    const val REASON_NOT_INSTALLED = "Estensione non installata sul ricevitore."
    const val REASON_DISABLED = "Estensione disabilitata dall'amministratore del ricevitore."
    const val REASON_REQUIRES_12K = "Richiede un ricevitore in modalità 12 kHz."
    const val REASON_NOT_IMPLEMENTED = "Non ancora disponibile in questa versione."

    fun compute(
        connected: Boolean,
        installedExtensions: Set<String>?,
        config: ReceiverConfig?,
        sampleRate: Int?,
        implemented: (DigitalMode) -> Boolean = { true }
    ): List<DigitalModeAvailability> = DigitalMode.entries.map { mode ->
        DigitalModeAvailability(mode, reasonFor(mode, connected, installedExtensions, config, sampleRate, implemented))
    }

    private fun reasonFor(
        mode: DigitalMode,
        connected: Boolean,
        installed: Set<String>?,
        config: ReceiverConfig?,
        sampleRate: Int?,
        implemented: (DigitalMode) -> Boolean
    ): String? {
        if (!implemented(mode)) return REASON_NOT_IMPLEMENTED
        if (!connected) return REASON_DISCONNECTED
        if (!mode.isServer) return null
        val extension = mode.extensionName ?: return null
        // Elenco non ancora ricevuto (waterfall chiuso): si prova comunque, l'esito arriva da /EXT.
        if (installed != null && extension !in installed) return REASON_NOT_INSTALLED
        val key = mode.configKey
        if (config != null && key != null && !config.isExtensionEnabled(key)) return REASON_DISABLED
        // ext_api_nchans = 0 non blocca qui: vale solo per gli IP non locali e il rifiuto arriva da /EXT.
        if (mode.requires12kHz && sampleRate != null && sampleRate > 13000) return REASON_REQUIRES_12K
        return null
    }
}
