package com.ulpro.ticovision_ai.ui.editor.controller

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Maneja la selección de medios visuales y la captura con cámara.
 *
 * Flujo soportado:
 * - Galería visual del sistema para imágenes y videos.
 * - Captura de foto con cámara.
 * - Captura de video con cámara.
 *
 * Se evita OpenDocument() porque abre el visor de archivos.
 * Para una experiencia de galería moderna se usa PickVisualMedia().
 */
class EditorMediaPickerManager(
    private val activity: ComponentActivity,
    private val onImageSelected: (Uri) -> Unit,
    private val onVideoSelected: (Uri) -> Unit,
    private val onOperationCancelled: (() -> Unit)? = null
) {

    private val pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private val takePictureLauncher: ActivityResultLauncher<Uri>
    private val captureVideoLauncher: ActivityResultLauncher<Uri>

    private var pendingCameraImageUri: Uri? = null
    private var pendingCameraVideoUri: Uri? = null

    init {
        pickMediaLauncher =
            activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri == null) {
                    onOperationCancelled?.invoke()
                    return@registerForActivityResult
                }

                dispatchPickedMedia(uri)
            }

        takePictureLauncher =
            activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                val resultUri = pendingCameraImageUri
                pendingCameraImageUri = null

                if (success && resultUri != null) {
                    onImageSelected(resultUri)
                } else {
                    resultUri?.let { safeDeletePendingUri(it) }
                    onOperationCancelled?.invoke()
                }
            }

        captureVideoLauncher =
            activity.registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
                val resultUri = pendingCameraVideoUri
                pendingCameraVideoUri = null

                if (success && resultUri != null) {
                    onVideoSelected(resultUri)
                } else {
                    resultUri?.let { safeDeletePendingUri(it) }
                    onOperationCancelled?.invoke()
                }
            }
    }

    /**
     * Abre la galería visual del sistema permitiendo imágenes y videos.
     */
    fun openMediaPicker() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    /**
     * Abre directamente la galería solo para imágenes.
     */
    fun openImagePicker() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /**
     * Abre directamente la galería solo para videos.
     */
    fun openVideoPicker() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    /**
     * Lanza la cámara para tomar una foto.
     * La imagen se almacena previamente en MediaStore.
     */
    fun capturePhoto() {
        val imageUri = createImageCaptureUri() ?: run {
            onOperationCancelled?.invoke()
            return
        }

        pendingCameraImageUri = imageUri
        takePictureLauncher.launch(imageUri)
    }

    /**
     * Lanza la cámara para grabar un video.
     * El archivo se reserva previamente en MediaStore.
     */
    fun captureVideo() {
        val videoUri = createVideoCaptureUri() ?: run {
            onOperationCancelled?.invoke()
            return
        }

        pendingCameraVideoUri = videoUri
        captureVideoLauncher.launch(videoUri)
    }

    /**
     * Decide si el URI seleccionado corresponde a imagen o video
     * usando el MIME type entregado por ContentResolver.
     */
    private fun dispatchPickedMedia(uri: Uri) {
        val mimeType = activity.contentResolver.getType(uri)?.lowercase().orEmpty()

        when {
            mimeType.startsWith("image/") -> onImageSelected(uri)
            mimeType.startsWith("video/") -> onVideoSelected(uri)
            else -> {
                val fallback = uri.toString().lowercase()
                when {
                    fallback.contains(".jpg") || fallback.contains(".jpeg") ||
                            fallback.contains(".png") || fallback.contains(".webp") ||
                            fallback.contains(".heic") || fallback.contains(".gif") -> {
                        onImageSelected(uri)
                    }

                    fallback.contains(".mp4") || fallback.contains(".mov") ||
                            fallback.contains(".mkv") || fallback.contains(".webm") ||
                            fallback.contains(".3gp") -> {
                        onVideoSelected(uri)
                    }

                    else -> onOperationCancelled?.invoke()
                }
            }
        }
    }

    /**
     * Crea una URI de destino para foto usando MediaStore.
     */
    private fun createImageCaptureUri(): Uri? {
        val fileName = "ticovision_img_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TicoVision-AI")
            }
        }

        return activity.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )
    }

    /**
     * Crea una URI de destino para video usando MediaStore.
     */
    private fun createVideoCaptureUri(): Uri? {
        val fileName = "ticovision_vid_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/TicoVision-AI")
            }
        }

        return activity.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        )
    }

    /**
     * Limpia URIs reservadas si la captura fue cancelada o falló.
     */
    private fun safeDeletePendingUri(uri: Uri) {
        runCatching {
            activity.contentResolver.delete(uri, null, null)
        }
    }
}