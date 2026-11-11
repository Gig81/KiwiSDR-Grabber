package com.grabber.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsTests {

    @Test
    fun freshSettingsRequireInitialSetup() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            val settings = AppSettings(settingsDir).settings.value
            assertFalse(settings.setupCompleted)
            assertFalse(settings.recordConnectedUsers)
            assertTrue(settings.logLevelName == "WARN")
            assertTrue(settings.waterfallZoom == 0)
            assertTrue(settings.sttModelPath.isEmpty())
            assertTrue(settings.serverPassword.isEmpty())
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }

    @Test
    fun waterfallZoomIsClampedAndPersisted() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            val settings = AppSettings(settingsDir)
            settings.setWaterfallZoom(20)
            assertTrue(settings.settings.value.waterfallZoom == 14)
            assertTrue(AppSettings(settingsDir).settings.value.waterfallZoom == 14)
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }

    @Test
    fun savesOfflineSttModelPath() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            val settings = AppSettings(settingsDir)
            settings.setSttModelPath(" C:\\models\\vosk-it ")
            assertTrue(settings.settings.value.sttModelPath == "C:\\models\\vosk-it")
            assertTrue(AppSettings(settingsDir).settings.value.sttModelPath == "C:\\models\\vosk-it")
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }

    @Test
    fun savesServerPasswordPerUser() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            val settings = AppSettings(settingsDir)
            settings.setServerPassword("receiver-password")
            assertTrue(settings.settings.value.serverPassword == "receiver-password")
            assertTrue(AppSettings(settingsDir).settings.value.serverPassword == "receiver-password")
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }

    @Test
    fun savesTelegramAlertSettings() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            val settings = AppSettings(settingsDir)
            settings.updateTelegramSettings(
                botToken = "token",
                chatId = "12345",
                enabled = true,
                notifyAnyQso = true,
                sensitiveWords = "mayday, soccorso",
                intervalMinutes = 60,
                sendAudio = true,
                sendTranscription = false,
                notifyQsoStart = true,
                notifyQsoEnd = true
            )
            val restored = AppSettings(settingsDir).settings.value
            assertTrue(restored.telegramAlertsEnabled)
            assertTrue(restored.telegramNotifyAnyQso)
            assertTrue(restored.telegramNotifyQsoStart)
            assertTrue(restored.telegramNotifyQsoEnd)
            assertTrue(restored.telegramMessageIntervalMinutes == 60)
            assertTrue(!restored.telegramSendTranscription)
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }

    @Test
    fun existingSettingsWithoutSetupFlagAreTreatedAsAlreadyConfigured() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            settingsDir.resolve("settings.json").writeText("""{"callsign":"IU7IYA"}""")
            assertTrue(AppSettings(settingsDir).settings.value.setupCompleted)
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }

    @Test
    fun secretsAreNeverWrittenInPlainText() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            val settings = AppSettings(settingsDir, DpapiSecretCipher)
            settings.setServerPassword("receiver-password")
            settings.updateTelegramSettings(
                botToken = "123456:telegram-token",
                chatId = "12345",
                enabled = true,
                notifyAnyQso = false,
                sensitiveWords = "",
                intervalMinutes = 5,
                sendAudio = false,
                sendTranscription = false
            )
            val fileContent = settingsDir.resolve("settings.json").readText()
            assertFalse(fileContent.contains("receiver-password"))
            assertFalse(fileContent.contains("telegram-token"))
            val restored = AppSettings(settingsDir, DpapiSecretCipher).settings.value
            assertEquals("receiver-password", restored.serverPassword)
            assertEquals("123456:telegram-token", restored.telegramBotToken)
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }

    @Test
    fun legacyPlainTextSecretsAreEncryptedOnLoad() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            val settingsFile = settingsDir.resolve("settings.json")
            settingsFile.writeText("""{"serverPassword":"old-password","telegramBotToken":"123:old-token"}""")
            val loaded = AppSettings(settingsDir, DpapiSecretCipher).settings.value
            assertEquals("old-password", loaded.serverPassword)
            assertEquals("123:old-token", loaded.telegramBotToken)
            assertFalse(settingsFile.readText().contains("old-password"))
            assertFalse(settingsFile.readText().contains("old-token"))
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }

    @Test
    fun undecryptableSecretIsClearedInsteadOfFailingLoad() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            settingsDir.resolve("settings.json").writeText("""{"callsign":"IK7ABC","serverPassword":"dpapi:AAAA"}""")
            val loaded = AppSettings(settingsDir, DpapiSecretCipher).settings.value
            assertEquals("", loaded.serverPassword)
            assertEquals("IK7ABC", loaded.callsign)
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }

    @Test
    fun removedFavoritesStayRemovedAfterRestart() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            val settings = AppSettings(settingsDir)
            val removed = settings.settings.value.favorites.first()
            settings.removeFavorite(removed)
            val restoredFavorites = AppSettings(settingsDir).settings.value.favorites
            assertEquals(defaultFavorites().size - 1, restoredFavorites.size)
            assertFalse(removed in restoredFavorites)
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }

    @Test
    fun legacyShortFavoriteListIsUpgradedToDefaults() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
        try {
            settingsDir.resolve("settings.json").writeText(
                """{"favorites":[{"name":"CB Ch1 - 26965","frequencyKhz":26965.0,"modeCode":"am"}]}"""
            )
            assertEquals(defaultFavorites(), AppSettings(settingsDir).settings.value.favorites)
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }
}
