package com.ulpro.ticovision_ai.ui.editor.util

import android.content.Context

/**
 * Convierte un valor entero en dp a píxeles reales usando la densidad actual del dispositivo.
 */
fun Int.dp(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}