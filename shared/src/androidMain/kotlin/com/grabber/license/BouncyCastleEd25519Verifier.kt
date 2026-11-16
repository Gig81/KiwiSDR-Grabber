package com.grabber.license

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Verifica Ed25519 con l'API leggera di BouncyCastle: il provider di sistema non offre
 * Ed25519 su tutte le versioni Android supportate (minSdk 29).
 */
object BouncyCastleEd25519Verifier : SignatureVerifier {
    /** Prefisso DER di una SubjectPublicKeyInfo Ed25519 (OID 1.3.101.112), seguito dai 32 byte della chiave. */
    private val SPKI_PREFIX = byteArrayOf(0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00)

    override fun verify(publicKeySpki: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKeySpki.size != SPKI_PREFIX.size + Ed25519PublicKeyParameters.KEY_SIZE) return false
        if (!publicKeySpki.copyOfRange(0, SPKI_PREFIX.size).contentEquals(SPKI_PREFIX)) return false
        if (signature.size != Ed25519PublicKeyParameters.KEY_SIZE * 2) return false
        return try {
            val key = Ed25519PublicKeyParameters(publicKeySpki, SPKI_PREFIX.size)
            Ed25519Signer().run {
                init(false, key)
                update(message, 0, message.size)
                verifySignature(signature)
            }
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
