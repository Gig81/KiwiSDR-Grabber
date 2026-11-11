package com.grabber.app

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.util.Base64

/**
 * Protegge i valori sensibili delle impostazioni (password del ricevitore, token Telegram)
 * prima che vengano scritti su disco.
 */
interface SecretCipher {
    fun encrypt(plainText: String): String
    fun decrypt(storedValue: String): String

    /** Indica un valore salvato in chiaro da una versione precedente, da cifrare al prossimo salvataggio. */
    fun isUnprotected(storedValue: String): Boolean

    companion object {
        fun forCurrentPlatform(): SecretCipher =
            if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
                DpapiSecretCipher
            } else {
                PlainTextSecretCipher
            }
    }
}

/**
 * Cifratura con Windows DPAPI (`CryptProtectData`) legata all'account Windows corrente:
 * il file delle impostazioni copiato su un altro utente o PC non rivela le credenziali.
 */
object DpapiSecretCipher : SecretCipher {
    private const val PREFIX = "dpapi:"
    private val entropy = "KiwiSDRRecorder:settings:v1".toByteArray(Charsets.UTF_8)

    override fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        val protectedBytes = Crypt32Util.cryptProtectData(
            plainText.toByteArray(Charsets.UTF_8),
            entropy,
            WinCrypt.CRYPTPROTECT_UI_FORBIDDEN,
            "",
            null
        )
        return PREFIX + Base64.getEncoder().encodeToString(protectedBytes)
    }

    override fun decrypt(storedValue: String): String {
        if (!storedValue.startsWith(PREFIX)) return storedValue
        val protectedBytes = Base64.getDecoder().decode(storedValue.removePrefix(PREFIX))
        val plainBytes = Crypt32Util.cryptUnprotectData(
            protectedBytes,
            entropy,
            WinCrypt.CRYPTPROTECT_UI_FORBIDDEN,
            null
        )
        return String(plainBytes, Charsets.UTF_8)
    }

    override fun isUnprotected(storedValue: String): Boolean =
        storedValue.isNotEmpty() && !storedValue.startsWith(PREFIX)
}

/** Ripiego per sistemi senza DPAPI: l'app è distribuita solo per Windows. */
object PlainTextSecretCipher : SecretCipher {
    override fun encrypt(plainText: String) = plainText
    override fun decrypt(storedValue: String) = storedValue
    override fun isUnprotected(storedValue: String) = false
}
