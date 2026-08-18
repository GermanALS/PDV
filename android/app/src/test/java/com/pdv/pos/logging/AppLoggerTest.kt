package com.pdv.pos.logging

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit

class AppLoggerTest {

    @Test
    fun `log writes a line with the exact expected format`(@TempDir tempDir: File) = runTest {
        val logger = AppLogger(tempDir)

        logger.log(LogType.INFO, sucursalId = "suc-1", usuario = "german", mensaje = "hola mundo")

        val line = tempDir.listFiles()!!.single().readText().trim()
        val pattern = Regex("""^\[INFO]\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}]\[suc-1]\[german] hola mundo$""")
        assertTrue(pattern.matches(line)) { "linea inesperada: $line" }
    }

    @Test
    fun `log supports all six categories`(@TempDir tempDir: File) = runTest {
        val logger = AppLogger(tempDir)

        LogType.entries.forEach { tipo ->
            logger.log(tipo, sucursalId = "suc-1", usuario = "german", mensaje = "evento $tipo")
        }

        val content = tempDir.listFiles()!!.single().readText()
        LogType.entries.forEach { tipo ->
            assertTrue(content.contains("[${tipo.name}]")) { "falta categoria $tipo" }
        }
    }

    @Test
    fun `rotates to a new file once the current one reaches the size limit`(@TempDir tempDir: File) = runTest {
        val oversizedFile = File(tempDir, "app-log-20260101-000000.txt")
        oversizedFile.writeText("x".repeat(5 * 1024 * 1024))
        val logger = AppLogger(tempDir)

        logger.log(LogType.INFO, sucursalId = "suc-1", usuario = "german", mensaje = "nuevo archivo")

        val files = tempDir.listFiles()!!.sortedBy { it.name }
        assertEquals(2, files.size)
        assertTrue(files.last().name.matches(Regex("""app-log-\d{8}-\d{6}\.txt""")))
    }

    @Test
    fun `purges files older than the retention window and keeps recent ones`(@TempDir tempDir: File) = runTest {
        val expired = File(tempDir, "app-log-20200101-000000.txt").apply { writeText("viejo") }
        expired.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31))
        val recent = File(tempDir, "app-log-20260101-000000.txt").apply { writeText("reciente") }
        recent.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        val logger = AppLogger(tempDir)

        logger.log(LogType.INFO, sucursalId = "suc-1", usuario = "german", mensaje = "trigger purge")

        assertFalse(expired.exists())
        assertTrue(recent.exists())
    }
}
