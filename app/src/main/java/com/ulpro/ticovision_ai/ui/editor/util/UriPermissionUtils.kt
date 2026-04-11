package com.ulpro.ticovision_ai.ui.editor.util

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/**
 * Intenta persistir el permiso de lectura del Uri para que el proyecto pueda seguir accediendo
 * al archivo incluso después de reiniciar la aplicación.
 */
fun ContentResolver.takeReadUriPermissionSafely(uri: Uri) {
    try {
        takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: Exception) {
    }
}