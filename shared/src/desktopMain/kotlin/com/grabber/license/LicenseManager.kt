package com.grabber.license

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object LicenseManager {
    private const val LICENSE_PREFIX = "KSR1"
    private const val PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEAwMbbSV5dNQle0RJ09vpZrXlrajpOMyZwsxIG1JzaB5Y="

    private data class Verification(val licenseKey: String, val isValid: Boolean)

    @Volatile private var lastVerification: Verification? = null

    /**
     * La verifica della firma viene ripetuta solo quando cambia la chiave: l'esito dipende
     * esclusivamente dalla chiave e dal Machine ID, che resta costante durante l'esecuzione.
     */
    fun verifyLicenseForCurrentMachine(licenseKey: String): Boolean {
        lastVerification?.takeIf { it.licenseKey == licenseKey }?.let { return it.isValid }
        val isValid = verifyLicense(licenseKey, MachineIdUtil.getMachineId())
        lastVerification = Verification(licenseKey, isValid)
        return isValid
    }

    internal fun verifyLicense(
        licenseKey: String,
        machineId: String,
        publicKeyBase64: String = PUBLIC_KEY_BASE64
    ): Boolean {
        if (!MachineIdUtil.isAvailable(machineId)) return false
        val parts = licenseKey.trim().split(':')
        if (parts.size != 3 || parts[0] != LICENSE_PREFIX) return false

        return try {
            val encodedMachineId = String(
                Base64.getUrlDecoder().decode(parts[1]),
                Charsets.UTF_8
            )
            if (encodedMachineId != machineId) return false

            val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
            val publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey)
            verifier.update(licensePayload(encodedMachineId))
            verifier.verify(Base64.getUrlDecoder().decode(parts[2]))
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }

    internal fun licensePayload(machineId: String): ByteArray =
        "KiwiSDRRecorder:license:v1:$machineId".toByteArray(Charsets.UTF_8)
}
