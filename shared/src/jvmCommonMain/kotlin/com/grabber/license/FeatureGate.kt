package com.grabber.license

/** Funzioni riservate alla versione attivata (vedi `docs/ARCHITECTURE.md` §4.10). */
enum class GatedFeature(val description: String) {
    BACKGROUND_PLAYBACK("L'ascolto in background e a schermo spento"),
    MANUAL_RECORDING("La registrazione"),
    PERSONAL_FAVORITES("La gestione dei preferiti personali");

    /** Messaggio mostrato quando la funzione viene richiesta senza attivazione. */
    val lockedMessage: String get() = "$description richiede l'attivazione della licenza (gratuita)."
}

/**
 * Decide se una funzione è disponibile in base all'esito della verifica della licenza.
 * Va interrogato dal controller prima di ogni azione, non solo dalla UI.
 */
class FeatureGate(private val isLicensed: () -> Boolean) {
    fun isEnabled(feature: GatedFeature): Boolean = isLicensed()

    val isActivated: Boolean get() = isLicensed()
}
