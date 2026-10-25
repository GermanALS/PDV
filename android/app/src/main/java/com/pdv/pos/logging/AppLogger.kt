package com.pdv.pos.logging

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_LOG_FILE_BYTES = 5L * 1024 * 1024
private const val RETENTION_DAYS = 30L
private const val LOG_FILE_PREFIX = "app-log-"
private const val LOGCAT_TAG = "AppLogger"

private val entryTimestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val fileTimestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/**
 * Rota a un archivo nuevo al llegar a [MAX_LOG_FILE_BYTES]; solo al rotar
 * purga los archivos de mas de [RETENTION_DAYS] dias.
 *
 * Recibe tipo, sucursalId y usuario como parametros explicitos del llamador
 * (PLAN.md Parte 5) - no depende de ningun modulo de negocio.
 */
@Singleton
class AppLogger @Inject constructor(
    private val logDirectory: File,
) {
    suspend fun log(tipo: LogType, sucursalId: String, usuario: String, mensaje: String) {
        withContext(Dispatchers.IO) {
            val line = "[${tipo.name}][${LocalDateTime.now().format(entryTimestampFormat)}][$sucursalId][$usuario] $mensaje\n"
            try {
                currentLogFile().appendText(line)
            } catch (e: IOException) {
                Log.e(LOGCAT_TAG, "No se pudo escribir el log: $line", e)
            }
        }
    }

    private fun currentLogFile(): File {
        val latest = logDirectory.listFiles { file -> file.name.startsWith(LOG_FILE_PREFIX) }
            ?.maxByOrNull { it.name }
        if (latest != null && latest.length() < MAX_LOG_FILE_BYTES) return latest
        purgeExpiredFiles()
        return newLogFile()
    }

    private fun newLogFile(): File =
        File(logDirectory, "$LOG_FILE_PREFIX${LocalDateTime.now().format(fileTimestampFormat)}.txt")
            .apply { createNewFile() }

    private fun purgeExpiredFiles() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        logDirectory.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }
}
