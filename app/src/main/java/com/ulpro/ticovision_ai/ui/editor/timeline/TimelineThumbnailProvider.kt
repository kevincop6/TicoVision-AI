package com.ulpro.ticovision_ai.ui.editor.timeline

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Proveedor de miniaturas para clips del timeline.
 *
 * Responsabilidades:
 * - Obtener duración real de videos.
 * - Extraer frames de video de forma segura.
 * - Cargar miniaturas de imagen reducidas para no saturar memoria.
 * - Mantener caché en memoria para evitar trabajo repetido.
 */
class TimelineThumbnailProvider(
    private val context: Context
) {

    private val thumbnailMemoryCache by lazy {
        // Calculamos el tamaño máximo del caché en KB, protegiendo contra overflow de Int
        val maxMemoryKb = Runtime.getRuntime().maxMemory() / 1024L
        val cacheMaxSizeKb = (maxMemoryKb / 16L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        object : LruCache<String, Bitmap>(cacheMaxSizeKb) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    /**
     * Obtiene la duración real del video en milisegundos.
     */
    suspend fun getVideoDuration(uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()

        return@withContext try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Devuelve una miniatura de video para el tiempo indicado.
     *
     * El resultado se escala a un tamaño razonable para timeline
     * y se guarda en caché para reutilización.
     */
    suspend fun getVideoFrameAtTime(
        uri: Uri,
        timeUs: Long,
        targetWidth: Int = 220,
        targetHeight: Int = 220
    ): Bitmap? = withContext(Dispatchers.IO) {
        val safeTimeUs = timeUs.coerceAtLeast(0L)
        val cacheKey = "video|$uri|$safeTimeUs|$targetWidth|$targetHeight"

        thumbnailMemoryCache.get(cacheKey)?.let { cached ->
            return@withContext cached
        }

        val retriever = MediaMetadataRetriever()

        return@withContext try {
            retriever.setDataSource(context, uri)

            val rawBitmap = retriever.getFrameAtTime(
                safeTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext null

            val scaledBitmap = scaleBitmapPreservingAspect(
                bitmap = rawBitmap,
                maxWidth = targetWidth,
                maxHeight = targetHeight
            )

            if (scaledBitmap != null) {
                thumbnailMemoryCache.put(cacheKey, scaledBitmap)
            }

            if (scaledBitmap !== rawBitmap) {
                rawBitmap.recycleSafely()
            }

            scaledBitmap
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Devuelve una miniatura representativa del video.
     */
    suspend fun getRepresentativeVideoThumbnail(uri: Uri): Bitmap? {
        return getVideoFrameAtTime(
            uri = uri,
            timeUs = 1_000_000L,
            targetWidth = 220,
            targetHeight = 220
        )
    }

    /**
     * Devuelve una miniatura reducida para una imagen.
     */
    suspend fun getImageThumbnail(
        uri: Uri,
        targetWidth: Int = 220,
        targetHeight: Int = 220
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "image|$uri|$targetWidth|$targetHeight"

        thumbnailMemoryCache.get(cacheKey)?.let { cached ->
            return@withContext cached
        }

        return@withContext try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return@withContext null
            }

            val sampleSize = calculateInSampleSize(
                srcWidth = boundsOptions.outWidth,
                srcHeight = boundsOptions.outHeight,
                reqWidth = targetWidth,
                reqHeight = targetHeight
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return@withContext null

            val scaledBitmap = scaleBitmapPreservingAspect(
                bitmap = decodedBitmap,
                maxWidth = targetWidth,
                maxHeight = targetHeight
            )

            if (scaledBitmap != null) {
                thumbnailMemoryCache.put(cacheKey, scaledBitmap)
            }

            if (scaledBitmap !== decodedBitmap) {
                decodedBitmap.recycleSafely()
            }

            scaledBitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Limpia la caché de miniaturas.
     */
    fun clearCache() {
        thumbnailMemoryCache.evictAll()
    }

    /**
     * Verifica si la URI se puede leer.
     */
    fun canReadUri(uri: Uri): Boolean {
        return try {
            when (uri.scheme?.lowercase()) {
                ContentResolver.SCHEME_CONTENT -> {
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
                }
                ContentResolver.SCHEME_FILE,
                "http",
                "https" -> true
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reduce el tamaño del bitmap respetando aspecto.
     */
    private fun scaleBitmapPreservingAspect(
        bitmap: Bitmap,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        if (maxWidth <= 0 || maxHeight <= 0) return bitmap

        val srcWidth = bitmap.width
        val srcHeight = bitmap.height

        if (srcWidth <= 0 || srcHeight <= 0) return bitmap
        if (srcWidth <= maxWidth && srcHeight <= maxHeight) return bitmap

        val widthRatio = maxWidth.toFloat() / srcWidth.toFloat()
        val heightRatio = maxHeight.toFloat() / srcHeight.toFloat()
        val scale = minOf(widthRatio, heightRatio)

        val finalWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
        val finalHeight = (srcHeight * scale).toInt().coerceAtLeast(1)

        return runCatching {
            Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
        }.getOrNull() ?: bitmap
    }

    /**
     * Calcula sample size para decodificación eficiente.
     */
    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1

        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            // Usamos directamente srcHeight/srcWidth con inSampleSize,
            // sin preasignar variables "half" que nunca se actualizaban
            while (
                (srcHeight / (inSampleSize * 2)) >= reqHeight &&
                (srcWidth / (inSampleSize * 2)) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Libera bitmap si no está reciclado.
     */
    private fun Bitmap.recycleSafely() {
        runCatching {
            if (!isRecycled) recycle()
        }
    }
}