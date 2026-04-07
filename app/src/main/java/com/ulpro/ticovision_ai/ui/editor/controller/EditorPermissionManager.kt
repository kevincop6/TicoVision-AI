package com.ulpro.ticovision_ai.ui.editor.controller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Encapsula la lógica de permisos de medios dependiendo de la versión de Android.
 */
class EditorPermissionManager(
    private val context: Context
) {

    /**
     * Devuelve el arreglo de permisos requeridos para acceso a imágenes y video.
     */
    fun getRequiredMediaPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }

            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    /**
     * Indica si todos los permisos necesarios ya fueron concedidos.
     */
    fun areMediaPermissionsGranted(): Boolean {
        val permissions = getRequiredMediaPermissions()
        if (permissions.isEmpty()) return true

        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}