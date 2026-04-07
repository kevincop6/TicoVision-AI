package com.ulpro.ticovision_ai.ui.editor.timeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Proveedor de miniaturas para clips del timeline.
 *
 * Incluye cache en memoria para evitar extraer frames repetidos de los mismos videos.
 */
class TimelineThumbnailProvider(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private val thumbnailMemoryCache by lazy {
        object : LruCache<String, Bitmap>(
            (Runtime.getRuntime().maxMemory() / 1024L / 16L).toInt()
        ) {
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
            duration?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    /**
     * Devuelve una miniatura cacheada o extraída del video para una posición temporal dada.
     */
    suspend fun getVideoFrameAtTime(uri: Uri, timeUs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${uri}_$timeUs"
        val cached = thumbnailMemoryCache.get(cacheKey)
        if (cached != null) return@withContext cached

        val retriever = MediaMetadataRetriever()

        return@withContext try {
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            if (bitmap != null) {
                thumbnailMemoryCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Devuelve una miniatura representativa del video cerca del primer segundo.
     */
    suspend fun getRepresentativeVideoThumbnail(uri: Uri): Bitmap? {
        return getVideoFrameAtTime(uri, 1_000_000L)
    }
}