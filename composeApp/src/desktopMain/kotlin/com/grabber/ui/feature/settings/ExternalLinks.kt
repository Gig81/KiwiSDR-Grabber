package com.grabber.ui.feature.settings

import com.grabber.app.AppLogger
import com.grabber.kiwi.ReceiverMaps
import java.awt.Desktop
import java.net.URI

/** Pagine esterne aperte nel browser di sistema dalle Impostazioni (URL condivisi con Android). */
internal object ExternalLinks {
    const val KIWISDR_PUBLIC_MAP = ReceiverMaps.KIWISDR_PUBLIC_MAP
    const val RX_TX_SDR_MAP = ReceiverMaps.RX_TX_SDR_MAP
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
