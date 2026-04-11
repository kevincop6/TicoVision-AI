package com.ulpro.ticovisionai.ui.editor.timeline

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Proveedor optimizado de miniaturas para timeline.
 *
 * Objetivos de rendimiento:
 * - Reducir consumo de memoria en dispositivos modestos.
 * - Evitar trabajos duplicados cuando varias vistas piden la misma miniatura.
 * - Generar thumbnails de baja resolución suficientes para timeline.
 * - Mantener caché separada para imágenes y frames de video.
 */
class TimelineThumbnailProvider(
    private val context: Context
) {

    private val cacheLock = Mutex()
    private val inFlightLock = Mutex()
    private val inFlightRequests = HashSet<String>()

    private val thumbnailMemoryCache by lazy {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).coerceAtLeast(8_192L)
        val cacheMaxSizeKb = (maxMemoryKb / 24L)
            .coerceIn(2_048L, 12_288L)
            .toInt()

        object : LruCache<String, Bitmap>(cacheMaxSizeKb) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return (value.byteCount / 1024).coerceAtLeast(1)
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
            retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Miniatura representativa de video para usos rápidos.
     */
    suspend fun getRepresentativeVideoThumbnail(uri: Uri): Bitmap? {
        return getVideoFrameAtTime(
            uri = uri,
            timeUs = 1_000_000L,
            targetWidth = 96,
            targetHeight = 96
        )
    }

    /**
     * Obtiene un frame de video optimizado para timeline.
     *
     * La lógica reduce deliberadamente el tamaño objetivo porque en la tira
     * del timeline no se necesita detalle fotográfico completo.
     */
    suspend fun getVideoFrameAtTime(
        uri: Uri,
        timeUs: Long,
        targetWidth: Int = 96,
        targetHeight: Int = 96
    ): Bitmap? = withContext(Dispatchers.IO) {
        val safeTimeUs = timeUs.coerceAtLeast(0L)
        val safeWidth = targetWidth.coerceIn(48, 160)
        val safeHeight = targetHeight.coerceIn(48, 160)
        val quantizedTimeUs = quantizeFrameTimeUs(safeTimeUs)
        val cacheKey = "video|$uri|$quantizedTimeUs|$safeWidth|$safeHeight"

        getFromCache(cacheKey)?.let { return@withContext it }

        if (!markRequestStarted(cacheKey)) {
            return@withContext waitForBitmapFromCache(cacheKey)
        }

        val retriever = MediaMetadataRetriever()

        return@withContext try {
            retriever.setDataSource(context, uri)

            val rawBitmap = retriever.getFrameAtTime(
                quantizedTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext null

            val preparedBitmap = rawBitmap.toTimelineBitmap(
                maxWidth = safeWidth,
                maxHeight = safeHeight
            )

            if (preparedBitmap != null) {
                putInCache(cacheKey, preparedBitmap)
            }

            if (preparedBitmap !== rawBitmap) {
                rawBitmap.recycleSafely()
            }

            preparedBitmap
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
            markRequestFinished(cacheKey)
        }
    }

    /**
     * Obtiene una miniatura reducida de imagen para timeline.
     */
    suspend fun getImageThumbnail(
        uri: Uri,
        targetWidth: Int = 96,
        targetHeight: Int = 96
    ): Bitmap? = withContext(Dispatchers.IO) {
        val safeWidth = targetWidth.coerceIn(48, 160)
        val safeHeight = targetHeight.coerceIn(48, 160)
        val cacheKey = "image|$uri|$safeWidth|$safeHeight"

        getFromCache(cacheKey)?.let { return@withContext it }

        if (!markRequestStarted(cacheKey)) {
            return@withContext waitForBitmapFromCache(cacheKey)
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
                reqWidth = safeWidth,
                reqHeight = safeHeight
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
                inDither = true
            }

            val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return@withContext null

            val preparedBitmap = decodedBitmap.toTimelineBitmap(
                maxWidth = safeWidth,
                maxHeight = safeHeight
            )

            if (preparedBitmap != null) {
                putInCache(cacheKey, preparedBitmap)
            }

            if (preparedBitmap !== decodedBitmap) {
                decodedBitmap.recycleSafely()
            }

            preparedBitmap
        } catch (_: Exception) {
            null
        } finally {
            markRequestFinished(cacheKey)
        }
    }

    /**
     * Limpia toda la caché.
     */
    fun clearCache() {
        thumbnailMemoryCache.evictAll()
    }

    /**
     * Permite recortar memoria si la app entra en presión.
     */
    fun trimMemory(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                thumbnailMemoryCache.evictAll()
            }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                thumbnailMemoryCache.trimToSize(thumbnailMemoryCache.maxSize() / 2)
            }
        }
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

    private suspend fun getFromCache(key: String): Bitmap? {
        return cacheLock.withLock {
            thumbnailMemoryCache.get(key)
        }
    }

    private suspend fun putInCache(key: String, bitmap: Bitmap) {
        cacheLock.withLock {
            thumbnailMemoryCache.put(key, bitmap)
        }
    }

    private suspend fun markRequestStarted(key: String): Boolean {
        return inFlightLock.withLock {
            if (inFlightRequests.contains(key)) {
                false
            } else {
                inFlightRequests.add(key)
                true
            }
        }
    }

    private suspend fun markRequestFinished(key: String) {
        inFlightLock.withLock {
            inFlightRequests.remove(key)
        }
    }

    /**
     * Espera breve para reutilizar el resultado de una solicitud ya activa.
     *
     * Evita que varias corrutinas extraigan el mismo frame a la vez.
     */
    private suspend fun waitForBitmapFromCache(key: String): Bitmap? {
        repeat(8) {
            getFromCache(key)?.let { return it }
            withContext(Dispatchers.IO) {
                Thread.sleep(18L)
            }
        }
        return getFromCache(key)
    }

    /**
     * Cuantiza el tiempo pedido para que frames muy cercanos reutilicen caché.
     *
     * Matemáticamente esto reduce cardinalidad de claves:
     * en vez de una clave por cada microsegundo, se agrupan ventanas fijas.
     */
    private fun quantizeFrameTimeUs(timeUs: Long): Long {
        val stepUs = 250_000L
        return ((timeUs / stepUs) * stepUs).coerceAtLeast(0L)
    }

    /**
     * Convierte un bitmap a formato liviano para timeline.
     *
     * Se usa RGB_565 porque consume aproximadamente la mitad de memoria
     * respecto a ARGB_8888, suficiente para miniaturas pequeñas.
     */
    private fun Bitmap.toTimelineBitmap(
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        val scaled = scaleBitmapPreservingAspect(
            bitmap = this,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        ) ?: return null

        if (scaled.config == Bitmap.Config.RGB_565) {
            return scaled
        }

        return runCatching {
            scaled.copy(Bitmap.Config.RGB_565, false)
        }.getOrNull() ?: scaled
    }

    /**
     * Reduce el bitmap respetando aspecto.
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
     * Calcula sample size eficiente para decodificación.
     */
    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1

        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            while (
                (srcHeight / (inSampleSize * 2)) >= reqHeight &&
                (srcWidth / (inSampleSize * 2)) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    private fun Bitmap.recycleSafely() {
        runCatching {
            if (!isRecycled) recycle()
        }
    }
}