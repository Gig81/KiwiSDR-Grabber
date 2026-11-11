package com.grabber.app

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLoggerTests {

    @Test
    fun rotatesLargeDailyLogsAndRemovesExpiredFiles() {
        val logDir = Files.createTempDirectory("kiwigrabber-logs-test").toFile()
        val todayLog = File(logDir, "grabber_${LocalDate.now()}.log")
        val expiredLog = File(logDir, "grabber_${LocalDate.now().minusDays(15)}.log")
        val rotationThresholdBytes = 5 * 1024 * 1024
        todayLog.writeText("x".repeat(rotationThresholdBytes + 1))
        expiredLog.writeText("expired")

        try {
            AppLogger.init(logDir, LogLevel.TRACE)
            AppLogger.info("AppLoggerTests", "Trigger log rotation")
            AppLogger.shutdown()

            assertTrue(File(logDir, "${todayLog.name}.1").exists())
            assertTrue(todayLog.length() < rotationThresholdBytes)
            assertFalse(expiredLog.exists())
        } finally {
            AppLogger.shutdown()
            logDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test log ${file.absolutePath}" }
            }
            check(logDir.delete()) { "Unable to remove test directory ${logDir.absolutePath}" }
        }
    }
}
