package com.grabber.app

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

enum class LogLevel(val priority: Int) {
    TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4)
}

object AppLogger {
    private const val MAX_LOG_FILE_SIZE_BYTES = 5L * 1024 * 1024
    private const val MAX_ROTATED_FILES = 3
    private const val LOG_RETENTION_DAYS = 14L

    private var logLevel = LogLevel.WARN
    private var logFile: File? = null
    private var writer: PrintWriter? = null
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val pendingLogs = ConcurrentLinkedQueue<String>()
    @Volatile private var initialized = false
    @Volatile private var initializationError: Exception? = null

    @Synchronized
    fun init(logDir: File, level: LogLevel = LogLevel.DEBUG): Boolean {
        writer?.close()
        writer = null
        initialized = false
        initializationError = null
        logLevel = level
        val today = LocalDate.now().toString()
        val activeLogFile = File(logDir, "grabber_$today.log")
        logFile = activeLogFile
        return try {
            Files.createDirectories(logDir.toPath())
            pruneOldLogs(logDir)
            writer = PrintWriter(
                Files.newBufferedWriter(
                    activeLogFile.toPath(),
                    Charsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                ),
                true
            )
            initialized = true
            // Flush pending logs
            while (pendingLogs.isNotEmpty()) {
                writer?.println(pendingLogs.poll())
            }
            info("AppLogger", "Logging inizializzato. Livello: $level. File: ${activeLogFile.absolutePath}")
            true
        } catch (e: Exception) {
            initialized = false
            writer?.close()
            writer = null
            initializationError = e
            System.err.println("[LOGGER ERROR] Impossibile inizializzare il log su file: ${e.message}")
            e.printStackTrace(System.err)
            false
        }
    }

    fun isInitialized(): Boolean = initialized

    fun currentLogFile(): File? = logFile

    fun initializationError(): Exception? = initializationError

    @Synchronized
    fun setLevel(level: LogLevel) {
        logLevel = level
        info("AppLogger", "Livello di log cambiato a: $level")
    }

    @Synchronized
    private fun log(level: LogLevel, tag: String, message: String) {
        if (level.priority < logLevel.priority) return
        val timestamp = LocalDateTime.now().format(timestampFormat)
        val line = "$timestamp [${level.name}] [$tag] $message"
        if (initialized) {
            try {
                val lineSize = line.toByteArray(Charsets.UTF_8).size +
                    System.lineSeparator().toByteArray(Charsets.UTF_8).size
                rotateIfNeeded(lineSize.toLong())
                writer?.println(line)
                if (writer?.checkError() == true) {
                    System.err.println("[LOGGER ERROR] Scrittura fallita per ${logFile?.absolutePath}")
                }
            } catch (e: Exception) {
                System.err.println("[LOGGER ERROR] Impossibile scrivere nel log: ${e.message}")
            }
        } else {
            pendingLogs.offer(line)
        }
        // Sempre stampare anche su console durante lo sviluppo
        if (level.priority >= LogLevel.INFO.priority) {
            println(line)
        }
    }

    fun trace(tag: String, message: String) = log(LogLevel.TRACE, tag, message)
    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    @Synchronized
    fun warn(tag: String, message: String, throwable: Throwable) =
        logWithThrowable(LogLevel.WARN, tag, message, throwable)

    @Synchronized
    fun error(tag: String, message: String, throwable: Throwable) =
        logWithThrowable(LogLevel.ERROR, tag, message, throwable)

    private fun logWithThrowable(level: LogLevel, tag: String, message: String, throwable: Throwable) {
        if (level.priority < logLevel.priority) return
        log(level, tag, "$message: ${throwable.message}")
        if (initialized) {
            try {
                rotateIfNeeded(throwable.stackTraceToString().toByteArray(Charsets.UTF_8).size.toLong())
                throwable.printStackTrace(writer)
                writer?.flush()
                if (writer?.checkError() == true) {
                    System.err.println("[LOGGER ERROR] Scrittura dello stack trace fallita per ${logFile?.absolutePath}")
                }
            } catch (e: Exception) {
                System.err.println("[LOGGER ERROR] Impossibile scrivere lo stack trace: ${e.message}")
            }
        } else {
            pendingLogs.offer(throwable.stackTraceToString())
        }
    }

    @Synchronized
    fun shutdown() {
        try {
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
            System.err.println("[LOGGER ERROR] Impossibile chiudere il file di log: ${e.message}")
        } finally {
            writer = null
            initialized = false
        }
    }

    private fun rotateIfNeeded(incomingBytes: Long) {
        val activeFile = logFile ?: return
        if (activeFile.length() + incomingBytes <= MAX_LOG_FILE_SIZE_BYTES) return

        writer?.flush()
        writer?.close()
        try {
            for (index in MAX_ROTATED_FILES downTo 1) {
                val source = if (index == 1) activeFile else File(activeFile.parentFile, "${activeFile.name}.${index - 1}")
                val destination = File(activeFile.parentFile, "${activeFile.name}.$index")
                if (destination.exists() && !destination.delete()) {
                    throw IllegalStateException("Impossibile rimuovere ${destination.absolutePath}")
                }
                if (source.exists()) {
                    Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } catch (e: Exception) {
            System.err.println("[LOGGER ERROR] Rotazione del log fallita: ${e.message}")
        } finally {
            writer = try {
                PrintWriter(
                    Files.newBufferedWriter(
                        activeFile.toPath(),
                        Charsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                    ),
                    true
                )
            } catch (e: Exception) {
                System.err.println("[LOGGER ERROR] Impossibile riaprire il log: ${e.message}")
                null
            }
            if (writer == null) initialized = false
        }
    }

    private fun pruneOldLogs(logDir: File) {
        val cutoff = LocalDate.now().minusDays(LOG_RETENTION_DAYS)
        logDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("grabber_") && it.name.length >= 18 }
            ?.forEach { file ->
                val date = runCatching { LocalDate.parse(file.name.substring(8, 18)) }.getOrNull()
                if (date != null && date < cutoff && !file.delete()) {
                    warn("AppLogger", "Impossibile eliminare il log scaduto ${file.absolutePath}")
                }
            }
    }
}
