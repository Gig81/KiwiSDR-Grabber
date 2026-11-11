package com.grabber.monitor

import com.grabber.model.TranscriptionStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryDatabaseTests {

    @Test
    fun persistsTranscriptionsAndSearchesFullText() {
        val directory = Files.createTempDirectory("kiwigrabber-history-test").toFile()
        val database = HistoryDatabase(directory.resolve("history.db"))
        try {
            val recordingId = database.insertRecording(
                filePath = directory.resolve("audio.wav").absolutePath,
                durationSeconds = 12,
                freqKhz = 7100.0,
                mode = "USB",
                transcriptionStatus = TranscriptionStatus.PENDING
            )
            assertNull(database.getRecentRecordings().single().transcriptionText)

            database.updateTranscription(recordingId, TranscriptionStatus.COMPLETED, "Mayday emergenza sul canale")

            val result = database.searchRecordings("mayday emergenza").single()
            assertEquals(recordingId, result.id)
            assertEquals("Mayday emergenza sul canale", result.transcriptionText)
            assertEquals(true, result.hasVoice)
            assertEquals(TranscriptionStatus.COMPLETED, result.transcriptionStatus)
        } finally {
            database.close()
            directory.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test database ${file.absolutePath}" }
            }
            check(directory.delete()) { "Unable to remove test directory ${directory.absolutePath}" }
        }
    }

    @Test
    fun migratesExistingRecordingTable() {
        val directory = Files.createTempDirectory("kiwigrabber-history-migration-test").toFile()
        val databaseFile = directory.resolve("history.db")
        java.sql.DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("""
                    CREATE TABLE recording_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        duration_seconds INTEGER NOT NULL,
                        frequency_khz REAL,
                        mode TEXT
                    )
                """.trimIndent())
            }
        }
        val database = HistoryDatabase(databaseFile)
        try {
            val id = database.insertRecording("missing.wav", 2, 1000.0, "AM")
            database.updateTranscription(id, TranscriptionStatus.COMPLETED, "")
            assertEquals(false, database.getRecentRecordings().single().hasVoice)
        } finally {
            database.close()
            directory.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test database ${file.absolutePath}" }
            }
            check(directory.delete()) { "Unable to remove test directory ${directory.absolutePath}" }
        }
    }

    @Test
    fun legacyDatabaseIsMovedIntoAppDataDirectory() {
        val home = Files.createTempDirectory("kiwigrabber-relocate-test").toFile()
        try {
            val legacy = home.resolve("kiwigrabber_history.db").apply { writeText("db") }
            val target = home.resolve(".kiwigrabber/kiwigrabber_history.db")

            assertEquals(target, HistoryDatabase.relocateLegacyDatabase(legacy, target))
            assertEquals("db", target.readText())
            assertFalse(legacy.exists())
        } finally {
            check(home.deleteRecursively()) { "Unable to remove ${home.absolutePath}" }
        }
    }

    @Test
    fun legacyDatabaseWithPendingWalStaysInPlace() {
        val home = Files.createTempDirectory("kiwigrabber-relocate-test").toFile()
        try {
            val legacy = home.resolve("kiwigrabber_history.db").apply { writeText("db") }
            home.resolve("kiwigrabber_history.db-wal").writeText("wal")
            val target = home.resolve(".kiwigrabber/kiwigrabber_history.db")

            assertEquals(legacy, HistoryDatabase.relocateLegacyDatabase(legacy, target))
            assertTrue(legacy.exists())
            assertFalse(target.exists())
        } finally {
            check(home.deleteRecursively()) { "Unable to remove ${home.absolutePath}" }
        }
    }

    @Test
    fun existingTargetDatabaseIsNeverOverwritten() {
        val home = Files.createTempDirectory("kiwigrabber-relocate-test").toFile()
        try {
            val legacy = home.resolve("kiwigrabber_history.db").apply { writeText("old") }
            val target = home.resolve(".kiwigrabber/kiwigrabber_history.db").apply { parentFile.mkdirs(); writeText("new") }

            assertEquals(target, HistoryDatabase.relocateLegacyDatabase(legacy, target))
            assertEquals("new", target.readText())
            assertTrue(legacy.exists())
        } finally {
            check(home.deleteRecursively()) { "Unable to remove ${home.absolutePath}" }
        }
    }
}
