package com.grabber.mobile

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.grabber.app.SecretCipher
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra i segreti (password del ricevitore) con una chiave AES-GCM dell'Android Keystore,
 * non esportabile: un backup ripristinato su un altro dispositivo non è decifrabile.
 * Formato: `aks:` + base64(IV di 12 byte + testo cifrato con tag).
 */
class KeystoreSecretCipher(private val keyAlias: String = "kiwigrabber_settings_v1") : SecretCipher {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    override fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(cipher.iv + encrypted)
    }

    override fun decrypt(storedValue: String): String {
        if (!storedValue.startsWith(PREFIX)) return storedValue
        val payload = Base64.getDecoder().decode(storedValue.removePrefix(PREFIX))
        require(payload.size > IV_SIZE) { "Segreto cifrato troncato" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, payload, 0, IV_SIZE))
        return String(cipher.doFinal(payload, IV_SIZE, payload.size - IV_SIZE), Charsets.UTF_8)
    }

    override fun isUnprotected(storedValue: String): Boolean =
        storedValue.isNotEmpty() && !storedValue.startsWith(PREFIX)

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "aks:"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
