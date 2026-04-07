package com.ulpro.ticovision_ai.ui.editor.timeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.ulpro.ticovision_ai.R
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity
import com.ulpro.ticovision_ai.databinding.ActivityVideoEditorBinding
import com.ulpro.ticovision_ai.ui.editor.VideoEditorConfig
import com.ulpro.ticovision_ai.ui.editor.util.dp
import kotlinx.coroutines.launch

/**
 * Renderiza visualmente los clips del timeline: video, imagen y pista de audio externa.
 */
class TimelineRenderer(
    private val context: Context,
    private val binding: ActivityVideoEditorBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val thumbnailProvider: TimelineThumbnailProvider,
    private val onVideoItemSelected: (TimelineItemEntity, Int) -> Unit,
    private val onImageItemSelected: (TimelineItemEntity, Int) -> Unit
) {

    /**
     * Renderiza la lista completa de elementos del timeline.
     */
    fun renderTimelineItems(
        items: List<TimelineItemEntity>,
        currentTimelineItem: TimelineItemEntity?,
        currentPlaylistIndex: Int
    ) {
        binding.timelineVideoTrack.removeAllViews()

        if (items.isEmpty()) {
            updateTimelineContentWidth(0)
            renderExternalAudioTrackIfNeeded(items)
            return
        }

        val visualTimelineItems = items.filter { it.type == "video" || it.type == "image" }

        visualTimelineItems.forEachIndexed { index, item ->
            val itemView = when (item.type) {
                "video" -> createVideoThumbnailView(item, index, currentTimelineItem, currentPlaylistIndex)
                "image" -> createImageThumbnailView(item, index)
                else -> null
            }

            if (itemView != null) {
                binding.timelineVideoTrack.addView(itemView)
            }
        }

        binding.timelineVideoTrack.post {
            updateTimelineContentWidth(binding.timelineVideoTrack.width)
        }

        renderExternalAudioTrackIfNeeded(items)
    }

    /**
     * Actualiza el ancho total del contenido del timeline y las pistas asociadas.
     */
    private fun updateTimelineContentWidth(trackWidthPx: Int) {
        val minWidthPx = VideoEditorConfig.TIMELINE_MIN_CONTENT_WIDTH_DP.dp(context)
        val finalWidth = maxOf(trackWidthPx, minWidthPx)

        binding.timelineContent.layoutParams = binding.timelineContent.layoutParams.apply {
            width = finalWidth
        }

        binding.timelineRuler.layoutParams = binding.timelineRuler.layoutParams.apply {
            width = finalWidth
        }

        binding.audioWaveTrack.layoutParams = binding.audioWaveTrack.layoutParams.apply {
            width = finalWidth
        }
    }

    /**
     * Crea una vista de clip de video con miniaturas y borde de selección.
     */
    private fun createVideoThumbnailView(
        item: TimelineItemEntity,
        index: Int,
        currentTimelineItem: TimelineItemEntity?,
        currentPlaylistIndex: Int
    ): View {
        val itemWidth = calculateTimelineItemWidthPx(item)
        val frameLayout = FrameLayout(context)

        val params = LinearLayout.LayoutParams(
            itemWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            marginEnd = 2.dp(context)
        }

        frameLayout.layoutParams = params
        frameLayout.setBackgroundColor(0xFF2A2A2A.toInt())

        val framesStrip = createVideoFramesStripOptimized(item, itemWidth)
        frameLayout.addView(framesStrip)

        val isSelected = currentPlaylistIndex == index &&
                currentTimelineItem?.sourceUri == item.sourceUri

        val overlay = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(if (isSelected) 0x22FFFFFF else 0x08000000)
        }

        val borderView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = ContextCompat.getDrawable(
                context,
                if (isSelected) {
                    R.drawable.bg_selected_timeline_item
                } else {
                    R.drawable.bg_normal_timeline_item
                }
            )
        }

        frameLayout.addView(overlay)
        frameLayout.addView(borderView)

        frameLayout.setOnClickListener {
            onVideoItemSelected(item, index)
        }

        return frameLayout
    }

    /**
     * Crea una vista de clip de imagen con repetición visual de frames.
     */
    private fun createImageThumbnailView(
        item: TimelineItemEntity,
        index: Int
    ): View {
        val itemWidth = calculateTimelineItemWidthPx(item)
        val frameLayout = FrameLayout(context)

        val params = LinearLayout.LayoutParams(
            itemWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            marginEnd = 2.dp(context)
        }

        frameLayout.layoutParams = params
        frameLayout.setBackgroundColor(0xFF2E2E2E.toInt())

        val framesStrip = createImageFramesStrip(item, itemWidth)
        frameLayout.addView(framesStrip)

        val overlay = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x14000000)
        }

        frameLayout.addView(overlay)

        frameLayout.setOnClickListener {
            onImageItemSelected(item, index)
        }

        return frameLayout
    }

    /**
     * Calcula el ancho visual del clip en el timeline en función de la duración.
     */
    private fun calculateTimelineItemWidthPx(item: TimelineItemEntity): Int {
        val minWidth = VideoEditorConfig.TIMELINE_MIN_ITEM_WIDTH_DP.dp(context)
        val pxPerSecond = VideoEditorConfig.TIMELINE_PX_PER_SECOND_DP.dp(context)
        val durationSeconds = item.durationMs.coerceAtLeast(1000L) / 1000f
        val computedWidth = (durationSeconds * pxPerSecond).toInt()
        return computedWidth.coerceAtLeast(minWidth)
    }

    /**
     * Construye la tira de miniaturas para un clip de imagen repitiendo la misma imagen.
     */
    private fun createImageFramesStrip(
        item: TimelineItemEntity,
        itemWidth: Int
    ): LinearLayout {
        val strip = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        val frameWidth = VideoEditorConfig.TIMELINE_IMAGE_FRAME_WIDTH_DP.dp(context)
        val frameCount = (itemWidth / frameWidth).coerceAtLeast(2)
        val uri = item.sourceUri?.let { Uri.parse(it) }

        repeat(frameCount) {
            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    marginEnd = 1.dp(context)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF404040.toInt())
            }

            if (uri != null) {
                imageView.setImageURI(uri)
            }

            strip.addView(imageView)
        }

        return strip
    }

    /**
     * Construye la tira de miniaturas optimizada para un clip de video.
     */
    private fun createVideoFramesStripOptimized(
        item: TimelineItemEntity,
        itemWidth: Int
    ): LinearLayout {
        val strip = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        val frameWidth = VideoEditorConfig.TIMELINE_VIDEO_FRAME_WIDTH_DP.dp(context)
        val frameCount = (itemWidth / frameWidth).coerceAtLeast(2)
        val uri = item.sourceUri?.let { Uri.parse(it) }

        repeat(frameCount) { frameIndex ->
            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    marginEnd = 1.dp(context)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF353535.toInt())
            }

            strip.addView(imageView)

            if (uri != null) {
                val durationMs = item.durationMs.coerceAtLeast(1000L)
                val progress = frameIndex.toFloat() / frameCount.toFloat()
                val frameTimeUs = (durationMs * progress * 1000L).toLong()

                lifecycleScope.launch {
                    val bitmap = thumbnailProvider.getVideoFrameAtTime(uri, frameTimeUs)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }

        return strip
    }

    /**
     * Renderiza una pista visual de audio externa si existen elementos de tipo audio.
     */
    private fun renderExternalAudioTrackIfNeeded(items: List<TimelineItemEntity>) {
        val externalAudioItems = items.filter { it.type == "audio" }

        if (externalAudioItems.isEmpty()) {
            binding.externalAudioTrackContainer.visibility = View.GONE
            binding.audioWaveTrack.removeAllViews()
            binding.tvAudioTrack.text = ""
            return
        }

        binding.externalAudioTrackContainer.visibility = View.VISIBLE
        binding.audioWaveTrack.removeAllViews()

        val firstAudioItem = externalAudioItems.first()
        val label = firstAudioItem.sourceUri
            ?.substringAfterLast("/")
            ?.substringBefore("?")
            ?.ifBlank { "Audio externo" }
            ?: "Audio externo"

        binding.tvAudioTrack.text = "Audio: $label"

        val barCount = 48
        repeat(barCount) { barIndex ->
            binding.audioWaveTrack.addView(createAudioWaveBar(barIndex))
        }
    }

    /**
     * Crea una barra decorativa para la onda de audio.
     */
    private fun createAudioWaveBar(index: Int): View {
        val heightsDp = listOf(10, 16, 8, 20, 12, 24, 11, 18, 9, 22, 14, 19)
        val barHeight = heightsDp[index % heightsDp.size].dp(context)

        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                3.dp(context),
                barHeight
            ).apply {
                marginEnd = 3.dp(context)
            }
            setBackgroundColor(0xFFB7D9B0.toInt())
        }
    }
}