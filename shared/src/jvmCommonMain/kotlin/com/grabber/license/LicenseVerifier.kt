package com.grabber.license

import java.security.MessageDigest
import java.util.Base64

/**
 * Verifica di una firma Ed25519. Su desktop si usa il provider del JDK, su Android
 * l'API leggera di BouncyCastle (Ed25519 non è garantito dal sistema su tutte le versioni).
 */
fun interface SignatureVerifier {
    /**
     * @param publicKeySpki chiave pubblica Ed25519 in formato X.509 SubjectPublicKeyInfo (44 byte)
     * @return false anche per chiavi o firme malformate; non solleva eccezioni
     */
    fun verify(publicKeySpki: ByteArray, message: ByteArray, signature: ByteArray): Boolean
}

/**
 * Formato delle licenze, comune a desktop e Android:
 * `KSR1:<base64url(machineId)>:<firma Ed25519 base64url>` sul payload
 * `KiwiSDRRecorder:license:v1:<machineId>`, firmato con lo strumento License Manager.
 */
object LicenseFormat {
    const val LICENSE_PREFIX = "KSR1"
    const val PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEAwMbbSV5dNQle0RJ09vpZrXlrajpOMyZwsxIG1JzaB5Y="

    /** Restituito quando l'identificativo del dispositivo non è leggibile: nessuna licenza può corrispondere. */
    const val UNAVAILABLE_MACHINE_ID = "GRAB-NON-DISPONIBILE"

    fun isAvailable(machineId: String): Boolean = machineId != UNAVAILABLE_MACHINE_ID

    fun payload(machineId: String): ByteArray =
        "KiwiSDRRecorder:license:v1:$machineId".toByteArray(Charsets.UTF_8)

    fun verify(
        licenseKey: String,
        machineId: String,
        verifier: SignatureVerifier,
        publicKeyBase64: String = PUBLIC_KEY_BASE64
    ): Boolean {
        if (!isAvailable(machineId)) return false
        val parts = licenseKey.trim().split(':')
        if (parts.size != 3 || parts[0] != LICENSE_PREFIX) return false

        return try {
            val encodedMachineId = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            if (encodedMachineId != machineId) return false
            verifier.verify(
                publicKeySpki = Base64.getDecoder().decode(publicKeyBase64),
                message = payload(encodedMachineId),
                signature = Base64.getUrlDecoder().decode(parts[2])
            )
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

/**
 * Esito della licenza per il dispositivo corrente. La firma viene verificata di nuovo solo
 * quando cambia la chiave: l'esito dipende solo dalla chiave e dall'ID, costante durante l'esecuzione.
 */
class LicenseChecker(
    private val machineIdProvider: () -> String,
    private val verifier: SignatureVerifier
) {
    private data class Verification(val licenseKey: String, val isValid: Boolean)

    @Volatile private var lastVerification: Verification? = null

    val machineId: String get() = machineIdProvider()

    fun isValidForCurrentDevice(licenseKey: String): Boolean {
        lastVerification?.takeIf { it.licenseKey == licenseKey }?.let { return it.isValid }
        val isValid = LicenseFormat.verify(licenseKey, machineIdProvider(), verifier)
        lastVerification = Verification(licenseKey, isValid)
        return isValid
    }
}

/** Identificativi dei dispositivi: `GRAB-XXXX-…` su Windows, `GRAB-A-XXXX-…` su Android. */
object DeviceIds {
    private const val ANDROID_NAMESPACE = "KiwiSDRRecorder:machine:android:v1:"

    /**
     * ID Android derivato da `Settings.Secure.ANDROID_ID` (univoco per dispositivo, utente e chiave
     * di firma dell'app). Il prefisso `A` evita collisioni con gli ID desktop.
     */
    fun androidMachineIdFrom(androidId: String?): String {
        val normalized = androidId?.trim()?.lowercase()
        if (normalized.isNullOrEmpty()) return LicenseFormat.UNAVAILABLE_MACHINE_ID
        return "GRAB-A-" + groupedHash(ANDROID_NAMESPACE + normalized)
    }

    /** Primi 16 caratteri esadecimali (maiuscoli) dello SHA-256, in gruppi da 4 separati da trattini. */
    fun groupedHash(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
            .take(16)
            .chunked(4)
            .joinToString("-")
}
