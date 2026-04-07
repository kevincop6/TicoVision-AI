package com.ulpro.ticovision_ai.ui.editor.controller

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument

/**
 * Maneja los launchers de selección de archivos del editor.
 */
class EditorMediaPickerManager(
    activity: ComponentActivity,
    private val onVideoSelected: (Uri) -> Unit,
    private val onImageSelected: (Uri) -> Unit
) {

    private val pickVideoLauncher: ActivityResultLauncher<Array<String>>
    private val pickImageLauncher: ActivityResultLauncher<Array<String>>

    init {
        pickVideoLauncher = activity.registerForActivityResult(OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                onVideoSelected(uri)
            }
        }

        pickImageLauncher = activity.registerForActivityResult(OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                onImageSelected(uri)
            }
        }
    }

    /**
     * Abre el selector de documentos filtrando solo videos.
     */
    fun openVideoPicker() {
        pickVideoLauncher.launch(arrayOf("video/*"))
    }

    /**
     * Abre el selector de documentos filtrando solo imágenes.
     */
    fun openImagePicker() {
        pickImageLauncher.launch(arrayOf("image/*"))
    }
}