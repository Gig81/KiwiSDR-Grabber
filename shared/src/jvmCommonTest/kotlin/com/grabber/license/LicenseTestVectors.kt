package com.grabber.license

import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vettori di licenza comuni a desktop (verificatore JDK) e Android (BouncyCastle), firmati con
 * una chiave di TEST generata apposta: non è la chiave di produzione del License Manager.
 */
object LicenseTestVectors {
    const val TEST_PUBLIC_KEY = "MCowBQYDK2VwAyEAwH5orR/2L2sIOcbsjIJlQqFjboH6HPArEbt41tbqm1Y="
    const val ANDROID_MACHINE_ID = "GRAB-A-1A2B-3C4D-5E6F-7A8B"
    const val ANDROID_LICENSE =
        "KSR1:R1JBQi1BLTFBMkItM0M0RC01RTZGLTdBOEI:" +
            "7YDVWX-Jk7t7LMTyF8DOpDkt8ZT0SQEv_6bp26ju0ZWtwpmWnLKfUqsgDQr-DFi5KiIG0N-zjm0cr7XRHvidAQ"

    /** Stesse asserzioni per ogni piattaforma: codice valido accettato, varianti rifiutate. */
    fun assertVerifierBehaviour(verifier: SignatureVerifier) {
        assertTrue(LicenseFormat.verify(ANDROID_LICENSE, ANDROID_MACHINE_ID, verifier, TEST_PUBLIC_KEY))

        // Codice di un altro dispositivo (anche desktop con lo stesso suffisso).
        assertFalse(LicenseFormat.verify(ANDROID_LICENSE, "GRAB-A-0000-0000-0000-0000", verifier, TEST_PUBLIC_KEY))
        assertFalse(LicenseFormat.verify(ANDROID_LICENSE, "GRAB-1A2B-3C4D-5E6F-7A8B", verifier, TEST_PUBLIC_KEY))

        // Firma alterata.
        val tampered = ANDROID_LICENSE.dropLast(2) + if (ANDROID_LICENSE.endsWith("AQ")) "AA" else "AQ"
        assertFalse(LicenseFormat.verify(tampered, ANDROID_MACHINE_ID, verifier, TEST_PUBLIC_KEY))

        // Chiave pubblica diversa (quella di produzione) e formato non valido.
        assertFalse(LicenseFormat.verify(ANDROID_LICENSE, ANDROID_MACHINE_ID, verifier))
        assertFalse(LicenseFormat.verify("KSR1:abc", ANDROID_MACHINE_ID, verifier, TEST_PUBLIC_KEY))
        assertFalse(LicenseFormat.verify("KSR1:!!:??", ANDROID_MACHINE_ID, verifier, TEST_PUBLIC_KEY))
    }
}
