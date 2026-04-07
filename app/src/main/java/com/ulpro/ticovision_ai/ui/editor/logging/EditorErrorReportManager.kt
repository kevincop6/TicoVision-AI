package com.ulpro.ticovision_ai.ui.editor.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestor de reportes de errores del editor.
 *
 * Estrategia de almacenamiento:
 * 1) Intenta guardar en una carpeta propia de la app dentro de getExternalFilesDir(null)
 * 2) Si no está disponible, guarda en filesDir
 *
 * De esta forma siempre existe un fallback estable.
 */
class EditorErrorReportManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "EditorErrorReport"
        private const val REPORTS_FOLDER_NAME = "TicoVisionReports"
        private const val REPORTS_SUBFOLDER_EDITOR = "editor"
    }

    /**
     * Obtiene el directorio base más confiable para reportes.
     */
    private fun getSafeBaseDirectory(): File {
        val externalBase = context.getExternalFilesDir(null)
        return if (externalBase != null) {
            externalBase
        } else {
            context.filesDir
        }
    }

    /**
     * Obtiene la carpeta principal de reportes.
     */
    private fun getReportsDirectory(): File {
        val reportsDir = File(getSafeBaseDirectory(), REPORTS_FOLDER_NAME)
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }
        return reportsDir
    }

    /**
     * Obtiene la subcarpeta específica del editor.
     */
    private fun getEditorReportsDirectory(): File {
        val editorDir = File(getReportsDirectory(), REPORTS_SUBFOLDER_EDITOR)
        if (!editorDir.exists()) {
            editorDir.mkdirs()
        }
        return editorDir
    }

    /**
     * Genera un nombre de archivo legible y ordenable por fecha.
     */
    private fun buildFileName(prefix: String): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        val timestamp = formatter.format(Date())
        return "${prefix}_${timestamp}.log"
    }

    /**
     * Convierte una excepción en texto completo.
     */
    private fun throwableToString(throwable: Throwable): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        printWriter.flush()
        return stringWriter.toString()
    }

    /**
     * Construye el contenido del reporte.
     */
    private fun buildReportContent(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        extraData: Map<String, String> = emptyMap()
    ): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val now = formatter.format(Date())

        return buildString {
            appendLine("========== TicoVision AI Error Report ==========")
            appendLine("Fecha: $now")
            appendLine("Tag: $tag")
            appendLine("Package: ${context.packageName}")
            appendLine("")

            appendLine("Mensaje:")
            appendLine(message)
            appendLine("")

            if (extraData.isNotEmpty()) {
                appendLine("Datos adicionales:")
                extraData.forEach { (key, value) ->
                    appendLine("- $key: $value")
                }
                appendLine("")
            }

            if (throwable != null) {
                appendLine("Stacktrace:")
                appendLine(throwableToString(throwable))
            }

            appendLine("================================================")
        }
    }

    /**
     * Guarda un reporte de error.
     *
     * Devuelve el archivo creado o null si falló incluso el fallback.
     */
    fun saveErrorReport(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        extraData: Map<String, String> = emptyMap()
    ): File? {
        return try {
            val reportsDir = getEditorReportsDirectory()

            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val file = File(reportsDir, buildFileName(tag))
            val content = buildReportContent(
                tag = tag,
                message = message,
                throwable = throwable,
                extraData = extraData
            )

            file.writeText(content, Charsets.UTF_8)

            Log.e(TAG, "Reporte guardado en: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando reporte en carpeta principal", e)
            saveInternalFallback(tag, message, throwable, extraData)
        }
    }

    /**
     * Fallback absoluto en almacenamiento interno.
     */
    private fun saveInternalFallback(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        extraData: Map<String, String> = emptyMap()
    ): File? {
        return try {
            val internalReportsDir = File(context.filesDir, "$REPORTS_FOLDER_NAME/$REPORTS_SUBFOLDER_EDITOR")
            if (!internalReportsDir.exists()) {
                internalReportsDir.mkdirs()
            }

            val file = File(internalReportsDir, buildFileName("${tag}_fallback"))
            val content = buildReportContent(
                tag = tag,
                message = message,
                throwable = throwable,
                extraData = extraData
            )

            file.writeText(content, Charsets.UTF_8)

            Log.e(TAG, "Reporte guardado en fallback interno: ${file.absolutePath}")
            file
        } catch (inner: Exception) {
            Log.e(TAG, "Error guardando reporte incluso en fallback interno", inner)
            null
        }
    }

    /**
     * Devuelve la última ruta conocida de reportes.
     */
    fun getReportsFolderPath(): String {
        return File(getSafeBaseDirectory(), "$REPORTS_FOLDER_NAME/$REPORTS_SUBFOLDER_EDITOR").absolutePath
    }

    /**
     * Devuelve todos los reportes existentes ordenados del más reciente al más antiguo.
     */
    fun getAllReports(): List<File> {
        val externalDir = File(getSafeBaseDirectory(), "$REPORTS_FOLDER_NAME/$REPORTS_SUBFOLDER_EDITOR")
        if (!externalDir.exists()) return emptyList()

        return externalDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Elimina reportes antiguos dejando solo una cantidad máxima.
     */
    fun trimReports(maxFilesToKeep: Int = 25) {
        val files = getAllReports()
        if (files.size <= maxFilesToKeep) return

        files.drop(maxFilesToKeep).forEach { file ->
            runCatching { file.delete() }
        }
    }
}