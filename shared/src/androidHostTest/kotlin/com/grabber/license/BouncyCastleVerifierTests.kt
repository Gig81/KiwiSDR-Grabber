package com.grabber.license

import kotlin.test.Test
import kotlin.test.assertFalse

class BouncyCastleVerifierTests {
    @Test
    fun bouncyCastleVerifierAcceptsSharedVector() {
        LicenseTestVectors.assertVerifierBehaviour(BouncyCastleEd25519Verifier)
    }

    @Test
    fun malformedKeysAndSignaturesAreRejected() {
        assertFalse(BouncyCastleEd25519Verifier.verify(ByteArray(10), ByteArray(4), ByteArray(64)))
        assertFalse(BouncyCastleEd25519Verifier.verify(ByteArray(44), ByteArray(4), ByteArray(64)))
        assertFalse(BouncyCastleEd25519Verifier.verify(ByteArray(44), ByteArray(4), ByteArray(3)))
    }
}
