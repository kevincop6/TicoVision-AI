package com.ulpro.ticovision_ai.ui.editor.controller

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.common.C
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

@OptIn(UnstableApi::class)
class EditorExportController(
    private val context: Context
) {

    data class ExportOutput(
        val outputFile: File,
        val contentUri: Uri?,
        val exportResult: ExportResult?
    )

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    fun validateExportableTimeline(
        timelineItems: List<TimelineItemEntity>
    ): ValidationResult {
        if (timelineItems.isEmpty()) {
            return ValidationResult.Invalid("No hay elementos en el timeline para exportar")
        }

        val visualItems = timelineItems.filter {
            val type = it.type?.lowercase(Locale.ROOT)
            (type == "video" || type == "image") && !it.sourceUri.isNullOrBlank()
        }

        if (visualItems.isEmpty()) {
            return ValidationResult.Invalid("No hay clips visuales válidos para exportar")
        }

        val hasInvalidDurations = visualItems.any { it.durationMs <= 0L }
        if (hasInvalidDurations) {
            return ValidationResult.Invalid("Hay clips con duración inválida en el timeline")
        }

        return ValidationResult.Valid
    }

    suspend fun exportTimeline(
        timelineItems: List<TimelineItemEntity>,
        onExportStarted: () -> Unit = {},
        onExportCompleted: (ExportOutput) -> Unit = {},
        onExportError: (Throwable) -> Unit = {}
    ): Result<ExportOutput> {
        return runCatching {
            when (val validation = validateExportableTimeline(timelineItems)) {
                is ValidationResult.Invalid -> {
                    throw IllegalStateException(validation.reason)
                }
                ValidationResult.Valid -> Unit
            }

            val visualItems = timelineItems.filter {
                val type = it.type?.lowercase(Locale.ROOT)
                (type == "video" || type == "image") && !it.sourceUri.isNullOrBlank()
            }

            val outputFile = createOutputFile()
            val composition = buildComposition(visualItems)

            onExportStarted()

            val exportResult = startExport(
                composition = composition,
                outputPath = outputFile.absolutePath
            )

            val savedUri = publishExportedVideo(outputFile)

            ExportOutput(
                outputFile = outputFile,
                contentUri = savedUri,
                exportResult = exportResult
            ).also(onExportCompleted)
        }.onFailure(onExportError)
    }

    private fun buildComposition(
        timelineItems: List<TimelineItemEntity>
    ): Composition {
        val editedItems = timelineItems.mapNotNull { item ->
            val uriString = item.sourceUri?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val mediaItemBuilder = MediaItem.Builder()
                .setUri(uriString)

            if (item.type.equals("image", ignoreCase = true)) {
                mediaItemBuilder.setImageDurationMs(item.durationMs.coerceAtLeast(1L))
            }

            val mediaItem = mediaItemBuilder.build()

            EditedMediaItem.Builder(mediaItem)
                .setDurationUs(item.durationMs.coerceAtLeast(1L) * 1_000L)
                .setFrameRate(30)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(Presentation.createForHeight(1280))
                    )
                )
                .build()
        }

        require(editedItems.isNotEmpty()) {
            "No se pudieron construir clips exportables"
        }

        val sequence = EditedMediaItemSequence.Builder(
            setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)
        )
            .addItems(editedItems)
            .build()

        return Composition.Builder(sequence).build()
    }

    private suspend fun startExport(
        composition: Composition,
        outputPath: String
    ): ExportResult = suspendCancellableCoroutine { continuation ->
        val transformer = Transformer.Builder(context)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context).build()
            )
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        continuation.resumeSafely(exportResult)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(exportException))
                        }
                    }
                }
            )
            .build()

        continuation.invokeOnCancellation {
            transformer.cancel()
        }

        transformer.start(composition, outputPath)
    }

    private fun createOutputFile(): File {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir

        val exportDir = File(moviesDir, "exports").apply {
            if (!exists()) mkdirs()
        }

        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())

        return File(exportDir, "TicoVision_EXPORT_$timestamp.mp4")
    }

    private fun publishExportedVideo(outputFile: File): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(outputFile)
        } else {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(outputFile.absolutePath),
                arrayOf(MimeTypes.VIDEO_MP4),
                null
            )
            null
        }
    }

    private fun saveToMediaStore(outputFile: File): Uri? {
        val resolver = context.contentResolver
        val fileName = outputFile.name

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, MimeTypes.VIDEO_MP4)
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/TicoVision-AI"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Video.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )

        val uri = resolver.insert(collection, values) ?: return null

        resolver.openOutputStream(uri)?.use { outputStream ->
            outputFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return uri
    }

    private fun <T> CancellableContinuation<T>.resumeSafely(value: T) {
        if (isActive) resume(value)
    }
}