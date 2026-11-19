package com.grabber.ui.feature.settings

import com.grabber.app.AppLogger
import java.awt.Desktop
import java.net.URI

/** Pagine esterne aperte nel browser di sistema dalle Impostazioni. */
internal object ExternalLinks {
    /** Elenco e mappa pubblica dei ricevitori KiwiSDR. */
    const val KIWISDR_PUBLIC_MAP = "https://kiwisdr.com/public/"

    /** Mappa dei ricevitori SDR (KiwiSDR, WebSDR, OpenWebRX) di rx-tx.info. */
    const val RX_TX_SDR_MAP = "https://rx-tx.info/map-sdr-points"
}

/**
 * Apre [url] nel browser predefinito e restituisce il messaggio di esito da mostrare all'utente
 * (`✓ …` se riuscito, `✗ …` altrimenti). Gli errori vengono anche registrati nel log.
 *
 * @param description nome della pagina usato nei messaggi, per esempio "la mappa KiwiSDR".
 */
internal fun openInBrowser(url: String, description: String): String {
    return try {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            AppLogger.warn("ExternalLinks", "Apertura del browser non supportata: $url")
            "✗ Apertura del browser non supportata su questo sistema."
        } else {
            Desktop.getDesktop().browse(URI(url))
            "✓ Aperta nel browser: $description."
        }
    } catch (e: Exception) {
        AppLogger.warn("ExternalLinks", "Impossibile aprire $url", e)
        "✗ Impossibile aprire $description: ${e.message ?: "errore browser"}"
    }
}
