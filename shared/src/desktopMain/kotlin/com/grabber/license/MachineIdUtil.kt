package com.grabber.license

import com.grabber.app.AppLogger
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinReg

/**
 * Identificativo stabile della macchina a cui viene vincolata la licenza.
 *
 * Deriva dal `MachineGuid` di Windows, generato all'installazione del sistema operativo:
 * non cambia con interfacce di rete, VPN, dock o rinomina del PC, e si legge dal registro
 * senza avviare processi esterni. Cambia solo reinstallando Windows.
 */
object MachineIdUtil {
    private const val TAG = "MachineIdUtil"
    private const val CRYPTOGRAPHY_KEY = "SOFTWARE\\Microsoft\\Cryptography"
    private const val MACHINE_GUID_VALUE = "MachineGuid"
    private const val HASH_NAMESPACE = "KiwiSDRRecorder:machine:v2:"

    /** Restituito quando l'identificativo di Windows non è leggibile: nessuna licenza può corrispondere. */
    const val UNAVAILABLE_MACHINE_ID = LicenseFormat.UNAVAILABLE_MACHINE_ID

    private val currentMachineId: String by lazy { machineIdFrom(readMachineGuid()) }

    fun getMachineId(): String = currentMachineId

    fun isAvailable(id: String): Boolean = LicenseFormat.isAvailable(id)

    /** Formato compatto e leggibile: GRAB-XXXX-XXXX-XXXX-XXXX. */
    internal fun machineIdFrom(machineGuid: String?): String {
        val normalizedGuid = machineGuid?.trim()?.lowercase()
        if (normalizedGuid.isNullOrEmpty()) return UNAVAILABLE_MACHINE_ID
        return "GRAB-" + DeviceIds.groupedHash(HASH_NAMESPACE + normalizedGuid)
    }

    private fun readMachineGuid(): String? =
        try {
            Advapi32Util.registryGetStringValue(
                WinReg.HKEY_LOCAL_MACHINE,
                CRYPTOGRAPHY_KEY,
                MACHINE_GUID_VALUE,
                WinNT.KEY_WOW64_64KEY
            )
        } catch (e: Exception) {
            AppLogger.error(TAG, "MachineGuid di Windows non leggibile", e)
            null
        } catch (e: LinkageError) {
            AppLogger.error(TAG, "Accesso al registro di Windows non disponibile", e)
            null
        }
}
