package com.grabber.app

/**
 * Protegge i valori sensibili delle impostazioni (password del ricevitore, token Telegram)
 * prima che vengano scritti su disco. Ogni piattaforma fornisce la propria implementazione:
 * DPAPI su Windows (`dpapi:`), Android Keystore su Android (`aks:`).
 */
interface SecretCipher {
    fun encrypt(plainText: String): String
    fun decrypt(storedValue: String): String

    /** Indica un valore salvato in chiaro da una versione precedente, da cifrare al prossimo salvataggio. */
    fun isUnprotected(storedValue: String): Boolean
}

/** Ripiego per sistemi senza un archivio sicuro: nessuna cifratura. */
object PlainTextSecretCipher : SecretCipher {
    override fun encrypt(plainText: String) = plainText
    override fun decrypt(storedValue: String) = storedValue
    override fun isUnprotected(storedValue: String) = false
}
