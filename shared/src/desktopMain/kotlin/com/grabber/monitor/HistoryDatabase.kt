package com.grabber.monitor

import com.grabber.app.AppLogger
import com.grabber.model.TranscriptionStatus
import java.io.File
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class UserLogEntry(
    val id: Long,
    val timestamp: String,
    val activeUsers: Int,
    val maxUsers: Int,
    val stationName: String,
    val frequencyKhz: Double,
    val mode: String,
    val userNames: String = ""
)

data class RecordingHistoryEntry(
    val id: Long,
    val timestamp: String,
    val filePath: String,
    val durationSeconds: Long,
    val frequencyKhz: Double,
    val mode: String,
    val fileExists: Boolean = true,
    val transcriptionText: String? = null,
    val hasVoice: Boolean? = null,
    val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.NOT_REQUESTED,
    val transcriptionError: String? = null
)

private const val TAG = "HistoryDatabase"

class HistoryDatabase(
    dbFile: File = defaultDatabaseFile()
) {
    companion object {
        private const val DATABASE_FILE_NAME = "kiwigrabber_history.db"
        private val SQLITE_SIDECAR_SUFFIXES = listOf("-wal", "-shm")

        /** Il database sta in `~/.kiwigrabber/` con impostazioni e log; le versioni precedenti lo creavano in `~`. */
        fun defaultDatabaseFile(): File {
            val home = File(System.getProperty("user.home"))
            return relocateLegacyDatabase(
                legacyFile = File(home, DATABASE_FILE_NAME),
                targetFile = File(home, ".kiwigrabber/$DATABASE_FILE_NAME")
            )
        }

        /**
         * Sposta il database nella nuova posizione con un'unica rinomina. Se restano file WAL/SHM
         * (chiusura non pulita) o lo spostamento fallisce, continua a usare il file esistente.
         */
        internal fun relocateLegacyDatabase(legacyFile: File, targetFile: File): File {
            if (targetFile.exists() || !legacyFile.exists()) return targetFile
            if (SQLITE_SIDECAR_SUFFIXES.any { File(legacyFile.path + it).exists() }) {
                AppLogger.warn(TAG, "Database storico non spostato: file WAL/SHM presenti accanto a ${legacyFile.absolutePath}")
                return legacyFile
            }
            return try {
                targetFile.parentFile?.mkdirs()
                Files.move(legacyFile.toPath(), targetFile.toPath())
                AppLogger.info(TAG, "Database storico spostato in ${targetFile.absolutePath}")
                targetFile
            } catch (e: Exception) {
                AppLogger.error(TAG, "Spostamento del database storico non riuscito, uso ${legacyFile.absolutePath}", e)
                legacyFile
            }
        }
    }

    private val absoluteDbFile = dbFile.absoluteFile
    private val url = "jdbc:sqlite:${absoluteDbFile.absolutePath}"
    private val lock = Any()
    private var sharedConn: Connection? = null

    init {
        absoluteDbFile.parentFile?.let { Files.createDirectories(it.toPath()) }
        initSchema()
    }

    private fun getOrCreateConnection(): Connection {
        return synchronized(lock) {
            val existing = sharedConn
            if (existing != null && !existing.isClosed) {
                return existing
            }
            val conn = DriverManager.getConnection(url)
            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode = WAL;")
                stmt.execute("PRAGMA synchronous = NORMAL;")
                stmt.execute("PRAGMA busy_timeout = 5000;")
            }
            sharedConn = conn
            return conn
        }
    }

    private fun initSchema() {
        val conn = getOrCreateConnection()
        synchronized(lock) {
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp TEXT NOT NULL,
                        active_users INTEGER NOT NULL,
                        max_users INTEGER NOT NULL,
                        station_name TEXT,
                        frequency_khz REAL,
                        mode TEXT,
                        user_names TEXT
                    )
                """.trimIndent())

                ensureColumn(conn, "user_logs", "user_names", "TEXT")

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS recording_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        duration_seconds INTEGER NOT NULL,
                        frequency_khz REAL,
                        mode TEXT,
                        transcription_text TEXT,
                        has_voice INTEGER,
                        transcription_status TEXT NOT NULL DEFAULT 'NOT_REQUESTED',
                        transcription_error TEXT
                    )
                """.trimIndent())

                ensureColumn(conn, "recording_history", "transcription_text", "TEXT")
                ensureColumn(conn, "recording_history", "has_voice", "INTEGER")
                ensureColumn(conn, "recording_history", "transcription_status", "TEXT NOT NULL DEFAULT 'NOT_REQUESTED'")
                ensureColumn(conn, "recording_history", "transcription_error", "TEXT")
                val ftsTableExists = conn.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"
                ).use { pstmt ->
                    pstmt.setString(1, "recording_history_fts")
                    pstmt.executeQuery().use { rs -> rs.next() }
                }
                stmt.executeUpdate(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS recording_history_fts " +
                        "USING fts5(transcription_text, content='recording_history', content_rowid='id')"
                )
                stmt.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS recording_history_fts_insert
                    AFTER INSERT ON recording_history
                    WHEN new.transcription_text IS NOT NULL
                    BEGIN
                        INSERT INTO recording_history_fts(rowid, transcription_text)
                        VALUES (new.id, new.transcription_text);
                    END
                """.trimIndent())
                stmt.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS recording_history_fts_update
                    AFTER UPDATE OF transcription_text ON recording_history
                    BEGIN
                        INSERT INTO recording_history_fts(recording_history_fts, rowid, transcription_text)
                        SELECT 'delete', old.id, old.transcription_text WHERE old.transcription_text IS NOT NULL;
                        INSERT INTO recording_history_fts(rowid, transcription_text)
                        SELECT new.id, new.transcription_text WHERE new.transcription_text IS NOT NULL;
                    END
                """.trimIndent())
                stmt.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS recording_history_fts_delete
                    AFTER DELETE ON recording_history
                    WHEN old.transcription_text IS NOT NULL
                    BEGIN
                        INSERT INTO recording_history_fts(recording_history_fts, rowid, transcription_text)
                        VALUES ('delete', old.id, old.transcription_text);
                    END
                """.trimIndent())
                if (!ftsTableExists) {
                    stmt.executeUpdate("INSERT INTO recording_history_fts(recording_history_fts) VALUES ('rebuild')")
                }
            }
        }
    }

    /** Migrazione dei database creati da versioni precedenti, prive di alcune colonne. */
    private fun ensureColumn(conn: Connection, table: String, name: String, declaration: String) {
        val hasColumn = conn.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA table_info($table)").use { rs ->
                var found = false
                while (rs.next()) {
                    if (rs.getString("name") == name) found = true
                }
                found
            }
        }
        if (!hasColumn) {
            conn.createStatement().use { it.executeUpdate("ALTER TABLE $table ADD COLUMN $name $declaration") }
        }
    }

    fun insertUserLog(activeUsers: Int, maxUsers: Int, stationName: String, freqKhz: Double, mode: String, userNames: String = "", enabled: Boolean = true) {
        if (!enabled) return

        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val conn = getOrCreateConnection()
        synchronized(lock) {
            conn.prepareStatement("""
                INSERT INTO user_logs (timestamp, active_users, max_users, station_name, frequency_khz, mode, user_names)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { pstmt ->
                pstmt.setString(1, now)
                pstmt.setInt(2, activeUsers)
                pstmt.setInt(3, maxUsers)
                pstmt.setString(4, stationName)
                pstmt.setDouble(5, freqKhz)
                pstmt.setString(6, mode)
                pstmt.setString(7, userNames)
                pstmt.executeUpdate()
            }
        }
    }

    fun insertRecording(
        filePath: String,
        durationSeconds: Long,
        freqKhz: Double,
        mode: String,
        transcriptionStatus: TranscriptionStatus = TranscriptionStatus.NOT_REQUESTED
    ): Long {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val conn = getOrCreateConnection()
        return synchronized(lock) {
            conn.prepareStatement("""
                INSERT INTO recording_history (timestamp, file_path, duration_seconds, frequency_khz, mode, transcription_status)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { pstmt ->
                pstmt.setString(1, now)
                pstmt.setString(2, filePath)
                pstmt.setLong(3, durationSeconds)
                pstmt.setDouble(4, freqKhz)
                pstmt.setString(5, mode)
                pstmt.setString(6, transcriptionStatus.name)
                pstmt.executeUpdate()
            }
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT last_insert_rowid()").use { rs ->
                    check(rs.next()) { "SQLite did not return the inserted recording id" }
                    rs.getLong(1)
                }
            }
        }
    }

    fun getRecentUserLogs(limit: Int = 50): List<UserLogEntry> {
        val list = mutableListOf<UserLogEntry>()
        val conn = getOrCreateConnection()
        synchronized(lock) {
            conn.prepareStatement("SELECT * FROM user_logs ORDER BY id DESC LIMIT ?").use { pstmt ->
                pstmt.setInt(1, limit)
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    val uNames = rs.getString("user_names") ?: ""
                    list.add(
                        UserLogEntry(
                            id = rs.getLong("id"),
                            timestamp = rs.getString("timestamp"),
                            activeUsers = rs.getInt("active_users"),
                            maxUsers = rs.getInt("max_users"),
                            stationName = rs.getString("station_name") ?: "",
                            frequencyKhz = rs.getDouble("frequency_khz"),
                            mode = rs.getString("mode") ?: "",
                            userNames = uNames
                        )
                    )
                }
            }
        }
        return list
    }

    fun getRecentRecordings(limit: Int = 50): List<RecordingHistoryEntry> {
        return queryRecordings("SELECT * FROM recording_history ORDER BY id DESC LIMIT ?", limit)
    }

    fun searchRecordings(query: String, limit: Int = 100): List<RecordingHistoryEntry> {
        val terms = Regex("[\\p{L}\\p{N}_-]+").findAll(query).map { it.value }.toList()
        if (terms.isEmpty()) return getRecentRecordings(limit)
        val matchExpression = terms.joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }
        return queryRecordings(
            "SELECT recording_history.* FROM recording_history " +
                "JOIN recording_history_fts ON recording_history_fts.rowid = recording_history.id " +
                "WHERE recording_history_fts MATCH ? ORDER BY recording_history.id DESC LIMIT ?",
            limit,
            matchExpression
        )
    }

    private fun queryRecordings(sql: String, limit: Int, search: String? = null): List<RecordingHistoryEntry> {
        val list = mutableListOf<RecordingHistoryEntry>()
        val conn = getOrCreateConnection()
        synchronized(lock) {
            conn.prepareStatement(sql).use { pstmt ->
                if (search == null) {
                    pstmt.setInt(1, limit)
                } else {
                    pstmt.setString(1, search)
                    pstmt.setInt(2, limit)
                }
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    val path = rs.getString("file_path") ?: ""
                    val exists = if (path.isNotBlank()) File(path).exists() else false
                    list.add(
                        RecordingHistoryEntry(
                            id = rs.getLong("id"),
                            timestamp = rs.getString("timestamp"),
                            filePath = path,
                            durationSeconds = rs.getLong("duration_seconds"),
                            frequencyKhz = rs.getDouble("frequency_khz"),
                            mode = rs.getString("mode") ?: "",
                            fileExists = exists,
                            transcriptionText = rs.getString("transcription_text"),
                            hasVoice = rs.getInt("has_voice").let { if (rs.wasNull()) null else it != 0 },
                            transcriptionStatus = TranscriptionStatus.fromStorage(rs.getString("transcription_status")),
                            transcriptionError = rs.getString("transcription_error")
                        )
                    )
                }
            }
        }
        return list
    }

    fun updateTranscription(id: Long, status: TranscriptionStatus, text: String? = null, error: String? = null) {
        val conn = getOrCreateConnection()
        synchronized(lock) {
            conn.prepareStatement("""
                UPDATE recording_history
                SET transcription_status = ?, transcription_text = ?, has_voice = ?, transcription_error = ?
                WHERE id = ?
            """.trimIndent()).use { pstmt ->
                pstmt.setString(1, status.name)
                pstmt.setString(2, text)
                if (text == null) pstmt.setNull(3, java.sql.Types.INTEGER) else pstmt.setInt(3, if (text.isBlank()) 0 else 1)
                pstmt.setString(4, error)
                pstmt.setLong(5, id)
                pstmt.executeUpdate()
            }
        }
    }

    fun getPendingTranscriptions(): List<Pair<Long, String>> {
        val pending = mutableListOf<Pair<Long, String>>()
        val conn = getOrCreateConnection()
        synchronized(lock) {
            conn.prepareStatement("""
                SELECT id, file_path FROM recording_history
                WHERE transcription_status IN ('PENDING', 'PROCESSING')
                ORDER BY id
            """.trimIndent()).use { pstmt ->
                val rs = pstmt.executeQuery()
                while (rs.next()) pending += rs.getLong("id") to rs.getString("file_path")
            }
        }
        return pending
    }

    fun deleteRecording(id: Long, filePath: String, deleteFileFromDisk: Boolean = true): Boolean {
        if (deleteFileFromDisk) deleteAudioFile(filePath)
        val conn = getOrCreateConnection()
        return synchronized(lock) {
            conn.prepareStatement("DELETE FROM recording_history WHERE id = ?").use { pstmt ->
                pstmt.setLong(1, id)
                pstmt.executeUpdate() > 0
            }
        }
    }

    fun deleteRecordings(items: List<Pair<Long, String>>, deleteFilesFromDisk: Boolean = true): Int {
        if (items.isEmpty()) return 0
        if (deleteFilesFromDisk) items.forEach { (_, path) -> deleteAudioFile(path) }
        val conn = getOrCreateConnection()
        return synchronized(lock) {
            conn.prepareStatement("DELETE FROM recording_history WHERE id = ?").use { pstmt ->
                for ((id, _) in items) {
                    pstmt.setLong(1, id)
                    pstmt.addBatch()
                }
                pstmt.executeBatch().sum()
            }
        }
    }

    /** Un file non eliminabile non blocca la rimozione della voce dallo storico. */
    private fun deleteAudioFile(path: String) {
        if (path.isBlank()) return
        try {
            val file = File(path)
            if (file.exists() && !file.delete()) {
                AppLogger.warn(TAG, "Impossibile eliminare il file audio $path")
            }
        } catch (e: SecurityException) {
            AppLogger.warn(TAG, "Eliminazione del file audio $path non consentita", e)
        }
    }

    fun deleteUserLogs(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        val conn = getOrCreateConnection()
        return synchronized(lock) {
            conn.prepareStatement("DELETE FROM user_logs WHERE id = ?").use { pstmt ->
                for (id in ids) {
                    pstmt.setLong(1, id)
                    pstmt.addBatch()
                }
                pstmt.executeBatch().sum()
            }
        }
    }

    fun clearAllUserLogs(): Boolean {
        val conn = getOrCreateConnection()
        return synchronized(lock) {
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM user_logs") > 0
            }
        }
    }

    fun close() {
        synchronized(lock) {
            try {
                sharedConn?.close()
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Chiusura del database non riuscita", e)
            }
            sharedConn = null
        }
    }
}
