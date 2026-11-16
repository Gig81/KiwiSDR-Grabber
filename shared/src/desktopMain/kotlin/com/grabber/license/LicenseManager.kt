package com.grabber.license

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Verifica Ed25519 con il provider del JDK (disponibile dal JDK 15). */
object JdkEd25519SignatureVerifier : SignatureVerifier {
    override fun verify(publicKeySpki: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
        try {
            val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(publicKeySpki))
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey)
            verifier.update(message)
            verifier.verify(signature)
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
}

object LicenseManager {
    private val checker = LicenseChecker(MachineIdUtil::getMachineId, JdkEd25519SignatureVerifier)

    fun verifyLicenseForCurrentMachine(licenseKey: String): Boolean = checker.isValidForCurrentDevice(licenseKey)

    internal fun verifyLicense(
        licenseKey: String,
        machineId: String,
        publicKeyBase64: String = LicenseFormat.PUBLIC_KEY_BASE64
    ): Boolean = LicenseFormat.verify(licenseKey, machineId, JdkEd25519SignatureVerifier, publicKeyBase64)

    internal fun licensePayload(machineId: String): ByteArray = LicenseFormat.payload(machineId)
}
