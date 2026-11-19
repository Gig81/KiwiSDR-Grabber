package com.grabber.model

/** Tema dell'interfaccia scelto dall'utente. */
enum class UiThemeMode(val displayName: String) {
    DARK("Scuro"),
    LIGHT("Chiaro"),

    /** Segue il tema chiaro/scuro del sistema operativo. */
    SYSTEM("Sistema");

    companion object {
        /** Valore salvato nelle impostazioni; un nome sconosciuto o vuoto torna al tema scuro. */
        fun fromName(name: String?): UiThemeMode = entries.firstOrNull { it.name == name } ?: DARK
    }
}
