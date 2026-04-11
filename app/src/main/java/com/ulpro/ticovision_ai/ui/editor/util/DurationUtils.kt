package com.ulpro.ticovision_ai.ui.editor.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formatea una duración en milisegundos con el formato mm:ss.
 */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}