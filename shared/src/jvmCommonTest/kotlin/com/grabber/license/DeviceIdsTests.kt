package com.grabber.license

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DeviceIdsTests {
    /** Pattern accettato dal License Manager per gli ID desktop e Android. */
    private val licenseManagerPattern = Regex("GRAB-(?:A-)?[A-F0-9]{4}(?:-[A-F0-9]{4}){3}")

    @Test
    fun androidIdIsStableNormalizedAndAcceptedByLicenseManager() {
        val id = DeviceIds.androidMachineIdFrom("9774d56d682e549c")

        assertTrue(Regex("GRAB-A-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}").matches(id))
        assertTrue(licenseManagerPattern.matches(id))
        assertEquals(id, DeviceIds.androidMachineIdFrom("  9774D56D682E549C "))
        assertNotEquals(id, DeviceIds.androidMachineIdFrom("0123456789abcdef"))
    }

    @Test
    fun missingAndroidIdCannotBeLicensed() {
        assertEquals(LicenseFormat.UNAVAILABLE_MACHINE_ID, DeviceIds.androidMachineIdFrom(null))
        assertEquals(LicenseFormat.UNAVAILABLE_MACHINE_ID, DeviceIds.androidMachineIdFrom(""))
        assertFalse(LicenseFormat.isAvailable(DeviceIds.androidMachineIdFrom(" ")))
    }

    @Test
    fun featureGateFollowsLicenseOutcome() {
        var licensed = false
        val gate = FeatureGate { licensed }
        assertFalse(gate.isEnabled(GatedFeature.MANUAL_RECORDING))
        licensed = true
        GatedFeature.entries.forEach { assertTrue(gate.isEnabled(it)) }
    }
}
