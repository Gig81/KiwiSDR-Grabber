package com.grabber.license

import com.grabber.monitor.HistoryDatabase
import com.grabber.app.TelegramAlertCooldown
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.*

class LicenseAndRecordingTests {

    @Test
    fun testMachineIdGeneration() {
        val id1 = MachineIdUtil.getMachineId()
        val id2 = MachineIdUtil.getMachineId()

        assertNotNull(id1)
        assertTrue(MachineIdUtil.isAvailable(id1), "MachineGuid di Windows deve essere leggibile")
        assertTrue(id1.startsWith("GRAB-"), "Machine ID deve iniziare con il prefisso GRAB-")
        assertEquals(id1, id2, "Machine ID deve essere consistente e stabile")
        assertTrue(id1.length >= 20, "Machine ID deve avere lunghezza valida")
    }

    @Test
    fun machineIdDependsOnlyOnWindowsMachineGuid() {
        val guid = "4c4c4544-0042-3510-8051-b4c04f4e3332"
        val id = MachineIdUtil.machineIdFrom(guid)

        assertTrue(Regex("GRAB-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}").matches(id))
        assertEquals(id, MachineIdUtil.machineIdFrom("  ${guid.uppercase()}  "))
        assertNotEquals(id, MachineIdUtil.machineIdFrom("00000000-0000-0000-0000-000000000001"))
    }

    @Test
    fun unreadableMachineGuidProducesUnlicensableId() {
        assertEquals(MachineIdUtil.UNAVAILABLE_MACHINE_ID, MachineIdUtil.machineIdFrom(null))
        assertEquals(MachineIdUtil.UNAVAILABLE_MACHINE_ID, MachineIdUtil.machineIdFrom("  "))

        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(LicenseManager.licensePayload(MachineIdUtil.UNAVAILABLE_MACHINE_ID))
        val urlEncoder = Base64.getUrlEncoder().withoutPadding()
        val license = "KSR1:" +
            urlEncoder.encodeToString(MachineIdUtil.UNAVAILABLE_MACHINE_ID.toByteArray(Charsets.UTF_8)) + ":" +
            urlEncoder.encodeToString(signer.sign())
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        assertFalse(LicenseManager.verifyLicense(license, MachineIdUtil.UNAVAILABLE_MACHINE_ID, publicKey))
    }

    @Test
    fun testLicenseValidation() {
        val machineId = "GRAB-1234-5678-9ABC-DEF0"
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(LicenseManager.licensePayload(machineId))
        val encodedMachineId = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(machineId.toByteArray(Charsets.UTF_8))
        val signature = signer.sign()
        val tamperedSignature = signature.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        val urlEncoder = Base64.getUrlEncoder().withoutPadding()
        val license = "KSR1:$encodedMachineId:${urlEncoder.encodeToString(signature)}"
        val tamperedLicense = "KSR1:$encodedMachineId:${urlEncoder.encodeToString(tamperedSignature)}"
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        assertTrue(LicenseManager.verifyLicense(license, machineId, publicKey))
        assertFalse(LicenseManager.verifyLicense(license, "GRAB-9999-8888-7777-6666", publicKey))
        assertFalse(LicenseManager.verifyLicense("LIC-AAAA-BBBB-CCCC-DDDD", machineId, publicKey))
        assertFalse(LicenseManager.verifyLicense(tamperedLicense, machineId, publicKey))
    }

    @Test
    fun telegramCooldownBlocksAllAlertKindsUntilIntervalExpires() {
        val cooldown = TelegramAlertCooldown()
        val intervalMinutes = 5
        val minuteNanos = 60_000_000_000L

        assertTrue(cooldown.allowsSend(1_000L, intervalMinutes))
        cooldown.recordSent(1_000L)

        assertFalse(cooldown.allowsSend(1_000L + minuteNanos, intervalMinutes))
        assertTrue(cooldown.allowsSend(1_000L + intervalMinutes * minuteNanos, intervalMinutes))
        assertTrue(cooldown.allowsSend(1_001L, 0))
    }

    @Test
    fun testDatabaseRecordingDeletionAndFilePresence() {
        val tempDb = File.createTempFile("test_history", ".db")
        tempDb.deleteOnExit()

        val db = HistoryDatabase(tempDb)

        val tempAudio = File.createTempFile("test_rec", ".mp3")
        tempAudio.writeText("fake mp3 data")
        assertTrue(tempAudio.exists())

        db.insertRecording(tempAudio.absolutePath, 15, 7100.0, "LSB")
        var recs = db.getRecentRecordings(10)
        assertEquals(1, recs.size)
        assertTrue(recs[0].fileExists, "Il file audio appena creato deve risultare presente su disco")

        // Rimuoviamo il file da disco e verifichiamo che venga rilevato come non presente
        tempAudio.delete()
        assertFalse(tempAudio.exists())

        recs = db.getRecentRecordings(10)
        assertEquals(1, recs.size)
        assertFalse(recs[0].fileExists, "Il file cancellato deve risultare fileExists=false")

        // Eliminazione record dal DB
        val deleted = db.deleteRecording(recs[0].id, recs[0].filePath, true)
        assertTrue(deleted, "La cancellazione deve avere successo")

        recs = db.getRecentRecordings(10)
        assertEquals(0, recs.size, "Lo storico non deve più contenere il record eliminato")

        // Test cancellazione multipla (batch) registrazioni
        val f1 = File.createTempFile("batch_rec1", ".wav").also { it.writeText("wav1") }
        val f2 = File.createTempFile("batch_rec2", ".wav").also { it.writeText("wav2") }
        db.insertRecording(f1.absolutePath, 10, 14200.0, "USB")
        db.insertRecording(f2.absolutePath, 20, 27205.0, "AM")
        recs = db.getRecentRecordings(10)
        assertEquals(2, recs.size)

        val batchItems = recs.map { it.id to it.filePath }
        val deletedCount = db.deleteRecordings(batchItems, true)
        assertEquals(2, deletedCount)
        assertFalse(f1.exists())
        assertFalse(f2.exists())
        assertEquals(0, db.getRecentRecordings(10).size)

        // Test cancellazione multipla log utenti
        db.insertUserLog(2, 4, "Kiwi 1", 7100.0, "LSB", "User1, User2")
        db.insertUserLog(3, 4, "Kiwi 1", 14200.0, "USB", "UserA, UserB, UserC")
        var logs = db.getRecentUserLogs(10)
        assertEquals(2, logs.size)

        val deletedLogs = db.deleteUserLogs(logs.map { it.id })
        assertEquals(2, deletedLogs)
        logs = db.getRecentUserLogs(10)
        assertEquals(0, logs.size)

        db.close()
    }

    @Test
    fun testServerAddressParsingAndConfiguration() {
        val (h1, p1) = com.grabber.app.parseServerAddress("ik7fmo.ddns.net:8073")
        assertEquals("ik7fmo.ddns.net", h1)
        assertEquals(8073, p1)

        val (h2, p2) = com.grabber.app.parseServerAddress("http://sdr.myradio.org:8074/")
        assertEquals("sdr.myradio.org", h2)
        assertEquals(8074, p2)

        val (h3, p3) = com.grabber.app.parseServerAddress("192.168.1.100")
        assertEquals("192.168.1.100", h3)
        assertEquals(8073, p3) // Default port

        val (h4, p4) = com.grabber.app.parseServerAddress("   ws://remote-kiwi.net:8090/   ")
        assertEquals("remote-kiwi.net", h4)
        assertEquals(8090, p4)

        // Test persistenza in AppSettings
        val tempDir = File.createTempFile("test_settings_dir", "").also { it.delete(); it.mkdirs() }
        tempDir.deleteOnExit()
        val settings = com.grabber.app.AppSettings(tempDir)
        assertEquals("ik7fmo.ddns.net:8073", settings.settings.value.serverAddress)

        settings.setServerAddress("custom.kiwisdr.com:8080")
        assertEquals("custom.kiwisdr.com:8080", settings.settings.value.serverAddress)
        val (ch, cp) = settings.getServerHostAndPort()
        assertEquals("custom.kiwisdr.com", ch)
        assertEquals(8080, cp)
    }
}
